package online.bus.bmarian.conexiuniclujmobile.follow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64

class DebugWearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val path = intent.getStringExtra("path") ?: return
        val data = intent.getStringExtra("data")?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return
        WearLink.relayedFromWatch()
        WearLink.onMessage(context.applicationContext, path, data)
    }
}
