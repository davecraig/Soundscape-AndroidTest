---
title: Smoke test
layout: page
parent: Testing
has_toc: false
---

# Smoke test

A smoke test should be run against each release candidate to check for regressions. Work through
each section in order — later sections sometimes depend on state created by earlier ones (e.g.
markers must exist before a route can be created). Where an _Expected_ result is given, flag it
if you observe something different.

---

## 1. Home screen

### 1.1 Map and location
1. Launch the app and wait for a GPS fix.
1. Observe the map.

   _Expected:_ A red triangle appears at your current location. The triangle rotates as you change
   the direction the phone is pointing.

1. Walk a few steps.

   _Expected:_ The triangle moves along the map to track your position.

### 1.2 Bottom bar buttons
Tap each of the four buttons at the bottom of the home screen in turn:

| Button | Expected audio |
|---|---|
| **My Location** | Speaks your current street or road name and nearby context (e.g. neighbourhood or town) |
| **Around Me** | Speaks a list of nearby points of interest |
| **Ahead of Me** | Speaks points of interest in the direction the phone is pointing |
| **Nearby Markers** | Speaks any saved Markers within range, or says none are nearby |

### 1.3 Full-screen map
1. Tap the fullscreen icon (floating action button, bottom-right of the map).

   _Expected:_ The map fills the screen. The bottom bar and search bar are hidden.

1. Tap the icon again.

   _Expected:_ The normal home screen layout is restored.

---

## 2. Menu drawer

Open the menu by tapping the hamburger icon (top left). Verify each item is present and works:

| Menu item | Expected behaviour |
|---|---|
| **Exit app** | Closes the app |
| **Settings** | Opens the Settings screen |
| **Help & Tutorials** | Opens the Help screen |
| **Audio tutorial** | Starts a spoken walkthrough; label changes to _Cancel audio tutorial_ |
| **Cancel audio tutorial** | Stops the tutorial early |
| **Rate Soundscape** | Opens the Play Store listing |
| **Contact support** | Opens an email draft to the support address |
| **Offline Maps** | Opens the Offline Maps screen |
| **About Soundscape** | Opens the About page |
| **What's new** | Shows the new release information dialog |

Enable travel recording in Settings (see section 8), then reopen the menu:

| Menu item | Expected behaviour |
|---|---|
| **Share recording of travel** | Opens the share sheet to send the GPX file |

---

## 3. Sleep mode

### 3.1 Enter and wake immediately
1. Tap the sleep (snooze) icon in the top-right corner of the home screen.

   _Expected:_ The Sleep screen opens, showing a **Wake up now** button and a **Wake on leave** button.

1. Tap **Wake up now**.

   _Expected:_ The app returns to the home screen.

### 3.2 Wake on leave
1. Enter sleep mode again.
1. Tap **Wake on leave**.

   _Expected:_ The **Wake on leave** button disappears. The screen heading changes to indicate the
   app is watching for movement.

1. Move more than ~12 metres from where you were standing.

   _Expected:_ The app wakes automatically and returns to the home screen.

---

## 4. Search

### 4.1 Online search
1. Tap the search bar at the top of the home screen and type the name of a nearby street or business.

   _Expected:_ Results appear below the bar within a few seconds. Each result shows a name and a
   type label (e.g. _restaurant_, _road_, _village_).

1. Tap a result.

   _Expected:_ The Location Details screen opens for that place (see section 6).

### 4.2 International search
1. Search for the name of a well-known city in another country (e.g. "Paris").

   _Expected:_ Results from the online server appear, including the city itself.

### 4.3 Offline search (requires an offline map — see section 9)
1. Enable aeroplane mode (or disable mobile data and Wi-Fi).
1. Search for a nearby street or business.

   _Expected:_ Results from the downloaded offline map still appear. Online results are absent, but
   local results are found.

1. Disable aeroplane mode before continuing.

---

## 5. Places Nearby

1. Tap the **Places Nearby** button on the home screen.

   _Expected:_ A list of nearby categories is shown (e.g. _Food_, _Transport_, _Mobility_).

1. Tap a category folder.

   _Expected:_ A list of individual places within that category appears.

1. Tap a place.

   _Expected:_ The Location Details screen opens for that place.

1. From the category list, long-press or tap the beacon icon on a place (if visible).

   _Expected:_ An audio beacon starts playing from the direction of that place.

1. Tap the back button to return to the home screen.

---

## 6. Location Details

Open Location Details for any place (via search, Places Nearby, long-tap on the map, or the
_Current Location_ button on the home screen). Verify the following:

| Element | Expected |
|---|---|
| Place name | Shown at the top |
| Distance and direction | Shown relative to your current position (e.g. "120 m NE") |
| Map | Shows a pin at the place location with your position nearby |

Tap each action button:

