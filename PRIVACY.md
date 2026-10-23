# Privacy policy

The “Ce autobuz iau?” apps for Android and Wear OS show live bus and tram departures in Cluj-Napoca and other Romanian cities and plan trips. They have no accounts, no ads and no analytics or tracking libraries.

## Location

With your permission, the app uses your location on the phone: to open on the city you are in, to show the stops near you and where you are on the map, to plan trips from where you are, to tell you where to get off during a trip, and to time your walk to the stop for a reminder.

During a trip you started, the app keeps following you with the screen off, with a notification showing, until the trip ends.

If you allow location all the time, a reminder checks where you are a few times before the bus, even with the app closed: about an hour before, at the heads-up, and when it's time to leave. It uses that to time your walk to the stop and tell you when to go. It doesn't follow you in between, and nothing else reads your location in the background. Without that permission, a reminder times the walk from where you were the last time the app was open, if that was within the hour, and otherwise comes 20 minutes before the bus. These positions are kept only on the phone.

Your location only leaves the phone when you plan a trip from where you are: the start, rounded to about 10 metres, and the destination go to [ceautobuziau.ro](https://ceautobuziau.ro), which works out the trip.

## Search

Lines and stops are searched on the phone.

Places are looked up on ceautobuziau.ro, which searches its own copy of OpenStreetMap data. It receives what you type, or the name in something you share to the app, and never your location. When you drag the destination pin on the trip map, its coordinates go to ceautobuziau.ro to name the spot.

## Notifications and reminders

Followed buses, trips and reminders are kept on the phone. To keep their times live, the app asks ceautobuziau.ro about those stops and buses, the same way it does when you open a stop. Reminders use alarms to wake the app in time.

If you follow a line's changes, the app asks ceautobuziau.ro about those lines once an hour between 08:00 and 20:00, sending their numbers.

## What the app downloads

- Stops, lines, timetables, live bus positions, news, line changes and place search from ceautobuziau.ro, the server behind the website. Older versions of the apps use bus.bmarian.online, the same server under its old name.
- Map tiles, fonts and icons from [OpenFreeMap](https://openfreemap.org/).

Like any website, these services see your IP address when the app connects to them. ceautobuziau.ro doesn't store it: its logs keep a salted one-way hash instead, with the address of each request (for a planned trip, that includes its start and end), and delete them after 5 days. The same hash counts daily users and is deleted after 5 days too; only the totals are kept.

## On the watch

The watch app uses your location, with your permission, to list the stops near you and show where you are on the map, and, until the phone app tells it a city, to open on the city you are in. That happens on the watch, and the location is never sent anywhere.

A reminder set on the watch asks the phone app how long the walk to the stop is, but only while the phone is connected right beside the watch. The phone works it out as described above and sends back just the number of minutes, never the location.

Search on the watch uses its own voice and keyboard input; the app only gets the words, and looks them up in the stops and lines on the watch.

The watch downloads the same things as the phone, from ceautobuziau.ro and OpenFreeMap.

Your favorites, followed buses and trips go between the phone and the watch through Google Play services' Wear OS connection, which Google relays when the two aren't connected directly. A watch used without the phone app can join sync on its own (below), and then syncs only favorite stops and lines.

## Favorites and settings

Your favorites, reminders and settings are stored on your devices, and in Android's backup of your phone if you turned that on.

With Sync with other devices on, your favorite stops, lines and places and the lines whose changes you follow stay the same on your phone, watch and the website; with Also sync settings, so do the theme, language and map settings. Reminders, followed buses, trips and recent searches stay on the device they were made on.

Everything synced is encrypted on your devices with a key that ceautobuziau.ro never gets, so the server stores it but can't read it. The key goes to a new device when you add it: inside the QR code, in a part of the link that never reaches the server, or, when you type the code instead, through a key exchange that you confirm by checking that both screens show the same six-digit number. The key isn't in Android's backup.

For each group of synced devices, the server keeps the encrypted data, padded to a few fixed sizes so its size says little about what's in it; each device's type (phone, watch or browser), when it joined and the day it last synced; and what it needs to let only those devices in. The device names you see in the list are encrypted too. Codes for adding a device are kept only in the server's memory and expire after 5 minutes. The server's request log doesn't record which group a request is for.

Removing a device gives the others a new key, so it can't read anything synced afterwards. Turning off sync on the last device, or Turn off sync everywhere, deletes the group from the server, and a group that no device has used for a year is deleted on its own. Your devices keep their favorites either way.

## Children

The app isn't directed at children and collects nothing about anyone.

## Changes and contact

Changes to this policy will be published here. Questions go to the [issue tracker](https://github.com/bmarian/conexiuni-cluj-mobile/issues).

Last updated: October 23, 2026.
