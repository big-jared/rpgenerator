# Architecture Refactor: Unified Tool System + World Lore + GM Prompts

## 1. Problem Analysis

### Three Disconnected Tool Systems

RPGenerator currently has three independent tool implementations that overlap, diverge, and cannot share logic:

**System A: `GameToolsImpl`** (core, 22 methods)
- Used by the orchestrator's coordinated path
- Takes `GameState` as a parameter (stateless reads)
- Returns typed Kotlin data classes (`PlayerStatusResult`, `CombatResolution`, etc.)
- Includes an LLM-powered `analyzeIntent()` call — making it a hybrid tool/agent system
- Lives in `core/` (multiplatform)

**System B: `GeminiToolContractImpl`** (core/gemini, 21 methods)
- Used by Gemini Live API sessions
- Holds mutable `gameState` internally (stateful)
- Returns `ToolResult` with `JsonObject` data
- Emits `GameEvent` lists from tool calls
- Many methods are stubs ("not yet implemented", "placeholder")
- Lives in `core/` but only used from `cli/`

**System C: `McpHandler.executeTool()`** (server, 1400+ lines)
- Used by Claude Code / MCP clients
- Duplicates all state-reading logic with manual `buildJsonObject` blocks
- Routes through the game engine for actions (separate codepath from A or B)
- Has its own session state management
- Lives in `server/`

### Why This Fails

| Bug Category | Root Cause |
|---|---|
| State desync between narration and mechanics | System A executes mechanics, System B/C read stale state |
| Missing tool implementations | Gemini stubs never got wired to real logic |
| 3 LLM calls per player action | Intent classifier (tools) → GM plan (agent) → Narrator render (agent) |
| GM has zero tool access | GM generates JSON blobs, orchestrator interprets them |
| World lore underutilized | WorldSeed data isn't queryable — only used in narrator system prompt |
| Custom class names not persisted | `setClass` doesn't exist as a tool — orchestrator handles it ad-hoc |
| Tutorial has no exit | `completeTutorial` tool doesn't exist |
| Narration invents items | Narrator has no access to mechanical results — it hallucinates loot |

### The 3-Call Pipeline Problem

Current flow for every player action:

```
Player Input
  → [LLM Call 1] tools.analyzeIntent() — classifies intent via LLM
  → [LLM Call 2] gameMasterAgent.planScene() — generates JSON scene plan
  → [LLM Call 3] narratorAgent.renderScene() — renders plan into prose
```

This causes:
- ~3-5 second latency per action (3 sequential LLM round-trips)
- Information loss between stages (GM plan → Narrator loses mechanical details)
- The narrator sometimes contradicts mechanical results because it only sees the GM's prose description
- Intent classification is often wrong because it lacks the context the GM has

---

## 2. Unified GameToolContract

### Design Principles

1. **Tools are stateless functions.** State is passed in. Read-only tools return `ToolResult`. Mutating tools return `ToolOutcome` with new state + events.
2. **Single source of truth.** One tool interface, one implementation. Adapter layers for different consumers.
3. **JSON in, JSON out.** All tools accept/return JSON — the universal format for LLM tool use.
4. **AI directives are part of the tool definition.** Each tool includes guidance for when/how the GM should use it.

### Core Types

```kotlin
// In core/api/
@Serializable
data class ToolDef(
    val name: String,
    val description: String,
    val parameters: List<ToolParam>,
    val returns: String,           // JSON schema description
    val aiDirective: String        // When/how to use this tool
)

@Serializable
data class ToolParam(
    val name: String,
    val type: String,              // "string", "int", "boolean"
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String>? = null
)

@Serializable
data class ToolResult(
    val success: Boolean,
    val data: JsonObject? = null,
    val error: String? = null
)

@Serializable
data class ToolOutcome(
    val success: Boolean,
    val data: JsonObject? = null,
    val error: String? = null,
    val newState: GameState,
    val events: List<GameEvent> = emptyList()
)
```

### Interface

```kotlin
interface UnifiedToolContract {
    /** Get all tool definitions for LLM function calling */
    fun getToolDefinitions(): List<ToolDef>

    /** Execute a tool by name with JSON arguments */
    suspend fun executeTool(
        name: String,
        args: JsonObject,
        state: GameState
    ): ToolOutcome
}
```

---

## 3. Complete Tool Catalog

### 3.1 Lore & Knowledge

#### `queryLore`

The unified knowledge tool. Replaces the need for dozens of small lookup tools.

| Field | Value |
|---|---|
| **Name** | `queryLore` |
| **Description** | Look up game rules, world data, classes, skills, progression, biomes, narration guidelines, and more. Use this before making decisions that depend on game mechanics or world flavor. |
| **Parameters** | `category` (string, required, enum — see below), `filter` (string, optional — narrows results) |
| **Returns** | `{ "category": "...", "data": { ... } }` — shape varies by category |
| **AI Directive** | Call this BEFORE narrating any class selection, skill use, combat, or world-building. Do not guess game mechanics — look them up. When narrating, call `category="narration_guide"` or `category="examples"` to match the world's tone. |
| **Source** | New implementation aggregating: `TierSystem`, `SkillDatabase`, `WorldSeeds`, `LootTables`, `Biome` enum, `NPCArchetype` enum, `QuestType` enum, `StoryPlanningService`, plus new narrative data |

**Categories:**

