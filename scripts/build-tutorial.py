#!/usr/bin/env python3
"""Build a narrated audio guide from a tutorial script - see docs/developers/audio-tutorials.md.

The guide is *assembled*, not mixed: narration and the app's own audio never overlap, so the
output is a concatenation of "what the narrator said" and "what the app then did". That choice is
what keeps it in sync. The alternative - mixing narration in at predicted offsets - accumulates
drift from every bit of UI latency, and a guide that talks over its own callouts is useless.

Because the segments are cut from the recording using wall-clock windows measured during the run,
the dead air while Maestro starts up is simply never extracted, and there is no drift to correct.

    scripts/build-tutorial.py tutorials/hear-my-surroundings.yaml

Needs: a debug build installed and running, adb, maestro, ffmpeg, and piper (see PIPER_DIR).
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

import yaml

PIPER_DIR = Path(os.environ.get("PIPER_DIR", Path.home() / "piper"))
PACKAGE = "org.scottishtecharmy.soundscape"

# Where the capture is written, which snap confinement constrains twice over:
#   - snaps get a private /tmp, so a recording written there silently vanishes, and
#   - the snap `home` interface grants no access to *hidden* directories, which rules out the
#     ~/.cache location this would otherwise want to use.
# Hence a plain, visible directory under $HOME.
CAPTURE_DIR = Path(os.environ.get("TUTORIAL_CAPTURE_DIR", Path.home() / "soundscape-tutorials"))

# How much to widen each app-audio window by, to absorb the small error in lining the recording up
# with wall-clock time. Silence is trimmed from the segment afterwards, so generous padding costs
# nothing but makes it very unlikely that the start of a callout is clipped.
PAD_BEFORE_S = 1.0
PAD_AFTER_S = 2.0

# Narration is levelled to a fixed loudness because it is speech and should be consistent. The app
# audio gets a *single static gain* instead: loudness normalisation would ride the level up and
# down, and the spatialisation being demonstrated lives in the differences between the channels
# and in how loud a thing is relative to another. A constant gain leaves all of that intact.
NARRATION_LUFS = -16.0
APP_AUDIO_LUFS = -18.0

def run(cmd, **kwargs):
    """Run a command, raising with its output if it fails."""
    return subprocess.run(cmd, check=True, capture_output=True, text=True, **kwargs)


def ffprobe_duration(path):
    """Seconds of audio, or 0.0 if there are none.

    A segment that silence-trimmed down to nothing has no duration field at all, which is a thing
    to skip over rather than crash on - the warning about hearing nothing has already been printed
    by then, and it is more useful to finish the guide and let its gaps be heard.
    """
    out = run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
               "-of", "json", str(path)]).stdout
    return float(json.loads(out)["format"].get("duration") or 0.0)


def check_window_ended_quietly(capture, window_end_s, capture_len, label):
    """Warn if the app was still talking when a window closed.

