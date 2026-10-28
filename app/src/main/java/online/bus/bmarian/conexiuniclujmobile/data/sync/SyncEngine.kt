package online.bus.bmarian.conexiuniclujmobile.data.sync

import kotlinx.serialization.json.JsonObject
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncRecords.Read
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncVault.Base
import online.bus.bmarian.conexiuniclujmobile.data.sync.SyncVault.Membership

/** One sync run and the chain-wide steps (protocol.md §3, §5–§7). Port of utils/sync/engine.ts. */
class SyncEngine(private val vault: SyncVault) {
    enum class Problem { Broken, Update, OverCap, Full }

    class DeviceView(val id: String, val kind: String, val label: String, val seen: Long, val self: Boolean)

    class Report(val devices: List<DeviceView>, val dropped: Int, val problems: Set<Problem>)

    /** This device is out of its chain: removed, or sync was deleted. */
    class Gone : Exception()

    private class EpochChanged : Exception()

    private interface Kind<T> {
        fun read(raw: String): Read<T>
        fun write(value: T): String
        fun local(base: T?): T?
        fun merge(base: T?, local: T, remote: T): T
        fun apply(value: T)
        fun empty(value: T): Boolean = false
    }

    private class Run(val m: Membership, val keys: SyncCrypto.ChainKeys, val bases: MutableMap<String, Base>) {
        val problems = mutableSetOf<Problem>()
        var dropped = 0
        var devices = emptyList<SyncRecords.Device>()
    }

    private fun cityKind(slug: String, run: Run) = object : Kind<SyncRecords.City> {
        override fun read(raw: String) = SyncRecords.readCity(raw)
        override fun write(value: SyncRecords.City) = SyncRecords.writeCity(value)
        override fun local(base: SyncRecords.City?): SyncRecords.City? {
            val local = SyncLocal.cityFromLocal(slug)
            if (listOf(local.stops.size, local.lines.size, local.places.size, local.followed.size).any { it > SyncRecords.CITY_CAP }) {
                run.problems += Problem.OverCap
                return null
            }
            return local
        }
        override fun merge(base: SyncRecords.City?, local: SyncRecords.City, remote: SyncRecords.City): SyncRecords.City {
            val result = SyncMerge.mergeCity(base ?: SyncRecords.emptyCity, local, remote)
            run.dropped += result.dropped
            return result.merged
        }
        override fun apply(value: SyncRecords.City) = SyncLocal.applyCity(slug, value)
        override fun empty(value: SyncRecords.City) = value.isEmpty
    }

    private fun newsKind(slug: String) = object : Kind<SyncRecords.News> {
        override fun read(raw: String) = SyncRecords.readNews(raw)
        override fun write(value: SyncRecords.News) = SyncRecords.writeNews(value)
        override fun local(base: SyncRecords.News?) = SyncLocal.newsFromLocal(slug)
        override fun merge(base: SyncRecords.News?, local: SyncRecords.News, remote: SyncRecords.News) =
            SyncMerge.mergeNews(base ?: SyncRecords.emptyNews, local, remote)
        override fun apply(value: SyncRecords.News) = SyncLocal.applyNews(slug, value)
        override fun empty(value: SyncRecords.News) = value.seen.isEmpty()
    }

    private val settingsKind = object : Kind<SyncRecords.Settings> {
        override fun read(raw: String) = SyncRecords.readSettings(raw)
        override fun write(value: SyncRecords.Settings) = SyncRecords.writeSettings(value)
        override fun local(base: SyncRecords.Settings?) = SyncLocal.settingsFromLocal()
        override fun merge(base: SyncRecords.Settings?, local: SyncRecords.Settings, remote: SyncRecords.Settings) =
            SyncMerge.mergeSettings(base, local, remote)
        override fun apply(value: SyncRecords.Settings) = SyncLocal.applySettings(value)
    }

    private fun devicesKind(run: Run, label: String) = object : Kind<SyncRecords.Devices> {
        override fun read(raw: String) = SyncRecords.readDevices(raw)
        override fun write(value: SyncRecords.Devices) = SyncRecords.writeDevices(value)
        override fun local(base: SyncRecords.Devices?) =
            SyncRecords.Devices(base?.devices.orEmpty().filter { it.id != run.m.device } + SyncRecords.Device(run.m.device, KIND, label, run.m.devicePub))
        override fun merge(base: SyncRecords.Devices?, local: SyncRecords.Devices, remote: SyncRecords.Devices) =
            SyncRecords.Devices(SyncMerge.mergeDevices(base?.devices.orEmpty(), local.devices, remote.devices))
        override fun apply(value: SyncRecords.Devices) {
            run.devices = value.devices
        }
    }

    private suspend fun fetchRecord(run: Run, name: String): Pair<Long, String>? {
        val (rev, body) = SyncApi.readRecord(run.m.member, name)
        val sealed = SyncCrypto.fromB64(body) ?: return null
        val plain = SyncCrypto.open(run.keys.data, sealed, SyncCrypto.recordAAD(run.m.chain, run.m.epoch, name, rev)) ?: return null
        return SyncCrypto.unpad(plain)?.let { rev to String(it, Charsets.UTF_8) }
    }

