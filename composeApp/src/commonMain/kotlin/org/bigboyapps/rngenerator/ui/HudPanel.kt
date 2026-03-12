package org.bigboyapps.rngenerator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.AsyncImage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class HudTab(val label: String) {
    CHARACTER("Character"),
    INVENTORY("Inventory"),
    QUESTS("Quests")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HudPanel(
    stats: PlayerStatsUi?,
    fullSheet: FullCharacterSheetUi? = null,
    inventory: List<InventoryItemUi>,
    quests: List<QuestUi>,
    npcsHere: List<NpcUi>,
    onNpcClick: (String) -> Unit,
    onDismiss: () -> Unit,
    initialTab: HudTab = HudTab.CHARACTER
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeTab by remember { mutableStateOf(initialTab) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = AppColors.inkDark,
        dragHandle = null
    ) {
        // Parchment panel with ornamental corners
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 8.dp)
                .parchmentPanel()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(3.dp)
                            .background(AppColors.parchmentEdge, RoundedCornerShape(2.dp))
                    )
                }

                // Tab bar
                HudTabBar(activeTab = activeTab, onTabSelected = { activeTab = it })

                // Tab content
                when (activeTab) {
                    HudTab.CHARACTER -> CharacterTab(stats, fullSheet, npcsHere, onNpcClick)
                    HudTab.INVENTORY -> InventoryTab(inventory)
                    HudTab.QUESTS -> QuestsTab(quests)
                }
            }
        }
    }
}

// ── Parchment Panel Modifier ─────────────────────────────────────

private fun Modifier.parchmentPanel() = this
    .background(
        Brush.verticalGradient(
            listOf(AppColors.parchmentLight, AppColors.parchment, AppColors.parchmentDark)
        ),
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    )
    .border(
        width = 2.dp,
        brush = Brush.verticalGradient(listOf(AppColors.parchmentEdge, AppColors.bronze.copy(alpha = 0.4f))),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    )
    .drawBehind {
        val ornSize = 24f
        val color = AppColors.parchmentEdge
        // Top-left corner
        drawCornerOrnament(12f, 12f, ornSize, color)
        // Top-right corner
        drawCornerOrnament(size.width - 12f, 12f, ornSize, color, flipX = true)
    }

// ── Tab Bar ──────────────────────────────────────────────────────

@Composable
private fun HudTabBar(activeTab: HudTab, onTabSelected: (HudTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        HudTab.entries.forEach { tab ->
            val isActive = tab == activeTab
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                when (tab) {
                    HudTab.CHARACTER -> ShieldIcon(size = 18.dp, color = if (isActive) AppColors.bronze else AppColors.inkMuted)
                    HudTab.INVENTORY -> ChestIcon(size = 18.dp, color = if (isActive) AppColors.bronze else AppColors.inkMuted)
                    HudTab.QUESTS -> ScrollIcon(size = 18.dp, color = if (isActive) AppColors.bronze else AppColors.inkMuted)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (isActive) AppColors.bronze else AppColors.inkMuted,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                )
                if (isActive) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(2.dp)
                            .background(AppColors.bronze, RoundedCornerShape(1.dp))
                    )
                }
            }
        }
    }

    // Divider line
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(AppColors.parchmentEdge.copy(alpha = 0.5f))
    )
}

// ── Preview Wrappers ─────────────────────────────────────────────

@Composable
internal fun HudPreviewContent(
    stats: PlayerStatsUi?,
    fullSheet: FullCharacterSheetUi? = null,
    inventory: List<InventoryItemUi>,
    quests: List<QuestUi>,
    npcsHere: List<NpcUi>
) = CharacterTab(stats, fullSheet, npcsHere, onNpcClick = {})

@Composable
internal fun InventoryPreviewContent(inventory: List<InventoryItemUi>) = InventoryTab(inventory)

@Composable
internal fun QuestsPreviewContent(quests: List<QuestUi>) = QuestsTab(quests)

