package org.bigboyapps.rngenerator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.bigboyapps.rngenerator.BuildConfig
import org.bigboyapps.rngenerator.MainActivity
import org.bigboyapps.rngenerator.gemini.GeminiLiveConnection
import org.bigboyapps.rngenerator.network.GameApiClient
import org.bigboyapps.rngenerator.network.GameWebSocketClient
import org.bigboyapps.rngenerator.network.ServerMessage

/**
 * Foreground service that keeps the Gemini Live connection and audio pipeline alive
 * when the app is in the background.
 */
class GameSessionService : Service() {

    inner class LocalBinder : Binder() {
        val service: GameSessionService get() = this@GameSessionService
    }

    private val binder = LocalBinder()

    // Owned connection — direct to Gemini
    var connection: GeminiLiveConnection? = null
        private set

    // Expose messages to ViewModel
    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<ServerMessage> = _messages.asSharedFlow()

    private val _connectionState = MutableStateFlow(GameWebSocketClient.ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<GameWebSocketClient.ConnectionState> = _connectionState.asStateFlow()

    private var forwardScope: CoroutineScope? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Create a GeminiLiveConnection and wire up forwarding.
     * Called by both startSession and startReceptionistSession.
     */
    private fun ensureConnection(): GeminiLiveConnection {
        connection?.let { return it }

        val apiKey = BuildConfig.GOOGLE_API_KEY
        val apiClient = GameApiClient("") // placeholder — receptionist doesn't need server
        val conn = GeminiLiveConnection(apiClient, apiKey)
        connection = conn

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        forwardScope = scope

        // Forward connection state
        scope.launch {
            conn.connectionState.collect { state ->
                _connectionState.value = state
            }
        }

        // Forward server messages
        scope.launch {
            conn.messages.collect { msg ->
                _messages.emit(msg)
            }
        }

        return conn
    }

    fun startReceptionistSession(prompt: String) {
        startForegroundWithNotification()
        val conn = ensureConnection()
        conn.startReceptionistSession(prompt)
    }

    fun startSession(serverUrl: String, sessionId: String) {
        startForegroundWithNotification()

        // If we already have a connection from receptionist phase, reuse the apiClient
        if (connection == null) {
            val apiClient = GameApiClient(serverUrl)
            val apiKey = BuildConfig.GOOGLE_API_KEY
            val conn = GeminiLiveConnection(apiClient, apiKey)
            connection = conn

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            forwardScope = scope

            scope.launch {
                conn.connectionState.collect { state ->
                    _connectionState.value = state
                }
            }
            scope.launch {
                conn.messages.collect { msg ->
                    _messages.emit(msg)
                }
            }
        }

        // Start the Gemini Live connection
        connection!!.startSession(serverUrl, sessionId)
    }

    suspend fun sendConnect(voiceName: String = "Kore") {
        connection?.sendConnect(voiceName)
    }

    suspend fun sendText(text: String) {
        connection?.sendText(text)
    }

    fun startRecording() {
        connection?.startRecording()
    }

    fun stopRecording() {
        connection?.stopRecording()
    }

    private fun startForegroundWithNotification() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Adventure in progress")
            .setContentText("Your story continues...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Game Session",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the game connection alive in the background"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /**
     * Disconnect and tear down the current session without destroying the service.
     */
    fun teardown() {
        connection?.close()
        connection = null
        forwardScope?.cancel()
        forwardScope = null
        _connectionState.value = GameWebSocketClient.ConnectionState.DISCONNECTED
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        connection?.close()
        forwardScope?.cancel()
    }

    companion object {
        const val CHANNEL_ID = "game_session"
        const val NOTIFICATION_ID = 1
    }
}
