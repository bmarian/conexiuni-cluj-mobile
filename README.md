# Ce autobuz iau? for Android

The [Ce autobuz iau?](https://github.com/bmarian/conexiuni-cluj) bus and tram tracker as an Android app, for Cluj-Napoca, București, Iași, Oradea and Constanța. Same buses and timetables as the website, but designed for mobile.

**Join the beta:** [join the testers group](https://groups.google.com/g/conexiuni-cluj-beta), then [get the app on Google Play](https://play.google.com/apps/testing/online.bmarian.bus). Needs Android 8 or newer.

<p align="center">
  <img src="readme/home.png" width="200" alt="Home with a followed bus, a reminder and favorite lines">
  <img src="readme/stop.png" width="200" alt="Departures from every platform of a metro station">
  <img src="readme/line.png" width="200" alt="A line's stops with the next arrivals">
  <img src="readme/plan.png" width="200" alt="A planned trip">
</p>

## What it does

- Live buses, trams and trolleybuses on the map
- The stops around you and their next buses, on the first screen
- Departures for any stop. Tracked buses get a live countdown, the rest show the timetable
- Stations with a metro, tram, trolleybus or bus icon
- Full timetables for every line
- A route planner: leave now, leave at, arrive by, or the last one today
- Step-by-step guidance for a planned trip, with voice
- Reminders for the next bus or the ones you take often, timed from where you are: a heads-up 10 minutes before you need to leave, then when to leave and when it comes. At the stop, or too late to walk there, just when it comes
- An alarm before your stop
- Favorite stops and lines
- A home screen widget for a stop
- Share a place to the app and it plans the trip there
- Operator news, and a notification when a line you follow changes
- Pick the city in Settings
- Works with the [Wear OS app](https://github.com/bmarian/conexiuni-cluj-wearos)
- Move your favorites and settings to another phone, the website or your watch by scanning a code

---

## Running it yourself

You'll need Android Studio with the Android SDK. The map needs no key.

```bash
./gradlew installDebug
```

```bash
./gradlew testDebugUnitTest
```

**With a watch connected**, pick the phone with `ANDROID_SERIAL=<serial> ./gradlew installDebug`. Both apps use the package name `online.bmarian.bus`, so a plain install replaces the watch app.

**The logic** is the website's, ported to Kotlin in [`logic/`](app/src/main/java/online/bus/bmarian/conexiuniclujmobile/logic). The parity tests run both on the same real data and fail if anything differs. Their fixtures come from [`tools/parity`](tools/parity).

**The watch app** has to be signed with the same key as this one, or they can't talk to each other.

**Releasing** needs a `keystore.properties` next to this README. Git ignores it.

```properties
storeFile=../keys/upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Phone version codes are 1xxxx and the watch's 2xxxx. `./gradlew bundleRelease` builds the bundle for Google Play and `./gradlew assembleRelease` the APK. The privacy policy for both apps is [`PRIVACY.md`](PRIVACY.md).

## Credits

| Source                                                                             | Used for                                                                  |
| ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| [Tranzy.ai](https://tranzy.ai/)                                                    | Live positions, lines and stops for Cluj-Napoca, Iași, Oradea and Constanța |
| [CTP Cluj-Napoca](https://ctpcj.ro/)                                               | Cluj-Napoca's timetables and news                                         |
| [gtfs.ro](https://gtfs.ro/)                                                        | Timetables for Iași, Oradea and Constanța                                 |
| [TPBI](https://tpbi.ro/)                                                           | Lines, timetables, live positions and news for București                  |
| [OpenStreetMap](https://www.openstreetmap.org/) contributors                       | Map data, walking routes, place search                                    |
| [OpenFreeMap](https://openfreemap.org/), [OpenMapTiles](https://openmaptiles.org/) | Map tiles                                                                 |
| [MapLibre](https://maplibre.org/)                                                  | The map                                                                   |
| [ZXing](https://github.com/zxing/zxing)                                            | QR codes                                                                  |
