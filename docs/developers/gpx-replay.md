---
title: GPX replay
layout: page
parent: Information for developers
has_toc: true
---

# GPX replay

Debug builds can be driven over a recorded GPX track instead of the phone's own GPS. The whole app
runs normally — the geoengine, callouts and beacons all behave as they would on a real walk — but
the route is reproducible and can be walked at whatever pace you ask for, from your desk.

This is useful for two things:

* **Testing a change over a known route** without repeatedly walking it.
* **Recording the app's audio** for tutorial guides, where the same track has to produce the same
  callouts every time so the recording can be regenerated when the app changes.

Release builds have none of this: the intent filter lives in `app/src/debug/AndroidManifest.xml`,
so the action isn't declared at all in a release manifest, and
`SoundscapeService.setGpxPlaybackMode()` refuses to run outside a debug build regardless.

## Getting a track

Any GPX file with at least two track points will do. The easiest source is the app itself —
`GpxRecorder` records every walk, and "Share recording" writes it out as `travel.gpx`. A track
recorded on a real walk replays with all the wobble and pauses the receiver actually produced,
which is usually what you want when reproducing a bug.

Only the track points' latitude and longitude are used. Recorded timestamps are ignored; see
[Speed](#speed) below.

## Running a replay

Push the track to the app's own external files directory — no storage permission is needed for it,
and the app asks for none:

```bash
adb shell mkdir -p /sdcard/Android/data/org.scottishtecharmy.soundscape/files/gpx
adb push walk.gpx /sdcard/Android/data/org.scottishtecharmy.soundscape/files/gpx/
```

Start the app and let the service come up as normal, then fire the intent:

```bash
adb shell am start -a org.scottishtecharmy.soundscape.REPLAY_GPX \
    -e gpx walk.gpx \
    --ef speed 1.4 \
    --ez loop false
```

`scripts/replay-gpx.sh walk.gpx` does all of the above.

The service has to be running before the intent arrives — the replay swaps out the providers of a
live service rather than starting one. If the app was not already running, the intent will launch
it, but you may need to send the intent a second time once it has settled.

### Extras

| Extra   | Default | Meaning |
| ------- | ------- | ------- |
| `gpx`   | —       | File name under the `gpx` directory above, or an absolute path to somewhere already readable. Omit it entirely to stop a replay and hand the providers back to the phone. |
| `speed` | `1.4`   | Metres per second. |
| `loop`  | `false` | Restart from the first track point at the end rather than stopping. |

### Speed

The track is walked at a constant speed rather than at the pace it was recorded at. That's
deliberate: a replay being used to produce a recording needs to be reproducible, and needs to be
able to cover dull stretches quickly. 1.4 m/s is a brisk walk; 0.7 m/s is a slow one; anything
above about 3 m/s starts to outrun tile loading on a cold cache.

A fix is published once a second whatever the speed, matching the rate a phone's GPS manages.

## Stopping

Either of these hands location and direction back to the phone's own providers:

```bash
adb shell am start -a org.scottishtecharmy.soundscape.REPLAY_GPX
```

or entering street preview, which takes ownership of the location provider for itself.

## How it works

`GpxDrivenProvider` flattens every segment of every track in the file into one polyline and walks
it, publishing into the same `locationFlow` and `orientationFlow` that a real provider fills. It
synthesizes nothing else: the audio engine geometry is derived by `GeoEngine` from those flows, so
beacons and callouts spatialise exactly as they would on a real walk.

`SoundscapeService.setGpxPlaybackMode()` swaps the providers over, following the same
destroy/rebuild/restart sequence that `setStreetPreviewMode()` uses.

Because `locationFlow` and `orientationFlow` forward to *whichever provider is current*, swapping
the providers hands out new `StateFlow` objects and leaves existing collectors attached to the old
ones — which is why the map froze the first time this was tried. `startProviders()` bumps
`MediaControllableService.providerGeneration` to tell collectors to re-subscribe; `HomeViewModel`
watches it. Anything else that collects those flows across a provider swap needs to do the same.

The heading reported with each fix is the bearing of the polyline segment currently being walked,
so it stays correct between track points and not only at them.
