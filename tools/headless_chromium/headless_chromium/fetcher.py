from __future__ import annotations

import argparse
import asyncio
import hashlib
import html
import ipaddress
import json
import os
import socket
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

import bleach
from bs4 import BeautifulSoup
from playwright.async_api import async_playwright


class FetchBlockedError(RuntimeError):
    """A navigation or resource was denied by the retrieval policy."""


@dataclass
class FetchResult:
    requested_url: str
    final_url: str
    status: int
    content_type: str
    received_at: str
    redirect_chain: list[str]
    blocked_resources: list[dict[str, str]]
    omissions: list[dict[str, str | None]]
    capture_scope: str
    raw_sha256: str
    sanitized_core_sha256: str
    sanitized_html_sha256: str
    complete: bool
    mode: str
    javascript_enabled: bool
    rendered_title: str
    raw_html: str
    sanitized_html: str

    def report(self) -> dict:
        result = asdict(self)
        result.pop("raw_html")
        result.pop("sanitized_html")
        return result


def _origin(url: str) -> tuple[str, str, int]:
    p = urlsplit(url)
    if p.scheme not in {"http", "https"} or not p.hostname:
        raise ValueError("Only absolute HTTP(S) URLs are permitted")
    if p.username is not None or p.password is not None:
        raise ValueError("URLs containing credentials are not permitted")
    host = p.hostname.encode("idna").decode("ascii").lower().rstrip(".")
    port = p.port or (443 if p.scheme == "https" else 80)
    return p.scheme.lower(), host, port


def _resolve_addresses(host: str) -> set[str]:
    try:
        answers = socket.getaddrinfo(host, None, type=socket.SOCK_STREAM)
    except OSError as exc:
        raise ValueError(f"DNS resolution failed for {host}: {exc}") from exc
    addresses = {answer[4][0].split("%", 1)[0] for answer in answers}
    if not addresses:
        raise ValueError(f"No DNS addresses returned for {host}")
    return addresses


def validate_target(
    url: str,
    expected_origin: tuple[str, str, int],
    *,
    private_test_hosts: frozenset[str] = frozenset(),
    resolver=_resolve_addresses,
) -> None:
    """Validate origin and resolved addresses before Chromium is allowed to request.

    private_test_hosts is an explicit test-only escape hatch for loopback fixtures.
    Production callers must leave it empty. A deployment still needs OS/container
    egress controls to close the DNS-check-to-connect race.
    """
    scheme, host, port = _origin(url)
    if (scheme, host, port) != expected_origin:
        raise ValueError("Cross-origin request or redirect blocked")
    if port not in ({80, 443} if host not in private_test_hosts else {port}):
        raise ValueError("Non-standard destination port blocked")
    if host in private_test_hosts:
        return
    if host == "localhost" or host.endswith((".localhost", ".local", ".internal", ".test", ".invalid", ".onion")):
        raise ValueError("Local or special-use hostname blocked")
    try:
        literal = ipaddress.ip_address(host)
        addresses = {str(literal)}
    except ValueError:
        addresses = resolver(host)
    for value in addresses:
        address = ipaddress.ip_address(value)
        if not address.is_global:
            raise ValueError(f"Non-public destination address blocked: {address}")


_ALLOWED_TAGS = {
    "a", "article", "aside", "blockquote", "br", "caption", "code", "dd", "details",
    "div", "dl", "dt", "em", "figcaption", "figure", "h1", "h2", "h3", "h4",
    "h5", "h6", "hr", "li", "main", "ol", "p", "pre", "section", "small",
    "span", "strong", "sub", "summary", "sup", "table", "tbody", "td", "th",
    "thead", "tr", "ul",
}
_ALLOWED_ATTRIBUTES = {
    "a": {"href", "title"},
    "td": {"colspan", "rowspan"},
    "th": {"colspan", "rowspan", "scope"},
}


