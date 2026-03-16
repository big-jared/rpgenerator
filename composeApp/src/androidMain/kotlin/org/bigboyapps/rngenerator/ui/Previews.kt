package org.bigboyapps.rngenerator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// ══════════════════════════════════════════════════════════════════
//  Sample Data
// ══════════════════════════════════════════════════════════════════

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

private val sampleFullSheet = FullCharacterSheetUi(
    grade = "D-Grade",
    profession = "Herbalist",
    mana = 42,
    maxMana = 70,
    energy = 28,
    maxEnergy = 60,
    defense = 15,
    unspentStatPoints = 0,
    skills = listOf(
        SkillUi("1", "Quick Slash", "A swift blade strike that exploits openings", 3, manaCost = 0, energyCost = 8, rarity = "COMMON"),
        SkillUi("2", "Shadow Step", "Teleport behind target, gaining advantage", 2, manaCost = 15, energyCost = 5, rarity = "RARE"),
        SkillUi("3", "Evasion", "Dodge the next incoming attack", 4, energyCost = 12, rarity = "UNCOMMON"),
        SkillUi("4", "Poison Edge", "Coat blade with toxin. Deals DOT for 3 turns", 1, manaCost = 10, energyCost = 5, rarity = "EPIC"),
    ),
    equipment = EquipmentUi(
        weapon = "Enchanted Shortsword",
        weaponStats = "+12 DMG  +3 STR",
        armor = "Shadow Cloak",
        armorStats = "+8 DEF  +2 CON",
        accessory = "None"
    ),
    statusEffects = listOf(
        StatusEffectUi("Shadowmeld", 3)
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
            QuestObjectiveUi("Speak with Old Mae", true),
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
    NpcUi("1", "Old Mae", "Tavern Keeper", "Weathered woman who warded every building on Main Street"),
    NpcUi("2", "Aldric", "Barkeep", "Retired adventurer and sole survivor of the Company of the Torch"),
)

private val sampleNpcDetails = NpcDetailsUi(
    id = "1",
    name = "Old Mae",
    archetype = "Tavern Keeper",
    description = "A weathered woman in her sixties with kind eyes and calloused hands stronger than they look. Runs the tavern at the heart of Crossroads.",
    lore = "Mae warded every building on Main Street herself, though she'll never admit it. Before she was a tavern keeper, she was something else entirely — the System remembers, even if she doesn't talk about it.",
    traits = listOf("Warm", "Practical", "Fierce", "Perceptive"),
    speechPattern = "Warm and direct, gives advice that sounds simple but isn't",
    motivations = listOf("Protect Crossroads", "Help newcomers find their place", "Keep the past buried"),
    relationshipStatus = "Friendly",
    affinity = 35,
    hasShop = true,
    shopName = "Mae's Tavern",
    shopItems = listOf(
        ShopItemUi("s1", "Hearty Stew", "Restores 30 HP", 10, 5),
        ShopItemUi("s2", "Warding Charm", "Minor protection enchantment", 75, 2),
        ShopItemUi("s3", "Dried Herbs", "Crafting ingredient", 5, 10),
    ),
    recentConversations = listOf(
        ConversationUi("What can you tell me about this town?", "Crossroads is what you make of it. Forty-seven souls, give or take. We look out for each other."),
        ConversationUi("What's in the stew?", "It's hot, and it's good. That's all you need to know."),
    )
)

private val sampleCombat = CombatUi(
    enemyName = "Slag Beetle",
    enemyHP = 18,
    enemyMaxHP = 45,
    enemyCondition = "wounded",
    portraitResource = "monster_ashlands_slag_beetle",
    roundNumber = 3,
    danger = 3,
    lootTier = "normal",
    description = "A dog-sized beetle with obsidian mandibles, its carapace glowing dull red from the volcanic heat beneath.",
    immunities = listOf("FIRE"),
    vulnerabilities = listOf("ICE"),
    resistances = listOf("PHYSICAL")
)

// ── Feed item sets for different game flows ──────────────────────

private val sampleExplorationFeed = listOf(
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
        npcName = "Old Mae",
        role = "Tavern Keeper",
        text = "That key... where did you find it? I haven't seen one of those in twenty years."
    ),
    FeedItem.QuestUpdate("The Sunken Vault", "New Quest"),
    FeedItem.GoldGained(50),
    FeedItem.PlayerMessage("Tell me about the Sunken Vault"),
    FeedItem.Narration(
        "Mae leans in close, her voice dropping to a whisper. " +
        "\"Below the river, past the old mill — there's a place the townsfolk " +
        "don't speak of. The water runs backwards there.\""
    ),
    FeedItem.LevelUp(3),
    FeedItem.ItemGained("Enchanted Shortsword", 1),
)

