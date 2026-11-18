# Local Web, Historical Navigation, and HTML Layout Architecture

**Status:** User-submitted project baseline, consolidated and locked as the governing design direction on 2026-11-18; implementation remains staged and incomplete.
**Scope:** Browser-wide architecture for the local counterpart of each visited domain.
**Working labels:** “HTML Layout Protocol” (HLP) and “Local Web Address and Allocation Protocol” (LWAP) are names used in earlier assistant-authored drafts only. They are not existing standards and are not independently approved requirements.

## 0. User-submitted baseline and authority

This section records the user's submissions and corrections. It takes precedence over assistant-generated examples, terminology, implementation suggestions, and earlier drafts wherever they conflict.

### Governing requirements

1. The browser is both a means of visiting the live web and a locally navigable, durable record of the web it has received.
2. **HTML is the basis of the product.** The user's explicit correction is that all of the following are HTML: actual resource receipt records; the representation and organization of original resources; rendered snapshots; derived representations; persistent site and document identities; each domain's canonical local website; local navigation; and local storage organization. Browser foundations, including settings, storage, and navigation, are also to be built from, by, and for HTML. These are not separate product subsystems presented as independent archive/database/file-manager products.
3. Each domain has one canonical local HTML website, interconnected through its pages, resources, records, and links.
4. Each received resource has an actual, immutable receipt record. Original resource material, its receipt record, a rendered snapshot, and any rewritten, compiled, or reconstructed derivative are distinguishable and related in HTML.
5. Rewritten or compiled HTML refers to original receipt records through compact escape-code identifiers rather than duplicating original material unnecessarily.
6. Site and document identities are persistent. They do not depend on the current remote URL, current page layout, or physical storage placement.
7. A visited or downloaded website should remain locally reviewable and navigable offline. Page changes accumulate as records/revisions; local Back/history navigation must not depend on the remote server.
8. Preserve what was legitimately delivered to the browser while respecting access controls and applicable restrictions. Record incomplete, blocked, dynamic, missing, or partly reproducible material honestly.
9. The browser's own receipts and local history are its own record. Internet Archive/Wayback research is not authorization to import, incorporate, synchronize, or integrate Wayback records. No Wayback integration is part of this agreed product direction.
10. The user controls retention, review, and revisiting. Assistant proposals are not product decisions unless the user accepts them.

### Original media submissions and later refinement

- An earlier media submission specified retaining original images alongside SVG/JPEG derivatives; video was to be embedded inline, with conversion deferred.
- A later media-design direction described using original media as reconstruction input, allowing the original payload to be discarded after successful verification while preserving its immutable receipt and evolving remote-retrieval record. A failed or unverified reconstruction must not be treated as successful.
- The user also proposed discovering repeated/shared structures across sampled media and storing reusable patterns once, referenced by timing, placement, transformation, and other parameters.
- These dated directions are preserved together. Do not silently collapse them into a universal retention rule for every media type; apply the specific later direction only where it clearly governs, and preserve any unresolved retention choice as unresolved.

### Consent and scope

The user has explicitly consented to technical review, correction of the Android source, preparation of a working APK, and the previously identified paired deliverables: (1) a working HTML prototype and (2) the HTML re-encoder upgrade. In the current instruction, the user has also authorized consolidating this baseline, correcting the project background and repository documentation accordingly, and locking the resulting user-submitted requirements as the governing design baseline.

This consent applies to the existing project record and the current working branch. It does not authorize merging the pull request, changing `main`, publishing or distributing a release, changing account permissions, or adding unrelated features. Those remain separate actions requiring their own authorization.

### What is implemented versus intended

The current Android application is an early WebView browser with rendered-DOM snapshot extraction, HTML re-encoding for inspection, JavaScript disabled by default, and a top-level navigation domain lock. A rendered-DOM snapshot is not an original HTTP-response receipt. The current application does not yet provide the persistent HTML receipt system, canonical per-domain local website, persistent identities, complete local storage, or offline local-web navigation described above. Do not describe intended architecture as completed functionality.

