package org.bigboyapps.rngenerator.ui

import com.rpgenerator.composeapp.generated.resources.Res
import com.rpgenerator.composeapp.generated.resources.monster_ashlands_caldera_tyrant
import com.rpgenerator.composeapp.generated.resources.monster_ashlands_emberwraith
import com.rpgenerator.composeapp.generated.resources.monster_ashlands_magma_serpent
import com.rpgenerator.composeapp.generated.resources.monster_ashlands_slag_beetle
import com.rpgenerator.composeapp.generated.resources.monster_drowned_abyssal_reacher
import com.rpgenerator.composeapp.generated.resources.monster_drowned_leviathans_maw
import com.rpgenerator.composeapp.generated.resources.monster_drowned_reef_razor
import com.rpgenerator.composeapp.generated.resources.monster_drowned_tidewalker
import com.rpgenerator.composeapp.generated.resources.monster_greenreach_bone_crawler
import com.rpgenerator.composeapp.generated.resources.monster_greenreach_canopy_stalker
import com.rpgenerator.composeapp.generated.resources.monster_greenreach_hollow_matriarch
import com.rpgenerator.composeapp.generated.resources.monster_greenreach_thornback
import com.rpgenerator.composeapp.generated.resources.monster_hollow_spore_lurker
import com.rpgenerator.composeapp.generated.resources.monster_spire_guardian
import com.rpgenerator.composeapp.generated.resources.monster_spire_the_apex
import org.jetbrains.compose.resources.DrawableResource

/**
 * Lookup table: portraitResource name → Compose DrawableResource.
 * Used to load pre-made monster portraits by the name stored in the bestiary.
 */
internal val monsterPortraits: Map<String, DrawableResource> = mapOf(
    // Greenreach
    "monster_greenreach_bone_crawler" to Res.drawable.monster_greenreach_bone_crawler,
    "monster_greenreach_canopy_stalker" to Res.drawable.monster_greenreach_canopy_stalker,
    "monster_greenreach_thornback" to Res.drawable.monster_greenreach_thornback,
    "monster_greenreach_hollow_matriarch" to Res.drawable.monster_greenreach_hollow_matriarch,
    // Ashlands
    "monster_ashlands_slag_beetle" to Res.drawable.monster_ashlands_slag_beetle,
    "monster_ashlands_magma_serpent" to Res.drawable.monster_ashlands_magma_serpent,
    "monster_ashlands_emberwraith" to Res.drawable.monster_ashlands_emberwraith,
    "monster_ashlands_caldera_tyrant" to Res.drawable.monster_ashlands_caldera_tyrant,
    // Drowned Shelf
    "monster_drowned_reef_razor" to Res.drawable.monster_drowned_reef_razor,
    "monster_drowned_abyssal_reacher" to Res.drawable.monster_drowned_abyssal_reacher,
    "monster_drowned_tidewalker" to Res.drawable.monster_drowned_tidewalker,
    "monster_drowned_leviathans_maw" to Res.drawable.monster_drowned_leviathans_maw,
    // Spire
    "monster_spire_guardian" to Res.drawable.monster_spire_guardian,
    "monster_spire_the_apex" to Res.drawable.monster_spire_the_apex,
    // The Hollow
    "monster_hollow_spore_lurker" to Res.drawable.monster_hollow_spore_lurker
)
