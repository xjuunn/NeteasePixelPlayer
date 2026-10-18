package com.theveloper.pixelplay.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.viewmodel.ExternalServiceAccount
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.delay
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Entry for one cloud music source shown by [CloudSourceEntryRow].
 *
 * The icon and the brand color come from the same sources used by [StreamingProviderSheet], so a
 * provider keeps one identity everywhere it can be opened from.
 */
@Immutable
data class CloudSourceEntry(
    val service: ExternalServiceAccount,
    val name: String,
    @DrawableRes val iconRes: Int,
    val brandColor: Color,
    val isConnected: Boolean = false
)

/** Builds the entry for [service], resolving its icon and brand color. */
fun cloudSourceEntry(
    service: ExternalServiceAccount,
    name: String,
    isConnected: Boolean = false
): CloudSourceEntry = CloudSourceEntry(
    service = service,
    name = name,
    iconRes = when (service) {
        ExternalServiceAccount.TELEGRAM -> R.drawable.telegram
        ExternalServiceAccount.GOOGLE_DRIVE -> R.drawable.rounded_drive_export_24
        ExternalServiceAccount.NETEASE -> R.drawable.netease_cloud_music_logo_icon_206716__1_
        ExternalServiceAccount.QQ_MUSIC -> R.drawable.qq_music
        ExternalServiceAccount.NAVIDROME -> R.drawable.ic_navidrome_md3
        ExternalServiceAccount.JELLYFIN -> R.drawable.ic_jellyfin
    },
    brandColor = when (service) {
        ExternalServiceAccount.TELEGRAM -> Color(0xFF2AABEE)
        ExternalServiceAccount.GOOGLE_DRIVE -> Color(0xFF4285F4)
        ExternalServiceAccount.NETEASE -> Color(0xFFE85959)
        ExternalServiceAccount.QQ_MUSIC -> Color(0xFF31C27C)
        ExternalServiceAccount.NAVIDROME -> Color(0xFFE8A54B)
        ExternalServiceAccount.JELLYFIN -> Color(0xFF00A4DC)
    },
    isConnected = isConnected
)

/**
 * Centered, wrapping row of brand-colored entry chips for the supported cloud sources.
 *
 * Used by the Home empty state so a user without any local music gets obvious, tappable doors into
 * each source instead of a dead end. Chips fade and slide in one after another to keep the empty
 * state feeling light rather than static.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloudSourceEntryRow(
    entries: List<CloudSourceEntry>,
    onEntryClick: (CloudSourceEntry) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    maxItemsInEachRow: Int = 3
) {
    if (entries.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!hint.isNullOrBlank()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = maxItemsInEachRow
        ) {
            entries.forEachIndexed { index, entry ->
                CloudSourceEntryChip(
                    entry = entry,
                    entranceIndex = index,
                    onClick = { onEntryClick(entry) }
                )
            }
        }
    }
}

@Composable
private fun CloudSourceEntryChip(
    entry: CloudSourceEntry,
    entranceIndex: Int,
    onClick: () -> Unit
) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(entranceIndex) {
        delay(entranceIndex * 55L)
        entrance.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
        )
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.graphicsLayer {
            alpha = entrance.value
            translationY = (1f - entrance.value) * 24.dp.toPx()
        },
        shape = AbsoluteSmoothCornerShape(18.dp, 60),
        color = entry.brandColor.copy(alpha = 0.16f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, entry.brandColor.copy(alpha = 0.40f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                painter = painterResource(entry.iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = entry.brandColor
            )
            Text(
                text = entry.name,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (entry.isConnected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = entry.brandColor
                )
            }
        }
    }
}
