// Saves a timetable-grid fixture: a few lines with timetable, stop_times and hourly offsets.
import {writeFileSync} from 'node:fs'

const out = process.argv[2]
const API = 'https://bus.bmarian.online/api/'
const names = ['25', '35', '43', '24B', 'M26', '9']

async function get(path) {
  const r = await fetch(API + path, {headers: {'User-Agent': 'ConexiuniClujMobile-parity'}})
  if (!r.ok) throw new Error(`${path}: ${r.status}`)
  return r.json()
}

const routes = await get('routes')
const fixture = {routes: {}, scenarios: []}
for (const name of names) {
  const route = routes.find((r) => r.route_short_name === name)
  if (!route) continue
  const q = encodeURIComponent(name)
  const [timetable, stopTimes] = await Promise.all([get(`timetable?route_short_name=${q}`), get(`stop_times?route_short_name=${q}`)])
  const hourly = {}
  for (const [day, type] of [['weekdays', 'weekday'], ['saturday', 'saturday'], ['sunday', 'sunday']]) {
    hourly[day] = await get(`stop_times/hourly?route_short_name=${q}&day_type=${type}`).catch(() => ({}))
  }
  fixture.routes[name] = {route_id: route.route_id, timetable, stop_times: stopTimes, hourly}
  // Weekday morning, Saturday evening and a late Sunday, in Cluj time.
  for (const at of ['2026-10-07T08:42:00+03:00', '2026-10-10T19:05:00+03:00', '2026-10-11T23:50:00+03:00']) {
    fixture.scenarios.push({route: name, nowMs: Date.parse(at)})
  }
  console.error(`${name}: ${stopTimes.length} stop times`)
}
writeFileSync(out, JSON.stringify(fixture))
