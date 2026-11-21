package com.nhom21.english_learning_app.features.round

import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nhom21.english_learning_app.R
import com.nhom21.english_learning_app.core.ui.components.VocabularyAudioState
import com.nhom21.english_learning_app.ui.theme.OwlaType
import com.nhom21.english_learning_app.ui.theme.roundColors
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// Media URLs are public. Keep them off the authenticated API client.
private val imageClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
private const val MAX_IMAGE_BYTES = 8L * 1024 * 1024

@Composable
internal fun RoundWordImage(url: String, modifier: Modifier = Modifier.fillMaxWidth().height(128.dp)) {
    val image by produceState<WordImage>(WordImage.Loading, url) {
        value = WordImage.Loading
        value = withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                imageClient.newCall(request).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null || body.source().request(MAX_IMAGE_BYTES + 1)) {
                        return@use WordImage.Unavailable
                    }
                    val bytes = body.bytes()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    bitmap?.let { WordImage.Ready(it.asImageBitmap()) } ?: WordImage.Unavailable
                }
            } catch (_: IOException) {
                WordImage.Unavailable
            } catch (_: IllegalArgumentException) {
                WordImage.Unavailable
            }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        when (val result = image) {
            is WordImage.Ready -> Image(
                result.bitmap, contentDescription = null, modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Fit
            )
            WordImage.Unavailable -> Text(
                stringResource(R.string.round_image_unavailable), color = roundColors().ink, style = OwlaType.caption
            )
            WordImage.Loading -> Unit
        }
    }
}

private sealed interface WordImage {
    data object Loading : WordImage
    data object Unavailable : WordImage
    data class Ready(val bitmap: ImageBitmap) : WordImage
}

@Composable
internal fun rememberRound1Audio(url: String?): Round1Audio {
    val audio = remember(url) { Round1Audio(url) }
    DisposableEffect(audio) { onDispose { audio.stop() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { audio.stop() }
    return audio
}

internal class Round1Audio(private val url: String?) {
    private val idle = if (url.isNullOrBlank()) VocabularyAudioState.Unavailable else VocabularyAudioState.Ready
    private var player: MediaPlayer? = null
    var state by mutableStateOf(idle)
        private set

    fun play() {
        if (url.isNullOrBlank() || player != null) return
        state = VocabularyAudioState.Loading
        try {
            val audio = MediaPlayer()
            player = audio
            audio.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            audio.setOnPreparedListener { prepared ->
                if (player === prepared) {
                    try {
                        prepared.start()
                        state = VocabularyAudioState.Playing
                    } catch (_: IllegalStateException) {
                        fail()
                    }
                }
            }
            audio.setOnCompletionListener { if (player === it) stop() }
            audio.setOnErrorListener { failed, _, _ ->
                if (player === failed) fail()
                true
            }
            audio.setDataSource(url)
            audio.prepareAsync()
        } catch (_: IOException) {
            fail()
        } catch (_: RuntimeException) {
            fail()
        }
    }

    fun stop() {
        val active = player
        player = null
        active?.release()
        state = idle
    }

    private fun fail() {
        stop()
        state = VocabularyAudioState.Error
    }
}
