package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.size.Scale
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

private val embeddedArtworkCache = LruCache<String, ImageBitmap>(80)
private val embeddedArtworkMissingCache = LruCache<String, Boolean>(400)
private val ambientStyleCache = LruCache<String, CoverAmbientStyle>(16)
private val embeddedRetrieverSemaphore = Semaphore(2)

@Immutable
data class CoverAmbientStyle(
    val blurredBitmap: ImageBitmap? = null,
    val primaryColor: Color = Color(0xFF1F2B44),
    val secondaryColor: Color = Color(0xFF33203E),
    val deepColor: Color = Color(0xFF0E121B),
)

@OptIn(ExperimentalCoilApi::class)
fun invalidateArtworkCaches(context: Context, vararg uriStrings: String?) {
    embeddedArtworkCache.evictAll()
    embeddedArtworkMissingCache.evictAll()
    ambientStyleCache.evictAll()
    runCatching {
        val loader = context.imageLoader
        uriStrings.filterNotNull().filter { it.isNotBlank() }.forEach { uri ->
            loader.memoryCache?.remove(coil.memory.MemoryCache.Key(uri))
            loader.diskCache?.remove(uri)
        }
    }
}

suspend fun extractCoverAmbientStyle(
    context: Context,
    artworkUri: String?,
    fallbackUri: String?,
    seedKey: String,
): CoverAmbientStyle {
    val cacheKey = "$seedKey|${artworkUri.orEmpty()}|${fallbackUri.orEmpty()}"
    ambientStyleCache.get(cacheKey)?.let { return it }

    return withContext(Dispatchers.IO) {
        ambientStyleCache.get(cacheKey)?.let { return@withContext it }
        val bmp = loadSmallArtworkBitmap(context, artworkUri, fallbackUri, targetSize = 72)
        val computed = if (bmp == null) {
            fallbackAmbientForSeed(seedKey)
        } else {
            try {
                val (primary, secondary, deep) = extractRichCoverPalette(bmp, seedKey)
                val blurred = fastBoxBlur(bmp, radius = 6)?.asImageBitmap()
                CoverAmbientStyle(
                    blurredBitmap = blurred,
                    primaryColor = primary,
                    secondaryColor = secondary,
                    deepColor = deep,
                )
            } finally {
                bmp.recycle()
            }
        }
        ambientStyleCache.put(cacheKey, computed)
        computed
    }
}

private fun loadSmallArtworkBitmap(
    context: Context,
    artworkUri: String?,
    fallbackUri: String?,
    targetSize: Int,
): Bitmap? {
    fun decodeUriStream(uriStr: String): Bitmap? = runCatching {
        val uri = Uri.parse(uriStr)
        val stream = if (uri.scheme == "file") {
            val f = File(uri.path ?: return null)
            if (!f.exists()) return null
            f.inputStream()
        } else {
            context.contentResolver.openInputStream(uri) ?: return null
        }
        val bytes = stream.use { it.readBytes() }
        if (bytes.isEmpty()) return null
        decodeSampledAndroidBitmap(bytes, targetSize)
    }.getOrNull()

    if (!artworkUri.isNullOrBlank()) {
        decodeUriStream(artworkUri)?.let { return it }
    }
    if (!fallbackUri.isNullOrBlank()) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(fallbackUri))
                val pic = retriever.embeddedPicture ?: return@runCatching null
                decodeSampledAndroidBitmap(pic, targetSize)
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()?.let { return it }
    }
    return null
}

private fun decodeSampledAndroidBitmap(bytes: ByteArray, reqSizePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while ((bounds.outHeight / (sampleSize * 2)) >= reqSizePx && (bounds.outWidth / (sampleSize * 2)) >= reqSizePx) {
        sampleSize *= 2
    }
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
    return if (decoded.width > reqSizePx || decoded.height > reqSizePx) {
        val ratio = minOf(reqSizePx.toFloat() / decoded.width, reqSizePx.toFloat() / decoded.height)
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(16),
            (decoded.height * ratio).toInt().coerceAtLeast(16),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        scaled
    } else decoded
}