| Button | Expected behaviour |
|---|---|
| **Start audio beacon** | Returns to home screen; a directional audio beacon plays from the place |
| **Save as marker** | Opens the Save Marker screen |
| **Street Preview** | Returns to home screen in Street Preview mode (see section 10) |
| **Share** | Opens the system share sheet with a `soundscape://` link |
| **Offline Maps** | Opens the Offline Maps screen centred on this location (see section 9) |

If the place was opened from the Markers list, an **Edit** button replaces **Save**.

---

## 7. Markers and Routes

### 7.1 Create markers
1. Long-tap at three or more different locations on the home screen map.
   After each long-tap, tap **Save as marker** on the Location Details screen, give it a name, and
   tap **Save**.

   _Expected:_ Each marker is saved and you are returned to the home screen.

### 7.2 Markers list
1. Tap **Markers and Routes** on the home screen and select the **Markers** tab.

   _Expected:_ All saved markers are listed.

1. Toggle the sort order (name vs. distance) using the sort button.

   _Expected:_ The list reorders accordingly.

1. Tap a marker in the list.

   _Expected:_ Location Details opens for that marker, with an **Edit** button instead of **Save**.

1. Tap the beacon icon on a marker (if shown) without opening Location Details.

   _Expected:_ A beacon starts at that marker's location.

### 7.3 Edit and delete a marker
1. Open a marker's Location Details and tap **Edit**.
1. Change the marker name and tap **Save**.

   _Expected:_ The updated name appears in the Markers list.

1. Open the same marker and tap **Delete**.

   _Expected:_ The marker is removed from the list.

### 7.4 Create a route
1. On the **Routes** tab, tap the **+** icon in the top right.
1. Enter a route name and optional description.
1. Tap **Add Waypoints**, select at least two markers in order, and tap **Done**.
1. Tap **Done** to save.

   _Expected:_ The new route appears in the Routes list.

### 7.5 Edit a route
1. Open the route and tap **Edit**.
1. Reorder the waypoints by dragging them.
1. Remove one waypoint and tap **Done**, then **Save**.

   _Expected:_ The route reflects the changes.

1. Add the waypoint back, save again.

### 7.6 Route Details
1. Tap the route in the list to open Route Details.

   _Expected:_ The route name, description, and ordered list of waypoints are shown. A map
   displays all the waypoints.

### 7.7 Route playback
1. Tap **Start Route** on the Route Details screen.

   _Expected:_ You are returned to the home screen. A route control card appears showing the route
   name and current waypoint (e.g. "Waypoint 1 of 3"). An audio beacon plays from the first
   waypoint's direction.

1. Verify the route control card buttons:

   | Button | Expected behaviour |
   |---|---|
   | **Skip previous** | Disabled at waypoint 1 (greyed out); moves to the previous waypoint otherwise |
   | **Skip next** | Moves to the next waypoint; disabled on the last waypoint |
   | **Info** | Opens Route Details without stopping playback |
   | **Mute/unmute** | Silences or restores the audio beacon |
   | **Stop** | Stops playback and the card disappears |

1. Walk towards the first waypoint until the beacon moves to the next one automatically.

   _Expected:_ The waypoint counter increments and the beacon repositions to the next waypoint.

### 7.8 Route playback in reverse
1. Open Route Details and tap **Start Route in Reverse**.

   _Expected:_ Playback begins from the last waypoint and proceeds backwards.

### 7.9 Share and import a route
1. On Route Details, tap the share icon.

   _Expected:_ The system share sheet opens. Share the file to Files or an email.

1. Open the shared file from Files while Soundscape is installed.

   _Expected:_ Soundscape opens and the Add Route screen appears, pre-populated with the imported
   waypoints. Save it under a new name.

   _Expected:_ The imported route appears in the Routes list.

---

## 8. Settings

Open Settings from the menu. Verify each section expands when tapped.

### 8.1 Callouts
| Setting | Expected behaviour when toggled |
|---|---|
| **Allow callouts** | Disabling silences all automatic callouts while walking |
| **Places and landmarks** | Enables/disables POI callouts (only active when Allow callouts is on) |
| **Mobility** | Enables/disables intersection and road callouts |
| **Audio beacon** | Enables/disables the beacon callout when approaching a waypoint |

### 8.2 Beacon style
1. Tap the beacon style row.

   _Expected:_ A dialog lists the available beacon styles (Tactile, Flare, Ping, Drop, Standard,
   Shimmer, Signal, Signal Slow, Signal Very Slow).

1. Tap a different style.

   _Expected:_ A preview of that beacon sound plays.

1. Tap **OK**.

   _Expected:_ The new style is saved and used for subsequent beacons.