## 1. Governing idea

The browser is both a way to visit the live web and a locally navigable record of the web it has received.

Each domain receives one canonical local website inside the browser. That local website is an interconnected set of HTML documents and resources, rooted in one canonical site document. It is not an impersonation of the remote origin. It is the browser's own, clearly identified representation of material legitimately delivered to it.

**HTML is the product's representational and organizing basis, not merely the language used to display pages.** Receipt records, original-resource representation, rendered snapshots, derived representations, persistent identities, canonical local websites, navigation, and storage organization belong to the HTML system. Rewritten HTML carries or references the information needed to inspect, navigate, reconstruct, and understand the local web.

The user should not have to leave the page to reach a separate archive manager or history tool. The page's own rewritten HTML provides these capabilities inline.

## 2. One local website per domain

A domain has one stable local site identity and one canonical entry point. Its pages, revisions, resources, indexes, and records are interconnected through local hypertext.

Illustrative structure:

```text
Local Web
└── example.com
    ├── Site.html                 canonical site entry point
    ├── Home.html
    ├── About.html
    ├── Articles/
    ├── Resources/
    ├── History/
    ├── Layouts/
    └── Address-Index.html
```

This is a logical HTML website, not a requirement that every payload be stored as a separate user-visible file. Physical storage may use shared resources, chunks, and indexes while the user encounters one coherent local website.

The local site remains available offline. It can also offer an explicit route to request the current live page. Local and remote identities remain distinct.

## 3. Rewritten HTML is the complete navigable representation

For each received page, preserve the original receipt and create a rewritten HTML representation. The rewritten document enriches the page without silently changing the meaning of its original content or links.

It can contain or reference:

- Stable local site, document, and revision identities.
- Original URL, final URL, redirects, capture time, and relevant response metadata.
- The original link text, destination, position, and surrounding context.
- Historical captures of each destination, where known.
- Current remote destination, where applicable.
- Local reconstruction address and availability.
- Resource dependencies, provenance, integrity information, and missing-resource notices.
- Layout choices and revision history.
- Browser-generated technical observations, distinguished from source content and user annotations.

The original HTML remains a distinct preserved receipt. Rewritten HTML is a derived representation and must link back to the original receipt. It must not pretend that browser-generated material came from the website.

## 4. Hyperlinks are historical navigation records

A hyperlink is more than a URL. It has original meaning and context, and it may have multiple possible destinations across time.

**HLP principle: Every hyperlink is a navigable historical record, not merely a pointer to another URL.**

The ordinary page remains readable. An inline HTML dropdown or expandable section can expose the link's historical and local information. The browser does not need to replace every visible link label; it can add a native disclosure control adjacent to the link or provide an integrated link-details view.

Illustrative HTML:

```html
<details class="link-history">
  <summary>Historical versions of this destination</summary>
  <ul>
    <li>
      <a href="local://site-id/document-id/revision-id">
        2004 capture — About this page
      </a>
      <p>
        Captured: 2004-06-18.
        Original destination: https://example.com/about
        Local record: available.
        Historical resources: 12 available; 2 missing.
      </p>
    </li>
    <li>
      <a href="local://site-id/document-id/revision-id">
        2008 capture — About this page
      </a>
      <p>
        Captured: 2008-09-03.
        Original destination: https://example.com/about
        Local record: available.
        Historical resources: 18 available; none known missing.
      </p>
    </li>
    <li>
      <a href="https://example.com/about">
        Current live page
      </a>
      <p>Requests the current remote representation.</p>
    </li>
  </ul>
</details>
```

All addresses and dates above are illustrative. The local URI syntax is a placeholder until the local addressing protocol is specified.

Each link record should retain, where available:

1. Original displayed text and destination.
2. Original document identity and link location/context.
3. Historical destination identity and capture candidates.
4. The exact historical revision selected for navigation.
5. Current live destination, clearly distinguished from historical copies.
6. Local availability, completeness, and provenance.
7. Missing, uncertain, or inferred relationships.

