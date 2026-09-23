# WordTrace
<!-- impeccable:product-schema 1 -->

## Platform
android

## Users
Chinese-speaking learners using 不背单词 on Android phones and tablets.

## Product Purpose
Start recording from a floating control, collect English words seen while studying, then stop and receive a file with occurrence counts.

## Capabilities and Constraints
Installable APK, Chinese default UI, source suitable for public GitHub hosting. User confirmed 不背单词 as initial target. OCR runs on device. Android 11+ defaults to a screenshot-only accessibility service enabled by the user, avoiding replacement of the system recording projection. Older systems retain explicitly authorized MediaProjection with a conflict warning. The floating control is a small draggable ball. Protected screens cannot be captured. Continuous visibility is one exposure, not one count per frame.

## Stack
Implementation choice: native Android Java, Material components, bundled ML Kit Latin OCR. No pre-existing source or visual assets.

## Brand Commitments
Name: wordtrace / WordTrace. Default Chinese interface. English interface is optional.

## Open Decisions
User phone: vivo X100s, reportedly on the latest system; exact Android / OriginOS build is unknown. Compatibility with the user's installed 不背单词 version requires device validation. Public repository: https://github.com/HongHui1/wordtrace.
