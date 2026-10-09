"""Builds and runs a Node harness for RouteView.vue's timetable grid on a fixture.

python timetable_harness.py <workdir> <fixture.json> <expected.json>
The fixture holds routes with their timetable, stop_times and hourly offsets per day type.
"""
import re
import shutil
import subprocess
import sys
from pathlib import Path

SRC = Path(r"C:\Users\maria\Documents\Projects\conexiuni-cluj\frontend\src")
OUT = Path(sys.argv[1])
FIXTURE = Path(sys.argv[2]).resolve()
EXPECTED = Path(sys.argv[3]).resolve()

if OUT.exists():
    shutil.rmtree(OUT)
for sub in ("utils", "types"):
    shutil.copytree(SRC / sub, OUT / sub)
    for f in (OUT / sub).rglob("*.ts"):
        f.write_text(f.read_text(encoding="utf-8").replace("'@/", "'../").replace('"@/', '"../'), encoding="utf-8")

lines = (SRC / "views" / "RouteView.vue").read_text(encoding="utf-8").split("\n")


def block(start, end):
    a = next(i for i, l in enumerate(lines) if re.search(start, l))
    b = next(i for i in range(a + 1, len(lines)) if re.search(end, lines[i]))
    return lines[a:b]


parts = []
parts += block(r"^function formatAbsoluteMinutes", r"^function getStopLabel")
parts += block(r"^type TimetableTab", r"^const availableTabs")
parts += block(r"^function tripHourlyOffsets", r"^const hourlyTripOffsets")
parts += block(r"^function offsetsAt", r"^// Shared by the chips")
parts += block(r"^type TimetableChip", r"^const timetableEntries")
parts += block(r"^type HourGroup", r"^const timetableByHour")

header = """
import {readFileSync, writeFileSync} from 'node:fs'
import {getTimetableDayKey, timeStringToMinutes} from './utils/time.ts'
const ref = (v) => ({value: v})
const computed = (fn) => ({get value() { return fn() }})
const userTime = ref(null)
const timetable = ref(null)
const isOutgoing = ref(true)
const currentMinutes = ref(0)
const stopsForDirection = ref([])
"""

footer = """
const fixture = JSON.parse(readFileSync(process.argv[2], 'utf8'))
const out = []
for (const sc of fixture.scenarios) {
  const route = fixture.routes[sc.route]
  userTime.value = new Date(sc.nowMs)
  currentMinutes.value = userTime.value.getHours() * 60 + userTime.value.getMinutes()
  timetable.value = route.timetable
  for (const dir of ['0', '1']) {
    isOutgoing.value = dir === '0'
    const tripId = `${route.route_id}_${dir}`
    let cumulative = 0
    stopsForDirection.value = route.stop_times
      .filter((st) => st.trip_id === tripId)
      .sort((a, b) => a.stop_sequence - b.stop_sequence)
      .map((st) => {
        cumulative += st.offset_arrival_time
        return {...st, timeOffsetFromStart: Math.ceil(cumulative / 60)}
      })
    for (const day of ['weekdays', 'saturday', 'sunday']) {
      const offsets = route.hourly[day] ?? {}
      const trip = offsets[tripId]
      const stops = stopsForDirection.value
      const hourly = !trip || trip.stop_ids.length !== stops.length ? null
        : trip.stop_ids.every((id, i) => id === stops[i].stop_id) ? trip.hourly_offset_seconds : null
      const chips = chipsForDay(day, hourly, true)
      const groups = Array.from(chipsByHour(chips).entries()).map(([h, list]) => ({
        hour: formatAbsoluteMinutes(h * 60).slice(0, 2), isNextDay: h >= 24, chips: list.map((c) => [c.time, c.startMin, c.isPast]),
      }))
      const picked = chips.filter((c) => c.startMin !== null).slice(0, 3).map((c) => {
        const departureMin = timeStringToMinutes(c.time)
        const offs = offsetsAt(hourly, departureMin)
        return {time: c.time, stops: stops.map((s, idx) => formatAbsoluteMinutes(departureMin + (offs[idx] ?? s.timeOffsetFromStart)))}
      })
      out.push({label: `${sc.route}/${dir}/${day}@${sc.nowMs}`, hourly: hourly !== null, chips: chips.map((c) => [c.time, c.startMin, c.isPast]), groups, trips: picked})
    }
  }
}
writeFileSync(process.argv[3], JSON.stringify(out))
console.error(`${out.length} grids`)
"""

(OUT / "harness.ts").write_text(header + "\n".join(parts) + footer, encoding="utf-8")
subprocess.run(
    ["node", "--experimental-strip-types", "--no-warnings", "harness.ts", str(FIXTURE), str(EXPECTED)],
    cwd=OUT, check=True, env={**__import__("os").environ, "TZ": "Europe/Bucharest"},
)