A page captured in 2004 should normally resolve its historical links against the best corresponding historical representations, not silently send the user to today's pages. Where no corresponding capture exists, say so. An approximate/nearest capture may be offered only when clearly labelled as approximate and dated.

## 5. Historical page sets and honest replay

A historical page is not just its top-level HTML. It is a page-set: the document plus the resources and relationships used to render it.

A page-set manifest is itself an HTML document. It records:

- The page revision and its capture timestamp.
- Each dependency and its own capture timestamp.
- Original and final resource URLs where known.
- Which resources are locally present, shared, reconstructed, missing, blocked, or unavailable.
- Any differences between the page timestamp and dependency timestamps.
- Whether the view is an exact capture, a partial replay, or an approximate reconstruction.

A capture may have dependencies collected at different times. Preserve those individual times rather than falsely presenting the bundle as perfectly simultaneous.

**Never silently substitute a live resource or a different historical date into a historical page.** If the exact resource is missing, mark it missing. If the user chooses an approximation, disclose the substitution in the page itself.

General research about historical replay illustrates that page dependencies can be missing and fallback behavior can obscure gaps. This browser's own records must disclose missing material and must not invent historical records or dates. This principle does not introduce Wayback as a source or product integration.

## 6. Time and revision model

Keep separate concepts separate:

- **Received time:** when this browser received the resource.
- **Server-reported time:** a timestamp supplied by the remote system, if any.
- **Capture time:** when a local capture operation occurred.
- **First/last observed:** the browser's observation interval.
- **Revision time:** when the local representation was generated or updated.
- **Historical target time:** the time the user wants to inspect.

A page revision is addressable and immutable once committed as a historical record. The current-page pointer may advance, but earlier revisions are not overwritten.

A revision should identify its source receipt, transformation/reconstruction process, dependencies, and completeness. Changes to content, resource mappings, layout definitions, or reconstruction algorithms should be documented as revisions.

## 7. HTML Layout Protocol (HLP)

HLP is a browser-wide convention implemented by the browser, not a new markup language and not an existing standards-track protocol.

Its purpose is to make HTML the shared container and connective structure for the local web:

| Concern | HTML-first representation |
|---|---|
| Identity | HTML metadata and stable local identifiers |
| Document structure | Semantic HTML |
| Navigation | Hyperlinks and native disclosure elements |
| Link history | Inline HTML link records and historical choices |
| Layout | Embedded `<style>` rules and HTML layout metadata |
| Graphics | Inline SVG within HTML |
| Revisions | HTML revision records and links |
| Provenance | HTML receipt and transformation records |
| Resource relationships | HTML links and compact resource references |
| Address directory | HTML tables/indexes |
| Storage visualization | Inline SVG allocation maps |
| Completeness | HTML status records and visible explanations |

HTML remains the document and semantic structure. CSS may be embedded in HTML for presentation. SVG may be embedded in HTML for scalable graphical representation. The browser should not introduce a separate proprietary document format for metadata, layout, history, or indexes.

The user's explicit requirement is that the local browser system described here—including receipts, resource representation and relationships, snapshots, derivatives, identities, navigation, and storage organization—is HTML. Apply the user's specific media submissions within that HTML-based system; do not replace the agreed product architecture with separate non-HTML product surfaces or assume an unapproved alternative representation.

### Layout independence

Content identity must not depend on its current layout. A page may offer:

- Original-like layout.
- Compact layout.
- Reading layout.
- Media-focused layout.
- Accessibility-oriented layout.
- User-adapted layout.

Changing layout must not change the identity of the underlying page revision or duplicate its content. Layout definitions and changes are themselves addressable and historically documented.

## 8. Local addressing and storage

The local site needs stable logical addresses independent of remote URLs and physical storage placement.

Three address layers remain distinct:

