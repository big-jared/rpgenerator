package org.bigboyapps.rngenerator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class HudTab(val label: String) {
    CHARACTER("Character"),
    INVENTORY("Inventory"),
    QUESTS("Quests")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HudPanel(
    stats: PlayerStatsUi?,
    inventory: List<InventoryItemUi>,
    quests: List<QuestUi>,
    npcsHere: List<NpcUi>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeTab by remember { mutableStateOf(HudTab.CHARACTER) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
        contentColor = AppColors.textPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(AppColors.divider, RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            // Tab row
            TabRow(
                selectedTabIndex = activeTab.ordinal,
                containerColor = AppColors.surface,
                contentColor = AppColors.primary,
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        color = AppColors.accent
                    )
                }
            ) {
                HudTab.entries.forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        text = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = if (activeTab == tab) AppColors.primary else AppColors.textMuted
                                )
                            )
                        }
                    )
                }
            }

            // Tab content
            when (activeTab) {
                HudTab.CHARACTER -> CharacterTab(stats, npcsHere)
                HudTab.INVENTORY -> InventoryTab(inventory)
                HudTab.QUESTS -> QuestsTab(quests)
            }
        }
    }
}

// ── Preview Wrappers (internal for androidMain previews) ─────────

@Composable
internal fun HudPreviewContent(
    stats: PlayerStatsUi?,
    inventory: List<InventoryItemUi>,
    quests: List<QuestUi>,
    npcsHere: List<NpcUi>
) = CharacterTab(stats, npcsHere)

@Composable
internal fun InventoryPreviewContent(inventory: List<InventoryItemUi>) = InventoryTab(inventory)

@Composable
internal fun QuestsPreviewContent(quests: List<QuestUi>) = QuestsTab(quests)

// ── Character Tab ────────────────────────────────────────────────

@Composable
private fun CharacterTab(stats: PlayerStatsUi?, npcsHere: List<NpcUi>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (stats != null) {
            // Portrait + Name header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Portrait circle placeholder
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(AppColors.surfaceCard)
                            .border(3.dp, AppColors.accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stats.name.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                color = AppColors.accent
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stats.name,
                        style = MaterialTheme.typography.headlineMedium
                    )

                    if (stats.playerClass.isNotEmpty()) {
                        Text(
                            text = stats.playerClass,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = AppColors.primaryLight
                            )
                        )
                    }

                    Text(
                        text = "Level ${stats.level}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // HP / Energy / XP bars
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBar(label = "HP", current = stats.hp, max = stats.maxHp, color = AppColors.hpRed)
                    StatBar(label = "Energy", current = stats.energy, max = stats.maxEnergy, color = AppColors.energyBlue)
                    StatBar(label = "XP", current = stats.xp.toInt(), max = stats.xpToNext.toInt(), color = AppColors.xpGreen)
                }
            }

            // Stats row
            if (stats.stats.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        stats.stats.forEach { (name, value) ->
                            StatChip(name = name.take(3).uppercase(), value = value)
                        }
                    }
                }
            }
        }

        // NPCs Here
        if (npcsHere.isNotEmpty()) {
            item {
                HorizontalDivider(color = AppColors.divider)
                Text(
                    text = "Nearby",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(npcsHere) { npc ->
                NpcCard(npc)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun StatChip(name: String, value: Int) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = AppColors.surfaceCard
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge.copy(
                    color = AppColors.primary,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = AppColors.textMuted,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

@Composable
private fun StatBar(label: String, current: Int, max: Int, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = "$current / $max",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (max > 0) current.toFloat() / max else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = AppColors.surfaceCard,
        )
    }
}

@Composable
private fun NpcCard(npc: NpcUi) {
    Surface(
        color = AppColors.surfaceCard,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // NPC avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AppColors.npcCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = npc.name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = AppColors.npcCyan
                    )
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = npc.name,
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = AppColors.npcCyan
                    )
                )
                if (npc.archetype.isNotEmpty()) {
                    Text(
                        text = npc.archetype,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// ── Inventory Tab ────────────────────────────────────────────────

@Composable
private fun InventoryTab(inventory: List<InventoryItemUi>) {
    if (inventory.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Your pack is empty",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AppColors.textMuted
                )
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(inventory) { item ->
                InventoryGridItem(item)
            }
        }
    }
}

@Composable
private fun InventoryGridItem(item: InventoryItemUi) {
    val rarityColor = when (item.rarity.uppercase()) {
        "UNCOMMON" -> AppColors.rarityUncommon
        "RARE" -> AppColors.rarityRare
        "EPIC" -> AppColors.rarityEpic
        "LEGENDARY" -> AppColors.rarityLegendary
        else -> AppColors.textMuted
    }
    val borderColor = when (item.rarity.uppercase()) {
        "UNCOMMON", "RARE", "EPIC", "LEGENDARY" -> rarityColor.copy(alpha = 0.5f)
        else -> AppColors.divider
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = AppColors.surfaceCard,
        modifier = Modifier
            .aspectRatio(1f)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = rarityColor,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.description.isNotEmpty()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Quantity badge
            if (item.quantity > 1) {
                Surface(
                    shape = CircleShape,
                    color = AppColors.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${item.quantity}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AppColors.buttonText,
                                fontSize = 9.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

// ── Quests Tab ───────────────────────────────────────────────────

@Composable
private fun QuestsTab(quests: List<QuestUi>) {
    if (quests.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No active quests",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AppColors.textMuted
                )
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(quests) { quest ->
                QuestCard(quest)
            }
        }
    }
}

@Composable
private fun QuestCard(quest: QuestUi) {
    Surface(
        color = AppColors.surfaceCard,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = quest.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = AppColors.questGold,
                    fontWeight = FontWeight.Bold
                )
            )
            if (quest.description.isNotEmpty()) {
                Text(
                    text = quest.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (quest.objectives.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                quest.objectives.forEach { obj ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = obj.completed,
                            onCheckedChange = null,
                            modifier = Modifier.size(18.dp),
                            colors = CheckboxDefaults.colors(
                                checkedColor = AppColors.xpGreen,
                                uncheckedColor = AppColors.textMuted,
                                checkmarkColor = AppColors.buttonText
                            )
                        )
                        Text(
                            text = obj.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (obj.completed) AppColors.xpGreen else AppColors.textSecondary
                            )
                        )
                    }
                }
            }
        }
    }
}
