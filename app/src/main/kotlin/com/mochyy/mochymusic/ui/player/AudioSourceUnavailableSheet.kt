package com.mochyy.mochymusic.ui.player

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as Media3Metadata
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.playback.audio.LocalAudioSuggestion
import com.mochyy.mochymusic.playback.queues.ListQueue

@Composable
fun AudioSourceUnavailableDialog() {
    val playerConnection = LocalPlayerConnection.current ?: return
    val notice by playerConnection.unavailableNotice.collectAsState()
    val currentNotice = notice ?: return

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val localAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val item = MediaItem.Builder()
                .setMediaId("LOCAL_PICKED_${uri.hashCode()}")
                .setUri(uri)
                .setCustomCacheKey(uri.toString())
                .setMediaMetadata(
                    Media3Metadata.Builder()
                        .setTitle(currentNotice.title.ifBlank { "Local Audio Track" })
                        .setArtist(currentNotice.artist.ifBlank { "Local Device Music" })
                        .build()
                )
                .build()
            playerConnection.dismissUnavailableNotice()
            playerConnection.playQueue(
                ListQueue(
                    title = currentNotice.title.ifBlank { "Local Audio" },
                    items = listOf(item),
                )
            )
        }
    }

    AlertDialog(
        onDismissRequest = { playerConnection.dismissUnavailableNotice() },
        modifier = Modifier.testTag("audio_source_unavailable_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = "Audio source unavailable",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = "Audio source unavailable",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(10.dp),
                ) {
                    if (!currentNotice.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = currentNotice.thumbnailUrl,
                            contentDescription = currentNotice.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentNotice.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (currentNotice.artist.isNotBlank()) {
                            Text(
                                text = currentNotice.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Text(
                    text = "ytmusicapi provides catalogue metadata, artwork, and lyrics only. No authorized audio source matched this recording.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (currentNotice.localSuggestions.isNotEmpty()) {
                    Text(
                        text = "Available Local Library Tracks",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        currentNotice.localSuggestions.take(3).forEach { suggestion ->
                            LocalSuggestionRow(
                                suggestion = suggestion,
                                onClick = {
                                    val localUri = Uri.parse(suggestion.localUri)
                                    val mediaItem = MediaItem.Builder()
                                        .setMediaId("LOCAL_${suggestion.songId}")
                                        .setUri(localUri)
                                        .setCustomCacheKey(suggestion.localUri)
                                        .setMediaMetadata(
                                            Media3Metadata.Builder()
                                                .setTitle(suggestion.title)
                                                .setArtist(suggestion.artist)
                                                .build()
                                        )
                                        .build()
                                    playerConnection.dismissUnavailableNotice()
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = suggestion.title,
                                            items = listOf(mediaItem),
                                        )
                                    )
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        runCatching {
                            uriHandler.openUri(currentNotice.externalYoutubeMusicUrl)
                        }
                        playerConnection.dismissUnavailableNotice()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_youtube_music_external_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Open in YouTube Music",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Open in YouTube Music")
                }

                OutlinedButton(
                    onClick = {
                        localAudioPicker.launch(arrayOf("audio/*"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("browse_local_music_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = "Play Local Audio File",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Play Local Audio File")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { playerConnection.dismissUnavailableNotice() },
                modifier = Modifier.testTag("dismiss_unavailable_dialog_button"),
            ) {
                Text("Dismiss")
            }
        },
    )
}

@Composable
private fun LocalSuggestionRow(
    suggestion: LocalAudioSuggestion,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (suggestion.artist.isNotBlank()) {
                    Text(
                        text = suggestion.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Play local track",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
