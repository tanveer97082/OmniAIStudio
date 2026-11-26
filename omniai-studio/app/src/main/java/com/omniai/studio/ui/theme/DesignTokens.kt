package com.omniai.studio.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Unified v5.1 Design System tokens and reusable primitives for OmniAI-Studio.
 *
 * Surface Hierarchy (AMOLED / Dark Default):
 * - Canvas Background: #09090C
 * - Primary Card Surface (Level 1): #13131A with 1dp subtle border #232330
 * - Elevated Input / Sheet Surface (Level 2): #1B1B25
 * - Accent Highlights: Dynamic user-chosen accent with 12% alpha containers for chips/badges
 *
 * Strict Spacing Grid:
 * - Screen horizontal padding: 16dp
 * - Vertical section gap: 20dp
 * - Card inner padding: 16dp
 * - Minimum touch target: 48dp
 * - Max 2 primary actions visible per card (secondary actions in overflow DropdownMenu)
 */
object OmniDesignTokens {
    // Surface Hierarchy
    val CanvasBackground = Color(0xFF09090C)
    val CardSurfaceLevel1 = Color(0xFF13131A)
    val CardBorderSubtle = Color(0xFF232330)
    val ElevatedSurfaceLevel2 = Color(0xFF1B1B25)
    val SubtleDivider = Color(0xFF232330)

    // Semantic Status Colors
    val StatusSuccess = EmeraldAccent
    val StatusWarning = AmberWarning
    val StatusDanger = CrimsonDanger
    val StatusInfo = CyanSecondary

    // Strict Spacing Grid
    val ScreenHorizontalPadding: Dp = 16.dp
    val SectionVerticalGap: Dp = 20.dp
    val CardInnerPadding: Dp = 16.dp
    val ItemGap: Dp = 12.dp
    val CompactGap: Dp = 8.dp
    val MinTouchTarget: Dp = 48.dp
    val TopBarHeight: Dp = 56.dp

    // Radii & Borders
    val BorderWidth: Dp = 1.dp
    val CardRadius: Dp = 16.dp
    val SheetRadius: Dp = 24.dp
    val CapsuleRadius: Dp = 28.dp
    val ChipRadius: Dp = 12.dp

    // Dynamic Accent Alpha
    const val AccentContainerAlpha: Float = 0.12f
    const val AccentBorderAlpha: Float = 0.36f
}

/**
 * Returns the 12% alpha tinted container color for chips, badges, and icon wells.
 */
fun Color.omniAccentContainer(): Color = this.copy(alpha = OmniDesignTokens.AccentContainerAlpha)

/**
 * Returns the subtle accented border tint.
 */
fun Color.omniAccentBorder(): Color = this.copy(alpha = OmniDesignTokens.AccentBorderAlpha)

/**
 * Level 1 primary card enforcing #13131A surface (or active theme surface),
 * 1dp #232330 border, and 16dp internal padding.
 */
@Composable
fun OmniSurfaceCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    cornerRadius: Dp = OmniDesignTokens.CardRadius,
    contentPadding: Dp = OmniDesignTokens.CardInnerPadding,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    if (onClick != null) {
        ElevatedCard(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
        ) {
            Surface(
                color = Color.Transparent,
                shape = shape,
                border = BorderStroke(OmniDesignTokens.BorderWidth, borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(OmniDesignTokens.CompactGap),
                    content = content
                )
            }
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            border = BorderStroke(OmniDesignTokens.BorderWidth, borderColor)
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(OmniDesignTokens.CompactGap),
                content = content
            )
        }
    }
}

/**
 * Compact accent-tinted pill badge using the 12% alpha container rule.
 */
@Composable
fun OmniAccentBadge(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    leadingIcon: ImageVector? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
        color = accent.omniAccentContainer(),
        border = BorderStroke(OmniDesignTokens.BorderWidth, accent.omniAccentBorder())
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Section header with consistent typography and optional trailing action.
 */
@Composable
fun OmniSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * Overflow action item for keeping cards clean (max 2 visible primary actions).
 */
data class OmniOverflowAction(
    val label: String,
    val icon: ImageVector? = null,
    val isDestructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

/**
 * Standardized 48dp touch-target overflow menu button (`⋮`).
 */
@Composable
fun OmniOverflowMenuButton(
    actions: List<OmniOverflowAction>,
    contentDescription: String = "More options",
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.defaultMinSize(
                minWidth = OmniDesignTokens.MinTouchTarget,
                minHeight = OmniDesignTokens.MinTouchTarget
            )
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = contentDescription,
                tint = colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(colorScheme.surfaceVariant)
        ) {
            actions.forEach { item ->
                val tint = if (item.isDestructive) colorScheme.error else colorScheme.onSurface
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item.label,
                            color = if (item.enabled) tint else colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = item.icon?.let { icon ->
                        {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (item.enabled) tint else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    enabled = item.enabled,
                    onClick = {
                        expanded = false
                        item.onClick()
                    }
                )
            }
        }
    }
}

/**
 * Crisp, intentional empty state with centered icon well, 1-line helper text,
 * and single primary action CTA.
 */
@Composable
fun OmniEmptyState(
    icon: ImageVector,
    title: String,
    helperText: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OmniDesignTokens.CardRadius),
        color = colorScheme.surface,
        border = BorderStroke(OmniDesignTokens.BorderWidth, colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary.omniAccentContainer()),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!actionLabel.isNullOrBlank() && onActionClick != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onActionClick,
                    modifier = Modifier.defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                    shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/**
 * Lightweight Compose-only skeleton shimmer card for loading states (zero extra dependencies).
 */
@Composable
fun OmniSkeletonCard(
    modifier: Modifier = Modifier,
    lineCount: Int = 3
) {
    val transition = rememberInfiniteTransition(label = "omni_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val shimmerBrush = Brush.horizontalGradient(
        colors = listOf(
            baseColor.copy(alpha = alpha * 0.6f),
            baseColor.copy(alpha = alpha),
            baseColor.copy(alpha = alpha * 0.6f)
        )
    )
    OmniSurfaceCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(16.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(shimmerBrush)
        )
        repeat(lineCount.coerceIn(1, 5)) { index ->
            val widthFraction = if (index == lineCount - 1) 0.7f else 1f
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmerBrush)
            )
        }
    }
}