    private fun sealRecord(keys: SyncCrypto.ChainKeys, chain: String, epoch: Long, name: String, rev: Long, text: String): String {
        val plain = text.toByteArray(Charsets.UTF_8)
        val padded = SyncCrypto.pad(plain, SyncCrypto.bucketFor(plain.size))
        return SyncCrypto.toB64(SyncCrypto.seal(keys.data, padded, SyncCrypto.recordAAD(chain, epoch, name, rev)))
    }

    private suspend fun <T> syncRecord(run: Run, kind: Kind<T>, logical: String, name: String, remoteRev: Long) {
        val m = run.m
        var rev = remoteRev
        repeat(3) {
            val base = run.bases[logical]
            val sameEpoch = base?.epoch == m.epoch
            if (sameEpoch && rev < base!!.rev) {
                run.problems += Problem.Broken
                return
            }
            var remoteText: String? = null
            if (rev > 0) {
                if (sameEpoch && base!!.rev == rev) remoteText = base.text
                else {
                    val fetched = fetchRecord(run, name)
                    if (fetched == null) {
                        run.problems += Problem.Broken
                        return
                    }
                    rev = fetched.first
                    remoteText = fetched.second
                }
            }
            val remote = remoteText?.let(kind::read)
            if (remote != null && remote !is Read.Ok) {
                run.problems += if (remote == Read.Newer) Problem.Update else Problem.Broken
                return
            }
            val baseValue = base?.let { (kind.read(it.text) as? Read.Ok)?.value }
            val local = kind.local(if (remote != null) baseValue else null) ?: return
            val merged = if (remote is Read.Ok) kind.merge(baseValue, local, remote.value) else local
            val mergedText = kind.write(merged)
            if (mergedText != kind.write(local) || logical == DEVICES) kind.apply(merged)
            if (mergedText == remoteText) {
                run.bases[logical] = Base(mergedText, rev, m.epoch)
                return
            }
            if (remote == null && kind.empty(merged)) return
            try {
                val written = SyncApi.writeRecord(m.member, name, m.epoch, rev, sealRecord(run.keys, m.chain, m.epoch, name, rev + 1, mergedText))
                run.bases[logical] = Base(mergedText, written, m.epoch)
                return
            } catch (e: SyncHttpException) {
                if (e.status != 409) throw e
                if (e.error == "full") {
                    run.problems += Problem.Full
                    return
                }
                if (e.number("epoch") != m.epoch) throw EpochChanged()
                rev = e.number("rev") ?: throw e
            }
        }
    }

    private suspend fun state(m: Membership): SyncApi.ChainState = try {
        SyncApi.chainState(m.member)
    } catch (e: SyncHttpException) {
        if (e.status == 401) {
            vault.forget()
            throw Gone()
        }
        throw e
    }

    /** After another device removed someone: this device's new secret waits for it, sealed to its own key. */
    private suspend fun catchUp(m: Membership, epoch: Long): Membership {
        val (wrapEpoch, body) = SyncApi.readWrap(m.member)
        val s = SyncCrypto.fromB64(body)?.takeIf { wrapEpoch == epoch }
            ?.let { SyncCrypto.unwrapSecret(m.deviceKey, it, SyncCrypto.wrapInfo(m.chain, epoch, m.device)) }
            ?: throw conflict()
        val next = Membership(m.chain, epoch, m.device, m.token, s, m.deviceKey, m.devicePub)
        vault.save(next)
        return next
    }

    fun membership() = vault.membership()

    private fun conflict() = SyncHttpException(409, JsonObject(emptyMap()))

    suspend fun syncOnce(withSettings: Boolean, label: String): Report {
        try {
            return syncPass(withSettings, label)
        } catch (_: EpochChanged) {
            return syncPass(withSettings, label)
        }
    }

    private suspend fun syncPass(withSettings: Boolean, label: String): Report {
        var m = vault.membership() ?: throw Gone()
        var st = state(m)
        if (st.epoch != m.epoch) {
            m = catchUp(m, st.epoch)
            st = state(m)
        }
        val run = Run(m, SyncCrypto.chainKeys(m.s), vault.bases())
        val revs = st.records.associate { it.name to it.rev }
        suspend fun <T> step(logical: String, kind: Kind<T>) {
            val name = SyncCrypto.recordName(run.keys.names, logical)
            syncRecord(run, kind, logical, name, revs[name] ?: 0)
        }
        try {
            step(DEVICES, devicesKind(run, label))
            if (withSettings) step(SETTINGS, settingsKind)
            for (slug in SyncLocal.syncedCities()) {
                step("city:$slug", cityKind(slug, run))
                step("news:$slug", newsKind(slug))
            }
        } finally {
            vault.saveBases(run.bases)
        }
        val labels = run.devices.associate { it.id to it.label }
        return Report(st.devices.map { DeviceView(it.id, it.kind, labels[it.id].orEmpty(), it.seen, it.id == m.device) }, run.dropped, run.problems)
    }

