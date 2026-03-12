package com.rpgenerator.server

import com.rpgenerator.core.api.AgentChunk
import com.rpgenerator.core.api.AgentStream
import com.rpgenerator.core.api.LLMInterface
import com.rpgenerator.core.api.LLMToolDef
import com.rpgenerator.core.api.ToolExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Wraps an LLMInterface to track all agent conversations for debug introspection.
 * Each agent is identified by the first ~80 chars of its system prompt.
 * Uses CopyOnWriteArrayList for thread-safe access across coroutine dispatchers.
 */
class TrackingLLMInterface(private val delegate: LLMInterface) : LLMInterface {

    override val maxContextTokens: Int get() = delegate.maxContextTokens

    private val _agents = CopyOnWriteArrayList<TrackedAgent>()
    val agents: List<TrackedAgent> get() = _agents.toList()

    override fun startAgent(systemPrompt: String): AgentStream {
        val agent = TrackedAgent(
            agentId = "agent_${_agents.size}",
            name = inferAgentName(systemPrompt),
            systemPrompt = systemPrompt
        )
        _agents.add(agent)
        println("[TrackingLLM@${this.hashCode()}] startAgent called, now tracking ${_agents.size} agents: ${agent.name}")
        val delegateStream = delegate.startAgent(systemPrompt)
        return TrackingAgentStream(delegateStream, agent)
    }

    private fun inferAgentName(systemPrompt: String): String {
        val lower = systemPrompt.lowercase()

        // Per-NPC agents: extract character name from "You ARE <Name>"
        if (lower.contains("you are") && lower.contains("trapped in a")) {
            val nameMatch = Regex("""You ARE (\w[\w\s]+?)\.""", RegexOption.IGNORE_CASE).find(systemPrompt)
            if (nameMatch != null) {
                return "NPC: ${nameMatch.groupValues[1].trim()}"
            }
        }

        // Check specific patterns first to avoid false matches.
        // Order matters: prompts may contain keywords for OTHER agents
        // (e.g. a narrator prompt mentions "npc", a decide prompt mentions "narrator").
        // Match on the IDENTITY phrase, not incidental keywords.
        return when {
            "game master engine" in lower -> "GM-Decide"
            "game master" in lower -> "GameMaster"
            "litrpg author narrating" in lower -> "Narrator"
            "narrator" in lower -> "Narrator"
            "you are narrating" in lower -> "Narrator"
            "npc" in lower && "autonomous" in lower -> "AutonomousNPC"
            "you are" in lower && "trapped in a" in lower -> "NPC"
            "quest" in lower && "npc" !in lower -> "QuestGenerator"
            "location" in lower && "npc" !in lower -> "LocationGenerator"
            "story" in lower || "plot" in lower -> "StoryPlanner"
            "archetype" in lower -> "NPCArchetypeGenerator"
            "consensus" in lower -> "ConsensusEngine"
            else -> "Agent_${_agents.size}"
        }
    }
}

data class TrackedAgent(
    val agentId: String,
    val name: String,
    val systemPrompt: String,
    val messages: MutableList<TrackedMessage> = mutableListOf()
) {
    val messageCount: Int get() = messages.size
}

data class TrackedMessage(
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

private class TrackingAgentStream(
    private val delegate: AgentStream,
    private val agent: TrackedAgent
) : AgentStream {

    override suspend fun sendMessage(message: String): Flow<String> {
        agent.messages.add(TrackedMessage("user", message))

        // Collect the full response while re-emitting chunks
        val chunks = mutableListOf<String>()
        val delegateFlow = delegate.sendMessage(message)

        return flow {
            delegateFlow.collect { chunk ->
                chunks.add(chunk)
                emit(chunk)
            }
            // After collection completes, record full response
            val fullResponse = chunks.joinToString("")
            agent.messages.add(TrackedMessage("assistant", fullResponse))
        }
    }

    override suspend fun sendMessageWithTools(
        message: String,
        tools: List<LLMToolDef>,
        executor: ToolExecutor
    ): Flow<String> {
        agent.messages.add(TrackedMessage("user", message))

        val chunks = mutableListOf<String>()
        val delegateFlow = delegate.sendMessageWithTools(message, tools, executor)

        return flow {
            delegateFlow.collect { chunk ->
                chunks.add(chunk)
                emit(chunk)
            }
            val fullResponse = chunks.joinToString("")
            agent.messages.add(TrackedMessage("assistant", fullResponse))
        }
    }

    override suspend fun sendMessageMultimodal(
        message: String,
        generateImage: Boolean
    ): Flow<AgentChunk> {
        agent.messages.add(TrackedMessage("user", message))

        val textParts = mutableListOf<String>()
        val delegateFlow = delegate.sendMessageMultimodal(message, generateImage)

        return flow {
            delegateFlow.collect { chunk ->
                if (chunk is AgentChunk.Text) textParts.add(chunk.content)
                emit(chunk)
            }
            val fullResponse = textParts.joinToString("")
            agent.messages.add(TrackedMessage("assistant", fullResponse + if (generateImage) " [+image]" else ""))
        }
    }
}
