"""Generates res/values*/web_strings.xml from the website's locale files, skipping keys the app doesn't use."""
import json, re
from xml.sax.saxutils import escape

SRC = r"C:\Users\maria\Documents\Projects\conexiuni-cluj\frontend\src\locales"
DST = r"C:\Users\maria\AndroidStudioProjects\ConexiuniClujMobile\app\src\main\res"
SKIP = re.compile(
    r"^(head|kbd|simplified|watch|bsod|paper|arcade|legacyBlue|funMode|metroLegacy|pong(?!YouWin|AiWins|PlayAgain|Exit)|"
    r"themeDefault|adminDashboard|settingsAdminDesc|pushUnsupported|pushBrave|pushDeniedToastBody|pushFailed|notFoundFunny|"
    r"offlineBanner|searchRoutes|noRouteData|goBack|scheduledApprox|kbdHelp)"
)


# The app's own wording, in the app's voice: lines, stops and trips, short and plain. Already in Android format.
APP = {
    "values": {
        "w_route": "Line",
        "w_no_schedule": "No timetable for this direction.",
        "w_bus_stop": "Bus stop",
        "w_all_routes": "All lines",
        "w_all_stops": "All stops",
        "w_all_routes_at_stop": "Lines at this stop",
        "w_favorite_routes": "Favorite lines",
        "w_no_favorites": "No favorites yet. Tap the heart on a line or stop to save it.",
        "w_search_placeholder": "Search lines, stops, places…",
        "w_search_results_routes": "Lines",
        "w_not_found_desc": "This line or stop doesn\\'t exist anymore.",
        "w_plan_title": "Plan a trip",
        "w_plan_title_generic": "Plan a trip",
        "w_plan_origin_current_location": "My location",
        "w_plan_routes_label": "Trips",
        "w_plan_locating": "Finding you…",
        "w_plan_calculating": "Finding trips…",
        "w_loading_route": "Loading line…",
        "w_loading_stop": "Loading stop…",
        "w_plan_no_buses_next_hour": "No buses in the next hour",
        "w_plan_refresh": "Refresh",
        "w_plan_swap": "Swap",
        "w_plan_time_search_aria": "Search trips at this time",
        "w_plan_no_results": "No trips found",
        "w_plan_search_placeholder": "Where from?",
        "w_plan_dest_search_placeholder": "Where to?",
        "w_plan_searching": "Searching…",
        "w_rename_favorite_placeholder": "Name…",
        "w_export_copied": "Copied",
        "w_import_paste_placeholder": "Paste settings here…",
        "w_import_success": "Imported",
        "w_import_error": "These aren\\'t valid settings",
        "w_vehicle_extras": "Bus details",
        "w_vehicle_extras_desc": "Show wheelchair and bike access on buses.",
        "w_push_notifications": "Change alerts",
        "w_push_notifications_desc": "Get notified when a line you follow changes.",
        "w_push_on_toast": "Change alerts on",
        "w_push_on_toast_body": "You\\'ll be told when a line you follow changes.",
        "w_push_on_toast_no_lines": "Follow a line from its menu first.",
        "w_push_off_toast": "Change alerts off",
        "w_news_loading": "Loading…",
        "w_news_empty": "No news",
        "w_news_error": "Couldn\\'t load news",
        "w_news_follow_hint": "Follow a line from its menu to see its changes here.",
        "w_follow_line_toast_body": "Its changes will show in News.",
        "w_unfollow_line_toast_body": "Its changes won\\'t show in News.",
        "w_route_change_summary_timetable_one": "The %1$s timetable changed.",
        "w_route_change_summary_timetable_many": "The %1$s timetables changed.",
        "w_route_change_summary_timetable_any": "The timetable changed.",
        "w_route_change_summary_stop_added": "A stop was added.",
        "w_route_change_summary_stops_added": "Stops were added.",
        "w_route_change_summary_stop_removed": "A stop was removed.",
        "w_route_change_summary_stops_removed": "Stops were removed.",
        "w_route_change_summary_stops_both": "Stops were added and removed.",
        "w_language_desc": "Stop and line names stay in Romanian.",
        "w_weather_desc": "Show the forecast for Cluj.",
        "w_news_desc": "Show CTP news and changes to lines you follow.",
        "w_auto_center_on_me": "Center on me",
        "w_auto_center_on_me_desc": "Center the map on you when it opens.",
        "w_auto_fit_map_desc": "Show the whole line when you open it.",
        "w_timetable_changes_desc": "Follow lines from their menu to see what changes.",
        "w_transfer_desc": "Move your favorites and settings: tap Receive on the new device, then Send here.",
        "w_transfer_hint": "Scan the code, or type the number under Settings, Send on the other device.",
        "w_transfer_loading": "Getting a code…",
        "w_receive_done": "Received",
        "w_send_done": "Sent",
        "w_send_too_many": "Too many tries. Wait a minute.",
    },
    "values-ro": {
        "w_route": "Linie",
        "w_bus_stop": "Stație",
        "w_all_routes": "Toate liniile",
        "w_all_routes_at_stop": "Linii în această stație",
        "w_favorite_routes": "Linii favorite",
        "w_no_favorites": "Nu ai favorite încă. Apasă inima la o linie sau stație ca s-o salvezi.",
        "w_search_placeholder": "Caută linii, stații, locuri…",
        "w_search_results_routes": "Linii",
        "w_not_found_desc": "Linia sau stația nu mai există.",
        "w_plan_title": "Planifică o călătorie",
        "w_plan_title_generic": "Planifică o călătorie",
        "w_plan_origin_current_location": "Locația mea",
        "w_plan_routes_label": "Călătorii",
        "w_plan_locating": "Te căutăm…",
        "w_plan_calculating": "Căutăm călătorii…",
        "w_loading_route": "Se încarcă linia…",
        "w_loading_stop": "Se încarcă stația…",
        "w_plan_no_buses_next_hour": "Niciun autobuz în următoarea oră",
        "w_plan_refresh": "Reîmprospătează",
        "w_plan_swap": "Inversează",
        "w_plan_time_search_aria": "Caută călătorii la această oră",
        "w_plan_no_results": "Nicio călătorie găsită",
        "w_plan_search_placeholder": "De unde pleci?",
        "w_plan_dest_search_placeholder": "Unde mergi?",
        "w_plan_searching": "Se caută…",
        "w_rename_favorite": "Redenumește",
        "w_rename_favorite_placeholder": "Nume…",
        "w_export_copied": "Copiat",
        "w_import_paste_placeholder": "Lipește setările aici…",
        "w_import_success": "Importat",
        "w_import_error": "Nu sunt setări valide",
        "w_vehicle_extras": "Detalii autobuz",
        "w_vehicle_extras_desc": "Arată accesul pentru scaune cu rotile și biciclete.",
        "w_push_notifications": "Alerte de schimbări",
        "w_push_notifications_desc": "Primești o notificare când se schimbă o linie urmărită.",
        "w_push_on_toast": "Alertele de schimbări sunt pornite",
        "w_push_on_toast_body": "Îți spunem când se schimbă o linie urmărită.",
        "w_push_on_toast_no_lines": "Urmărește mai întâi o linie din meniul ei.",
        "w_push_off_toast": "Alertele de schimbări sunt oprite",
        "w_news_loading": "Se încarcă…",
        "w_news_empty": "Nicio știre",
        "w_news_error": "Știrile nu s-au încărcat",
        "w_news_follow_hint": "Urmărește o linie din meniul ei ca să-i vezi schimbările aici.",
        "w_follow_line_toast_body": "Schimbările ei apar la Știri.",
        "w_unfollow_line_toast_body": "Schimbările ei nu mai apar la Știri.",
        "w_route_change_summary_timetable_one": "Orarul %1$s s-a schimbat.",
        "w_route_change_summary_timetable_many": "Orarele %1$s s-au schimbat.",
        "w_route_change_summary_timetable_any": "Orarul s-a schimbat.",
        "w_route_change_summary_stop_added": "A fost adăugată o stație.",
        "w_route_change_summary_stops_added": "Au fost adăugate stații.",
        "w_route_change_summary_stop_removed": "A fost scoasă o stație.",
        "w_route_change_summary_stops_removed": "Au fost scoase stații.",
        "w_route_change_summary_stops_both": "Au fost adăugate și scoase stații.",
        "w_language_desc": "Numele stațiilor și liniilor rămân în română.",
        "w_weather_desc": "Arată prognoza pentru Cluj.",
        "w_news_desc": "Arată știrile CTP și schimbările liniilor urmărite.",
        "w_auto_center_on_me": "Centrează pe mine",
        "w_auto_center_on_me_desc": "Centrează harta pe tine când se deschide.",
        "w_auto_fit_map_desc": "Arată toată linia când o deschizi.",
        "w_timetable_changes_desc": "Urmărește linii din meniul lor ca să vezi ce se schimbă.",
        "w_transfer_desc": "Mută favoritele și setările: apasă Primește pe dispozitivul nou, apoi Trimite aici.",
        "w_transfer_hint": "Scanează codul sau scrie numărul la Setări, Trimite pe celălalt dispozitiv.",
        "w_transfer_failed": "Nu s-a putut obține un cod. Încearcă din nou.",
        "w_transfer_loading": "Se generează codul…",
        "w_receive_done": "Primit",
        "w_send_done": "Trimis",
        "w_send_too_many": "Prea multe încercări. Așteaptă un minut.",
    },
}


