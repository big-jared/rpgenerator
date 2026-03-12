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
    // Parchment palette
    val parchmentLight = Color(0xFFF2EDE6)
    val parchment = Color(0xFFEAE5DD)
    val parchmentDark = Color(0xFFD8D2C9)
    val parchmentEdge = Color(0xFFB89B6A)

    // Bronze / gold
    val bronze = Color(0xFF8B6914)
    val bronzeLight = Color(0xFFC49A4C)
    val bronzeDark = Color(0xFF6B4E10)
    val gold = Color(0xFFD4A843)

    // Leather / wood
    val leather = Color(0xFF6B4226)
    val leatherLight = Color(0xFF8B6040)
    val leatherDark = Color(0xFF4A2D18)
    val wood = Color(0xFF5C3D2E)

    // Ink
    val inkDark = Color(0xFF3A2315)
    val inkMedium = Color(0xFF5A4A3A)
    val inkFaded = Color(0xFF7A6B5A)
    val inkMuted = Color(0xFF9E8E7A)

    // Semantic
    val hpRed = Color(0xFFC23A22)
    val hpRedDark = Color(0xFF8B2A18)
    val manaBlue = Color(0xFF3B7BB8)
    val manaBlueDark = Color(0xFF2A5A8B)
    val xpGreen = Color(0xFF4A8B3A)
    val xpGreenDark = Color(0xFF3A6B2A)

    // Rarity
    val rarityCommon = Color(0xFF7A6B5A)
    val rarityUncommon = Color(0xFF4A8B3A)
    val rarityRare = Color(0xFF3B7BB8)
    val rarityEpic = Color(0xFF8B4A8B)
    val rarityLegendary = Color(0xFFD4A843)

    // NPC
    val npcName = Color(0xFF8B6914)

    // Backward compat aliases
    val primary = wood
    val primaryLight = bronzeLight
    val accent = gold
    val background = parchmentLight
    val surface = parchment
    val surfaceCard = parchmentDark
    val textPrimary = inkDark
    val textSecondary = inkMedium
    val textMuted = inkMuted
    val questGold = gold
    val npcCyan = Color(0xFF1ABC9C)
    val energyBlue = manaBlue
    val error = hpRed
    val divider = parchmentEdge
    val buttonPrimary = leather
    val buttonText = parchmentLight
    val micIdle = parchmentDark
    val micActive = hpRed
    val micSpeaking = leather
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
        displayLarge = TextStyle(fontFamily = heading, fontSize = 42.sp, fontWeight = FontWeight.Normal, color = AppColors.inkDark),
        headlineLarge = TextStyle(fontFamily = heading, fontSize = 28.sp, fontWeight = FontWeight.Normal, color = AppColors.inkDark),
        headlineMedium = TextStyle(fontFamily = heading, fontSize = 22.sp, fontWeight = FontWeight.Normal, color = AppColors.inkDark),
        titleLarge = TextStyle(fontFamily = body, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.inkDark),
        titleMedium = TextStyle(fontFamily = body, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.inkDark),
        bodyLarge = TextStyle(fontFamily = body, fontSize = 16.sp, fontWeight = FontWeight.Normal, color = AppColors.inkDark),
        bodyMedium = TextStyle(fontFamily = body, fontSize = 14.sp, fontWeight = FontWeight.Normal, color = AppColors.inkMedium),
        bodySmall = TextStyle(fontFamily = body, fontSize = 12.sp, fontWeight = FontWeight.Normal, color = AppColors.inkFaded),
        labelLarge = TextStyle(fontFamily = body, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.inkDark),
        labelMedium = TextStyle(fontFamily = body, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.inkMedium),
        labelSmall = TextStyle(fontFamily = body, fontSize = 10.sp, fontWeight = FontWeight.Normal, color = AppColors.inkFaded)
    )
}