### 8.3 Audio (Android only)
| Setting | Expected |
|---|---|
| **Voice engine** | Dropdown lists available TTS engines; changing it affects callout voice |
| **Voice** | Dropdown lists voices for the chosen engine |
| **Speaking rate** | Slider adjusts how fast callouts are spoken |

### 8.4 Accessibility (Android only)
| Setting | Expected |
|---|---|
| **Theme (light/dark/auto)** | Changes the app colour scheme immediately |
| **Contrast (regular/medium/high)** | Adjusts UI contrast |
| **Show map** | Hiding the map removes it from the home and Location Details screens |
| **Relative directions** | Switches between clockface (e.g. "2 o'clock"), degrees, or left/right |
| **Units** | Switches between auto, imperial, and metric distances |

### 8.5 Language
1. Change the language using the dropdown.

   _Expected:_ The app UI relaunches in the chosen language.

1. Restore the original language.

### 8.6 Search
| Setting | Expected |
|---|---|
| **Search network** | _Auto_ uses online + offline; _Offline_ uses only downloaded maps |
| **Search results language** | Sets the language preference for Photon server results |

### 8.7 Media controls
Change the media controls mode:

| Mode | Expected |
|---|---|
| **Original** | Media buttons trigger the next callout (same as iOS) |
| **Voice Command** | Holding the media button starts voice recognition for commands |
| **Audio Menu** | Media button opens a spoken menu of options |

### 8.8 Offline map storage (Android only)
1. If a microSD card is present, verify the dropdown includes both internal storage and the card.
1. Select a storage location.

   _Expected:_ Subsequent offline map downloads go to that location.

### 8.9 Debug — travel recording
1. Enable **Enable recording of travel**.

   _Expected:_ The **Share recording of travel** item appears in the menu drawer.

1. Disable it again.

   _Expected:_ The menu item disappears.

### 8.10 Reset settings
1. Tap **Reset settings to defaults** and confirm.

   _Expected:_ The app restarts and the onboarding flow appears from the beginning.

1. Complete onboarding to return to the home screen.

---

## 9. Offline Maps

### 9.1 Access via menu
1. Open the menu and tap **Offline Maps**.

   _Expected:_ The Offline Maps screen opens. A section labelled _Nearby_ lists map extracts that
   cover your current location, each showing a name and file size. A _Downloaded_ section is
   empty (or shows any previously downloaded maps).

### 9.2 Download a map
1. Tap an extract in the _Nearby_ list.
1. Tap **Download**.

   _Expected:_ A progress bar appears and advances to completion. The extract then moves to the
   _Downloaded_ section.

### 9.3 Offline operation
1. Enable aeroplane mode.
1. Walk around and listen for callouts.

   _Expected:_ Audio callouts still work. The map still renders.

1. Disable aeroplane mode.

### 9.4 Access via Location Details
1. Open Location Details for a place some distance away (use search or the map).
1. Tap **Offline Maps**.

   _Expected:_ The Offline Maps screen opens with the _Nearby_ list filtered to extracts relevant
   to that place's location rather than your current location.

### 9.5 Delete a map
1. On the Offline Maps screen, tap the downloaded map.
1. Tap **Delete**.

   _Expected:_ The extract is removed from the _Downloaded_ section.

---

## 10. Street Preview

1. Open Location Details for any location (search for a street name, for example).
1. Tap **Street Preview**.

   _Expected:_ You are returned to the home screen. The title bar shows _Street Preview_ and the
   bottom buttons are replaced by forward/back navigation controls.

1. Tap the forward button several times.

   _Expected:_ Each tap moves the virtual position along the street and triggers a callout
   describing the new position (e.g. intersection names, POIs).

1. Tap the exit icon in the top-right corner.

   _Expected:_ The title bar returns to _Soundscape_ and the normal bottom buttons reappear.

---

## 11. Alternative entry points

### 11.1 Share from Google Maps
1. Open Google Maps, find a location, tap **Share**, and choose **Soundscape**.

   _Expected:_ Soundscape opens at the Location Details screen for that location.

### 11.2 soundscape:// URL
1. Open any app that can open URLs and navigate to a `soundscape://` link (e.g. one shared in
   section 6 above).

   _Expected:_ Soundscape opens at Location Details for the linked location.

### 11.3 Import a route file
1. Open a `.gpx` route file from the Files app (or an email attachment) while Soundscape is
   installed.

   _Expected:_ Soundscape opens and the Add Route screen is pre-populated with the file's
   waypoints. Save it to confirm import.

---

## 12. Media controls

1. Start an audio beacon (from a marker or a route).
1. Lock the phone screen and use the hardware media button (or headphone remote).

   _Expected (Original mode):_ The button triggers the next callout.

1. Change the media controls mode to **Audio Menu** in Settings.
1. Press the media button again.

   _Expected:_ A spoken menu is read out offering options.
