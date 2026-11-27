import asyncio
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread

import pytest
from headless_chromium.fetcher import sanitize_html

from headless_chromium.fetcher import (
    FetchBlockedError,
    _origin,
    fetch_static,
    validate_target,
)


def test_loopback_literal_is_rejected():
    origin = ("http", "127.0.0.1", 80)
    with pytest.raises(ValueError, match="Non-public"):
        validate_target("http://127.0.0.1/", origin)


def test_private_dns_answer_is_rejected():
    origin = ("https", "private.example", 443)
    with pytest.raises(ValueError, match="Non-public"):
        validate_target(
            "https://private.example/",
            origin,
            resolver=lambda _host: {"10.0.0.7"},
        )


def test_mixed_public_and_private_dns_answers_are_rejected():
    origin = ("https", "mixed.example", 443)
    with pytest.raises(ValueError, match="Non-public"):
        validate_target(
            "https://mixed.example/",
            origin,
            resolver=lambda _host: {"93.184.216.34", "192.168.1.10"},
        )


def test_cross_origin_request_is_rejected_before_dns():
    origin = ("https", "example.com", 443)
    with pytest.raises(ValueError, match="Cross-origin"):
        validate_target("https://169.254.169.254/latest/meta-data/", origin)


def test_unsupported_schemes_and_url_credentials_are_rejected():
    with pytest.raises(ValueError):
        _origin("file:///etc/passwd")
    with pytest.raises(ValueError, match="credentials"):
        _origin("https://user:pass@example.com/")


def test_redirect_to_different_host_is_blocked():
    url = "http://127.0.0.1:8000/redirect"
    fixtures = {
        url: {
            "status": 302,
            "headers": {"location": "http://localhost:8000/landing"},
            "body": "",
        }
    }
    try:
        with pytest.raises(FetchBlockedError, match="stopped by policy"):
            asyncio.run(fetch_static(
                url,
                private_test_hosts=frozenset({"127.0.0.1"}),
                _test_responses=fixtures,
            ))
    except Exception as exc:
        if os.getenv("GITHUB_ACTIONS", "").lower() != "true" and "ERR_BLOCKED_BY_ADMINISTRATOR" in str(exc):
            pytest.skip("The execution environment blocks Chromium loopback navigation")
        raise


def test_cross_origin_subresource_is_blocked_and_javascript_stays_off():
    url = "http://127.0.0.1:8000/cross"
    fixtures = {
        url: {
            "status": 200,
            "headers": {"content-type": "text/html; charset=utf-8"},
            "body": (
                "<title>fixture</title><h1>Static fixture</h1>"
                '<img src="http://localhost:8000/pixel">'
                "<script>document.title='SCRIPT-RAN'</script>"
            ),
        },
        "http://127.0.0.1:8000/favicon.ico": {
            "status": 204,
            "headers": {},
            "body": "",
        },
    }
    try:
        result = asyncio.run(fetch_static(
            url,
            private_test_hosts=frozenset({"127.0.0.1"}),
            _test_responses=fixtures,
        ))
    except Exception as exc:
        if "ERR_BLOCKED_BY_ADMINISTRATOR" in str(exc):
            pytest.skip("The execution environment blocks Chromium loopback navigation")
        raise
    assert result.javascript_enabled is False
    assert result.rendered_title == "fixture"
    assert result.blocked_resources
    assert any("localhost" in item["url"] for item in result.blocked_resources)
    assert "<script" not in result.sanitized_html.lower()
    assert result.complete is False



def test_sanitizer_removes_active_markup_and_hashes_are_stable():
    import hashlib

    raw = '<h1>safe</h1><script>alert(1)</script><form action="https://evil.example"><input></form><a href="javascript:alert(1)" onclick="alert(2)">bad</a>'
    raw_hash = hashlib.sha256(raw.encode()).hexdigest()
    sanitized, core_hash, final_hash, omissions = sanitize_html(raw, raw_hash, "https://example.com/")
    assert "<script" not in sanitized.lower()
    assert "<form" not in sanitized.lower()
    from bs4 import BeautifulSoup
    parsed = BeautifulSoup(sanitized, "html.parser")
    assert all(not name.lower().startswith("on") for tag in parsed.find_all(True) for name in tag.attrs)
    assert "javascript:" not in sanitized.lower()
    assert "safe" in sanitized
    assert "Omitted behavior" in sanitized
    assert any(item["feature"] == "inline_script" for item in omissions)
    assert any(item["feature"] == "event_handler" for item in omissions)
    assert len(core_hash) == len(final_hash) == 64
