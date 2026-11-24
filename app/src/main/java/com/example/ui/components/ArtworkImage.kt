package com.example.ui.components

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ArtworkImage(
    artworkUri: String?,
    fallbackUri: String? = null,
    contentDescription: String? = stringResource(R.string.cd_album_artwork),
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    fallbackIcon: ImageVector = Icons.Rounded.MusicNote,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUri.isNullOrBlank() && fallbackUri.isNullOrBlank()) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxSize(0.42f),
            )
        } else {
            val primaryUri = artworkUri ?: fallbackUri
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(primaryUri)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                    is AsyncImagePainter.State.Error, is AsyncImagePainter.State.Empty -> {
                        EmbeddedArtworkFallback(
                            audioUri = fallbackUri ?: artworkUri,
                            contentDescription = contentDescription,
                            fallbackIcon = fallbackIcon,
                        )
                    }
                    is AsyncImagePainter.State.Loading -> {
                        Icon(
                            imageVector = fallbackIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxSize(0.36f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmbeddedArtworkFallback(
    audioUri: String?,
    contentDescription: String?,
    fallbackIcon: ImageVector,
) {
    val context = LocalContext.current
    val embeddedBitmap by produceState<ImageBitmap?>(initialValue = null, audioUri) {
        value = if (audioUri.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(audioUri))
                    val bytes = retriever.embeddedPicture ?: return@withContext null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } finally {
                    runCatching { retriever.release() }
                }
            }.getOrNull()
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
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                ArtworkImage(
                    artworkUri = artworkUris[1],
                    cornerRadius = 0.dp,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ArtworkImage(
                    artworkUri = artworkUris[2],
                    cornerRadius = 0.dp,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                ArtworkImage(
                    artworkUri = artworkUris[3],
                    cornerRadius = 0.dp,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}
