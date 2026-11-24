package app.aniverse.local.data.extensions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Receives the system's install-session verdict. Not exported; only the system talks to it. */
@AndroidEntryPoint
class InstallResultReceiver : BroadcastReceiver() {
    @Inject
    lateinit var registry: InstallSessionRegistry

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        if (sessionId < 0) return
        val statusCode = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        registry.complete(sessionId, InstallSignal(statusCode, message))
    }

    companion object {
        const val EXTRA_SESSION_ID = "app.aniverse.local.extra.SESSION_ID"
        const val EXTRA_PKG = "app.aniverse.local.extra.PKG"
    }
}
