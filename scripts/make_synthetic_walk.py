"""Make a synthetic walking GPX along an OSM route relation, for the replay tests in MvtTileTest.

The walk starts at START (a car park, say), joins the relation where it comes nearest, follows its
members in order at 1.3m/s with one fix a second and a few metres of wandering GPS error (seeded by
the relation id, so the same every time), and goes back to START. The relation's geometry comes from
an OSM extract via GDAL:

  ogr2ogr -f GeoJSON relations.geojson scotland.osm.pbf multilinestrings \
      -spat -4.45 56.15 -4.33 56.22 -where "type='route'" -oo INTERLEAVED_READING=NO

usage: make_synthetic_walk.py relations.geojson RELATION_ID START_LON START_LAT out.gpx "Track name"

e.g. gpxFiles/LochArdSculptureTrail-synthetic.gpx, the red route (relation 2866086) from Milton
Forest Car Park:
  make_synthetic_walk.py relations.geojson 2866086 -4.4192864 56.1787049 \
      LochArdSculptureTrail-synthetic.gpx "Loch Ard Sculpture Trail synthetic"
"""
import json, math, random, sys
from datetime import datetime, timedelta, timezone

src, rel_id, slon, slat, out, title = sys.argv[1:7]
start = [float(slon), float(slat)]
rel = next(f for f in json.load(open(src))['features'] if f['properties'].get('osm_id') == rel_id)
parts = [list(p) for p in rel['geometry']['coordinates']]

KX = 111320 * math.cos(math.radians(start[1])); KY = 111320
def d(a, b): return math.hypot((a[0] - b[0]) * KX, (a[1] - b[1]) * KY)

# Chain the relation's pieces, flipping each to start where the last one ended
line = parts[0][:]
if len(parts) > 1 and (d(parts[0][0], parts[1][0]) < 1 or d(parts[0][0], parts[1][-1]) < 1):
    line.reverse()
for p in parts[1:]:
    if d(line[-1], p[-1]) < d(line[-1], p[0]):
        p = p[::-1]
    line += p[1:]

# Start (and finish) at the car park: rotate the loop to begin at its point nearest it
i0 = min(range(len(line)), key=lambda i: d(line[i], start))
if d(line[0], line[-1]) < 1:
    line = line[i0:] + line[1:i0 + 1]
    line = [start] + line + [start]
else:
    # Not a loop: there and back again
    line = [start] + line + line[-2::-1] + [start]

# Resample at walking pace, one fix a second
SPEED = 1.3
pts, carry = [], 0.0
for a, b in zip(line, line[1:]):
    seg = d(a, b)
    t = carry
    while t < seg:
        f = t / seg
        pts.append([a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f])
        t += SPEED
    carry = t - seg
pts.append(line[-1])

# Correlated GPS error: a slowly wandering offset of a few metres, plus a little jitter
rng = random.Random(2866086)
ex = ey = 0.0
t0 = datetime(2026, 11, 21, 10, 0, 0, tzinfo=timezone.utc)
with open(out, 'w') as g:
    g.write("<?xml version='1.0' encoding='utf-8'?>\n")
    g.write('<gpx xmlns="http://www.topografix.com/GPX/1/0" version="1.0" creator="Soundscape" recorderVersion="3" locationStream="raw">\n')
    g.write(f'<trk>\n<name>{title}</name>\n<number>0</number>\n<trkseg>\n')
    for n, (lon, lat) in enumerate(pts):
        ex = 0.97 * ex + rng.gauss(0, 0.6); ey = 0.97 * ey + rng.gauss(0, 0.6)
        jx, jy = rng.gauss(0, 0.8), rng.gauss(0, 0.8)
        lon_n = lon + (ex + jx) / KX; lat_n = lat + (ey + jy) / KY
        acc = round(4.0 + abs(rng.gauss(0, 2.0)) + math.hypot(ex, ey) * 0.5, 3)
        spd = round(max(0.0, SPEED + rng.gauss(0, 0.1)), 3)
        ts = (t0 + timedelta(seconds=n)).strftime('%Y-%m-%dT%H:%M:%S.000Z')
        g.write(f'<trkpt lat="{lat_n:.7f}" lon="{lon_n:.7f}">\n<ele>0.0</ele>\n<accuracy>{acc}</accuracy>\n'
                f'<speed>{spd}</speed>\n<time>{ts}</time>\n</trkpt>\n')
    g.write('</trkseg>\n</trk>\n</gpx>\n')
total = sum(d(a, b) for a, b in zip(line, line[1:]))
print(f'{len(pts)} points, {total:.0f} m, {len(pts) / 60:.0f} min, first {pts[0]}, loop start {line[1]}')
