package cn.gdou.edu.timetable.ui.theme

import androidx.compose.ui.graphics.Color

// GDOU Ocean Blue Light Theme
val primaryLight = Color(0xFF005A7D)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFC9E9FF)
val onPrimaryContainerLight = Color(0xFF001E2E)

val secondaryLight = Color(0xFF3E6373)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFD2E8F5)
val onSecondaryContainerLight = Color(0xFF102A36)

val tertiaryLight = Color(0xFF1E6D61)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFBDF0E6)
val onTertiaryContainerLight = Color(0xFF00201C)

val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF410002)

val backgroundLight = Color(0xFFF3F7FA)
val onBackgroundLight = Color(0xFF171C20)
val surfaceLight = Color(0xFFF3F7FA)
val onSurfaceLight = Color(0xFF171C20)
val surfaceVariantLight = Color(0xFFDDE5EC)
val onSurfaceVariantLight = Color(0xFF414C54)
val outlineLight = Color(0xFF727E87)
val outlineVariantLight = Color(0xFFC3CDD6)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFF8FAFC)
val surfaceContainerLight = Color(0xFFEDF2F6)
val surfaceContainerHighLight = Color(0xFFE6ECF1)

// GDOU Ocean Blue Dark Theme
val primaryDark = Color(0xFF83D0F9)
val onPrimaryDark = Color(0xFF00344B)
val primaryContainerDark = Color(0xFF004C6A)
val onPrimaryContainerDark = Color(0xFFC9E9FF)

val secondaryDark = Color(0xFFB3CBD9)
val onSecondaryDark = Color(0xFF1D3440)
val secondaryContainerDark = Color(0xFF2D4958)
val onSecondaryContainerDark = Color(0xFFD2E8F5)

val tertiaryDark = Color(0xFF88D4C7)
val onTertiaryDark = Color(0xFF003730)
val tertiaryContainerDark = Color(0xFF005046)
val onTertiaryContainerDark = Color(0xFFBDF0E6)

val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)

val backgroundDark = Color(0xFF0E1418)
val onBackgroundDark = Color(0xFFE0E5EA)
val surfaceDark = Color(0xFF0E1418)
val onSurfaceDark = Color(0xFFE0E5EA)
val surfaceVariantDark = Color(0xFF3E4951)
val onSurfaceVariantDark = Color(0xFFBDC8D1)
val outlineDark = Color(0xFF88939C)
val outlineVariantDark = Color(0xFF3E4951)
val surfaceContainerLowestDark = Color(0xFF0A0F12)
val surfaceContainerLowDark = Color(0xFF161C20)
val surfaceContainerDark = Color(0xFF1B2227)
val surfaceContainerHighDark = Color(0xFF252D33)

// Semantic status accents
val AccentSuccess = Color(0xFF15803D)
val AccentWarning = Color(0xFFD97706)
val AccentInfo = Color(0xFF0284C7)
val AccentDanger = Color(0xFFDC2626)

data class CourseCardColor(
    val background: Color,
    val accent: Color,
    val content: Color
)

object CourseCardPalette {
    private val lightColors = listOf(
        CourseCardColor(Color(0xFFE0F2FE), Color(0xFF0284C7), Color(0xFF0C4A6E)),
        CourseCardColor(Color(0xFFDCFCE7), Color(0xFF16A34A), Color(0xFF14532D)),
        CourseCardColor(Color(0xFFFEF3C7), Color(0xFFD97706), Color(0xFF78350F)),
        CourseCardColor(Color(0xFFF3E8FF), Color(0xFF9333EA), Color(0xFF581C87)),
        CourseCardColor(Color(0xFFFFE4E6), Color(0xFFE11D48), Color(0xFF881337)),
        CourseCardColor(Color(0xFFCCFBF1), Color(0xFF0D9488), Color(0xFF134E4A)),
        CourseCardColor(Color(0xFFE0E7FF), Color(0xFF4F46E5), Color(0xFF312E81)),
        CourseCardColor(Color(0xFFFFEDD5), Color(0xFFEA580C), Color(0xFF7C2D12)),
        CourseCardColor(Color(0xFFECFCCB), Color(0xFF65A30D), Color(0xFF365314)),
        CourseCardColor(Color(0xFFFCE7F3), Color(0xFFDB2777), Color(0xFF831843)),
        CourseCardColor(Color(0xFFCFFAFE), Color(0xFF0891B2), Color(0xFF164E63)),
        CourseCardColor(Color(0xFFEDE9FE), Color(0xFF7C3AED), Color(0xFF4C1D95))
    )

