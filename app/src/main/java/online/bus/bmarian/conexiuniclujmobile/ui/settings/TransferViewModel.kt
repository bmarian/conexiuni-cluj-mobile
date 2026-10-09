package online.bus.bmarian.conexiuniclujmobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.Api
import online.bus.bmarian.conexiuniclujmobile.data.Transfer
import online.bus.bmarian.conexiuniclujmobile.data.TransferLink

private const val POLL_MS = 2000L

enum class TransferMode { Export, Import, Send, Receive }

enum class ReceiveState { Idle, Loading, Waiting, Review, Done, Expired, Failed, Broken }

enum class SendState { Idle, Busy, Sent, Unknown, Limited, Failed }

/** Port of SettingsExportImport and useTransferReceiver. */
class TransferViewModel : ViewModel() {
    val mode = MutableStateFlow<TransferMode?>(null)

    val text = MutableStateFlow("")
    val exportDone = MutableStateFlow(false)
    val importState = MutableStateFlow("idle")

    val code = MutableStateFlow("")
    val sendState = MutableStateFlow(SendState.Idle)
    private var scanned: Pair<String, String?>? = null

    val receiveState = MutableStateFlow(ReceiveState.Idle)
    val link = MutableStateFlow<TransferLink?>(null)
    val summary = MutableStateFlow<Transfer.Summary?>(null)
    var key: String = ""
        private set
    private var received: JsonElement? = null
    private var expiresAt = 0L
    private var receiveJob: Job? = null
    private var timer: Job? = null

    private fun closeLater(ms: Long, then: () -> Unit) {
        timer?.cancel()
        timer = viewModelScope.launch {
            delay(ms)
            then()
        }
    }

    fun cancel() {
        if (mode.value == TransferMode.Receive) stopReceive()
        mode.value = null
        exportDone.value = false
        importState.value = "idle"
        sendState.value = SendState.Idle
        timer?.cancel()
    }

    private fun switchTo(next: TransferMode) {
        if (mode.value == TransferMode.Receive && next != TransferMode.Receive) stopReceive()
        mode.value = next
    }

    fun openExport() {
        switchTo(TransferMode.Export)
        text.value = Transfer.compress(Transfer.buildExport().toString())
        exportDone.value = false
    }

    fun markExported() {
        exportDone.value = true
        closeLater(2000) { exportDone.value = false }
    }

    fun openImport() {
        switchTo(TransferMode.Import)
        text.value = ""
        importState.value = "idle"
    }

    fun doImport() {
        try {
            Transfer.applyExport(Transfer.parseImport(text.value))
            importState.value = "success"
            closeLater(1500) { cancel() }
        } catch (_: Exception) {
            importState.value = "error"
            closeLater(2000) { importState.value = "idle" }
        }
    }

    fun openSend(prefill: String = "", key: String? = null) {
        switchTo(TransferMode.Send)
        code.value = prefill
        scanned = if (prefill.isNotEmpty()) prefill to key else null
        sendState.value = SendState.Idle
    }

    val codeDigits: String get() = code.value.filter(Char::isDigit)

    fun canSend(): Boolean = codeDigits.length == 6 && sendState.value == SendState.Idle

    fun doSend() {
        if (!canSend()) return
        sendState.value = SendState.Busy
        val digits = codeDigits
        val sealKey = scanned?.takeIf { it.first == digits }?.second
        viewModelScope.launch {
            val result = Transfer.send(digits, Transfer.buildExport(), sealKey)
            sendState.value = when (result) {
                Transfer.SendResult.Sent -> SendState.Sent
                Transfer.SendResult.Unknown -> SendState.Unknown
                Transfer.SendResult.Limited -> SendState.Limited
                Transfer.SendResult.Failed -> SendState.Failed
            }
            if (result == Transfer.SendResult.Sent) closeLater(1500) { cancel() }
            else closeLater(2000) { sendState.value = SendState.Idle }
        }
    }

    fun openReceive() {
        switchTo(TransferMode.Receive)
        startReceive()
    }

    fun startReceive() {
        receiveJob?.cancel()
        release()
        receiveState.value = ReceiveState.Loading
        receiveJob = viewModelScope.launch {
            val created = try {
                Api.createTransfer()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                receiveState.value = ReceiveState.Failed
                return@launch
            }
            key = Transfer.newKey()
            link.value = created
            expiresAt = System.currentTimeMillis() + created.expiresIn * 1000L
            receiveState.value = ReceiveState.Waiting
            poll(created)
        }
    }

    // pollNow may have taken it meanwhile.
    private fun stillWaitingOn(current: TransferLink) =
        receiveState.value == ReceiveState.Waiting && link.value === current

    private suspend fun poll(current: TransferLink) {
        while (stillWaitingOn(current)) {
            delay(POLL_MS)
            if (!stillWaitingOn(current)) return
            if (System.currentTimeMillis() > expiresAt) {
                receiveState.value = ReceiveState.Expired
                return
            }
            val result = runCatching { Api.readTransfer(current) }.getOrNull() ?: continue
            if (!stillWaitingOn(current)) return
            when (result) {
                Api.TransferRead.Waiting -> {}
                Api.TransferRead.Gone -> receiveState.value = ReceiveState.Expired
                is Api.TransferRead.Received -> receive(current, result.data)
            }
        }
    }

    fun pollNow() {
        val current = link.value ?: return
        if (receiveState.value != ReceiveState.Waiting) return
        viewModelScope.launch {
            val result = runCatching { Api.readTransfer(current) }.getOrNull() ?: return@launch
            if (!stillWaitingOn(current)) return@launch
            when (result) {
                Api.TransferRead.Gone -> receiveState.value = ReceiveState.Expired
                is Api.TransferRead.Received -> receive(current, result.data)
                Api.TransferRead.Waiting -> {}
            }
        }
    }

    private fun receive(from: TransferLink, payload: JsonElement) {
        Services.scope.launch { Api.deleteTransfer(from) }
        link.value = null
        received = runCatching { Transfer.open(payload, key) }.getOrNull()
        summary.value = received?.let(Transfer::describe)
        receiveState.value = if (summary.value != null) ReceiveState.Review else ReceiveState.Broken
    }

    fun accept() {
        if (receiveState.value != ReceiveState.Review) return
        try {
            Transfer.applyExport(received ?: return)
            receiveState.value = ReceiveState.Done
            closeLater(1500) { cancel() }
        } catch (_: Exception) {
            receiveState.value = ReceiveState.Broken
        }
    }

    private fun stopReceive() {
        receiveJob?.cancel()
        release()
        receiveState.value = ReceiveState.Idle
    }

    private fun release() {
        link.value?.let { Services.scope.launch { Api.deleteTransfer(it) } }
        link.value = null
        summary.value = null
        received = null
    }

    override fun onCleared() {
        receiveJob?.cancel()
        release()
    }
}
