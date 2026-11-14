package com.mochyy.mochymusic.ui.screens.settings.integrations

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.PreferYouTubeDataApiSearchKey
import com.mochyy.mochymusic.constants.UseYouTubeDataApiFallbackKey
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.ui.component.AppSwitch
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.utils.rememberPreference
import com.mochyy.mochymusic.viewmodels.DataApiExplorerMode
import com.mochyy.mochymusic.viewmodels.YouTubeDataApiViewModel
import com.music.innertube.models.SongItem
import java.util.Locale

const val YouTubeDataApiRoute = "integrations_settings/youtube_data_api"

private val CardSurface = Color(0xFF1F1F1F)
private val StatusGreen = Color(0xFF4ADE80)
private val StatusAmber = Color(0xFFFBBF24)

@Composable
fun YouTubeDataApiScreen(
    navController: NavController,
    viewModel: YouTubeDataApiViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val playerConnection = LocalPlayerConnection.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val (useFallback, onUseFallbackChange) = rememberPreference(
        UseYouTubeDataApiFallbackKey,
        defaultValue = true,
    )
    val (preferDataApiSearch, onPreferDataApiSearchChange) = rememberPreference(
        PreferYouTubeDataApiSearchKey,
        defaultValue = false,
    )
    val (fastApiBackendUrl, onFastApiBackendUrlChange) = rememberPreference(
        com.mochyy.mochymusic.constants.FastApiBackendUrlKey,
        defaultValue = com.mochyy.mochymusic.constants.DefaultFastApiBackendUrl,
    )

    SubSettingsScaffold(
        title = stringResource(R.string.youtube_data_api_title),
        onBack = { navController.navigateUp() },
        backLabel = stringResource(R.string.integrations),
    ) {
        IntegrationPageSubtitle(stringResource(R.string.youtube_data_api_subtitle))

        ApiStatusCard(
            isKeyConfigured = uiState.isKeyConfigured,
            packageName = uiState.packageName,
            sha1Fingerprint = uiState.sha1Fingerprint,
            onCopySha1 = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("SHA-1", uiState.sha1Fingerprint))
                Toast.makeText(context, "SHA-1 copied", Toast.LENGTH_SHORT).show()
            },
        )

        Spacer(Modifier.height(14.dp))

        Surface(
            color = CardSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                Text(
                    text = "FastAPI ytmusicapi Discovery Backend",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    text = "Unauthenticated sigma67/ytmusicapi endpoint for search, song metadata, artwork, lyrics, albums, artists, playlists, and radio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
                OutlinedTextField(
                    value = fastApiBackendUrl,
                    onValueChange = onFastApiBackendUrlChange,
                    singleLine = true,
                    placeholder = { Text(com.mochyy.mochymusic.constants.DefaultFastApiBackendUrl) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF141414),
                        unfocusedContainerColor = Color(0xFF141414),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fastapi_backend_url_input"),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        val fallbackTitle = stringResource(R.string.youtube_data_api_fallback_title)
        val preferTitle = stringResource(R.string.youtube_data_api_prefer_search_title)
        SettingsGroup(
            title = stringResource(R.string.youtube_data_api_title),
            style = SettingsGroupStyle.Grouped,
            items = listOf(
                SettingsItem(
                    title = { Text(fallbackTitle) },
                    highlightKey = "YouTube Data API fallback",
                    searchText = fallbackTitle,
                    descriptionText = stringResource(R.string.youtube_data_api_fallback_desc),
                    icon = painterResource(R.drawable.cloud),
                    iconShape = CircleShape,
                    onClick = { onUseFallbackChange(!useFallback) },
                    trailingContent = {
                        AppSwitch(
                            checked = useFallback,
                            onCheckedChange = onUseFallbackChange,
                            modifier = Modifier.testTag("youtube_data_api_fallback_switch"),
                        )
                    },
                ),
                SettingsItem(
                    title = { Text(preferTitle) },
                    highlightKey = "Prefer YouTube Data API search",
                    searchText = preferTitle,
                    descriptionText = stringResource(R.string.youtube_data_api_prefer_search_desc),
                    icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.Search),
                    iconShape = CircleShape,
                    onClick = { onPreferDataApiSearchChange(!preferDataApiSearch) },
                    trailingContent = {
                        AppSwitch(
                            checked = preferDataApiSearch,
                            onCheckedChange = onPreferDataApiSearchChange,
                            modifier = Modifier.testTag("youtube_data_api_prefer_switch"),
                        )
                    },
                ),
            ),
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.youtube_data_api_explorer_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ExplorerModeChip(
                selected = uiState.mode == DataApiExplorerMode.SEARCH,
                label = stringResource(R.string.youtube_data_api_mode_search),
                icon = Icons.Rounded.Search,
                tag = "youtube_data_api_mode_search",
                onClick = { viewModel.setMode(DataApiExplorerMode.SEARCH) },
            )
            ExplorerModeChip(
                selected = uiState.mode == DataApiExplorerMode.PLAYLIST,
                label = stringResource(R.string.youtube_data_api_mode_playlist),
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                tag = "youtube_data_api_mode_playlist",
                onClick = { viewModel.setMode(DataApiExplorerMode.PLAYLIST) },
            )
            ExplorerModeChip(
                selected = uiState.mode == DataApiExplorerMode.CHANNEL,
                label = stringResource(R.string.youtube_data_api_mode_channel),
                icon = Icons.Rounded.VideoLibrary,
                tag = "youtube_data_api_mode_channel",
                onClick = { viewModel.setMode(DataApiExplorerMode.CHANNEL) },
            )
        }

        Spacer(Modifier.height(10.dp))

        val hintRes = when (uiState.mode) {
            DataApiExplorerMode.SEARCH -> R.string.youtube_data_api_search_hint
            DataApiExplorerMode.PLAYLIST -> R.string.youtube_data_api_playlist_hint
            DataApiExplorerMode.CHANNEL -> R.string.youtube_data_api_channel_hint
        }

        OutlinedTextField(
            value = uiState.inputText,
            onValueChange = viewModel::setInputText,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(hintRes),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    viewModel.execute()
                },
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = CardSurface,
                unfocusedContainerColor = CardSurface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("youtube_data_api_input"),
        )

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                focusManager.clearFocus()
                viewModel.execute()
            },
            enabled = !uiState.isLoading && uiState.inputText.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("youtube_data_api_fetch_button"),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = stringResource(R.string.youtube_data_api_run_action),
                fontWeight = FontWeight.SemiBold,
            )
        }

        val errorMsg = uiState.errorMessage
        if (!errorMsg.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = errorMsg,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        uiState.playlistHeader?.let { header ->
            Spacer(Modifier.height(14.dp))
            Surface(
                color = CardSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp),
                ) {
                    AsyncImage(
                        model = header.thumbnail,
                        contentDescription = header.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = header.title,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val meta = listOfNotNull(
                            header.author?.name,
                            header.songCountText,
                        ).joinToString(" · ")
                        if (meta.isNotEmpty()) {
                            Text(
                                text = meta,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        if (uiState.tracks.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            val queueTitle = uiState.playlistHeader?.title
                ?: uiState.activeTargetId.ifBlank { "YouTube Data API" }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = {
                        playerConnection?.playQueue(
                            ListQueue(
                                title = queueTitle,
                                items = uiState.tracks.map { it.toMediaItem() },
                                startIndex = 0,
                            ),
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("youtube_data_api_play_all_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.youtube_data_api_play_all))
                }

                OutlinedButton(
                    onClick = {
                        playerConnection?.addToQueue(uiState.tracks.map { it.toMediaItem() })
                        Toast.makeText(
                            context,
                            context.getString(R.string.youtube_data_api_queued_toast, uiState.tracks.size),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("youtube_data_api_queue_all_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.youtube_data_api_queue_all))
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.tracks.forEachIndexed { index, song ->
                    DataApiTrackRow(
                        song = song,
                        onClick = {
                            playerConnection?.playQueue(
                                ListQueue(
                                    title = queueTitle,
                                    items = uiState.tracks.map { it.toMediaItem() },
                                    startIndex = index,
                                ),
                            )
                        },
                    )
                }
            }

            if (uiState.nextPageToken != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = viewModel::loadMore,
                    enabled = !uiState.isLoadingMore,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("youtube_data_api_load_more_button"),
                ) {
                    if (uiState.isLoadingMore) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.youtube_data_api_load_more))
                }
            }
        } else if (uiState.hasExecuted && !uiState.isLoading && uiState.errorMessage == null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No music tracks matched this request.",
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun ApiStatusCard(
    isKeyConfigured: Boolean,
    packageName: String,
    sha1Fingerprint: String,
    onCopySha1: () -> Unit,
) {
    Surface(
        color = CardSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isKeyConfigured) {
                StatusGreen.copy(alpha = 0.28f)
            } else {
                StatusAmber.copy(alpha = 0.35f)
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isKeyConfigured) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = if (isKeyConfigured) StatusGreen else StatusAmber,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (isKeyConfigured) R.string.youtube_data_api_configured
                        else R.string.youtube_data_api_missing_key,
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "${stringResource(R.string.youtube_data_api_package_label)}: $packageName",
                color = Color.White.copy(alpha = 0.72f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "${stringResource(R.string.youtube_data_api_sha1_label)}: $sha1Fingerprint",
                    color = Color.White.copy(alpha = 0.72f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onCopySha1,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("youtube_data_api_copy_sha1_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy SHA-1 fingerprint",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExplorerModeChip(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = CardSurface,
            labelColor = Color.White.copy(alpha = 0.75f),
            iconColor = Color.White.copy(alpha = 0.75f),
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
        ),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag(tag),
    )
}

@Composable
private fun DataApiTrackRow(
    song: SongItem,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("youtube_data_api_track_${song.id}"),
    ) {
        AsyncImage(
            model = song.thumbnail,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val artistName = song.artists.joinToString(", ") { it.name }.ifBlank { "YouTube" }
            Text(
                text = artistName,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val durationText = formatDurationSeconds(song.duration)
        if (durationText != null) {
            Text(
                text = durationText,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private fun formatDurationSeconds(totalSeconds: Int?): String? {
    if (totalSeconds == null || totalSeconds <= 0) return null
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
