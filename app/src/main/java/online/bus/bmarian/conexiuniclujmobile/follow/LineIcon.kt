package online.bus.bmarian.conexiuniclujmobile.follow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import online.bus.bmarian.conexiuniclujmobile.ui.theme.routeColor

private const val APP_GREEN = 0xFF10B981.toInt()

internal fun trackerIcon(context: Context, drawable: Int): Icon = Icon.createWithResource(context, drawable).setTint(APP_GREEN)

/** The app's line badge as a notification large icon. */
internal object LineIcon {
    private val cache = LruCache<String, Icon>(8)

    fun of(context: Context, shortName: String, color: String?): Icon = cache.get("$shortName|$color") ?: draw(context, shortName, color).also {
        cache.put("$shortName|$color", it)
    }

    private fun draw(context: Context, shortName: String, color: String?): Icon {
        val size = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = routeColor(color).toArgb() }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.22f, size * 0.22f, fill)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, 900, false)
            textAlign = Paint.Align.CENTER
            textSize = size * 0.42f
        }
        val room = size * 0.84f
        val width = text.measureText(shortName)
        if (width > room) text.textSize *= room / width
        val baseline = size / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(shortName, size / 2f, baseline, text)
        return Icon.createWithBitmap(bitmap)
    }
}
