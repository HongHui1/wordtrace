---
name: WordTrace
description: Native Android companion for collecting English words during study.
colors:
  primary: "#286449"
  on-primary: "#FFFFFF"
  primary-container: "#DBEBDE"
  surface: "#F8F9F3"
  ink: "#1B211C"
  secondary-ink: "#525D54"
  outline: "#D5DDD3"
  dark-primary: "#9ED4B1"
  dark-on-primary: "#063923"
  dark-primary-container: "#244D37"
  dark-surface: "#111813"
  dark-ink: "#E2E9DF"
  dark-secondary-ink: "#B7C5B8"
  dark-outline: "#3D4C40"
typography:
  headline:
    fontFamily: "sans"
    fontSize: "30sp"
    fontWeight: 700
  brand:
    fontFamily: "sans"
    fontSize: "28sp"
    fontWeight: 700
  section:
    fontFamily: "sans"
    fontSize: "21sp"
    fontWeight: 700
  status:
    fontFamily: "sans"
    fontSize: "18sp"
    fontWeight: 700
  body:
    fontFamily: "sans"
    fontSize: "14sp"
  button:
    fontFamily: "sans"
    fontSize: "15sp"
  caption:
    fontFamily: "sans"
    fontSize: "12sp"
rounded:
  button: "12dp"
  container: "16dp"
spacing:
  tight: "4dp"
  small: "8dp"
  compact: "12dp"
  medium: "16dp"
  panel: "20dp"
  page: "24dp"
  section: "28dp"
  footer: "32dp"
  bottom: "40dp"
---

# Design System: WordTrace

## Overview

A quiet reading desk: warm neutral surfaces, ink-colored text, and deep green primary controls. The built interface uses native Android Java with Material 3 DayNight components, system sans typography, Chinese task labels, and no decorative imagery. Dark mode uses charcoal surfaces and pale green controls.

The central flow is to open the floating control, authorize screen sharing from its Start action, record while studying, end and save, then review and export the vocabulary list. This is an on-device OCR companion for Chinese-speaking learners using 不背单词, not an integration with that app.

## Colors

The frontmatter records `values/colors.xml` and `values-night/colors.xml`; `dark-*` denotes the corresponding night resource, not a separate UI role. Theme `Theme.Material3.DayNight.NoActionBar` selects these resources automatically.

Use `primary` / `on-primary` for the filled main action, `primary-container` / `ink` for the recording panel and floating control, and `surface` / `ink` for the page. `secondary-ink` supports explanatory text and the privacy footer. `outline` is the 1dp section divider. System bars follow the page surface, with light or dark icon appearance set by theme.

## Typography

Use Android system `sans` and sp sizing. The hierarchy is 30sp bold introductory headline, 28sp bold wordmark, 21sp bold section headings, and 18sp bold recording status. Supporting titles and the preference switch use 16sp; intro and main-screen buttons use 15sp; body copy uses 14sp; preference help uses 13sp; privacy and count explanations use 12sp. Text created by the main-screen helper adds 3dp line spacing. Native controls retain their own text metrics; buttons do not force uppercase.

## Layout

The main screen is one vertically scrolling column, centered with a maximum outer width of 760dp on tablets. Page padding is 24dp horizontally, 20dp above, and 40dp below. System-bar and display-cutout insets are applied to the scroll container. The reading order is header and help, introductory message, recording panel, recognition preferences, history, then privacy footer.

The recording panel has 20dp internal padding. Repeated gaps use the recorded 4–40dp spacing values: 8dp for close associations, 16dp around actions, and 24–28dp between larger sections. Dialog content uses 24dp horizontal padding. Main Material buttons and the search field have a 48dp minimum height; the preference switch uses 56dp and history actions 76dp. Floating actions are 96 × 48dp, with a status/drag target at least 48dp high.

Post-build UI review covered phone and tablet layouts, plus dark theme at font scale 1.3, and returned a ship disposition. This records visual review scope; compatibility with the user's installed 不背单词 version still requires device validation.

## Elevation & Depth

The main screen uses tonal grouping rather than custom shadows. The floating control has 4dp elevation to distinguish it from the underlying study app. Material dialogs and control interaction feedback retain native behavior.

## Shapes

Main-screen buttons use 12dp corners. The recording panel and floating control use 16dp corners. A single 1dp divider separates preferences from history. Inputs, switch, range slider, and dialogs retain Material shapes instead of custom decorative treatments.

## Components

- **Recording panel:** state text distinguishes ready, floating control ready, recording with word count, paused, and saving. The primary action opens the control or returns to study when it is open. End-and-save is enabled during an active session; saving disables the start action.
- **Floating control:** 112dp-wide vertical panel with 8dp padding, a 12sp Chinese status label, and 14sp native button labels. Actions change between 开始 / 暂停 / 继续 and 关闭 / 结束. Drag the status label to move; tap it to switch docking edge. Both buttons disable while saving. Status is also available through foreground notifications.
- **Preferences:** Material switch for large-text filtering, a range-slider dialog for screen-height percentages, and a multiline ignored-word field. Changes affect the next recording. Range values advance in 5% steps with at least 10% separation.
- **History:** text-based session actions show date, unique words, total occurrences, and relevant recording/recovery state. Empty history explains how to create the first record. Session dialogs provide search, a 240dp-high scrolling word list, export, and deletion with confirmation; active records cannot be deleted.
- **Permissions and feedback:** Material dialogs explain overlay permission and local screen processing before the Android consent flow. Snackbars acknowledge saves and settings or explain recoverable failures. Export offers CSV/TXT sharing and the system save-file picker.

## Do's and Don'ts

- **Do** keep Chinese task labels, explicit state text, scalable sp typography, and at least 48dp action targets.
- **Do** preserve the centered single-column layout and light/night semantic color pairing.
- **Do** describe counts as appearances: continuous visibility counts once, and same-screen duplicates count once.
- **Don't** imply guaranteed OCR accuracy, capture of protected screens, or an official 不背单词 integration.
- **Don't** add English labels to the floating control; its Chinese labels reduce interference with English OCR.
- **Don't** replace the native system consent, sharing, and file-saving flows with imitation screens.
