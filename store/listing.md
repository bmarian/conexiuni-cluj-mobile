# Google Play listing

Everything the Play Console asks for, ready to paste. The phone and the watch app share the package, so they share this one listing. Images are in this folder.

| Field | Value |
| --- | --- |
| Package | `online.bmarian.bus` |
| App name | Conexiuni Cluj |
| Default language | Romanian (ro-RO), with English (en-US) as a translation |
| Category | Maps & Navigation |
| Free / paid | Free, no ads, no in-app purchases |
| Form factors | Phone, and Wear OS (works without the phone too) |
| Privacy policy | https://github.com/bmarian/conexiuni-cluj-mobile/blob/master/PRIVACY.md |
| Website | https://bus.bmarian.online |
| App icon | `icon-512.png` |
| Feature graphic | `feature-graphic-ro.png` (Romanian), `feature-graphic-en.png` (English) |
| Phone screenshots | `screenshots/phone-ro/`, `screenshots/phone-en/` (1080 × 1920) |
| Wear OS screenshots | `screenshots/watch/` (454 × 454) |

## Romanian (ro-RO), default

**Short description**

> Autobuzele și tramvaiele din Cluj-Napoca, live, cu mementouri și călătorii.

**Full description**

> Autobuzele și tramvaiele CTP Cluj-Napoca, live, pe telefon și pe ceas.
>
> • Toate autobuzele și tramvaiele pe hartă, în timp real
> • Plecările din orice stație, cu numărătoare inversă live
> • Liniile cu stațiile, următoarele sosiri și orarul
> • Planificarea călătoriilor: acum, la o oră anume, cu sosire până la, sau ultimul autobuz din seara asta
> • Călătorii pas cu pas, care îți spun unde să cobori
> • Urmărește autobuze: numărătoarea apare în bara de stare, iar telefonul vibrează când vin
> • Mementouri: alegi un autobuz sau ultimul din zi, și îți spunem când să pleci
> • Stații, linii și locuri favorite
> • Un widget cu următoarele plecări dintr-o stație
> • Pe Wear OS: favoritele, plecările, hărțile și autobuzele urmărite pe încheietură, cu sau fără telefon
>
> Poziții live de la Tranzy.ai, orare de la CTP Cluj-Napoca, date de hartă © contributorii OpenStreetMap, hărți de la OpenFreeMap. Aplicația nu e afiliată cu CTP Cluj-Napoca.

## English (en-US), translation

**Short description** (80 characters max)

> Live buses and trams in Cluj-Napoca, with reminders and trip planning.

**Full description**

> Live CTP Cluj-Napoca buses and trams, on your phone and your watch.
>
> • Every bus and tram on the map, live
> • Departures for any stop, with live countdowns
> • Lines with their stops, next arrivals and timetables
> • Trip planning: leave now, leave at, arrive by, or the last bus tonight
> • Step-by-step trips that tell you where to get off
> • Follow buses: a live countdown in the status bar, and a buzz when they come
> • Reminders: pick a bus, or the day's last one, and you'll be told when to leave
> • Favorite stops, lines and places
> • A home-screen widget with a stop's next departures
> • On Wear OS: favorites, departures, maps and followed buses on your wrist, with or without the phone
>
> Live positions from Tranzy.ai, timetables from CTP Cluj-Napoca, map data © OpenStreetMap contributors, tiles by OpenFreeMap. Not affiliated with CTP Cluj-Napoca.

## Other answers

| Question | Answer |
| --- | --- |
| App access | Everything works without signing in |
| Ads | No ads |
| Content rating | Reference / navigation tool, no user content, no purchases: Everyone / PEGI 3 |
| Target audience | 13 and over; not designed for children |
| News app | No |
| Government app | No (not affiliated with CTP or the city) |
| Health, financial features | None |
| Data safety | See [`data-safety.md`](data-safety.md) |

## Permission declarations

**Foreground service: special use** (`FollowService`)

> Shows a live countdown for buses the user chose to follow or set a reminder for, and alerts them when it's time to leave and when the bus comes. It stops on its own once the bus has gone.

Video: tap the bell on a departure, show the live notification counting down, and stop it.

**Foreground service: location** (`JourneyService`)

> Guides a trip the user started: it follows their position along the route to tell walking, waiting and riding apart and to tell them where to get off. It stops when the trip ends or the user ends it.

Video: plan a trip, tap Start, show the navigation screen and its live notification, and end the trip.

**Exact alarms** (`SCHEDULE_EXACT_ALARM`): nothing to declare. The user allows it from the reminder screen.
