package online.bus.bmarian.conexiuniclujmobile.ui.settings

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Cities
import online.bus.bmarian.conexiuniclujmobile.data.sync.Sync
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncEngine
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

private sealed interface Ask {
    data class Remove(val id: String, val name: String) : Ask
    data object Off : Ask
    data object Everywhere : Ask
}

private val CODE = Regex("""^\d{6}$""")

/** Port of components/SettingsSync.vue. */
@Composable
fun SyncPanel(pairCode: String?, pairKey: String?, onPairing: () -> Unit) {
    val vm: SyncPairingViewModel = viewModel()
    val sync = Services.sync
    val scope = rememberCoroutineScope()
    val status by sync.status.collectAsStateWithLifecycle()
    val devices by sync.devices.collectAsStateWithLifecycle()
    val problems by sync.problems.collectAsStateWithLifecycle()
    val busy by sync.busy.collectAsStateWithLifecycle()
    val removed by sync.removed.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    var entering by rememberSaveable { mutableStateOf(false) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    var removeFailed by remember { mutableStateOf(false) }
    var lastAction by rememberSaveable { mutableStateOf("show") }
    val pairingOpen = entering || phase != PairPhase.Idle

    // A scanned QR opened the app; scanning was the user's go-ahead. Once per link.
    var linkHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pairCode, pairKey) {
        if (linkHandled || pairCode == null || !CODE.matches(pairCode)) return@LaunchedEffect
        linkHandled = true
        lastAction = "enter"
        vm.enter(pairCode, pairKey)
        onPairing()
    }
    LaunchedEffect(phase) {
        if (phase == PairPhase.Done) {
            delay(1500)
            if (vm.phase.value == PairPhase.Done) vm.cancel()
        }
    }

    fun show() {
        lastAction = "show"
        entering = false
        vm.show()
    }

    fun close() {
        vm.cancel()
        entering = false
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.w_sync_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (removed && status == Sync.Status.Off && !pairingOpen) Note(stringResource(R.string.w_sync_removed))
        if (status == Sync.Status.On) {
            StatusLine()
            for (problem in problems) Note(stringResource(problemText(problem), SyncRecords.CITY_CAP.toString()))
            if (removeFailed) Note(stringResource(R.string.w_sync_remove_failed))
        }
    }
    if (status == Sync.Status.On) for (device in devices) DeviceRow(device, busy) { ask = Ask.Remove(device.id, it) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!pairingOpen && ask == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shapes = ButtonDefaults.shapes(), onClick = ::show, modifier = Modifier.weight(1f)) {
                    Icon(if (status == Sync.Status.On) Icons.Rounded.Add else Icons.Rounded.QrCode2, null, Modifier.size(18.dp))
                    Text(stringResource(if (status == Sync.Status.On) R.string.w_sync_add_device else R.string.w_sync_show_code), Modifier.padding(start = 8.dp))
                }
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = {
                    lastAction = "enter"
                    vm.cancel()
                    entering = true
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Dialpad, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.w_sync_enter_code), Modifier.padding(start = 8.dp))
                }
            }
        }
        if (pairingOpen) {
            PairingPanel(vm, entering, onConnect = { code, key ->
                entering = false
                vm.enter(code, key)
            }, onRetry = { if (lastAction == "show") show() else { vm.cancel(); entering = true } }, onClose = ::close)
        }
        ask?.let { current ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        when (current) {
                            is Ask.Remove -> stringResource(R.string.w_sync_remove_ask, current.name)
                            Ask.Off -> stringResource(R.string.w_sync_turn_off_ask)
                            Ask.Everywhere -> stringResource(R.string.w_sync_delete_ask)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            shapes = ButtonDefaults.shapes(),
                            onClick = {
                                ask = null
                                removeFailed = false
                                scope.launch {
                                    try {
                                        when (current) {
                                            is Ask.Remove -> sync.removeDevice(current.id)
                                            Ask.Off -> sync.stop(everywhere = false)
                                            Ask.Everywhere -> sync.stop(everywhere = true)
                                        }
                                    } catch (_: Exception) {
                                        removeFailed = current is Ask.Remove
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                        ) {
                            Text(stringResource(when (current) {
                                is Ask.Remove -> R.string.w_sync_remove
                                Ask.Off -> R.string.w_sync_turn_off
                                Ask.Everywhere -> R.string.w_sync_delete
                            }))
                        }
                        OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = { ask = null }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.w_cancel)) }
                    }
                }
            }
        }
    }
    if (status == Sync.Status.On && !pairingOpen && ask == null) {
        val withSettings by sync.withSettings.collectAsStateWithLifecycle()
        Toggle(Icons.Rounded.BrightnessMedium, stringResource(R.string.w_sync_settings), stringResource(R.string.w_sync_settings_desc), withSettings) {
            sync.setWithSettings(it, scope)
        }
        Action(Icons.AutoMirrored.Rounded.Logout, stringResource(R.string.w_sync_turn_off)) { ask = Ask.Off }
        Action(Icons.Rounded.Delete, stringResource(R.string.w_sync_delete)) { ask = Ask.Everywhere }
    }
}

