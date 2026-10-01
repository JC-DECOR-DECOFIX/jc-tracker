package br.com.jcdecor.tracker.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.jcdecor.tracker.TrackerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Depois do reboot a sessão fica marcada como interrompida.
 * O GPS não é religado aqui.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as TrackerApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.graph.repository.markInterruptedFromBoot()
            } finally {
                pending.finish()
            }
        }
    }
}
