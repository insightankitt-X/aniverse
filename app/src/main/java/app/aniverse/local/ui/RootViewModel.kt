package app.aniverse.local.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aniverse.local.data.db.AniVerseDatabase
import app.aniverse.local.domain.model.Settings
import app.aniverse.local.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val database: AniVerseDatabase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    /** Non-null when the local database could not be opened, which only happens after corruption. */
    private val _databaseError = MutableStateFlow<String?>(null)
    val databaseError = _databaseError.asStateFlow()

    init {
        probe()
    }

    fun probe() {
        viewModelScope.launch {
            val failure = withContext(Dispatchers.IO) {
                runCatching { database.openHelper.writableDatabase }.exceptionOrNull()?.let { error ->
                    error.message ?: error::class.java.simpleName
                }
            }
            _databaseError.value = failure
        }
    }

    /** Last resort: drops the local database, losing library and progress but not installed extensions. */
    fun resetDatabase() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val name = runCatching { database.openHelper.databaseName }.getOrNull()
                    runCatching { database.close() }
                    if (!name.isNullOrBlank()) context.deleteDatabase(name)
                }
            }
            probe()
        }
    }
}
