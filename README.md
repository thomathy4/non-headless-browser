# Non-Headless Browser

An Android WebView browser with JavaScript disabled by default, a main-frame domain navigation lock, mobile/desktop User-Agent switching, and rendered-HTML snapshots for inspection.

## Features

| Control | Default | Behavior |
|---|---:|---|
| JavaScript | Off | Disables page-authored JavaScript and DOM storage on startup. |
| Domain Lock | On | Restricts top-level navigation to the current root host and its subdomains. |
| Desktop UA | Off | Requests desktop layouts with a Linux Chromium User-Agent. |
| Snapshot HTML | — | Extracts the rendered DOM, converts scripts and inline event handlers into inspectable HTML metadata, and supports preview, render, copy, share, and saving a derived representation into Local Web. |
| Local Web | — | Opens the browser-owned HTML index. Saved snapshots are grouped under one canonical HTML site index per host, with persistent document IDs and append-only revision files. |

## Architecture direction

The governing design is the user's HTML-based local web: one canonical, interconnected local HTML website per domain. Actual resource receipt records, original-resource representation, rendered snapshots, derived representations, persistent site/document identities, local navigation, and local storage organization all belong to this HTML system. A visited or downloaded website is intended to remain reviewable and navigable offline. This is intended architecture, not current product capability. Historical link navigation concerns the browser's own records; Wayback research does not authorize Wayback import or integration.

The names **HTML Layout Protocol (HLP)** and **Local Web Address and Allocation Protocol (LWAP)** are working labels from earlier drafts, not existing standards or independently approved requirements. Internet Archive/Wayback references are research context only. No Wayback records are imported, incorporated, synchronized, or integrated.

See [Local Web, Historical Navigation, and HTML Layout Architecture](docs/architecture/local-web-historical-navigation-and-html-layout.md) for the architecture, Wayback/Internet Archive research, design principles, limitations, and staged implementation path.

## User-submitted project baseline

The browser's governing product architecture is HTML-based. The receipts, original-resource representations, snapshots, derivatives, persistent identities, canonical local websites, navigation, and storage organization described below are all part of the HTML system—not separate product subsystems. HTML is not merely a display format: the user's requirements place resource receipt records, original-resource representation, rendered snapshots, derived representations, persistent site/document identities, the canonical local website for each domain, navigation, and storage organization within the HTML system. Browser foundations, including settings, storage, and navigation, are to be built from, by, and for HTML.

Each domain is intended to have one canonical local HTML website, with interconnected pages, resources, receipts, representations, and links. Resource receipts are actual and immutable; original material, its receipt, rendered snapshots, and derived representations remain distinguishable and related. Rewritten/compiled HTML uses compact escape-code references to receipts rather than needlessly duplicating original material. Stable local identities are distinct from remote URLs and physical storage placement.

A visited or downloaded website should remain reviewable and navigable offline. Changes accumulate as local records/revisions, with local navigation independent of the remote server. Preservation records what was legitimately delivered, respects access controls and applicable restrictions, and honestly identifies missing, blocked, dynamic, incomplete, or partly reproducible material.

Media submissions include SVG/JPEG image derivatives, inline video with conversion deferred in the earlier direction, and later algorithmic reconstruction/compression by shared patterns and parameters. An earlier direction retained original images alongside derivatives; a later direction allowed original media payloads to be discarded after verified reconstruction while retaining immutable receipt and retrieval information. These directions are preserved chronologically; no universal media-retention rule is invented here.

No Wayback record import or integration is part of this product. Internet Archive/Wayback material in the architecture document is research context only.

The user's current consent authorizes consolidation of this baseline, correction of the existing project background/repository documentation, and work on the existing feature branch. It does not authorize merging PR #1, changing main, or publishing a release.

## Domain lock boundary

The lock applies to top-level navigations initiated by links, page redirects, and browser controls. It is not a network firewall and does not block every third-party image, stylesheet, font, iframe, or other subresource. Sites may still make background requests to other hosts. Entering a new address intentionally establishes that host as the new locked root.

## Rendered HTML snapshot

The current Snapshot HTML action extracts `document.documentElement.outerHTML` from WebView and processes that rendered DOM with Jsoup. The Local Web action persists the neutralized result as an HTML revision and updates the HTML site/document indexes. It is not an original HTTP-response capture and does not create an immutable receipt. It replaces inline/external script elements, stores inline script source as escaped text, preserves external script URLs as links, converts inline on-event attributes to data attributes, converts noscript containers to ordinary div elements, and appends a provenance manifest.

The HTML snapshot includes a representation-status disclosure, provisional URL-derived prototype keys (not persistent identities), a representation-generated timestamp (not a receipt or capture time), and expandable records for original hyperlinks. The Save to Local Web action persists the script-neutralized rendered-DOM representation as an HTML revision. The browser creates a canonical per-host HTML site index, a document index, and a new revision file for each save. Site and document identifiers are stored in those HTML records; repeated saves of the same normalized URL reuse the identifiers and create a new revision. This first persistence slice does not yet correlate document identity across changed URLs. The saved revision is a derived rendered-DOM snapshot, not the original HTTP response; no original response body, response headers, or immutable remote-resource receipt is captured by this action. The local index and record navigation work offline, but complete page replay is not yet implemented: external dependencies may still be missing or requested remotely, and historical link resolution is not yet connected.\n\nThis is script neutralization for inspection, **not a complete HTML sanitizer**. It does not guarantee that every active HTML feature, embedded frame, remote resource, form, CSS behavior, or navigation mechanism is inert. Rendered snapshots are displayed with JavaScript disabled.

## Network and privacy

The app requests Internet access. Cleartext HTTP is enabled to support local development servers and HTTP sites; HTTP traffic is not encrypted, so use HTTPS where available. WebView Safe Browsing is enabled, file/content URL access is disabled, mixed content is blocked, and no JavaScript-to-native bridge is exposed.

The app does not currently implement download management, cookie controls, private browsing, content filtering, certificate pinning, or comprehensive subresource isolation.

## Build and verification

GitHub Actions runs testDebugUnitTest, lintDebug, and assembleDebug. A successful run uploads app-debug.apk as the non-headless-browser-debug-apk artifact for 14 days. The workflow does not publish a GitHub Release or signed production APK.

Requirements: Android 8.0 (API 26)+, Android SDK 34, JDK 17, and Gradle 8.7.

CI success does not establish physical-device installation or runtime compatibility; those require separate testing.
