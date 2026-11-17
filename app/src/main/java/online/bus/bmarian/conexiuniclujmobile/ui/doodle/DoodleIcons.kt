package online.bus.bmarian.conexiuniclujmobile.ui.doodle

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import online.bus.bmarian.conexiuniclujmobile.ui.theme.LocalDoodle

private const val VIEW = 24f
private const val PEN = 2f

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} $cy a$r $r 0 1 0 ${2 * r} 0 a$r $r 0 1 0 ${-2 * r} 0"

private fun draw(name: String, lines: List<String>, filled: List<String> = emptyList(), mirror: Boolean = false): ImageVector =
    ImageVector.Builder(name, VIEW.dp, VIEW.dp, VIEW, VIEW, autoMirror = mirror).apply {
        for (d in lines) {
            addPath(
                PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black), strokeLineWidth = PEN, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        for (d in filled) addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
    }.build()

private const val HEART =
    "M12 20 C6 15.5 3.5 12.5 3.5 9 C3.5 6.5 5.5 4.5 8 4.5 C9.8 4.5 11.2 5.5 12 7 C12.8 5.5 14.2 4.5 16 4.5 C18.5 4.5 20.5 6.5 20.5 9 C20.5 12.5 18 15.5 12 20 Z"

private const val BELL = "M6.5 16.5 V11 C6.5 7.8 8.8 5.5 12 5.5 C15.2 5.5 17.5 7.8 17.5 11 V16.5 H6.5 Z M5 16.5 H19 M10.5 19.5 C11 20.5 13 20.5 13.5 19.5"
private const val BELLS = "M4.5 5.5 L7 3.5 M19.5 5.5 L17 3.5"
private const val PENCIL = "M5 19 L6 15 L15.5 5.5 C16.5 4.5 18 4.5 19 5.5 C20 6.5 20 8 19 9 L9.5 18.5 Z M14 7 L17.5 10.5"
private const val EYE = "M2.5 12 C5 7.5 8.5 5.5 12 5.5 C15.5 5.5 19 7.5 21.5 12 C19 16.5 15.5 18.5 12 18.5 C8.5 18.5 5 16.5 2.5 12 Z"
private const val MOON =
    "M20 14.5 C18.8 15 17.5 15.3 16 15.3 C11 15.3 8.7 11.9 8.7 7.9 C8.7 6.5 9 5.2 9.5 4 C6.3 5.3 4 8.4 4 12 C4 16.4 7.6 20 12 20 C15.6 20 18.7 17.7 20 14.5 Z"
private val QR_LINES = listOf("M4 4 H10 V10 H4 Z", "M14 4 H20 V10 H14 Z", "M4 14 H10 V20 H4 Z", "M14 14 H16.5 M20 14 V16.5 M17 17 V20 H20")
private val QR_DOTS = listOf(circle(7f, 7f, 1.2f), circle(17f, 7f, 1.2f), circle(7f, 17f, 1.2f))
private val DIALPAD = listOf(7f, 12f, 17f).flatMap { x -> listOf(4.5f, 9.5f, 14.5f).map { y -> circle(x, y, 1.4f) } } + circle(12f, 19.5f, 1.4f)
private const val SPEAKER = "M4 9.5 H7.5 L12 5.5 V18.5 L7.5 14.5 H4 Z"

/** Keyed by the replaced icon's name. */
private val byName: Map<String, ImageVector> by lazy {
    listOf(
        draw("Rounded.Search", listOf(circle(10f, 10f, 6.5f), "M15 15.5 L20.5 21")),
        draw(
            "Rounded.Newspaper",
            listOf("M4 6 H17 V19 H6 C4.8 19 4 18.2 4 17 Z", "M17 9 H20 V17 C20 18.2 19.2 19 18 19 H6", "M7 9.5 H14 M7 12.5 H14 M7 15.5 H11"),
        ),
        draw(
            "Rounded.Settings",
            listOf(
                circle(12f, 12f, 3.4f),
                "M12 3 v3 M12 18 v3 M3 12 h3 M18 12 h3 M5.6 5.6 l2.1 2.1 M16.3 16.3 l2.1 2.1 M5.6 18.4 l2.1 -2.1 M16.3 7.7 l2.1 -2.1",
            ),
        ),
        draw("Rounded.MyLocation", listOf(circle(12f, 12f, 6f), "M12 2.5 v3 M12 18.5 v3 M2.5 12 h3 M18.5 12 h3"), listOf(circle(12f, 12f, 1.6f))),
        draw("Rounded.Close", listOf("M6 6.5 C10 10 14 14 18 18 M18 6 C14 10.5 10 14 6.5 18")),
        draw("AutoMirrored.Rounded.ArrowBack", listOf("M19 12.5 C14 12 10 12 5.5 12 M11 6 C9 8.5 7.5 10 5 12 C7.5 14 9 15.5 11 18"), mirror = true),
        draw("Rounded.MoreVert", emptyList(), listOf(circle(12f, 5.5f, 1.9f), circle(12.4f, 12f, 1.9f), circle(11.8f, 18.5f, 1.9f))),
        draw("Rounded.Share", listOf(circle(6f, 12f, 2.4f), circle(17.5f, 6f, 2.4f), circle(17.5f, 18f, 2.4f), "M8.2 11 L15.3 7.1 M8.2 13 L15.3 16.9")),
        draw("Rounded.FavoriteBorder", listOf(HEART)),
        draw("Rounded.Favorite", listOf(HEART), listOf(HEART)),
        draw("Rounded.ExpandMore", listOf("M6 9.5 C8.5 12 10 13.5 12 15 C14 13.5 15.5 12 18 9.5")),
        draw("Rounded.Add", listOf("M12 5.5 C12.2 10 11.8 14 12 18.5 M5.5 12 C10 11.8 14 12.2 18.5 12")),
        draw(
            "Rounded.Place",
            listOf("M12 21 C8 16.5 5.5 13 5.5 9.5 C5.5 5.9 8.4 3 12 3 C15.6 3 18.5 5.9 18.5 9.5 C18.5 13 16 16.5 12 21 Z", circle(12f, 9.5f, 2.4f)),
        ),
        draw(
            "Rounded.DirectionsBus",
            listOf("M6 16.5 V6.5 C6 4.5 7.5 3.5 12 3.5 C16.5 3.5 18 4.5 18 6.5 V16.5 Z", "M6 11 H18", "M7.5 16.5 V19.5 M16.5 16.5 V19.5"),
            listOf(circle(9f, 13.8f, 1f), circle(15f, 13.8f, 1f)),
        ),
        draw("Rounded.Alarm", listOf(circle(12f, 13f, 7f), "M12 9.5 V13 L14.5 14.5", BELLS)),
        draw("Rounded.AlarmAdd", listOf(circle(12f, 13f, 7f), "M12 10 V16 M9 13 H15", BELLS)),
        draw("Rounded.AlarmOn", listOf(circle(12f, 13f, 7f), "M9 13 L11 15 L15 11", BELLS)),
        draw("AutoMirrored.Rounded.KeyboardArrowRight", listOf("M9.5 6 C11.5 8.5 13 10 15 12 C13 14 11.5 15.5 9.5 18"), mirror = true),
        draw("AutoMirrored.Rounded.KeyboardArrowLeft", listOf("M14.5 6 C12.5 8.5 11 10 9 12 C11 14 12.5 15.5 14.5 18"), mirror = true),
        draw("AutoMirrored.Rounded.ArrowForward", listOf("M5 11.5 C10 12 14 12 18.5 12 M13 6 C15 8.5 16.5 10 19 12 C16.5 14 15 15.5 13 18"), mirror = true),
        draw("Rounded.ArrowUpward", listOf("M12.5 19 C12 14 12 10 12 5.5 M6 11 C8.5 9 10 7.5 12 5 C14 7.5 15.5 9 18 11")),
        draw("Rounded.Map", listOf("M3.5 6.5 L9 4.5 L15 6.5 L20.5 4.5 V17.5 L15 19.5 L9 17.5 L3.5 19.5 Z", "M9 4.5 V17.5 M15 6.5 V19.5")),
        draw("Rounded.Check", listOf("M5 12.5 C7 14.5 8.5 16 10 17.5 C13 13 16 9.5 19.5 6.5")),
        draw("Rounded.SwapHoriz", listOf("M4 9 H17 M13.5 5.5 L17 9 L13.5 12.5", "M20 15 H7 M10.5 11.5 L7 15 L10.5 18.5")),
        draw("Rounded.SwapVert", listOf("M9 20 V7 M5.5 10.5 L9 7 L12.5 10.5", "M15 4 V17 M11.5 13.5 L15 17 L18.5 13.5")),
        draw("Rounded.Schedule", listOf(circle(12f, 12f, 8.5f), "M12 7.5 V12 L15 14")),
        draw("Rounded.NotificationsActive", listOf(BELL, "M3.5 9 C3.8 7 4.6 5.5 6 4.2 M20.5 9 C20.2 7 19.4 5.5 18 4.2")),
        draw("Rounded.Notifications", listOf(BELL)),
        draw("Rounded.NotificationsNone", listOf(BELL)),
        draw("Rounded.Navigation", listOf("M12 3.5 L19 20 L12 16.5 L5 20 Z")),
        draw("Rounded.Edit", listOf(PENCIL)),
        draw("Rounded.Draw", listOf(PENCIL, "M3.5 21 C5.5 19.5 7 21.5 9 20")),
        draw(
            "Rounded.Refresh",
            listOf("M19 12 C19 15.9 15.9 19 12 19 C8.1 19 5 15.9 5 12 C5 8.1 8.1 5 12 5 C14.3 5 16.3 6.1 17.6 7.8", "M18 3.5 V8 H13.5"),
        ),
        draw("Rounded.Directions", listOf("M12 3 L21 12 L12 21 L3 12 Z", "M9 14 V11.5 C9 10.7 9.7 10 10.5 10 H15 M13 8 L15 10 L13 12")),
        draw(
            "AutoMirrored.Rounded.DirectionsWalk",
            listOf(circle(13f, 4.5f, 1.8f), "M12.5 8 L10.5 13.5 L13.5 16 L14.5 21 M12.5 8 L15.5 11 L18 12 M10.5 13.5 L8.5 20 M12.5 8 L9 9.5 L7.5 12.5"),
            mirror = true,
        ),
        draw("Rounded.Traffic", listOf("M8.5 3.5 H15.5 V20.5 H8.5 Z", circle(12f, 7.5f, 1.5f), circle(12f, 12f, 1.5f), circle(12f, 16.5f, 1.5f))),
        draw(
            "Rounded.Route",
            listOf(
                circle(6f, 18f, 2f), circle(18f, 6f, 2f),
                "M8 18 H15 C16.7 18 18 16.7 18 15 C18 13.3 16.7 12 15 12 H9 C7.3 12 6 10.7 6 9 C6 7.3 7.3 6 9 6 H16",
            ),
        ),
        draw("Rounded.PushPin", listOf("M9 4 H15 M10 4 V10 L7 14 H17 L14 10 V4 M12 14 V20.5")),
        draw(
            "Rounded.Flag",
            listOf("M6 21 V4 M6 4.5 C9 3 12 6 15 4.5 C16.5 3.8 17.5 4 18.5 4.5 V13 C15.5 11.5 12.5 14.5 9.5 13 C8 12.3 7 12.3 6 13"),
        ),
        draw("Rounded.Delete", listOf("M5 7 H19 M9.5 7 V5 H14.5 V7 M7 7 L8 20 H16 L17 7 M10.5 10.5 V16.5 M13.5 10.5 V16.5")),
        draw("Rounded.CropFree", listOf("M4 9 V4 H9 M15 4 H20 V9 M20 15 V20 H15 M9 20 H4 V15")),
        draw("AutoMirrored.Rounded.OpenInNew", listOf("M18 13.5 V19 H5 V6 H10.5", "M14 4 H20 V10 M20 4 L11.5 12.5"), mirror = true),
        draw(
            "Rounded.WifiOff",
            listOf("M3.5 9 C8 5 16 5 20.5 9 M6.5 12.5 C9.5 10 14.5 10 17.5 12.5 M9.5 16 C11 14.8 13 14.8 14.5 16", "M4 4 L20 20"),
            listOf(circle(12f, 19f, 1.2f)),
        ),
        draw("Rounded.Visibility", listOf(EYE, circle(12f, 12f, 3f))),
        draw("Rounded.VisibilityOff", listOf(EYE, "M4 4 L20 20")),
        draw("Rounded.Info", listOf(circle(12f, 12f, 8.5f), "M12 11 V16.5"), listOf(circle(12f, 7.8f, 1.2f))),
        draw("Rounded.WarningAmber", listOf("M12 4 L21 19.5 H3 Z", "M12 10 V14"), listOf(circle(12f, 17f, 1.1f))),
        draw(
            "Rounded.LightMode",
            listOf(
                circle(12f, 12f, 4f),
                "M12 2.5 v2 M12 19.5 v2 M2.5 12 h2 M19.5 12 h2 M5.3 5.3 l1.4 1.4 M17.3 17.3 l1.4 1.4 M5.3 18.7 l1.4 -1.4 M17.3 6.7 l1.4 -1.4",
            ),
        ),
        draw("Rounded.DarkMode", listOf(MOON)),
        draw("Rounded.Bedtime", listOf(MOON)),
        draw("Rounded.BrightnessMedium", listOf(circle(12f, 12f, 8f)), listOf("M12 4 A8 8 0 0 1 12 20 Z")),
        draw(
            "Rounded.Palette",
            listOf(
                "M12 3.5 C7.3 3.5 3.5 7.3 3.5 12 C3.5 16.7 7.3 20.5 12 20.5 C13.2 20.5 13.8 19.6 13.5 18.6 C13.1 17.4 13.9 16.2 15.2 16.2 H17 " +
                    "C19 16.2 20.5 14.6 20.5 12.6 C20.5 7.6 16.7 3.5 12 3.5 Z",
            ),
            listOf(circle(8f, 11f, 1.3f), circle(10.5f, 7.5f, 1.3f), circle(15f, 7.8f, 1.3f)),
        ),
        draw("Rounded.Tune", listOf("M4 7 H11 M15 7 H20 M4 17 H8 M12 17 H20", circle(13f, 7f, 2f), circle(10f, 17f, 2f))),
        draw(
            "Rounded.HourglassTop",
            listOf("M7 3.5 H17 M7 20.5 H17 M8 3.5 C8 8 12 10 12 12 C12 14 8 16 8 20.5 M16 3.5 C16 8 12 10 12 12 C12 14 16 16 16 20.5"),
        ),
        draw("Rounded.Email", listOf("M3.5 6 H20.5 V18 H3.5 Z", "M4 6.5 L12 13 L20 6.5")),
        draw(
            "Rounded.DragIndicator", emptyList(),
            listOf(circle(9f, 6f, 1.6f), circle(15f, 6f, 1.6f), circle(9f, 12f, 1.6f), circle(15f, 12f, 1.6f), circle(9f, 18f, 1.6f), circle(15f, 18f, 1.6f)),
        ),
        draw(
            "Rounded.Smartphone",
            listOf("M7.5 3 H16.5 C17.3 3 18 3.7 18 4.5 V19.5 C18 20.3 17.3 21 16.5 21 H7.5 C6.7 21 6 20.3 6 19.5 V4.5 C6 3.7 6.7 3 7.5 3 Z", "M10.5 18 H13.5"),
        ),
        draw("AutoMirrored.Rounded.Logout", listOf("M10 4 H5 V20 H10", "M9 12 H20 M16.5 8.5 L20 12 L16.5 15.5"), mirror = true),
        draw("AutoMirrored.Rounded.VolumeUp", listOf(SPEAKER, "M15.5 9 C16.5 10.5 16.5 13.5 15.5 15 M18 6.5 C20.5 9.5 20.5 14.5 18 17.5"), mirror = true),
        draw("AutoMirrored.Rounded.VolumeOff", listOf(SPEAKER, "M16 9.5 L21 14.5 M21 9.5 L16 14.5"), mirror = true),
        draw("Rounded.Eco", listOf("M5 19 C5 11 10 5 19.5 4.5 C19 14 13 19 5 19 Z M5 19 C8 15 11 12 15 9")),
        draw("Rounded.PedalBike", listOf(circle(6f, 16f, 3.5f), circle(18f, 16f, 3.5f), "M6 16 L10 9 H15 L18 16 M10 9 L13.5 16 H6 M14 6 H16.5")),
        draw("Rounded.Computer", listOf("M4 5 H20 V15 H4 Z", "M9 19 H15 M12 15 V19")),
        draw("Rounded.QrCode", QR_LINES, QR_DOTS),
        draw("Rounded.QrCode2", QR_LINES, QR_DOTS),
        draw("Rounded.QrCodeScanner", listOf("M4 8 V4.5 H8 M16 4.5 H19.5 V8 M19.5 16 V19.5 H16 M8 19.5 H4.5 V16", "M7 12 H17")),
        draw("Rounded.Dialpad", emptyList(), DIALPAD),
        draw(
            "Rounded.Devices",
            listOf("M3.5 16 V6 H17 V9", "M2.5 18.5 H12", "M14 9 H20 C20.6 9 21 9.4 21 10 V19 C21 19.6 20.6 20 20 20 H14 C13.4 20 13 19.6 13 19 V10 C13 9.4 13.4 9 14 9 Z"),
        ),
        draw(
            "Rounded.LocationCity",
            listOf("M3.5 20.5 H20.5", "M5 20.5 V9 L9.5 6.5 V20.5", "M9.5 20.5 V4 H15 V20.5", "M15 20.5 V11 H19 V20.5", "M11.5 7.5 H13 M11.5 11 H13 M11.5 14.5 H13"),
        ),
        draw("Rounded.AltRoute", listOf("M12 21 V13 C12 10 9 8.5 6 6", "M12 13 C12 10 15 8.5 18 6", "M5 9.5 V5 H9.5 M19 9.5 V5 H14.5")),
        draw("Rounded.SubdirectoryArrowRight", listOf("M6 4 V12 C6 13.7 7.3 15 9 15 H19", "M15.5 11.5 L19 15 L15.5 18.5"), mirror = true),
        draw("Rounded.UTurnLeft", listOf("M17 20 V10 C17 7.2 14.8 5 12 5 C9.2 5 7 7.2 7 10 V15", "M3.5 11.5 L7 15 L10.5 11.5")),
        draw("Rounded.Accessible", listOf(circle(11f, 4.5f, 1.8f), "M11 8 V14 H16 L18.5 19.5", "M8 11 C5.5 12.2 4.5 14.5 5 17 C5.8 20 9.5 21.2 12 19.5")),
        draw(
            "Rounded.AddToHomeScreen",
            listOf("M8 3 H16 C16.8 3 17.5 3.7 17.5 4.5 V19.5 C17.5 20.3 16.8 21 16 21 H8 C7.2 21 6.5 20.3 6.5 19.5 V16", "M6.5 8 V4.5 C6.5 3.7 7.2 3 8 3", "M2.5 12 H11 M8 9 L11 12 L8 15"),
        ),
        draw("Rounded.Watch", listOf(circle(12f, 12f, 5.5f), "M9 7 L9.8 3.5 H14.2 L15 7 M9 17 L9.8 20.5 H14.2 L15 17", "M12 9.5 V12 L13.5 13.5")),
        draw("Rounded.Widgets", listOf("M4 13 H10 V19 H4 Z", "M4 4.5 H10 V10.5 H4 Z", "M13 13 H19 V19 H13 Z", "M16 3.5 L20 7.5 L16 11.5 L12 7.5 Z")),
    ).associateBy { it.name }
}

private val placeDrawings by lazy {
    mapOf(
        "home" to draw("Place.Home", listOf("M4 11 L12 4.5 L20 11", "M6 9.5 V19.5 H18 V9.5", "M10 19.5 V14.5 H14 V19.5")),
        "work" to draw(
            "Place.Work",
            listOf("M5 20 V5 H14 V20", "M14 10 H19 V20", "M3.5 20 H20.5", "M8 8.5 H11 M8 12 H11 M8 15.5 H11 M16 13.5 H17 M16 16.5 H17"),
        ),
        "school" to draw(
            "Place.School",
            listOf("M2.5 9 L12 4.5 L21.5 9 L12 13.5 Z", "M6.5 11 V15.5 C8 17 10 17.8 12 17.8 C14 17.8 16 17 17.5 15.5 V11", "M21.5 9 V14"),
        ),
        "person" to draw("Place.Person", listOf(circle(12f, 8f, 3.6f), "M5 20 C5.5 15.8 8.4 13.5 12 13.5 C15.6 13.5 18.5 15.8 19 20")),
        "heart" to draw("Place.Heart", listOf(HEART)),
    )
}

private val emojiPlaces = mapOf(
    "home" to "🏠🏡🏘",
    "work" to "🏢🏬🏭💼",
    "school" to "🏫🎓📚",
    "person" to "👤👥🧑👩👨👵👴",
    "heart" to "❤💕",
)

/** Null when [emoji] has no drawing. */
fun placeDrawing(emoji: String): ImageVector? {
    val first = emoji.codePointAt(0)
    val place = emojiPlaces.entries.firstOrNull { (_, set) -> set.codePoints().anyMatch { it == first } } ?: return null
    return placeDrawings[place.key]
}

/** [icon] itself when there is no drawing for it. */
@Composable
fun inked(icon: ImageVector): ImageVector = if (LocalDoodle.current != null) byName[icon.name] ?: icon else icon

/** Hand-drawn in the doodle look when there is a drawing. */
@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) =
    androidx.compose.material3.Icon(inked(imageVector), contentDescription, modifier, tint)