    private val darkColors = listOf(
        CourseCardColor(Color(0xFF133349), Color(0xFF38BDF8), Color(0xFFE0F2FE)),
        CourseCardColor(Color(0xFF143828), Color(0xFF4ADE80), Color(0xFFDCFCE7)),
        CourseCardColor(Color(0xFF3D2E12), Color(0xFFFBBF24), Color(0xFFFEF3C7)),
        CourseCardColor(Color(0xFF2F1D45), Color(0xFFC084FC), Color(0xFFF3E8FF)),
        CourseCardColor(Color(0xFF451927), Color(0xFFFB7185), Color(0xFFFFE4E6)),
        CourseCardColor(Color(0xFF113837), Color(0xFF2DD4BF), Color(0xFFCCFBF1)),
        CourseCardColor(Color(0xFF1E254D), Color(0xFF818CF8), Color(0xFFE0E7FF)),
        CourseCardColor(Color(0xFF442415), Color(0xFFFB923C), Color(0xFFFFEDD5)),
        CourseCardColor(Color(0xFF243814), Color(0xFFA3E635), Color(0xFFECFCCB)),
        CourseCardColor(Color(0xFF431A33), Color(0xFFF472B6), Color(0xFFFCE7F3)),
        CourseCardColor(Color(0xFF113542), Color(0xFF22D3EE), Color(0xFFCFFAFE)),
        CourseCardColor(Color(0xFF2A1F4B), Color(0xFFA78BFA), Color(0xFFEDE9FE))
    )

    fun colorFor(index: Int, darkTheme: Boolean): CourseCardColor {
        val list = if (darkTheme) darkColors else lightColors
        return list[((index % list.size) + list.size) % list.size]
    }
}

data class ThemePreset(
    val name: String,
    val argb: Long
)

object ThemeColorPresets {
    val presets: List<ThemePreset> = listOf(
        ThemePreset("深海蓝", 0xFF005A7DL),
        ThemePreset("啾比紫", 0xFF7B52D3L),
        ThemePreset("樱花粉", 0xFFD94A82L),
        ThemePreset("翡翠青", 0xFF0E8A78L),
        ThemePreset("落日橙", 0xFFD96B27L),
        ThemePreset("薄荷绿", 0xFF2B8A3EL),
        ThemePreset("星河靛", 0xFF3F51B5L),
        ThemePreset("莓果红", 0xFFC6284FL),
        ThemePreset("暖琥珀", 0xFFC2820AL),
        ThemePreset("莫兰迪灰", 0xFF546E7AL)
    )
}

/**
 * Vivid multi-stop rainbow/aurora gradients exclusively for exam blocks on the Timetable grid
 * so exams stand out unmistakably from regular pastel course cards.
 */
object ExamGradientPalette {
    private val lightGradients: List<List<Color>> = listOf(
        listOf(Color(0xFFFF4B6E), Color(0xFFFF7A59), Color(0xFFFFB74D)), // 珊瑚霞光
        listOf(Color(0xFF6A11CB), Color(0xFFB721FF), Color(0xFFFF5E9C)), // 极光虹紫
        listOf(Color(0xFF0099F7), Color(0xFF5E60CE), Color(0xFFB5179E)), // 幻彩星云
        listOf(Color(0xFF00B09B), Color(0xFF26C6DA), Color(0xFF66BB6A)), // 霓虹翡翠
        listOf(Color(0xFFEC008C), Color(0xFFFC6767), Color(0xFFFFA751)), // 玫瑰晚霞
        listOf(Color(0xFF3A7BD5), Color(0xFF00D2FF), Color(0xFF9D50BB))  // 琉璃极光
    )

    private val darkGradients: List<List<Color>> = listOf(
        listOf(Color(0xFFD8315B), Color(0xFFE05A3B), Color(0xFFD98E2B)),
        listOf(Color(0xFF5A10B0), Color(0xFF9B1CE0), Color(0xFFD94480)),
        listOf(Color(0xFF0077C2), Color(0xFF4C4EB0), Color(0xFF961382)),
        listOf(Color(0xFF008C7A), Color(0xFF199EB0), Color(0xFF4A9E4E)),
        listOf(Color(0xFFC40074), Color(0xFFD94E4E), Color(0xFFD98236)),
        listOf(Color(0xFF2D62AD), Color(0xFF009EC4), Color(0xFF7D3F96))
    )

