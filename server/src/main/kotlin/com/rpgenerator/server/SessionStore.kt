package com.rpgenerator.server

import com.rpgenerator.core.api.Difficulty
import com.rpgenerator.core.api.SystemType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Persists MCP session → game mappings so sessions survive server restarts.
 * Uses a standalone SQLite DB (separate from per-game DBs).
 */
object SessionStore {

    private val json = Json { ignoreUnknownKeys = true }
    private val DATA_DIR = System.getenv("DATA_DIR") ?: "/data"
    private val DB_PATH = "$DATA_DIR/sessions.db"

    private val connection: Connection by lazy {
        File(DATA_DIR).mkdirs()
        DriverManager.getConnection("jdbc:sqlite:$DB_PATH").also { conn ->
            conn.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    mcp_session_id TEXT PRIMARY KEY,
                    game_id TEXT NOT NULL,
                    system_type TEXT NOT NULL DEFAULT 'SYSTEM_INTEGRATION',
                    seed_id TEXT,
                    difficulty TEXT NOT NULL DEFAULT 'NORMAL',
                    character_name TEXT,
                    backstory TEXT,
                    appearance TEXT,
                    avatar_image_id TEXT,
                    game_started INTEGER NOT NULL DEFAULT 0,
                    game_created INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL DEFAULT 0
                )
            """)
        }
    }

    fun save(mcpSessionId: String, state: PersistedSession) {
        val stmt = connection.prepareStatement("""
            INSERT OR REPLACE INTO sessions
            (mcp_session_id, game_id, system_type, seed_id, difficulty,
             character_name, backstory, appearance, avatar_image_id,
             game_started, game_created, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """)
        stmt.setString(1, mcpSessionId)
        stmt.setString(2, state.gameId)
        stmt.setString(3, state.systemType)
        stmt.setString(4, state.seedId)
        stmt.setString(5, state.difficulty)
        stmt.setString(6, state.characterName)
        stmt.setString(7, state.backstory)
        stmt.setString(8, state.appearance)
        stmt.setString(9, state.avatarImageId)
        stmt.setInt(10, if (state.gameStarted) 1 else 0)
        stmt.setInt(11, if (state.gameCreated) 1 else 0)
        stmt.setLong(12, System.currentTimeMillis() / 1000)
        stmt.executeUpdate()
        stmt.close()
    }

    fun load(mcpSessionId: String): PersistedSession? {
        val stmt = connection.prepareStatement(
            "SELECT * FROM sessions WHERE mcp_session_id = ?"
        )
        stmt.setString(1, mcpSessionId)
        val rs = stmt.executeQuery()
        val result = if (rs.next()) {
            PersistedSession(
                gameId = rs.getString("game_id"),
                systemType = rs.getString("system_type"),
                seedId = rs.getString("seed_id"),
                difficulty = rs.getString("difficulty"),
                characterName = rs.getString("character_name"),
                backstory = rs.getString("backstory"),
                appearance = rs.getString("appearance"),
                avatarImageId = rs.getString("avatar_image_id"),
                gameStarted = rs.getInt("game_started") == 1,
                gameCreated = rs.getInt("game_created") == 1
            )
        } else null
        rs.close()
        stmt.close()
        return result
    }

    fun delete(mcpSessionId: String) {
        val stmt = connection.prepareStatement(
            "DELETE FROM sessions WHERE mcp_session_id = ?"
        )
        stmt.setString(1, mcpSessionId)
        stmt.executeUpdate()
        stmt.close()
    }

    /** Get the data directory for game DBs */
    fun getDataDir(): String = DATA_DIR
}

data class PersistedSession(
    val gameId: String,
    val systemType: String = "SYSTEM_INTEGRATION",
    val seedId: String? = null,
    val difficulty: String = "NORMAL",
    val characterName: String? = null,
    val backstory: String? = null,
    val appearance: String? = null,
    val avatarImageId: String? = null,
    val gameStarted: Boolean = false,
    val gameCreated: Boolean = false
)
