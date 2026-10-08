# WordTrace
<!-- impeccable:product-schema 1 -->

## Platform
android

## Users
Chinese-speaking learners using 不背单词 on Android phones and tablets. User phone: vivo X100s, current OriginOS / Android build not specified.

## Product Purpose
Accurately collect the large target vocabulary word while studying and export a simple, unique list as plain TXT. A small draggable floating ball starts and stops collection.

## Capabilities and Constraints
Chinese UI, directly installable APK, public GitHub source. User explicitly prioritizes word recognition over recording coexistence and frequency statistics. Version 1.2.0 removes accessibility capture and frequency UI, uses one MediaProjection path, and warns before starting that system recording may conflict. Target words are the confirmed default. OCR glyph geometry, an offline lexicon, and consecutive-frame confirmation reduce fragments; unknown or single-letter words and fast page transitions may be missed. No screenshot storage or network permission.

## Stack
Native Android Java, Material 3 DayNight, bundled ML Kit Latin OCR, CMUdict-derived word forms. Local atomic sessions, deduplicated TXT export. Old records remain readable without rewriting their historical counts.

## Brand Commitments
wordtrace / WordTrace, Chinese default, quiet green palette, native controls, compact UI. Core path: small ball → study → finish → text.

## Open Decisions
Version 1.3.0 was tested on actual 不背单词 5.11.6 in MuMu Android 12. Capture processing runs in the background; target mode requires a dominant English heading, excluding measured home menus and vocabulary lists. The small ball docks and remembers its side, with a two-row menu; isolated consent returns directly to the learning app. Actual vivo behavior must still be confirmed on the user's hardware. Public repository: https://github.com/HongHui1/wordtrace.
