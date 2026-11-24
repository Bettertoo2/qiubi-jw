package cn.gdou.edu.timetable.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.gdou.edu.timetable.data.local.AppSettings
import cn.gdou.edu.timetable.ui.theme.LocalIsDarkTheme

data class CustomNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/**
 * Custom frosted-glass navigation bar with real-time GPU backdrop blur (磨砂度)
 * and smooth transparency control (透明度), supporting both floating capsule and docked glass styles.
 */
@Composable
fun CustomFrostedBottomBar(
    tabs: List<CustomNavTab>,
    selectedIndex: Int,
    onSelectTab: (Int) -> Unit,
    transparency: Float, // 0f = opaque, 1f = fully transparent
    frost: Float,        // 0f = crisp, 1f = strong frosted glass blur
    style: String,       // FLOATING | DOCKED
    backdropLayer: GraphicsLayer,
    contentPositionInRoot: Offset,
    modifier: Modifier = Modifier
) {
    val darkTheme = LocalIsDarkTheme.current
    val isFloating = style != AppSettings.BOTTOM_BAR_STYLE_DOCKED
    val clampedTransparency = transparency.coerceIn(0f, 1f)
    val clampedFrost = frost.coerceIn(0f, 1f)
    val opacity = 1f - clampedTransparency
    val blurRadiusDp = (clampedFrost * 28f).dp

    val barShape = if (isFloating) {
        RoundedCornerShape(28.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    }

    var barPositionInRoot by remember { mutableStateOf(Offset.Zero) }

    val baseSurface = if (darkTheme) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val glassHighlight = if (darkTheme) Color.White else Color.White
    val borderTopAlpha = ((0.65f * (0.25f + 0.75f * clampedFrost)) * (0.22f + 0.78f * opacity)).coerceIn(0f, 0.75f)
    val borderBottomAlpha = ((0.30f * (0.25f + 0.75f * clampedFrost)) * (0.18f + 0.82f * opacity)).coerceIn(0f, 0.42f)
    // Only apply Material drop shadow when nearly opaque; translucent glass avoids dark shadow muddying the backdrop
    val shadowDp = if (isFloating && opacity > 0.85f) ((opacity - 0.85f) / 0.15f * 8f).dp else 0.dp

    val outerModifier = if (isFloating) {
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .then(if (shadowDp > 0.dp) Modifier.shadow(elevation = shadowDp, shape = barShape, clip = false) else Modifier)
            .clip(barShape)
            .height(66.dp)
    } else {
        modifier
            .fillMaxWidth()
            .clip(barShape)
    }

    Box(
        modifier = outerModifier
            .onGloballyPositioned { barPositionInRoot = it.positionInRoot() }
    ) {
        // Layer 1: Live blurred backdrop sampled directly from sibling content's GraphicsLayer
        if (clampedFrost > 0.01f && opacity < 0.99f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(radius = blurRadiusDp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .drawWithCache {
                        onDrawBehind {
                            if (backdropLayer.size.width > 0 && backdropLayer.size.height > 0) {
                                val dx = contentPositionInRoot.x - barPositionInRoot.x
                                val dy = contentPositionInRoot.y - barPositionInRoot.y
                                translate(left = dx, top = dy) {
                                    drawLayer(backdropLayer)
                                }
                            }
                        }
                    }
            )
        }

        // Layer 2: Translucent surface tint + delicate frosted specular sheen + glass rim border
        val frostSheenTop = (clampedFrost * (if (darkTheme) 0.12f else 0.24f) * (0.25f + 0.75f * opacity)).coerceIn(0f, 0.35f)
        val frostSheenBottom = (clampedFrost * (if (darkTheme) 0.04f else 0.08f) * (0.25f + 0.75f * opacity)).coerceIn(0f, 0.15f)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(baseSurface.copy(alpha = opacity))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            glassHighlight.copy(alpha = frostSheenTop),
                            glassHighlight.copy(alpha = frostSheenBottom)
                        )
                    )
                )
                .then(
                    if (borderTopAlpha > 0.02f) {
                        Modifier.border(
                            width = 0.9.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    glassHighlight.copy(alpha = borderTopAlpha),
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderBottomAlpha)
                                )
                            ),
                            shape = barShape
                        )
                    } else Modifier
                )
        )

        // Layer 3: Sharp, crisp custom navigation items
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFloating) Modifier.fillMaxHeight()
                    else Modifier.navigationBarsPadding().height(68.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val pillAlpha = (0.38f + 0.48f * opacity).coerceIn(0.32f, 0.9f)
                val pillColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = pillAlpha)
                    } else Color.Transparent,
                    label = "customNavPill_$index"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = if (opacity < 0.3f) 0.85f else 0.68f)
                    },
                    label = "customNavContent_$index"
                )

                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 3.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(pillColor)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onSelectTab(index) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            tint = contentColor,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    val s = if (selected) 1.08f else 1f
                                    scaleX = s
                                    scaleY = s
                                }
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
