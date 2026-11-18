# Non-Headless Browser

An Android WebView browser with JavaScript disabled by default, a main-frame domain navigation lock, mobile/desktop User-Agent switching, and rendered-HTML snapshots for inspection.

## Features

| Control | Default | Behavior |
|---|---:|---|
| JavaScript | Off | Disables page-authored JavaScript and DOM storage on startup. |
| Domain Lock | On | Restricts top-level navigation to the current root host and its subdomains. |
| Desktop UA | Off | Requests desktop layouts with a Linux Chromium User-Agent. |
| Snapshot HTML | — | Extracts the rendered DOM as an HTML snapshot, adds provisional URL-derived prototype keys and a representation-generation timestamp, converts each hyperlink into an expandable HTML record with an explicitly unimplemented historical-navigation placeholder, converts scripts and inline event handlers into inspectable HTML metadata, and supports preview, render, copy, and share. |

## Architecture direction

The longer-term design is an HTML-first local web: one locally navigable website per domain, with a canonical HTML entry point, self-describing rewritten pages, historical link navigation, original-receipt/provenance records, revisions, and stable local addressing. This is the intended architecture, not the current product capability. When implemented, historical captures and their dependencies will be represented within the page's own HTML interface; missing or approximate material must be disclosed rather than silently replaced with live content.

The proposed **HTML Layout Protocol (HLP)** and **Local Web Address and Allocation Protocol (LWAP)** are project design proposals, not existing standards and not yet implemented. The Internet Archive/Wayback references in the architecture document are research context only; this product work does not import or integrate Wayback records.

See [Local Web, Historical Navigation, and HTML Layout Architecture](docs/architecture/local-web-historical-navigation-and-html-layout.md) for the architecture, Wayback/Internet Archive research, design principles, limitations, and staged implementation path.

## Domain lock boundary

The lock applies to top-level navigations initiated by links, page redirects, and browser controls. It is not a network firewall and does not block every third-party image, stylesheet, font, iframe, or other subresource. Sites may still make background requests to other hosts. Entering a new address intentionally establishes that host as the new locked root.

## Rendered HTML snapshot

The current Snapshot HTML action extracts `document.documentElement.outerHTML` from WebView and processes that rendered DOM with Jsoup. It is not an original HTTP-response capture and does not create an immutable receipt. It replaces inline/external script elements, stores inline script source as escaped text, preserves external script URLs as links, converts inline on-event attributes to data attributes, converts noscript containers to ordinary div elements, and appends a provenance manifest.

The HTML snapshot includes a representation-status disclosure, provisional URL-derived prototype keys (not registered local identities), a representation-generated timestamp (not a receipt or capture time), and one expandable record for each original hyperlink. Each link record preserves the original href and link attributes and exposes the resolved destination. Historical navigation is not implemented; the placeholder explicitly says so. The current preview is not persisted, no original HTTP response receipt is stored, and no historical records are asserted.\n\nThis is script neutralization for inspection, **not a complete HTML sanitizer**. It does not guarantee that every active HTML feature, embedded frame, remote resource, form, CSS behavior, or navigation mechanism is inert. Rendered snapshots are displayed with JavaScript disabled.

## Network and privacy

The app requests Internet access. Cleartext HTTP is enabled to support local development servers and HTTP sites; HTTP traffic is not encrypted, so use HTTPS where available. WebView Safe Browsing is enabled, file/content URL access is disabled, mixed content is blocked, and no JavaScript-to-native bridge is exposed.

The app does not currently implement download management, cookie controls, private browsing, content filtering, certificate pinning, or comprehensive subresource isolation.

## Build and verification

GitHub Actions runs testDebugUnitTest, lintDebug, and assembleDebug. A successful run uploads app-debug.apk as the non-headless-browser-debug-apk artifact for 14 days. The workflow does not publish a GitHub Release or signed production APK.

Requirements: Android 8.0 (API 26)+, Android SDK 34, JDK 17, and Gradle 8.7.

CI success does not establish physical-device installation or runtime compatibility; those require separate testing.
