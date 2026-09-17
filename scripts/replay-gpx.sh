#!/usr/bin/env bash
#
# Drive a debug build of Soundscape over a recorded GPX track instead of the phone's GPS.
# See docs/developers/gpx-replay.md.
#
#   scripts/replay-gpx.sh walk.gpx [speed] [loop]
#   scripts/replay-gpx.sh --stop
#
# The app must already be running with its service up - the replay swaps the providers of a live
# service rather than starting one.

set -euo pipefail

PACKAGE=org.scottishtecharmy.soundscape
ACTION="${PACKAGE}.REPLAY_GPX"
REMOTE_DIR="/sdcard/Android/data/${PACKAGE}/files/gpx"

usage() {
    echo "usage: $(basename "$0") <track.gpx> [speed m/s] [loop true|false]" >&2
    echo "       $(basename "$0") --stop" >&2
    exit 2
}

[ $# -ge 1 ] || usage

if [ "$1" = "--stop" ]; then
    echo "Stopping GPX replay"
    adb shell am start -a "$ACTION" >/dev/null
    exit 0
fi

GPX="$1"
SPEED="${2:-1.4}"
LOOP="${3:-false}"

[ -f "$GPX" ] || { echo "No such file: $GPX" >&2; exit 1; }

# A release build doesn't declare the action at all, so the failure would otherwise be a confusing
# "no activity found" from am.
if ! adb shell pm list packages | grep -q "^package:${PACKAGE}$"; then
    echo "$PACKAGE is not installed on the connected device" >&2
    exit 1
fi

echo "Pushing $(basename "$GPX") to $REMOTE_DIR"
adb shell mkdir -p "$REMOTE_DIR"
adb push "$GPX" "$REMOTE_DIR/" >/dev/null

echo "Replaying $(basename "$GPX") at ${SPEED} m/s (loop=${LOOP})"
adb shell am start -a "$ACTION" \
    -e gpx "$(basename "$GPX")" \
    --ef speed "$SPEED" \
    --ez loop "$LOOP" >/dev/null

echo "Watch it with: adb logcat -s GpxDrivenProvider:D SoundscapeIntents:D"
