# QA Playtest Report — RPGenerator

**Date**: 2026-03-06
**Tester**: Claude Code (automated QA via MCP tools)
**Character**: Mira Solenne (Veterinarian backstory)
**World**: System Integration (`integration` seed) / NORMAL difficulty
**Session Duration**: 14 player interactions + state inspections
**Tools Used**: All MCP game tools + debug inspection tools

---

## Test Scenario

A custom character (veterinarian mid-surgery on a golden retriever, elderly mother in care facility) was created to test:
- Backstory integration into narration
- Custom class selection ("Vitality Weaver")
- Intent routing accuracy across natural language inputs
- Combat flow and state consistency
- Tutorial progression and exit

## Playthrough Summary

| Step | Input | Result |
|------|-------|--------|
| 1 | "Look around -- where am I?" | Opening narration references surgery, golden retriever, clinic. Truncated mid-sentence. |
| 2 | "Walk toward whatever is nearby" | Short narration about System Terminal. 3 sentences. |
| 3 | "I want to be a Vitality Weaver..." | System accepted custom class, renamed to "Essence Weaver", granted 2 skills. State stored "Healer" instead. |
| 4 | Check stats | Class field = "Healer", not "Essence Weaver". Skills (Life Spark, Essence Sense) present. |
| 5 | "Look for an exit from the tutorial" | Narration says tutorial incomplete. No exit available. |
| 6 | "I need to fight something" | "No combat target specified" — no enemy spawned via natural language. |
| 7 | "Walk deeper into the void" | Same loop — System says "Integration Protocol Requires Attention". |
| 8 | "Check my status" | Status display shown. Quest objective "Review status" completed. |
| 9 | "Use Life Spark on myself" | BUG: Routed as combat attack (21 damage to phantom target) instead of self-heal. |
| 10 | `game_attack_target("Shadow Echo")` x3 | Combat works via direct tool. XP: 0->50->100->150. Level up at 100 XP. Loot granted. |
| 11 | "I need to find my mother" | Correctly references "care facility, three towns over" from backstory. |
| 12 | "use Life Spark" | Correctly routed to USE_SKILL. Detected cooldown (2 turns). |
| 13 | "show my skills" | Correctly routed to SKILL_MENU. Clean formatted output. |
| 14 | "what's in my bag" | Correctly routed to INVENTORY_MENU. Shows items + equipped slots. |
| 15 | "use Essence Sense" | Correctly detected as passive skill ("always active"). |
| 16 | "talk to the person here" | Ignored the NPC in state. Narrated "no one is here". |
| 17 | "I accept the transfer" | Misrouted to QUEST_MENU. Returned quest commands. |
| 18 | `game_move_to_location("outside")` | success: false, but narrator described leaving. Location unchanged. |

---

## Evaluation Summary

### Narration Brevity: PASS (with caveats)
- Most responses are 2-4 sentences. No walls of text. No action option lists appended.
- Combat narration is short and punchy — single paragraph per kill.
- One truncation issue: opening narration cuts off mid-sentence ("...Then cold. Blue").

### Custom Class: FAIL
- Requested "Vitality Weaver", narration called it "Essence Weaver" (acceptable creative rename).
- **Game state stores "Healer"** (base archetype) — the custom class name is completely lost at the persistence layer.
- Skills were thematically appropriate (Life Spark, Essence Sense) and correctly granted.
- No unique stat modifiers for the custom class — uses default Healer template.
- The entire custom class feature is cosmetic narration only; mechanically it's just the base archetype.

### Intent Routing: MIXED (8/12 correct)

