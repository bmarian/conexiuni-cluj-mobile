// Saves a planner fixture: every stop, and plan_routes answers for a few trips and time modes.
// Run with TZ=Europe/Bucharest so local times match Cluj.
import {writeFileSync} from 'node:fs'

const out = process.argv[2]
const API = 'https://ceautobuziau.ro/api/'
const pad = (n) => String(n).padStart(2, '0')
const local = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`

function lastCutoff(now) {
  const cutoff = new Date(now)
  cutoff.setHours(3, 0, 0, 0)
  if (cutoff <= now) cutoff.setDate(cutoff.getDate() + 1)
  return local(cutoff)
}

async function get(path) {
  const r = await fetch(API + path, {headers: {'User-Agent': 'ConexiuniClujMobile-parity'}})
  if (!r.ok) throw new Error(`${path}: ${r.status}`)
  return r.json()
}

const trips = [
  ['unirii-iulius', 46.7699, 23.5899, 46.7716, 23.6262],
  ['manastur-gara', 46.7560, 23.5530, 46.7845, 23.5870],
  ['zorilor-aeroport', 46.7520, 23.5800, 46.7810, 23.6860],
  ['grigorescu-marasti', 46.7680, 23.5550, 46.7830, 23.6150],
]

const now = new Date()
now.setSeconds(0, 0)
const later = new Date(now.getTime() + 3 * 3600_000)
const modes = [
  ['now', null, null],
  ['leave', local(later), 'false'],
  ['arrive', local(later), 'true'],
  ['last', lastCutoff(now), 'true'],
]

const stops = (await get('stops')).map((s) => ({stop_id: s.stop_id, stop_name: s.stop_name, stop_lat: s.stop_lat, stop_lon: s.stop_lon}))
const scenarios = []
for (const [name, fl, fg, tl, tg] of trips) {
  for (const [mode, time, arriveBy] of modes) {
    const params = new URLSearchParams({from_lat: fl.toFixed(4), from_lng: fg.toFixed(4), to_lat: tl.toFixed(4), to_lng: tg.toFixed(4)})
    if (time) {
      params.set('time', time)
      params.set('arrive_by', arriveBy)
    }
    const response = await get(`plan_routes?${params}`)
    scenarios.push({
      label: `${name}/${mode}`,
      nowMs: now.getTime(),
      mode,
      timeValue: mode === 'leave' || mode === 'arrive' ? time : '',
      response,
    })
    console.error(`${name}/${mode}: ${response.plans.length} plans`)
  }
}
writeFileSync(out, JSON.stringify({stops, scenarios}))
