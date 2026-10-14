package online.bus.bmarian.conexiuniclujmobile.ui.settings

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Accessible
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalResources
import androidx.core.net.toUri
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.AppUpdates
import online.bus.bmarian.conexiuniclujmobile.BuildConfig
import online.bus.bmarian.conexiuniclujmobile.R
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.ThemeMode
import online.bus.bmarian.conexiuniclujmobile.data.Transfer
import online.bus.bmarian.conexiuniclujmobile.follow.WearLink
import online.bus.bmarian.conexiuniclujmobile.ui.ChoiceRow
import online.bus.bmarian.conexiuniclujmobile.ui.LocalSheet
import online.bus.bmarian.conexiuniclujmobile.ui.PopIcon
import online.bus.bmarian.conexiuniclujmobile.ui.RisingArrow
import online.bus.bmarian.conexiuniclujmobile.ui.SheetSnap
import online.bus.bmarian.conexiuniclujmobile.ui.SheetTopBar
import online.bus.bmarian.conexiuniclujmobile.ui.buzz
import online.bus.bmarian.conexiuniclujmobile.ui.theme.Palette

@Composable
fun SettingsScreen(sendCode: String?, sendKey: String?) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val settings = Services.settings
    val locale by settings.locale.collectAsStateWithLifecycle()
    val theme by settings.theme.collectAsStateWithLifecycle()
    val news by settings.showNews.collectAsStateWithLifecycle()
    val extras by settings.showVehicleExtras.collectAsStateWithLifecycle()
    val greenFriday by settings.showGreenFriday.collectAsStateWithLifecycle()
    val center by settings.autoCenterOnMe.collectAsStateWithLifecycle()
    val fit by settings.autoFitMap.collectAsStateWithLifecycle()
    val alarms by settings.fullScreenAlarms.collectAsStateWithLifecycle()
    val update by AppUpdates.available.collectAsStateWithLifecycle()
    val watchApp by WearLink.watchWithoutApp.collectAsStateWithLifecycle()
    val transfer: TransferViewModel = viewModel()
    val sheet = LocalSheet.current
    val scope = rememberCoroutineScope()
    val updateFlow = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}

    // Once per link.
    var linkHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sendCode, sendKey) {
        if (linkHandled) return@LaunchedEffect
        linkHandled = true
        if (sendCode != null && Regex("""^\d{6}$""").matches(sendCode)) {
            transfer.openSend(sendCode, sendKey)
            sheet?.animateTo(SheetSnap.Full)
        }
    }

    val notifications = context.getSystemService(NotificationManager::class.java)
    fun fullScreenAllowed() = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || notifications.canUseFullScreenIntent()
    val fullScreenAccess = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!fullScreenAllowed()) settings.showToast(resources.getString(R.string.alarm_full_screen_off))
    }
    fun toggleAlarms(on: Boolean) {
        settings.setFullScreenAlarms(on)
        if (on && !fullScreenAllowed()) {
            fullScreenAccess.launch(Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri()))
        }
    }

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        val pastHeader by remember { derivedStateOf { scroll.value > 120 } }
        SheetTopBar(stringResource(R.string.w_settings), pastHeader)
        Column(Modifier.weight(1f).verticalScroll(scroll)) {
            Text(
                stringResource(R.string.w_settings),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Prompt(
                update,
                stringResource(R.string.update_question),
                Icons.Rounded.Check,
                stringResource(R.string.w_yes),
                onYes = { scope.launch { AppUpdates.update(updateFlow) } },
                onNo = AppUpdates::decline,
            ) { RisingArrow(tint = it) }
            Prompt(
                watchApp && !update,
                stringResource(R.string.watch_install_question),
                Icons.Rounded.Download,
                stringResource(R.string.watch_install),
                onYes = { WearLink.installOnWatch(context) },
                onNo = { WearLink.skipWatchApp(context) },
            ) { Icon(Icons.Rounded.Watch, null, Modifier.buzz(), tint = it) }

            Section(stringResource(R.string.w_language))
            if (locale == "en") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Palette.Amber.copy(alpha = 0.12f)).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.WarningAmber, null, Modifier.size(18.dp), tint = Palette.Amber)
                    Text(stringResource(R.string.w_language_desc), style = MaterialTheme.typography.bodySmall)
                }
            }
            val languages = listOf("ro" to "Română", "en" to "English")
            ChoiceRow(languages.map { it.first }, locale, settings::setLocale, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { tag ->
                Text(languages.first { it.first == tag }.second)
            }

            Section(stringResource(R.string.w_display))
            ThemeRow(theme, settings::setTheme)
            Toggle(Icons.Rounded.Newspaper, stringResource(R.string.w_news), stringResource(R.string.w_news_desc), news, onChange = settings::setShowNews)
            Toggle(Icons.Rounded.Accessible, stringResource(R.string.w_vehicle_extras), stringResource(R.string.w_vehicle_extras_desc), extras, onChange = settings::setShowVehicleExtras)
            Toggle(Icons.Rounded.Eco, stringResource(R.string.w_green_friday_title), stringResource(R.string.w_green_friday_setting_desc), greenFriday, onChange = settings::setShowGreenFriday)

            Section(stringResource(R.string.w_settings_map))
            Toggle(Icons.Rounded.MyLocation, stringResource(R.string.w_auto_center_on_me), stringResource(R.string.w_auto_center_on_me_desc), center, onChange = settings::setAutoCenterOnMe)
            Toggle(Icons.Rounded.CropFree, stringResource(R.string.w_auto_fit_map), stringResource(R.string.w_auto_fit_map_desc), fit, onChange = settings::setAutoFitMap)

            Section(stringResource(R.string.w_settings_notifications))
            Toggle(Icons.Rounded.Alarm, stringResource(R.string.alarm_setting), stringResource(R.string.alarm_setting_desc), alarms, beta = true, onChange = ::toggleAlarms)

            Section(stringResource(R.string.w_transfer))
            Text(
                stringResource(R.string.w_transfer_desc),
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TransferPanel(transfer)
            Text(
                stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                Modifier.fillMaxWidth().padding(top = 24.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** A one-off question at the top. Slides in and folds away once answered. */
@Composable
private fun Prompt(
    visible: Boolean,
    question: String,
    yesIcon: ImageVector,
    yes: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val state = remember { MutableTransitionState(false) }.apply { targetState = visible }
    AnimatedVisibility(
        state,
        enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val colors = MaterialTheme.colorScheme
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                        icon(colors.onPrimary)
                    }
                    Text(question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(shapes = ButtonDefaults.shapes(), onClick = onYes, modifier = Modifier.weight(1f)) {
                        Icon(yesIcon, null, Modifier.size(18.dp))
                        Text(yes, Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onNo,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onPrimaryContainer),
                        border = BorderStroke(1.dp, colors.onPrimaryContainer.copy(alpha = 0.4f)),
                    ) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.w_no), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Toggle(icon: ImageVector, title: String, description: String, checked: Boolean, beta: Boolean = false, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.padding(horizontal = 4.dp),
        leadingContent = { Icon(icon, null) },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title)
                if (beta) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Palette.Amber.copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Text(stringResource(R.string.w_beta).uppercase(), style = MaterialTheme.typography.labelSmall, color = Color(0xFFB45309))
                    }
                }
            }
        },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                thumbContent = { PopIcon(if (checked) Icons.Rounded.Check else Icons.Rounded.Close, checked, Modifier.size(SwitchDefaults.IconSize)) },
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun TransferPanel(vm: TransferViewModel) {
    val context = LocalContext.current
    val mode by vm.mode.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.pollNow() } }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(shapes = ButtonDefaults.shapes(), onClick = { vm.openSend() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(18.dp))
                Text(stringResource(R.string.w_transfer_send), Modifier.padding(start = 8.dp))
            }
            Button(shapes = ButtonDefaults.shapes(), onClick = vm::openReceive, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.QrCode2, null, Modifier.size(18.dp))
                Text(stringResource(R.string.w_transfer_receive), Modifier.padding(start = 8.dp))
            }
        }
        when (mode) {
            TransferMode.Send -> SendPanel(vm)
            TransferMode.Receive -> ReceivePanel(vm)
            else -> {}
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = vm::openExport, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                Text(stringResource(R.string.w_export_settings), Modifier.padding(start = 8.dp))
            }
            FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = vm::openImport, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp))
                Text(stringResource(R.string.w_import_settings), Modifier.padding(start = 8.dp))
            }
        }
        if (mode == TransferMode.Export || mode == TransferMode.Import) {
            val text by vm.text.collectAsStateWithLifecycle()
            val exportDone by vm.exportDone.collectAsStateWithLifecycle()
            val importState by vm.importState.collectAsStateWithLifecycle()
            val export = mode == TransferMode.Export
            OutlinedTextField(
                value = text,
                onValueChange = { if (!export) vm.text.value = it },
                readOnly = export,
                minLines = 5,
                maxLines = 7,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { if (!export) Text(stringResource(R.string.w_import_paste_placeholder)) },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (export) {
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(Intent.createChooser(send, null))
                            vm.markExported()
                        },
                        enabled = !exportDone,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (exportDone) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Text(stringResource(if (exportDone) R.string.w_export_copied else R.string.w_share), Modifier.padding(start = 6.dp))
                    }
                } else {
                    Button(
                        shapes = ButtonDefaults.shapes(),
                        onClick = vm::doImport,
                        enabled = text.isNotBlank() && importState == "idle",
                        modifier = Modifier.weight(1f),
                        colors = if (importState == "error") ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                    ) {
                        Text(
                            when (importState) {
                                "success" -> "✓ " + stringResource(R.string.w_import_success)
                                "error" -> "✕ " + stringResource(R.string.w_import_error)
                                else -> stringResource(R.string.w_import_confirm)
                            },
                        )
                    }
                }
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = vm::cancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.w_cancel)) }
            }
        }
    }
}

