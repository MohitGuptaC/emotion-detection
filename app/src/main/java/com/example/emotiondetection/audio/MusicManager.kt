package com.example.emotiondetection.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.random.Random

/**
 * Plays pre-generated tracks stored under assets/pre_generated_music.
 */
class MusicManager(
    private val context: Context,
    private val onPlaybackStatusChanged: ((Boolean?, String) -> Unit)? = null
) {
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var playbackTimeout: Runnable? = null
    private var playbackCompletionCallback: (() -> Unit)? = null

    fun playPreGenerated(
        emotionLabel: String,
        usePrimarySet: Boolean,
        playbackDurationMs: Long,
        onPlaybackFinished: (() -> Unit)? = null
    ): Boolean {
        if (emotionLabel.isBlank()) return false

        val baseName = mapEmotionToAssetBase(emotionLabel)
        if (baseName == null) {
            Log.w(TAG, "No asset mapping for emotion '$emotionLabel'")
            return false
        }

        val targetFolder = buildFolderPath(baseName, usePrimarySet)
        val tracks = loadTracks(targetFolder)

        val tracksAvailable = tracks.isNotEmpty()

        val assetPath = if (tracksAvailable) {
            val chosenTrack = tracks.random(Random.Default)
            "$targetFolder/$chosenTrack"
        } else {
            val fallback = fallbackAssetPath(baseName)
            if (fallback == null) {
                Log.w(TAG, "No tracks found in assets/$targetFolder and no fallback for $baseName")
                return false
            }
            Log.w(TAG, "Falling back to $fallback for emotion '$emotionLabel'")
            fallback
        }

        stopPlayback(triggerCallback = false)
        playbackCompletionCallback = onPlaybackFinished

        return try {
            val afd = context.assets.openFd(assetPath)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                prepare()
                start()
                setOnCompletionListener { completePlayback() }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra for $assetPath")
                    mp.reset()
                    completePlayback()
                    true
                }
            }

            onPlaybackStatusChanged?.invoke(tracksAvailable, emotionLabel)

            val timeout = Runnable { completePlayback() }
            playbackTimeout = timeout
            handler.postDelayed(timeout, playbackDurationMs)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play asset track '$assetPath'", e)
            playbackCompletionCallback = null
            false
        }
    }

    fun stopPlayback(triggerCallback: Boolean = false) {
        playbackTimeout?.let { handler.removeCallbacks(it) }
        playbackTimeout = null
        mediaPlayer?.release()
        mediaPlayer = null
        if (triggerCallback) {
            notifyPlaybackFinished()
        }
    }

    fun release() {
        stopPlayback(triggerCallback = false)
        playbackCompletionCallback = null
    }

    private fun completePlayback() {
        stopPlayback(triggerCallback = false)
        notifyPlaybackFinished()
    }

    private fun notifyPlaybackFinished() {
        val callback = playbackCompletionCallback
        playbackCompletionCallback = null
        onPlaybackStatusChanged?.invoke(null, "")
        callback?.invoke()
    }

    private fun loadTracks(folderPath: String): List<String> {
        return try {
            context.assets.list(folderPath)?.filter { asset ->
                TRACK_EXTENSIONS.any { ext -> asset.endsWith(ext, ignoreCase = true) }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list assets for $folderPath", e)
            emptyList()
        }
    }

    private fun fallbackAssetPath(baseName: String): String? {
        return TRACK_EXTENSIONS.asSequence()
            .map { ext -> "$FALLBACK_BASE/$baseName$ext" }
            .firstOrNull { assetExists(it) }
    }

    private fun assetExists(assetPath: String): Boolean {
        return try {
            context.assets.openFd(assetPath).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun mapEmotionToAssetBase(emotion: String): String? {
        return when (emotion.trim()) {
            "Neutral" -> "neutral"
            "Happiness" -> "happiness"
            "Surprise" -> "surprise"
            "Sadness" -> "sadness"
            "Anger" -> "anger"
            "Disgust" -> "disgust"
            "Fear" -> "fear"
            "Contempt" -> "contempt"
            else -> null
        }
    }

    private fun buildFolderPath(baseName: String, primary: Boolean): String {
        val bucket = if (primary) PRIMARY_FOLDER else SECONDARY_FOLDER
        return "$BASE_PATH/$baseName/$bucket"
    }

    companion object {
        private const val TAG = "MusicManager"
        private const val BASE_PATH = "pre_generated_music"
        private const val PRIMARY_FOLDER = "set1"
        private const val SECONDARY_FOLDER = "set2"
        private const val FALLBACK_BASE = "default_music"
        private val TRACK_EXTENSIONS = listOf(".mp3", ".wav", ".ogg")
    }
}


