package app.anilab.local

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.anilab.local.ui.RootViewModel
import app.anilab.local.ui.nav.AniLabNav
import app.anilab.local.ui.theme.AniLabTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AniLabRoot() }
    }
}

@Composable
private fun AniLabRoot(viewModel: RootViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val databaseError by viewModel.databaseError.collectAsStateWithLifecycle()

    AniLabTheme(
        themeMode = settings.themeMode,
        pureBlack = settings.pureBlack,
        dynamicColor = settings.dynamicColor,
        reduceMotion = settings.reduceMotion,
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            if (databaseError != null) {
                DatabaseRecoveryScreen(
                    message = databaseError.orEmpty(),
                    onRetry = viewModel::probe,
                    onReset = viewModel::resetDatabase,
                )
            } else {
                AniLabNav()
            }
        }
    }
}

@Composable
private fun DatabaseRecoveryScreen(message: String, onRetry: () -> Unit, onReset: () -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Local data could not be opened", style = MaterialTheme.typography.titleLarge)
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Text(
                "Resetting removes your library, history and downloads list, but installed extensions stay installed. " +
                    "Export a backup first if the app can still open.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("Try again") }
            Button(onClick = onReset, modifier = Modifier.padding(top = 8.dp)) { Text("Reset local database") }
        }
    }
}
