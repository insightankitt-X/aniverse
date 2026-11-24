package app.anilab.local.ui.screens.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.anilab.local.ui.components.Badge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenRepositories: () -> Unit,
    onOpenSettings: () -> Unit,
    extensionUpdates: Int,
    downloadCount: Int,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("More") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            MoreRow("History", "Everything you have watched", Icons.Default.History, null, onOpenHistory)
            MoreRow("Downloads", "Episodes saved on this device", Icons.Default.Download, downloadCount.takeIf { it > 0 }?.toString(), onOpenDownloads)
            HorizontalDivider()
            MoreRow("Extensions", "Install and manage sources", Icons.Default.Extension, extensionUpdates.takeIf { it > 0 }?.toString(), onOpenExtensions)
            MoreRow("Repositories", "Extension index URLs you added", Icons.Default.Source, null, onOpenRepositories)
            HorizontalDivider()
            MoreRow("Settings", "Appearance, playback, downloads and backup", Icons.Default.Settings, null, onOpenSettings)
            HorizontalDivider()
            Column(Modifier.padding(16.dp)) {
                Text("AniLab Local", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(2.dp))
                Text(
                    "No account. No backend. No bundled sources. Content only comes from extensions you install, " +
                        "and all data stays on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MoreRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (badge != null) Badge(badge)
    }
}
