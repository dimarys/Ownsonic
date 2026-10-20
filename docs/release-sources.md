# Release source access

Ownsonic release `v1.0.0` includes the signed APK, an application source archive, a dependency source archive, and SHA-256 checksums. The application source archive is exported from the same tagged commit as the APK. It includes the build scripts and artwork sources but excludes private signing keys, local configuration, downloaded media, and Git history.

## Build Ownsonic

Use the checked-in Gradle wrapper, Java 25, and Android SDK 37. Follow the root README to build a debug APK or configure your own release signing key and run `./gradlew assembleRelease`. Dependencies resolve from Google Maven and Maven Central using the versions in the build files. The dependency archive provides the published library source packages for inspection and modification; it is not an offline Maven repository.

## Upstream source and build infrastructure

- AndroidX: https://android.googlesource.com/platform/frameworks/support
- Media3 1.8.0: https://github.com/androidx/media/tree/1.8.0
- Kotlin 2.2.10: https://github.com/JetBrains/kotlin/tree/v2.2.10
- Kotlin coroutines 1.10.2: https://github.com/Kotlin/kotlinx.coroutines/tree/1.10.2
- Kotlin serialization 1.7.3: https://github.com/Kotlin/kotlinx.serialization/tree/v1.7.3
- Guava 33.3.1: https://github.com/google/guava/tree/v33.3.1
- JetBrains annotations: https://github.com/JetBrains/java-annotations
- JSpecify 1.0.0: https://github.com/jspecify/jspecify/tree/v1.0.0

Graphics Path's Maven source archive omits native implementation files. The dependency archive therefore also contains its module source and build file, including native implementation files from AndroidX revision `8a05a22af450d589ef911d772a001a49dcb05b71`, linked by the official 1.0.1 release notes. The complete upstream repository and build infrastructure at that revision are available at https://android.googlesource.com/platform/frameworks/support/+/8a05a22af450d589ef911d772a001a49dcb05b71.

## Runtime source packages

These are the source package URLs for the exact resolved versions. The dependency release archive also includes `dependency-sources.csv` with local archive paths and SHA-256 checksums. The empty Guava `listenablefuture:9999.0-empty-to-avoid-conflict-with-guava` compatibility artifact has no implementation code or published source archive.