def snake(key):
    s = re.sub(r"(?<=[a-z0-9])([A-Z])", r"_\1", key)
    return "w_" + s.lower()


def convert(value, order):
    # {name} placeholders become %n$s in order of first appearance in English.
    def repl(m):
        return f"%{order.index(m.group(1)) + 1}$s"
    out = re.sub(r"\{(\w+)\}", repl, value)
    out = escape(out).replace("'", "\\'").replace('"', '\\"')
    if out.startswith("@") or out.startswith("?"):
        out = "\\" + out
    return out


en = json.load(open(f"{SRC}\\en.json", encoding="utf-8"))
ro = json.load(open(f"{SRC}\\ro.json", encoding="utf-8"))

orders = {k: list(dict.fromkeys(re.findall(r"\{(\w+)\}", v))) for k, v in en.items()}

for lang, data, folder in (("en", en, "values"), ("ro", ro, "values-ro")):
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             "<!-- Generated from frontend/src/locales/%s.json by tools/gen_strings.py, with the app's own wording; keys keep the website's names. -->" % lang,
             "<resources>"]
    for key, value in data.items():
        if SKIP.match(key):
            continue
        name = snake(key)
        text = APP[folder].get(name) or convert(value, orders.get(key, []))
        lines.append(f'    <string name="{name}">{text}</string>')
    lines.append("</resources>")
    open(f"{DST}\\{folder}\\web_strings.xml", "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")
    print(folder, len(lines) - 4)
