# Conexiuni Cluj for Android

The [Conexiuni Cluj](https://github.com/bmarian/conexiuni-cluj) bus and tram tracker as a native Android app, in Material 3 with your wallpaper's colours. It does everything [bus.bmarian.online](https://bus.bmarian.online) does, laid out like Google Maps: the map fills the screen, a search bar floats over it, and everything else lives in a sheet you pull up and down.

## What it does

- Every CTP bus and tram on the map, live, gliding between updates, with your position
- Nearby: the next buses at the stops around you, first thing on Home
- Departures for any stop. Tracked buses get a live countdown, the rest show the timetable
- A line's stops with the next three arrivals at each, its timetable by day, and any run's time at every stop
- A route planner: leave now, leave at, arrive by, or the last one today, with live times for the first bus
- Favorite stops, lines per direction and destinations, in the order you drag them into
- Search for lines, stops and places, or paste a Google Maps link
- Follow a bus: a live countdown in the status bar (a Live Update on Android 16), a heads-up two minutes before and when it comes, and it lets go once the bus has left
- Navigation: Start on a planned trip for a turn-by-turn screen like Maps' transit navigation: the step in a banner, what comes next under it, the map following you tilted, and when you get there. A Live Update keeps going when you put it away: walk to the stop, the bus's live countdown, the stops left, a heads-up when yours is next, then the next leg or the walk to the end
- Wake me before my stop: pick the stop on a line page and get the same heads-up one stop before it
- Remind me to leave: pick one of the next buses and how early to hear about it; the alarm counts the walk to the stop
- With the watch app: follow a bus or take a journey on the phone and it opens on the watch, the journey with a ring for how far it has come and a map of the ride; a bus followed on the watch shows on the phone. Stop it on either and it stops on both. Favourites stay the same on both on their own, and a stop or line opens on the watch from the phone's menu
- A home-screen widget with a stop's next departures, launcher shortcuts for Search, Plan a trip and favourite stops, and a stop pinned to the home screen
- Share a place from Google Maps or a chat to the app and it plans the trip there
- Optional: arrive at a favourite stop and a quiet notification shows its next buses
- Follow a line and get a notification when CTP changes its timetable or route
- Weather, CTP news and the Green Friday reminder, like the website
- Move favorites and settings between the app, the website and the watch with a code or a QR scan
- Opens bus.bmarian.online links for stops, lines, plans and transfer codes

## How it works

It talks to the same API as the website and keeps the same data on the phone. The whole network (lines, stops, timetables and the learned travel times for every hour) comes down as one bundle from `/api/network`, checked against `/api/network/version` when the app comes back and fetched again only when the lines changed or the copy is half a day old ([`data/Network.kt`](app/src/main/java/online/bus/bmarian/conexiuniclujmobile/data/Network.kt)). Stops, lines and timetables open from it without a request, exactly as the server would answer (`NetworkTest` checks that against a captured answer); only the buses come live, from the version 2 stream, and shapes as polylines kept until the bundle says they changed.

Everything the website works out in the browser is worked out on the phone, ported from TypeScript to Kotlin in [`logic/`](app/src/main/java/online/bus/bmarian/conexiuniclujmobile/logic): arrival times, live estimates from the bus positions, the planner's grouping, alternatives, connections and times, and the timetable with its learned delays. The arrival logic is shared with the [watch app](https://github.com/bmarian/conexiuni-cluj-wearos).

Three tests run the website's own code and the port on the same snapshots of real data, and fail if anything differs:

- `ParityTest`: departures at stops and arrivals along lines
- `PlannerParityTest`: the planner's plans and times in every time mode
- `TimetableParityTest`: the timetable grid with its learned offsets, and the trip view

The fixtures for the last two come from [`tools/parity`](tools/parity): `fetch.mjs` and `fetch_timetables.mjs` save live answers from the API, and the two Python scripts lift the planning and timetable code out of `RoutePlanningView.vue` and `RouteView.vue` and run it under Node (23+, for `--experimental-strip-types`) with `TZ=Europe/Bucharest`.

The map is [MapLibre](https://maplibre.org/) with [OpenFreeMap](https://openfreemap.org/)'s vector tiles: its light and dark styles are bundled in [`assets/styles`](app/src/main/assets/styles), the tiles, fonts and icons come from OpenFreeMap and stay in MapLibre's cache. The website's routes, stops, buses and pins are drawn on top as GeoJSON layers ([`ui/map/`](app/src/main/java/online/bus/bmarian/conexiuniclujmobile/ui/map)), with the marker art turned into bitmaps. MapLibre zoom levels are one below the website's Leaflet ones.

A followed bus is worked out by whichever device started following it, phone or watch, the same way as the stop page. That device sends each change to the other over the Wear OS data layer ([`follow/`](app/src/main/java/online/bus/bmarian/conexiuniclujmobile/follow)), and the other shows it in its notifications and, on the watch, on the watch face. Only one bus is followed at a time across both: a new follow on either one replaces the old one.

A journey ([`journey/`](app/src/main/java/online/bus/bmarian/conexiuniclujmobile/journey)) runs on the phone. It follows the phone's position along the trip's stops to tell walking, waiting and riding apart and to count the stops left; without the location it goes by the bus times and the planned ride time. At the start the whole trip, with its paths, goes to the watch (`/journey/plan`, asked for again with `/journey/plan/request` if the watch missed it); after that each step goes as `/journey/state`, which the watch buzzes for. The navigation screen ([`ui/nav/`](app/src/main/java/online/bus/bmarian/conexiuniclujmobile/ui/nav)) only reads the journey's state; closing it leaves the journey running.

Favourite stops and lines sync through a shared data item: the newest change wins, and a fresh install never overwrites the other device's.

The data layer only connects apps with the same package name and signing key, so this app uses the watch app's id, `online.bmarian.bus`, and must be signed with the same key. The phone and watch builds can share one Play listing; give them version codes that don't collide.

The server sends followed-line changes as browser web push only, so the app checks for them itself every hour and posts the same notification, between 08:00 and 20:00.

## Building it

You'll need Android Studio with the Android SDK.

The map needs no key. Run the `app` configuration from Android Studio, or:

```bash
./gradlew installDebug
```

```bash
./gradlew testDebugUnitTest
```

runs the parity tests, and the map and journey protocol tests.

To try following between devices on emulators, pair a Wear OS emulator with a phone emulator that has the Play Store (Android Studio: Device Manager, then **Pair Wearable**), and install both apps from the same machine so they share the debug key.

`installDebug` installs on every connected device, and both apps share one package name: with a watch connected it replaces the watch app. Pick the device with `ANDROID_SERIAL=emulator-5554 ./gradlew installDebug`, or `adb -s <serial> install -r` the APK.

To try the app against a backend running on this PC: `adb reverse tcp:6698 tcp:6698`, then build with `-PapiBase=http://localhost:6698/api/` (debug builds only; plain http is allowed to `localhost` there). An emulator's `10.0.2.2` doesn't work for this: Android 17 keeps apps off private addresses without a permission.

Pairing emulators needs the Wear OS app and a Google account on the phone emulator. Without them, install the debug builds of both apps and run `python tools/wear-relay.py`: debug builds log every phone–watch message, and the script hands each one to the other emulator, so following, journeys and favourites can be tried end to end. Only the Bluetooth link itself is left out.

## Links

bus.bmarian.online links open in the app without asking: the site's `/.well-known/assetlinks.json` names this package and the release key's certificate (`ANDROID_CERT_SHA256` in the website's `.env`). A build signed with another key, the debug one included, opens them in the browser unless "Open supported links" is turned on in the app's settings.

## Releasing

Both apps are signed with one release key (the data layer and the site's app links depend on it). Point a `keystore.properties` next to this README at it, the same as the watch app's. Git ignores the file:

```properties
storeFile=../keys/upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Raise `versionCode` in [`app/build.gradle.kts`](app/build.gradle.kts) for every upload. Before a release, record the baseline profile on a phone emulator (English, with a location near Cluj stops; the run reinstalls the app there), so startup and the first screens are compiled ahead:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateBaselineProfile
```

Then:

```bash
./gradlew bundleRelease
```

Without the upload key the release build is signed with the debug key, so it still installs from Android Studio.

## Credits

Same sources as [the main app](https://github.com/bmarian/conexiuni-cluj#credits): live positions and GTFS from [Tranzy.ai](https://tranzy.ai/), timetables from [CTP Cluj-Napoca](https://www.ctpcj.ro/), weather from [Open-Meteo](https://open-meteo.com/), map data © [OpenStreetMap](https://www.openstreetmap.org/) contributors, tiles by [OpenFreeMap](https://openfreemap.org/) and [OpenMapTiles](https://openmaptiles.org/), drawn by [MapLibre](https://maplibre.org/), place search by [Nominatim](https://nominatim.org/), routing by [OpenTripPlanner](https://www.opentripplanner.org/). QR codes by [ZXing](https://github.com/zxing/zxing).
