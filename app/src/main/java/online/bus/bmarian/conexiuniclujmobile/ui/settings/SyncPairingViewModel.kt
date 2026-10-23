package online.bus.bmarian.conexiuniclujmobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import online.bus.bmarian.conexiuniclujmobile.BuildConfig
import online.bus.bmarian.conexiuniclujmobile.Services
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncApi
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncCrypto
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncHttpException
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncLocal
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncMessages
import java.io.IOException

private const val POLL_MS = 2000L

enum class PairPhase { Idle, Starting, Showing, Connecting, Confirm, Waiting, Done, Failed }

enum class PairFailure { Expired, Unknown, Limited, Broken, Both, OverCap, Offline }

/** Pairing of protocol.md §2: this device either shows a code (D) or enters one (E). Port of useSyncPairing.ts. */
class SyncPairingViewModel : ViewModel() {
    val phase = MutableStateFlow(PairPhase.Idle)
    val failure = MutableStateFlow<PairFailure?>(null)
    val overCap = MutableStateFlow<List<SyncLocal.OverCap>>(emptyList())
    val code = MutableStateFlow("")
    val url = MutableStateFlow("")
    val check = MutableStateFlow("")
    val peer = MutableStateFlow<SyncMessages.Hello?>(null)

    private class Pairing(
        val role: Char,
        val slot: SyncApi.Slot,
        val keys: SyncCrypto.KeyPair,
        val k: ByteArray?,
        val commit: ByteArray? = null,
    ) {
        var t: ByteArray? = null
        var read = 0
        var mine: SyncMessages.Hello? = null
        var giving = false
        var confirmed = false
        var peerReady = false
        var sentSecret = false
    }

    private var pairing: Pairing? = null
    private var job: Job? = null
    private val sync get() = Services.sync

    private fun reset() {
        job?.cancel()
        job = null
        pairing = null
        failure.value = null
        peer.value = null
        code.value = ""
        url.value = ""
        check.value = ""
    }

    private fun fail(why: PairFailure) {
        pairing?.let { p -> Services.scope.launch { SyncApi.closeSlot(p.slot) } }
        pairing = null
        failure.value = why
        phase.value = PairPhase.Failed
    }

    private fun failFrom(e: Exception) = fail(
        when {
            e is SyncHttpException && e.status == 404 -> PairFailure.Unknown
            e is SyncHttpException && e.status == 429 -> PairFailure.Limited
            e is SyncHttpException -> PairFailure.Broken
            e is IOException -> PairFailure.Offline
            else -> PairFailure.Broken
        },
    )

    private fun precheck(): Boolean {
        overCap.value = SyncLocal.overCaps()
        if (overCap.value.isNotEmpty()) fail(PairFailure.OverCap)
        return overCap.value.isEmpty()
    }

    fun show() {
        reset()
        if (!precheck()) return
        phase.value = PairPhase.Starting
        job = viewModelScope.launch {
            try {
                val keys = SyncCrypto.newKeyPair()
                val k = SyncCrypto.random(16)
                val slot = SyncApi.openSlot(SyncCrypto.toB64(SyncCrypto.sha256(keys.spki)))
                pairing = Pairing('D', slot, keys, k)
                code.value = slot.code
                url.value = "${BuildConfig.SITE_ORIGIN}/settings?pair=${slot.code}#k=${SyncCrypto.toB64(k)}"
                phase.value = PairPhase.Showing
                pollLoop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failFrom(e)
            }
        }
    }

    fun enter(entered: String, key: String? = null) {
        reset()
        if (!precheck()) return
        val k = key?.let { SyncCrypto.fromB64(it, 16) }
        phase.value = PairPhase.Connecting
        job = viewModelScope.launch {
            try {
                val keys = SyncCrypto.newKeyPair()
                val (commit, secret) = SyncApi.claimSlot(entered, SyncCrypto.toB64(keys.spki), k != null)
                val commitBytes = SyncCrypto.fromB64(commit, 32) ?: return@launch fail(PairFailure.Broken)
                pairing = Pairing('E', SyncApi.Slot(entered, secret), keys, k, commitBytes)
                code.value = entered
                pollLoop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failFrom(e)
            }
        }
    }

    private suspend fun pollLoop() {
        while (true) {
            val p = pairing ?: return
            if (!poll(p) || pairing !== p || phase.value == PairPhase.Done) return
            delay(POLL_MS)
        }
    }

