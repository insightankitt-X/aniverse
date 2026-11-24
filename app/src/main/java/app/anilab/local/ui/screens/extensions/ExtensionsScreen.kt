package app.anilab.local.ui.screens.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.anilab.local.data.source.SourceRegistry
import app.anilab.local.domain.model.InstalledExtension
import app.anilab.local.domain.model.RepoExtension
import app.anilab.local.domain.model.RepoExtensions
import app.anilab.local.domain.model.SourceRef
import app.anilab.local.domain.repository.AddRepoResult
import app.anilab.local.domain.repository.ConfirmationReason
import app.anilab.local.domain.repository.ExtensionRepository
import app.anilab.local.domain.repository.InstallFailure
import app.anilab.local.domain.repository.InstallOutcome
import app.anilab.local.domain.repository.RepoRepository
import app.anilab.local.domain.repository.UpdateFailureItem
import app.anilab.local.domain.repository.UpdateProgress
import app.anilab.local.ui.components.AddRepositoryDialog
import app.anilab.local.ui.components.Badge
import app.anilab.local.ui.components.EmptyState
import app.anilab.local.ui.components.LoadingState
import app.anilab.local.ui.components.SectionHeader
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

fun installFailureText(failure: InstallFailure, message: String): String = when (failure) {
    InstallFailure.CANCELLED -> "Install cancelled"
    InstallFailure.SIGNATURE_MISMATCH -> "Signature mismatch: $message"
    InstallFailure.DOWNGRADE -> "Downgrade blocked: $message"
    InstallFailure.CORRUPT_APK -> "The downloaded APK is corrupt"
    InstallFailure.PACKAGE_MISMATCH -> "The APK does not match the index"
    InstallFailure.DOWNLOAD_FAILED -> "The download failed"
    InstallFailure.INCOMPATIBLE_API -> message
    InstallFailure.INSTALLER_BLOCKED -> "Android blocked the install. Allow unknown apps for AniLab Local."
    InstallFailure.NOT_FOUND -> message
    InstallFailure.UNKNOWN -> message
}