private fun extractRichCoverPalette(bmp: Bitmap, seedKey: String): Triple<Color, Color, Color> {
    val w = bmp.width
    val h = bmp.height
    val pixels = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)

    // Quantize into 18 hue buckets + grayscale bucket to find dominant vibrant & tonal colors
    val buckets = FloatArray(19)
    val hueSums = FloatArray(19)
    val satSums = FloatArray(19)
    val valSums = FloatArray(19)
    val counts = IntArray(19)
    val hsv = FloatArray(3)

    for (px in pixels) {
        android.graphics.Color.colorToHSV(px, hsv)
        val hue = hsv[0]
        val sat = hsv[1]
        val bri = hsv[2]
        if (bri < 0.08f) continue
        val bucket = if (sat < 0.14f) 18 else ((hue / 20f).toInt().coerceIn(0, 17))
        val weight = if (sat >= 0.25f && bri in 0.18f..0.92f) 1.5f + sat * 2.5f else 0.35f
        buckets[bucket] += weight
        hueSums[bucket] += hue
        satSums[bucket] += sat
        valSums[bucket] += bri
        counts[bucket]++
    }

    val ranked = (0..17).filter { counts[it] > 0 }.sortedByDescending { buckets[it] }
    if (ranked.isEmpty()) {
        return fallbackAmbientForSeed(seedKey).let { Triple(it.primaryColor, it.secondaryColor, it.deepColor) }
    }

    fun colorFromBucket(b: Int, targetValMin: Float, targetValMax: Float, satBoost: Float): Color {
        val c = counts[b].coerceAtLeast(1)
        val hVal = (hueSums[b] / c).coerceIn(0f, 360f)
        val sVal = ((satSums[b] / c) * satBoost).coerceIn(0.32f, 0.88f)
        val vVal = (valSums[b] / c).coerceIn(targetValMin, targetValMax)
        return Color(android.graphics.Color.HSVToColor(floatArrayOf(hVal, sVal, vVal)))
    }

    val first = ranked.first()
    val primary = colorFromBucket(first, targetValMin = 0.32f, targetValMax = 0.56f, satBoost = 1.15f)
    val second = ranked.firstOrNull { abs(it - first) in 2..15 } ?: first
    val secondary = if (second != first) {
        colorFromBucket(second, targetValMin = 0.22f, targetValMax = 0.44f, satBoost = 1.1f)
    } else {
        val c = counts[first].coerceAtLeast(1)
        val shiftedHue = ((hueSums[first] / c) + 32f) % 360f
        val sat = ((satSums[first] / c) * 1.05f).coerceIn(0.30f, 0.80f)
        Color(android.graphics.Color.HSVToColor(floatArrayOf(shiftedHue, sat, 0.28f)))
    }
    val deep = Color(
        red = primary.red * 0.32f,
        green = primary.green * 0.32f,
        blue = primary.blue * 0.32f,
        alpha = 1f,
    )
    return Triple(primary, secondary, deep)
}

private fun fallbackAmbientForSeed(seedKey: String): CoverAmbientStyle {
    val hue = ((seedKey.hashCode() and 0x7FFFFFFF) % 360).toFloat()
    val primary = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.52f, 0.36f)))
    val secondary = Color(android.graphics.Color.HSVToColor(floatArrayOf((hue + 42f) % 360f, 0.48f, 0.26f)))
    val deep = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.44f, 0.12f)))
    return CoverAmbientStyle(
        blurredBitmap = null,
        primaryColor = primary,
        secondaryColor = secondary,
        deepColor = deep,
    )
}

