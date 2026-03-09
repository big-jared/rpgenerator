package org.bigboyapps.rngenerator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// ── Sample Data ──────────────────────────────────────────────────

private val sampleFeedItems = listOf(
    FeedItem.SystemNotice("You awaken in a dimly lit chamber..."),
    FeedItem.Narration(
        "The air is thick with the scent of damp stone and old iron. " +
        "Torchlight flickers along the walls, casting long shadows that dance " +
        "across ancient runes carved into the floor. A heavy wooden door stands " +
        "before you, its surface scarred with claw marks."
    ),
    FeedItem.PlayerMessage("I examine the runes on the floor"),
    FeedItem.Narration(
        "You kneel down and trace your fingers along the carved symbols. " +
        "They pulse faintly with a blue-white light at your touch — some kind " +
        "of warding spell, old but still active."
    ),
    FeedItem.CompanionAside("Hank", "I've seen marks like these before. Tread carefully."),
    FeedItem.XpGain(25),
    FeedItem.ItemGained("Rusted Iron Key"),
    FeedItem.NpcDialogue(
        npcName = "Old Ferryman",
        role = "Merchant",
        text = "That key... where did you find it? I haven't seen one of those in twenty years."
    ),
    FeedItem.QuestUpdate("The Sunken Vault", "New Quest"),
    FeedItem.GoldGained(50),
    FeedItem.PlayerMessage("Tell me about the Sunken Vault"),
    FeedItem.Narration(
        "The ferryman leans in close, his voice dropping to a whisper. " +
        "\"Below the river, past the old mill — there's a place the townsfolk " +
        "don't speak of. The water runs backwards there.\""
    ),
    FeedItem.LevelUp(3),
    FeedItem.ItemGained("Enchanted Shortsword", 1),
)

private val sampleStats = PlayerStatsUi(
    name = "Kael Ashwood",
    level = 7,
    playerClass = "Shadow Ranger",
    hp = 73,
    maxHp = 120,
    energy = 28,
    maxEnergy = 60,
    xp = 1450,
    xpToNext = 2000,
    stats = mapOf(
        "Strength" to 14,
        "Dexterity" to 18,
        "Wisdom" to 12,
        "Charisma" to 10
    )
)

private val sampleInventory = listOf(
    InventoryItemUi("1", "Enchanted Shortsword", "+3 ATK, Flame damage", 1, "RARE"),
    InventoryItemUi("2", "Health Potion", "Restores 50 HP", 3, "COMMON"),
    InventoryItemUi("3", "Shadow Cloak", "Stealth +5", 1, "EPIC"),
    InventoryItemUi("4", "Iron Shield", "DEF +2", 1, "UNCOMMON"),
    InventoryItemUi("5", "Torch", "Lights dark areas", 5, "COMMON"),
    InventoryItemUi("6", "Dragon Scale", "Legendary material", 1, "LEGENDARY"),
    InventoryItemUi("7", "Lockpicks", "Open locked doors", 2, "COMMON"),
    InventoryItemUi("8", "Mana Crystal", "Restores 30 Energy", 2, "UNCOMMON"),
    InventoryItemUi("9", "Ancient Map", "Shows hidden paths", 1, "RARE"),
)

private val sampleQuests = listOf(
    QuestUi(
        id = "1",
        name = "The Sunken Vault",
        description = "Find the entrance to the vault beneath the old mill.",
        objectives = listOf(
            QuestObjectiveUi("Speak with the Old Ferryman", true),
            QuestObjectiveUi("Find the rusted key", true),
            QuestObjectiveUi("Locate the old mill", false),
            QuestObjectiveUi("Enter the Sunken Vault", false)
        )
    ),
    QuestUi(
        id = "2",
        name = "Shadow in the Pines",
        description = "Investigate disappearances near Thornwood Forest.",
        objectives = listOf(
            QuestObjectiveUi("Talk to the village elder", false),
            QuestObjectiveUi("Search the forest at night", false)
        )
    )
)

private val sampleNpcs = listOf(
    NpcUi("1", "Old Ferryman", "Merchant", "Weathered boatman who knows the river's secrets"),
    NpcUi("2", "Captain Voss", "Guard", "Stern but fair leader of the town watch"),
)

// ── Feed Preview ─────────────────────────────────────────────────

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    name = "Story Feed"
)
@Composable
private fun StoryFeedPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                contentPadding = PaddingValues(top = 48.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(sampleFeedItems, key = { it.id }) { item ->
                    PreviewFeedItemView(item)
                }
            }
        }
    }
}

// ── Character Sheet Preview ──────────────────────────────────────

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 600,
    name = "Character Sheet"
)
@Composable
private fun CharacterSheetPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.background)
        ) {
            HudPreviewContent(
                stats = sampleStats,
                inventory = sampleInventory,
                quests = sampleQuests,
                npcsHere = sampleNpcs
            )
        }
    }
}

// ── Inventory Grid Preview ───────────────────────────────────────

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 500,
    name = "Inventory Grid"
)
@Composable
private fun InventoryPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.background)
                .padding(12.dp)
        ) {
            InventoryPreviewContent(sampleInventory)
        }
    }
}

// ── Quest Panel Preview ──────────────────────────────────────────

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 500,
    name = "Quest Panel"
)
@Composable
private fun QuestPanelPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.background)
                .padding(12.dp)
        ) {
            QuestsPreviewContent(sampleQuests)
        }
    }
}

// ── Notification Cards Preview ───────────────────────────────────

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 400,
    name = "Notification Cards"
)
@Composable
private fun NotificationCardsPreview() {
    MaterialTheme {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.background),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val cards = listOf(
                FeedItem.XpGain(150),
                FeedItem.ItemGained("Enchanted Shortsword"),
                FeedItem.ItemGained("Health Potion", 3),
                FeedItem.GoldGained(250),
                FeedItem.QuestUpdate("The Sunken Vault", "New Quest"),
                FeedItem.QuestUpdate("Shadow in the Pines", "Completed"),
                FeedItem.LevelUp(5),
                FeedItem.SystemNotice("Auto-saved"),
                FeedItem.CompanionAside("Hank", "Something doesn't feel right about this place."),
            )
            items(cards, key = { it.id }) { item ->
                PreviewFeedItemView(item)
            }
        }
    }
}