| Category | Filter Behavior | Returns | Source |
|---|---|---|---|
| `classes` | Optional: class name or archetype (e.g., `"healer"`, `"combat"`) | All 15 classes with displayName, description, statBonuses, archetype, D-grade evolution options | `PlayerClass` enum, `ClassArchetype` enum |
| `skills` | Optional: skill name, category, or rarity (e.g., `"fire"`, `"combat"`, `"rare"`) | Matching skills with id, name, description, costs, effects, fusionTags, evolutionPaths | `SkillDatabase.allSkills` |
| `progression` | Optional: grade name (e.g., `"D_GRADE"`) | 6 grades with levelRange, description, statPointsAwarded, skillSlotUnlocked | `Grade` enum, `TierUpEvent.create()` |
| `loot` | Optional: enemy type or danger level (e.g., `"goblin"`, `"danger_5"`) | Loot tables with drops, rarities, gold ranges | `LootTables` |
| `biomes` | Optional: biome name (e.g., `"FOREST"`) | 12 biomes from enum | `Biome` enum |
| `world` | None | Current WorldSeed: powerSystem, worldState, corePlot, systemVoice, tone, themes, inspirations | `WorldSeeds.byId(seedId)` |
| `npc_archetypes` | Optional: archetype name (e.g., `"MERCHANT"`) | 11 archetypes with name, typical roles, behavioral patterns | `NPCArchetype` enum + archetype descriptions |
| `quest_templates` | Optional: quest type (e.g., `"KILL"`) | 8 quest types with descriptions, typical objectives, reward patterns | `QuestType` enum |
| `tutorial` | None | Current world's tutorial objectives, guide NPC, completion rewards, exit description | `WorldSeed.tutorial` |
| `fusions` | Optional: skill ID to check available fusions | Fusion recipes: inputs, level requirements, results, discovery hints | `SkillDatabase.fusionRecipes` |
| `insights` | Optional: action type (e.g., `"sword_slash"`) | Insight thresholds: action type → skill unlock at partial/full counts | `SkillDatabase.insightThresholds` |
| `narration_guide` | Optional: world ID (e.g., `"integration"`) | Writing do's/don'ts, pacing rules, genre conventions for the world | New data (see section 3.1.1) |
| `system_voice` | Optional: world ID | How the System speaks: personality, message format, examples, attitude | `WorldSeed.systemVoice` |
| `examples` | Required: situation type (e.g., `"combat"`, `"class_selection"`, `"death"`, `"level_up"`, `"exploration"`, `"npc_dialogue"`, `"tutorial"`) | 2-3 gold-standard narration passages per situation per world | New data (see section 3.1.2) |

**Example queries and responses:**

```json
// Query: queryLore(category="classes", filter="healer")
{
  "category": "classes",
  "data": {
    "HEALER": {
      "displayName": "Healer",
      "description": "Life is sacred. Mend wounds, cure ailments, restore what was broken.",
      "archetype": "SUPPORT",
      "statBonuses": { "wisdom": 5, "charisma": 3, "constitution": 2 },
      "evolutions": [
        { "name": "Life Weaver", "description": "Restore even the nearly dead" },
        { "name": "Combat Medic", "description": "Heal and harm in equal measure" },
        { "name": "Purifier", "description": "Cleanse corruption, cure the incurable" }
      ]
    }
  }
}

// Query: queryLore(category="skills", filter="fire")
{
  "category": "skills",
  "data": {
    "skills": [
      {
        "id": "fireball",
        "name": "Fireball",
        "description": "Hurl a ball of flame at your enemy.",
        "rarity": "COMMON",
        "manaCost": 20,
        "baseCooldown": 1,
        "effects": [{ "type": "Damage", "base": 30, "scaling": "INTELLIGENCE x0.7", "damageType": "FIRE" }],
        "fusionTags": ["magic", "fire", "offensive"],
        "evolutionPaths": [
          { "name": "Inferno", "requires": "INT >= 25" },
          { "name": "Flame Lance", "requires": "Level >= 15" }
        ]
      },
      {
        "id": "flame_blade",
        "name": "Flame Blade",
        "description": "Wreath your weapon in flame for devastating fire damage.",
        "rarity": "RARE",
        "energyCost": 15, "manaCost": 15,
        "effects": [...]
      }
    ]
  }
}

// Query: queryLore(category="narration_guide", filter="integration")
{
  "category": "narration_guide",
  "data": {
    "worldId": "integration",
    "genre": "System Apocalypse",
    "do": [
      "Visceral sensory details — blood, pain, adrenaline, the wrongness of monsters",
      "Punchy sentences. No filler. Every word earns its place.",
      "Show don't tell: 'Blood drips from your blade' not 'You attacked successfully'",
      "Leveling up feels GOOD — intoxicating, addictive",
      "The tutorial is sterile but the stakes are real"
    ],
    "dont": [
      "Make it feel safe or cozy",
      "Use passive voice",
      "Over-explain mechanics in prose",
      "Add other survivors to a SOLO tutorial",
      "Skip class selection",
      "Be generic — every detail should feel specific and earned",
      "Start with 'You find yourself...'",
      "End with bulleted action lists"
    ],
    "pacing": "Every beat earns its place. Quiet moments before violence. Level-ups are dopamine hits. Death is unglamorous.",
    "genre_conventions": "Survival horror meets power fantasy. The world is ending, but you're getting stronger. Everything wants to kill you.",
    "tone_keywords": ["brutal", "visceral", "desperate", "empowering"]
  }
}

// Query: queryLore(category="examples", filter="combat")
{
  "category": "examples",
  "data": {
    "situation": "combat",
    "examples": {
      "integration": [
        "The rift-spawn lunges. You sidestep, blade catching it across the throat. Black ichor sprays. The thing crumples, and the System's cold confirmation burns behind your eyes: [Kill Confirmed: +15 XP]. Your hands don't shake anymore.",
        "You drive the blade down. Bone crunches. The creature stops twitching. Something hot and electric floods your veins — the System, rewarding violence. [Level Up]. The rush is better than anything you've ever felt."
      ],
      "crawler": [
        "You cave in the goblin's skull with a satisfying CRUNCH. Somewhere, an alien audience cheers. [KILL CAM ACTIVATED! BloodDrinker_9000 sends: 'MORE!'] A rusty knife clatters to the floor — sponsor gift.",
        "The monster explodes into loot confetti. Because of course it does. [ACHIEVEMENT UNLOCKED: First Blood! Your sponsors are LOVING this!] A floating camera zooms in on the gore. You try not to think about the viewer count."
      ],
      "tutorial": [
        "Your sword meets the guardian's shield with a crack that echoes through the chamber. The Tower watches. You press harder. The guardian stumbles. One more strike. [Floor Guardian Defeated. The Tower remembers.]",
        "The blow lands clean. The floor guardian dissolves into golden light. [Achievement: First Blood. The Tower remembers.] Somewhere above, a hundred more floors wait."
      ],
      "quiet_life": [
        "The wolf lunges from the treeline. Old instincts take over — you step aside, blade out, one clean cut. It yelps and bolts. Your hands are steadier than they should be. The war taught you that much. [Skill Used: Quick Slash]",
        "You swing the hoe like a weapon. Old muscle memory. The rock-beetle cracks open. [Threat Neutralized. Garden Defended.] Mae would be proud. Or worried."
      ]
    }
  }
}

// Query: queryLore(category="system_voice", filter="crawler")
{
  "category": "system_voice",
  "data": {
    "worldId": "crawler",
    "personality": "Game show host from hell. Enthusiastic about your suffering.",
    "messageStyle": "Theatrical announcements. Achievement unlocked energy. Sponsor messages.",
    "format": "ALL CAPS excitement, bracketed, sponsor names, viewer engagement metrics",
    "exampleMessages": [
      "[ACHIEVEMENT UNLOCKED: First Kill! Your sponsors are LOVING this!]",
      "[Sponsor Gift from BloodDrinker_9000: Rusty Knife! 'Make it messy!']",
      "[FLOOR ANNOUNCEMENT: Only 847 crawlers remaining! Pick up the pace, people!]",
      "[New Follower! GalacticKaren wants to see you suffer!]"
    ],
    "attitude": "You're content. Good content gets rewarded. Bad content gets forgotten."
  }
}

// Query: queryLore(category="tutorial")
{
  "category": "tutorial",
  "data": {
    "isSolo": true,
    "objectives": [
      { "id": "select_class", "description": "Select your class", "type": "class_selection" },
      { "id": "kill_monsters", "description": "Kill 10 Rift-spawn", "type": "kill_count", "target": 10 },
      { "id": "reach_level", "description": "Reach Level 5", "type": "reach_level", "target": 5 }
    ],
    "guide": {
      "name": "Integration Protocol",
      "personality": "Clinical, efficient, utterly without empathy. It's a program, not a person.",
      "dialogueOnMeet": "Integration complete. You have been allocated to Tutorial Instance 7,291,847...",
      "exampleLines": ["Query irrelevant. Proceed with class selection.", ...]
    },
    "completionReward": "Tutorial Completion Bonus: +500 XP, Basic Equipment Cache, Return to Earth",
    "exitDescription": "The white void cracks. Reality reasserts itself. You're back on Earth..."
  }
}
```