def _remove_active_content(raw_html: str) -> tuple[str, list[dict[str, str | None]]]:
    """Parse active markup and retain only inert omission metadata, never source code."""
    soup = BeautifulSoup(raw_html, "html.parser")
    omissions: list[dict[str, str | None]] = []

    for index, script in enumerate(soup.find_all("script"), start=1):
        source = script.get("src")
        source_text = script.string or script.get_text()
        omissions.append({
            "feature": "external_script" if source else "inline_script",
            "id": f"script-{index}",
            "source_url": str(source) if source else None,
            "sha256": hashlib.sha256(source_text.encode("utf-8")).hexdigest() if source_text else None,
            "reason": "not executed; source omitted from static output",
        })
        script.decompose()

    for element in soup.find_all(True):
        for attribute, value in list(element.attrs.items()):
            if attribute.lower().startswith("on"):
                omissions.append({
                    "feature": "event_handler",
                    "id": None,
                    "source_url": None,
                    "sha256": hashlib.sha256(str(value).encode("utf-8")).hexdigest(),
                    "reason": f"removed {element.name}[{attribute}]",
                })
                del element.attrs[attribute]

    for tag_name in ("noscript", "style", "form", "iframe", "object", "embed", "svg", "canvas", "video", "audio"):
        for element in soup.find_all(tag_name):
            omissions.append({
                "feature": tag_name,
                "id": None,
                "source_url": None,
                "sha256": None,
                "reason": f"{tag_name} content omitted by static-only policy",
            })
            element.decompose()

    for meta in soup.find_all("meta"):
        if str(meta.get("http-equiv", "")).lower() == "refresh":
            omissions.append({
                "feature": "meta_refresh",
                "id": None,
                "source_url": None,
                "sha256": None,
                "reason": "automatic navigation removed",
            })
            meta.decompose()

    return str(soup), omissions


def sanitize_html(raw_html: str, raw_sha256: str, source_url: str) -> tuple[str, str, str, list[dict[str, str | None]]]:
    """Create inert HTML and a manifest of omitted behavior; never translate JavaScript."""
    parsed_html, omissions = _remove_active_content(raw_html)
    fragment = bleach.clean(
        parsed_html,
        tags=_ALLOWED_TAGS,
        attributes=_ALLOWED_ATTRIBUTES,
        protocols={"http", "https", "mailto"},
        strip=True,
        strip_comments=True,
    )
    core_hash = hashlib.sha256(fragment.encode("utf-8")).hexdigest()
    safe_source = html.escape(source_url, quote=True)
    omission_items = "".join(
        "<li>" + html.escape(
            f"{item['feature']} | {item['reason']} | source={item['source_url'] or 'not recorded'} | sha256={item['sha256'] or 'not available'}"
        ) + "</li>"
        for item in omissions
    )
    omission_manifest = (
        f"<details><summary>Omitted behavior ({len(omissions)} records)</summary>"
        f"<ul>{omission_items}</ul></details>"
    )
    document = (
        "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
        "<meta name=\"robots\" content=\"noindex,nofollow\">"
        f"<meta name=\"browser:source-url\" content=\"{safe_source}\">"
        f"<meta name=\"browser:raw-sha256\" content=\"{raw_sha256}\">"
        f"<meta name=\"browser:sanitized-core-sha256\" content=\"{core_hash}\">"
        "<title>Static preserved representation</title></head><body>"
        "<p><strong>Derived static representation.</strong> This is not a behavioral "
        "translation of JavaScript. Active code and behavior are omitted and summarized below.</p>"
        f"{omission_manifest}{fragment}</body></html>"
    )
    final_hash = hashlib.sha256(document.encode("utf-8")).hexdigest()
    return document, core_hash, final_hash, omissions

