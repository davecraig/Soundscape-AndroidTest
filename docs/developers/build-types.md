---
title: Build types and analytics
layout: page
parent: Information for developers
has_toc: false
---

# Build types and analytics

The app has three build types, defined in `app/build.gradle.kts`. They differ in two things: whether code is minified/shrunk, and whether Firebase analytics + Crashlytics are wired in.

| Build type | Minified | Signing config | `DUMMY_ANALYTICS` | Source set used for analytics |
| --- | --- | --- | --- | --- |
| `debug` | no | debug | `true` | `app/src/debug/.../utils/PlatformAnalytics.kt` — returns `NoOpAnalytics` |
| `release` | yes | release | `false` | `app/src/release/.../utils/PlatformAnalytics.kt` — returns `FirebaseAnalyticsImpl`. `app/src/release/` is the only source set that compiles `FirebaseAnalyticsImpl.kt` against the Firebase SDK. |
| `releaseTest` | yes (inherits `release`) | release | `true` | `app/src/releaseTest/.../utils/PlatformAnalytics.kt` — returns `NoOpAnalytics` |

`releaseTest` is built with `initWith(getByName("release"))`, so it picks up R8/proguard, the release keystore and everything else — only the analytics implementation and the `DUMMY_ANALYTICS` flag differ. It exists so we can install a release-shaped build (signed, minified, optimised) on a developer device for debugging or pre-release testing without writing events into the production Firebase project.

## How the variant split works

The trick is plain Android source-set merging. The shared API lives in `app/src/main/java/.../utils/Analytics.kt`:

```kotlin
interface Analytics {
    fun logEvent(name: String, params: Bundle? = null)
    fun logCostlyEvent(name: String, params: Bundle? = null)
    fun crashSetCustomKey(key: String, value: String)
    fun crashLogNotes(name: String)

    companion object {
        fun getInstance(dummy: Boolean? = null, context: Context? = null): Analytics { ... }
    }
}
```

Each build type provides its own `PlatformAnalytics.kt` with a top-level `createPlatformAnalytics(context)` function. `Analytics.getInstance(dummy = false, context = ...)` calls that function — and because only one variant's source set is on the classpath for any given build, the right implementation is picked up at compile time. Firebase classes never leak into debug or `releaseTest` builds, so those variants do not need `google-services.json` or the Firebase SDK at runtime.

`NoOpAnalytics` is in `app/src/main/`, so all three variants can fall back to it; `FirebaseAnalyticsImpl` lives only in `app/src/release/`.

## Three reasons to use dummy analytics

`MainActivity.onCreate` decides at runtime whether to use the real analytics instance even on a `release` build:

```kotlin
Analytics.getInstance(
    BuildConfig.DUMMY_ANALYTICS ||
        !hasPlayServices(this) ||
        "true" == Settings.System.getString(contentResolver, "firebase.test.lab"),
    context = applicationContext,
)
```

i.e. dummy analytics if any of:

