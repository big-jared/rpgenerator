import Foundation
import AVFoundation
import ComposeApp

/// URLSessionWebSocketTask implementation of NativeGeminiBridge.
/// Connects to the RPGenerator server via WebSocket instead of Firebase AI SDK.
class SwiftGeminiBridge: NativeGeminiBridge {

    private var webSocketTask: URLSessionWebSocketTask? = nil
    private var callback: GeminiMessageCallback? = nil
    private var isReceptionistMode = false
    private var serverUrl: String = "ws://localhost:8080"

    // Audio debug
    private var audioChunkCount = 0
    private var turnCount = 0

    // Audio — single engine, set up once
    private var audioEngine: AVAudioEngine? = nil
    private var playerNode: AVAudioPlayerNode? = nil
    private var audioConverter: AVAudioConverter? = nil
    private var playbackFormat: AVAudioFormat? = nil
    private var isAudioSetup = false
    private var isRecording = false

    // MARK: - Configuration

    func configure(serverUrl: String) {
        // Strip trailing slash
        self.serverUrl = serverUrl.hasSuffix("/") ? String(serverUrl.dropLast()) : serverUrl
        // Ensure ws:// or wss:// prefix
        if self.serverUrl.hasPrefix("http://") {
            self.serverUrl = "ws://" + self.serverUrl.dropFirst("http://".count)
        } else if self.serverUrl.hasPrefix("https://") {
            self.serverUrl = "wss://" + self.serverUrl.dropFirst("https://".count)
        } else if !self.serverUrl.hasPrefix("ws://") && !self.serverUrl.hasPrefix("wss://") {
            self.serverUrl = "ws://" + self.serverUrl
        }
        print("SwiftGeminiBridge: configured serverUrl=\(self.serverUrl)")
    }

    // MARK: - Audio Setup (call once before session starts)