async def fetch_static(
    url: str,
    *,
    timeout_ms: int = 20000,
    private_test_hosts: frozenset[str] = frozenset(),
    _test_responses: dict[str, dict] | None = None,
) -> FetchResult:
    """Fetch one HTML document in Chromium with page JavaScript disabled.

    Only requests to the exact starting origin are permitted. Every redirect and
    subresource is checked independently. Other origins are recorded as blocked.
    """
    expected_origin = _origin(url)
    # Validate the first destination before starting Chromium.
    validate_target(url, expected_origin, private_test_hosts=private_test_hosts)
    blocked: list[dict[str, str]] = []
    redirects: list[str] = []

    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(
            headless=True,
            executable_path=os.getenv("CHROMIUM_EXECUTABLE") or None,
            args=[
                "--disable-background-networking",
                "--disable-component-update",
                "--disable-sync",
                "--no-first-run",
                "--no-default-browser-check",
            ],
        )
        try:
            context = await browser.new_context(
                java_script_enabled=False,
                service_workers="block",
                accept_downloads=False,
                ignore_https_errors=False,
            )
            page = await context.new_page()

            async def guard(route):
                request = route.request
                request_url = request.url
                try:
                    validate_target(
                        request_url,
                        expected_origin,
                        private_test_hosts=private_test_hosts,
                    )
                    if request.method not in {"GET", "HEAD"}:
                        raise ValueError("State-changing request method blocked")
                    if not request.is_navigation_request or request.frame != page.main_frame:
                        path = urlsplit(request_url).path.lower()
                        if path.endswith("/favicon.ico"):
                            await route.abort("blockedbyclient")
                            return
                        raise ValueError("Subresource not captured by the main-document-only prototype")
                    redirects_seen = 0
                    previous = request.redirected_from
                    while previous is not None:
                        redirects_seen += 1
                        previous = previous.redirected_from
                    if redirects_seen > 5:
                        raise ValueError("Redirect limit exceeded")
                except ValueError as exc:
                    blocked.append({"url": request_url, "reason": str(exc)})
                    await route.abort("blockedbyclient")
                else:
                    fixture = (_test_responses or {}).get(request_url)
                    if fixture is not None:
                        await route.fulfill(**fixture)
                    else:
                        await route.continue_()

            await page.route("**/*", guard)
            try:
                response = await page.goto(
                    url, wait_until="domcontentloaded", timeout=timeout_ms
                )
            except Exception as exc:
                if blocked:
                    raise FetchBlockedError(
                        f"Navigation stopped by policy; blocked {len(blocked)} request(s)"
                    ) from exc
                raise
            try:
                await page.wait_for_load_state("networkidle", timeout=1500)
            except Exception:
                pass
            if response is None:
                raise FetchBlockedError("Chromium returned no main-document response")
            final_url = response.url
            if _origin(final_url) != expected_origin:
                raise FetchBlockedError("Final URL escaped the requested origin")
            chain = []
            request = response.request
            while request is not None:
                chain.append(request.url)
                request = request.redirected_from
            redirects = list(reversed(chain))
            body = await response.body()
            content_type = response.headers.get("content-type", "")
            if "text/html" not in content_type.lower():
                raise FetchBlockedError(f"Expected text/html, received {content_type!r}")
            raw_hash = hashlib.sha256(body).hexdigest()
            raw_html = body.decode("utf-8", errors="replace")
            sanitized, core_hash, final_hash, omissions = sanitize_html(raw_html, raw_hash, final_url)
            status = response.status
            return FetchResult(
                requested_url=url,
                final_url=final_url,
                status=status,
                content_type=content_type,
                received_at=datetime.now(timezone.utc).isoformat(),
                redirect_chain=redirects,
                blocked_resources=blocked,
                omissions=omissions,
                capture_scope="main_document_only",
                raw_sha256=raw_hash,
                sanitized_core_sha256=core_hash,
                sanitized_html_sha256=final_hash,
                complete=(200 <= status < 300 and not blocked),
                mode="static_only",
                javascript_enabled=False,
                rendered_title=await page.title(),
                raw_html=raw_html,
                sanitized_html=sanitized,
            )
        finally:
            await browser.close()


async def _main() -> None:
    parser = argparse.ArgumentParser(description="Fetch a static HTML representation with Chromium")
    parser.add_argument("url")
    parser.add_argument("--out", default="artifacts/example-com")
    args = parser.parse_args()
    result = await fetch_static(args.url)
    output = Path(args.out)
    output.mkdir(parents=True, exist_ok=True)
    (output / "raw.html").write_text(result.raw_html, encoding="utf-8")
    (output / "sanitized.html").write_text(result.sanitized_html, encoding="utf-8")
    (output / "report.json").write_text(
        json.dumps(result.report(), indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(json.dumps(result.report(), indent=2, sort_keys=True))


if __name__ == "__main__":
    asyncio.run(_main())
