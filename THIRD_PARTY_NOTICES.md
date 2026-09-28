# Third-party dependencies

The MIT license applies to WordTrace's own source code only.

- Android SDK / build tools: Android SDK License Agreement, https://developer.android.com/studio/terms
- AndroidX AppCompat, Core and transitive AndroidX libraries: Apache License 2.0, https://source.android.com/docs/setup/about/licenses
- Material Components for Android: Apache License 2.0, https://github.com/material-components/material-components-android
- Google ML Kit Text Recognition (bundled Latin model), and its transitive Google libraries: governed by their respective licenses and the ML Kit terms, https://developers.google.com/ml-kit/terms ; usage documentation https://developers.google.com/ml-kit/vision/text-recognition/v2/android . Bundling the model does not relicense it as MIT.
- JUnit (test only): Eclipse Public License 1.0, https://junit.org/junit4/
- Gradle Wrapper: Apache License 2.0, https://github.com/gradle/gradle

Dependency versions are pinned in `app/build.gradle` and the root build files. Embedded META-INF notices from dependencies are retained by the Android build defaults.

## CMU Pronouncing Dictionary (CMUdict)

WordTrace 1.2.0 bundles 125,086 unique lowercase word forms extracted from `cmudict.dict`; pronunciation data and alternate-pronunciation suffixes are not included. Used locally to validate OCR words and reassembled fragments.

Source: https://github.com/cmusphinx/cmudict (retrieved 2026-09-28).
Source dictionary SHA-256: `81917843c7f44ce2b094ac63873c2c7a4cf802040792c455ba3ca406891c3d22`.
The full copyright notice, redistribution conditions, and disclaimer ship in `app/src/main/assets/CMUDICT-LICENSE.txt` (also inside the APK). This dictionary retains its own BSD-style license; the project MIT license does not replace it.
