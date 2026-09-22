# Third-party dependencies

The MIT license applies to WordTrace's own source code only.

- Android SDK / build tools: Android SDK License Agreement, https://developer.android.com/studio/terms
- AndroidX AppCompat, Core and transitive AndroidX libraries: Apache License 2.0, https://source.android.com/docs/setup/about/licenses
- Material Components for Android: Apache License 2.0, https://github.com/material-components/material-components-android
- Google ML Kit Text Recognition (bundled Latin model), and its transitive Google libraries: governed by their respective licenses and the ML Kit terms, https://developers.google.com/ml-kit/terms ; usage documentation https://developers.google.com/ml-kit/vision/text-recognition/v2/android . Bundling the model does not relicense it as MIT.
- JUnit (test only): Eclipse Public License 1.0, https://junit.org/junit4/
- Gradle Wrapper: Apache License 2.0, https://github.com/gradle/gradle

Dependency versions are pinned in `app/build.gradle` and the root build files. Embedded META-INF notices from dependencies are retained by the Android build defaults.
