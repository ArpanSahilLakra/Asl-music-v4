// about app and license sheets opened from the settings/profile page

package com.mochyy.mochymusic.ui.screens.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.pressBounce
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface

@Immutable
private data class LeadDevEntry(
    val name: String,
    val subtitle: String,
    val devSpaceIndex: Int,
    @DrawableRes val avatarRes: Int,
)

@Immutable
private data class LicenseEntry(
    val tag: String,
    val title: String,
    val subtitle: String,
    val url: String,
)

private val LeadDevs = listOf(
    LeadDevEntry(
        name = "Arpan",
        subtitle = "Lead Developer · ASL Beats",
        devSpaceIndex = 0,
        avatarRes = R.drawable.frame_51_3,
    ),
)

private val Licenses = listOf(
    LicenseEntry(
        tag = "GPL-3.0",
        title = "ASL Beats · GNU General Public License v3.0",
        subtitle = "Open source music streaming client",
        url = "https://www.gnu.org/licenses/gpl-3.0.en.html",
    ),
)

@Composable
internal fun SheetPrimaryButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.26f))
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    }
}

/** "8.1.0 build#1165" -> "8.1.0", "8.0.2 (1100)" -> "8.0.2" */
internal fun shortVersion(version: String): String = version.substringBefore(' ')

internal fun Context.openUrl(url: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

fun PopupSheetState.showAboutSheet(onOpenDevSpace: (Int) -> Unit = {}) {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = "Done", onClick = popup::dismiss) }) {
        AboutSheetContent(popup = popup, onOpenDevSpace = onOpenDevSpace)
    }
}

private fun PopupSheetState.showLicenseSheet() {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = "Done", onClick = popup::dismiss) }) {
        LicenseSheetContent()
    }
}

@Composable
private fun ColumnScope.AboutSheetContent(
    popup: PopupSheetState,
    onOpenDevSpace: (Int) -> Unit,
) {
    val installed = shortVersion(BuildConfig.VERSION_NAME)
    val scrollState = rememberScrollState()
    val ringScrollRangePx = with(LocalDensity.current) { 180.dp.toPx() }

    Box(Modifier.fillMaxWidth()) {
        // zero-size layout places the spinning ring behind the top icon without pushing the column down
        VersionRing(
            text = "ASL BEATS $installed",
            scrollProgress = { scrollState.value / ringScrollRangePx },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = 0,
                            minHeight = 0,
                            maxWidth = Constraints.Infinity,
                            maxHeight = Constraints.Infinity,
                        ),
                    )
                    layout(0, 0) {
                        placeable.place(x = -placeable.width / 2, y = (-166).dp.roundToPx())
                    }
                },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            Spacer(Modifier.height(18.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MenuRowSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mochy_mark),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "About app",
                    fontSize = 36.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.4).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "ASL Beats | $installed",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }

            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MenuRowSurface)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "ASL Beats — built by Arpan for\nseamless music streaming.",
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader("Developer")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MenuRowSurface)
                    .padding(vertical = 6.dp),
            ) {
                LeadDevs.forEach { dev ->
                    PersonRow(
                        name = dev.name,
                        subtitle = dev.subtitle,
                        avatarRes = dev.avatarRes,
                        showArrow = true,
                        onClick = { onOpenDevSpace(dev.devSpaceIndex) },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader("License")
            Spacer(Modifier.height(8.dp))
            AboutCardButton(
                text = "More about license",
                onClick = { popup.showLicenseSheet() },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ColumnScope.LicenseSheetContent() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "License",
            fontSize = 36.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.4).sp,
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Licenses.forEach { entry ->
                LicenseCard(
                    entry = entry,
                    onClick = { context.openUrl(entry.url) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
        color = Color.White.copy(alpha = 0.55f),
    )
}

@Composable
private fun AboutCardButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.97f)
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MenuRowSurface)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PersonRow(
    name: String,
    subtitle: String?,
    @DrawableRes avatarRes: Int,
    showArrow: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.98f)
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        DeveloperAvatar(
            avatarRes = avatarRes,
            size = 44.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.2).sp,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showArrow) {
            Spacer(Modifier.width(10.dp))
            ArrowBadge()
        }
    }
}

@Composable
private fun LicenseCard(
    entry: LicenseEntry,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.98f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MenuRowSurface)
            .clickable(interactionSource = interaction, indication = RemoteFocus, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.tag,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                color = Color.White.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.subtitle,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.2).sp,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.width(12.dp))
        ArrowBadge()
    }
}

@Composable
private fun ArrowBadge() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f)),
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_forward),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun DeveloperAvatar(
    @DrawableRes avatarRes: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFD9D9D9)),
    ) {
        Image(
            painter = painterResource(avatarRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
