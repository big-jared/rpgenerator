package com.rpgenerator.server

import com.rpgenerator.core.api.AgentStream
import com.rpgenerator.core.api.LLMInterface
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

/**
 * Wraps an LLMInterface to track all agent conversations for debug introspection.
 * Each agent is identified by the first ~80 chars of its system prompt.
 */
class TrackingLLMInterface(private val delegate: LLMInterface) : LLMInterface {

    override val maxContextTokens: Int get() = delegate.maxContextTokens

    private val _agents = mutableListOf<TrackedAgent>()
    val agents: List<TrackedAgent> get() = _agents.toList()

    override fun startAgent(systemPrompt: String): AgentStream {
        val agent = TrackedAgent(
            agentId = "agent_${_agents.size}",
            name = inferAgentName(systemPrompt),
            systemPrompt = systemPrompt
        )
        _agents.add(agent)
        val delegateStream = delegate.startAgent(systemPrompt)
        return TrackingAgentStream(delegateStream, agent)
    }

    private fun inferAgentName(systemPrompt: String): String {
        val lower = systemPrompt.lowercase()
        return when {
            "narrator" in lower -> "Narrator"
            "game master" in lower -> "GameMaster"
            "npc" in lower && "autonomous" in lower -> "AutonomousNPC"
            "npc" in lower -> "NPC"
            "quest" in lower -> "QuestGenerator"
            "location" in lower -> "LocationGenerator"
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
}
