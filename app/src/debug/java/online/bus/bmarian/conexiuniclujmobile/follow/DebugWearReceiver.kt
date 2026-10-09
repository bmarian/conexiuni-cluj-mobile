package online.bus.bmarian.conexiuniclujmobile.follow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64

/**
 * Debug builds only: takes a watch message that tools/wear-relay.py carried over from a Wear
 * emulator, for testing the two apps together without pairing (which needs a Google account).
 */
class DebugWearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val path = intent.getStringExtra("path") ?: return
        val data = intent.getStringExtra("data")?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return
        WearLink.relayedFromWatch()
        WearLink.onMessage(context.applicationContext, path, data)
    }
}
