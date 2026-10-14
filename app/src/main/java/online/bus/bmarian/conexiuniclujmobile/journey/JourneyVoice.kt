package online.bus.bmarian.conexiuniclujmobile.journey

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import online.bus.bmarian.conexiuniclujmobile.Services
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private val FIRST_NAMES = listOf(
    Triple("M", "Viteaz", "Mihai"),
    Triple("N", "B[aă]lcescu", "Nicolae"),
    Triple("I", "Ag[aâ]rbiceanu", "Ion"),
    Triple("Al", "Averescu", "Alexandru"),
    Triple("T", "Vladimirescu", "Tudor"),
    Triple("E", "Quinet", "Edgar"),
    Triple("E", "Ionesco", "Eugen"),
    Triple("I", "Ionescu", "Ion"),
    Triple("T", "D[aâ]rjan", "Traian"),
    Triple("V", "Babe[șş]", "Victor"),
    Triple("A", "Vlaicu", "Aurel"),
    Triple("Ghe?", "(Asachi|Dima)", "Gheorghe"),
    Triple("Gr", "Alexandrescu", "Grigore"),
    Triple("""W\.\s*A""", "Mozart", "Wolfgang Amadeus"),
    Triple("C", "Porumbescu", "Ciprian"),
    Triple("G", "Bari[țţ]iu", "George"),
    Triple("""I\.\s*P""", "Voite[șş]ti", "Ion Popescu"),
    Triple("""D\.\s*D""", "Ro[șş]ca", "Dumitru D."),
).map { (initial, surname, first) -> Regex("""\b$initial\.?\s*(?=$surname)""") to "$first " }

private val SHORT_FORMS = listOf(
    // Metropolitan stops start with "M-".
    Regex("""^M\s*-\s*|^M\s+(?=\p{Lu})""") to "",
    Regex("""\bP-?[țţt]a\b\.?\s*""") to "Piața ",
    Regex("""\bStr\.\s*""") to "Strada ",
    Regex("""\b(B-?dul|Bld|Bd)\b\.?\s*""") to "Bulevardul ",
    Regex("""\bDisp\.\s*""") to "Dispecerat ",
    Regex("""\bCompl\.\s*""") to "Complex ",
    Regex("""\bBis\.\s*""") to "Biserica ",
    Regex("""\bCart\.\s*""") to "Cartierul ",
    Regex("""\bSp\.\s*""") to "Spitalul ",
    Regex("""\bSf\.\s*""") to "Sfântul ",
    Regex("""\bAv\.\s*""") to "Aviator ",
    Regex("""\bC-tin\b\s*""") to "Constantin ",
    Regex("""\bAp\.$""") to "Apahida",
    Regex("""\bSos\b""") to "Sosire",
    Regex("""\bSUD\b""") to "Sud",
    Regex("""\bDarjan\b""") to "Dârjan",
    // A direction letter at the end, before any "(Florești)".
    Regex("""\s+N\.?(?=\s*(\(|$))""") to " Nord",
    Regex("""\s+S\.?(?=\s*(\(|$))""") to " Sud",
    Regex("""\s+E\.?(?=\s*(\(|$))""") to " Est",
    Regex("""\s+V\.?(?=\s*(\(|$))""") to " Vest",
)

internal fun spokenStopName(name: String): String =
    (FIRST_NAMES + SHORT_FORMS).fold(name.trim()) { acc, (pattern, full) -> acc.replace(pattern, full) }
        .replace(Regex("""\s+"""), " ")
        .trim()

// U+2068 and U+2069, around names.
private const val NAME_START = '⁨'
private const val NAME_END = '⁩'

internal fun spokenName(name: String) = "$NAME_START${spokenStopName(name)}$NAME_END"