@Composable
private fun SendPanel(vm: TransferViewModel) {
    val context = LocalContext.current
    val code by vm.code.collectAsStateWithLifecycle()
    val state by vm.sendState.collectAsStateWithLifecycle()
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 7) vm.code.value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.w_send_code_placeholder), Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                textStyle = TextStyle(fontSize = 20.sp, letterSpacing = 4.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(onDone = { vm.doSend() }),
                trailingIcon = {
                    androidx.compose.material3.IconButton(onClick = {
                        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                        GmsBarcodeScanning.getClient(context, options).startScan().addOnSuccessListener { barcode ->
                            val uri = barcode.rawValue?.let(Uri::parse) ?: return@addOnSuccessListener
                            val scannedCode = uri.getQueryParameter("send") ?: return@addOnSuccessListener
                            val key = uri.fragment?.split('&')?.firstOrNull { it.startsWith("k=") }?.removePrefix("k=")
                            vm.openSend(scannedCode, key)
                        }
                    }) { Icon(Icons.Rounded.QrCodeScanner, contentDescription = stringResource(R.string.scan_code)) }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val failed = state == SendState.Unknown || state == SendState.Limited || state == SendState.Failed
                Button(
                    shapes = ButtonDefaults.shapes(),
                    onClick = vm::doSend,
                    enabled = vm.canSend() || state != SendState.Idle,
                    modifier = Modifier.weight(1f),
                    colors = when {
                        state == SendState.Sent -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        failed -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        else -> ButtonDefaults.buttonColors()
                    },
                ) {
                    if (state == SendState.Busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text(
                        when (state) {
                            SendState.Sent -> "✓ " + stringResource(R.string.w_send_done)
                            SendState.Unknown -> "✕ " + stringResource(R.string.w_send_unknown_code)
                            SendState.Limited -> "✕ " + stringResource(R.string.w_send_too_many)
                            SendState.Failed -> "✕ " + stringResource(R.string.w_send_failed)
                            else -> stringResource(R.string.w_send_confirm)
                        },
                    )
                }
                OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = vm::cancel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.w_cancel)) }
            }
        }
    }
}

