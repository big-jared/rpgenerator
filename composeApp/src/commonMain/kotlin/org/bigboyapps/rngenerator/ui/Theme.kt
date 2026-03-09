package org.bigboyapps.rngenerator.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import com.rpgenerator.composeapp.generated.resources.Res
import com.rpgenerator.composeapp.generated.resources.alegreya_sans_bold
import com.rpgenerator.composeapp.generated.resources.alegreya_sans_regular
import com.rpgenerator.composeapp.generated.resources.medievalsharp_regular

// ── Colors ──────────────────────────────────────────────────────

object AppColors {
    // Primary
    val primary = Color(0xFF5C3D2E)        // rich brown
    val primaryLight = Color(0xFF8B6F4E)    // warm tan
    val accent = Color(0xFFC49A6C)          // gold accent

    // Background
    val background = Color(0xFFF5EFE0)      // warm parchment
    val surface = Color(0xFFEDE5D4)         // slightly darker parchment
    val surfaceCard = Color(0xFFE8DFC8)     // card surfaces

    // Text
    val textPrimary = Color(0xFF2C1810)     // deep brown-black
    val textSecondary = Color(0xFF6B5744)   // medium brown
    val textMuted = Color(0xFF9E8E7A)       // light brown

    // Semantic
    val hpRed = Color(0xFFC0392B)
    val energyBlue = Color(0xFF2980B9)
    val xpGreen = Color(0xFF27AE60)
    val questGold = Color(0xFFD4A843)
    val npcCyan = Color(0xFF1ABC9C)
    val rarityUncommon = Color(0xFF27AE60)
    val rarityRare = Color(0xFF2980B9)
    val rarityEpic = Color(0xFF8E44AD)
    val rarityLegendary = Color(0xFFE67E22)

    // UI
    val error = Color(0xFFC0392B)
    val divider = Color(0xFFD4C9B4)
    val buttonPrimary = Color(0xFF5C3D2E)
    val buttonText = Color(0xFFF5EFE0)
    val micIdle = Color(0xFFBDA68E)
    val micActive = Color(0xFFC0392B)
    val micSpeaking = Color(0xFF5C3D2E)
    val overlay = Color(0x99000000)
    val notificationBg = Color(0xE6EDE5D4)
    val subtitleBg = Color(0xCC2C1810)
}

// ── Fonts ────────────────────────────────────────────────────────

@Composable
fun adventureHeadingFamily(): FontFamily = FontFamily(
    Font(Res.font.medievalsharp_regular, FontWeight.Normal)
)

@Composable
fun adventureBodyFamily(): FontFamily = FontFamily(
    Font(Res.font.alegreya_sans_regular, FontWeight.Normal),
    Font(Res.font.alegreya_sans_bold, FontWeight.Bold)
)

@Composable
fun appTypography(): Typography {
    val heading = adventureHeadingFamily()
    val body = adventureBodyFamily()

    return Typography(
        displayLarge = TextStyle(fontFamily = heading, fontSize = 42.sp, fontWeight = FontWeight.Normal, color = AppColors.primary),
        headlineLarge = TextStyle(fontFamily = heading, fontSize = 28.sp, fontWeight = FontWeight.Normal, color = AppColors.primary),
        headlineMedium = TextStyle(fontFamily = heading, fontSize = 22.sp, fontWeight = FontWeight.Normal, color = AppColors.primary),
        titleLarge = TextStyle(fontFamily = body, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.textPrimary),
        titleMedium = TextStyle(fontFamily = body, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.textPrimary),
        bodyLarge = TextStyle(fontFamily = body, fontSize = 16.sp, fontWeight = FontWeight.Normal, color = AppColors.textPrimary),
        bodyMedium = TextStyle(fontFamily = body, fontSize = 14.sp, fontWeight = FontWeight.Normal, color = AppColors.textSecondary),
        bodySmall = TextStyle(fontFamily = body, fontSize = 12.sp, fontWeight = FontWeight.Normal, color = AppColors.textMuted),
        labelLarge = TextStyle(fontFamily = body, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.textPrimary),
        labelMedium = TextStyle(fontFamily = body, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.textSecondary),
        labelSmall = TextStyle(fontFamily = body, fontSize = 10.sp, fontWeight = FontWeight.Normal, color = AppColors.textMuted)
    )
}
