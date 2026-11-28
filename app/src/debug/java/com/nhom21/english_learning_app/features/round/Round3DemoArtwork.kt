package com.nhom21.english_learning_app.features.round

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.nhom21.english_learning_app.R

internal val Round3DemoPictureWords = listOf(
    "Grape" to "grape",
    "Strawberry" to "strawberry",
    "Watermelon" to "watermelon",
    "Pear" to "pear",
    "Cherry" to "cherry",
)

@Composable
internal fun rememberRound3DemoArtwork(): Map<String, ImageBitmap> {
    val resources = LocalContext.current.resources
    return remember(resources) {
        resources.openRawResource(R.raw.round3_figma_fruit).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.associate { line ->
                val key = line.substringBefore('|')
                val bytes = Base64.decode(line.substringAfter('|'), Base64.DEFAULT)
                key to BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
            }
        }
    }
}

@Composable
internal fun Round3DemoImage(artwork: Map<String, ImageBitmap>, key: String, modifier: Modifier) {
    artwork[key]?.let {
        Image(it, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    }
}
