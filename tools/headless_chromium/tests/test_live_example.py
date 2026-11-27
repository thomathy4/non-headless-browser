import asyncio
import os
import pytest

from headless_chromium.fetcher import fetch_static


def test_example_com_static_retrieval_and_provenance():
    try:
        result = asyncio.run(fetch_static("https://example.com/"))
    except Exception as exc:
        if os.getenv("GITHUB_ACTIONS", "").lower() != "true" and ("DNS resolution failed" in str(exc) or "Timeout" in type(exc).__name__):
            pytest.skip("This execution environment has no outbound DNS/network access")
        raise
    assert result.status == 200
    assert result.final_url.startswith("https://example.com")
    assert result.mode == "static_only"
    assert result.javascript_enabled is False
    assert result.raw_sha256 and len(result.raw_sha256) == 64
    assert result.sanitized_core_sha256 and len(result.sanitized_core_sha256) == 64
    assert result.sanitized_html_sha256 and len(result.sanitized_html_sha256) == 64
    assert "Example Domain" in result.raw_html
    assert "Example Domain" in result.sanitized_html
    assert "<script" not in result.sanitized_html.lower()
    assert result.complete is True
