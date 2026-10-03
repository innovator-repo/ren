package app.ren.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.ren.di.ApplicationScope
import app.ren.domain.model.Story
import app.ren.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class Sfx(val asset: String) { Correct("sfx/correct.wav"), Wrong("sfx/wrong.wav"), Complete("sfx/complete.wav") }

data class StoryPlayback(
    val storyId: String? = null,
    val playing: Boolean = false,
    val sentence: Int = -1,
    val elapsedSec: Float = 0f,
    val totalSec: Float = 0f,
) {
    val progress get() = if (totalSec <= 0f) 0f else (elapsedSec / totalSec).coerceIn(0f, 1f)
}

/**
 * Dual-engine audio (spec §10): SoundPool for sub-50ms clips, Media3 ExoPlayer for long stories,
 * in one lifecycle-aware singleton. Ren's content ships without recorded audio, so any item whose
 * audio asset is empty is voiced by the device's Japanese text-to-speech instead.
 */
@Singleton
class AudioEngineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {

    private val soundPool = SoundPool.Builder().setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).build(),
        )
        .build()
    private val soundMap = ConcurrentHashMap<String, Int>()
    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private val pending = ConcurrentHashMap<Int, Boolean>()

    init {
        soundPool.setOnLoadCompleteListener { pool, id, status ->
            if (status == 0) {
                loaded += id
                if (pending.remove(id) == true) pool.play(id, 1f, 1f, 1, 0, 1f)
            }
        }
        // Warm the effects so the first correct answer chimes without delay.
        Sfx.entries.forEach { load(it.asset) }
    }

    private fun load(assetPath: String): Int? = runCatching {
        soundMap.computeIfAbsent(assetPath) { soundPool.load(context.assets.openFd(assetPath), 1) }
    }.getOrNull()

    fun playPhoneticSound(assetPath: String) {
        val id = load(assetPath) ?: return
        if (id in loaded) soundPool.play(id, 1f, 1f, 1, 0, 1f) else pending[id] = true
    }

    private var exoPlayer: ExoPlayer? = null

    fun playLongStory(assetPath: String) {
        if (exoPlayer == null) exoPlayer = ExoPlayer.Builder(context).build()
        exoPlayer?.apply {
            setMediaItem(MediaItem.fromUri("file:///android_asset/$assetPath"))
            prepare()
            play()
        }
    }

    fun release() {
        soundPool.release()
        exoPlayer?.release()
        exoPlayer = null
        tts?.shutdown()
        tts = null
    }

    // ---------------------------------------------------------------- UI sounds

    fun sfx(effect: Sfx) {
        scope.launch { if (prefs.current().soundEnabled) playPhoneticSound(effect.asset) }
    }

    // ---------------------------------------------------------------- speech

    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private val queued = mutableListOf<() -> Unit>()
    private val _speaking = MutableStateFlow<String?>(null)
    /** The utterance currently being spoken, so speaker buttons can pulse. */
    val speaking: StateFlow<String?> = _speaking.asStateFlow()

    private fun withTts(block: (TextToSpeech) -> Unit) {
        val engine = tts
        if (engine != null && ttsReady) { block(engine); return }
        synchronized(queued) { queued += { tts?.let(block) } }
        if (engine == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.JAPAN
                    tts?.setOnUtteranceProgressListener(listener)
                    ttsReady = true
                    val run = synchronized(queued) { queued.toList().also { queued.clear() } }
                    run.forEach { it() }
                }
            }
        }
    }

    /** Plays a word or sentence: the bundled clip when there is one, otherwise TTS. */
    fun say(text: String, assetPath: String = "") {
        if (assetPath.isNotEmpty()) { playPhoneticSound(assetPath); return }
        // TextToSpeech binds a service and calls back on the main thread; create and drive it there.
        scope.launch(Dispatchers.Main) {
            val rate = prefs.current().speechRate
            withTts {
                it.setSpeechRate(rate)
                it.speak(text, TextToSpeech.QUEUE_FLUSH, null, "say:$text")
            }
        }
    }

    // ---------------------------------------------------------------- stories

    private val _story = MutableStateFlow(StoryPlayback())
    val story: StateFlow<StoryPlayback> = _story.asStateFlow()
    private var ticker: Job? = null
    private var sentences: List<String> = emptyList()

    fun toggleStory(story: Story, from: Int = 0) {
        val now = _story.value
        if (now.storyId == story.id && now.playing) pauseStory() else playStory(story, if (now.storyId == story.id && now.sentence >= 0) now.sentence else from)
    }

    fun playStory(story: Story, from: Int = 0) {
        stopSpeech()
        val total = story.durationSec.toFloat()
        val perSentence = total / story.sentences.size.coerceAtLeast(1)
        _story.value = StoryPlayback(story.id, true, from, perSentence * from, total)
        if (story.audioAssetPath.isNotEmpty()) {
            playLongStory(story.audioAssetPath)
            exoPlayer?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = _story.update { it.copy(playing = isPlaying) }
            })
        } else {
            sentences = story.sentences.map { it.plain }
            scope.launch(Dispatchers.Main) {
                val rate = prefs.current().speechRate
                withTts { engine ->
                    engine.setSpeechRate(rate)
                    for (i in from until sentences.size) {
                        engine.speak(sentences[i], if (i == from) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "story:${story.id}:$i")
                        engine.playSilentUtterance(450, TextToSpeech.QUEUE_ADD, "gap:$i")
                    }
                }
            }
        }
        ticker?.cancel()
        ticker = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(100)
                val p = _story.value
                if (!p.playing) continue
                val exo = exoPlayer
                if (story.audioAssetPath.isNotEmpty() && exo != null) {
                    _story.update { it.copy(elapsedSec = exo.currentPosition / 1000f) }
                } else {
                    val cap = perSentence * (p.sentence + 1).coerceAtLeast(1)
                    _story.update { it.copy(elapsedSec = (it.elapsedSec + 0.1f).coerceAtMost(cap)) }
                }
            }
        }
    }

    fun pauseStory() {
        _story.update { it.copy(playing = false) }
        stopSpeech()
        exoPlayer?.pause()
    }

    fun stopStory() {
        ticker?.cancel()
        _story.value = StoryPlayback()
        stopSpeech()
        exoPlayer?.stop()
    }

    private fun stopSpeech() {
        tts?.stop()
        _speaking.value = null
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            _speaking.value = utteranceId
            if (utteranceId.startsWith("story:")) {
                val parts = utteranceId.split(':')
                val index = parts[2].toInt()
                _story.update { p ->
                    if (p.storyId != parts[1]) p
                    else p.copy(sentence = index, elapsedSec = maxOf(p.elapsedSec, p.totalSec / sentences.size.coerceAtLeast(1) * index))
                }
            }
        }

        override fun onDone(utteranceId: String) {
            _speaking.value = null
            if (utteranceId == "gap:${sentences.lastIndex}") {
                _story.update { it.copy(playing = false, sentence = -1, elapsedSec = it.totalSec) }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            _speaking.value = null
        }
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onStop(owner: LifecycleOwner) {
        // App went to the background: never keep talking over the learner's next app.
        pauseStory()
    }
}