private val sampleCombatFeed = listOf(
    FeedItem.LocationChange("The Ashlands"),
    FeedItem.Narration(
        "The ground cracks beneath your boots, heat rising in shimmering waves. " +
        "Something moves between the slag heaps — low, fast, chitinous."
    ),
    FeedItem.CombatStart(CombatUi(
        enemyName = "Slag Beetle",
        enemyHP = 45,
        enemyMaxHP = 45,
        enemyCondition = "healthy",
        portraitResource = "monster_ashlands_slag_beetle",
        roundNumber = 1,
        danger = 3,
        lootTier = "normal",
        description = "A dog-sized beetle with obsidian mandibles, its carapace glowing dull red from the volcanic heat beneath.",
        immunities = listOf("FIRE"),
        vulnerabilities = listOf("ICE"),
        resistances = listOf("PHYSICAL")
    )),
    FeedItem.CombatAction("You swing your shortsword at the Slag Beetle — 12 damage!"),
    FeedItem.CombatAction("Slag Beetle snaps at you with molten mandibles — 8 damage blocked by armor."),
    FeedItem.CombatAction("You activate Quick Slash — critical hit! 24 damage!"),
    FeedItem.CompanionAside("Hank", "Nice one, kid. Now finish it before it calls friends."),
    FeedItem.CombatAction("You drive your blade through the carapace — 15 damage. Slag Beetle defeated!"),
    FeedItem.CombatEnd("Slag Beetle", true),
    FeedItem.XpGain(35),
    FeedItem.ItemGained("Beetle Carapace Fragment"),
    FeedItem.GoldGained(12),
)

private val sampleNpcMeetingFeed = listOf(
    FeedItem.LocationChange("Crossroads — Mae's Tavern"),
    FeedItem.Narration(
        "The tavern door swings open to warmth, lamplight, and the smell of stew. " +
        "A dozen patrons fill the low-ceilinged room — farmers, a hooded figure nursing a drink alone, " +
        "and behind the bar, a weathered woman with kind eyes who looks up as you enter."
    ),
    FeedItem.NpcDialogue(
        npcName = "Old Mae",
        role = "Tavern Keeper",
        text = "Well now. Fresh face in Crossroads — doesn't happen often. You look like you've been walking for a while. Sit down, I'll get you something hot."
    ),
    FeedItem.PlayerMessage("Thank you. What is this place?"),
    FeedItem.NpcDialogue(
        npcName = "Old Mae",
        role = "Tavern Keeper",
        text = "Crossroads. Population forty-seven — forty-eight now, I suppose. We're the last stop before the Ashlands to the east and the Thornwood to the north. Most folks here are just trying to get by."
    ),
    FeedItem.CompanionAside("Hank", "Ask her about the key. She knows something — I can tell by the way she keeps glancing at your belt pouch."),
    FeedItem.PlayerMessage("I found this old key in the ruins below town. Do you know anything about it?"),
    FeedItem.NpcDialogue(
        npcName = "Old Mae",
        role = "Tavern Keeper",
        text = "That key... where did you find it? I haven't seen one of those in twenty years. There's a vault beneath the old mill — sealed since before I came to Crossroads. Whatever's down there, the town sealed it for a reason."
    ),
    FeedItem.QuestUpdate("The Sunken Vault", "New Quest"),
    FeedItem.NpcDialogue(
        npcName = "Aldric",
        role = "Barkeep",
        text = "Mae's being polite. I'll be blunt: the last crew that went down there — only two came back. I was one of them. Take my advice and throw that key in the river."
    ),
    FeedItem.CompanionAside("Hank", "Heh. You're not gonna throw it in the river, are ya? Didn't think so."),
)

private val sampleLocationFeed = listOf(
    FeedItem.Narration("You step through the portal, the air shifting from dry heat to damp cold."),
    FeedItem.LocationChange("Greenreach Forest"),
    FeedItem.Narration(
        "Towering trees stretch overhead, their canopy so thick that only " +
        "scattered beams of light reach the mossy ground below. The air smells " +
        "of pine and something older — something watching."
    ),
    FeedItem.CompanionAside("Hank", "Great. Trees. My wings don't work so good when it's humid, just so you know."),
    FeedItem.PlayerMessage("What's that sound?"),
    FeedItem.Narration("A low chittering rises from the undergrowth. Something is tracking you."),
    FeedItem.LocationChange("Greenreach — Deep Hollow"),
    FeedItem.Narration("The trees grow denser. Bioluminescent fungi line the path, pulsing slowly like a heartbeat."),
    FeedItem.NpcDialogue(
        npcName = "Lin Wei",
        role = "Ranger",
        text = "Stay on the path. The things that live off-trail don't negotiate."
    ),
)