    /** One look at the slot; false once pairing ended. */
    private suspend fun poll(p: Pairing): Boolean {
        val read = try {
            SyncApi.readSlot(p.slot)
        } catch (e: SyncHttpException) {
            if (e.status != 404) throw e
            if (p.sentSecret) finish() else fail(if (p.t != null) PairFailure.Broken else PairFailure.Expired)
            return false
        } catch (_: IOException) {
            return true
        }
        if (p.t == null && read.pub != null) {
            val other = SyncCrypto.fromB64(read.pub, SyncCrypto.SPKI_SIZE)?.takeIf { SyncCrypto.publicKey(it) != null }
                ?: return false.also { fail(PairFailure.Broken) }
            if (p.role == 'D') SyncApi.revealKey(p.slot, SyncCrypto.toB64(p.keys.spki))
            else if (!SyncCrypto.sha256(other).contentEquals(p.commit)) return false.also { fail(PairFailure.Broken) }
            openChannel(p, other, if (p.role == 'D') read.scanned else p.k != null)
        }
        val t = p.t ?: return true
        while (p.read < read.messages.size) {
            val sealed = SyncCrypto.fromB64(read.messages[p.read++])
            val plain = sealed?.let { SyncCrypto.open(t, it, SyncCrypto.pairAAD(p.slot.code, if (p.role == 'D') 'E' else 'D')) }
            val message = plain?.let(SyncCrypto::unpad)?.let { SyncMessages.read(String(it, Charsets.UTF_8)) }
                ?: return false.also { fail(PairFailure.Broken) }
            handle(p, message)
            if (pairing !== p) return false
        }
        return true
    }

    private suspend fun send(p: Pairing, message: SyncMessages.Message) {
        val plain = SyncCrypto.pad(SyncMessages.write(message).toByteArray(Charsets.UTF_8), SyncCrypto.PAIR_PADDED)
        SyncApi.sendMessage(p.slot, SyncCrypto.toB64(SyncCrypto.seal(p.t!!, plain, SyncCrypto.pairAAD(p.slot.code, p.role))))
    }

    private suspend fun openChannel(p: Pairing, other: ByteArray, scanned: Boolean) {
        val (pe, pd) = if (p.role == 'D') other to p.keys.spki else p.keys.spki to other
        val t = SyncCrypto.pairKey(SyncCrypto.ecdh(p.keys.private, other), if (scanned) p.k else null, p.slot.code, pe, pd)
        p.t = t
        check.value = SyncCrypto.checkNumber(t)
        val member = sync.engine.membership()
        val mine = SyncMessages.Hello("android", SyncLocal.deviceLabel(), member != null, if (member != null) maxOf(sync.devices.value.size, 1) else 0)
        p.mine = mine
        phase.value = PairPhase.Connecting
        send(p, mine)
    }

    private suspend fun handle(p: Pairing, message: SyncMessages.Message) {
        when (message) {
            is SyncMessages.Hello -> {
                if (peer.value != null) return fail(PairFailure.Broken)
                peer.value = message
                val mine = p.mine!!
                if (mine.syncs && message.syncs) return fail(PairFailure.Both)
                p.giving = mine.syncs || (!message.syncs && p.role == 'E')
                phase.value = PairPhase.Confirm
            }
            SyncMessages.Ready -> {
                if (!p.giving || p.peerReady) return fail(PairFailure.Broken)
                p.peerReady = true
                give(p)
            }
            is SyncMessages.Secret -> {
                if (p.giving || !p.confirmed) return fail(PairFailure.Broken)
                sync.engine.joinChain(message.chain, message.s)
                Services.scope.launch { SyncApi.closeSlot(p.slot) }
                sync.joined()
                finish()
            }
        }
    }

    private suspend fun give(p: Pairing) {
        if (!p.giving || !p.confirmed || !p.peerReady || p.sentSecret) return
        p.sentSecret = true
        val member = sync.engine.membership()
        val secret = if (member != null) SyncMessages.Secret(member.chain, member.epoch, SyncCrypto.toB64(member.s))
        else sync.engine.createChain().also { sync.joined() }
        send(p, secret)
    }

    fun confirm() {
        val p = pairing ?: return
        if (phase.value != PairPhase.Confirm) return
        p.confirmed = true
        phase.value = PairPhase.Waiting
        viewModelScope.launch {
            try {
                if (p.giving) give(p) else send(p, SyncMessages.Ready)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failFrom(e)
            }
        }
    }

    private fun finish() {
        pairing = null
        phase.value = PairPhase.Done
        Services.scope.launch { sync.syncNow() }
    }

    fun cancel() {
        pairing?.let { p -> Services.scope.launch { SyncApi.closeSlot(p.slot) } }
        reset()
        phase.value = PairPhase.Idle
    }

    override fun onCleared() {
        pairing?.let { p -> Services.scope.launch { SyncApi.closeSlot(p.slot) } }
        job?.cancel()
    }
}
