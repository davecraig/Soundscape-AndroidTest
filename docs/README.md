## Project aim
The [original Soundscape app](https://github.com/Scottish-Tech-Army/Soundscape) is an iOS only app that provides 3D audio navigation for the blind or visually impaired. The main aim of this project is to widen the Soundscape availability by creating a very similar app that runs on Android. A secondary aim is that by using modern technologies we can also generate an iOS app from the same codebase.

The app is developed using [Android Studio](https://developer.android.com/studio) with the majority of the app code written in Kotlin. For 3D audio rendering, the code uses [FMOD Studio audio engine by Firelight Technologies Pty Ltd.](https://www.fmod.com/). That library is wrapped in a C++ library with an API exposed to Kotlin. Note that for non-commercial use the [FMOD license](https://www.fmod.com/legal) allows use without payment. This project is non-commercial with an open source license and meets this requirement.
## Early days
It's very early days in the project. We've done various proof of concept, and the code for those has mostly landed in this repo, but a lot of it is not actually used yet. The app will be made available via the Google Play Store, initially to a small group for 'internal testing'. Obviously, anyone can download and build the code from this repo, but testing should be done on the Play Store releases. Once we have something worth testing, we'll update here with how to join up.   

## Get started
The quickest way to get started building the app is to run Android Studio and select `File/New/Project from version control` from the menu. Select `Git` as the version control and paste in the HTTPS code link for this project from above. Click on Clone and the project will download, open and initialize ready to build.

## CI
See the document [here](actions.md) to learn about the GitHub actions used and how they are configured.

## Mapping data
See the document [here](mapping.md) for information about how we configure and use mapping data. There's also [this document](debugging-geojson.md) which describes a possible workflow for GeoJSON debugging.

## Release notes
[Here](release-notes.md) are the latest release notes.

## App Help
[This page](help.md) is auto-generated from the same source as the app Help screen. Currently we're just generating the English (US) version.

## Testing resources
[A document](tricky-locations.md) containing locations which cause particular issues in Soundscape. 

## Code documentation
* [App architecture](architecture.md) is an introduction to the app structure and its UI.
* [Audio API](audio-API.md) describes the interface with the audio engine for beacon and text to speech play out.
* [Dokka generated docs](dokka/index.html) contains docs auto-generated from comments in the code.
