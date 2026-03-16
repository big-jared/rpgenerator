package org.bigboyapps.rngenerator.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rpgenerator.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

fun npcNameToSlug(name: String): String {
    return name.trim().lowercase().replace(Regex("\\s+"), "_").replace(Regex("[^a-z0-9_]"), "")
}

private val portraitMap: Map<String, DrawableResource> = mapOf(
    "aldric" to Res.drawable.aldric,
    "bramble" to Res.drawable.bramble,
    "eli_park" to Res.drawable.eli_park,
    "glitch" to Res.drawable.glitch,
    "hank_morales" to Res.drawable.hank_morales,
    "hank" to Res.drawable.hank,
    "host" to Res.drawable.host,
    "jin_yasuda" to Res.drawable.jin_yasuda,
    "lin_wei" to Res.drawable.lin_wei,
    "marcus_cole" to Res.drawable.marcus_cole,
    "old_mae" to Res.drawable.old_mae,
    "pip" to Res.drawable.pip,
    "receptionist" to Res.drawable.receptionist,
    "sable_okafor" to Res.drawable.sable_okafor,
    "tomoko_ito" to Res.drawable.tomoko_ito,
    "voss" to Res.drawable.voss,
)

private fun findPortrait(slug: String): DrawableResource? {
    val titles = listOf("captain_", "commander_", "elder_", "sir_", "lady_", "lord_", "master_", "doctor_", "dr_")
    val strippedSlug = titles.fold(slug) { s, title -> s.removePrefix(title) }
    val lastName = slug.substringAfterLast("_")
    return listOf(slug, strippedSlug, lastName)
        .distinct()
        .firstNotNullOfOrNull { portraitMap[it] }
}

@Composable
fun NpcPortraitImage(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    generatedPortrait: ByteArray? = null
) {
    val slug = npcNameToSlug(name)
    val resource = findPortrait(slug)
    val generatedBitmap: ImageBitmap? = generatedPortrait?.let {
        try { decodeImageBytes(it) } catch (_: Exception) { null }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                brush = Brush.sweepGradient(
                    listOf(AppColors.bronze, AppColors.gold, AppColors.bronzeLight, AppColors.bronze)
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (generatedBitmap != null) {
            // Generated portrait takes priority
            Image(
                bitmap = generatedBitmap,
                contentDescription = "$name portrait",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else if (resource != null) {
            Image(
                painter = painterResource(resource),
                contentDescription = "$name portrait",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(AppColors.bronze.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(color = AppColors.bronze)
                )
            }
        }
    }
}