#### Section 3.1.1: Narration Guide Data (New)

Each world gets a `NarrationGuide` data class stored alongside `WorldSeeds`:

```kotlin
data class NarrationGuide(
    val worldId: String,
    val genre: String,
    val doList: List<String>,       // Writing principles to follow
    val dontList: List<String>,     // Things to avoid
    val pacing: String,             // Pacing philosophy
    val genreConventions: String,   // What makes this genre distinct
    val toneKeywords: List<String>  // Quick tone reference
)
```

This is populated from the existing `narratorPrompt` field in each WorldSeed — the data is already there, just not queryable.

#### Section 3.1.2: Example Narration Data (New)

Gold-standard narration passages per world per situation type:

```kotlin
data class NarrationExamples(
    val worldId: String,
    val examples: Map<String, List<String>>  // situation type -> passages
)
```

Situation types: `combat`, `exploration`, `class_selection`, `death`, `level_up`, `npc_dialogue`, `tutorial`, `skill_use`.

These are hand-written (or distilled from good playtest output) to serve as few-shot examples for the GM.

### 3.2 Live World State

Tools that return dynamic data from the current game session (not static lore).

#### `getTutorialState`

| Field | Value |
|---|---|
| **Name** | `getTutorialState` |
| **Description** | Get current tutorial progress: which objectives are complete, which is next, current kill count. |
| **Parameters** | None |
| **Returns** | `{ "inTutorial": bool, "objectives": [...], "nextObjective": {...}, "canComplete": bool }` |
| **AI Directive** | Call this at the start of every turn during tutorial. Use to determine if the player should be guided toward class selection, combat practice, or tutorial exit. |
| **Source** | `GameState.activeQuests["quest_survive_tutorial"]` + tutorial definition from WorldSeed |

#### `getStoryState`

| Field | Value |
|---|---|
| **Name** | `getStoryState` |
| **Description** | Get current story context: act, recent events, active plot threads, foreshadowing hooks. |
| **Parameters** | None |
| **Returns** | `{ "currentAct": "...", "recentEvents": [...], "plotThreads": [...], "foreshadowing": [...] }` |
| **AI Directive** | Call this when narrating scene transitions, quiet moments, or any time you want to weave in story threads. Use foreshadowing hooks when they fit naturally — never force them. |
| **Source** | `StoryFoundation.narratorContext`, `MainStoryArc.getCurrentAct()`, event log |

### 3.3 State Queries (Read-Only)

These tools read current game state. The GM calls them to understand the situation before acting.

#### `getPlayerStats`

| Field | Value |
|---|---|
| **Name** | `getPlayerStats` |
| **Description** | Get player's core stats: level, XP, HP, mana, energy, location, class, grade. |
| **Parameters** | None |
| **Returns** | `{ "name": "...", "level": N, "xp": N, "xpToNextLevel": N, "hp": N, "maxHP": N, "mana": N, "maxMana": N, "energy": N, "maxEnergy": N, "location": "...", "class": "...", "grade": "..." }` |
| **AI Directive** | Call at the start of each turn to know the player's current state. Reference HP before allowing dangerous actions. |
| **Source** | `GameState.characterSheet` |

#### `getCharacterSheet`

| Field | Value |
|---|---|
| **Name** | `getCharacterSheet` |
| **Description** | Get the full character sheet: stats, skills, equipment, status effects, backstory. |
| **Parameters** | None |
| **Returns** | `{ "level": N, "class": "...", "grade": "...", "stats": {...}, "skills": [...], "equipment": {...}, "statusEffects": [...], "backstory": "..." }` |
| **AI Directive** | Call when the player asks about their character, before skill-related narration, or when you need to reference equipment in combat descriptions. |
| **Source** | `GameState.characterSheet` |

#### `getInventory`

| Field | Value |
|---|---|
| **Name** | `getInventory` |
| **Description** | Get all items in the player's inventory with details. |
| **Parameters** | None |
| **Returns** | `{ "items": [{ "id": "...", "name": "...", "type": "...", "quantity": N, "rarity": "..." }], "usedSlots": N, "maxSlots": N, "gold": N }` |
| **AI Directive** | Call when the player asks about items, before using or equipping items, or when narrating loot. ONLY reference items that appear in this result. |
| **Source** | `GameState.characterSheet.inventory` |

#### `getActiveQuests`

| Field | Value |
|---|---|
| **Name** | `getActiveQuests` |
| **Description** | Get all active quests with objectives and progress. |
| **Parameters** | None |
| **Returns** | `{ "quests": [{ "id": "...", "name": "...", "description": "...", "objectives": [{ "description": "...", "progress": N, "target": N, "complete": bool }], "canComplete": bool }] }` |
| **AI Directive** | Call every turn. Guide the player toward their next incomplete objective. When all objectives are done, prompt quest completion. |
| **Source** | `GameState.activeQuests` |

