# Headless Chromium implementation companion

**Canonical reusable agent skill:** [Headless Chromium Web Browser v2.0.0](https://github.com/thomathy4/manus-skills/blob/publish-skills/headless-chromium-web-browser/SKILL.md).

That published skill and its supporting security, staticization, and connector-deployment references govern the full browser-agent design. This repository file is only a companion for the prototype in `tools/headless_chromium`; it does not replace or redefine the canonical skill.

## Implemented here

- A `static_only` Playwright/Chromium prototype.
- JavaScript disabled before navigation; service workers and downloads disabled.
- Exact-origin checks, DNS resolution checks, local/private-address rejection, redirect checks, and cross-origin request blocking.
- Main-document-only capture; uncaptured subresources and non-GET/HEAD methods are blocked and reported.
- SHA-256 for the raw HTTP response body, sanitizer output before the wrapper, and final sanitized HTML.
- Parser-based removal of scripts, event handlers, forms, embedded documents, active media, CSS, and automatic refresh.
- An inert HTML omission manifest records script/event categories and source hashes without returning executable source code.

## Not implemented

- `rendered_snapshot` or `partially_reconstructed` modes.
- Full resource-set capture, authenticated sessions, CSS preservation, or complete offline replay.
- A network-layer egress proxy or DNS pinning. DNS validation followed by a separate Chromium connection has a time-of-check/time-of-use race; production use requires network-layer egress controls.
- An MCP server, hosted HTTPS endpoint, authentication, or Manus connector registration.

Use the canonical v2.0.0 skill for the complete workflow, tool contract, security boundaries, and deployment requirements. Do not describe this prototype as production-ready or as a behaviorally equivalent JavaScript translator.
