# Tiles
I'm going to assume that we try and move to using protomaps in place of soundscape-backend. This is not a given, but it affects how we approach much of the tile handling.

At the highest level of zoom from planetiler, tiles are 1222m. This is four times the size of what we get from soundscape-backend. Because we want (and have) the same resolution of data, this means that we have to deal with 16 times the amount of data. Phones have got a lot faster since Soundscape iOS was written, so perhaps this is okay? THe other upside is that tiles need updated far less often. It takes a longer time to walk out of a 3x3 tile grid!

Tile update should be done:

1. Immediately after the first location is received from the provider.
2. When the current location leaves the 'current tile'. This will need some hysteresis to prevent constant updates if someone walks down the edge of the tile. Therefore, I think what we really want to update when leaving the current tile scaled up by a factor e.g. 25% wider. Instead of being on a timer, can we do these updates on the location flow?
3. Whenever the tile changes on the server. The use case here is that the phone never leaves a single tile (i.e. the user has a very small range) and they never shutdown the soundscape service. We still want the tile to be updated if the mapping data is updated. We can check the ETAG of the `.pmtiles` every 12 hours to see if the underlying file has changed. If not, then no need to update the tile. 

## How much memory does a decoded tile consume?
This is the most important metric as it affects memory usage and also should indicate processing time.

Taipei is a good test bed for dense tiles, as is Sao Paolo - though really we have to find where the densest tile in the world is for proper testing. There's a list [here](https://fred.dev.openstreetmap.org/density/) of the highest OSM density tiles, and although that doesn't directly correspond to the largest vector tile the top one does seem to challenge maplibre rendering [soundscape://3.84233,C11.49170](soundscape://3.84233%2C11.49170).

As noted in `mapping.md` the vector tiles have two types of compressions - coordinates and strings. We can also see that the `building` and `poi` layers are the largest consumers of memory. For POI it's almost certainly the strings that are consuming the memory and it probably makes sense to keep the strings in an array and only look them up when they are actually required. The coordinates will have to be de-compressed, at least to the integer position within the tile. Keeping the coordinates as 16 bit integers would be a memory improvement over 64 bit double latitude and longitudes. How easy would that be to do?

## What's innovative in a soundscape-backend tile?
The soundscape-backend tiles are GeoJSON, but contain some algorithmically generated extra points. The file that does this is `tilefunc.sql`. Two very important sets of features that it generates are:

1. `gd_intersection` which is the point at which highways/paths meet. This saves the soundscape app from calculating where roads meet.
2. `gd_entrance_list` which is a list of entrance locations for a building.

The iOS TileData contains lists of `pois`, `roads`, `paths`, `intersections` and `entrances`. I think the first three are fairly standard, just the last 2 that are custom. The data required to calculate these should exist in the vector tile too, but will take a little more effort from the soundscape app. 
