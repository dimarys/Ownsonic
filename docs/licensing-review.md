# GPLv3 compatibility review

Reviewed on October 19, 2026, against commit `e0ded34` and its resolved Gradle dependencies.

## Result

No GPLv3-incompatible license was identified in the release application's dependency graph. Ownsonic has since adopted `GPL-3.0-only`, declared in the root README with the full license text in `LICENSE`. On October 20, 2026, third-party notices and license texts were added to the repository and configured for inclusion in APK assets. Public binary distribution still requires providing the matching corresponding source described below.

The release runtime contains 97 resolved artifact entries representing 95 distinct Maven coordinates. All 95 coordinates declare Apache-2.0 in their POM or inherited parent POM. Apache-2.0 is compatible with GPLv3; the dependencies retain their own licenses and notices.

The important exception is JUnit 4.13.2, which uses EPL-1.0 and is present only in test configurations. EPL-1.0 is not GPL-compatible for a combined work. JUnit is absent from `releaseRuntimeClasspath`, so it does not prevent licensing the application under GPLv3. Before distributing compiled test bundles containing JUnit, resolve their licensing separately, for example with a suitable exception or separate compatible licensing of the test code. No test exception or relicensing has been added by this review.

## Dependency findings

| Component | Resolved versions | License / finding |
| --- | --- | --- |
| AndroidX Compose | 1.10.4; Material3 1.4.0 | Apache-2.0 |
| AndroidX Activity, Core, Lifecycle | 1.8.2, 1.16.0, 2.9.4 | Apache-2.0 |
| AndroidX Media3 | 1.8.0 | Apache-2.0; includes the standard ExoPlayer, session, extractor, container, datasource, decoder, and database modules |
| AndroidX WorkManager, Room, SQLite | 2.10.1, 2.6.1, 2.4.0 | Apache-2.0 |
| AndroidX Graphics Path | 1.0.1 | Apache-2.0 metadata; the release APK includes its native library for four ABIs |
| Guava and failureaccess | 33.3.1-android, 1.0.2 | Apache-2.0 |
| Kotlin stdlib | 2.2.10 | Apache-2.0, with upstream third-party notices including BSD-3-Clause and Boost-1.0 code; these are GPL-compatible |
| Kotlin coroutines and serialization core | 1.10.2, 1.7.3 | Apache-2.0 |
| Other runtime annotations and support libraries | See inventory | Apache-2.0 |
| JUnit | 4.13.2 | EPL-1.0; tests only, absent from the release runtime |
| Hamcrest | 1.3 | BSD-3-Clause; tests only |
| Other test libraries | See inventory | Apache-2.0 |

The [dependency inventory](license-dependencies.csv) records exact resolved coordinates, configuration membership, and the official Maven POM used as license evidence, including parent POMs where licensing is inherited. It records declared top-level licenses, not every embedded third-party notice. The complete inventory covers 117 distinct external coordinates across release and test configurations.

Media3 source archives were checked for license headers. No separate FFmpeg, VLC, or other external codec extension is declared or resolved. Playback uses Android's platform codecs. The APK's only native library family is `libandroidx.graphics.path.so`.

For Graphics Path, the published 1.0.1 POM declares Apache-2.0. The current upstream native source headers also use Apache-2.0, including the math helpers; those current sources are supplementary evidence, not a byte-for-byte provenance match for the older binary.

## Resources and repository files

- The launcher artwork has an editable project SVG and project vector/WebP variants. No external artwork dependency or attribution requirement was found in its recorded provenance.
- The playback thumb icons closely follow Google's Material `thumb_up` design, with simplified coordinates and a rotated variant for dislike. They are attributed as Material Icons adaptations in `THIRD_PARTY_NOTICES.txt`, `artwork/README.md`, and their source comments, with the Apache-2.0 text retained under `legal/licenses/`.
- There are no bundled font files. The app uses Android's default fonts and renders any emoji through the platform.
- The instrumented test MP3 and embedded cover are documented as generated silence and a solid-color image, without third-party music or artwork.
- Music, podcast episodes, and covers downloaded from a user's server are not bundled project resources. Choosing a software license does not grant redistribution rights to that media.
- The Gradle wrapper scripts already contain Apache-2.0 headers. Keep them and include the applicable license text when distributing the repository. Build tools and test dependencies are distinct from libraries included in the release APK.
- The Subsonic client is implemented in this repository using the REST API. No Airsonic server implementation or third-party Subsonic client library is bundled.

## Distribution work still required

1. Completed: the root `LICENSE` contains the full GPLv3 text, and the README declares `GPL-3.0-only` for the project's original code, documentation, and artwork. Preserve third-party license exceptions and ownership notices.
2. Completed for the reviewed dependency snapshot: `THIRD_PARTY_NOTICES.txt` lists the 95 release runtime coordinates and includes Material icon attribution and modification descriptions. `legal/COPYRIGHT_NOTICES.txt` retains copyright statements from available published source archives; `legal/licenses/` contains Apache-2.0 and the relevant Kotlin third-party texts. No separate root NOTICE files were found in the checked AndroidX, Media3, Kotlin, coroutines, serialization, Guava, Material Icons, or JSpecify upstream trees.
3. Completed: `prepareLegalAssets` copies the root GPL text, third-party notices, and `legal/` contents into `assets/legal/` for every APK. The files are readable when extracted; no dedicated licenses screen is included. Keep the notice snapshot in sync with dependency and resource changes.
4. For GPLv3 binary releases, provide the matching Corresponding Source, including required non-system dependency sources and build scripts, through a method allowed by GPLv3 section 6. A link to only the application source repository is not automatically sufficient for bundled dependencies. Personal signing credentials are not part of the public source package for an ordinary APK release.
5. Keep JUnit out of production dependencies and address licensing before publishing combined compiled test artifacts.

## Evidence and scope

The review resolved `releaseRuntimeClasspath`, `debugUnitTestRuntimeClasspath`, and `debugAndroidTestRuntimeClasspath`, read local and official Maven POM metadata, inspected available source/archive license information, inspected the existing release APK, and examined tracked resources and their history. No production code, dependency versions, or release APK was changed.

This is a dependency and provenance review, not an exhaustive authorship analysis of every upstream source file. Repeat it when dependencies or third-party assets change. Packaging the required notices remains separate from determining license compatibility.

- [GNU: license compatibility](https://www.gnu.org/licenses/license-compatibility.en.html)
- [GNU: license list, including Apache-2.0, BSD, Boost, and EPL](https://www.gnu.org/licenses/license-list.html)
- [GPLv3, including Corresponding Source and section 6](https://www.gnu.org/licenses/gpl-3.0.html)
- [Apache-2.0 redistribution conditions](https://www.apache.org/licenses/LICENSE-2.0.html)
- [Media3 1.8.0 license](https://github.com/androidx/media/blob/1.8.0/LICENSE)
- [Kotlin 2.2.10 third-party licensing notes](https://github.com/JetBrains/kotlin/blob/v2.2.10/license/README.md)
- [Material Icons license](https://github.com/google/material-design-icons/blob/master/LICENSE)
- [JUnit 4 license](https://junit.org/junit4/license.html)
- [Eclipse license FAQ](https://www.eclipse.org/legal/epl/faq/)