#### `getQuestDetails`

| Field | Value |
|---|---|
| **Name** | `getQuestDetails` |
| **Description** | Get detailed info about a specific quest including rewards. |
| **Parameters** | `questId` (string, required) |
| **Returns** | `{ "id": "...", "name": "...", "description": "...", "objectives": [...], "rewards": { "xp": N, "items": [...], "gold": N }, "giver": "..." }` |
| **AI Directive** | Call when the player asks about a specific quest or when narrating quest progress/completion. |
| **Source** | `GameState.activeQuests[questId]` |

#### `getNPCsHere`

| Field | Value |
|---|---|
| **Name** | `getNPCsHere` |
| **Description** | Get all NPCs at the player's current location. |
| **Parameters** | None |
| **Returns** | `{ "npcs": [{ "id": "...", "name": "...", "archetype": "...", "hasShop": bool, "hasQuests": bool }] }` |
| **AI Directive** | Call when the player arrives at a location, wants to talk to someone, or when narrating scenes with NPCs. Reference NPCs by name from this list — do not invent NPCs. |
| **Source** | `GameState.getNPCsAtCurrentLocation()` |

#### `getNPCDetails`

| Field | Value |
|---|---|
| **Name** | `getNPCDetails` |
| **Description** | Get detailed info about a specific NPC: personality, relationship, recent conversations. |
| **Parameters** | `npcName` (string, required) |
| **Returns** | `{ "id": "...", "name": "...", "archetype": "...", "personality": { "traits": [...], "speechPattern": "...", "motivations": [...] }, "relationship": { "affinity": N, "status": "..." }, "recentConversations": [...], "lore": "...", "hasShop": bool, "shopItems": [...] }` |
| **AI Directive** | Call before generating NPC dialogue. Match their speech pattern and personality traits. Adjust tone based on relationship status. |
| **Source** | `GameState.findNPCByName()` |

#### `getLocation`

| Field | Value |
|---|---|
| **Name** | `getLocation` |
| **Description** | Get current location details: description, features, danger level, biome, lore. |
| **Parameters** | None |
| **Returns** | `{ "id": "...", "name": "...", "description": "...", "biome": "...", "danger": N, "features": [...], "connections": [...], "lore": "..." }` |
| **AI Directive** | Call when describing scenes. Use features and lore for environmental details. Danger level determines combat encounter intensity. |
| **Source** | `GameState.currentLocation` |

#### `getConnectedLocations`

| Field | Value |
|---|---|
| **Name** | `getConnectedLocations` |
| **Description** | Get locations the player can travel to from their current position. |
| **Parameters** | None |
| **Returns** | `{ "locations": [{ "id": "...", "name": "...", "biome": "...", "danger": N, "discovered": bool }] }` |
| **AI Directive** | Call when the player wants to move. Only allow movement to locations in this list. If the player describes a place not in the list, use `generateLocation` to create it. |
| **Source** | `LocationManager` + `GameState.customLocations` |

#### `getEventHistory`

| Field | Value |
|---|---|
| **Name** | `getEventHistory` |
| **Description** | Get recent game events for context. |
| **Parameters** | `limit` (int, optional, default 20) |
| **Returns** | `{ "events": [{ "type": "...", "text": "...", "timestamp": N }] }` |
| **AI Directive** | Call for context on what happened recently. Use to avoid repeating narration or to make callbacks to earlier events. |
| **Source** | In-memory event log |

### 3.4 Combat

#### `getCombatTargets`

| Field | Value |
|---|---|
| **Name** | `getCombatTargets` |
| **Description** | Get valid combat targets at the current location. |
| **Parameters** | None |
| **Returns** | `{ "targets": [{ "name": "...", "type": "...", "dangerLevel": "..." }], "inCombat": bool }` |
| **AI Directive** | Call before any combat action. If no targets exist and the player wants to fight, use `spawnEnemy` first. |
| **Source** | New — derived from location danger + spawned enemies |

#### `spawnEnemy`

| Field | Value |
|---|---|
| **Name** | `spawnEnemy` |
| **Description** | Spawn an enemy appropriate for the current location and player level. |
| **Parameters** | `enemyType` (string, optional — hint for what kind of enemy), `count` (int, optional, default 1) |
| **Returns** | `{ "enemies": [{ "name": "...", "type": "...", "level": N }] }` |
| **AI Directive** | Call when combat should happen but no enemies are present. Use location biome and danger level to determine appropriate enemies. During tutorial, spawn tutorial-appropriate enemies. |
| **Source** | New — uses `RulesEngine` + location data |

#### `resolveAttack`

| Field | Value |
|---|---|
| **Name** | `resolveAttack` |
| **Description** | Resolve a physical attack against a target. Returns damage, XP, loot. |
| **Parameters** | `target` (string, required) |
| **Returns** | `{ "damage": N, "xpGained": N, "levelUp": bool, "newLevel": N, "targetDefeated": bool, "loot": [{ "name": "...", "rarity": "..." }], "gold": N }` |
| **AI Directive** | Call when the player attacks. ONLY describe loot items listed in the result. If `targetDefeated` is true, the enemy is dead — no counter-attack. If `levelUp`, narrate the rush of power. |
| **Source** | `RulesEngine.calculateCombatOutcome()` + `LootTables` |

#### `resolveSkillUse`

| Field | Value |
|---|---|
| **Name** | `resolveSkillUse` |
| **Description** | Use a skill in combat. Resolves effects (damage, healing, buffs). |
| **Parameters** | `skillId` (string, required), `target` (string, optional) |
| **Returns** | `{ "skillName": "...", "effects": [{ "type": "...", "amount": N }], "resourceCost": { "mana": N, "energy": N }, "success": bool }` |
| **AI Directive** | Call when the player uses a named skill. Describe the skill's visual effect based on its element/type. Reference the skill's description for flavor. |
| **Source** | `SkillCombatService` + `SkillDatabase` |

### 3.5 Movement

#### `moveToLocation`

| Field | Value |
|---|---|
| **Name** | `moveToLocation` |
| **Description** | Move the player to a connected location. |
| **Parameters** | `locationName` (string, required) |
| **Returns** | `{ "success": bool, "newLocation": { "name": "...", "description": "...", "danger": N, "features": [...] }, "npcsHere": [...] }` |
| **AI Directive** | Call when the player wants to travel. After moving, describe the new location using its features. Mention any NPCs present. |
| **Source** | `GameState.moveToLocation()` + `LocationManager` |

#### `generateLocation`

