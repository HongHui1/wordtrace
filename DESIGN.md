# Design System: WordTrace 1.2

The native Android app uses Material 3 DayNight with a quiet green palette. This revision simplifies the existing identity around one task: collect words, then save text.

## Color and typography

Resource tokens remain the source of truth: `values/colors.xml` and `values-night/colors.xml`. Primary is #286449 in light mode and #9ED4B1 in dark mode. Warm #F8F9F3 / charcoal #111813 surfaces use the existing ink and secondary-ink roles. Dialog surfaces follow the surface token. System status and navigation icons adapt to theme.

System sans type: brand 24sp bold; recording state and section titles 20sp bold; supporting copy 14sp; buttons 15sp; footer 12sp. All text uses sp. There is no oversized promotional headline.

## Main screen

One scrollable column, at most 760dp wide, centered on tablets. Page padding 24dp horizontal, 20dp top, 40dp bottom; system bars and cutouts get insets. A 24sp wordmark and Help action lead to a short explanation and a tonal recording panel. The panel has 20dp padding, 16dp corners, current state, brief instructions, and a 56dp primary Open ball button. End-and-save appears only while recording or saving.

Recognition settings are behind one entry: content, height range, ignored words. The visible summary states whether target words or all English are selected. A 1dp explicit-height divider separates setup from My word lists. History rows show date and unique word count, never frequency. Empty history names the next action.

Word-list dialogs contain search and alphabetically sorted words without counts. Export offers save TXT, share TXT, or copy words. TXT has one word per line, no header. Deletion requires confirmation and is disabled while recording. Old record contents are preserved.

## Floating control

36dp solid circle within a 48dp touch window, draggable. Tap opens the existing 176dp menu with start / pause / resume, end-and-save / close, and collapse. Actions collapse the menu; idle timeout is eight seconds. Play, dot, pause, and ring indicate ready, recording, paused, and saving. No English labels contaminate OCR. Buttons remain at least 48dp tall.

## Permissions and feedback

A single screen-sharing path. No accessibility service or capture-mode selector. Clear disclosure explains local processing and the conflict with system recording before the OS consent dialog. System sharing, permission, and save-file interfaces remain native. Settings take effect on the next recording; transient feedback uses snackbars.

## Removed complexity

Removed the oversized hero, permanently disabled End button, frequency labels, CSV choices, and two competing capture modes. Core recognition settings remain accessible rather than overwhelming the main screen. The low-level legacy record reader still supports old counts, but they are not treated as reliable product output.
