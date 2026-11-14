package androidx.compose.material.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

private fun batteryIcon(name: String, fillTopY: Float): ImageVector = materialIcon(name = name) {
    materialPath {
        moveTo(17f, 5f)
        verticalLineToRelative(16f)
        curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
        horizontalLineTo(8f)
        curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
        verticalLineTo(5f)
        curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f)
        horizontalLineToRelative(2f)
        verticalLineTo(2f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(2f)
        curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
        close()
        if (fillTopY > 6f) {
            moveTo(15f, 6f)
            horizontalLineTo(9f)
            verticalLineToRelative(fillTopY - 6f)
            horizontalLineToRelative(6f)
            verticalLineTo(6f)
            close()
        }
    }
}

val Icons.Filled.Battery1Bar: ImageVector
    get() = _b1 ?: batteryIcon("Filled.Battery1Bar", 18f).also { _b1 = it }
private var _b1: ImageVector? = null

val Icons.Filled.Battery2Bar: ImageVector
    get() = _b2 ?: batteryIcon("Filled.Battery2Bar", 16f).also { _b2 = it }
private var _b2: ImageVector? = null

val Icons.Filled.Battery4Bar: ImageVector
    get() = _b4 ?: batteryIcon("Filled.Battery4Bar", 12f).also { _b4 = it }
private var _b4: ImageVector? = null

val Icons.Filled.Battery6Bar: ImageVector
    get() = _b6 ?: batteryIcon("Filled.Battery6Bar", 8f).also { _b6 = it }
private var _b6: ImageVector? = null

val Icons.Filled.BatteryFull: ImageVector
    get() = _bf ?: batteryIcon("Filled.BatteryFull", 6f).also { _bf = it }
private var _bf: ImageVector? = null

val Icons.Filled.Bluetooth: ImageVector
    get() = _bt ?: materialIcon(name = "Filled.Bluetooth") {
        materialPath {
            moveTo(17.71f, 7.71f)
            lineTo(12f, 2f)
            horizontalLineToRelative(-1f)
            verticalLineToRelative(7.59f)
            lineTo(6.41f, 5f)
            lineTo(5f, 6.41f)
            lineTo(10.59f, 12f)
            lineTo(5f, 17.59f)
            lineTo(6.41f, 19f)
            lineTo(11f, 14.41f)
            verticalLineTo(22f)
            horizontalLineToRelative(1f)
            lineToRelative(5.71f, -5.71f)
            lineToRelative(-4.3f, -4.29f)
            lineToRelative(4.3f, -4.29f)
            close()
        }
    }.also { _bt = it }
private var _bt: ImageVector? = null

