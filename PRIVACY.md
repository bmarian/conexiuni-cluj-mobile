# Privacy policy

Conexiuni Cluj for Android and its Wear OS app show live bus and tram departures in Cluj-Napoca and plan trips. They have no accounts, no ads and no analytics or tracking libraries.

## Location

With your permission, the app uses your location on the phone: to show the stops near you and where you are on the map, to plan trips from where you are, to tell you where to get off during a trip, and to time your walk to the stop for a reminder.

If you also allow location in the background, a reminder can time that walk while the app is closed. Without it, the reminder comes 20 minutes before the bus.

Your location only leaves the phone when you plan a trip from where you are: the start, rounded to about 10 metres, and the destination go to [bus.bmarian.online](https://bus.bmarian.online), which works out the trip.

## Search

Lines and stops are searched on the phone.

Places are looked up on [Photon](https://photon.komoot.io), a free service by Komoot built on OpenStreetMap data. It receives what you type, or the name in something you share to the app, limited to the Cluj area, and never your location. When you drag the destination pin on the trip map, its coordinates go to Photon to name the spot.

## Notifications and reminders

Followed buses, trips and reminders are worked out on the phone. Reminders are kept on the phone and use alarms to wake the app in time.

If you follow a line's changes, the app asks bus.bmarian.online about those lines once an hour between 08:00 and 20:00, sending their numbers.

## What the app downloads

- Stops, lines, timetables, live bus positions, news and line changes from bus.bmarian.online, the server behind the Conexiuni Cluj website.
- Map tiles, fonts and icons from [OpenFreeMap](https://openfreemap.org/).
- The weather for Cluj-Napoca from [Open-Meteo](https://open-meteo.com/), for the city, not your location.
- Place search from Photon, as above.

Like any website, these services see your IP address when the app connects to them. bus.bmarian.online doesn't store it: its logs keep a salted one-way hash instead, with the address of each request (for a planned trip, that includes its start and end), and delete them after 5 days. The same hash is used to count daily users and how often stops and lines are opened.

## On the watch

The watch app uses your location, with your permission, to list the stops near you and show where you are on the map. That happens on the watch, and the location is never sent anywhere.

Search on the watch uses its own voice and keyboard input; the app only gets the words, and looks them up in the stops and lines on the watch.

The watch downloads the same things as the phone, from bus.bmarian.online and OpenFreeMap.

Your favorites, followed buses and trips go between the phone and the watch through Google Play services' Wear OS connection, which Google relays when the two aren't connected directly.

## Favorites and settings

Your favorites, reminders and settings are stored on your devices, and in Android's backup of your phone if you turned that on.

When you move them to another device, they go through bus.bmarian.online. If the other device scans the code, they are encrypted with a key the server never sees. If the code is typed, the server can read them while it passes them on. They're only ever kept in the server's memory, never written to disk. They're deleted as soon as the other device picks them up, and an unused code expires after 10 minutes.

## Children

The app isn't directed at children and collects nothing about anyone.

## Changes and contact

Changes to this policy will be published here. Questions go to the [issue tracker](https://github.com/bmarian/conexiuni-cluj-mobile/issues).

Last updated: October 10, 2026.
