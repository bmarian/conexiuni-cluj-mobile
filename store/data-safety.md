# Data safety answers

For the Play Console's Data safety form, from what the phone and watch apps actually send (see [`PRIVACY.md`](../PRIVACY.md)).

## Overview

| Question | Answer |
| --- | --- |
| Does the app collect or share any of the required user data types? | Yes |
| Is all collected data encrypted in transit? | Yes (HTTPS only) |
| Do you provide a way for users to request that their data is deleted? | No: there are no accounts, and the server deletes its logs after 5 days on its own |

## Data types

**Location: approximate and precise**

- Collected: yes. A planned trip's start and end go to bus.bmarian.online, which keeps them in its request log for up to 5 days.
- Shared: no.
- Processed ephemerally: no (the log keeps it for 5 days).
- Required or optional: optional. Everything works without location; trips can start from a place you pick.
- Purpose: app functionality.

**App activity: in-app search history**

- Collected: yes. Place searches go to Photon (Komoot), which finds the places.
- Shared: no (Photon is the service doing the search for the app).
- Processed ephemerally: yes; the app keeps no search history off the device.
- Required or optional: optional.
- Purpose: app functionality.

**App info and performance, device or other IDs, personal info, messages, photos, contacts, financial, health**

- Not collected.

## Not declared, and why

- Favorites and settings moved with a transfer code pass through bus.bmarian.online's memory for at most 10 minutes and are deleted when picked up; scanned codes are encrypted with a key the server never sees. This counts as user-initiated transfer, processed ephemerally.
- The IP address isn't stored: the server logs a salted one-way hash instead.
- Favorites, followed buses and trips sent between the phone and the watch go through Google Play services' Wear OS connection between the user's own devices.
