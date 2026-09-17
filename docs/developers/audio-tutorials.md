---
title: Audio tutorial guides
layout: page
parent: Information for developers
has_toc: true
---

# Audio tutorial guides

Narrated audio guides that teach Soundscape by walking through it: a narrator explains a feature,
then you hear the app actually doing it, spatialised exactly as a user would hear it through
headphones.

They are **audio, not video**. Most Soundscape users are blind, so a screen recording would be
teaching the wrong thing — what matters is what the app sounds like, and where each callout comes
from. That decision is also what makes the whole pipeline cheap: no screen capture, no emulator
GPU, no video editing.

Guides are generated, not hand-edited, so they can be rebuilt when the app changes.

## Building one

```bash
scripts/build-tutorial.py tutorials/hear-my-surroundings.yaml
```

Output lands in `build/tutorials/` as an `.opus` file and a `.txt` transcript.

You need a **debug build installed and running**, with its service up and the home screen showing —
the script drives the app, it doesn't launch it. It also needs `adb`, `maestro`, `ffmpeg`, and
`piper` (see [Narrator voice](#narrator-voice)).

## The script format

```yaml
name: hear-my-surroundings
title: Hearing your surroundings with Soundscape

gpx: app/src/test/resources/gpx/milngavie-centre.gpx
speed: 1.4

beats:
  - say: Welcome to Soundscape. This short guide introduces ...

  - say: The first button is My Location. Here is what that sounds like.
    tap: homeMyLocation
    listen: 12
```

A beat is narration, optionally followed by a button press and a window of the app's own audio:

| Key | Meaning |
| --- | --- |
| `say` | Narration text. Rendered to speech; also goes into the transcript. |
| `tap` | A `testTag` to tap, resolved by Maestro — the same tags the Maestro suite uses. |
| `listen` | Seconds to wait after the tap. Only has to be **long enough** — see below. |
| `gpx` | Where the app stands, or the track it walks — see [GPX replay]({% link developers/gpx-replay.md %}). |
| `speed` | Metres per second. **`0`, the default, means stand still** — see below. |
| `settle` | Seconds to wait for the arrival callouts before the first beat. |
| `setup` | Maestro flows to run before recording — see below. |
| `voice` | Piper model filename. Defaults to `alba.onnx`. |

### Setting the scene

Some beats need something to exist before they can demonstrate anything. Nearby Markers says
nothing at all on a device with no markers saved, so a guide that introduces it would play two
earcons and move on.

```yaml
setup:
  - flow: tutorials/setup/save-marker.yaml
```

Setup flows run **after** the GPX replay is in place, so anything location-dependent is created
where the app is actually standing. They are ordinary Maestro flows.

Replaying a fixed position is what makes a guide reproducible: the same spot announces the same
places, so rebuilding after a change produces a comparable guide rather than a different one.

### Stand still

`speed: 0` is the default, and these recordings depend on it. **A user who is walking gets
automatic callouts continuously**, so the audio from pressing a button is never surrounded by
silence and cannot be cut out cleanly — you end up splicing mid-sentence through some unrelated
announcement about a shop.

Standing still gives each button press a clean run of silence either side, which is what makes a
precise cut possible. It is also what a listener is doing while following a guide.

Arriving somewhere new sets off a burst of callouts about the surroundings, and none of that
belongs in the guide, so the script waits it out before the first beat. When standing still it
waits at least long enough for the geoengine's `StationaryDetector` to reach a verdict — that
detector works on net displacement over a **30 second** window, so nothing shorter will do.

### Waiting for the callout to end

`listen` is simply how long to wait after the tap, and it only has to be **long enough**. Where the
callout actually starts and stops is decided afterwards, from the recording, by trimming the
silence either side of it — which is only possible because the recording is stationary.

It is tempting to end the window by watching `TtsEngine`'s log for utterances completing, and that
does not work: those lines mark *synthesis* finishing, not playback. Synthesis is fast, so all four
parts of Ahead of Me complete within a couple of seconds while the audio then plays for another
twenty-five. The app knows when a callout is really over — `CalloutController`'s
`activeCalloutFlow` clears at exactly that moment — but does not expose it over adb.

So the wait is a bound rather than an answer, and the build checks the recording to see whether the
bound held:

* **`'X' was still making sound when its window closed`** — the callout outran its `listen`, and
  will bleed into the next beat's segment. Raise `listen` for that beat.
* **`'X' recorded no audible callout`** — the beat captured nothing. Either the button genuinely
  says nothing here (Nearby Markers with no markers saved), or something is wrong.

Both look at the audio rather than at the app's logs, deliberately. Every log-derived signal tried
here — the tag, a buffer count that turned out not to be cumulative, a baseline sampled too late —
produced false alarms while the audio was fine. Check the artefact.

## How it stays in sync

The guide is **assembled, not mixed**. Narration and the app never talk over each other, so the
output is a concatenation: what the narrator said, then what the app then did, beat after beat.

The obvious alternative — record the app, then mix narration in at predicted offsets — accumulates
drift from every bit of UI latency, and a guide that talks over its own callouts is useless.

Instead the script records the wall-clock window around each button press, captures continuously,
and afterwards cuts exactly those windows out of the recording. Two things fall out of that:

* There is no drift to correct, because every offset was measured rather than predicted.
* The dead air while Maestro starts up is simply never extracted, so it costs nothing.

The recording is lined up with wall-clock time using its own duration — the recording's length
tells you when it started, which is more reliable than guessing how long `scrcpy` took to warm up.
Windows are padded either side and then silence-trimmed, which absorbs any small residual error.

## Audio handling

Capture is via `scrcpy`, which records the device's output mix as the `shell` user:

```bash
scrcpy --no-video --no-playback --audio-source=playback --record=out.opus --no-window
```

Some things worth knowing:

* **It captures the whole device**, not just Soundscape — silence notifications before a take.
* **Write to `$HOME`, not `/tmp`.** `scrcpy` is usually a snap, and snaps get a private `/tmp`; a
  recording written there silently vanishes.
* `timeout` killing `scrcpy` with exit 124 is normal. It runs until killed and still finalises.

The narration is loudness-normalised, because it is speech and should be consistent. The app audio
gets a **single static gain instead** — loudness normalisation would ride the level up and down,
and the spatialisation being demonstrated lives in the differences between the channels and in how
loud one thing is relative to another. A constant gain leaves all of that intact.

Everything stays stereo throughout. Never downmix: the direction a callout comes from *is* the
thing being taught.

## Narrator voice

[Piper](https://github.com/rhasspy/piper) renders the narration, from `$PIPER_DIR` (default
`~/piper`). The default voice is `en_GB-alba-medium`, which is Scottish — fitting for the app, and
clearly distinct from the Android TTS voice the app itself speaks with. That distinction matters:
a listener needs to know instantly whether they are hearing the tutor or the app.

### Pronunciation

`tutorials/pronunciations.yaml` holds respellings applied **only to the text sent to the TTS**. The
script and the transcript keep the real spelling.

```yaml
Milngavie: Mulguy            # "mul-GUY", not "miln-GAY-vee"
```

Scottish place names are the main reason this exists — they are systematically unguessable from
their spelling, and a guide that mispronounces the town it is walking through loses the listener
immediately. Add to the shared file when you hit a new one; a guide can also override locally with
its own `pronunciations:` block.

## The transcript

Every build writes a `.txt` alongside the audio, with timestamps, the narration, and markers for
each stretch of app audio. It is a deliverable in its own right for this audience.

It is also the staleness check. The callouts for a fixed GPX track should not change unless the app
changed, so diffing transcripts between builds shows when a guide has gone out of date — which a
recording can't tell you on its own, since a wrong guide still plays perfectly.

## Localisation

Mostly free, and worth doing. The app's own callouts are already translated through Weblate, so
running the same track on a device set to another language makes the app narrate itself in that
language. Putting the narration strings through the same component localises the other half.
