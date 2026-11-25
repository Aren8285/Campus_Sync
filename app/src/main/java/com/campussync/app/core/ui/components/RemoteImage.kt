package com.campussync.app.core.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads an image from an external link.
 *
 * Campus Sync does not upload files, so images are links. Coil needs a separate
 * network module, and this project keeps its dependency list small, so this
 * loader uses the platform HTTP client.
 *
 * [maxDimension] caps the decoded size. Use a smaller value for thumbnails so a
 * chat bubble does not hold a full-screen bitmap in memory.
 *
 * [fallback] replaces the spinner and the broken-image icon, which lets callers
 * show initials while a profile photo loads or when it cannot be loaded.
 */
@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxDimension: Int = 1200,
    fallback: (@Composable () -> Unit)? = null
) {
    var bitmap by remember(url, maxDimension) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url, maxDimension) { mutableStateOf(false) }

    LaunchedEffect(url, maxDimension) {
        bitmap = null
        failed = false
        val loaded = RemoteImageLoader.load(url, maxDimension)
        bitmap = loaded
        failed = loaded == null
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        when {
            image != null -> Image(
                bitmap = image,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )

            failed -> if (fallback != null) {
                fallback()
            } else {
                Icon(
                    imageVector = Icons.Outlined.BrokenImage,
                    contentDescription = "Image could not be loaded",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }

            else -> if (fallback != null) {
                fallback()
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

private object RemoteImageLoader {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val BYTES_PER_PIXEL = 4
    private const val MIN_CACHE_BYTES = 8L * 1024 * 1024
    private const val MAX_CACHE_BYTES = 64L * 1024 * 1024

    // Measured in bytes so a few large images cannot fill the cache with
    // hundreds of megabytes, which an entry-count cache would allow. An eighth of
    // the heap keeps scrolling smooth on small devices without hoarding memory on
    // large ones.
    private val cache = object : LruCache<String, ImageBitmap>(cacheSizeBytes()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * BYTES_PER_PIXEL
    }

    private fun cacheSizeBytes(): Int =
        (Runtime.getRuntime().maxMemory() / 8L)
            .coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
            .toInt()

    suspend fun load(url: String, maxDimension: Int): ImageBitmap? {
        val trimmed = url.trim()
        // Only public https links are allowed; cleartext images are never fetched.
        if (!trimmed.startsWith("https://")) return null

        val key = "$maxDimension:$trimmed"
        cache.get(key)?.let { return it }

        return withContext(Dispatchers.IO) {
            val decoded = runCatching { fetch(trimmed, maxDimension) }.getOrNull()
            if (decoded != null) {
                cache.put(key, decoded)
            }
            decoded
        }
    }

    private fun fetch(url: String, maxDimension: Int): ImageBitmap? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }

        try {
            if (connection.responseCode !in 200..299) return null
            if (connection.contentLengthLong > MAX_BYTES) return null

            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_BYTES) return null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }

            if (bytes.isEmpty()) return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxDimension ||
                bounds.outHeight / sampleSize > maxDimension
            ) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap: Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: return null

            return bitmap.asImageBitmap()
        } finally {
            connection.disconnect()
        }
    }
}