private fun problemText(problem: SyncEngine.Problem) = when (problem) {
    SyncEngine.Problem.Broken -> R.string.w_sync_problem_broken
    SyncEngine.Problem.Update -> R.string.w_sync_problem_update
    SyncEngine.Problem.OverCap -> R.string.w_sync_problem_over_cap
    SyncEngine.Problem.Full -> R.string.w_sync_problem_full
}

@Composable
private fun StatusLine() {
    val busy by Services.sync.busy.collectAsStateWithLifecycle()
    val offline by Services.sync.offline.collectAsStateWithLifecycle()
    val last by Services.sync.lastSynced.collectAsStateWithLifecycle()
    val text = when {
        busy -> stringResource(R.string.w_sync_syncing)
        offline -> stringResource(R.string.w_sync_offline)
        last != null -> stringResource(R.string.w_sync_synced, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(last!!)))
        else -> return
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Amber.copy(alpha = 0.12f)).padding(12.dp),
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun kindIcon(kind: String): ImageVector = when (kind) {
    "android" -> Icons.Rounded.Smartphone
    "wear" -> Icons.Rounded.Watch
    else -> Icons.Rounded.Computer
}

@Composable
private fun kindName(kind: String) = when (kind) {
    "android" -> "Android"
    "wear" -> "Wear OS"
    else -> stringResource(R.string.w_sync_web_device)
}

@Composable
private fun seenText(seen: Long): String {
    val day = seen * 1000
    val today = System.currentTimeMillis() / 86_400_000 * 86_400_000
    if (day >= today) return stringResource(R.string.w_sync_seen_today)
    val format = DateFormat.getDateInstance(DateFormat.MEDIUM).apply { timeZone = TimeZone.getTimeZone("UTC") }
    return stringResource(R.string.w_sync_seen, format.format(Date(day)))
}

@Composable
private fun DeviceRow(device: SyncEngine.DeviceView, busy: Boolean, onRemove: (String) -> Unit) {
    val name = device.label.ifEmpty { kindName(device.kind) }
    ListItem(
        modifier = Modifier.padding(horizontal = 4.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(kindIcon(device.kind), null) },
        headlineContent = { Text(name) },
        supportingContent = { Text(if (device.self) stringResource(R.string.w_sync_this_device) else seenText(device.seen)) },
        trailingContent = if (device.self) null else {
            { IconButton(onClick = { onRemove(name) }, enabled = !busy) { Icon(Icons.Rounded.Close, stringResource(R.string.w_sync_remove)) } }
        },
    )
}

@Composable
private fun Action(icon: ImageVector, title: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
    )
}

/** The code and key from a scanned sync QR, or null for anything else. */
private fun scannedPair(raw: String?): Pair<String, String?>? {
    val uri = raw?.let(Uri::parse) ?: return null
    val code = uri.getQueryParameter("pair")?.takeIf(CODE::matches) ?: return null
    return code to uri.fragment?.split('&')?.firstOrNull { it.startsWith("k=") }?.removePrefix("k=")
}

