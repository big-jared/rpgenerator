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

    // Lyria music — separate player node, 48kHz stereo
    private var musicPlayerNode: AVAudioPlayerNode? = nil
    private var musicPlaybackFormat: AVAudioFormat? = nil
    private var musicVolume: Float = 0.15  // Background music level

    // Dedicated audio queue — keep audio scheduling off the main thread
    private let audioQueue = DispatchQueue(label: "com.rpgenerator.audio", qos: .userInteractive)

    // Audio accumulation buffer — batch small chunks into larger buffers to avoid micro-gaps
    private var pendingAudioData = Data()
    // ~80ms at 24kHz 16-bit mono = 3840 bytes (2 chunks of 1920)
    // Low enough to stay close to transcript timing, high enough to avoid per-chunk gaps
    private let audioFlushThreshold = 3840

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
            // Match our audio sample rate to reduce resampling artifacts
            try avSession.setPreferredSampleRate(24000)
            // Low buffer duration for snappy playback (5ms)
            try avSession.setPreferredIOBufferDuration(0.005)
            try avSession.setActive(true, options: .notifyOthersOnDeactivation)
            print("SwiftGeminiBridge: Audio session: sampleRate=\(avSession.sampleRate), bufferDuration=\(avSession.ioBufferDuration)")
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

        // 3. Build playback format — Float32 at 24kHz for clean sample-rate conversion.
        //    Int16 → mixer caused scratchy artifacts; Float32 lets AVAudioEngine's
        //    internal SRC produce smooth output at the hardware rate (48kHz).
        guard let outFormat = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: 24000,
            channels: 1,
            interleaved: false
        ) else { return }

        // 4. Attach voice player and connect with Float32 format.
        engine.attach(player)
        engine.connect(player, to: engine.mainMixerNode, format: outFormat)
        print("SwiftGeminiBridge: Mixer format: \(engine.mainMixerNode.outputFormat(forBus: 0))")

        // 4b. Attach music player — Lyria outputs 48kHz stereo 16-bit PCM,
        //     we play it as Float32 stereo at 48kHz, mixed quieter behind voice.
        let musicPlayer = AVAudioPlayerNode()
        guard let musicFormat = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: 48000,
            channels: 2,
            interleaved: false
        ) else { return }
        engine.attach(musicPlayer)
        engine.connect(musicPlayer, to: engine.mainMixerNode, format: musicFormat)
        musicPlayer.volume = musicVolume

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

        // 7. Now that engine is running, start players
        player.play()
        musicPlayer.play()

        self.audioEngine = engine
        self.playerNode = player
        self.playbackFormat = outFormat
        self.audioConverter = converter
        self.musicPlayerNode = musicPlayer
        self.musicPlaybackFormat = musicFormat
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
            // Reconnect players — config change invalidates existing connections
            engine.connect(player, to: engine.mainMixerNode, format: format)
            if let musicPlayer = musicPlayerNode, let musicFormat = musicPlaybackFormat {
                engine.connect(musicPlayer, to: engine.mainMixerNode, format: musicFormat)
                musicPlayer.volume = musicVolume
            }
            do {
                try engine.start()
                player.play()
                musicPlayerNode?.play()
                // Re-install mic tap if recording was active — engine stop removes all taps
                if isRecording {
                    isRecording = false  // reset so startRecording() re-installs
                    startRecording()
                    print("SwiftGeminiBridge: Re-installed mic tap after config change")
                }
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

        var urlString = "\(serverUrl)/ws/receptionist"
        if let token = authToken {
            urlString += "?token=\(token)"
        }
        guard let url = URL(string: urlString) else {
            callback.onError(message: "Invalid receptionist WebSocket URL: \(urlString)")
            callback.onDisconnected()
            return
        }

        print("SwiftGeminiBridge: Connecting to receptionist at \(serverUrl)/ws/receptionist (token=\(authToken != nil ? "yes" : "none"))")
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

        var urlString = "\(serverUrl)/ws/game/\(sessionId)"
        if let token = authToken {
            urlString += "?token=\(token)"
        }
        guard let url = URL(string: urlString) else {
            callback.onError(message: "Invalid game WebSocket URL")
            callback.onDisconnected()
            return
        }

        print("SwiftGeminiBridge: Connecting to game session \(sessionId) (token=\(authToken != nil ? "yes" : "none"))")
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
    /// Auth token — appended as ?token= query param on WebSocket URLs
    private var authToken: String? = nil

    func setSessionId(sessionId: String) {
        self.currentSessionId = sessionId
    }

    func setAuthToken(token: String) {
        self.authToken = token.isEmpty ? nil : token
    }

    // MARK: - WebSocket Receive Loop

    private func receiveMessage() {
        webSocketTask?.receive { [weak self] result in
            guard let self = self else { return }

            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    // Fast-path: detect audio messages and route directly to audio queue
                    // to avoid main-queue latency for the most frequent message type
                    if text.hasPrefix("{\"type\":\"audio\"") || text.hasPrefix("{\"type\": \"audio\"") {
                        self.processAudioMessage(text)
                    } else {
                        DispatchQueue.main.async {
                            self.processServerMessage(text)
                        }
                    }
                case .data(let data):
                    // Binary frames use a prefix byte to distinguish streams:
                    //   0x02 = Lyria music PCM (48kHz stereo 16-bit)
                    //   anything else = voice PCM audio from Gemini (24kHz mono 16-bit)
                    if data.count > 1 && data[0] == 0x02 {
                        self.playMusicAudio(data.subdata(in: 1..<data.count))
                    } else {
                        self.playPcmAudio(data)
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

    /// Fast-path audio handler — runs on WebSocket callback thread, skips main queue entirely.
    private func processAudioMessage(_ text: String) {
        guard let data = text.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let base64 = json["data"] as? String,
              let audioData = Data(base64Encoded: base64) else { return }

        audioChunkCount += 1
        if audioChunkCount % 50 == 1 {
            print("SwiftGeminiBridge: Audio chunk #\(audioChunkCount), \(audioData.count) bytes, engine=\(audioEngine?.isRunning ?? false), player=\(playerNode?.isPlaying ?? false)")
        }
        playPcmAudio(audioData)
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
            // Base64-encoded PCM audio from server (24kHz 16-bit mono)
            if let base64 = json["data"] as? String,
               let audioData = Data(base64Encoded: base64) {
                audioChunkCount += 1
                if audioChunkCount % 50 == 1 {
                    print("SwiftGeminiBridge: Audio chunk #\(audioChunkCount), \(audioData.count) bytes, engine=\(audioEngine?.isRunning ?? false), player=\(playerNode?.isPlaying ?? false)")
                }
                // Play immediately — playPcmAudio dispatches to audio queue internally
                playPcmAudio(audioData)
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
            // success may be at top level OR nested inside "result" object
            let success = (json["success"] as? Bool)
                ?? ((json["result"] as? [String: Any])?["success"] as? Bool)
                ?? false
            print("SwiftGeminiBridge: tool_result: \(name), success=\(success)")
            callback.onToolResult(name: name, success: success)

        case "turn_complete":
            turnCount += 1
            print("SwiftGeminiBridge: Turn \(turnCount) complete, \(audioChunkCount) total audio chunks so far")
            audioChunkCount = 0
            flushPendingAudio()
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
            // Clear buffered audio so stale data doesn't play after interruption.
            // Guard playerNode.reset() — node may have been detached from engine
            // (e.g. after config change or disconnect), and calling it crashes with
            // '_engine != nil' assertion.
            audioQueue.async { [weak self] in
                self?.pendingAudioData.removeAll()
            }
            if let engine = audioEngine, engine.isRunning, let player = playerNode {
                player.reset()
            }
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
        audioQueue.async { [weak self] in
            guard let self = self else { return }
            self.pendingAudioData.append(data)

            // Flush when we've accumulated enough (~200ms) for smooth playback
            if self.pendingAudioData.count >= self.audioFlushThreshold {
                self.flushAudioBuffer()
            }
        }
    }

    /// Flush any remaining audio (called on turn_complete / interrupted)
    private func flushPendingAudio() {
        audioQueue.async { [weak self] in
            guard let self = self, !self.pendingAudioData.isEmpty else { return }
            self.flushAudioBuffer()
        }
    }

    /// Actually schedule accumulated PCM data on the player node. Must be called on audioQueue.
    private func flushAudioBuffer() {
        guard let engine = audioEngine, let player = playerNode, let format = playbackFormat else {
            pendingAudioData.removeAll()
            return
        }
        // Don't touch nodes if engine was torn down
        guard engine.isRunning || isAudioSetup else {
            pendingAudioData.removeAll()
            return
        }

        let pcmData = pendingAudioData
        pendingAudioData = Data()

        let frameCount = pcmData.count / 2  // 16-bit = 2 bytes per sample
        guard frameCount > 0 else { return }

        // Convert Int16 PCM → Float32 for clean sample-rate conversion
        guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(frameCount))
        else { return }
        buffer.frameLength = AVAudioFrameCount(frameCount)

        guard let floatData = buffer.floatChannelData else { return }
        pcmData.withUnsafeBytes { rawBuffer in
            let src = rawBuffer.bindMemory(to: Int16.self)
            let scale: Float = 1.0 / 32768.0
            for i in 0..<frameCount {
                floatData[0][i] = Float(src[i]) * scale
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

    // MARK: - Music Playback (Lyria — 48kHz stereo 16-bit PCM)

    private func playMusicAudio(_ data: Data) {
        audioQueue.async { [weak self] in
            guard let self = self,
                  let player = self.musicPlayerNode,
                  let format = self.musicPlaybackFormat else { return }

            // 48kHz stereo 16-bit = 4 bytes per frame (2 channels × 2 bytes)
            let frameCount = data.count / 4
            guard frameCount > 0 else { return }

            guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(frameCount))
            else { return }
            buffer.frameLength = AVAudioFrameCount(frameCount)

            guard let floatL = buffer.floatChannelData?[0],
                  let floatR = buffer.floatChannelData?[1] else { return }

            data.withUnsafeBytes { rawBuffer in
                let src = rawBuffer.bindMemory(to: Int16.self)
                let scale: Float = 1.0 / 32768.0
                for i in 0..<frameCount {
                    floatL[i] = Float(src[i * 2]) * scale
                    floatR[i] = Float(src[i * 2 + 1]) * scale
                }
            }

            if !player.isPlaying {
                player.play()
            }
            player.scheduleBuffer(buffer, completionHandler: nil)
        }
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
        // Stop engine first — nodes must be stopped while engine owns them,
        // otherwise calling stop() on a detached node crashes with '_engine != nil'.
        if let engine = audioEngine {
            engine.stop()
        }
        audioEngine = nil
        playerNode = nil
        musicPlayerNode = nil
        musicPlaybackFormat = nil
        audioConverter = nil
        isAudioSetup = false
        isRecording = false
        callback = nil
        currentSessionId = nil
        NotificationCenter.default.removeObserver(self)
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
