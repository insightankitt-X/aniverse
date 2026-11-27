package app.aniverse.local.data.extensions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
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

        if (statusCode == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // A regular app is not a privileged installer, so Android hands the confirmation back
            // to us instead of installing silently. Launch it and keep waiting for the real result
            // that arrives after the user has confirmed or cancelled.
            val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
            if (confirmation == null) {
                registry.complete(
                    sessionId,
                    InstallSignal(
                        PackageInstaller.STATUS_FAILURE,
                        "The system did not provide an install confirmation dialog",
                    ),
                )
                return
            }
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching { context.startActivity(confirmation) }.isSuccess
            if (!launched) {
                registry.complete(
                    sessionId,
                    InstallSignal(
                        PackageInstaller.STATUS_FAILURE,
                        "The install dialog could not be opened. Allow AniVerse to install unknown apps and try again.",
                    ),
                )
            }
            return
        }

        registry.complete(sessionId, InstallSignal(statusCode, message))
    }

    companion object {
        const val EXTRA_SESSION_ID = "app.aniverse.local.extra.SESSION_ID"
        const val EXTRA_PKG = "app.aniverse.local.extra.PKG"
    }
}
