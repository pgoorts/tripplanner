TripPlanner Phase 3 Mockups
===========================

These are Android UI mockups (static, illustrative — not pixel-perfect) for the seven new/changed
screens introduced in Phase 3, per description_detail.txt. They cover the same "what will it look
like" purpose as Phase 1's designmockups/*.jpg, just produced with a different tool (Phase 1 used
a direct image generator; this session's environment did not have one, so an interactive design
canvas was used instead — see below).

Published, viewable canvas (all 7 screens laid out side by side, exportable as PNG/PDF):
https://claude.ai/code/artifact/4d0c8789-3d9d-4137-be18-ffe875e3c4e2

Screens (left to right on the canvas):
1. Main.dc.html          - Global Settings screen (merged Profile + default timezone + sync interval)
2. TripSettings.dc.html  - Per-trip settings panel (trip-level default timezone override)
3. OpenedTrip.dc.html    - Opened Trip screen with the new sync status bar, plus a delete-confirmation dialog overlay
4. AddNote.dc.html       - Add Note flow: type picker (incl. new "Pass"), the Pass file-picker state, and the simplified single-URL-paste Link state
5. PkpassView.dc.html    - Opened Pkpass note: wallet-style rendering with barcode and pass fields
6. AddEventFlight.dc.html- Add Event form for the Flight category, with relabeled departure/arrival fields
7. EventListFlight.dc.html - Trip event list showing the custom Flight card next to generic event cards, including the split departure/arrival-day card

The .dc.html files in this folder are the mockups' source (plain HTML/CSS fragments matching the
app's real Material3 color palette and Outfit typeface, taken from Color.kt/Type.kt) and
canvas.json is their layout manifest — both are what's published at the link above. They render
correctly only inside that canvas viewer, not as standalone web pages.

To get flat PNG images (matching the Phase 1 folder's format), open the link above and use its
Export PNG (per screen) or Export PDF (all screens) function.