| Input | Expected Route | Actual Route | Verdict |
|-------|---------------|-------------|---------|
| "Look around" | EXPLORE | EXPLORE | PASS |
| "Walk toward..." | EXPLORE | EXPLORE | PASS |
| "I want to be a Vitality Weaver" | CLASS_SELECT | CLASS_SELECT | PASS |
| "Check my status" | STATUS | STATUS | PASS |
| "use Life Spark on myself" | USE_SKILL (self-heal) | COMBAT (attack) | FAIL |
| "I need to fight something" | COMBAT/EXPLORE | Error: no target | FAIL |
| "use Life Spark" | USE_SKILL | USE_SKILL | PASS |
| "show my skills" | SKILL_MENU | SKILL_MENU | PASS |
| "what's in my bag" | INVENTORY_MENU | INVENTORY_MENU | PASS |
| "use Essence Sense" | USE_SKILL | USE_SKILL (passive detect) | PASS |
| "talk to the person here" | TALK_TO_NPC | NARRATIVE (ignored NPC) | FAIL |
| "I accept the transfer" | EXPLORE/NARRATIVE | QUEST_MENU | FAIL |

### Backstory Integration: PASS
- **Opening narration**: References golden retriever surgery, surgical sutures, antiseptic, the clinic. Excellent pull from backstory.
- **"Find my mother"**: Correctly references "care facility, three towns over". Response is emotionally appropriate and in-character.
- The narration consistently treats Mira as a pragmatic veterinarian — tone is right.

### Combat Narration: PASS
- Short, 2-3 sentence combat descriptions. Thematically consistent (healing energy used offensively against shadow creatures — fits the "Essence Weaver" concept).
- No purple prose. Clean loot drops narrated inline.

### State Consistency: MIXED

| Check | Consistent? | Notes |
|-------|------------|-------|
| XP tracking | PASS | 0->50->100->150, correct across 3 kills |
| Level up | PASS | Lvl 1->2 at 100 XP, HP 200->210, Energy 120->130, all stats increased |
| Inventory | PARTIAL | Items granted correctly, but identical items don't stack (2 separate Leather Armor entries) |
| Quest tracking | FAIL | "System Integration" quest disappeared entirely instead of showing COMPLETED |
| Location | FAIL | Narrator described leaving tutorial; player permanently stuck in "Tutorial Instance" |
| Class name | FAIL | Narrated "Essence Weaver", state stores "Healer" |
| NPC state | FAIL | Player character duplicated as NPC "Mira Solenne" (TRAINER) at tutorial location |

### Plot Graph: FAIL
- `debug_get_plot_graph` returned 0 threads, 0 nodes, 0 edges.
- `StoryPlanningService` was never invoked or failed silently.
- No narrative structure exists for the engine to track long-term story arcs.

---

## Bugs

### P0 — Critical

**BUG-001: `start_game` silently fails / returns empty response**
- `start_game` returned no output (empty response body).
- `get_game_state` still showed `phase: "character_creation"` after calling `start_game`.
- `game_get_player_stats` returned "Game not started" errors.
- However, `send_player_input` worked — the game was actually running.
- **Impact**: Any client relying on the `start_game` response to confirm readiness will think the game never started. This is the entry point for all gameplay.
- **Likely cause**: `GameSessionManager.createSession()` in `McpHandler.kt:525` is a suspend function that creates GeminiLLM, DB driver, and game engine. It likely threw or timed out (possibly `GeminiLLM()` constructor reading `GOOGLE_API_KEY`), but the MCP transport swallowed the error. The game may have been partially initialized enough for `send_player_input` to work via a fallback path.
- **Location**: `server/src/main/kotlin/com/rpgenerator/server/McpHandler.kt:497-545`

**BUG-002: Custom class name not persisted**
- Player requested "Vitality Weaver". Narration and system notifications said "Essence Weaver".
- `game_get_player_stats` and `game_get_character_sheet` both show `class: "Healer"` (the base archetype).
- The custom class name generated by the AI is never written to `GameState.playerClass` or equivalent field.
- **Impact**: The entire custom class feature is cosmetic-only. Any UI, save file, or downstream logic sees "Healer" — the player's creative choice is lost.
- **Location**: Likely in `GameOrchestrator` or `GameMasterAgent` where class selection is processed. The AI generates the custom name but only the archetype enum is persisted.

