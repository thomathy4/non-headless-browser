"""Evidence-preserving static Chromium retrieval."""
from .fetcher import FetchBlockedError, FetchResult, fetch_static

__all__ = ["FetchBlockedError", "FetchResult", "fetch_static"]