| Field | Value |
|---|---|
| **Name** | `generateLocation` |
| **Description** | Generate a new location connected to the current one. |
| **Parameters** | `description` (string, required — what the player is looking for or what direction they're going) |
| **Returns** | `{ "location": { "name": "...", "description": "...", "biome": "...", "danger": N, "features": [...] } }` |
| **AI Directive** | Call when the player wants to explore somewhere that doesn't exist yet. Make the generated location fit the world's biome and danger progression. |
| **Source** | `LocationGeneratorAgent` |

#### `completeTutorial`

| Field | Value |
|---|---|
| **Name** | `completeTutorial` |
| **Description** | End the tutorial and transition to the main world. Awards completion rewards. |
| **Parameters** | None |
| **Returns** | `{ "rewards": { "xp": N, "items": [...] }, "exitDescription": "...", "newLocation": { ... } }` |
| **AI Directive** | Call ONLY when all tutorial objectives are complete (check with `getTutorialState`). Read the `exitDescription` and use it as the basis for your transition narration. This is a major story moment — give it weight. |
| **Source** | New — uses `WorldSeed.tutorial.completionReward` + `WorldSeed.tutorial.exitDescription` |

### 3.6 NPCs

#### `talkToNPC`

| Field | Value |
|---|---|
| **Name** | `talkToNPC` |
| **Description** | Initiate or continue conversation with an NPC. Returns NPC details for generating dialogue. |
| **Parameters** | `npcName` (string, required), `playerDialogue` (string, optional — what the player said) |
| **Returns** | `{ "npc": { "name": "...", "personality": {...}, "speechPattern": "...", "relationship": "...", "recentHistory": [...] }, "conversationSaved": bool }` |
| **AI Directive** | Call before generating any NPC dialogue. Use the returned personality and speechPattern to voice the NPC. Match their archetype — a merchant talks about goods, a guard talks about threats. Adjust warmth based on relationship status. |
| **Source** | `GameState.findNPCByName()` + `NPC.addConversation()` |

#### `spawnNPC`

| Field | Value |
|---|---|
| **Name** | `spawnNPC` |
| **Description** | Create a new NPC at the current location. |
| **Parameters** | `name` (string, required), `role` (string, required — archetype), `personality` (string, required), `lore` (string, optional) |
| **Returns** | `{ "npc": { "id": "...", "name": "...", "archetype": "...", ... } }` |
| **AI Directive** | Call when the story needs a new character — a merchant, a quest giver, a mysterious stranger. Don't spawn NPCs randomly. Each NPC should serve the narrative. |
| **Source** | Merged from `GameMasterAgent.createNPC()` + `NPCArchetypeGenerator` |

### 3.7 Character

#### `setClass`

| Field | Value |
|---|---|
| **Name** | `setClass` |
| **Description** | Set the player's class. Supports both standard classes and custom names. |
| **Parameters** | `className` (string, required — standard class name or custom), `customDisplayName` (string, optional — flavor name, e.g., "Void Walker" for CHANNELER) |
| **Returns** | `{ "class": "...", "displayName": "...", "statBonuses": {...}, "description": "..." }` |
| **AI Directive** | Call during class selection. If the player chooses a standard class name (from `queryLore("classes")`), use it directly. If they invent a custom name, map it to the closest standard class and store the custom name as `customDisplayName`. Always call `queryLore("classes")` first to present options. |
| **Source** | New — combines class assignment + custom name persistence |

#### `equipItem`

| Field | Value |
|---|---|
| **Name** | `equipItem` |
| **Description** | Equip an item from inventory to a slot. |
| **Parameters** | `itemId` (string, required) |
| **Returns** | `{ "equipped": "...", "slot": "...", "statChanges": {...} }` |
| **AI Directive** | Call when the player equips gear. Narrate the feel of new equipment briefly. |
| **Source** | `GameState.equipItem()` |

#### `useItem`

| Field | Value |
|---|---|
| **Name** | `useItem` |
| **Description** | Use a consumable item from inventory. |
| **Parameters** | `itemId` (string, required), `quantity` (int, optional, default 1) |
| **Returns** | `{ "used": "...", "effect": "...", "remaining": N }` |
| **AI Directive** | Call when the player uses a potion, scroll, etc. Briefly describe the effect. |
| **Source** | `GameState.removeItem()` + item effect application |

#### `addXP`

| Field | Value |
|---|---|
| **Name** | `addXP` |
| **Description** | Award XP to the player. Handles level-up detection and stat point awards. |
| **Parameters** | `amount` (long, required), `source` (string, required — reason for XP, e.g., "quest_completion", "exploration") |
| **Returns** | `{ "xpGained": N, "newTotal": N, "levelUp": bool, "newLevel": N, "gradeUp": bool, "newGrade": "...", "statPointsAwarded": N }` |
| **AI Directive** | Call for non-combat XP awards (quest completion, exploration, NPC interaction). Combat XP is handled by `resolveAttack`. If `levelUp`, narrate the power surge. If `gradeUp`, this is a MAJOR moment — use the system voice style. |
| **Source** | `GameState.gainXP()` + `Grade.isGradeUp()` + `TierUpEvent.create()` |

#### `updateQuestObjective`

| Field | Value |
|---|---|
| **Name** | `updateQuestObjective` |
| **Description** | Update progress on a quest objective. |
| **Parameters** | `questId` (string, required), `objectiveId` (string, required), `progress` (int, required, default 1) |
| **Returns** | `{ "questName": "...", "objective": "...", "newProgress": N, "target": N, "objectiveComplete": bool, "questComplete": bool }` |
| **AI Directive** | Call when the player does something that advances a quest (kills the right enemy, reaches a location, talks to an NPC). Check `getActiveQuests` to know which objectives are active. |
| **Source** | `GameState.updateQuestObjective()` |

#### `acceptQuest`

| Field | Value |
|---|---|
| **Name** | `acceptQuest` |
| **Description** | Accept a quest and add it to active quests. |
| **Parameters** | `questId` (string, required) |
| **Returns** | `{ "quest": { "name": "...", "objectives": [...] } }` |
| **AI Directive** | Call when an NPC offers a quest and the player accepts. |
| **Source** | `GameState.addQuest()` |

#### `completeQuest`

| Field | Value |
|---|---|
| **Name** | `completeQuest` |
| **Description** | Complete a quest and award rewards. All objectives must be done. |
| **Parameters** | `questId` (string, required) |
| **Returns** | `{ "questName": "...", "rewards": { "xp": N, "items": [...], "gold": N }, "newLocationsUnlocked": [...] }` |
| **AI Directive** | Call when all objectives are complete. Narrate the reward moment using the system voice. |
| **Source** | `GameState.completeQuest()` |

### 3.8 Multimodal

#### `generateSceneArt`

| Field | Value |
|---|---|
| **Name** | `generateSceneArt` |
| **Description** | Generate scene artwork for the current narrative moment. |
| **Parameters** | `description` (string, required — scene description for image generation) |
| **Returns** | `{ "status": "generating", "description": "..." }` |
| **AI Directive** | Call at major scene transitions, dramatic moments, or new locations. Not every turn — save it for impactful moments. |
| **Source** | Existing multimodal pipeline |

#### `generatePortrait`

| Field | Value |
|---|---|
| **Name** | `generatePortrait` |
| **Description** | Generate a portrait for an NPC or the player character. |
| **Parameters** | `subjectName` (string, required), `description` (string, required) |
| **Returns** | `{ "status": "generating" }` |
| **AI Directive** | Call when the player first meets an important NPC. |
| **Source** | Existing multimodal pipeline |

#### `shiftMusicMood`

| Field | Value |
|---|---|
| **Name** | `shiftMusicMood` |
| **Description** | Change the background music mood. |
| **Parameters** | `mood` (string, required — e.g., "tense", "peaceful", "combat", "triumphant"), `intensity` (float, optional, 0.0-1.0) |
| **Returns** | `{ "mood": "...", "intensity": N }` |
| **AI Directive** | Shift music to match scene tone. Combat → tense. Safe area → peaceful. Boss fight → epic. Level up → triumphant. |
| **Source** | Existing multimodal pipeline |

---

## 4. GM System Prompt

The GM system prompt is built dynamically from WorldSeed data. It replaces both the current static GM prompt and the narrator-specific prompts.

### Template

```kotlin
fun buildGMSystemPrompt(
    worldSeed: WorldSeed,
    playerName: String,
    backstory: String
): String = """
You are the GAME MASTER of "${worldSeed.displayName}" — a LitRPG adventure.
You are ${worldSeed.systemVoice.personality}.

WORLD: ${worldSeed.tagline}
${worldSeed.worldState.atmosphere}

POWER SYSTEM: ${worldSeed.powerSystem.name}
${worldSeed.powerSystem.progression}
Unique Mechanic: ${worldSeed.powerSystem.uniqueMechanic}

PLAYER: $playerName
Backstory: $backstory

YOUR ROLE:
You are BOTH the game master AND the narrator. When the player acts, you:
1. Call tools to understand the current situation (stats, location, quests, NPCs)
2. Call tools to execute mechanical actions (combat, movement, item use)
3. Narrate the result in 2-4 sentences of vivid prose

NARRATION RULES:
- Second person, present tense. Always.
- 2-4 sentences unless a major story moment demands more.
- ${worldSeed.narratorPrompt.lines().filter { it.startsWith("- ") }.take(5).joinToString("\n")}
- NEVER end with a bulleted list of action options. End on momentum.
- NEVER invent items or loot — only reference what tools return.
- NEVER narrate the player's emotions — describe sensations, not feelings.

SYSTEM VOICE (for System notifications):
Style: ${worldSeed.systemVoice.messageStyle}
Examples:
${worldSeed.systemVoice.exampleMessages.joinToString("\n") { "  $it" }}

TOOL USE:
- Call getPlayerStats and getActiveQuests at the start of each turn.
- Call queryLore("classes") before class selection — present all options.
- Call queryLore("narration_guide") if you're unsure about tone.
- Call queryLore("examples", "<situation>") for narration reference.
- Call getTutorialState during tutorial — guide toward next objective.
- ALWAYS call resolveAttack or resolveSkillUse for combat — never make up damage numbers.
- ALWAYS call getInventory before referencing items.
- When level-up occurs, narrate the power surge using the system voice style.

TUTORIAL FLOW:
${if (worldSeed.tutorial.isSolo) "This is a SOLO tutorial. No other people." else "Other NPCs may be present."}
Objectives: ${worldSeed.tutorial.objectives.joinToString(" → ") { it.description }}
Guide: ${worldSeed.tutorial.guide?.name ?: "None"}
When all objectives complete → call completeTutorial.

BACKSTORY INTEGRATION:
Reference the player's backstory ONCE per session, early, specifically.
Their past shapes reactions but doesn't dominate the present.
The ${worldSeed.powerSystem.name} cares about what you DO, not who you WERE.

THEMES: ${worldSeed.themes.joinToString(", ")}
TONE: ${worldSeed.tone.joinToString(", ")}
INSPIRATIONS: ${worldSeed.inspirations.joinToString(", ")}
""".trimIndent()
```

### Key Differences from Current

| Aspect | Before | After |
|---|---|---|
| GM prompt | Generic, 20 lines, no world context | Dynamic, ~80 lines, full WorldSeed integration |
| GM tool access | Zero — generates JSON blobs | Full tool access — calls 30+ tools |
| Narrator prompt | Separate agent with its own prompt | Merged into GM — one agent narrates |
| World flavor | Only in narrator's system prompt | In GM prompt + queryable via `queryLore` |
| System voice | Hardcoded in WorldSeed, not in prompts | In GM prompt + queryable |
| Tutorial guidance | None | Explicit flow with `getTutorialState` + `completeTutorial` |

---

## 5. LLMInterface Extension

### New Types

```kotlin
// Tool definition for LLM function calling
@Serializable
data class LLMToolDef(
    val name: String,
    val description: String,
    val parameters: JsonObject  // JSON Schema for parameters
)

// Tool call request from the LLM
@Serializable
data class LLMToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject
)

// Tool call result to send back to the LLM
@Serializable
data class LLMToolResult(
    val callId: String,
    val result: JsonObject
)

// Executor that the LLM provider calls to run tools
fun interface ToolExecutor {
    suspend fun execute(call: LLMToolCall): LLMToolResult
}
```

### Extended AgentStream

```kotlin
interface AgentStream {
    /** Existing: text-only message */
    suspend fun sendMessage(message: String): Flow<String>

    /**
     * NEW: Send a message with tool-use support.
     * The LLM can call tools during its response. The executor handles tool calls.
     * Returns the final text response after all tool calls are resolved.
     *
     * Default implementation falls back to text-only (no tools).
     */
    suspend fun sendMessageWithTools(
        message: String,
        tools: List<LLMToolDef>,
        executor: ToolExecutor
    ): Flow<String> = sendMessage(message)  // Default: ignore tools
}
```

### Provider Implementations

**Gemini Live API:** Already has native tool support. `GeminiLiveSession` maps `LLMToolDef` → Gemini `FunctionDeclaration`, handles tool call/response loop internally.

**Claude API:** Maps `LLMToolDef` → Claude tool definitions. Handles the tool_use/tool_result content block loop.

**Text-only providers (OpenAI, Grok, Mock):** Use default fallback — tools are ignored, GM works in text-only mode (degraded but functional).

---

## 6. New Game Loop

### `processInput()` — Single GM Call with Tools

```kotlin
suspend fun processInput(input: String): Flow<GameEvent> = flow {
    // Handle opening narration (uses NarratorAgent — retained for this)
    if (!gameState.hasOpeningNarrationPlayed) {
        val opening = narratorAgent.narrateOpening(gameState, storyFoundation?.narratorContext)
        emit(GameEvent.NarratorText(opening))
        gameState = gameState.copy(hasOpeningNarrationPlayed = true)
        if (input.isBlank()) return@flow
    }

    // Death check
    if (gameState.isDead) {
        val deathNarration = narratorAgent.narrateDeath(gameState, "combat")
        emit(GameEvent.NarratorText(deathNarration))
        handleRespawn(this)
        return@flow
    }

    // Build context message for the GM
    val contextMessage = buildContextMessage(input, gameState)

    // Single GM call with tools — GM reads state, takes actions, and narrates
    val toolDefs = unifiedTools.getToolDefinitions().map { it.toLLMToolDef() }
    val executor = ToolExecutor { call ->
        val outcome = unifiedTools.executeTool(call.name, call.arguments, gameState)
        // Apply state mutations
        gameState = outcome.newState
        // Collect events
        outcome.events.forEach { event ->
            emit(event)
            eventLog.add(event)
        }
        LLMToolResult(call.id, outcome.toJson())
    }

    val responseFlow = gmAgentStream.sendMessageWithTools(contextMessage, toolDefs, executor)

    // Emit GM's narration as NarratorText events
    val fullResponse = StringBuilder()
    responseFlow.collect { chunk ->
        fullResponse.append(chunk)
    }

    val narration = fullResponse.toString()
    if (narration.isNotBlank()) {
        emit(GameEvent.NarratorText(narration))
        eventLog.add(GameEvent.NarratorText(narration))
    }
}
```

### Context Message Builder

```kotlin
private fun buildContextMessage(input: String, state: GameState): String = """
Player says: "$input"

Quick context (call tools for details):
- Location: ${state.currentLocation.name} (danger ${state.currentLocation.danger})
- Level ${state.playerLevel}, HP ${state.characterSheet.resources.currentHP}/${state.characterSheet.resources.maxHP}
- ${state.activeQuests.size} active quests
- ${state.getNPCsAtCurrentLocation().size} NPCs here: ${state.getNPCsAtCurrentLocation().joinToString { it.name }}

Respond with narration. Call tools as needed before narrating.
""".trimIndent()
```

### What Changes

| Before | After |
|---|---|
| `analyzeIntent()` — LLM call #1 | Eliminated — GM figures out intent via tools |
| `planScene()` — LLM call #2 | Eliminated — GM acts directly |
| `renderScene()` — LLM call #3 | GM narrates as part of its single response |
| Orchestrator interprets GM's JSON plan | GM calls tools directly |
| NarratorAgent renders every turn | NarratorAgent only for opening + death |
| Intent → action mapping in 400 lines of `when` blocks | Tools handle mechanics, GM handles narration |

### NarratorAgent Retention

`NarratorAgent` is kept for exactly two purposes:
1. **Opening narration** — the first scene before the player acts
2. **Death narration** — stylized death descriptions

Everything else is handled by the GM in a single turn.

---

## 7. Adapter Layers

### 7.1 Gemini Live API

```kotlin
class GeminiLiveAdapter(private val tools: UnifiedToolContract) {
    // Convert UnifiedToolContract definitions to Gemini FunctionDeclarations
    fun getGeminiFunctionDeclarations(): List<FunctionDeclaration> {
        return tools.getToolDefinitions().map { def ->
            FunctionDeclaration(
                name = def.name,
                description = def.description,
                parameters = def.parameters.toGeminiSchema()
            )
        }
    }

    // Handle a Gemini tool call
    suspend fun handleToolCall(
        call: GeminiToolCall,
        state: GameState
    ): Pair<ToolResult, GameState> {
        val outcome = tools.executeTool(call.name, call.arguments, state)
        return ToolResult(
            success = outcome.success,
            data = outcome.data,
            error = outcome.error,
            gameEvents = outcome.events
        ) to outcome.newState
    }
}
```

Gemini Live sessions use this adapter instead of `GeminiToolContractImpl` directly. The voice-based GM has the same tools as the text-based GM.

### 7.2 MCP Server

```kotlin
class McpToolAdapter(private val tools: UnifiedToolContract) {
    // Convert tool definitions to MCP tool schema
    fun getMcpTools(): List<McpToolDefinition> {
        return tools.getToolDefinitions().map { def ->
            McpToolDefinition(
                name = "game_${def.name.toSnakeCase()}",
                description = def.description,
                inputSchema = def.parameters.toMcpSchema()
            )
        }
    }

    // Execute MCP tool call
    suspend fun executeMcpTool(
        name: String,
        args: JsonObject,
        state: GameState
    ): Pair<JsonObject, GameState> {
        val toolName = name.removePrefix("game_").toCamelCase()
        val outcome = tools.executeTool(toolName, args, state)
        return outcome.toMcpResponse() to outcome.newState
    }
}
```

This replaces the 1400-line `McpHandler.executeTool()` `when` block with a ~30-line adapter.

### 7.3 CLI / Debug Dashboard

The CLI continues to use `GameOrchestrator.processInput()`, which now internally uses the unified tools. The debug dashboard's Character tab reads from `Game.getState()` as before — no change needed.

---

## 8. Migration Plan

### Phase 1: Foundation (No Behavior Change)

**Create:**
- `core/.../tools/UnifiedToolContract.kt` — interface + types (`ToolDef`, `ToolParam`, `ToolResult`, `ToolOutcome`)
- `core/.../tools/UnifiedToolContractImpl.kt` — implementation, delegates to existing code
- `core/.../tools/LoreQueryHandler.kt` — handles `queryLore` categories
- `core/.../story/NarrationGuides.kt` — narration guide + example data per world

**Wire:**
- Each tool in `UnifiedToolContractImpl` calls existing code:
  - State queries → read from `GameState` directly
  - Combat → `RulesEngine.calculateCombatOutcome()`
  - Movement → `LocationManager` + `GameState.moveToLocation()`
  - NPC → `GameState.findNPCByName()` + `NPCArchetypeGenerator`
  - Quests → `GameState.updateQuestObjective()` etc.
- `queryLore` aggregates from `SkillDatabase`, `TierSystem`, `WorldSeeds`, `LootTables`, `NPCArchetype`, `QuestType`

**New tools:**
- `getTutorialState` — reads tutorial quest + WorldSeed tutorial def
- `getStoryState` — reads StoryFoundation + event log
- `completeTutorial` — awards rewards, transitions location
- `setClass` with custom name support
- `addXP` with level-up/grade-up detection
- `spawnEnemy` / `getCombatTargets`

**Test:** All existing tests pass. New tools have unit tests.

### Phase 2: LLM Tool-Use Support

**Extend:**
- `core/.../api/LLMInterface.kt` — add `LLMToolDef`, `LLMToolCall`, `LLMToolResult`, `ToolExecutor`
- `AgentStream.sendMessageWithTools()` with default fallback
- `cli/.../GeminiLLM.kt` — implement `sendMessageWithTools` for Gemini

**Test:** Mock LLM provider gets tool-use support for testing.

### Phase 3: New Game Loop

**Modify:**
- `core/.../orchestration/GameOrchestrator.kt`:
  - New `processInput()` with single GM call + tools
  - GM system prompt built from `buildGMSystemPrompt(worldSeed, ...)`
  - `ToolExecutor` wired to `UnifiedToolContractImpl`
  - Remove `handleCoordinatedPath()` (3-call pipeline)
  - Remove intent classification (`analyzeIntent()`)
  - Keep `NarratorAgent` for opening + death only

**Delete from orchestrator:**
- `executeMechanicalActions()` — tools handle this
- `handleQuestActionMenu()` — GM handles via tools
- `handleStatusMenu()`, `handleInventoryMenu()`, `handleSkillMenu()` — GM handles via tools
- `handleClassSelection()` — GM calls `setClass` tool
- Intent routing `when` block (~200 lines)

**Retain:**
- `handleDeath()` / `handleRespawn()` — uses `NarratorAgent`
- Opening narration flow
- Event log
- Story planning service initialization
- NPC initialization

### Phase 4: Adapter Migration

**Modify:**
- `core/.../gemini/GeminiToolContractImpl.kt` → replaced by `GeminiLiveAdapter` wrapping `UnifiedToolContract`
- `server/.../McpHandler.kt` → `executeTool()` replaced by `McpToolAdapter` (~1300 lines deleted)

**Delete:**
- `core/.../gemini/GeminiToolContract.kt` — replaced by `UnifiedToolContract`
- `core/.../gemini/GeminiToolContractImpl.kt` — replaced by adapter
- `core/.../tools/GameTools.kt` — interface replaced by `UnifiedToolContract`
- `core/.../tools/GameToolsImpl.kt` — implementation merged into `UnifiedToolContractImpl`

**Retain:**
- `core/.../gemini/GeminiOutput.kt` — still used for Live API output types
- `core/.../gemini/GeminiToolCall.kt` — reuse or alias to `LLMToolCall`

### Phase 5: Cleanup

**Reduce `NarratorAgent`:**
- Delete `renderScene()`, `narrateExploration()`, `narrateCombat()`, `narrateSkillUse()`, `narrateClassSelection()`, `narrateClassAcquisition()`, `narrateRespawn()`
- Keep `narrateOpening()`, `narrateDeath()`

**Reduce `GameMasterAgent`:**
- Delete `planScene()`, `coordinateResponse()`, `shouldCreateNPC()`, `extractNPCsFromNarration()`, `shouldTriggerEncounter()`, `generateQuest()`, `narrateChoice()`
- The GM agent is now just a system prompt + `sendMessageWithTools` — no dedicated class needed. The orchestrator holds the agent stream directly.

**Delete data classes:**
- `ScenePlan`, `ScenePlanJson`, `PlannedAction`, `NPCReaction`, `NarrativeBeat`, etc. (from `GameOrchestrator`)
- `NPCCreationDecision`, `EncounterDecision`, `GeneratedQuest`, `ChoiceOutcome` (from `GameMasterAgent`)
- `SceneResults`, `CombatSceneResult`, `XPChange`, `ItemGain`, `QuestProgressUpdate` (from `GameOrchestrator`)
- `IntentAnalysis`, `ActionValidation` (from `GameTools`)

**Estimated line count changes:**
- `GameOrchestrator.kt`: 2049 → ~400 lines
- `McpHandler.kt`: 1400 → ~200 lines
- `NarratorAgent.kt`: 575 → ~100 lines
- `GameMasterAgent.kt`: 850 → deleted (replaced by system prompt)
- New `UnifiedToolContractImpl.kt`: ~800 lines
- New `LoreQueryHandler.kt`: ~400 lines
- Net reduction: ~2500 lines

---

## 9. QA Bug Fix Mapping

Every bug from the playtest report has a clear fix path in this architecture:

| Bug | Fix |
|---|---|
| Narrator invents items not in loot | GM calls `resolveAttack` and only narrates returned loot |
| State desync (narration says one thing, state is different) | Single agent reads state via tools, mutates via tools, narrates result |
| 3+ second latency per action | 1 LLM call instead of 3 |
| GM generates JSON that orchestrator misparses | GM calls tools directly — no JSON parsing layer |
| Custom class names lost | `setClass` tool persists `customDisplayName` |
| Tutorial never exits | `completeTutorial` tool with explicit trigger |
| World lore underutilized in GM decisions | GM prompt built from WorldSeed + `queryLore` for on-demand lookup |
| System voice inconsistent | System voice in GM prompt + `queryLore("system_voice")` |
| NPC dialogue doesn't match personality | GM calls `getNPCDetails` before generating dialogue |
| Quest objectives not tracked accurately | GM calls `updateQuestObjective` explicitly |
| Exploration describes wrong location | GM calls `getLocation` for current location details |
| Skill use doesn't resolve mechanically | GM calls `resolveSkillUse` for actual effect resolution |
| Opening narration ignores backstory | Backstory in GM prompt + explicit integration rules |
| Intent classification wrong | No separate intent classifier — GM determines intent naturally |
