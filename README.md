# Ce autobuz iau? for Android

The [Ce autobuz iau?](https://github.com/bmarian/conexiuni-cluj) bus, tram and trolleybus tracker as an Android app.

**Join the beta:** [join the testers group](https://groups.google.com/g/ce-autobuz-iau-beta), then [get the app on Google Play](https://play.google.com/apps/testing/online.bmarian.bus). Needs Android 8 or newer.

<p align="center">
  <img src="readme/home.png" width="200" alt="Home in light and dark: the dorm, UTCN and the library saved as places, favorite lines and stops">
  <img src="readme/stop.png" width="200" alt="The stop by the dorm in light and dark, with the 35 and 46B live on the map">
  <img src="readme/line.png" width="200" alt="Line 35 in light and dark, with the next arrivals at every stop">
  <img src="readme/plan.png" width="200" alt="A trip from the dorm to UTCN in light and dark, with a change from the 40 to the 25">
</p>

## What it does

- Live buses, trams and trolleybuses on the map
- Departures for any stop. Tracked buses get a live countdown, the rest show approximate times
- Full timetables for every line
- A route planner: leave now, leave at, arrive by, or the last one today
- Step-by-step guidance for a planned trip, with voice, and an alarm before your stop
- Follow a bus, the countdown stays in your notifications and the phone buzzes when it comes
- Reminders for the buses you take often, timed from where you are so you know when to leave
- Favorite stops, places and lines
- A home screen widget for a stop
- Share a place to the app and it plans the trip there
- Operator news, and a notification when a line you follow changes
- Works with the [Wear OS app](https://github.com/bmarian/conexiuni-cluj-wearos), followed buses, trips, reminders and favorites show on both
- Move your favorites and settings to another phone, the website or your watch by scanning a QR

The cities and where their data comes from are listed in the [website's README](https://github.com/bmarian/conexiuni-cluj#cities).

---

## Running it yourself

You'll need Android Studio with the Android SDK. The map needs no key.

```bash
./gradlew installDebug
```

```bash
./gradlew testDebugUnitTest
```

<details>
<summary><b>With a watch connected</b></summary>

<br>

Pick the phone with `ANDROID_SERIAL=<serial> ./gradlew installDebug`. Both apps use the package name `online.bmarian.bus`, so a plain install replaces the watch app. The watch app has to be signed with the same key as this one, or they can't talk to each other.

`-PapiBase=http://localhost:6698/api/` points a debug build at a local backend (run `adb reverse tcp:6698 tcp:6698` too).

</details>

<details>
<summary><b>Parity with the website</b></summary>

<br>

The timetable and departure logic is the website's, ported to Kotlin in [`logic/`](app/src/main/java/online/bus/bmarian/conexiuniclujmobile/logic). The parity tests run both on the same real data and fail if anything differs. Their fixtures come from [`tools/parity`](tools/parity).

</details>

<details>
<summary><b>Releasing</b></summary>

<br>

Releasing needs a `keystore.properties` next to this README. Git ignores it.

```properties
storeFile=../keys/upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

Phone version codes are 1xxxx and the watch's 2xxxx. `./gradlew bundleRelease` builds the bundle for Google Play. Release builds need NDK 28.2.13676358, which ships MapLibre's debug symbols with the bundle. The privacy policy for both apps is [`PRIVACY.md`](PRIVACY.md).

</details>

## Credits

| Source                                                                             | Used for                                                     |
| ---------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors              | Map data, walking routes and place search                    |
| [OpenFreeMap](https://openfreemap.org/), [OpenMapTiles](https://openmaptiles.org/) | Map tiles                                                    |
| [MapLibre](https://maplibre.org/)                                                  | The map                                                      |
| [ZXing](https://github.com/zxing/zxing)                                            | QR codes                                                     |