    fun colorsFor(index: Int, darkTheme: Boolean): List<Color> {
        val list = if (darkTheme) darkGradients else lightGradients
        return list[((index % list.size) + list.size) % list.size]
    }
}

data class StickyNoteStyle(
    val name: String,
    val paper: Color,
    val topTape: Color,
    val foldCorner: Color,
    val border: Color,
    val ink: Color
)

/**
 * Pastel paper sticky-note palette for user-created custom schedule events on the 日程 grid.
 */
object StickyNotePalette {
    val lightStyles: List<StickyNoteStyle> = listOf(
        StickyNoteStyle("柠檬黄", Color(0xFFFFF59D), Color(0xFFF5DA58), Color(0xFFE5C440), Color(0xFFE8CA4A), Color(0xFF4F3E00)),
        StickyNoteStyle("蜜桃粉", Color(0xFFFFD6E0), Color(0xFFF7A6BA), Color(0xFFE589A0), Color(0xFFEC96AC), Color(0xFF661830)),
        StickyNoteStyle("薄荷青", Color(0xFFC5F6E4), Color(0xFF7FDDBE), Color(0xFF5DC4A1), Color(0xFF6CD1AE), Color(0xFF0E4A37)),
        StickyNoteStyle("晴空蓝", Color(0xFFCDE9FF), Color(0xFF8BC8FA), Color(0xFF6BB0EC), Color(0xFF7BBDF4), Color(0xFF0F3D66)),
        StickyNoteStyle("芋泥紫", Color(0xFFE7DAFF), Color(0xFFC0A3FA), Color(0xFFA684EA), Color(0xFFB393F2), Color(0xFF391C6B)),
        StickyNoteStyle("奶杏橙", Color(0xFFFFE2C2), Color(0xFFF8BA78), Color(0xFFE39E55), Color(0xFFECA861), Color(0xFF633008)),
        StickyNoteStyle("抹茶绿", Color(0xFFE1F5C4), Color(0xFFB6DE7F), Color(0xFF9AC75E), Color(0xFFA7D36C), Color(0xFF2F4A0D)),
        StickyNoteStyle("奶油白", Color(0xFFFFFBE8), Color(0xFFEADDB8), Color(0xFFD7C79E), Color(0xFFDCCEA8), Color(0xFF3D3424))
    )

    private val darkStyles: List<StickyNoteStyle> = listOf(
        StickyNoteStyle("柠檬黄", Color(0xFF4A411A), Color(0xFF695B22), Color(0xFF8C792C), Color(0xFF9E8A38), Color(0xFFFFF5B8)),
        StickyNoteStyle("蜜桃粉", Color(0xFF4F2030), Color(0xFF732E45), Color(0xFF943D5A), Color(0xFFA84C6B), Color(0xFFFFDCE5)),
        StickyNoteStyle("薄荷青", Color(0xFF154538), Color(0xFF1E6652), Color(0xFF2B856B), Color(0xFF36997D), Color(0xFFCCF7E8)),
        StickyNoteStyle("晴空蓝", Color(0xFF163A57), Color(0xFF20547D), Color(0xFF2C6E9F), Color(0xFF3B82B8), Color(0xFFD4ECFF)),
        StickyNoteStyle("芋泥紫", Color(0xFF342254), Color(0xFF4D327A), Color(0xFF66449E), Color(0xFF7A56B5), Color(0xFFEBDFFF)),
        StickyNoteStyle("奶杏橙", Color(0xFF4D2E14), Color(0xFF70421C), Color(0xFF945827), Color(0xFFA86936), Color(0xFFFFE6CC)),
        StickyNoteStyle("抹茶绿", Color(0xFF2C4216), Color(0xFF416120), Color(0xFF57802C), Color(0xFF689438), Color(0xFFE6F7CE)),
        StickyNoteStyle("奶油白", Color(0xFF3D382B), Color(0xFF57503E), Color(0xFF706751), Color(0xFF827860), Color(0xFFFFF8E1))
    )

    fun styleFor(index: Int, darkTheme: Boolean): StickyNoteStyle {
        val list = if (darkTheme) darkStyles else lightStyles
        return list[((index % list.size) + list.size) % list.size]
    }
}
