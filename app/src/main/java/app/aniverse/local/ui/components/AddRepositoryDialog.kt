package app.aniverse.local.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.aniverse.local.domain.model.RepoSummary
import app.aniverse.local.domain.repository.AddRepoResult
import app.aniverse.local.domain.repository.RepoInputError
import kotlinx.coroutines.launch

/** Human wording for every repository validation failure. */
fun repoErrorText(error: RepoInputError): String = when (error) {
    RepoInputError.EMPTY -> "Enter a repository URL"
    RepoInputError.NOT_HTTPS -> "Only https:// repository URLs are supported"
    RepoInputError.UNREACHABLE -> "Couldn't reach that URL. Check the link and your connection."
    RepoInputError.NOT_AN_INDEX -> "That URL isn't a valid extension repository index"
    RepoInputError.ALREADY_ADDED -> "That repository is already added"
}

/**
 * The one dialog used from Settings, Extensions and the Extensions empty state. It never closes on
 * failure: errors are shown inline and the typed URL is kept so it can be corrected.
 */
@Composable
fun AddRepositoryDialog(
    onDismiss: () -> Unit,
    onSubmit: suspend (String) -> AddRepoResult,
    onAdded: (RepoSummary) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<RepoInputError?>(null) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    fun submit() {
        if (adding) return
        if (text.isBlank()) {
            error = RepoInputError.EMPTY
            return
        }
        adding = true
        error = null
        scope.launch {
            when (val result = onSubmit(text)) {
                is AddRepoResult.Added -> onAdded(result.repo)
                is AddRepoResult.Rejected -> error = result.error
            }
            adding = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!adding) onDismiss() },
        title = { Text("Add repository") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        if (error != null) error = null
                    },
                    label = { Text("Repository URL") },
                    placeholder = { Text("https://raw.githubusercontent.com/user/repo/repo/index.min.json") },
                    singleLine = true,
                    isError = error != null,
                    enabled = !adding,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                val pasted = clipboard.getText()?.text?.trim().orEmpty()
                                if (pasted.isNotEmpty()) {
                                    text = pasted
                                    error = null
                                }
                            },
                            enabled = !adding,
                        ) { Text("Paste") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = repoErrorText(error!!),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Paste the URL of an extension repository's index file or its folder. Nothing is uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { submit() }, enabled = text.isNotBlank() && !adding) {
                if (adding) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (adding) "Adding" else "Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !adding) { Text("Cancel") }
        },
    )
}