**BUG-003: Tutorial has no exit mechanism**
- Player is permanently stuck in "Tutorial Instance" after completing all 3 quest objectives.
- `game_move_to_location("outside")` returns `success: false` but the narrator describes the transfer happening (state/narration desync).
- Natural language ("I accept the transfer") misroutes to QUEST_MENU.
- No location transition logic exists for completing the tutorial.
- **Impact**: The game cannot progress past the tutorial. Complete blocker for any real gameplay session.
- **Location**: Tutorial completion logic — likely missing a location transition trigger when all objectives in "System Integration" quest are marked complete.

### P1 — High

**BUG-004: Self-targeted heal routed as combat attack**
- "use Life Spark on myself" was treated as a combat attack dealing 21 damage to an unnamed entity.
- Life Spark is described as a healing skill ("restoring vitality") but the intent router sent it to the combat system.
- No self-targeting or ally-targeting path exists for skills — everything goes through the attack pipeline.
- **Impact**: Healer class is functionally broken. Cannot heal self or allies.
- **Location**: Intent analysis in `GameOrchestrator.kt` — "on myself" should route to USE_SKILL with self-target, not COMBAT.

**BUG-005: Natural language combat requests fail**
- "I need to fight something" and "I'm ready for combat" return `"Cannot perform action: No combat target specified"`.
- The game requires a named target but doesn't spawn enemies for the player to fight.
- Only the direct `game_attack_target` tool works, and it auto-spawns + auto-kills in one step.
- **Impact**: Players using natural language (the primary input method) cannot initiate combat. The narration path for enemy encounters is broken.
- **Location**: Combat initiation in `GameOrchestrator` — needs an enemy spawn step before requiring a target name.

**BUG-006: Player character duplicated as NPC**
- `game_get_npcs_here` returns an NPC named "Mira Solenne" with archetype TRAINER and description "A veterinarian with gloved hands, performing surgery" — this is the player character's backstory.
- This NPC is not interactable (narrator says "no one is here" when asked to talk).
- The duplicate propagates into the narrator's scene planning context, confusing the AI.
- **Impact**: Pollutes NPC state, confuses narrator context, could cause name resolution bugs if the player tries to interact with NPCs sharing their name.
- **Location**: Likely in `GameImpl` or initial game setup — the character creation data is being registered as both player and NPC.

**BUG-007: Plot graph never populated**
- `debug_get_plot_graph` returned 0 threads, 0 nodes, 0 edges.
- The `StoryPlanningService` was either never invoked during game start or failed silently.
- `narratorContext.activeThreads` has text-based threads but they're not in the plot graph data structure.
- **Impact**: No long-term narrative structure. The engine can't track story arcs, foreshadowing payoffs, or quest chains beyond the immediate scene.
- **Location**: `core/src/commonMain/kotlin/com/rpgenerator/core/story/StoryPlanningService.kt` — check if it's called during game initialization.

**BUG-008: Stale narrator foreshadowing and context**
- `narratorContext.currentForeshadowing` references `[Skill Acquired: Power Strike]` — a skill that was never granted to this character.
- `narratorContext.upcomingBeats` still includes "Choose your class" after class selection is complete.
- The narrator context is not updated as quest objectives are completed.
- **Impact**: Narrator may reference things that don't exist or repeat prompts for completed actions, breaking immersion.
- **Location**: Narrator context update logic — likely in `NarratorAgent` or wherever `narratorContext` fields are refreshed.

### P2 — Medium

**BUG-009: Quest disappears instead of showing COMPLETED**
- The "System Integration" quest vanishes from `game_get_active_quests` after objectives are done.
- `game_get_character_sheet` also shows `quests: []`.
- Expected behavior: quest should transition to COMPLETED status and remain visible.
- **Impact**: Players lose track of what they've accomplished. Quest history is lost.