| Component | Published source |
| --- | --- |
| `androidx.activity:activity-compose:1.8.2` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/1.8.2/activity-compose-1.8.2-sources.jar) |
| `androidx.activity:activity-ktx:1.8.2` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/activity/activity-ktx/1.8.2/activity-ktx-1.8.2-sources.jar) |
| `androidx.activity:activity:1.8.2` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/activity/activity/1.8.2/activity-1.8.2-sources.jar) |
| `androidx.annotation:annotation-experimental:1.4.1` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/annotation/annotation-experimental/1.4.1/annotation-experimental-1.4.1-sources.jar) |
| `androidx.annotation:annotation-jvm:1.9.1` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/annotation/annotation-jvm/1.9.1/annotation-jvm-1.9.1-sources.jar) |
| `androidx.arch.core:core-common:2.2.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/arch/core/core-common/2.2.0/core-common-2.2.0-sources.jar) |
| `androidx.arch.core:core-runtime:2.2.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/arch/core/core-runtime/2.2.0/core-runtime-2.2.0-sources.jar) |
| `androidx.autofill:autofill:1.0.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/autofill/autofill/1.0.0/autofill-1.0.0-sources.jar) |
| `androidx.collection:collection-jvm:1.5.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/collection/collection-jvm/1.5.0/collection-jvm-1.5.0-sources.jar) |
| `androidx.collection:collection-ktx:1.5.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/collection/collection-ktx/1.5.0/collection-ktx-1.5.0-sources.jar) |
| `androidx.compose.animation:animation-android:1.10.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/animation/animation-android/1.10.4/animation-android-1.10.4-sources.jar) |
| `androidx.compose.animation:animation-core-android:1.10.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/animation/animation-core-android/1.10.4/animation-core-android-1.10.4-sources.jar) |
| `androidx.compose.foundation:foundation-android:1.10.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/foundation/foundation-android/1.10.4/foundation-android-1.10.4-sources.jar) |
| `androidx.compose.foundation:foundation-layout-android:1.10.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/foundation/foundation-layout-android/1.10.4/foundation-layout-android-1.10.4-sources.jar) |
| `androidx.compose.material3:material3-android:1.4.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/material3/material3-android/1.4.0/material3-android-1.4.0-sources.jar) |
| `androidx.compose.material:material-ripple-android:1.10.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/material/material-ripple-android/1.10.4/material-ripple-android-1.10.4-sources.jar) |
| `androidx.compose.runtime:runtime-android:1.10.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/runtime/runtime-android/1.10.4/runtime-android-1.10.4-sources.jar) |
| `androidx.compose.runtime:runtime-annotation-android:1.10.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/runtime/runtime-annotation-android/1.10.4/runtime-annotation-android-1.10.4-sources.jar) |
| `androidx.compose.runtime:runtime-retain-android:1.10.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/runtime/runtime-retain-android/1.10.4/runtime-retain-android-1.10.4-sources.jar) |
| `androidx.compose.runtime:runtime-saveable-android:1.10.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/runtime/runtime-saveable-android/1.10.4/runtime-saveable-android-1.10.4-sources.jar) |
| `androidx.compose.ui:ui-android:1.10.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-android/1.10.4/ui-android-1.10.4-sources.jar) |
| `androidx.compose.ui:ui-geometry-android:1.10.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-geometry-android/1.10.4/ui-geometry-android-1.10.4-sources.jar) |
| `androidx.compose.ui:ui-graphics-android:1.10.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-graphics-android/1.10.4/ui-graphics-android-1.10.4-sources.jar) |
| `androidx.compose.ui:ui-text-android:1.10.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-text-android/1.10.4/ui-text-android-1.10.4-sources.jar) |
| `androidx.compose.ui:ui-tooling-preview-android:1.10.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-tooling-preview-android/1.10.4/ui-tooling-preview-android-1.10.4-sources.jar) |
| `androidx.compose.ui:ui-unit-android:1.10.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-unit-android/1.10.4/ui-unit-android-1.10.4-sources.jar) |
| `androidx.compose.ui:ui-util-android:1.10.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-util-android/1.10.4/ui-util-android-1.10.4-sources.jar) |
| `androidx.concurrent:concurrent-futures-ktx:1.1.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/concurrent/concurrent-futures-ktx/1.1.0/concurrent-futures-ktx-1.1.0-sources.jar) |
| `androidx.concurrent:concurrent-futures:1.1.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/concurrent/concurrent-futures/1.1.0/concurrent-futures-1.1.0-sources.jar) |
| `androidx.core:core-ktx:1.16.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/core/core-ktx/1.16.0/core-ktx-1.16.0-sources.jar) |
| `androidx.core:core-viewtree:1.0.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/core/core-viewtree/1.0.0/core-viewtree-1.0.0-sources.jar) |
| `androidx.core:core:1.16.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/core/core/1.16.0/core-1.16.0-sources.jar) |
| `androidx.customview:customview-poolingcontainer:1.0.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/customview/customview-poolingcontainer/1.0.0/customview-poolingcontainer-1.0.0-sources.jar) |
| `androidx.documentfile:documentfile:1.0.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/documentfile/documentfile/1.0.0/documentfile-1.0.0-sources.jar) |
| `androidx.dynamicanimation:dynamicanimation:1.0.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/dynamicanimation/dynamicanimation/1.0.0/dynamicanimation-1.0.0-sources.jar) |
| `androidx.emoji2:emoji2:1.4.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/emoji2/emoji2/1.4.0/emoji2-1.4.0-sources.jar) |
| `androidx.exifinterface:exifinterface:1.3.6` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/exifinterface/exifinterface/1.3.6/exifinterface-1.3.6-sources.jar) |
| `androidx.graphics:graphics-path:1.0.1` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/graphics/graphics-path/1.0.1/graphics-path-1.0.1-sources.jar) |
| `androidx.interpolator:interpolator:1.0.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/interpolator/interpolator/1.0.0/interpolator-1.0.0-sources.jar) |
| `androidx.legacy:legacy-support-core-utils:1.0.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/legacy/legacy-support-core-utils/1.0.0/legacy-support-core-utils-1.0.0-sources.jar) |
| `androidx.lifecycle:lifecycle-common-java8:2.9.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-common-java8/2.9.4/lifecycle-common-java8-2.9.4-sources.jar) |
| `androidx.lifecycle:lifecycle-common-jvm:2.9.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-common-jvm/2.9.4/lifecycle-common-jvm-2.9.4-sources.jar) |
| `androidx.lifecycle:lifecycle-livedata-core-ktx:2.9.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-livedata-core-ktx/2.9.4/lifecycle-livedata-core-ktx-2.9.4-sources.jar) |
| `androidx.lifecycle:lifecycle-livedata-core:2.9.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-livedata-core/2.9.4/lifecycle-livedata-core-2.9.4-sources.jar) |
| `androidx.lifecycle:lifecycle-livedata:2.9.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-livedata/2.9.4/lifecycle-livedata-2.9.4-sources.jar) |
| `androidx.lifecycle:lifecycle-process:2.9.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-process/2.9.4/lifecycle-process-2.9.4-sources.jar) |
| `androidx.lifecycle:lifecycle-runtime-android:2.9.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-android/2.9.4/lifecycle-runtime-android-2.9.4-sources.jar) |
| `androidx.lifecycle:lifecycle-runtime-compose-android:2.9.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-compose-android/2.9.4/lifecycle-runtime-compose-android-2.9.4-sources.jar) |
| `androidx.lifecycle:lifecycle-runtime-ktx-android:2.9.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-ktx-android/2.9.4/lifecycle-runtime-ktx-android-2.9.4-sources.jar) |
| `androidx.lifecycle:lifecycle-service:2.9.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-service/2.9.4/lifecycle-service-2.9.4-sources.jar) |
| `androidx.lifecycle:lifecycle-viewmodel-android:2.9.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel-android/2.9.4/lifecycle-viewmodel-android-2.9.4-sources.jar) |
| `androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel-ktx/2.9.4/lifecycle-viewmodel-ktx-2.9.4-sources.jar) |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.9.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel-savedstate-android/2.9.4/lifecycle-viewmodel-savedstate-android-2.9.4-sources.jar) |
| `androidx.lifecycle:lifecycle-viewmodel:2.9.4` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel/2.9.4/lifecycle-viewmodel-2.9.4-sources.jar) |
| `androidx.loader:loader:1.0.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/loader/loader/1.0.0/loader-1.0.0-sources.jar) |
| `androidx.localbroadcastmanager:localbroadcastmanager:1.0.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/localbroadcastmanager/localbroadcastmanager/1.0.0/localbroadcastmanager-1.0.0-sources.jar) |
| `androidx.media3:media3-common:1.8.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/media3/media3-common/1.8.0/media3-common-1.8.0-sources.jar) |
| `androidx.media3:media3-container:1.8.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/media3/media3-container/1.8.0/media3-container-1.8.0-sources.jar) |
| `androidx.media3:media3-database:1.8.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/media3/media3-database/1.8.0/media3-database-1.8.0-sources.jar) |
| `androidx.media3:media3-datasource:1.8.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/media3/media3-datasource/1.8.0/media3-datasource-1.8.0-sources.jar) |
| `androidx.media3:media3-decoder:1.8.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/media3/media3-decoder/1.8.0/media3-decoder-1.8.0-sources.jar) |
| `androidx.media3:media3-exoplayer:1.8.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/media3/media3-exoplayer/1.8.0/media3-exoplayer-1.8.0-sources.jar) |
| `androidx.media3:media3-extractor:1.8.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/media3/media3-extractor/1.8.0/media3-extractor-1.8.0-sources.jar) |
| `androidx.media3:media3-session:1.8.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/media3/media3-session/1.8.0/media3-session-1.8.0-sources.jar) |
| `androidx.media:media:1.7.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/media/media/1.7.0/media-1.7.0-sources.jar) |
| `androidx.print:print:1.0.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/print/print/1.0.0/print-1.0.0-sources.jar) |
| `androidx.profileinstaller:profileinstaller:1.4.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/profileinstaller/profileinstaller/1.4.0/profileinstaller-1.4.0-sources.jar) |
| `androidx.room:room-common:2.6.1` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/room/room-common/2.6.1/room-common-2.6.1-sources.jar) |
| `androidx.room:room-ktx:2.6.1` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/room/room-ktx/2.6.1/room-ktx-2.6.1-sources.jar) |
| `androidx.room:room-runtime:2.6.1` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/2.6.1/room-runtime-2.6.1-sources.jar) |
| `androidx.savedstate:savedstate-android:1.3.2` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/savedstate/savedstate-android/1.3.2/savedstate-android-1.3.2-sources.jar) |
| `androidx.savedstate:savedstate-compose-android:1.3.2` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/savedstate/savedstate-compose-android/1.3.2/savedstate-compose-android-1.3.2-sources.jar) |
| `androidx.savedstate:savedstate-ktx:1.3.2` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/savedstate/savedstate-ktx/1.3.2/savedstate-ktx-1.3.2-sources.jar) |
| `androidx.sqlite:sqlite-framework:2.4.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/sqlite/sqlite-framework/2.4.0/sqlite-framework-2.4.0-sources.jar) |
| `androidx.sqlite:sqlite:2.4.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/sqlite/sqlite/2.4.0/sqlite-2.4.0-sources.jar) |
| `androidx.startup:startup-runtime:1.1.1` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/startup/startup-runtime/1.1.1/startup-runtime-1.1.1-sources.jar) |
| `androidx.tracing:tracing-ktx:1.2.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/tracing/tracing-ktx/1.2.0/tracing-ktx-1.2.0-sources.jar) |
| `androidx.tracing:tracing:1.2.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/tracing/tracing/1.2.0/tracing-1.2.0-sources.jar) |
| `androidx.transition:transition:1.6.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/transition/transition/1.6.0/transition-1.6.0-sources.jar) |
| `androidx.versionedparcelable:versionedparcelable:1.1.1` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/versionedparcelable/versionedparcelable/1.1.1/versionedparcelable-1.1.1-sources.jar) |
| `androidx.window:window-core-android:1.5.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/window/window-core-android/1.5.0/window-core-android-1.5.0-sources.jar) |
| `androidx.window:window:1.5.0` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/window/window/1.5.0/window-1.5.0-sources.jar) |
| `androidx.work:work-runtime-ktx:2.10.1` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/work/work-runtime-ktx/2.10.1/work-runtime-ktx-2.10.1-sources.jar) |
| `androidx.work:work-runtime:2.10.1` | [Source archive](https://dl.google.com/dl/android/maven2/androidx/work/work-runtime/2.10.1/work-runtime-2.10.1-sources.jar) |
| `com.google.guava:failureaccess:1.0.2` | [Source archive](https://repo.maven.apache.org/maven2/com/google/guava/failureaccess/1.0.2/failureaccess-1.0.2-sources.jar) |
| `com.google.guava:guava:33.3.1-android` | [Source archive](https://repo.maven.apache.org/maven2/com/google/guava/guava/33.3.1-android/guava-33.3.1-android-sources.jar) |
| `com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava` | Empty placeholder; no implementation |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22` | [Source archive](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib-jdk7/1.8.22/kotlin-stdlib-jdk7-1.8.22-sources.jar) |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22` | [Source archive](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib-jdk8/1.8.22/kotlin-stdlib-jdk8-1.8.22-sources.jar) |
| `org.jetbrains.kotlin:kotlin-stdlib:2.2.10` | [Source archive](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.2.10/kotlin-stdlib-2.2.10-sources.jar) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2` | [Source archive](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-android/1.10.2/kotlinx-coroutines-android-1.10.2-sources.jar) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2` | [Source archive](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.10.2/kotlinx-coroutines-core-jvm-1.10.2-sources.jar) |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3` | [Source archive](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-core-jvm/1.7.3/kotlinx-serialization-core-jvm-1.7.3-sources.jar) |
| `org.jetbrains:annotations:23.0.0` | [Source archive](https://repo.maven.apache.org/maven2/org/jetbrains/annotations/23.0.0/annotations-23.0.0-sources.jar) |
| `org.jspecify:jspecify:1.0.0` | [Source archive](https://repo.maven.apache.org/maven2/org/jspecify/jspecify/1.0.0/jspecify-1.0.0-sources.jar) |
