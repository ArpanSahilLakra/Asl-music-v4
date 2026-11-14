package androidx.compose.material.icons.automirrored.rounded

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

val Icons.AutoMirrored.Rounded.OpenInNew: ImageVector
    get() = _openInNew ?: materialIcon(name = "AutoMirrored.Rounded.OpenInNew", autoMirror = true) {
        materialPath {
            moveTo(19f, 19f)
            horizontalLineTo(5f)
            verticalLineTo(5f)
            horizontalLineToRelative(7f)
            verticalLineTo(3f)
            horizontalLineTo(5f)
            curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(14f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineToRelative(-7f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(7f)
            close()
            moveTo(14f, 3f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(3.59f)
            lineToRelative(-9.83f, 9.83f)
            lineToRelative(1.41f, 1.41f)
            lineTo(19f, 6.41f)
            verticalLineTo(10f)
            horizontalLineToRelative(2f)
            verticalLineTo(3f)
            horizontalLineToRelative(-7f)
            close()
        }
    }.also { _openInNew = it }
private var _openInNew: ImageVector? = null

val Icons.AutoMirrored.Rounded.PlaylistPlay: ImageVector
    get() = _playlistPlay ?: materialIcon(name = "AutoMirrored.Rounded.PlaylistPlay", autoMirror = true) {
        materialPath {
            moveTo(3f, 10f)
            horizontalLineToRelative(11f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            verticalLineToRelative(-2f)
            close()
            moveTo(3f, 6f)
            horizontalLineToRelative(11f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            verticalLineTo(6f)
            close()
            moveTo(3f, 14f)
            horizontalLineToRelative(7f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            verticalLineToRelative(-2f)
            close()
            moveTo(16f, 13f)
            verticalLineToRelative(8f)
            lineToRelative(6f, -4f)
            lineToRelative(-6f, -4f)
            close()
        }
    }.also { _playlistPlay = it }
private var _playlistPlay: ImageVector? = null
