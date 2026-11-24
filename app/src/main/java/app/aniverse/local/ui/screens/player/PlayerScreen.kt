package app.aniverse.local.ui.screens.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.aniverse.local.domain.model.Episode
import app.aniverse.local.domain.model.PlaybackThresholds
import app.aniverse.local.domain.model.PlayerQualityMode
import app.aniverse.local.domain.model.Series
import app.aniverse.local.domain.model.Settings
import app.aniverse.local.domain.repository.DownloadRepository
import app.aniverse.local.domain.repository.LibraryRepository
import app.aniverse.local.domain.repository.PlayableSubtitle
import app.aniverse.local.domain.repository.PlayableVideo
import app.aniverse.local.domain.repository.PlayerSource
import app.aniverse.local.domain.repository.ProgressRepository
import app.aniverse.local.domain.repository.SettingsRepository
import app.aniverse.local.ui.components.ErrorState
import app.aniverse.local.ui.components.LoadingState
import app.aniverse.local.ui.components.formatDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** A ready-to-play selection: either a local file or one streamed video with headers. */
data class Playback(
    val url: String,
    val headers: Map<String, String>,
    val subtitles: List<PlayableSubtitle>,
    val quality: String,
    val local: Boolean,
) {
    val cleartext: Boolean get() = url.startsWith("http://")
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val source: PlayerSource,
    private val progressRepository: ProgressRepository,
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val series: Series? = null,
        val episode: Episode? = null,
        val queue: List<Episode> = emptyList(),
        val playback: Playback? = null,
        val videos: List<PlayableVideo> = emptyList(),
        val startPositionMs: Long = 0L,
        val settings: Settings = Settings(),
        val error: String? = null,
        val retryable: Boolean = false,
    ) {
        val next: Episode?
            get() {
                val index = queue.indexOfFirst { it.id == episode?.id }
                return if (index >= 0) queue.getOrNull(index + 1) else null
            }
    }

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private var episodeId: Long = 0L
    private var lastSavedMs: Long = 0L
    private var watchedRecorded = false

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings -> _state.update { it.copy(settings = settings) } }
        }
    }

    fun load(episodeId: Long) {
        this.episodeId = episodeId
        watchedRecorded = false
        lastSavedMs = 0L
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, playback = null) }
            val context = source.context(episodeId)
            if (context == null) {
                _state.update { it.copy(loading = false, error = "This episode is not stored on this device yet.", retryable = false) }
                return@launch
            }
            val localFile = runCatching { downloadRepository.localFile(episodeId) }.getOrNull()
            if (localFile != null) {
                _state.update {
                    it.copy(
                        loading = false,
                        series = context.series,
                        episode = context.episode,
                        queue = context.queue,
                        playback = Playback(localFile, emptyMap(), emptyList(), "Downloaded", local = true),
                        startPositionMs = context.episode.positionMs,
                        error = null,
                    )
                }
                return@launch
            }
            val result = source.videos(context)
            val videos = result.getOrElse { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        series = context.series,
                        episode = context.episode,
                        queue = context.queue,
                        error = error.message ?: "Couldn't resolve a video for this episode",
                        retryable = true,
                    )
                }
                return@launch
            }
            val chosen = chooseVideo(videos, _state.value.settings)
            if (chosen == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        series = context.series,
                        episode = context.episode,
                        queue = context.queue,
                        videos = videos,
                        error = "This source returned no playable video.",
                        retryable = true,
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    loading = false,
                    series = context.series,
                    episode = context.episode,
                    queue = context.queue,
                    videos = videos,
                    playback = Playback(chosen.url, chosen.headers, chosen.subtitles, chosen.quality, local = false),
                    startPositionMs = context.episode.positionMs,
                    error = null,
                )
            }
        }
    }

    fun retry() {
        if (episodeId != 0L) load(episodeId)
    }

    fun selectQuality(video: PlayableVideo) {
        _state.update { current ->
            val position = current.playback?.let { current.startPositionMs }
            current.copy(
                playback = Playback(video.url, video.headers, video.subtitles, video.quality, local = false),
                startPositionMs = current.startPositionMs,
            ).also { position }
        }
        viewModelScope.launch {
            settingsRepository.edit { settings ->
                settings.copy(playerQualityMode = PlayerQualityMode.MANUAL, playerPreferredQuality = video.quality)
            }
        }
    }

    fun onProgress(positionMs: Long, durationMs: Long, force: Boolean = false) {
        val current = _state.value
        val episode = current.episode ?: return
        if (durationMs <= 0L) return
        if (!force && positionMs - lastSavedMs < PlaybackThresholds.PROGRESS_SAVE_INTERVAL_MS) return
        lastSavedMs = positionMs
        viewModelScope.launch {
            progressRepository.save(episode.seriesId, episode.id, positionMs, durationMs)
            if (!watchedRecorded && PlaybackThresholds.isWatched(positionMs, durationMs)) {
                watchedRecorded = true
                libraryRepository.markWatched(episode.id, true)
            }
        }
    }

    fun onStopped(positionMs: Long, durationMs: Long) {
        onProgress(positionMs, durationMs, force = true)
    }

    fun setSpeed(speed: Float) {
        viewModelScope.launch { settingsRepository.edit { it.copy(playerSpeed = speed) } }
    }

    private fun chooseVideo(videos: List<PlayableVideo>, settings: Settings): PlayableVideo? {
        if (videos.isEmpty()) return null
        val preferred = settings.playerPreferredQuality
        if (settings.playerQualityMode == PlayerQualityMode.MANUAL && preferred != null) {
            videos.firstOrNull { it.quality.equals(preferred, ignoreCase = true) }?.let { return it }
        }
        return videos.maxByOrNull { video -> video.quality.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    episodeId: Long,
    onBack: () -> Unit,
    onPlayEpisode: (Long) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(episodeId) { viewModel.load(episodeId) }

    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val playback = state.playback
    var controlsVisible by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf<Int?>(null) }

    val exoPlayer = remember(playback?.url) {
        val ready = playback
        if (ready == null) {
            null
        } else {
            val http = OkHttpDataSource.Factory(
                OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build(),
            ).setDefaultRequestProperties(ready.headers)
            val dataSource = DefaultDataSource.Factory(context, http)
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
                .build()
                .apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    val builder = MediaItem.Builder().setUri(ready.url)
                    if (ready.subtitles.isNotEmpty()) {
                        builder.setSubtitleConfigurations(
                            ready.subtitles.map { subtitle ->
                                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.url))
                                    .setMimeType(mimeTypeFor(subtitle.url))
                                    .setLanguage(subtitle.lang)
                                    .setLabel(subtitle.label)
                                    .setSelectionFlags(if (subtitle.embedded) MediaItem.SELECTION_FLAG_DEFAULT else 0)
                                    .build()
                            },
                        )
                    }
                    setMediaItem(builder.build(), state.startPositionMs)
                    prepare()
                    playWhenReady = true
                }
        }
    }

    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        ended = true
                        viewModel.onStopped(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
                    }
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                viewModel.onStopped(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }
    }

    LaunchedEffect(exoPlayer, state.settings.playerSpeed) {
        exoPlayer?.setPlaybackSpeed(state.settings.playerSpeed)
    }

    LaunchedEffect(exoPlayer) {
        if (exoPlayer == null) return@LaunchedEffect
        while (true) {
            positionMs = exoPlayer.currentPosition
            if (durationMs <= 0L) durationMs = exoPlayer.duration.coerceAtLeast(0L)
            viewModel.onProgress(positionMs, durationMs)
            delay(1_000L)
        }
    }

    LaunchedEffect(controlsVisible, playing) {
        if (controlsVisible && playing) {
            delay(3_500L)
            controlsVisible = false
        }
    }

    LaunchedEffect(ended, state.next) {
        val next = state.next
        if (ended && next != null) {
            var remaining = (PlaybackThresholds.NEXT_EPISODE_COUNTDOWN_MS / 1_000L).toInt()
            countdown = remaining
            while (remaining > 0) {
                delay(1_000L)
                remaining -= 1
                countdown = remaining
                if (countdown == null) return@LaunchedEffect
            }
            onPlayEpisode(next.id)
        } else if (!ended) {
            countdown = null
        }
    }

    BackHandler {
        exoPlayer?.let { viewModel.onStopped(it.currentPosition, it.duration.coerceAtLeast(0L)) }
        onBack()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            state.loading -> LoadingState(label = "Preparing video")
            state.error != null && playback == null -> ErrorState(
                message = state.error ?: "",
                onRetry = { if (state.retryable) viewModel.retry() else onBack() },
            )
            exoPlayer != null -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { controlsVisible = !controlsVisible },
                                onDoubleTap = { offset ->
                                    val forward = offset.x > size.width / 2
                                    val step = if (forward) PlaybackThresholds.SEEK_STEP_MS else -PlaybackThresholds.SEEK_STEP_MS
                                    val target = (exoPlayer.currentPosition + step)
                                        .coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
                                    exoPlayer.seekTo(target)
                                },
                            )
                        },
                )
                if (controlsVisible) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                viewModel.onStopped(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
                                onBack()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(state.series?.title.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleMedium)
                                Text(state.episode?.name.orEmpty(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                            }
                            if (state.videos.size > 1) {
                                TextButton(onClick = { qualityMenu = true }) {
                                    Text(playback?.quality ?: "Auto", color = Color.White)
                                }
                            }
                            TextButton(onClick = { speedMenu = true }) {
                                Text("${state.settings.playerSpeed}x", color = Color.White)
                            }
                        }
                        if (playback?.cleartext == true) {
                            Text(
                                "This stream is plain http. Anyone on your network could see it.",
                                color = Color(0xFFFFD8A8),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (countdown != null && state.next != null) {
                            Column(
                                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("Next episode in ${countdown}s", color = Color.White)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { countdown = null }) { Text("Cancel") }
                                    Button(onClick = { state.next?.let { onPlayEpisode(it.id) } }) { Text("Play now") }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatDuration(positionMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                                onValueChange = { fraction ->
                                    if (durationMs > 0L) exoPlayer.seekTo((fraction * durationMs).toLong())
                                },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            )
                            Text(formatDuration(durationMs), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { exoPlayer.seekTo((positionMs - PlaybackThresholds.SEEK_STEP_MS).coerceAtLeast(0L)) }) {
                                Text("-10s", color = Color.White)
                            }
                            IconButton(onClick = { if (playing) exoPlayer.pause() else exoPlayer.play() }) {
                                Icon(
                                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                            TextButton(onClick = { exoPlayer.seekTo(positionMs + PlaybackThresholds.SEEK_STEP_MS) }) {
                                Text("+10s", color = Color.White)
                            }
                            if (state.next != null) {
                                IconButton(onClick = { state.next?.let { onPlayEpisode(it.id) } }) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "Next episode", tint = Color.White)
                                }
                            }
                        }
                    }
                }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}x") },
                            onClick = { speedMenu = false; viewModel.setSpeed(speed) },
                        )
                    }
                }
                DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                    state.videos.forEach { video ->
                        DropdownMenuItem(
                            text = { Text(video.quality) },
                            onClick = { qualityMenu = false; viewModel.selectQuality(video) },
                        )
                    }
                }
            }
            else -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
    }
}

private fun mimeTypeFor(url: String): String = when {
    url.endsWith(".vtt", true) -> "text/vtt"
    url.endsWith(".srt", true) -> "application/x-subrip"
    url.endsWith(".ssa", true) || url.endsWith(".ass", true) -> "text/x-ssa"
    else -> "text/vtt"
}
