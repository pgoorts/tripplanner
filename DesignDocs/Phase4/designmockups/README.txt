TripPlanner Phase 4 Mockups
===========================

These are Android UI mockups (static, illustrative — not pixel-perfect) for the seven new/changed
screens/states Phase 4 introduces, per description_detail.txt and datastructure.txt.

Redone per developer request: generated entirely locally as flat PNG files, with nothing uploaded
or published anywhere (the earlier version of this folder used the /design skill's canvas, which
publishes a viewable link on claude.ai — that approach has been discarded). This version instead
uses a small Python script (Pillow, already available in this environment) that draws each screen
directly onto a bitmap and saves it as a PNG — no network calls, no external service, no exported
link. The generator script is not part of this folder (it lived in the session's scratch directory);
these PNGs are the deliverable.

Colors and type are read from the app's real theme source (Color.kt/Type.kt): the dark navy/teal
Material3 palette (#0D1526 background, #1ABC9C/#16A085 teal primary, category chip colors, etc.)
is reproduced exactly. The app's actual typeface (Outfit, loaded from Google Fonts at runtime) is
not installed in this environment, so DejaVu Sans (bundled locally) is used as a stand-in — a
comparable geometric-humanist sans, close enough for layout/spacing purposes. Icons are simplified
line-drawn approximations of the app's Lucide-style icons (globe for timezone, plane for flights,
warning triangle, document/folder for files, etc.), not the exact vector glyphs.

Screens:
1. Home.png                   - Home screen with real trip cover photos: a "Current Trips" card
                                 with an auto-fetched destination photo (illustrated stand-in —
                                 dusk skyline silhouette), and a "Past Trips" card falling back to
                                 a built-in illustration (no photo resolved).
2. AddTripPhoto.png           - Add Trip dialog with the new cover-photo section: auto-resolved
                                 photo preview, "Change photo" button, and a caption explaining
                                 the automatic-fetch + manual-override behavior.
3. TripSettingsDates.png      - Trip Settings expanded into a full screen: editable Start/End
                                 date fields (new) above the existing default-timezone setting,
                                 plus a cover-photo override control at the bottom.
4. AddEventTimezones.png      - Add Event form for Flight, extended with the new dual-timezone
                                 fields (separate Departure timezone / Arrival timezone rows)
                                 alongside Phase 3's existing flight number/airport code fields.
5. InvalidEvent.png           - An "invalid" event: its itinerary list card with a distinct
                                 warning badge ("Outside trip dates"), and the Opened Event
                                 screen's inline fix-it banner with two actions (edit the event's
                                 dates, or edit the trip's dates).
6. AddNoteFile.png            - Add Note flow: the type picker with the new "File" entry added,
                                 and the Link type's expanded state showing both the existing URL
                                 field and the new "Pick from Drive" button with its
                                 single-device-only caption.
7. FileDriveNote.png          - Two Opened Note states: a generic File note (filename, file-type
                                 icon, size, an Open button), and a Drive-picker note opened on a
                                 non-originating device, showing a clear "not available here"
                                 message instead of a broken link.

Each PNG is a self-contained flat image (phone-frame or stacked-panel layout matching the
scope of the equivalent screen), viewable directly in any image viewer or file browser — no
special tooling, canvas, or published link needed.
