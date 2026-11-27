# Headless Chromium validation prototype

This is a separate, headless retrieval/validation tool. It is **not** the Android WebView application and does not replace its interactive browser role.

## Modes and evidence

The initial implementation provides only `static_only`:
- launches Chromium headlessly with page JavaScript disabled;
- allows requests only to the exact starting origin;
- revalidates redirects and DNS for each request;
- blocks state-changing methods, uncaptured subresources, cross-origin requests, local/special-use hostnames, and non-public resolved IP addresses;
- captures the actual main-document response body and response metadata;
- creates a sanitized static HTML derivative;
- records raw-body, sanitized-core, and final-HTML SHA-256 hashes;
- reports blocked resources, omissions, and a `main_document_only` capture scope rather than implying a complete website archive.

The HTML sanitizer parses with BeautifulSoup, removes active elements, records inert omission metadata (including script/event hashes without returning code), and then applies a Bleach allowlist. It intentionally drops scripts, styles, forms, frames, embeds, resource-loading attributes, and other active content. This is a conservative prototype, not a full browser-equivalence transformation.

## Security boundary

The route guard performs DNS checks before Chromium requests each URL, and rejects cross-origin traffic. A DNS check followed by a separate Chromium connection still has a DNS-rebinding/time-of-check race. **Do not expose this prototype to untrusted users or treat it as a production SSRF boundary without OS/container egress filtering or a validating egress proxy that pins the resolved destination.** Redirects and subresources are rechecked, but that does not replace network-layer isolation.

The `private_test_hosts` and `_test_responses` parameters exist only for deterministic fixtures in tests. Production callers must leave it empty. It must not be exposed as a user-controlled setting.

## Run

```bash
cd tools/headless_chromium
python -m pip install -e '.[test]'
python -m playwright install chromium
python -m pytest -q
python -m headless_chromium.fetcher https://example.com/ --out artifacts/example-com
```

Outputs: `raw.html`, `sanitized.html`, and `report.json`. Hash definitions:
- `raw_sha256`: exact response-body bytes returned by Chromium;
- `sanitized_core_sha256`: sanitizer output before the generated HTML wrapper/provenance;
- `sanitized_html_sha256`: final generated HTML bytes, including provenance metadata.

## Scope limitations

The transformation is not JavaScript equivalence: it preserves safe static structure and records omitted scripts/events as inert metadata, without executing or returning source code.

This is not a complete offline website archiver. It captures one main HTML response only; it does not save image/font/CSS/media bodies, cookies, authenticated sessions, or server-side state. Cross-origin subresources are blocked and listed. It does not execute JavaScript and therefore cannot reconstruct content that only exists after script execution.