val Icons.Filled.Error: ImageVector
    get() = _err ?: materialIcon(name = "Filled.Error") {
        materialPath {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
            reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
            reflectiveCurveTo(17.52f, 2f, 12f, 2f)
            close()
            moveTo(13f, 17f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            close()
            moveTo(13f, 13f)
            horizontalLineToRelative(-2f)
            verticalLineTo(7f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(6f)
            close()
        }
    }.also { _err = it }
private var _err: ImageVector? = null

val Icons.Filled.ExpandMore: ImageVector
    get() = _expandMore ?: materialIcon(name = "Filled.ExpandMore") {
        materialPath {
            moveTo(16.59f, 8.59f)
            lineTo(12f, 13.17f)
            lineTo(7.41f, 8.59f)
            lineTo(6f, 10f)
            lineToRelative(6f, 6f)
            lineToRelative(6f, -6f)
            close()
        }
    }.also { _expandMore = it }
private var _expandMore: ImageVector? = null

val Icons.Filled.Headphones: ImageVector
    get() = _headphones ?: materialIcon(name = "Filled.Headphones") {
        materialPath {
            moveTo(12f, 3f)
            curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
            verticalLineToRelative(7f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(-8f)
            horizontalLineTo(5f)
            verticalLineToRelative(-1f)
            curveToRelative(0f, -3.87f, 3.13f, -7f, 7f, -7f)
            reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
            verticalLineToRelative(1f)
            horizontalLineToRelative(-4f)
            verticalLineToRelative(8f)
            horizontalLineToRelative(4f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineToRelative(-7f)
            curveToRelative(0f, -4.97f, -4.03f, -9f, -9f, -9f)
            close()
        }
    }.also { _headphones = it }
private var _headphones: ImageVector? = null

val Icons.Filled.MusicNote: ImageVector
    get() = _musicNote ?: materialIcon(name = "Filled.MusicNote") {
        materialPath {
            moveTo(12f, 3f)
            verticalLineToRelative(10.55f)
            curveToRelative(-0.59f, -0.34f, -1.27f, -0.55f, -2f, -0.55f)
            curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
            reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
            reflectiveCurveToRelative(4f, -1.79f, 4f, -4f)
            verticalLineTo(7f)
            horizontalLineToRelative(4f)
            verticalLineTo(3f)
            horizontalLineToRelative(-6f)
            close()
        }
    }.also { _musicNote = it }
private var _musicNote: ImageVector? = null

val Icons.Filled.PhoneAndroid: ImageVector
    get() = _phoneAndroid ?: materialIcon(name = "Filled.PhoneAndroid") {
        materialPath {
            moveTo(16f, 1f)
            horizontalLineTo(8f)
            curveTo(6.34f, 1f, 5f, 2.34f, 5f, 4f)
            verticalLineToRelative(16f)
            curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
            horizontalLineToRelative(8f)
            curveToRelative(1.66f, 0f, 3f, -1.34f, 3f, -3f)
            verticalLineTo(4f)
            curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
            close()
            moveTo(14f, 21f)
            horizontalLineToRelative(-4f)
            verticalLineToRelative(-1f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(1f)
            close()
            moveTo(17.25f, 18f)
            horizontalLineTo(6.75f)
            verticalLineTo(4f)
            horizontalLineToRelative(10.5f)
            verticalLineToRelative(14f)
            close()
        }
    }.also { _phoneAndroid = it }
private var _phoneAndroid: ImageVector? = null

val Icons.Filled.Speaker: ImageVector
    get() = _speaker ?: materialIcon(name = "Filled.Speaker") {
        materialPath {
            moveTo(17f, 2f)
            horizontalLineTo(7f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(16f)
            curveToRelative(0f, 1.1f, 0.9f, 1.99f, 2f, 1.99f)
            lineTo(17f, 22f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(4f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(12f, 19f)
            curveToRelative(-2.21f, 0f, -4f, -1.79f, -4f, -4f)
            reflectiveCurveToRelative(1.79f, -4f, 4f, -4f)
            reflectiveCurveToRelative(4f, 1.79f, 4f, 4f)
            reflectiveCurveToRelative(-1.79f, 4f, -4f, 4f)
            close()
        }
    }.also { _speaker = it }
private var _speaker: ImageVector? = null

val Icons.Filled.Tune: ImageVector
    get() = _tune ?: materialIcon(name = "Filled.Tune") {
        materialPath {
            moveTo(3f, 17f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(6f)
            verticalLineToRelative(-2f)
            horizontalLineTo(3f)
            close()
            moveTo(3f, 5f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(10f)
            verticalLineTo(5f)
            horizontalLineTo(3f)
            close()
            moveTo(13f, 21f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(8f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-8f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(2f)
            close()
            moveTo(7f, 9f)
            verticalLineToRelative(2f)
            horizontalLineTo(3f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(4f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(2f)
            verticalLineTo(9f)
            horizontalLineTo(7f)
            close()
            moveTo(21f, 13f)
            verticalLineToRelative(-2f)
            horizontalLineTo(11f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(10f)
            close()
            moveTo(15f, 9f)
            horizontalLineToRelative(2f)
            verticalLineTo(7f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            horizontalLineToRelative(-4f)
            verticalLineTo(3f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(6f)
            close()
        }
    }.also { _tune = it }
private var _tune: ImageVector? = null

val Icons.Filled.Tv: ImageVector
    get() = _tv ?: materialIcon(name = "Filled.Tv") {
        materialPath {
            moveTo(21f, 3f)
            horizontalLineTo(3f)
            curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
            verticalLineToRelative(12f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(5f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(8f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(5f)
            curveToRelative(1.1f, 0f, 1.99f, -0.9f, 1.99f, -2f)
            lineTo(23f, 5f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
            close()
            moveTo(21f, 17f)
            horizontalLineTo(3f)
            verticalLineTo(5f)
            horizontalLineToRelative(18f)
            verticalLineToRelative(12f)
            close()
        }
    }.also { _tv = it }
private var _tv: ImageVector? = null

val Icons.Filled.Usb: ImageVector
    get() = _usb ?: materialIcon(name = "Filled.Usb") {
        materialPath {
            moveTo(15f, 7f)
            verticalLineToRelative(4f)
            horizontalLineToRelative(1f)
            verticalLineToRelative(2f)
            horizontalLineToRelative(-3f)
            verticalLineTo(5f)
            horizontalLineToRelative(2f)
            lineToRelative(-3f, -4f)
            lineToRelative(-3f, 4f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(8f)
            horizontalLineTo(8f)
            verticalLineToRelative(-2.07f)
            curveToRelative(0.7f, -0.37f, 1.2f, -1.08f, 1.2f, -1.93f)
            curveToRelative(0f, -1.21f, -0.99f, -2.2f, -2.2f, -2.2f)
            reflectiveCurveTo(4.8f, 7.79f, 4.8f, 9f)
            curveToRelative(0f, 0.85f, 0.5f, 1.56f, 1.2f, 1.93f)
            verticalLineTo(13f)
            curveToRelative(0f, 1.11f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(3.05f)
            curveToRelative(-0.71f, 0.37f, -1.2f, 1.1f, -1.2f, 1.95f)
            curveToRelative(0f, 1.22f, 0.99f, 2.2f, 2.2f, 2.2f)
            reflectiveCurveToRelative(2.2f, -0.98f, 2.2f, -2.2f)
            curveToRelative(0f, -0.85f, -0.49f, -1.58f, -1.2f, -1.95f)
            verticalLineTo(15f)
            horizontalLineToRelative(3f)
            curveToRelative(1.11f, 0f, 2f, -0.89f, 2f, -2f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(1f)
            verticalLineTo(7f)
            horizontalLineToRelative(-4f)
            close()
        }
    }.also { _usb = it }
private var _usb: ImageVector? = null

val Icons.Filled.VolumeOff: ImageVector
    get() = _volOff ?: materialIcon(name = "Filled.VolumeOff") {
        materialPath {
            moveTo(16.5f, 12f)
            curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
            verticalLineToRelative(2.21f)
            lineToRelative(2.45f, 2.45f)
            curveToRelative(0.03f, -0.2f, 0.05f, -0.41f, 0.05f, -0.63f)
            close()
            moveTo(19f, 12f)
            curveToRelative(0f, 0.94f, -0.2f, 1.82f, -0.54f, 2.64f)
            lineToRelative(1.51f, 1.51f)
            curveTo(20.63f, 14.91f, 21f, 13.5f, 21f, 12f)
            curveToRelative(0f, -4.28f, -2.99f, -7.86f, -7f, -8.77f)
            verticalLineToRelative(2.06f)
            curveToRelative(2.89f, 0.86f, 5f, 3.54f, 5f, 6.71f)
            close()
            moveTo(4.27f, 3f)
            lineTo(3f, 4.27f)
            lineTo(7.73f, 9f)
            horizontalLineTo(3f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(4f)
            lineToRelative(5f, 5f)
            verticalLineToRelative(-6.73f)
            lineToRelative(4.25f, 4.25f)
            curveToRelative(-0.67f, 0.52f, -1.42f, 0.93f, -2.25f, 1.18f)
            verticalLineToRelative(2.06f)
            curveToRelative(1.38f, -0.31f, 2.63f, -0.95f, 3.69f, -1.81f)
            lineTo(19.73f, 21f)
            lineTo(21f, 19.73f)
            lineToRelative(-9f, -9f)
            lineTo(4.27f, 3f)
            close()
            moveTo(12f, 4f)
            lineTo(9.91f, 6.09f)
            lineTo(12f, 8.18f)
            verticalLineTo(4f)
            close()
        }
    }.also { _volOff = it }
private var _volOff: ImageVector? = null

val Icons.Filled.VolumeUp: ImageVector
    get() = _volUp ?: materialIcon(name = "Filled.VolumeUp") {
        materialPath {
            moveTo(3f, 9f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(4f)
            lineToRelative(5f, 5f)
            verticalLineTo(4f)
            lineTo(7f, 9f)
            horizontalLineTo(3f)
            close()
            moveTo(16.5f, 12f)
            curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
            verticalLineToRelative(8.05f)
            curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
            close()
            moveTo(14f, 3.23f)
            verticalLineToRelative(2.06f)
            curveToRelative(2.89f, 0.86f, 5f, 3.54f, 5f, 6.71f)
            reflectiveCurveToRelative(-2.11f, 5.85f, -5f, 6.71f)
            verticalLineToRelative(2.06f)
            curveToRelative(4.01f, -0.91f, 7f, -4.49f, 7f, -8.77f)
            reflectiveCurveToRelative(-2.99f, -7.86f, -7f, -8.77f)
            close()
        }
    }.also { _volUp = it }
private var _volUp: ImageVector? = null