private val sampleOnboardingMessages = listOf(
    OnboardingMessage("model", "You walk into a dim room that smells like mildew and old wood — behind a cluttered desk sits a woman in a wrinkled blouse, glasses on a chain, squinting at a monitor from 1997. She looks up. \"Oh great, another one. Let's skip the pleasantries — what's your name?\""),
    OnboardingMessage("user", "Kael."),
    OnboardingMessage("model", "\"Kael. Sure. Very heroic. Alright Kael, what's your story? What were you doing before you wound up in my lobby?\""),
    OnboardingMessage("user", "I was a ranger. Tracked things in the deep woods."),
    OnboardingMessage("model", "\"A ranger. Riveting. Well, at least you can handle yourself outdoors, I suppose. Now then — we've got four worlds available right now. Pick your poison. System Integration — that's the big one. Towers, tiers, the whole progression fantasy. High Fantasy — classic swords and sorcery. Dungeon Crawler — floors, bosses, loot. Or Quiet Life — post-apocalypse, but the cozy kind.\""),
    OnboardingMessage("user", "System Integration."),
    OnboardingMessage("model", "\"Naturally. Everyone picks the hard one.\"")
)

// ══════════════════════════════════════════════════════════════════
//  1. LOBBY SCREEN
// ══════════════════════════════════════════════════════════════════

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "1 - Lobby")
@Composable
private fun LobbyScreenPreview() {
    MaterialTheme(typography = appTypography()) {
        LobbyContent()
    }
}

// ══════════════════════════════════════════════════════════════════
//  2. ONBOARDING — THE RECEPTIONIST
// ══════════════════════════════════════════════════════════════════

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "2 - Receptionist (Conversation)")
@Composable
private fun OnboardingConversationPreview() {
    MaterialTheme(typography = appTypography()) {
        OnboardingContent(messages = sampleOnboardingMessages)
    }
}

// ══════════════════════════════════════════════════════════════════
//  3. LOADING GAME
// ══════════════════════════════════════════════════════════════════

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "3a - Loading (System Integration)")
@Composable
private fun LoadingSystemPreview() {
    MaterialTheme(typography = appTypography()) {
        LoadingGameContent(
            seedId = "integration",
            loadingStatus = "Creating game session..."
        )
    }
}

