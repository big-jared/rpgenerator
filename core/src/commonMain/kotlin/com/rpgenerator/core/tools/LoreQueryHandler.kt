package com.rpgenerator.core.tools

import com.rpgenerator.core.domain.*
import com.rpgenerator.core.loot.LootTables
import com.rpgenerator.core.skill.SkillDatabase
import com.rpgenerator.core.story.WorldSeeds
import kotlinx.serialization.json.*

/**
 * Aggregates game lore from all data sources for the queryLore tool.
 * The GM calls this to look up world rules, skills, classes, etc.
 */
internal class LoreQueryHandler {

    fun queryLore(category: String, filter: String?, state: GameState): JsonObject {
        return when (category.lowercase()) {
            "classes" -> queryClasses(filter)
            "professions" -> queryProfessions(filter)
            "skills" -> querySkills(filter)
            "world" -> queryWorld(state)
            "tutorial" -> queryTutorial(state)
            "biomes" -> queryBiomes()
            "npc_archetypes" -> queryNPCArchetypes()
            "quest_templates" -> queryQuestTemplates()
            "loot" -> queryLoot(filter)
            "progression" -> queryProgression()
            // Narrative categories — stub placeholders for writers
            "story" -> stubNarrative("story", "Main story arcs, plot hooks, and narrative structure")
            "narration_guide" -> stubNarrative("narration_guide", "How to narrate: tone, pacing, POV rules")
            "system_voice" -> querySystemVoice(state)
            "examples" -> stubNarrative("examples", "Example narration passages for reference")
            else -> buildJsonObject {
                put("error", JsonPrimitive("Unknown lore category: $category"))
                putJsonArray("available_categories") {
                    listOf("classes", "professions", "skills", "world", "tutorial", "biomes", "npc_archetypes",
                        "quest_templates", "loot", "progression", "story", "narration_guide",
                        "system_voice", "examples").forEach { add(JsonPrimitive(it)) }
                }
            }
        }
    }

    private fun queryClasses(filter: String?): JsonObject = buildJsonObject {
        val classes = if (filter != null) {
            PlayerClass.selectableClasses().filter {
                it.name.equals(filter, ignoreCase = true) ||
                it.archetype.name.equals(filter, ignoreCase = true)
            }
        } else {
            PlayerClass.selectableClasses()
        }

        putJsonArray("classes") {
            classes.forEach { cls ->
                addJsonObject {
                    put("name", JsonPrimitive(cls.displayName))
                    put("id", JsonPrimitive(cls.name))
                    put("description", JsonPrimitive(cls.description))
                    put("archetype", JsonPrimitive(cls.archetype.displayName))
                    putJsonObject("statBonuses") {
                        put("strength", JsonPrimitive(cls.statBonuses.strength))
                        put("dexterity", JsonPrimitive(cls.statBonuses.dexterity))
                        put("constitution", JsonPrimitive(cls.statBonuses.constitution))
                        put("intelligence", JsonPrimitive(cls.statBonuses.intelligence))
                        put("wisdom", JsonPrimitive(cls.statBonuses.wisdom))
                        put("charisma", JsonPrimitive(cls.statBonuses.charisma))
                    }
                }
            }
        }

        putJsonArray("archetypes") {
            ClassArchetype.values().filter { it != ClassArchetype.UNALIGNED }.forEach {
                add(JsonPrimitive(it.displayName))
            }
        }
    }

