package cn.gdou.edu.timetable.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.gdou.edu.timetable.data.local.AppSettings
import cn.gdou.edu.timetable.ui.theme.ExamGradientPalette
import cn.gdou.edu.timetable.ui.theme.LocalIsDarkTheme
import cn.gdou.edu.timetable.ui.theme.StickyNotePalette
import cn.gdou.edu.timetable.ui.theme.ThemeColorPresets
import cn.gdou.edu.timetable.ui.theme.colorToHex
import cn.gdou.edu.timetable.ui.theme.hslToColor
import cn.gdou.edu.timetable.ui.theme.parseHexToArgb
import cn.gdou.edu.timetable.ui.theme.rgbToHsl
import cn.gdou.edu.timetable.ui.theme.themeHeroGradientColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ThemeCustomizerBottomSheet(
    currentSeedArgb: Long,
    currentDarkMode: String,
    bottomBarTransparency: Float,
    bottomBarFrost: Float,
    bottomBarStyle: String,
    onSelectColor: (Long) -> Unit,
    onSelectDarkMode: (String) -> Unit,
    onSetBottomBarTransparency: (Float) -> Unit,
    onSetBottomBarFrost: (Float) -> Unit,
    onSetBottomBarStyle: (String) -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialHsl = remember(currentSeedArgb) { rgbToHsl(Color(currentSeedArgb)) }
    var hue by remember { mutableFloatStateOf(initialHsl.first) }
    var saturation by remember { mutableFloatStateOf(initialHsl.second.coerceIn(0f, 1f)) }
    var lightness by remember { mutableFloatStateOf(initialHsl.third.coerceIn(0.20f, 0.72f)) }
    var hexInput by remember { mutableStateOf(colorToHex(currentSeedArgb)) }
    var hexError by remember { mutableStateOf(false) }

    LaunchedEffect(currentSeedArgb) {
        val (h, s, l) = rgbToHsl(Color(currentSeedArgb))
        hue = h
        saturation = s.coerceIn(0f, 1f)
        lightness = l.coerceIn(0.20f, 0.72f)
        hexInput = colorToHex(currentSeedArgb)
        hexError = false
    }

    fun applyHsl(h: Float, s: Float, l: Float) {
        val color = hslToColor(h, s, l)
        val argb = color.toArgb().toLong() and 0xFFFFFFFFL
        hexInput = colorToHex(argb)
        hexError = false
        onSelectColor(argb)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "自定义主题与配色",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "支持预设色板、自由调色盘和深浅色切换",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onResetDefault) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("恢复默认")
                }
            }

            // Live Theme Preview Banner
            val previewSeed = Color(currentSeedArgb)
            val heroBrush = Brush.linearGradient(
                themeHeroGradientColors(previewSeed, LocalIsDarkTheme.current)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(heroBrush)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "主题实时预览",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "当前主色 ${colorToHex(currentSeedArgb)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "已应用",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Appearance Mode (System / Light / Dark)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "外观模式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        Triple(AppSettings.THEME_MODE_SYSTEM, "跟随系统", Icons.Outlined.SettingsBrightness),
                        Triple(AppSettings.THEME_MODE_LIGHT, "浅色", Icons.Outlined.LightMode),
                        Triple(AppSettings.THEME_MODE_DARK, "深色", Icons.Outlined.DarkMode)
                    )
                    modes.forEach { (mode, label, icon) ->
                        FilterChip(
                            selected = currentDarkMode == mode,
                            onClick = { onSelectDarkMode(mode) },
                            leadingIcon = {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Preset Color Swatches
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "精选配色",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThemeColorPresets.presets.forEach { preset ->
                        val swatchColor = Color(preset.argb)
                        val selected = (currentSeedArgb and 0xFFFFFFL) == (preset.argb and 0xFFFFFFL)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectColor(preset.argb) }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Black.copy(alpha = 0.12f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = "已选",
                                        tint = if (swatchColor.luminance() > 0.55f) Color.Black else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Free Custom Color Sliders (Hue / Saturation / Lightness)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "自由调色盘",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                // Hue rainbow bar + slider
                ColorChannelSlider(
                    label = "色相",
                    valueText = "${hue.toInt()}°",
                    gradientColors = listOf(
                        Color(0xFFFF0000),
                        Color(0xFFFFFF00),
                        Color(0xFF00FF00),
                        Color(0xFF00FFFF),
                        Color(0xFF0000FF),
                        Color(0xFFFF00FF),
                        Color(0xFFFF0000)
                    ),
                    value = hue,
                    valueRange = 0f..360f,
                    onValueChange = {
                        hue = it
                        applyHsl(hue, saturation, lightness)
                    }
                )

                // Saturation slider
                ColorChannelSlider(
                    label = "饱和度",
                    valueText = "${(saturation * 100).toInt()}%",
                    gradientColors = listOf(
                        hslToColor(hue, 0f, lightness),
                        hslToColor(hue, 1f, lightness)
                    ),
                    value = saturation,
                    valueRange = 0f..1f,
                    onValueChange = {
                        saturation = it
                        applyHsl(hue, saturation, lightness)
                    }
                )

                // Lightness slider
                ColorChannelSlider(
                    label = "明暗度",
                    valueText = "${(lightness * 100).toInt()}%",
                    gradientColors = listOf(
                        hslToColor(hue, saturation, 0.20f),
                        hslToColor(hue, saturation, 0.46f),
                        hslToColor(hue, saturation, 0.72f)
                    ),
                    value = lightness,
                    valueRange = 0.20f..0.72f,
                    onValueChange = {
                        lightness = it
                        applyHsl(hue, saturation, lightness)
                    }
                )

                // Direct HEX code input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = {
                            hexInput = it.uppercase().take(7)
                            hexError = false
                        },
                        label = { Text("自定义色值 (HEX)") },
                        placeholder = { Text("#7B52D3") },
                        isError = hexError,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            val parsed = parseHexToArgb(hexInput)
                            if (parsed != null) {
                                hexError = false
                                onSelectColor(parsed)
                            } else {
                                hexError = true
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("应用")
                    }
                }
                if (hexError) {
                    Text(
                        text = "请输入 6 位十六进制色值，例如 #7B52D3",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            HorizontalDivider()

            // Custom Bottom Bar Transparency & Frost Section with Live Glass Preview
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "自定义底部栏样式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = bottomBarStyle != AppSettings.BOTTOM_BAR_STYLE_DOCKED,
                        onClick = { onSetBottomBarStyle(AppSettings.BOTTOM_BAR_STYLE_FLOATING) },
                        label = { Text("悬浮胶囊") }
                    )
                    FilterChip(
                        selected = bottomBarStyle == AppSettings.BOTTOM_BAR_STYLE_DOCKED,
                        onClick = { onSetBottomBarStyle(AppSettings.BOTTOM_BAR_STYLE_DOCKED) },
                        label = { Text("贴底玻璃") }
                    )
                }

                BottomBarGlassLivePreview(
                    transparency = bottomBarTransparency,
                    frost = bottomBarFrost,
                    style = bottomBarStyle
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "透明度（0% 不透明 ←→ 100% 全透明）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(bottomBarTransparency * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = bottomBarTransparency,
                        onValueChange = onSetBottomBarTransparency,
                        valueRange = 0f..1f
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "磨砂度（0% 清晰 ←→ 100% 强毛玻璃）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(bottomBarFrost * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = bottomBarFrost,
                        onValueChange = onSetBottomBarFrost,
                        valueRange = 0f..1f
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarGlassLivePreview(
    transparency: Float,
    frost: Float,
    style: String
) {
    val darkTheme = LocalIsDarkTheme.current
    val previewLayer = rememberGraphicsLayer()
    var bgPos by remember { mutableStateOf(Offset.Zero) }
    var barPos by remember { mutableStateOf(Offset.Zero) }
    val isFloating = style != AppSettings.BOTTOM_BAR_STYLE_DOCKED
    val opacity = (1f - transparency.coerceIn(0f, 1f))
    val blurDp = (frost.coerceIn(0f, 1f) * 28f).dp
    val barShape = if (isFloating) RoundedCornerShape(22.dp) else RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    val baseSurface = if (darkTheme) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // Colorful mock schedule cards behind the mini bottom bar
        Row(
            modifier = Modifier
                .matchParentSize()
                .onGloballyPositioned { bgPos = it.positionInRoot() }
                .drawWithContent {
                    previewLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(previewLayer)
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(ExamGradientPalette.colorsFor(0, darkTheme)))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StickyNotePalette.styleFor(0, darkTheme).paper)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(ExamGradientPalette.colorsFor(2, darkTheme)))
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StickyNotePalette.styleFor(2, darkTheme).paper)
            )
        }

        // Live frosted mini bottom bar preview
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = if (isFloating) 14.dp else 0.dp, vertical = if (isFloating) 10.dp else 0.dp)
                .height(48.dp)
                .clip(barShape)
                .onGloballyPositioned { barPos = it.positionInRoot() }
        ) {
            if (frost > 0.01f && opacity < 0.99f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(radius = blurDp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .drawWithCache {
                            onDrawBehind {
                                if (previewLayer.size.width > 0 && previewLayer.size.height > 0) {
                                    translate(bgPos.x - barPos.x, bgPos.y - barPos.y) {
                                        drawLayer(previewLayer)
                                    }
                                }
                            }
                        }
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(baseSurface.copy(alpha = opacity))
                    .border(
                        width = 0.8.dp,
                        color = Color.White.copy(alpha = (0.45f * (0.2f + 0.8f * frost)).coerceIn(0f, 0.6f)),
                        shape = barShape
                    )
            )
            Row(
                modifier = Modifier.matchParentSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("日程", "考试", "成绩绩点", "我的").forEachIndexed { i, title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorChannelSlider(
    label: String,
    valueText: String,
    gradientColors: List<Color>,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Brush.horizontalGradient(gradientColors))
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}