internal fun speechParts(text: String): List<Pair<String, Boolean>> = buildList {
    var rest = text
    while (rest.isNotEmpty()) {
        val start = rest.indexOf(NAME_START)
        if (start < 0) {
            add(rest to false)
            break
        }
        if (start > 0) add(rest.substring(0, start) to false)
        val end = rest.indexOf(NAME_END, start).takeIf { it >= 0 } ?: rest.length
        add(rest.substring(start + 1, end) to true)
        rest = if (end < rest.length) rest.substring(end + 1) else ""
    }
}.filter { (part, _) -> part.any(Char::isLetterOrDigit) }

private val UNITS = listOf("zero", "unu", "două", "trei", "patru", "cinci", "șase", "șapte", "opt", "nouă")
private val TEENS = listOf("zece", "unsprezece", "douăsprezece", "treisprezece", "paisprezece", "cincisprezece", "șaisprezece", "șaptesprezece", "optsprezece", "nouăsprezece")
private val TENS = listOf("", "", "douăzeci", "treizeci", "patruzeci", "cincizeci", "șaizeci", "șaptezeci", "optzeci", "nouăzeci")

/** A count of stops or minutes in Romanian words: "două", "douăsprezece", "douăzeci și una". */
internal fun romanianNumber(n: Int, feminine: Boolean): String {
    fun unit(u: Int) = if (u == 1 && feminine) "una" else UNITS[u]
    fun belowHundred(x: Int) = when {
        x < 10 -> unit(x)
        x < 20 -> TEENS[x - 10]
        x % 10 == 0 -> TENS[x / 10]
        else -> "${TENS[x / 10]} și ${unit(x % 10)}"
    }
    val value = n.coerceIn(0, 999)
    if (value < 100) return belowHundred(value)
    val hundreds = when (val h = value / 100) {
        1 -> "o sută"
        2 -> "două sute"
        else -> "${UNITS[h]} sute"
    }
    return if (value % 100 == 0) hundreds else "$hundreds ${belowHundred(value % 100)}"
}

/** "optsprezece și douăzeci și două", "nouă fix". */
internal fun romanianClock(hour: Int, minute: Int): String {
    val h = romanianNumber(hour, feminine = false)
    return if (minute == 0) "$h fix" else "$h și ${romanianNumber(minute, feminine = false)}"
}

private val ROMANIAN: Locale = Locale.forLanguageTag("ro-RO")

class JourneyVoice(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private val queued = AtomicInteger(0)
    private val tts: TextToSpeech
    private val languages = HashMap<Locale, Boolean>()
    private var ready = false
    private var closing = false
    private var pending: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status -> onInit(status) }
    }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS || closing) return
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finished()
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished()
        })
        ready = true
        pending?.let(::say)
        pending = null
    }

    fun say(text: String) {
        if (closing || !Services.settings.journeyVoice.value) return
        if (!ready) {
            pending = text
            return
        }
        val main = if (Services.settings.locale.value == "en") Locale.UK else ROMANIAN
        if (!available(main)) return
        // Names use the Romanian voice.
        val romanianNames = main != ROMANIAN && available(ROMANIAN)
        val parts = speechParts(text)
        val spoken = if (romanianNames) parts else listOf(parts.joinToString(" ") { it.first } to false)
        for ((part, name) in spoken) {
            tts.setLanguage(if (name && romanianNames) ROMANIAN else main)
            if (queued.getAndIncrement() == 0) audio.requestAudioFocus(focus)
            if (tts.speak(part, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString()) != TextToSpeech.SUCCESS) finished()
        }
    }

    fun stop() {
        pending = null
        if (ready) tts.stop()
        queued.set(0)
        audio.abandonAudioFocusRequest(focus)
    }

    fun close() {
        closing = true
        pending = null
        if (queued.get() == 0) shutdown()
    }

    private fun available(locale: Locale) = languages.getOrPut(locale) { tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE }

    private fun finished() {
        if (queued.decrementAndGet() > 0) return
        queued.set(0)
        audio.abandonAudioFocusRequest(focus)
        if (closing) shutdown()
    }

    private fun shutdown() {
        audio.abandonAudioFocusRequest(focus)
        tts.shutdown()
    }
}