    private fun queryProfessions(filter: String?): JsonObject = buildJsonObject {
        val professions = if (filter != null) {
            Profession.selectableProfessions().filter {
                it.name.equals(filter, ignoreCase = true) ||
                it.category.name.equals(filter, ignoreCase = true)
            }
        } else {
            Profession.selectableProfessions()
        }

        putJsonArray("professions") {
            professions.forEach { prof ->
                addJsonObject {
                    put("name", JsonPrimitive(prof.displayName))
                    put("id", JsonPrimitive(prof.name))
                    put("description", JsonPrimitive(prof.description))
                    put("category", JsonPrimitive(prof.category.displayName))
                    putJsonObject("statBonuses") {
                        put("strength", JsonPrimitive(prof.statBonuses.strength))
                        put("dexterity", JsonPrimitive(prof.statBonuses.dexterity))
                        put("constitution", JsonPrimitive(prof.statBonuses.constitution))
                        put("intelligence", JsonPrimitive(prof.statBonuses.intelligence))
                        put("wisdom", JsonPrimitive(prof.statBonuses.wisdom))
                        put("charisma", JsonPrimitive(prof.statBonuses.charisma))
                    }
                }
            }
        }

        putJsonArray("categories") {
            ProfessionCategory.values().filter { it != ProfessionCategory.NONE }.forEach {
                add(JsonPrimitive(it.displayName))
            }
        }
    }

    private fun querySkills(filter: String?): JsonObject = buildJsonObject {
        val allSkills = SkillDatabase.queryAll()
        val skills = if (filter != null) {
            allSkills.filter { (_, skill) ->
                skill.category.name.equals(filter, ignoreCase = true) ||
                skill.rarity.name.equals(filter, ignoreCase = true) ||
                skill.fusionTags.any { it.equals(filter, ignoreCase = true) }
            }
        } else {
            allSkills
        }

        putJsonArray("skills") {
            skills.values.forEach { skill ->
                addJsonObject {
                    put("id", JsonPrimitive(skill.id))
                    put("name", JsonPrimitive(skill.name))
                    put("description", JsonPrimitive(skill.description))
                    put("category", JsonPrimitive(skill.category.name))
                    put("rarity", JsonPrimitive(skill.rarity.name))
                    put("manaCost", JsonPrimitive(skill.manaCost))
                    put("energyCost", JsonPrimitive(skill.energyCost))
                }
            }
        }

        putJsonArray("fusionRecipes") {
            SkillDatabase.queryFusionRecipes().forEach { recipe ->
                addJsonObject {
                    put("id", JsonPrimitive(recipe.id))
                    put("name", JsonPrimitive(recipe.name))
                    put("resultSkill", JsonPrimitive(recipe.resultSkillName))
                    putJsonArray("inputs") {
                        recipe.inputSkillIds.forEach { add(JsonPrimitive(it)) }
                    }
                }
            }
        }
    }

    private fun queryWorld(state: GameState): JsonObject {
        val seed = state.seedId?.let { WorldSeeds.byId(it) }
        return buildJsonObject {
            if (seed != null) {
                put("name", JsonPrimitive(seed.name))
                put("displayName", JsonPrimitive(seed.displayName))
                put("tagline", JsonPrimitive(seed.tagline))
                putJsonObject("powerSystem") {
                    put("name", JsonPrimitive(seed.powerSystem.name))
                    put("source", JsonPrimitive(seed.powerSystem.source))
                    put("progression", JsonPrimitive(seed.powerSystem.progression))
                    put("uniqueMechanic", JsonPrimitive(seed.powerSystem.uniqueMechanic))
                    put("limitations", JsonPrimitive(seed.powerSystem.limitations))
                }
                putJsonObject("worldState") {
                    put("era", JsonPrimitive(seed.worldState.era))
                    put("civilizationStatus", JsonPrimitive(seed.worldState.civilizationStatus))
                    putJsonArray("threats") { seed.worldState.threats.forEach { add(JsonPrimitive(it)) } }
                    put("atmosphere", JsonPrimitive(seed.worldState.atmosphere))
                }
                putJsonArray("tone") { seed.tone.forEach { add(JsonPrimitive(it)) } }
                putJsonArray("themes") { seed.themes.forEach { add(JsonPrimitive(it)) } }
                putJsonArray("inspirations") { seed.inspirations.forEach { add(JsonPrimitive(it)) } }
            } else {
                put("worldName", JsonPrimitive(state.worldSettings.worldName))
                put("coreConcept", JsonPrimitive(state.worldSettings.coreConcept))
                put("originStory", JsonPrimitive(state.worldSettings.originStory))
                put("currentState", JsonPrimitive(state.worldSettings.currentState))
            }
        }
    }

