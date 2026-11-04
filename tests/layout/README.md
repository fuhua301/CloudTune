# Desktop layout regression checks

Optional desktop tests using JDK 17, JUnit 4.13.2 and Robolectric 4.14.1. These files are not part of the Android application or the AIDE build.

Compile the application with `python build.py`, resolve the dependencies in this directory's pom.xml with Maven, and compile EffectsLayoutTest.java against build/classes, Android framework classes and the resolved test dependencies. Unpack classes.jar from the AndroidX monitor and espresso AAR dependencies onto the runtime classpath. Run `org.junit.runner.JUnitCore com.cloudtune.player.EffectsLayoutTest` with the full Robolectric android-all framework on the classpath (an SDK stub jar cannot run these tests). Robolectric resolves instrumented API 28 and 35 frameworks at runtime.

The test starts the actual EffectsActivity in Robolectric, measures its decor and ScrollView hierarchy, and verifies that every card, description and action has nonzero visible geometry. It covers 320/360/480 dp and font scale 1.6 on API 28 and 35, eight cases total. The fixture sets nativeLibraryDir to an empty test directory; it does not load the ARM64 DSP or test audio playback.

Current checks also verify that HIFI is on the right of 3D, row heights match, descriptions fit, and ordinary cards are compact. Results for 1.0.17 are saved in docs/effects-layout-test-results.txt. This is framework measurement, not a phone screenshot or device listening test.


# Player portrait checks (1.0.18)

PlayerPortraitTest uses native graphics on API 28 and 35. It checks the photo/full-lyrics switch, excerpt synchronization, short-screen transport visibility, HTTPS URL normalization and portrait selection using a public upstream response fixture. It has a Player subclass that suppresses QR/network login. Run with the same classpath as above, plus `--add-opens=java.base/java.io=ALL-UNNAMED`. Supply `-Dportrait.json=<absolute path to tests/fixtures/portrait-public.json>`, `-Dportrait.fixture=<local JPG>`, and `-Dportrait.screenshot=<temporary PNG>`. The public test photo can be downloaded from the first `data[0][0].imgs["6"][0].sizable_portrait` URL in that JSON fixture, replacing http with https. The image itself is not redistributed in the source ZIP. Results in docs/player-portrait-test-results.txt include both player and effects checks. No real account or private cloud metadata is used.


# Local music and frosted settings (1.0.21)

LocalSettingsTest uses API28/35, native graphics and a MediaMetadataRetriever shadow for deterministic tags. It checks copying, persistence, duplicate import, cancellation, offline library entry, SAF multiple selection, upload login gate, HiFi migration and rounded frosted dialog rendering. Run alongside the existing tests and pass -Dsettings.screenshot=<temporary PNG path>. Phone audio decoding and real cloud-account uploads are separate external checks. Results are recorded in docs/local-settings-test-results.txt.