// ══════════════════════════════════════════════════════════════════
//  4. GAME — EXPLORATION & STORY FEED
// ══════════════════════════════════════════════════════════════════

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "4a - Story Feed (Exploration)")
@Composable
private fun ExplorationFeedPreview() {
    MaterialTheme(typography = appTypography()) {
        Box(
            modifier = Modifier.fillMaxSize().parchmentBackground()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 110.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(sampleExplorationFeed, key = { it.id }) { item ->
                    PreviewFeedItemView(item)
                }
            }

            ControlBar(
                isListening = true,
                isGeminiSpeaking = false,
                isMusicEnabled = true,
                companionName = "Hank",
                playerAvatarBytes = null,
                onMicPressed = {},
                onMenuPressed = {},
                onMusicToggle = {},
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  5. GAME — LOCATION CHANGE
// ══════════════════════════════════════════════════════════════════

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "5 - Location Exploration")
@Composable
private fun LocationExplorationPreview() {
    MaterialTheme(typography = appTypography()) {
        Box(
            modifier = Modifier.fillMaxSize().parchmentBackground()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 110.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(sampleLocationFeed, key = { it.id }) { item ->
                    PreviewFeedItemView(item)
                }
            }

            ControlBar(
                isListening = true,
                isGeminiSpeaking = true,
                isMusicEnabled = true,
                companionName = "Hank",
                playerAvatarBytes = null,
                onMicPressed = {},
                onMenuPressed = {},
                onMusicToggle = {},
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  6. GAME — MEETING NPCs
// ══════════════════════════════════════════════════════════════════

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "6 - NPC Conversation")
@Composable
private fun NpcMeetingPreview() {
    MaterialTheme(typography = appTypography()) {
        Box(
            modifier = Modifier.fillMaxSize().parchmentBackground()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 110.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(sampleNpcMeetingFeed, key = { it.id }) { item ->
                    PreviewFeedItemView(item)
                }
            }

            ControlBar(
                isListening = true,
                isGeminiSpeaking = false,
                isMusicEnabled = true,
                companionName = "Hank",
                playerAvatarBytes = null,
                onMicPressed = {},
                onMenuPressed = {},
                onMusicToggle = {},
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  7. GAME — COMBAT
// ══════════════════════════════════════════════════════════════════

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "7a - Combat (Active)")
@Composable
private fun CombatActivePreview() {
    MaterialTheme(typography = appTypography()) {
        Box(
            modifier = Modifier.fillMaxSize().parchmentBackground()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 110.dp),
                contentPadding = PaddingValues(top = 80.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(sampleCombatFeed.take(6), key = { it.id }) { item ->
                    PreviewFeedItemView(item)
                }
            }

            // Combat overlay at top
            CombatOverlay(
                combat = sampleCombat,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            ControlBar(
                isListening = true,
                isGeminiSpeaking = false,
                isMusicEnabled = true,
                companionName = "Hank",
                playerAvatarBytes = null,
                onMicPressed = {},
                onMenuPressed = {},
                onMusicToggle = {},
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "7b - Combat Feed (Victory)")
@Composable
private fun CombatVictoryFeedPreview() {
    MaterialTheme(typography = appTypography()) {
        Box(
            modifier = Modifier.fillMaxSize().parchmentBackground()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 110.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(sampleCombatFeed, key = { it.id }) { item ->
                    PreviewFeedItemView(item)
                }
            }

            ControlBar(
                isListening = true,
                isGeminiSpeaking = false,
                isMusicEnabled = true,
                companionName = "Hank",
                playerAvatarBytes = null,
                onMicPressed = {},
                onMenuPressed = {},
                onMusicToggle = {},
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  8. HUD — CHARACTER SHEET
// ══════════════════════════════════════════════════════════════════

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "8a - Character Sheet")
@Composable
private fun CharacterSheetPreview() {
    MaterialTheme(typography = appTypography()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.background)
        ) {
            HudPreviewContent(
                stats = sampleStats,
                fullSheet = sampleFullSheet,
                inventory = sampleInventory,
                quests = sampleQuests,
                npcsHere = sampleNpcs
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "8b - Character Sheet (New Player)")
@Composable
private fun CharacterSheetNewPlayerPreview() {
    MaterialTheme(typography = appTypography()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.background)
        ) {
            HudPreviewContent(
                stats = PlayerStatsUi(
                    name = "New Adventurer",
                    level = 1,
                    playerClass = "",
                    hp = 60,
                    maxHp = 60,
                    energy = 40,
                    maxEnergy = 40,
                    xp = 0,
                    xpToNext = 100,
                    stats = mapOf("Strength" to 10, "Dexterity" to 10, "Wisdom" to 10, "Charisma" to 10)
                ),
                fullSheet = null,
                inventory = emptyList(),
                quests = emptyList(),
                npcsHere = sampleNpcs
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  9. HUD — INVENTORY
// ══════════════════════════════════════════════════════════════════

@Preview(showBackground = true, widthDp = 390, heightDp = 500, name = "9 - Inventory Grid")
@Composable
private fun InventoryPreview() {
    MaterialTheme(typography = appTypography()) {
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

// ══════════════════════════════════════════════════════════════════
//  10. HUD — QUESTS
// ══════════════════════════════════════════════════════════════════

@Preview(showBackground = true, widthDp = 390, heightDp = 500, name = "10 - Quest Panel")
@Composable
private fun QuestPanelPreview() {
    MaterialTheme(typography = appTypography()) {
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

// ══════════════════════════════════════════════════════════════════
//  11. NPC DETAIL SHEET
// ══════════════════════════════════════════════════════════════════

@Preview(showBackground = true, widthDp = 390, heightDp = 700, name = "11 - NPC Detail Sheet")
@Composable
private fun NpcDetailSheetPreview() {
    MaterialTheme(typography = appTypography()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.background)
                .padding(12.dp)
        ) {
            npcDetailPreviewContent(sampleNpcDetails).invoke()
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  12. NOTIFICATION CARDS (all types)
// ══════════════════════════════════════════════════════════════════

@Preview(showBackground = true, widthDp = 390, heightDp = 500, name = "12 - Notification Cards")
@Composable
private fun NotificationCardsPreview() {
    MaterialTheme(typography = appTypography()) {
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

// ══════════════════════════════════════════════════════════════════
//  13. MENU OPEN
// ══════════════════════════════════════════════════════════════════

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "13 - Menu Open")
@Composable
private fun MenuOpenPreview() {
    MaterialTheme(typography = appTypography()) {
        Box(modifier = Modifier.fillMaxSize().parchmentBackground()) {
            // Story feed behind
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 110.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(sampleExplorationFeed.take(5), key = { it.id }) { item ->
                    PreviewFeedItemView(item)
                }
            }

            // Semi-transparent scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppColors.overlay)
            )

            // Menu pinned to bottom
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                MenuContent()
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  14. RECONNECTING OVERLAY
// ══════════════════════════════════════════════════════════════════

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "13 - Reconnecting")
@Composable
private fun ReconnectingPreview() {
    MaterialTheme(typography = appTypography()) {
        Box(modifier = Modifier.fillMaxSize().parchmentBackground()) {
            // Faded story feed behind overlay
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(sampleExplorationFeed.take(4), key = { it.id }) { item ->
                    PreviewFeedItemView(item)
                }
            }
            ReconnectingOverlay()
        }
    }
}
