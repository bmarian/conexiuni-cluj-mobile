package online.bus.bmarian.conexiuniclujmobile.logic

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object Wobble {
    /** x, y pairs clockwise from the top left, each pushed out by up to [amplitude]. */
    fun outline(width: Float, height: Float, radii: FloatArray, amplitude: Float, wavelength: Float, step: Float, seed: Int): FloatArray {
        val cap = min(width, height) / 2
        val (tl, tr, br, bl) = radii.map { it.coerceIn(0f, cap) }
        val base = ArrayList<FloatArray>()
        fun edge(x0: Float, y0: Float, x1: Float, y1: Float, nx: Float, ny: Float) {
            val length = max(kotlin.math.hypot(x1 - x0, y1 - y0), 0f)
            val count = max(1, (length / step).roundToInt())
            for (i in 0 until count) {
                val t = i.toFloat() / count
                base += floatArrayOf(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, nx, ny)
            }
        }
        fun corner(cx: Float, cy: Float, r: Float, from: Double) {
            val count = max(1, (r * PI / 2 / step).roundToInt())
            for (i in 0 until count) {
                val a = from + PI / 2 * i / count
                val nx = cos(a).toFloat()
                val ny = sin(a).toFloat()
                base += floatArrayOf(cx + r * nx, cy + r * ny, nx, ny)
            }
        }
        edge(tl, 0f, width - tr, 0f, 0f, -1f)
        corner(width - tr, tr, tr, -PI / 2)
        edge(width, tr, width, height - br, 1f, 0f)
        corner(width - br, height - br, br, 0.0)
        edge(width - br, height, bl, height, 0f, 1f)
        corner(bl, height - bl, bl, PI / 2)
        edge(0f, height - bl, 0f, tl, -1f, 0f)
        corner(tl, tl, tl, PI)

        var perimeter = 0f
        val along = FloatArray(base.size)
        for (i in 1 until base.size) {
            perimeter += kotlin.math.hypot(base[i][0] - base[i - 1][0], base[i][1] - base[i - 1][1])
            along[i] = perimeter
        }
        perimeter += kotlin.math.hypot(base.first()[0] - base.last()[0], base.first()[1] - base.last()[1])
        val cells = max(3, (perimeter / wavelength).roundToInt())
        val out = FloatArray(base.size * 2)
        base.forEachIndexed { i, (x, y, nx, ny) ->
            val d = amplitude * noise(along[i] / perimeter * cells, cells, seed)
            out[i * 2] = x + nx * d
            out[i * 2 + 1] = y + ny * d
        }
        return out
    }

    /** Smooth value noise in [-1, 1] that repeats every [cells]. */
    fun noise(x: Float, cells: Int, seed: Int): Float {
        val i = floor(x).toInt()
        val f = x - i
        val a = lattice(Math.floorMod(i, cells), seed)
        val b = lattice(Math.floorMod(i + 1, cells), seed)
        val s = f * f * (3 - 2 * f)
        return a + (b - a) * s
    }

    private fun lattice(i: Int, seed: Int): Float {
        var h = i * 374761393 + seed * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 32767.5f - 1f
    }
}