    private func setupAudio() {
        guard !isAudioSetup else { return }

        let avSession = AVAudioSession.sharedInstance()
        do {
            // Use .default mode — .voiceChat forces earpiece which fights .defaultToSpeaker.
            // Hardware AEC is provided by setVoiceProcessingEnabled() on the engine instead.
            try avSession.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth])
            try avSession.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            print("SwiftGeminiBridge: Audio session error: \(error)")
            return
        }

        let engine = AVAudioEngine()
        let player = AVAudioPlayerNode()

        // 1. Access inputNode FIRST (forces engine to create I/O nodes)
        let inputNode = engine.inputNode

        // 2. Enable Voice Processing I/O — hardware echo cancellation, AGC, noise suppression.
        //    Must be called BEFORE engine.start() while engine is stopped.
        do {
            try inputNode.setVoiceProcessingEnabled(true)
            print("SwiftGeminiBridge: Voice processing (AEC) enabled, inputNode.isVoiceProcessingEnabled=\(inputNode.isVoiceProcessingEnabled)")
        } catch {
            print("SwiftGeminiBridge: Voice processing enable failed: \(error)")
        }

        // 3. Build playback format — server sends 24kHz 16-bit mono PCM
        guard let outFormat = AVAudioFormat(
            commonFormat: .pcmFormatInt16,
            sampleRate: 24000,
            channels: 1,
            interleaved: true
        ) else { return }

        // 4. Attach player and connect with our PCM format.
        //    AVAudioEngine handles sample rate conversion to the mixer automatically.
        engine.attach(player)
        engine.connect(player, to: engine.mainMixerNode, format: outFormat)
        print("SwiftGeminiBridge: Mixer format: \(engine.mainMixerNode.outputFormat(forBus: 0))")

        // 5. Read input format AFTER enabling voice processing (VPIO may change it)
        let inputFormat = inputNode.outputFormat(forBus: 0)
        print("SwiftGeminiBridge: Input format after VPIO: \(inputFormat)")

        guard let targetFormat = AVAudioFormat(
            commonFormat: .pcmFormatInt16,
            sampleRate: 16000,
            channels: 1,
            interleaved: true
        ) else { return }

        guard let converter = AVAudioConverter(from: inputFormat, to: targetFormat) else {
            print("SwiftGeminiBridge: Failed to create audio converter from \(inputFormat) to 16kHz")
            return
        }

        // 6. Start engine
        do {
            try engine.start()
        } catch {
            print("SwiftGeminiBridge: Engine start error: \(error)")
            return
        }

        // 7. Now that engine is running, start player
        player.play()

        self.audioEngine = engine
        self.playerNode = player
        self.playbackFormat = outFormat
        self.audioConverter = converter
        self.isAudioSetup = true

        // Monitor engine config changes (VPIO can trigger reconfiguration)
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleEngineConfigChange),
            name: .AVAudioEngineConfigurationChange,
            object: engine
        )

        // Monitor for audio interruptions (phone calls, Siri, etc.)
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAudioInterruption),
            name: AVAudioSession.interruptionNotification,
            object: nil
        )
    }

    @objc private func handleEngineConfigChange(notification: Notification) {
        print("SwiftGeminiBridge: Engine config changed")
        guard let engine = audioEngine, let player = playerNode, let format = playbackFormat else { return }
        if !engine.isRunning {
            print("SwiftGeminiBridge: Engine stopped after config change, reconnecting and restarting")
            // Reconnect player — config change invalidates existing connections
            engine.connect(player, to: engine.mainMixerNode, format: format)
            do {
                try engine.start()
                player.play()
            } catch {
                print("SwiftGeminiBridge: Engine restart after config change failed: \(error)")
            }
        }
    }

    @objc private func handleAudioInterruption(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: typeValue) else { return }

        switch type {
        case .began:
            print("SwiftGeminiBridge: Audio interruption began")
        case .ended:
            print("SwiftGeminiBridge: Audio interruption ended, restarting engine")
            do {
                try audioEngine?.start()
                playerNode?.play()
            } catch {
                print("SwiftGeminiBridge: Failed to restart after interruption: \(error)")
            }
        @unknown default:
            break
        }
    }

    // MARK: - Session Management

    func startReceptionistSession(prompt: String, toolsJson: String, voiceName: String, callback: GeminiMessageCallback) {
        self.callback = callback
        self.isReceptionistMode = true

        // Set up audio graph synchronously before connecting
        setupAudio()

        guard let url = URL(string: "\(serverUrl)/ws/receptionist") else {
            callback.onError(message: "Invalid receptionist WebSocket URL: \(serverUrl)/ws/receptionist")
            callback.onDisconnected()
            return
        }

        print("SwiftGeminiBridge: Connecting to receptionist at \(url)")
        let task = URLSession.shared.webSocketTask(with: url)
        self.webSocketTask = task
        task.resume()

        // Send connect message with voice and prompt
        let connectMsg: [String: Any] = [
            "type": "connect",
            "voiceName": voiceName,
            "prompt": prompt
        ]
        sendJsonMessage(connectMsg)

        // Start receive loop
        receiveMessage()
    }

    func startGameSession(systemPrompt: String, toolsJson: String, voiceName: String, callback: GeminiMessageCallback) {
        self.callback = callback
        self.isReceptionistMode = false

        setupAudio()

        // Extract sessionId from toolsJson or systemPrompt context — the BridgedGeminiConnection
        // calls startGameSession after fetching setup, so we parse sessionId from the URL pattern.
        // Actually, the session ID should be passed. For now, we'll use a method to set it.
        guard let sessionId = currentSessionId else {
            callback.onError(message: "No sessionId set. Call setSessionId() before startGameSession().")
            callback.onDisconnected()
            return
        }

        guard let url = URL(string: "\(serverUrl)/ws/game/\(sessionId)") else {
            callback.onError(message: "Invalid game WebSocket URL")
            callback.onDisconnected()
            return
        }

        print("SwiftGeminiBridge: Connecting to game session at \(url)")
        let task = URLSession.shared.webSocketTask(with: url)
        self.webSocketTask = task
        task.resume()

        // Send connect message
        let connectMsg: [String: Any] = [
            "type": "connect",
            "voiceName": voiceName
        ]
        sendJsonMessage(connectMsg)

        // Start receive loop
        receiveMessage()
    }

    /// Session ID for game mode — set by Kotlin side before calling startGameSession
    private var currentSessionId: String? = nil

    func setSessionId(sessionId: String) {
        self.currentSessionId = sessionId
    }

    // MARK: - WebSocket Receive Loop

    private func receiveMessage() {
        webSocketTask?.receive { [weak self] result in
            guard let self = self else { return }

            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    DispatchQueue.main.async {
                        self.processServerMessage(text)
                    }
                case .data(let data):
                    // Binary frame — treat as raw PCM audio
                    DispatchQueue.main.async {
                        self.playPcmAudio(data)
                        self.callback?.onAudio(pcmData: data.toKotlinByteArray())
                    }
                @unknown default:
                    break
                }
                // Continue receiving
                self.receiveMessage()

            case .failure(let error):
                print("SwiftGeminiBridge: WebSocket receive error: \(error)")
                DispatchQueue.main.async {
                    self.callback?.onError(message: "Connection lost: \(error.localizedDescription)")
                    self.callback?.onDisconnected()
                }
            }
        }
    }

    private func processServerMessage(_ text: String) {
        guard let callback = callback else { return }
        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["type"] as? String else {
            print("SwiftGeminiBridge: Failed to parse server message: \(text.prefix(200))")
            return
        }

        switch type {
        case "audio":
            // Base64-encoded PCM audio
            if let base64 = json["data"] as? String,
               let audioData = Data(base64Encoded: base64) {
                audioChunkCount += 1
                if audioChunkCount % 50 == 1 {
                    print("SwiftGeminiBridge: Audio chunk #\(audioChunkCount), \(audioData.count) bytes, engine=\(audioEngine?.isRunning ?? false), player=\(playerNode?.isPlaying ?? false)")
                }
                playPcmAudio(audioData)
                callback.onAudio(pcmData: audioData.toKotlinByteArray())
            }

        case "transcript":
            let role = json["role"] as? String ?? "model"
            let content = json["content"] as? String ?? ""
            callback.onTranscript(role: role, text: content)

        case "text":
            let content = json["content"] as? String ?? ""
            callback.onText(text: content)

        case "tool_call":
            let name = json["name"] as? String ?? ""
            let args = json["args"] as? [String: Any] ?? [:]
            let argsJson = serializeDict(args)
            print("SwiftGeminiBridge: tool_call: \(name), args: \(argsJson)")
            callback.onToolCall(id: name, name: name, argsJson: argsJson)

        case "tool_result":
            let name = json["name"] as? String ?? ""
            let success = json["success"] as? Bool ?? false
            print("SwiftGeminiBridge: tool_result: \(name), success=\(success)")

        case "turn_complete":
            turnCount += 1
            print("SwiftGeminiBridge: Turn \(turnCount) complete, \(audioChunkCount) total audio chunks so far")
            audioChunkCount = 0
            callback.onTurnComplete()

        case "onboarding_complete":
            let seedId = json["seedId"] as? String ?? "integration"
            let playerName = json["playerName"] as? String ?? "Adventurer"
            let backstory = json["backstory"] as? String ?? ""
            print("SwiftGeminiBridge: onboarding_complete: name=\(playerName), seed=\(seedId)")
            callback.onOnboardingComplete(seedId: seedId, playerName: playerName, backstory: backstory)

        case "connected":
            print("SwiftGeminiBridge: Server confirmed connection")
            callback.onConnected()

        case "error":
            let message = json["message"] as? String ?? "Unknown error"
            print("SwiftGeminiBridge: Server error: \(message)")
            callback.onError(message: message)

        case "interrupted":
            playerNode?.reset()
            callback.onInterrupted()

        case "game_event":
            if let eventObj = json["event"] {
                if let eventData = try? JSONSerialization.data(withJSONObject: eventObj),
                   let eventStr = String(data: eventData, encoding: .utf8) {
                    callback.onGameEvent(eventJson: eventStr)
                }
            }

        case "state_update":
            if let stateObj = json["state"] {
                if let stateData = try? JSONSerialization.data(withJSONObject: stateObj),
                   let stateStr = String(data: stateData, encoding: .utf8) {
                    callback.onStateUpdate(stateJson: stateStr)
                }
            }

        case "scene_image":
            if let base64 = json["data"] as? String,
               let mimeType = json["mimeType"] as? String {
                callback.onSceneImage(imageBase64: base64, mimeType: mimeType)
            }

        default:
            print("SwiftGeminiBridge: Unknown message type: \(type)")
        }
    }

    // MARK: - Audio Playback

    private func playPcmAudio(_ data: Data) {
        guard let player = playerNode, let format = playbackFormat else { return }

        let frameCount = data.count / 2
        guard frameCount > 0,
              let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(frameCount))
        else { return }
        buffer.frameLength = AVAudioFrameCount(frameCount)

        if let channelData = buffer.int16ChannelData {
            data.withUnsafeBytes { rawBuffer in
                let src = rawBuffer.bindMemory(to: Int16.self)
                for i in 0..<frameCount {
                    channelData[0][i] = src[i]
                }
            }
        }

        // Re-play if the node drained its queue and went idle
        if !player.isPlaying {
            print("SwiftGeminiBridge: playerNode stopped, restarting. Engine running: \(audioEngine?.isRunning ?? false)")
            if let engine = audioEngine, !engine.isRunning {
                do {
                    try engine.start()
                } catch {
                    print("SwiftGeminiBridge: Engine restart failed: \(error)")
                    return
                }
            }
            player.play()
        }
        player.scheduleBuffer(buffer, completionHandler: nil)
    }

    // MARK: - Audio Recording

    func startRecording() {
        guard !isRecording, let engine = audioEngine, let converter = audioConverter else { return }

        let inputNode = engine.inputNode
        let inputFormat = inputNode.outputFormat(forBus: 0)

        let targetFormat = AVAudioFormat(
            commonFormat: .pcmFormatInt16,
            sampleRate: 16000,
            channels: 1,
            interleaved: true
        )!

        inputNode.installTap(onBus: 0, bufferSize: 4096, format: inputFormat) { [weak self] buffer, _ in
            guard let self = self, let task = self.webSocketTask else { return }

            let chunkFrames: AVAudioFrameCount = 1600
            guard let outputBuffer = AVAudioPCMBuffer(pcmFormat: targetFormat, frameCapacity: chunkFrames) else { return }

            var error: NSError?
            converter.convert(to: outputBuffer, error: &error) { _, outStatus in
                outStatus.pointee = .haveData
                return buffer
            }

            guard error == nil, let channelData = outputBuffer.int16ChannelData else { return }

            let frameLength = Int(outputBuffer.frameLength)
            let data = Data(bytes: channelData[0], count: frameLength * 2)

            // Send as binary WebSocket frame (server handles Frame.Binary)
            task.send(.data(data)) { sendError in
                if let sendError = sendError {
                    print("SwiftGeminiBridge: Audio send error: \(sendError)")
                }
            }
        }

        isRecording = true
    }

    func stopRecording() {
        guard isRecording, let engine = audioEngine else { return }
        engine.inputNode.removeTap(onBus: 0)
        isRecording = false
    }

    // MARK: - Tool Calls

    func sendToolResponse(id: String, name: String, responseJson: String) {
        // No-op — server handles tool execution and responses directly
        print("SwiftGeminiBridge: sendToolResponse(\(name)) — no-op, server handles tools")
    }

    // MARK: - Text

    func sendText(text: String) {
        let msg: [String: Any] = [
            "type": "text",
            "content": text
        ]
        sendJsonMessage(msg)
    }

    // MARK: - Lifecycle

    func disconnect() {
        stopRecording()
        webSocketTask?.cancel(with: .goingAway, reason: nil)
        webSocketTask = nil
    }

    func close() {
        disconnect()
        playerNode?.stop()
        audioEngine?.stop()
        audioEngine = nil
        playerNode = nil
        audioConverter = nil
        isAudioSetup = false
        isRecording = false
        callback = nil
        currentSessionId = nil
    }

    // MARK: - WebSocket Helpers

    private func sendJsonMessage(_ dict: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: dict),
              let str = String(data: data, encoding: .utf8) else {
            print("SwiftGeminiBridge: Failed to serialize message")
            return
        }
        webSocketTask?.send(.string(str)) { error in
            if let error = error {
                print("SwiftGeminiBridge: WebSocket send error: \(error)")
            }
        }
    }

    private func serializeDict(_ dict: [String: Any]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: dict),
              let str = String(data: data, encoding: .utf8) else { return "{}" }
        return str
    }
}

extension Data {
    func toKotlinByteArray() -> KotlinByteArray {
        let bytes = KotlinByteArray(size: Int32(self.count))
        self.withUnsafeBytes { rawBuffer in
            let src = rawBuffer.bindMemory(to: Int8.self)
            for i in 0..<self.count {
                bytes.set(index: Int32(i), value: src[i])
            }
        }
        return bytes
    }
}
