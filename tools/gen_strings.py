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
             "<!-- Generated from frontend/src/locales/%s.json; keys keep the website's names. -->" % lang,
             "<resources>"]
    for key, value in data.items():
        if SKIP.match(key):
            continue
        order = orders.get(key, [])
        lines.append(f'    <string name="{snake(key)}">{convert(value, order)}</string>')
    lines.append("</resources>")
    open(f"{DST}\\{folder}\\web_strings.xml", "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")
    print(folder, len(lines) - 4)