A window is a fixed wait, so a callout longer than its `listen:` is still going when the window
    closes - and that audio then bleeds into the next beat's segment, where it sounds like the
    narrator interrupting the app. There is no signal over adb for playback finishing (the app
    knows, via CalloutController's activeCalloutFlow, but does not expose it), so rather than
    trusting the wait to be long enough, check the tail of the window and say so when it wasn't.
    """
    start = max(0.0, min(window_end_s, capture_len) - 1.0)
    if start >= capture_len:
        return
    proc = subprocess.run(
        ["ffmpeg", "-nostdin", "-hide_banner", "-ss", f"{start:.3f}", "-t", "1.0",
         "-i", str(capture), "-af", "astats=metadata=1:reset=0", "-f", "null", "-"],
        capture_output=True, text=True,
    )
    levels = re.findall(r"RMS level dB:\s+(-?\d+\.\d+|-inf)", proc.stderr)
    if not levels:
        return
    loudest = max((float(x) for x in levels if x != "-inf"), default=float("-inf"))
    if loudest > -50.0:
        print(f"      WARNING: '{label}' was still making sound when its window closed "
              f"({loudest:.0f} dB in the last second) - it will bleed into the next segment. "
              f"Raise `listen:` for this beat.")


def segment_is_silent(path):
    """Whether an extracted segment holds no audible callout.

    Checked against the audio rather than the app's logs, because the logs describe synthesis and
    what matters here is what was actually recorded. A beat can be genuinely silent - Nearby
    Markers on a device with no markers saved says nothing at all - and that is worth being told
    about loudly, since the guide will introduce a feature and then play nothing.
    """
    if ffprobe_duration(path) < 0.15:
        return True
    proc = subprocess.run(
        ["ffmpeg", "-nostdin", "-hide_banner", "-i", str(path),
         "-af", "astats=metadata=1:reset=0", "-f", "null", "-"],
        capture_output=True, text=True,
    )
    levels = [float(x) for x in re.findall(r"RMS level dB:\s+(-?\d+\.\d+)", proc.stderr)]
    return (not levels) or (max(levels) < -50.0)


def measure_loudness(path):
    """Integrated loudness in LUFS, via ffmpeg's ebur128 filter."""
    proc = subprocess.run(
        ["ffmpeg", "-hide_banner", "-i", str(path), "-af", "ebur128", "-f", "null", "-"],
        capture_output=True, text=True,
    )
    matches = re.findall(r"I:\s+(-?\d+\.\d+)\s+LUFS", proc.stderr)
    if not matches:
        raise RuntimeError(f"Could not measure loudness of {path}")
    return float(matches[-1])


def load_pronunciations(script_path, spec):
    """Shared respellings, plus any the script adds itself. Later entries win."""
    merged = {}
    shared = script_path.parent / "pronunciations.yaml"
    if shared.exists():
        merged.update(yaml.safe_load(shared.read_text()) or {})
    merged.update(spec.get("pronunciations") or {})
    return merged


def apply_pronunciations(text, pronunciations):
    """Respell words for the TTS only - the transcript keeps the real spelling.

    Longest key first, so "Milngavie's" is matched before "Milngavie" would eat its stem.
    """
    for word in sorted(pronunciations, key=len, reverse=True):
        text = re.sub(rf"\b{re.escape(word)}\b", pronunciations[word], text, flags=re.IGNORECASE)
    return text


def synthesize(text, out_path, voice):
    """Render narration to a wav with piper."""
    model = PIPER_DIR / voice
    if not model.exists():
        sys.exit(f"Voice model not found: {model}\nSet PIPER_DIR or fix `voice:` in the script.")
    env = {**os.environ, "LD_LIBRARY_PATH": str(PIPER_DIR)}
    subprocess.run(
        [str(PIPER_DIR / "piper"), "-m", str(model), "-f", str(out_path)],
        input=text, text=True, capture_output=True, env=env, check=True,
    )


def maestro_run(flow_path):
    """Run a whole Maestro flow, used for a script's `setup:` steps."""
    proc = subprocess.run(["maestro", "test", str(flow_path)], capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"setup flow '{flow_path}' failed:\n{proc.stdout[-2000:]}")


def maestro_tap(test_tag, work_dir):
    """Tap a testTag. One Maestro run per tap: slow to start, but it resolves testTags properly,
    and its startup happens outside every window we extract so it costs nothing in the output."""
    flow = work_dir / "tap.yaml"
    flow.write_text(f"appId: {PACKAGE}\n---\n- tapOn:\n    id: {test_tag}\n")
    proc = subprocess.run(["maestro", "test", str(flow)], capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError(f"maestro failed to tap '{test_tag}':\n{proc.stdout[-2000:]}")


def start_replay(gpx, speed):
    remote_dir = f"/sdcard/Android/data/{PACKAGE}/files/gpx"
    run(["adb", "shell", "mkdir", "-p", remote_dir])
    run(["adb", "push", str(gpx), remote_dir + "/"])
    run(["adb", "shell", "am", "start", "-a", f"{PACKAGE}.REPLAY_GPX",
         "-e", "gpx", Path(gpx).name, "--ef", "speed", str(speed), "--ez", "loop", "true"])


def stop_replay():
    subprocess.run(["adb", "shell", "am", "start", "-a", f"{PACKAGE}.REPLAY_GPX"],
                   capture_output=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("script", type=Path, help="tutorial YAML")
    parser.add_argument("-o", "--out-dir", type=Path, default=Path("build/tutorials"))
    parser.add_argument("--keep", action="store_true", help="keep intermediate files")
    args = parser.parse_args()

    for tool in ("adb", "maestro", "ffmpeg", "ffprobe", "scrcpy"):
        if not shutil.which(tool):
            sys.exit(f"{tool} not found on PATH")

    spec = yaml.safe_load(args.script.read_text())
    name = spec.get("name", args.script.stem)
    voice = spec.get("voice", "alba.onnx")
    beats = spec["beats"]

    CAPTURE_DIR.mkdir(parents=True, exist_ok=True)
    work = CAPTURE_DIR / name
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    args.out_dir.mkdir(parents=True, exist_ok=True)

    # 1. Narration first. Doing this up front means a typo fails in seconds rather than after a
    #    four minute recording, and the durations are needed before anything is captured.
    pronunciations = load_pronunciations(args.script, spec)
    print(f"Rendering narration with {voice} ({len(pronunciations)} respellings) ...")
    for i, beat in enumerate(beats):
        if beat.get("say"):
            path = work / f"say-{i:02d}.wav"
            spoken = apply_pronunciations(" ".join(beat["say"].split()), pronunciations)
            synthesize(spoken, path, voice)
            beat["_narration"] = path
            beat["_narration_s"] = ffprobe_duration(path)
            print(f"  beat {i}: {beat['_narration_s']:5.1f}s  {beat['say'][:58]}...")

    # 2. Put the app at a known place so the callouts are reproducible. Standing still (speed 0)
    #    is the default and what these recordings want: a user who is walking gets automatic
    #    callouts continuously, so the audio from a button press is never surrounded by silence
    #    and can't be cut out cleanly.
    speed = spec.get("speed", 0.0)
    if spec.get("gpx"):
        where = "Standing still at" if speed <= 0 else f"Walking at {speed} m/s along"
        print(f"{where} {spec['gpx']}")
        start_replay(spec["gpx"], speed)

    # 2b. Anything the guide needs to exist before it is recorded - saving a marker, say, so that
    #     a beat demonstrating Nearby Markers has something to announce. Runs after the replay so
    #     that anything location-dependent is created where the app is actually standing.
    for step in spec.get("setup") or []:
        flow = step["flow"] if isinstance(step, dict) else step
        print(f"Setup: {flow}")
        maestro_run(flow)

    if spec.get("gpx"):
        # Arriving somewhere new sets off a burst of callouts about the surroundings, and the
        # setup flow above adds its own. None of that belongs in the guide, so wait it out before
        # the first beat - and when standing still, wait at least as long as StationaryDetector's
        # 30s window needs to reach a verdict.
        settle = spec.get("settle", 35 if speed <= 0 else 5)
        print(f"Letting the arrival callouts finish ({settle}s cap) ...")
        time.sleep(settle)

    # 3. Capture continuously. Windows get cut out of this afterwards.
    capture = work / "capture.opus"
    print("Starting audio capture ...")
    scrcpy_log = work / "scrcpy.log"
    with open(scrcpy_log, "w") as log:
        scrcpy = subprocess.Popen(
            ["scrcpy", "--no-video", "--no-playback", "--audio-source=playback",
             f"--record={capture}", "--no-window"],
            stdout=log, stderr=subprocess.STDOUT,
        )
    time.sleep(6)  # let the capture settle before the first window
    if scrcpy.poll() is not None:
        sys.exit(f"scrcpy exited immediately:\n{scrcpy_log.read_text()[-1500:]}\n"
                 f"(if it mentions permissions, note that the snap cannot write to hidden "
                 f"directories or to /tmp - see TUTORIAL_CAPTURE_DIR)")

    # 4. Walk the beats, noting exactly when each window of app audio starts and ends.
    try:
        for i, beat in enumerate(beats):
            if not beat.get("tap"):
                continue
            cap = beat.get("listen", 45)
            print(f"  beat {i}: tap {beat['tap']}, listening {cap}s")
            # The window opens *before* Maestro runs, because the callout starts the moment the
            # button is tapped and Maestro takes a moment to tear down afterwards - opening it on
            # return would clip the start. Maestro itself makes no sound, so the extra seconds are
            # silence, and silence is trimmed off the segment anyway.
            beat["_win_start"] = time.time()
            maestro_tap(beat["tap"], work)
            # Just wait. The window only has to be *long enough* - where the callout actually
            # starts and stops is decided afterwards, from the recording, by trimming the silence
            # either side of it. Standing still is what makes that silence exist.
            time.sleep(cap)
            beat["_win_end"] = time.time()
    finally:
        capture_stopped_at = time.time()
        scrcpy.terminate()
        try:
            scrcpy.wait(timeout=15)
        except subprocess.TimeoutExpired:
            scrcpy.kill()
        stop_replay()

    # 5. Line the recording up with wall-clock time. The recording's own length tells us when it
    #    began, which is more reliable than guessing how long scrcpy took to start up.
    capture_len = ffprobe_duration(capture)
    capture_started_at = capture_stopped_at - capture_len
    print(f"Captured {capture_len:.1f}s")

    for beat in beats:
        if beat.get("_win_end"):
            check_window_ended_quietly(
                capture, beat["_win_end"] - capture_started_at, capture_len, beat["tap"]
            )

    app_gain_db = APP_AUDIO_LUFS - measure_loudness(capture)
    print(f"App audio gain: {app_gain_db:+.1f} dB (single static gain, dynamics preserved)")

    # 6. Assemble: narration, then the app audio that followed it, beat after beat.
    segments, transcript = [], []
    running = 0.0

    def add(seg_path, label, text=None):
        nonlocal running
        seconds = ffprobe_duration(seg_path)
        if seconds < 0.15:
            return
        segments.append(seg_path)
        transcript.append((running, label, text))
        running += seconds

    for i, beat in enumerate(beats):
        if beat.get("_narration"):
            out = work / f"seg-{i:02d}-say.wav"
            run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(beat["_narration"]),
                 "-af", f"loudnorm=I={NARRATION_LUFS}:TP=-1.5:LRA=11,aresample=48000",
                 "-ac", "2", "-ar", "48000", str(out)])
            add(out, "narrator", " ".join(beat["say"].split()))

        if beat.get("_win_start"):
            start = beat["_win_start"] - capture_started_at - PAD_BEFORE_S
            end = beat["_win_end"] - capture_started_at + PAD_AFTER_S
            start = max(0.0, start)
            out = work / f"seg-{i:02d}-app.wav"
            run(["ffmpeg", "-y", "-loglevel", "error", "-ss", f"{start:.3f}",
                 "-to", f"{min(end, capture_len):.3f}", "-i", str(capture),
                 # Static gain only - no compression, so the spatial image is untouched. Silence is
                 # trimmed from both ends, which is also what absorbs the window padding above.
                 "-af", f"volume={app_gain_db:.2f}dB,"
                        "silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.2,"
                        "areverse,"
                        "silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.2,"
                        "areverse",
                 "-ac", "2", "-ar", "48000", str(out)])
            if segment_is_silent(out):
                print(f"      WARNING: '{beat['tap']}' recorded no audible callout - the guide "
                      f"will introduce it and then play nothing.")
            add(out, f"app ({beat['tap']})")

    if not segments:
        sys.exit("Nothing was captured - is the app running with its service up?")

    concat_list = work / "concat.txt"
    concat_list.write_text("".join(f"file '{p.resolve()}'\n" for p in segments))
    audio_out = args.out_dir / f"{name}.opus"
    run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0",
         "-i", str(concat_list), "-c:a", "libopus", "-b:a", "96k", str(audio_out)])

    # 7. The transcript is a deliverable in its own right for this audience, and it also makes it
    #    obvious when a guide has gone stale - the callouts for a fixed track should not change.
    text_out = args.out_dir / f"{name}.txt"
    lines = [spec.get("title", name), "=" * len(spec.get("title", name)), ""]
    for offset, label, text in transcript:
        stamp = f"[{int(offset // 60):02d}:{offset % 60:05.2f}]"
        lines.append(f"{stamp} {label}: {text}" if text else f"{stamp} {label}")
    text_out.write_text("\n".join(lines) + "\n")

    total = ffprobe_duration(audio_out)
    print(f"\n{audio_out}  ({int(total // 60)}m {total % 60:04.1f}s)")
    print(f"{text_out}")
    if not args.keep:
        shutil.rmtree(work)


if __name__ == "__main__":
    main()
