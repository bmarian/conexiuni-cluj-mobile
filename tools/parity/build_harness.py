"""Builds a Node harness that runs RoutePlanningView's planning code verbatim on a fixture."""
import re
import shutil
import sys
from pathlib import Path

SRC = Path(r"C:\Users\maria\Documents\Projects\conexiuni-cluj\frontend\src")
OUT = Path(sys.argv[1])

if OUT.exists():
    shutil.rmtree(OUT)
for sub in ("utils", "types"):
    shutil.copytree(SRC / sub, OUT / sub)
    for f in (OUT / sub).rglob("*.ts"):
        text = f.read_text(encoding="utf-8").replace("'@/", "'../").replace('"@/', '"../')
        f.write_text(text, encoding="utf-8")

lines = (SRC / "views" / "RoutePlanningView.vue").read_text(encoding="utf-8").split("\n")


def index_of(pattern, start=0):
    for i in range(start, len(lines)):
        if re.search(pattern, lines[i]):
            return i
    raise SystemExit(f"not found: {pattern}")


def block(start_pattern, end_pattern):
    a = index_of(start_pattern)
    b = index_of(end_pattern, a + 1)
    return lines[a:b]


parts = block(r"^const MAX_MINUTES", r"^const {t, locale}")
parts += block(r"^interface PlanStop", r"^const customOrigin")
parts += block(r"^const planData = ref", r"^function selectPlanAt")
parts += block(r"^// --- Timetable helpers", r"^const streamTripIds")
parts += block(r"^watch\(planData, \(data\)", r"^// Update departure times in-place")
parts += block(r"^function computeTotalMinutes", r"^function getRelativeDepartureFormatted")

header = """
import {readFileSync, writeFileSync} from 'node:fs'
import {getRideMinutesBetweenStops, getShapeStopTimes, getTimeOffsetToStop} from './utils/trips.ts'
import {getMinutesFromDate, getTimetableForDay, timeStringToMinutes} from './utils/time.ts'
import {haversineMeters} from './utils/geo.ts'
const ref = (v) => ({value: v})
const computed = (fn) => ({get value() { return fn() }})
const watchers = []
const watch = (_src, cb) => { watchers.push(cb) }
const userTime = ref(null)
const timeMode = ref('now')
const timeValue = ref('')
"""

footer = """
const fixture = JSON.parse(readFileSync(process.argv[2], 'utf8'))
allStops.value = fixture.stops
const entries = (list) => list.map((t) => ({
  minutes: t.minutes, live: t.is_live, routeId: t.routeId ?? null, tripId: t.tripId ?? null,
  transferRouteId: t.transferRouteId ?? null, transferTripId: t.transferTripId ?? null,
}))
const out = []
for (const sc of fixture.scenarios) {
  timeMode.value = sc.mode
  timeValue.value = sc.timeValue
  userTime.value = new Date(sc.nowMs)
  routesWithTimes.value = []
  liveEtaByKey.value = new Map()
  planData.value = sc.response
  watchers[0](sc.response)
  const built = routesWithTimes.value.map((p) => ({
    key: p.key,
    routeIds: p.legs.map((l) => l.routeIds),
    tripIds: p.legs.map((l) => l.tripIds),
    nextTimes: entries(p.nextTimes),
    total: computeTotalMinutes(p),
    duration: getJourneyDuration(p),
  }))
  userTime.value = new Date(sc.nowMs + 7 * 60_000)
  applyPlanTimingFromSchedule(userTime.value)
  const ticked = routesWithTimes.value.map((p) => ({key: p.key, nextTimes: entries(p.nextTimes), isLive: p.isLive}))
  out.push({label: sc.label, built, ticked})
}
writeFileSync(process.argv[3], JSON.stringify(out))
console.error(`${out.length} scenarios, ${out.reduce((n, s) => n + s.built.length, 0)} plans`)
"""

(OUT / "harness.ts").write_text(header + "\n".join(parts) + footer, encoding="utf-8")
print(OUT / "harness.ts")