    private suspend fun register(s: ByteArray, send: suspend (String) -> SyncApi.Registered, chain: String?) {
        val keys = SyncCrypto.chainKeys(s)
        val device = SyncCrypto.newKeyPair()
        val registered = send(SyncCrypto.toB64(keys.join))
        vault.forget()
        vault.save(
            Membership(
                registered.chain ?: chain!!, registered.epoch, registered.device, registered.token,
                s, device.private, SyncCrypto.toB64(device.spki),
            ),
        )
    }

    /** Starts a chain with this device alone in it; the secret it returns goes to the other device. */
    suspend fun createChain(): SyncMessages.Secret {
        val s = SyncCrypto.random(SyncCrypto.KEY_SIZE)
        register(s, { SyncApi.createChain(it, KIND) }, null)
        val m = vault.membership()!!
        return SyncMessages.Secret(m.chain, m.epoch, SyncCrypto.toB64(s))
    }

    suspend fun joinChain(chain: String, s: String) {
        val secret = SyncCrypto.fromB64(s, SyncCrypto.KEY_SIZE) ?: throw IllegalArgumentException("secret")
        register(secret, { SyncApi.joinChain(chain, it, KIND) }, chain)
    }

    /** Removes another device for good: every record moves to a new secret the removed device never gets (§7). */
    suspend fun removeDevice(id: String, withSettings: Boolean, label: String) {
        syncOnce(withSettings, label)
        val m = vault.membership() ?: throw Gone()
        val st = state(m)
        val keys = SyncCrypto.chainKeys(m.s)
        val logicals = listOf(DEVICES, SETTINGS) + SyncLocal.syncedCities().flatMap { listOf("city:$it", "news:$it") }
        val byName = logicals.associateBy { SyncCrypto.recordName(keys.names, it) }
        val run = Run(m, keys, mutableMapOf())
        val texts = linkedMapOf<String, String>()
        for (record in st.records) {
            val logical = byName[record.name] ?: continue
            val fetched = fetchRecord(run, record.name)
            if (fetched == null || fetched.first != record.rev) throw conflict()
            texts[logical] = fetched.second
        }
        val entries = (texts[DEVICES]?.let(SyncRecords::readDevices) as? Read.Ok)?.value?.devices
            ?: throw conflict()
        texts[DEVICES] = SyncRecords.writeDevices(SyncRecords.Devices(entries.filter { it.id != id }))
        val remaining = st.devices.filter { it.id != id && it.id != m.device }
        val pubs = remaining.map { d -> entries.firstOrNull { it.id == d.id }?.pub ?: throw conflict() }

        val s = SyncCrypto.random(SyncCrypto.KEY_SIZE)
        val next = SyncCrypto.chainKeys(s)
        val epoch = st.epoch + 1
        val bases = vault.bases()
        val records = texts.map { (logical, text) ->
            val name = SyncCrypto.recordName(next.names, logical)
            bases[logical] = Base(text, 1, epoch)
            name to sealRecord(next, m.chain, epoch, name, 1, text)
        }
        val wraps = remaining.mapIndexed { i, d ->
            d.id to SyncCrypto.toB64(SyncCrypto.wrapSecret(SyncCrypto.fromB64(pubs[i])!!, s, SyncCrypto.wrapInfo(m.chain, epoch, d.id)))
        }
        SyncApi.rotate(m.member, st.epoch, st.records, SyncCrypto.toB64(next.join), listOf(id), records, wraps)
        vault.save(Membership(m.chain, epoch, m.device, m.token, s, m.deviceKey, m.devicePub))
        vault.saveBases(bases)
    }

    /** Leaves the chain; the data on this device stays as it is. */
    suspend fun leave() {
        val m = vault.membership() ?: return
        try {
            val keys = SyncCrypto.chainKeys(m.s)
            val name = SyncCrypto.recordName(keys.names, DEVICES)
            val run = Run(m, keys, mutableMapOf())
            val fetched = runCatching { fetchRecord(run, name) }.getOrNull()
            val read = fetched?.second?.let(SyncRecords::readDevices) as? Read.Ok
            if (fetched != null && read != null) {
                val text = SyncRecords.writeDevices(SyncRecords.Devices(read.value.devices.filter { it.id != m.device }))
                SyncApi.writeRecord(m.member, name, m.epoch, fetched.first, sealRecord(keys, m.chain, m.epoch, name, fetched.first + 1, text))
            }
            SyncApi.leave(m.member)
        } catch (e: SyncHttpException) {
            if (e.status != 401 && e.status != 404) throw e
        }
        vault.forget()
    }

    suspend fun deleteEverything() {
        val m = vault.membership() ?: return
        try {
            SyncApi.deleteChain(m.member)
        } catch (e: SyncHttpException) {
            if (e.status != 401) throw e
        }
        vault.forget()
    }

    private companion object {
        const val KIND = "android"
        const val DEVICES = "devices"
        const val SETTINGS = "settings"
    }
}