1. The build type sets `DUMMY_ANALYTICS = true` (debug, releaseTest).
2. The device has no Google Play Services.
3. The app is running in [Firebase Test Lab](https://firebase.google.com/docs/test-lab/android/android-studio#modify_instrumented_test_behavior_for) — Google's pre-launch checks run every release and would otherwise pollute the analytics with synthetic sessions.

## Runtime gating on `BuildConfig.DUMMY_ANALYTICS`

Beyond picking the analytics backend, `BuildConfig.DUMMY_ANALYTICS` is also used to suppress UI that only makes sense on real release builds:

* The new-release dialog (`Home.kt`) — only shown when `DUMMY_ANALYTICS != true`.
* The language-mismatch dialog (`Home.kt`) — same.

Add to this list cautiously: if you're tempted to gate something on `DUMMY_ANALYTICS`, ask whether the gate really wants "is this a real release build" (use `DUMMY_ANALYTICS`) or "is this a debug build" (consider `BuildConfig.DEBUG` instead — `releaseTest` is also minified and signed).

## `BuildConfig` values from `local.properties`

The same `defaultConfig` block also reads five values from `local.properties` and exposes them as `BuildConfig` strings:

```
TILE_PROVIDER_URL       TILE_PROVIDER_API_KEY
SEARCH_PROVIDER_URL     SEARCH_PROVIDER_API_KEY
EXTRACT_PROVIDER_URL
```

`local.properties` is not under version control. Each developer fills it in by hand (the format is documented in [Developer information]({% link developers/developers.md %})). On GitHub Actions, the workflow writes `local.properties` from a repo secret before invoking Gradle — see [GitHub actions]({% link developers/actions.md %}). If a value is missing the build still succeeds but those `BuildConfig` strings are empty, and the corresponding feature will fail at runtime.

## Optional source set: Meta glasses head tracking

The same source-set trick gates a whole feature rather than a build type. Head tracking from Meta smart glasses uses the [Meta Wearables Device Access Toolkit](https://wearables.developer.meta.com/docs/develop/dat), whose SDK needs credentials issued by the Meta Wearables Developer Center against our package name:

```
metaWearablesAppId=XXXXXXXXXXXXXXXX
metaWearablesClientToken=XXXXXXXXXXXXXXXXXXXXXXXX
```

Set both in `local.properties` and `app/build.gradle.kts` adds `src/metaGlasses/java` to the main source set and pulls in `mwdat-core` + `mwdat-motion`. Leave either out - which is the case for CI, F-Droid, forks and every shipping build - and `src/noMetaGlasses/java` is compiled instead. Both directories hold one file defining `createMetaGlassesHeadTrackingProvider()`; the real one returns a `MetaGlassesHeadTrackingProvider`, the stub returns null, and `SoundscapeService.rebuildHeadTrackingProvider()` simply drops a null from the list of head trackers it hands to `CompositeHeadTrackingProvider`. Nothing else differs between the two builds.

Keeping it optional rather than always-on matters for more than tidiness:

* `mwdat-core` is a ~9MB AAR of native libraries, dead weight in a build that can never use it.
* The credentials are compiled into the APK (as `com.meta.wearable.mwdat.*` manifest meta-data, filled from manifest placeholders), and they are registered against one package name - a fork or a suffixed debug `applicationId` would not attest anyway.
* Motion is a beta capability that Meta serves only to its development and beta release channels, so this cannot ship to users yet in any case.

`MetaGlassesHeadTrackingProvider` itself lives in `app/src/main/`, and takes a `MetaMotionClient` rather than talking to the SDK directly, so the provider and its unit tests compile and run in every build. Only `MwdatMotionClient`, the ~100-line wrapper that turns a `DeviceSession` into orientation samples, is in the optional source set.

Beyond the credentials, getting samples on a device also needs Developer Mode enabled in the Meta AI app, and that Meta account invited as a tester on our beta release channel. For local development Meta's own samples use `0` for both credentials, which is enough to turn the feature on here.

The remaining prerequisite, linking the app to the user's Meta account, is handled by `MetaGlassesRegistration` (same two-source-set trick, all no-ops when the feature is off). `MainActivity` calls it in three places: `observe()` in `onCreate` logs registration state and failures for the life of the activity; `startIfAvailable()` runs when the user switches head tracking on, which opens the Meta AI app but only when it is installed and has not linked us already; and `handleIntent()` in both `onCreate` and `onNewIntent` picks up a registration request coming the other way, from Meta AI. The callback returns on the `soundscape` URI scheme `MainActivity` already declares, so no new intent filter was needed - if the scheme registered in the Developer Center project differs, that filter is what has to change.

## iOS gating

iOS does not use Android-style build-type source-set splitting — the same `iosApp` target compiles for both Debug and Release. Instead, `iosApp/iosApp/FirebaseAnalyticsBridge.swift` runs a `shouldEnableFirebase()` gate at launch inside `FirebaseBootstrap.configureIfEnabled()`:

```swift
#if DEBUG
return false
#else
let env = ProcessInfo.processInfo.environment
if env["XCTestConfigurationFilePath"] != nil { return false }
if NSClassFromString("XCTestCase") != nil { return false }
return true
#endif
```

Mapping to Android's three-way gate:

| Android | iOS |
| --- | --- |
| `BuildConfig.DUMMY_ANALYTICS` (from `debug` / `releaseTest`) | `#if DEBUG` |
| `firebase.test.lab` system setting | `XCTestConfigurationFilePath` env var + `XCTestCase` class presence |
| `hasPlayServices()` | No analog — Firebase iOS has no equivalent hard runtime dependency |

Only Release, non-XCTest launches call `FirebaseApp.configure()` and inject `FirebaseAnalyticsBridge` into `IosSoundscapeService` via `setAnalyticsFactory { ... }`. Debug builds and XCTest runs leave the shared code's default `NoOpAnalytics` in place, so no Firebase framework code runs even though it is linked in.

The shared Kotlin `Analytics` interface is renamed for Objective-C export using `@ObjCName("SoundscapeAnalytics", exact = true)` in `shared/src/commonMain/kotlin/.../utils/Analytics.kt` — Firebase's Swift API also exports a class called `Analytics`, and renaming the Kotlin symbol at the source avoids module-qualified references at every Swift call site.