1. **Remote address:** the URL used to request a resource from the web.
2. **Local logical address:** the stable identity of the browser's local representation.
3. **Physical storage address:** where the bytes currently reside on the device.

The local logical address must survive remote URL changes, page revisions, deduplication, and physical relocation.

LWAP is a working proposal for browser-internal addressing and allocation. Its concepts include:

- Stable site, document, resource, and revision identifiers.
- Logical extents/chunks with byte-level addressability.
- Sparse pre-addressed ranges, allocated as content arrives.
- Shared storage for identical resources, with multiple logical references.
- Append-first allocation and atomic index updates.
- Integrity checks and resumable writes.
- An HTML address directory mapping logical addresses to documents, resources, and extents.
- An optional inline SVG map for visualizing allocation.

The HTML index is a readable view of the address directory, not a requirement to create one metadata record per byte. Fixed-size physical allocation units and variable logical content boundaries are separate design decisions.

Local addresses are resolved internally. LWAP identifiers are never sent to remote websites and do not replace HTTPS, TLS, or remote-origin semantics.

## 9. Resource receipts, reconstruction, and media

Every resource legitimately received should have a lightweight, immutable receipt record. Keep source identity, receipt facts, transformations, and current retrieval knowledge distinct.

For resources that are reconstructed:

- Preserve the receipt and provenance record.
- Keep a record of the original remote locator, final URL, redirect history, receipt time, MIME type, size, checksum when available, referring page, and relevant response metadata.
- Keep the reconstruction identity, algorithm/version, verification result, and local address.
- Allow the remote retrieval record to evolve as locations move or retrieval attempts succeed/fail.
- Do not imply that future retrieval is guaranteed; remote servers can remove, restrict, or relocate resources.
- Where reconstruction is verified, the reconstructed representation may replace the original payload in normal local use. The original payload need not be kept permanently unless the user chooses to retain it.
- Do not discard temporary source data after a failed or unverified reconstruction if it is needed to retry or explain the failure.

For graphical media, the design direction is SVG-based graphical representation in the browser's displayed local experience. Raster source media may be temporary reconstruction input and may be discarded after successful verification according to the retention policy. SVG itself does not guarantee smaller storage or photographic fidelity; actual reconstruction and compression must be benchmarked on the target Android device.

Audio remains encoded audio data decoded by a native media engine. SVG can provide waveforms and visual timelines, but does not decode arbitrary audio codecs. Media reconstruction should identify repeated structures and store shared patterns once, referencing them with timing, placement, transformation, and other parameters. Audio timing must be sample-accurate and need not align to video frame boundaries.

## 10. Provenance and evidence boundaries

Keep these categories distinct in the local HTML:

1. **Source content:** material received from the remote resource.
2. **Browser observations:** requests, responses, redirects, cookies, resource use, measured behavior, and capture limitations.
3. **Browser transformations:** rewritten HTML, local URL mapping, reconstruction, layout, and local substitutions.
4. **User annotations and preferences:** private user-authored material.

Browser observations should say what was observed and how it is known. Distinguish measured facts from inferences and unverifiable claims. The browser must not speak for the user or treat its own technical commentary as source content.

A preserved page should disclose whether it is complete, partial, blocked, dynamic, reconstructed, or dependent on unavailable resources. Preservation does not depend on a website approving the act of preserving material already legitimately delivered, but the browser must respect access controls and applicable restrictions.

## 11. Internet Archive and Wayback research

Internet Archive/Wayback material in this document is research context only. The user has explicitly said that incorporating Wayback records is not part of this project direction. No import, incorporation, synchronization, or runtime integration of Wayback records is authorized or planned by this baseline.

Relevant mechanisms and references:

- [Wayback Machine Help Center](https://help.archive.org/help/using-the-wayback-machine/) — historical page lookup, Save Page Now, and known replay limitations.
- [Wayback CDX Server API](https://github.com/internetarchive/wayback/tree/master/wayback-cdx-server) — capture indexes with URL, timestamp, MIME type, status, digest, and length fields.
- [Memento: Time-Based Access to Resource States (RFC 7089)](https://www.rfc-editor.org/rfc/rfc7089) — time negotiation, Mementos, TimeMaps, and related concepts.
- [Webrecorder pywb](https://pywb.readthedocs.io/) — WARC-based replay and rewriting of URLs in archived HTML, CSS, and HTTP headers.
- [HTML Living Standard](https://html.spec.whatwg.org/) — native HTML document, hyperlink, and resource model.
- [CSS specifications](https://www.w3.org/Style/CSS/) — presentation and layout mechanisms.

Research-derived lessons:

- A saved top-level page does not guarantee that all dependencies were captured.
- JavaScript-generated navigation and server-dependent interactions may not replay faithfully.
- URL rewriting is central to replaying archived content without sending the user to the wrong origin.
- Historical replay needs explicit date/resource provenance and honest missing-resource handling.
- The browser's own archive should not silently fall back to live content when a historical dependency is unavailable.

These sources are research references only. They do not create a product dependency or authorize incorporation of Wayback records. HLP and LWAP remain assistant-used working labels, not existing standards and not independent user-approved requirements.

## 12. Security and execution boundary

- Remote JavaScript remains disabled by default.
- Script and event-handler content is represented as inspectable HTML metadata where possible.
- A browser-controlled Commit / Actions / Events interface may invoke selected, explicitly approved actions through controlled native mechanisms.
- A site cannot silently promote displayed content into executable authority.
- Local archived HTML is not automatically trusted merely because it is stored locally.
- The local site's trusted origin and the untrusted remote page origin must remain separate.
- Do not expose a privileged native bridge to arbitrary remote content.
- No automatic form submission, purchase, credential use, or other consequential transaction.
- Browser-generated technical reports remain separate from private user records and are not transmitted externally without separate authorization.

## 13. Implementation status and staged path

This document records architecture, not completed implementation. The current Android app is an early WebView browser with HTML capture and script/event-handler neutralization. It does not yet implement the local website, persistent capture store, temporal resolver, HLP, LWAP, full resource capture, or media reconstruction.

Suggested staged work:

1. **HTML document model:** define site/document/revision identities and receipt records.
2. **Local site root:** create one local HTML entry point per domain and a minimal local resolver.
3. **Rewritten document:** generate a self-describing HTML page with original-link preservation and inline link-history disclosure.
4. **Revision and page-set records:** capture dependencies, timestamps, completeness, and explicit missing-resource states.
5. **Historical resolver:** navigate to exact local historical revisions; never silently substitute live content.
6. **HTML index and allocation model:** prototype stable logical addresses and an HTML address directory.
7. **Layout protocol:** define metadata conventions and multiple layouts without duplicating page identity.
8. **Media reconstruction:** prototype on representative content and benchmark fidelity, storage, processing time, memory, and device thermals.
9. **Security and privacy review:** validate local/remote origin separation, permissions, user consent, and action execution boundaries.

Each stage should be separately testable. No stage should be described as implemented until source changes and verification demonstrate it.

## 14. Acceptance principles

The architecture is consistent with the user's established direction only if:

- One local website exists per domain, with one canonical HTML entry point.
- Rewritten HTML carries or references the information needed to understand the page and its links.
- Historical choices appear within the page's own HTML interface.
- Historical links resolve to historical local representations when available.
- Missing or approximate historical material is disclosed, never silently substituted.
- Original receipts, derived representations, and user annotations remain distinguishable.
- Logical local addresses survive physical storage changes and remote URL changes.
- The complete local-web system described in the user's requirements is HTML-based, including receipt records, original-resource representation, snapshots, derivatives, identities, navigation, and storage organization; SVG and media-specific treatments follow the user's submitted media requirements.
- Local records remain available offline without depending on the Internet Archive.
- Security, consent, and provenance boundaries remain explicit.