@Composable
private fun ReceivePanel(vm: TransferViewModel) {
    val state by vm.receiveState.collectAsStateWithLifecycle()
    val link by vm.link.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (state) {
                ReceiveState.Waiting -> link?.let { current ->
                    val url = Transfer.url(current.code, vm.key)
                    Box(
                        Modifier.size(170.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp)).padding(10.dp),
                    ) { QrCode(url, Modifier.fillMaxSize()) }
                    Text(Transfer.spaced(current.code), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Text(stringResource(R.string.w_transfer_hint), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ReceiveState.Review, ReceiveState.Done -> {
                    Text(stringResource(R.string.w_receive_review), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    summary?.let { s ->
                        for ((label, count) in listOf(
                            R.string.w_favorite_stops to s.stops,
                            R.string.w_favorite_routes to s.lines,
                            R.string.w_favorite_plans to s.places,
                            R.string.w_followed_lines to s.followed,
                        )) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                                Text("$count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                ReceiveState.Expired -> Text(stringResource(R.string.w_transfer_expired))
                ReceiveState.Failed -> Text(stringResource(R.string.w_transfer_failed))
                ReceiveState.Broken -> Text(stringResource(R.string.w_transfer_broken), textAlign = TextAlign.Center)
                else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.w_transfer_loading))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    ReceiveState.Done -> Button(shapes = ButtonDefaults.shapes(), onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                        Text("✓ " + stringResource(R.string.w_receive_done))
                    }
                    ReceiveState.Review -> Button(shapes = ButtonDefaults.shapes(), onClick = vm::accept, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.w_receive_replace)) }
                    ReceiveState.Expired, ReceiveState.Failed, ReceiveState.Broken ->
                        Button(shapes = ButtonDefaults.shapes(), onClick = vm::startReceive, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.w_transfer_new_code)) }
                    else -> {}
                }
                if (state != ReceiveState.Done) {
                    TextButton(shapes = ButtonDefaults.shapes(), onClick = vm::cancel, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(16.dp))
                        Text(stringResource(R.string.w_cancel), Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
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

@Composable
private fun ThemeRow(theme: ThemeMode, onChange: (ThemeMode) -> Unit) {
    val options = listOf(
        Triple(ThemeMode.Light, Icons.Rounded.LightMode, R.string.w_theme_light),
        Triple(ThemeMode.Dark, Icons.Rounded.DarkMode, R.string.w_theme_dark),
        Triple(ThemeMode.System, Icons.Rounded.BrightnessMedium, R.string.w_theme_system),
    )
    ChoiceRow(
        options.map { it.first }, theme, onChange,
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        icon = { mode -> options.first { it.first == mode }.second },
    ) { mode ->
        Text(stringResource(options.first { it.first == mode }.third), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