@HiltViewModel
class ExtensionsViewModel @Inject constructor(
    private val extensionRepository: ExtensionRepository,
    private val repoRepository: RepoRepository,
    private val registry: SourceRegistry,
) : ViewModel() {

    data class PendingConfirmation(
        val pkg: String,
        val repoId: Long?,
        val reasons: List<ConfirmationReason>,
        val permissions: List<String>,
        val isUpdate: Boolean,
    )

    data class State(
        val loaded: Boolean = false,
        val installed: List<InstalledExtension> = emptyList(),
        val available: List<RepoExtensions> = emptyList(),
        val updateCount: Int = 0,
        val busyPkg: String? = null,
        val progress: Float = 0f,
        val pending: PendingConfirmation? = null,
        val message: String? = null,
        val updateProgress: UpdateProgress? = null,
        val updateReport: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            extensionRepository.observeInstalled().collect { installed ->
                _state.update { it.copy(installed = installed, loaded = true) }
            }
        }
        viewModelScope.launch {
            extensionRepository.observeAvailable().collect { available ->
                _state.update { it.copy(available = available) }
            }
        }
        viewModelScope.launch {
            extensionRepository.observeUpdateCount().collect { count ->
                _state.update { it.copy(updateCount = count) }
            }
        }
    }

    fun install(pkg: String, repoId: Long?) {
        if (_state.value.busyPkg != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyPkg = pkg, progress = 0f) }
            val outcome = extensionRepository.install(pkg, repoId, confirmed = false) { fraction ->
                _state.update { it.copy(progress = fraction) }
            }
            handle(outcome, repoId, isUpdate = false)
        }
    }

    fun update(pkg: String) {
        if (_state.value.busyPkg != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyPkg = pkg, progress = 0f) }
            val outcome = extensionRepository.update(pkg, confirmed = false) { fraction ->
                _state.update { it.copy(progress = fraction) }
            }
            handle(outcome, null, isUpdate = true)
        }
    }

    private fun handle(outcome: InstallOutcome, repoId: Long?, isUpdate: Boolean) {
        when (outcome) {
            is InstallOutcome.Installed -> _state.update { it.copy(busyPkg = null, progress = 0f, message = "Installed") }
            is InstallOutcome.Cancelled -> _state.update { it.copy(busyPkg = null, progress = 0f, message = "Install cancelled") }
            is InstallOutcome.NeedsConfirmation -> _state.update {
                it.copy(busyPkg = null, progress = 0f, pending = PendingConfirmation(outcome.pkg, repoId, outcome.reasons, outcome.permissions, isUpdate))
            }
            is InstallOutcome.Failed -> _state.update {
                it.copy(busyPkg = null, progress = 0f, message = installFailureText(outcome.reason, outcome.message))
            }
        }
    }

    fun confirmPending(confirmed: Boolean) {
        val pending = _state.value.pending ?: return
        _state.update { it.copy(pending = null) }
        if (!confirmed) return
        viewModelScope.launch {
            _state.update { it.copy(busyPkg = pending.pkg, progress = 0f) }
            val outcome = if (pending.isUpdate) {
                extensionRepository.update(pending.pkg, confirmed = true) { fraction -> _state.update { it.copy(progress = fraction) } }
            } else {
                extensionRepository.install(pending.pkg, pending.repoId, confirmed = true) { fraction -> _state.update { it.copy(progress = fraction) } }
            }
            handle(outcome, pending.repoId, pending.isUpdate)
        }
    }

    fun cancelInstall() {
        viewModelScope.launch { extensionRepository.cancelInstall() }
    }

    fun updateAll() {
        if (_state.value.busyPkg != null) return
        viewModelScope.launch {
            val report = extensionRepository.updateAll { progress ->
                _state.update { it.copy(updateProgress = progress, busyPkg = progress.pkg.ifBlank { null }, progress = progress.fraction) }
            }
            val failures = report.failed + report.skipped
            _state.update {
                it.copy(
                    busyPkg = null,
                    updateProgress = null,
                    updateReport = buildString {
                        append("Updated ${report.updated.size}. ")
                        if (failures.isNotEmpty()) {
                            append(failures.size)
                            append(" needed attention: ")
                            append(failures.take(3).joinToString("; ") { failure -> "${failure.name}: ${failure.message}" })
                        }
                    },
                )
            }
        }
    }

    fun setEnabled(pkg: String, enabled: Boolean) = viewModelScope.launch { extensionRepository.setEnabled(pkg, enabled) }

    fun setNsfw(pkg: String, nsfw: Boolean) = viewModelScope.launch { extensionRepository.setNsfw(pkg, nsfw) }

    fun uninstall(pkg: String) = viewModelScope.launch {
        extensionRepository.uninstall(pkg)
        _state.update { it.copy(message = "Removed from this app") }
    }

    fun open(pkg: String, onOpen: (SourceRef) -> Unit) {
        viewModelScope.launch {
            val handle = runCatching { registry.enabled().firstOrNull { it.ref.pkg == pkg } }.getOrNull()
            if (handle == null) {
                _state.update { it.copy(message = "No sources from this extension are available") }
            } else {
                onOpen(handle.ref)
            }
        }
    }

    suspend fun addRepository(url: String): AddRepoResult = repoRepository.addRepo(url)

    fun confirmTrust(repoId: Long, trusted: Boolean) = viewModelScope.launch { repoRepository.setTrusted(repoId, trusted) }

    fun clearMessage() = _state.update { it.copy(message = null, updateReport = null) }

    fun dismissUpdateReport() = _state.update { it.copy(updateReport = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    onOpenRepositories: () -> Unit,
    onOpenSource: (SourceRef) -> Unit,
    onBack: () -> Unit,
    viewModel: ExtensionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf(false) }
    var justAdded by remember { mutableStateOf<Long?>(null) }
    var trustPrompt by remember { mutableStateOf<Pair<Long, String>?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    TextButton(onClick = onOpenRepositories) { Text("Repositories") }
                    IconButton(onClick = { adding = true }) { Icon(Icons.Default.Add, contentDescription = "Add repository") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            !state.loaded -> LoadingState(Modifier.padding(padding))
            state.installed.isEmpty() && state.available.isEmpty() -> EmptyState(
                title = "No extensions yet",
                message = "Add an extension repository by pasting its index URL, then install a source.",
                actionLabel = "Add repository",
                onAction = { adding = true },
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item {
                    SectionHeader(
                        title = "Installed (${state.installed.size})",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(state.installed, key = { it.pkg }) { extension ->
                    InstalledRow(
                        extension = extension,
                        busy = state.busyPkg == extension.pkg,
                        progress = state.progress,
                        onEnabled = { enabled -> viewModel.setEnabled(extension.pkg, enabled) },
                        onNsfw = { nsfw -> viewModel.setNsfw(extension.pkg, nsfw) },
                        onUpdate = { viewModel.update(extension.pkg) },
                        onOpen = { viewModel.open(extension.pkg, onOpenSource) },
                        onUninstall = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:${extension.pkg}"))
                            runCatching { context.startActivity(intent) }
                            viewModel.uninstall(extension.pkg)
                        },
                    )
                }
                state.available.forEach { repo ->
                    item(key = "repo-${repo.repoId}") {
                        SectionHeader(
                            title = repo.repoName,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            actionLabel = if (repo.updateCount > 0) "Update all (${repo.updateCount})" else null,
                            onAction = if (repo.updateCount > 0) ({ viewModel.updateAll() }) else null,
                        )
                        if (!repo.trusted) {
                            Text(
                                "Untrusted repository - installs need an extra confirmation and are never auto-updated.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                    items(repo.extensions, key = { "${repo.repoId}-${it.pkg}" }) { extension ->
                        AvailableRow(
                            extension = extension,
                            trusted = repo.trusted,
                            installedVersionCode = state.installed.firstOrNull { it.pkg == extension.pkg }?.versionCode ?: 0,
                            busy = state.busyPkg == extension.pkg,
                            progress = state.progress,
                            onInstall = { viewModel.install(extension.pkg, repo.repoId) },
                            onUpdate = { viewModel.update(extension.pkg) },
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        AddRepositoryDialog(
            onDismiss = { adding = false },
            onSubmit = { url -> viewModel.addRepository(url) },
            onAdded = { repo ->
                adding = false
                trustPrompt = repo.id to repo.name
            },
        )
    }

    trustPrompt?.let { (repoId, name) ->
        AlertDialog(
            onDismissRequest = { trustPrompt = null },
            title = { Text("Trust $name?") },
            text = { Text("Extensions are third-party code. They can fetch whatever they want from the internet while you use them. Only add repositories you trust.") },
            confirmButton = {
                Button(onClick = { viewModel.confirmTrust(repoId, true); trustPrompt = null }) { Text("I understand, trust it") }
            },
            dismissButton = { TextButton(onClick = { viewModel.confirmTrust(repoId, false); trustPrompt = null }) { Text("Keep untrusted") } },
        )
    }

    state.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.confirmPending(false) },
            title = { Text(if (pending.isUpdate) "Confirm update" else "Confirm install") },
            text = {
                Column {
                    if (pending.reasons.contains(ConfirmationReason.UNTRUSTED_REPOSITORY)) {
                        Text("This extension comes from an untrusted repository.")
                        Spacer(Modifier.height(8.dp))
                    }
                    if (pending.reasons.contains(ConfirmationReason.UNUSUAL_PERMISSIONS)) {
                        Text("It requests permissions extensions normally do not need:")
                        pending.permissions.forEach { permission ->
                            Text("\u2022 $permission", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { viewModel.confirmPending(true) }) { Text("Install anyway") } },
            dismissButton = { TextButton(onClick = { viewModel.confirmPending(false) }) { Text("Cancel") } },
        )
    }

    state.updateReport?.let { report ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdateReport,
            title = { Text("Updates finished") },
            text = { Text(report) },
            confirmButton = { Button(onClick = viewModel::dismissUpdateReport) { Text("OK") } },
        )
    }
}

@Composable
private fun ExtensionIcon(iconUrl: String?, name: String) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (iconUrl != null) {
            AsyncImage(model = iconUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun InstalledRow(
    extension: InstalledExtension,
    busy: Boolean,
    progress: Float,
    onEnabled: (Boolean) -> Unit,
    onNsfw: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExtensionIcon(extension.iconUrl, extension.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(extension.name, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("v${extension.versionName}", style = MaterialTheme.typography.labelSmall)
                    Text(extension.lang.uppercase(), style = MaterialTheme.typography.labelSmall)
                    Badge(if (extension.trusted) "Trusted" else "Untrusted")
                    if (extension.nsfw) Badge("NSFW", container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.onErrorContainer)
                }
                extension.disabledReason?.let { reason ->
                    Text(reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Switch(checked = extension.enabled, onCheckedChange = onEnabled)
        }
        if (busy) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("NSFW", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 4.dp))
            Switch(checked = extension.nsfw, onCheckedChange = onNsfw)
            Spacer(Modifier.weight(1f))
            if (extension.hasUpdate) {
                Button(onClick = onUpdate, enabled = !busy) { Text("Update") }
            }
            TextButton(onClick = onOpen) { Text("Open") }
            TextButton(onClick = onUninstall) { Text("Uninstall") }
        }
    }
}

@Composable
private fun AvailableRow(
    extension: RepoExtension,
    trusted: Boolean,
    installedVersionCode: Int,
    busy: Boolean,
    progress: Float,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
) {
    val installed = installedVersionCode > 0
    val updateAvailable = installed && extension.versionCode > installedVersionCode
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExtensionIcon(extension.iconUrl, extension.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(extension.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "v${extension.versionName} · ${extension.lang.uppercase()}" + if (!trusted) " · untrusted" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                extension.description?.let { description ->
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                updateAvailable -> Button(onClick = onUpdate) { Text("Update") }
                installed -> OutlinedButton(onClick = onInstall) { Text("Reinstall") }
                else -> Button(onClick = onInstall) { Text("Install") }
            }
        }
        if (busy) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}