    private fun queryTutorial(state: GameState): JsonObject {
        val seed = state.seedId?.let { WorldSeeds.byId(it) }
        return buildJsonObject {
            if (seed != null) {
                val tut = seed.tutorial
                put("isSolo", JsonPrimitive(tut.isSolo))
                putJsonArray("objectives") {
                    tut.objectives.forEach { obj ->
                        addJsonObject {
                            put("id", JsonPrimitive(obj.id))
                            put("description", JsonPrimitive(obj.description))
                            put("type", JsonPrimitive(obj.type))
                            put("target", JsonPrimitive(obj.target))
                        }
                    }
                }
                tut.guide?.let { guide ->
                    putJsonObject("guide") {
                        put("name", JsonPrimitive(guide.name))
                        put("appearance", JsonPrimitive(guide.appearance))
                        put("personality", JsonPrimitive(guide.personality))
                        put("role", JsonPrimitive(guide.role))
                    }
                }
                put("completionReward", JsonPrimitive(tut.completionReward))
                put("exitDescription", JsonPrimitive(tut.exitDescription))
            } else {
                put("message", JsonPrimitive("No tutorial defined for this world"))
            }
        }
    }

    private fun queryBiomes(): JsonObject = buildJsonObject {
        putJsonArray("biomes") {
            Biome.values().forEach { biome ->
                add(JsonPrimitive(biome.name))
            }
        }
    }

    private fun queryNPCArchetypes(): JsonObject = buildJsonObject {
        putJsonArray("archetypes") {
            NPCArchetype.values().forEach { archetype ->
                add(JsonPrimitive(archetype.name))
            }
        }
    }

    private fun queryQuestTemplates(): JsonObject = buildJsonObject {
        putJsonArray("questTypes") {
            QuestType.values().forEach { type ->
                addJsonObject {
                    put("type", JsonPrimitive(type.name))
                }
            }
        }
    }

    private fun queryLoot(filter: String?): JsonObject = buildJsonObject {
        put("enemyTiers", JsonPrimitive("goblin/kobold=low, orc/troll/ogre=mid, dragon/demon/lich=high, boss=boss"))
        put("dangerTiers", JsonPrimitive("1-2=low, 3-5=mid, 6-8=high, 9+=boss"))
        if (filter != null) {
            put("filter", JsonPrimitive(filter))
            put("note", JsonPrimitive("Loot is generated procedurally based on enemy type and location danger"))
        }
    }

    private fun queryProgression(): JsonObject = buildJsonObject {
        putJsonArray("grades") {
            Grade.values().forEach { grade ->
                addJsonObject {
                    put("name", JsonPrimitive(grade.displayName))
                    put("levelRange", JsonPrimitive("${grade.levelRange.first}-${grade.levelRange.last}"))
                    put("description", JsonPrimitive(grade.description))
                }
            }
        }
        put("xpFormula", JsonPrimitive("XP to next level = (currentLevel + 1) * 100"))
        put("statGrowth", JsonPrimitive("+2 STR/CON, +1 others per level"))
    }

    private fun querySystemVoice(state: GameState): JsonObject {
        val seed = state.seedId?.let { WorldSeeds.byId(it) }
        return buildJsonObject {
            if (seed != null) {
                val voice = seed.systemVoice
                put("personality", JsonPrimitive(voice.personality))
                put("messageStyle", JsonPrimitive(voice.messageStyle))
                putJsonArray("exampleMessages") {
                    voice.exampleMessages.forEach { add(JsonPrimitive(it)) }
                }
                put("attitudeTowardPlayer", JsonPrimitive(voice.attitudeTowardPlayer))
            } else {
                put("personality", JsonPrimitive("Neutral, informative"))
                put("messageStyle", JsonPrimitive("Standard system notifications"))
            }
        }
    }

    private fun stubNarrative(category: String, description: String): JsonObject = buildJsonObject {
        put("category", JsonPrimitive(category))
        put("description", JsonPrimitive(description))
        put("status", JsonPrimitive("TODO: Content to be filled in by writers"))
        put("placeholder", JsonPrimitive(true))
    }
}
