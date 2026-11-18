# Non-Headless Browser

An Android WebView browser with JavaScript disabled by default, a main-frame domain navigation lock, mobile/desktop User-Agent switching, and HTML capture for inspection.

## Features

| Control | Default | Behavior |
|---|---:|---|
| JavaScript | Off | Disables page-authored JavaScript and DOM storage on startup. |
| Domain Lock | On | Restricts top-level navigation to the current root host and its subdomains. |
| Desktop UA | Off | Requests desktop layouts with a Linux Chromium User-Agent. |
| Capture HTML | — | Extracts the current document, adds stable local site/page identifiers and capture metadata, converts each hyperlink into an expandable HTML link record with a nested historical-captures dropdown, converts scripts and inline event handlers into inspectable HTML metadata, and supports preview, render, copy, and share. |

## Architecture direction

The longer-term design is an HTML-first local web: one locally navigable website per domain, with a canonical HTML entry point, self-describing rewritten pages, historical link navigation, revision/provenance records, and stable local addressing. Historical captures and their dependencies are represented within the page's own HTML interface; missing or approximate material must be disclosed rather than silently replaced with live content.

The proposed **HTML Layout Protocol (HLP)** and **Local Web Address and Allocation Protocol (LWAP)** are project design proposals, not existing standards and not yet implemented.

See [Local Web, Historical Navigation, and HTML Layout Architecture](docs/architecture/local-web-historical-navigation-and-html-layout.md) for the architecture, Wayback/Internet Archive research, design principles, limitations, and staged implementation path.

## Domain lock boundary

The lock applies to top-level navigations initiated by links, page redirects, and browser controls. It is not a network firewall and does not block every third-party image, stylesheet, font, iframe, or other subresource. Sites may still make background requests to other hosts. Entering a new address intentionally establishes that host as the new locked root.

## HTML capture

Capture uses WebView document extraction and Jsoup parsing. It replaces inline/external script elements, stores inline script source as escaped text, preserves external script URLs as links, converts inline on-event attributes to data attributes, converts noscript containers to ordinary div elements, and appends a provenance manifest.

Rewritten output includes a page-history disclosure, deterministic local site/document identifiers, a generated-at timestamp, and one expandable record for each original hyperlink. Each link record preserves the original href and link attributes, exposes the resolved destination, and contains a nested historical-captures dropdown. The prototype honestly reports that local captures and historical indexes are not yet connected; it does not fabricate capture dates, claim durable storage, or silently substitute live pages.\n\nThis is script neutralization for inspection, **not a complete HTML sanitizer**. It does not guarantee that every active HTML feature, embedded frame, remote resource, form, CSS behavior, or navigation mechanism is inert. Captured snapshots are displayed with JavaScript disabled.

## Network and privacy

The app requests Internet access. Cleartext HTTP is enabled to support local development servers and HTTP sites; HTTP traffic is not encrypted, so use HTTPS where available. WebView Safe Browsing is enabled, file/content URL access is disabled, mixed content is blocked, and no JavaScript-to-native bridge is exposed.

The app does not currently implement download management, cookie controls, private browsing, content filtering, certificate pinning, or comprehensive subresource isolation.

## Build and verification

GitHub Actions runs testDebugUnitTest, lintDebug, and assembleDebug. A successful run uploads app-debug.apk as the non-headless-browser-debug-apk artifact for 14 days. The workflow does not publish a GitHub Release or signed production APK.

Requirements: Android 8.0 (API 26)+, Android SDK 34, JDK 17, and Gradle 8.7.

CI success does not establish physical-device installation or runtime compatibility; those require separate testing.