// ── Character Tab ────────────────────────────────────────────────

@Composable
private fun CharacterTab(stats: PlayerStatsUi?, fullSheet: FullCharacterSheetUi?, npcsHere: List<NpcUi>, onNpcClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (stats != null) {
            // Portrait + Name + Grade
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Ornate portrait frame
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .drawBehind {
                                val s = size.minDimension
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        listOf(AppColors.bronze, AppColors.bronzeLight, AppColors.gold, AppColors.bronze)
                                    ),
                                    radius = s / 2,
                                    style = Stroke(width = 4f)
                                )
                                drawCircle(
                                    color = AppColors.bronzeDark,
                                    radius = s / 2 - 5f,
                                    style = Stroke(width = 1.5f)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(AppColors.parchmentDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stats.name.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    color = AppColors.bronze
                                )
                            )
                        }
                    }

                    Column {
                        Text(
                            text = stats.name,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = AppColors.inkDark
                            )
                        )
                        val subtitle = buildString {
                            append("Level ${stats.level}")
                            if (stats.playerClass.isNotEmpty()) append(" ${stats.playerClass}")
                            val grade = fullSheet?.grade ?: ""
                            if (grade.isNotEmpty() && grade != "None") append(" · $grade")
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = AppColors.inkFaded
                            )
                        )
                        val profession = fullSheet?.profession ?: ""
                        if (profession.isNotEmpty() && profession != "None") {
                            Text(
                                text = profession,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = AppColors.inkMuted,
                                    fontStyle = FontStyle.Italic
                                )
                            )
                        }
                    }
                }
            }

            // HP + Mana + Energy bars
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RpgStatBar(
                        icon = { HeartIcon(size = 14.dp) },
                        label = "HP",
                        current = stats.hp,
                        max = stats.maxHp,
                        barColor = AppColors.hpRed,
                        trackColor = AppColors.hpRedDark.copy(alpha = 0.2f)
                    )
                    RpgStatBar(
                        icon = { DropletIcon(size = 14.dp) },
                        label = "Mana",
                        current = fullSheet?.mana ?: stats.energy,
                        max = fullSheet?.maxMana ?: stats.maxEnergy,
                        barColor = AppColors.manaBlue,
                        trackColor = AppColors.manaBlueDark.copy(alpha = 0.2f)
                    )
                    if (fullSheet != null && fullSheet.maxEnergy > 0) {
                        RpgStatBar(
                            icon = { StarIcon(size = 14.dp, color = AppColors.xpGreen) },
                            label = "NRG",
                            current = fullSheet.energy,
                            max = fullSheet.maxEnergy,
                            barColor = AppColors.xpGreen,
                            trackColor = AppColors.xpGreenDark.copy(alpha = 0.2f)
                        )
                    }
                }
            }

            // Stat chips row
            if (stats.stats.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        stats.stats.entries.forEachIndexed { index, (name, value) ->
                            val icon: @Composable () -> Unit = when (index) {
                                0 -> { { SwordIcon(size = 12.dp, color = AppColors.bronze) } }
                                1 -> { { BowIcon(size = 12.dp, color = AppColors.bronze) } }
                                2 -> { { StarIcon(size = 12.dp, color = AppColors.bronze) } }
                                else -> { { ShieldIcon(size = 12.dp, color = AppColors.bronze) } }
                            }
                            StatChip(icon = icon, name = name.take(3).uppercase(), value = value)
                        }
                        // Defense chip from full sheet
                        if (fullSheet != null && fullSheet.defense > 0) {
                            StatChip(
                                icon = { ShieldIcon(size = 12.dp, color = AppColors.bronze) },
                                name = "DEF",
                                value = fullSheet.defense
                            )
                        }
                    }
                }
            }

            // XP bar
            item {
                RpgStatBar(
                    icon = { StarIcon(size = 14.dp, color = AppColors.gold) },
                    label = "XP",
                    current = stats.xp.toInt(),
                    max = stats.xpToNext.toInt(),
                    barColor = AppColors.gold,
                    trackColor = AppColors.parchmentEdge.copy(alpha = 0.2f)
                )
            }

            // Equipment section
            if (fullSheet != null) {
                val eq = fullSheet.equipment
                val hasEquipment = eq.weapon != "None" || eq.armor != "None" || eq.accessory != "None"
                if (hasEquipment) {
                    item { OrnamentalDivider(modifier = Modifier.fillMaxWidth()) }
                    item {
                        Text(
                            text = "Equipment",
                            style = MaterialTheme.typography.titleMedium.copy(color = AppColors.inkDark)
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (eq.weapon != "None") EquipmentRow("Weapon", eq.weapon)
                            if (eq.armor != "None") EquipmentRow("Armor", eq.armor)
                            if (eq.accessory != "None") EquipmentRow("Accessory", eq.accessory)
                        }
                    }
                }
            }

            // Skills section
            if (fullSheet != null && fullSheet.skills.isNotEmpty()) {
                item { OrnamentalDivider(modifier = Modifier.fillMaxWidth()) }
                item {
                    Text(
                        text = "Skills",
                        style = MaterialTheme.typography.titleMedium.copy(color = AppColors.inkDark)
                    )
                }
                items(fullSheet.skills.size) { index ->
                    SkillCard(fullSheet.skills[index])
                }
            }

            // Status effects
            if (fullSheet != null && fullSheet.statusEffects.isNotEmpty()) {
                item { OrnamentalDivider(modifier = Modifier.fillMaxWidth()) }
                item {
                    Text(
                        text = "Status Effects",
                        style = MaterialTheme.typography.titleMedium.copy(color = AppColors.inkDark)
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        fullSheet.statusEffects.forEach { effect ->
                            Box(
                                modifier = Modifier
                                    .background(AppColors.rarityEpic.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                    .border(1.dp, AppColors.rarityEpic.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${effect.name} (${effect.turnsRemaining}t)",
                                    style = MaterialTheme.typography.labelSmall.copy(color = AppColors.rarityEpic)
                                )
                            }
                        }
                    }
                }
            }
        }

        // NPCs
        if (npcsHere.isNotEmpty()) {
            item { OrnamentalDivider(modifier = Modifier.fillMaxWidth()) }
            item {
                Text(
                    text = "Nearby",
                    style = MaterialTheme.typography.titleMedium.copy(color = AppColors.inkDark)
                )
            }
            items(npcsHere) { npc -> NpcCard(npc, onNpcClick) }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun EquipmentRow(slot: String, name: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.parchmentDark.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = slot,
            style = MaterialTheme.typography.labelMedium.copy(color = AppColors.inkFaded)
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AppColors.inkDark,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun SkillCard(skill: SkillUi) {
    val rarityColor = when (skill.rarity.uppercase()) {
        "UNCOMMON" -> AppColors.rarityUncommon
        "RARE" -> AppColors.rarityRare
        "EPIC" -> AppColors.rarityEpic
        "LEGENDARY" -> AppColors.rarityLegendary
        else -> AppColors.rarityCommon
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.parchmentDark.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .border(1.dp, AppColors.parchmentEdge.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = skill.name,
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = rarityColor,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Lv.${skill.level}",
                    style = MaterialTheme.typography.labelSmall.copy(color = AppColors.inkFaded)
                )
            }
            // Cost badges
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (skill.manaCost > 0) {
                    CostBadge("${skill.manaCost}", AppColors.manaBlue)
                }
                if (skill.energyCost > 0) {
                    CostBadge("${skill.energyCost}", AppColors.xpGreen)
                }
                if (!skill.ready) {
                    CostBadge("CD", AppColors.inkMuted)
                }
            }
        }
        Text(
            text = skill.description,
            style = MaterialTheme.typography.bodySmall.copy(color = AppColors.inkMedium),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun CostBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                color = color,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun RpgStatBar(
    icon: @Composable () -> Unit,
    label: String,
    current: Int,
    max: Int,
    barColor: Color,
    trackColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        icon()
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(color = AppColors.inkMedium),
            modifier = Modifier.width(36.dp)
        )

        // Bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(trackColor)
                .border(1.dp, AppColors.parchmentEdge.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
        ) {
            val fraction = if (max > 0) current.toFloat() / max else 0f
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(
                        Brush.horizontalGradient(
                            listOf(barColor.copy(alpha = 0.7f), barColor)
                        ),
                        RoundedCornerShape(3.dp)
                    )
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$current / $max",
            style = MaterialTheme.typography.bodySmall.copy(
                color = AppColors.inkFaded,
                fontSize = 11.sp
            ),
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun StatChip(icon: @Composable () -> Unit, name: String, value: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(AppColors.parchmentDark.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .border(1.dp, AppColors.parchmentEdge.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        icon()
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(
                color = AppColors.inkFaded,
                letterSpacing = 1.sp,
                fontSize = 9.sp
            )
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium.copy(
                color = AppColors.inkDark,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun NpcCard(npc: NpcUi, onNpcClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(AppColors.parchmentDark.copy(alpha = 0.4f))
            .border(1.dp, AppColors.parchmentEdge.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .clickable { onNpcClick(npc.id) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // NPC portrait with ornate frame
        NpcPortraitImage(
            name = npc.name,
            size = 40.dp
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = npc.name,
                style = MaterialTheme.typography.labelLarge.copy(color = AppColors.npcName)
            )
            if (npc.archetype.isNotEmpty()) {
                Text(
                    text = npc.archetype,
                    style = MaterialTheme.typography.bodySmall.copy(color = AppColors.inkFaded)
                )
            }
        }
    }
}

// ── Inventory Tab ────────────────────────────────────────────────

@Composable
private fun InventoryTab(inventory: List<InventoryItemUi>) {
    if (inventory.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ChestIcon(size = 32.dp, color = AppColors.inkMuted)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Your pack is empty", style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.inkMuted))
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(inventory) { item -> InventoryCell(item) }
        }
    }
}

@Composable
private fun InventoryCell(item: InventoryItemUi) {
    val rarityColor = when (item.rarity.uppercase()) {
        "UNCOMMON" -> AppColors.rarityUncommon
        "RARE" -> AppColors.rarityRare
        "EPIC" -> AppColors.rarityEpic
        "LEGENDARY" -> AppColors.rarityLegendary
        else -> AppColors.rarityCommon
    }

    // Beveled cell: light border top-left, darker bottom-right
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .drawBehind {
                val w = size.width
                val h = size.height
                val r = 6f
                // Fill
                drawRoundRect(
                    color = AppColors.parchmentDark.copy(alpha = 0.6f),
                    cornerRadius = CornerRadius(r),
                    size = size
                )
                // Light edge (top + left)
                drawLine(AppColors.parchmentLight, Offset(r, 0f), Offset(w - r, 0f), strokeWidth = 1.5f)
                drawLine(AppColors.parchmentLight, Offset(0f, r), Offset(0f, h - r), strokeWidth = 1.5f)
                // Dark edge (bottom + right)
                drawLine(AppColors.parchmentEdge, Offset(r, h), Offset(w - r, h), strokeWidth = 1.5f)
                drawLine(AppColors.parchmentEdge, Offset(w, r), Offset(w, h - r), strokeWidth = 1.5f)
                // Outer border
                drawRoundRect(
                    color = AppColors.parchmentEdge.copy(alpha = 0.6f),
                    cornerRadius = CornerRadius(r),
                    style = Stroke(width = 1f)
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Item icon: Coil async image if URL available, Canvas fallback otherwise
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppColors.parchment.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.iconUrl != null) {
                    AsyncImage(
                        model = item.iconUrl,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    ItemCategoryIcon(item.name, size = 20.dp, color = rarityColor.copy(alpha = 0.7f))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = rarityColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Quantity badge
        if (item.quantity > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(16.dp)
                    .background(AppColors.leather, CircleShape)
                    .border(1.dp, AppColors.leatherDark, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${item.quantity}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AppColors.parchmentLight,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

/**
 * Maps item name keywords to Canvas-drawn category icons.
 */
@Composable
private fun ItemCategoryIcon(name: String, size: Dp, color: Color) {
    val lower = name.lowercase()
    when {
        lower.contains("sword") || lower.contains("blade") || lower.contains("dagger") ||
        lower.contains("axe") || lower.contains("weapon") -> SwordIcon(size = size, color = color)
        lower.contains("bow") || lower.contains("arrow") -> BowIcon(size = size, color = color)
        lower.contains("shield") || lower.contains("armor") || lower.contains("helm") ||
        lower.contains("plate") || lower.contains("mail") -> ShieldIcon(size = size, color = color)
        lower.contains("potion") || lower.contains("elixir") || lower.contains("vial") ||
        lower.contains("flask") -> DropletIcon(size = size, color = color)
        lower.contains("scroll") || lower.contains("tome") || lower.contains("book") ||
        lower.contains("spell") || lower.contains("map") -> ScrollIcon(size = size, color = color)
        lower.contains("coin") || lower.contains("gold") || lower.contains("silver") ||
        lower.contains("gem") || lower.contains("crystal") -> CoinIcon(size = size, color = color)
        lower.contains("key") || lower.contains("lock") || lower.contains("chest") ||
        lower.contains("box") -> ChestIcon(size = size, color = color)
        lower.contains("ring") || lower.contains("amulet") || lower.contains("necklace") ||
        lower.contains("pendant") -> StarIcon(size = size, color = color)
        lower.contains("scale") || lower.contains("claw") || lower.contains("fang") ||
        lower.contains("bone") || lower.contains("hide") -> ShieldIcon(size = size, color = color)
        else -> SwordIcon(size = size, color = color) // Default fallback
    }
}

// ── Quests Tab ───────────────────────────────────────────────────

@Composable
private fun QuestsTab(quests: List<QuestUi>) {
    if (quests.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ScrollIcon(size = 32.dp, color = AppColors.inkMuted)
                Spacer(modifier = Modifier.height(8.dp))
                Text("No active quests", style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.inkMuted))
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(quests) { quest -> QuestCard(quest) }
        }
    }
}

@Composable
private fun QuestCard(quest: QuestUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.parchmentDark.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .border(1.dp, AppColors.parchmentEdge.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        // Quest header with scroll icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScrollIcon(size = 16.dp, color = AppColors.gold)
            Text(
                text = quest.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = AppColors.gold,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        if (quest.description.isNotEmpty()) {
            Text(
                text = quest.description,
                style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.inkMedium),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (quest.objectives.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppColors.parchmentEdge.copy(alpha = 0.3f))
            )
            Spacer(modifier = Modifier.height(6.dp))

            quest.objectives.forEach { obj ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Custom checkbox
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .border(
                                1.dp,
                                if (obj.completed) AppColors.xpGreen else AppColors.inkMuted,
                                RoundedCornerShape(2.dp)
                            )
                            .background(
                                if (obj.completed) AppColors.xpGreen.copy(alpha = 0.15f) else Color.Transparent,
                                RoundedCornerShape(2.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (obj.completed) {
                            // Checkmark drawn with canvas
                            androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
                                val path = Path().apply {
                                    moveTo(size.width * 0.15f, size.height * 0.5f)
                                    lineTo(size.width * 0.4f, size.height * 0.75f)
                                    lineTo(size.width * 0.85f, size.height * 0.2f)
                                }
                                drawPath(
                                    path, AppColors.xpGreen,
                                    style = Stroke(width = 2f, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }
                    Text(
                        text = obj.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (obj.completed) AppColors.xpGreen else AppColors.inkMedium
                        )
                    )
                }
            }
        }
    }
}
