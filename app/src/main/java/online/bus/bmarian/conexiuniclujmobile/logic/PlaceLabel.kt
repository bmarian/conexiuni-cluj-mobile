package online.bus.bmarian.conexiuniclujmobile.logic

class PlaceLabel(val icon: String?, val name: String)

/** Port of utils/placeLabel.ts. */
fun placeIcon(label: String): PlaceLabel {
    val words = label.trim().split(Regex("""\s+"""))
    val first = words.first()
    val name = words.drop(1).joinToString(" ")
    val symbol = first.codePoints().anyMatch { Character.getType(it) == Character.OTHER_SYMBOL.toInt() }
    if (name.isEmpty() || !symbol || first.codePoints().anyMatch(Character::isLetterOrDigit)) return PlaceLabel(null, label.trim())
    return PlaceLabel(first, name)
}
