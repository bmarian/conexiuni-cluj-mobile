package online.bus.bmarian.conexiuniclujmobile.follow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.ui.theme.routeColor
import kotlin.math.roundToInt

private const val APP_GREEN = 0xFF10B981.toInt()

/** Bitmaps for the follow and journey notifications. */
internal object NotificationArt {
    private val cache = LruCache<String, Icon>(16)

    private fun cached(key: String, draw: () -> Icon): Icon = cache.get(key) ?: draw().also { cache.put(key, it) }

    /** The app's line badge, for the large icon. */
    fun badge(context: Context, shortName: String, color: String?): Icon = cached("badge|$shortName|$color") {
        val size = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = routeColor(color).toArgb() }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.22f, size * 0.22f, fill)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, 900, false)
            textAlign = Paint.Align.CENTER
            textSize = size * 0.42f
        }
        val room = size * 0.84f
        val width = text.measureText(shortName)
        if (width > room) text.textSize *= room / width
        canvas.drawText(shortName, size / 2f, size / 2f - (text.descent() + text.ascent()) / 2f, text)
        Icon.createWithBitmap(bitmap)
    }

    /** A side-on bus in the line's colour for the progress tracker, 40×20 dp. */
    fun bus(context: Context, color: String?): Icon = cached("bus|$color") {
        val d = context.resources.displayMetrics.density
        val w = (40 * d).roundToInt().toFloat()
        val h = (20 * d).roundToInt().toFloat()
        val bitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val body = routeColor(color).toArgb()

        val shell = RectF(w * 0.03f, h * 0.08f, w * 0.97f, h * 0.80f)
        paint.color = body
        canvas.drawRoundRect(shell, h * 0.24f, h * 0.24f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = h * 0.06f
        paint.color = shade(body, 0.7f)
        canvas.drawRoundRect(shell, h * 0.24f, h * 0.24f, paint)
        paint.style = Paint.Style.FILL

        paint.color = 0xFFE0F2FE.toInt()
        val top = h * 0.22f
        val windows = 4
        val start = w * 0.09f
        val gap = w * 0.025f
        val pane = (w * 0.74f - start - gap * (windows - 1)) / windows
        for (i in 0 until windows) {
            val left = start + i * (pane + gap)
            canvas.drawRoundRect(RectF(left, top, left + pane, h * 0.47f), h * 0.05f, h * 0.05f, paint)
        }
        canvas.drawRoundRect(RectF(w * 0.78f, top, w * 0.93f, h * 0.58f), h * 0.08f, h * 0.08f, paint)

        paint.color = 0xFFFDE68A.toInt()
        canvas.drawCircle(w * 0.935f, h * 0.68f, h * 0.05f, paint)

        for (x in listOf(w * 0.25f, w * 0.72f)) {
            paint.color = 0xFF1F2937.toInt()
            canvas.drawCircle(x, h * 0.80f, h * 0.17f, paint)
            paint.color = 0xFFCBD5E1.toInt()
            canvas.drawCircle(x, h * 0.80f, h * 0.07f, paint)
        }
        Icon.createWithBitmap(bitmap)
    }

    fun walker(context: Context): Icon = Icon.createWithResource(context, R.drawable.ic_walk).setTint(APP_GREEN)

    /** A stop sign for the end of the bar, like the selected stop on the map. */
    fun stop(context: Context): Icon = cached("stop") { sign(context, R.drawable.ic_bus) }

    fun destination(context: Context): Icon = cached("destination") { sign(context, R.drawable.ic_flag) }

    private fun sign(context: Context, glyph: Int): Icon {
        val size = (20 * context.resources.displayMetrics.density).roundToInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = APP_GREEN }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        val inset = (size * 0.22f).roundToInt()
        context.getDrawable(glyph)!!.mutate().apply {
            setTint(Color.WHITE)
            setBounds(inset, inset, size - inset, size - inset)
            draw(canvas)
        }
        return Icon.createWithBitmap(bitmap)
    }

    private fun shade(color: Int, factor: Float) = Color.rgb(
        (Color.red(color) * factor).roundToInt(),
        (Color.green(color) * factor).roundToInt(),
        (Color.blue(color) * factor).roundToInt(),
    )
}