private fun fastBoxBlur(src: Bitmap, radius: Int): Bitmap? {
    val w = src.width
    val h = src.height
    if (w <= 0 || h <= 0 || radius < 1) return null
    val pixels = IntArray(w * h)
    val tmp = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    val div = radius * 2 + 1
    repeat(2) {
        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0
                for (dx in -radius..radius) {
                    val sx = (x + dx).coerceIn(0, w - 1)
                    val px = pixels[rowOff + sx]
                    r += (px shr 16) and 0xFF
                    g += (px shr 8) and 0xFF
                    b += px and 0xFF
                }
                tmp[rowOff + x] = (0xFF shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
            }
        }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var r = 0; var g = 0; var b = 0
                for (dy in -radius..radius) {
                    val sy = (y + dy).coerceIn(0, h - 1)
                    val px = tmp[sy * w + x]
                    r += (px shr 16) and 0xFF
                    g += (px shr 8) and 0xFF
                    b += px and 0xFF
                }
                pixels[y * w + x] = (0xFF shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
            }
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

private fun decodeSampledBitmap(bytes: ByteArray, reqSizePx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sampleSize = 1
    if (reqSizePx > 0 && (bounds.outHeight > reqSizePx || bounds.outWidth > reqSizePx)) {
        val halfH = bounds.outHeight / 2
        val halfW = bounds.outWidth / 2
        while ((halfH / sampleSize) >= reqSizePx && (halfW / sampleSize) >= reqSizePx) {
            sampleSize *= 2
        }
    }
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        if (reqSizePx in 1..360) {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
}

@Composable
fun ArtworkImage(
    artworkUri: String?,
    fallbackUri: String? = null,
    contentDescription: String? = stringResource(R.string.cd_album_artwork),
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    fallbackIcon: ImageVector = Icons.Rounded.MusicNote,
    maxSizePx: Int = 320,
    crossfade: Boolean = false,
) {
    val context = LocalContext.current
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val primaryUri = artworkUri?.takeIf { it.isNotBlank() } ?: fallbackUri?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (primaryUri == null) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxSize(0.42f),
            )
        } else {
            var loadFailed by remember(primaryUri) { mutableStateOf(false) }
            var isLoaded by remember(primaryUri) { mutableStateOf(false) }

            if (!loadFailed) {
                if (!isLoaded) {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                        modifier = Modifier.fillMaxSize(0.36f),
                    )
                }
                val request = remember(context, primaryUri, maxSizePx, crossfade) {
                    ImageRequest.Builder(context)
                        .data(primaryUri)
                        .apply {
                            if (maxSizePx > 0) {
                                size(maxSizePx, maxSizePx)
                                scale(Scale.FILL)
                            }
                            crossfade(crossfade)
                        }
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    onState = { state ->
                        when (state) {
                            is AsyncImagePainter.State.Success -> {
                                isLoaded = true
                                loadFailed = false
                            }
                            is AsyncImagePainter.State.Error, is AsyncImagePainter.State.Empty -> {
                                loadFailed = true
                            }
                            is AsyncImagePainter.State.Loading -> Unit
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                EmbeddedArtworkFallback(
                    audioUri = fallbackUri ?: artworkUri,
                    contentDescription = contentDescription,
                    fallbackIcon = fallbackIcon,
                    targetSizePx = maxSizePx,
                )
            }
        }
    }
}

@Composable
private fun EmbeddedArtworkFallback(
    audioUri: String?,
    contentDescription: String?,
    fallbackIcon: ImageVector,
    targetSizePx: Int,
) {
    val context = LocalContext.current
    val cacheKey = audioUri?.let { "$it|$targetSizePx" }
    val cached = cacheKey?.let { embeddedArtworkCache.get(it) }
    val knownMissing = audioUri != null && embeddedArtworkMissingCache.get(audioUri) == true
    val embeddedBitmap by produceState<ImageBitmap?>(
        initialValue = cached,
        audioUri,
        targetSizePx,
    ) {
        if (cached != null) {
            value = cached
            return@produceState
        }
        if (audioUri.isNullOrBlank() || knownMissing) {
            value = null
            return@produceState
        }
        value = embeddedRetrieverSemaphore.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, Uri.parse(audioUri))
                        val bytes = retriever.embeddedPicture
                        if (bytes == null) {
                            embeddedArtworkMissingCache.put(audioUri, true)
                            return@withContext null
                        }
                        val decoded = decodeSampledBitmap(bytes, targetSizePx)
                        if (decoded != null && cacheKey != null) {
                            embeddedArtworkCache.put(cacheKey, decoded)
                        } else if (decoded == null) {
                            embeddedArtworkMissingCache.put(audioUri, true)
                        }
                        decoded
                    } finally {
                        runCatching { retriever.release() }
                    }
                }.getOrNull()
            }
        }
    }

    val bmp = embeddedBitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxSize(0.42f),
        )
    }
}

@Composable
fun PlaylistCollageArtwork(
    customCoverUri: String?,
    artworkUris: List<String>,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    if (!customCoverUri.isNullOrBlank() || artworkUris.size < 4) {
        ArtworkImage(
            artworkUri = customCoverUri ?: artworkUris.firstOrNull(),
            modifier = modifier,
            cornerRadius = cornerRadius,
            fallbackIcon = Icons.Rounded.Album,
        )
    } else {
        Column(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        ) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ArtworkImage(
                    artworkUri = artworkUris[0],
                    cornerRadius = 0.dp,
                    maxSizePx = 180,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                ArtworkImage(
                    artworkUri = artworkUris[1],
                    cornerRadius = 0.dp,
                    maxSizePx = 180,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ArtworkImage(
                    artworkUri = artworkUris[2],
                    cornerRadius = 0.dp,
                    maxSizePx = 180,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                ArtworkImage(
                    artworkUri = artworkUris[3],
                    cornerRadius = 0.dp,
                    maxSizePx = 180,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}