**BUG-010: Identical items don't stack in inventory**
- Two "Leather Armor" items have different IDs (`leather_armor-common-1772849176507` and `leather_armor-common-1772849248393`) and appear as separate entries.
- Health Potions stack correctly by rarity (Uncommon x2, Rare x2).
- **Impact**: Inventory bloat. Equipment items from different combat encounters don't merge.
- **Location**: Item creation logic — equipment items get unique timestamp-based IDs while consumables use generic IDs.

**BUG-011: Opening narration truncated mid-sentence**
- First narrator event ends with "...Then cold. Blue" — clearly cut off.
- Likely hitting a character or token limit on the first LLM response.
- **Impact**: First impression of the game is a broken sentence.

**BUG-012: Intent misroute on "I accept the transfer"**
- Player said "I accept the transfer. Take me out of the tutorial."
- Routed to QUEST_MENU, returning "Quest commands: 'list quests', 'get new quest', 'complete quests'".
- Should have routed to EXPLORE or MOVEMENT.
- **Impact**: Breaks immersion at a critical narrative moment.

### P3 — Low

**BUG-013: Leather Armor tagged as MISC instead of EQUIPMENT**
- Armor items show as `[MISC]` category in inventory display.
- Should be `[EQUIPMENT]` to match the equipped slots system.
- **Impact**: Confusing item categorization.

**BUG-014: No equip mechanic via natural language**
- Armor drops and shows in inventory, but there's no way to equip it.
- Equipped slots all show "(none)".
- Inventory menu doesn't show an equip command.
- **Impact**: Equipment is non-functional. Defense stat stays at 0 despite having armor.

---

## Architecture Concerns

### The `start_game` Flow is Fragile
The current flow is: `create_game` (sets config) -> `set_character` (sets narrative fields) -> `start_game` (creates actual GameSession). The `start_game` step does the heavy lifting — it instantiates `GeminiLLM`, creates a SQLite database, calls `RPGClient.startGame()`, and returns. If any of these fail, the MCP transport returns an empty response with no error, but the game may be partially initialized. This needs proper error handling and a clear contract about what "started" means.

### Narration and State are Decoupled
The narrator AI generates prose independently of what the game engine actually does. This leads to desync: the narrator says "you leave the tutorial" but the location doesn't change; the narrator says "Essence Weaver" but the class is "Healer". The narrator should be downstream of state changes, not generating aspirational fiction that the engine doesn't fulfill.

### Combat Requires Pre-existing Targets
The combat system assumes a named target already exists. There's no "encounter generation" step where the engine spawns an enemy based on context. The `game_attack_target` tool auto-spawns and auto-kills in one call, which works mechanically but skips the entire encounter setup that natural language combat expects.

### Tutorial is a Dead End
The tutorial location has no connected locations and no transition trigger. Even after all quest objectives are complete, there's no mechanism to move the player to the next area. This needs either an automatic transition on quest completion or a connected "exit" location.

---

## Recommendations

1. **Fix `start_game` error handling** — Wrap the session creation in try/catch, return proper error responses via MCP, and validate the game is fully initialized before returning success.
2. **Persist custom class names** — Store the AI-generated class name (e.g., "Essence Weaver") in the player's class field, not the base archetype.
3. **Add tutorial exit trigger** — When all "System Integration" objectives complete, auto-transition the player to the first real location.
4. **Add self/ally targeting for skills** — "use [heal] on myself" should route through USE_SKILL with target=self, not COMBAT.
5. **Add encounter spawning** — Natural language combat requests should trigger enemy generation before requiring a target name.
6. **Fix player-as-NPC duplication** — Don't register the player character as an NPC during game setup.
7. **Invoke StoryPlanningService** — Ensure plot graph is generated during game initialization so the narrator has long-term structure to work with.
8. **Update narrator context on objective completion** — Clear completed beats from `upcomingBeats` and remove stale foreshadowing.
