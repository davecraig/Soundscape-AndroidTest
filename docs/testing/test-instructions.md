---
title: Test instructions for a new user
layout: page
parent: Testing
has_toc: false
---

# Testing the Soundscape app
The app is a port of the Soundscape iOS app and the UI is designed to be pretty much the same.
Whilst we're interested in improving the UI in the long term, matching the iOS behaviour is the 
most important goal for this initial release.

Unless you are an STA member and can ping us on Slack, all feedback should go via the Help Desk by emailing <soundscapeAndroid@scottishtecharmy.support>.

## Requirements
The app currently requires Android 11 (API 30 - see <https://apilevels.com/>). We are hoping to drop this to Android 9 with some more work, but for now we only support Android 11 and later.
We don't know of any other requirements, but that's one of the thing this testing should help us understand.

## Installing the app
The app is freely available on the Play Store [here](https://play.google.com/store/apps/details?id=org.scottishtecharmy.soundscape).

## Running the app the first time
The first time you run the Soundscape app you will see a series of onboarding screens which let 
you select various initial settings. English is US English and is the base for all translations. If a string is missing in the language being used then it will be replaced by the English string instead. We already know about these, but all other language issues are of interest e.g. text that is difficult to understand or where there's a problem with the ordering of phrases.

The onboarding flow includes several screens:

* **Welcome** — introduction to the app.
* **Language selection** — choose the language for the app.
* **Navigation walkthrough** — explains how the app works.
* **Battery optimization** — asks you to exempt the app from battery optimisation so it can run in the background; we recommend doing this.
* **Hear your surroundings** — plays a sample of the audio callouts.
* **Choose an audio beacon** — lets you pick the beacon sound style.
* **Offline map storage** — lets you choose where to store offline map downloads (internal storage or a microSD card if you have one).
* **Accessibility** — additional accessibility options.
* **Terms of use** — agree to continue.

Things we're interested in on the initial screens:

* Is there any text on the screens that you are unable to read or where words are split across 
  lines?
* Do you just hear silence when you click the **Listen** button on the **Hear Your Surroundings** screen?
* Do you only hear silence when selecting the different beacon sounds on the **Choose an Audio Beacon** screen?
* Does the storage location dropdown on the **Offline map storage** screen show sensible options for your device?

Please report any of these issues via the Help Desk.

## Main app operation
Now that you're past the onboarding screens, you shouldn't see them again and you should be on 
the main screen:

<img src="{{ "/documentationScreens/homeScreen.png" | relative_url }}" width="200" alt="Screenshot of the Soundscape home screen">

Soundscape will now continue to run in the background. To exit it, click on the top right corner 
to put the app to sleep, and then close the app (swipe up etc.).

Soundscape is designed to be used with headphones.

Things that should happen on the Home screen and we're interested if they do not:

* The map should show your current location with a red triangle. (There should be a map!)
* The red triangle on the map should rotate as you point the phone in different directions.
* Speech describing your surroundings should be heard when you click on each of the 4 buttons at the bottom of the screen.

If you've got to this point and it all seems to be working, then you can move on to more detailed testing.

### Test 1 - Go for a walk
As you move around, Soundscape should periodically describe your location and call out any points of interest that you pass e.g. Shops, Bus Stops etc. We're interested if there's anything that doesn't sound right. When online, the app downloads map tiles as you move; each tile covers a small area and is cached on the device so it only needs to be downloaded once. If you want the app to work without an internet connection at all, download an offline map first (see Test 2 below).

### Test 2 - Offline maps
Offline maps let the app work without an internet connection by downloading map data for a region to your phone in advance.

#### Download an offline map
1. Tap the hamburger menu (top left) and select **Offline Maps**.
1. The screen shows map extracts that cover your current location. Each entry shows a name and a file size so you can judge how much storage it will use.
1. Tap an extract and then tap **Download** to start the download. A progress bar shows the download progress.
1. Once downloaded, the extract appears in the **Downloaded maps** section. You can delete it from here when you no longer need it.

You can also reach the Offline Maps screen from a Location Details screen — useful if you want to download maps for a place you're planning to visit.

Things to check:
* Does the list of nearby extracts look correct for your location?
* Does the download progress bar move and complete without errors?
* Does the downloaded map appear in the Downloaded maps list?
* If you turn off mobile data and Wi-Fi, does the app still show the map and generate audio callouts?
* Does searching (see Test 3) work while offline when you have a downloaded map?

The storage location for offline maps can be changed in Settings (see the Offline map storage section).

### Test 3 - Search for places
The search bar appears at the top of the home screen.

1. Tap the search bar and type the name of a nearby street, business, or landmark.
1. Results should appear below the search bar, each showing a name and a type (e.g. restaurant, village, road). Tap a result to open its **Location Details** screen.
1. From **Location Details** you can start an audio beacon at that location, save it as a Marker, or open the **Offline Maps** screen centred on that location.
1. Try an international search e.g. the name of a well-known city abroad — results from the online server should still appear.

Things to check:
* Do results appear in a reasonable time?
* Are the result names and types readable?
* Does tapping a result open the correct location on the map?

If you have offline maps downloaded (see Test 2 above), the search will also look through those and show local results without an internet connection.

### Test 4 - Create a route and play it back
This uses a bit more of the UI, but once set up it should be fairly straightforward.
#### Create some Markers
Markers are points on the map which can be added together to make a route. Markers can be saved from
the Location Details screen, but there are many ways to get to that.
1. A long tap on the map on the home screen, or the map on the _Current Location_ screen will bring up a Location Details screen for that location.
1. The _Current Location_ button on the main screen brings up the Location Details for the current location. The map there is scrollable and you can zoom in and out. It's possible to save markers, move to a new point, long tap and save another marker.
1. The _Places Nearby_ button on the home screen shows nearby points that can be clicked on for Location Details.
1. The search bar will bring up results which can be clicked on for Location Details.

Once saved, Markers appear in the screen that can be navigated from the _Markers and Routes_ button on the home screen. Once you have a number of Markers, you can create a route.

#### Create a route
1. With the _Routes_ tab of the _Markers and Routes_ screen selected, click on the + icon in the top right. 
1. Type in a name for your route, and an optional description.
1. Click _Add Waypoints_ and add the Markers you've created. Select the markers in the order that you want them to appear in the route and then click _Done_.
1. Click _Done_ again to save the route.

There should now be a route listed. Click on that and you can check that it's what you think it should be.

#### Play the route
Click _Start Route_ on the _Route Details_ screen to start an audio beacon playing at the first waypoint of the route. The audio beacon will sound from the direction of the waypoint from where you are. When you're using the Soundscape app and your phone is unlocked, the direction used is the direction that the phone is pointing in. You can lock your phone and put it in your bag and then it will start using the direction in which your walking. The sound of the beacon will be different if you are walking towards it or away from it. If you stop moving and your phone is locked then any beacon will go quieter to indicate that there's no available direction data.

You can also play the route in **reverse** using the _Start Route in Reverse_ button on the Route Details screen.

While a route is playing, controls appear on the home screen: you can skip to the previous or next waypoint, mute the beacon, and stop the route.

Routes can be **shared** with other users. On the Route Details screen, tap the share icon to export the route as a file that can be sent by email or any other app. Other people can import shared routes by opening the file while Soundscape is installed.

## Providing debug location trace
The app can store up to an hour buffer of the user location recorded whilst the app is running. This feature is disabled by default, and even when enabled the data stays on the phone unless the user chooses to share it via interaction with the app. To use the feature:
1. Tap on the Menu hamburger in the top left, and then tap on "Settings" scroll to the bottom and you'll see the "Enable recording of travel" option. Click to enable/disable.
2. With the setting enabled, a new option appears in the Menu drawer below "About Soundscape" which is "Share recording of travel". If you want to share a GPX track you can click on that and you can then choose whether to use email/slack etc. to send the file to us to debug.
The file contains the data from the Android location services for up to the last hour that the app has been running. Don't share it with us if you don't want us to know where you've been. There's no identifying data in it, though obviously we'll know who sent it.

We can load the GPX file into our test code and it will generate the callouts that the user will have heard and we can see which road/path the app thought it was following, and figure out why callouts were generated incorrectly or not generated at all.
Enabling the setting is absolutely optional, but it is useful to us for debugging.


## Other features worth exploring

### Settings
Open the menu (top left) and tap **Settings** to find:
* **Beacon type** — choose the audio beacon sound style.
* **Voice engine and voice** — select the text-to-speech engine and voice used for callouts (Android only).
* **Theme** — switch between light, dark, or auto (follows the system setting).
* **Contrast** — regular, medium, or high contrast for the UI (Android only).
* **Media controls** — choose how the hardware media buttons and headphone controls work: Original (same as iOS), Voice Command, or Audio Menu.
* **Callout filters** — control which types of callout you hear (mobility, places, landmarks etc.).
* **Offline map storage** — choose internal storage or a microSD card for downloaded offline maps.
* **Enable recording of travel** — see the debug section above.
* **Language** — change the app language independently of the system language.
* **Reset settings** — returns all settings to their defaults and reruns onboarding.

### Sleep mode
Tap the sleep icon in the top-right corner of the home screen to put the app to sleep. In sleep mode the map and audio are paused but the app stays open. You can:
* Tap **Wake up now** to resume immediately.
* Tap **Wake up when I leave** to have the app wake automatically when you move away from your current location.

### Audio tutorial
The menu (top left) contains an **Audio tutorial** option that plays a guided walkthrough of the main app features through the speakers. Tap it again (it changes to **Cancel audio tutorial**) to stop it early.

### Help and About
The menu also links to **Help & Tutorials** and **About Soundscape**, which contain in-app documentation about how to use the app.

### Sharing your location from another app
If you share a location from Google Maps (or a `soundscape://` URL from anywhere on your phone), Soundscape will open at the Location Details screen for that place.

## Final notes
If there's anything unclear in these instructions let us know. Once we have some feedback,
there'll be some bugs to fix, and then we'll do incremental releases. If you are interested in helping out 
further on the project, take a look at the STA volunteer app for some available roles.

Thanks for reading!