@Composable
private fun PairingPanel(vm: SyncPairingViewModel, entering: Boolean, onConnect: (String, String?) -> Unit, onRetry: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val phase by vm.phase.collectAsStateWithLifecycle()
    val failure by vm.failure.collectAsStateWithLifecycle()
    val overCap by vm.overCap.collectAsStateWithLifecycle()
    val code by vm.code.collectAsStateWithLifecycle()
    val url by vm.url.collectAsStateWithLifecycle()
    val check by vm.check.collectAsStateWithLifecycle()
    val peer by vm.peer.collectAsStateWithLifecycle()
    var typed by rememberSaveable { mutableStateOf("") }
    val digits = typed.filter(Char::isDigit)
    fun spaced(text: String) = if (text.length == 6) "${text.take(3)} ${text.drop(3)}" else text

    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                entering -> OutlinedTextField(
                    value = typed,
                    onValueChange = { if (it.length <= 7) typed = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.w_sync_code_placeholder), Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    textStyle = TextStyle(fontSize = 20.sp, letterSpacing = 4.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(onDone = { if (digits.length == 6) onConnect(digits, null) }),
                    trailingIcon = {
                        IconButton(onClick = {
                            val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                            GmsBarcodeScanning.getClient(context, options).startScan().addOnSuccessListener { barcode ->
                                scannedPair(barcode.rawValue)?.let { (scanned, key) -> onConnect(scanned, key) }
                            }
                        }) { Icon(Icons.Rounded.QrCodeScanner, contentDescription = stringResource(R.string.scan_code)) }
                    },
                )
                phase == PairPhase.Showing -> {
                    Box(
                        Modifier.size(170.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp)).padding(10.dp),
                    ) { QrCode(url, Modifier.fillMaxSize()) }
                    Text(spaced(code), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Text(stringResource(R.string.w_sync_show_hint), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                phase == PairPhase.Confirm && peer != null -> {
                    Text(stringResource(R.string.w_sync_confirm_title, peer!!.label), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Text(spaced(check), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                    Text(stringResource(R.string.w_sync_confirm_check), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                phase == PairPhase.Done -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.w_sync_done))
                }
                phase == PairPhase.Failed -> {
                    Text(stringResource(failureText(failure), SyncRecords.CITY_CAP.toString()), textAlign = TextAlign.Center)
                    if (failure == PairFailure.OverCap) {
                        for (item in overCap) {
                            Text(stringResource(R.string.w_sync_over_cap_item, stringResource(listName(item.list)), Cities.name(item.city), item.count.toString()), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(when (phase) {
                        PairPhase.Waiting -> R.string.w_sync_waiting
                        PairPhase.Starting -> R.string.w_sync_starting
                        else -> R.string.w_sync_connecting
                    }))
                }
            }
            if (phase != PairPhase.Done) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        entering -> Button(shapes = ButtonDefaults.shapes(), onClick = { onConnect(digits, null) }, enabled = digits.length == 6, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.w_sync_connect))
                        }
                        phase == PairPhase.Confirm -> Button(shapes = ButtonDefaults.shapes(), onClick = vm::confirm, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.w_sync_confirm))
                        }
                        phase == PairPhase.Failed && failure != PairFailure.OverCap && failure != PairFailure.Both ->
                            Button(shapes = ButtonDefaults.shapes(), onClick = onRetry, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.w_sync_try_again)) }
                    }
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = onClose, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                        Text(stringResource(R.string.w_cancel), Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

private fun failureText(failure: PairFailure?) = when (failure) {
    PairFailure.Expired -> R.string.w_sync_fail_expired
    PairFailure.Unknown -> R.string.w_sync_fail_unknown
    PairFailure.Limited -> R.string.w_sync_fail_limited
    PairFailure.Both -> R.string.w_sync_fail_both
    PairFailure.OverCap -> R.string.w_sync_fail_over_cap
    PairFailure.Offline -> R.string.w_sync_fail_offline
    else -> R.string.w_sync_fail_broken
}

private fun listName(list: String) = when (list) {
    "stops" -> R.string.w_favorite_stops
    "lines" -> R.string.w_favorite_routes
    "places" -> R.string.w_favorite_plans
    else -> R.string.w_followed_lines
}

/** Same as the website's uqr: ECC L, no margin. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L),
        )
    }
    Canvas(modifier) {
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color(0xFF0F172A), Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}
