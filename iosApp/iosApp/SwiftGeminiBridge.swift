import Foundation
import AVFoundation
import ComposeApp

/// URLSessionWebSocketTask implementation of NativeGeminiBridge.
/// Connects to the RPGenerator server via WebSocket instead of Firebase AI SDK.
class SwiftGeminiBridge: NativeGeminiBridge {

    private var webSocketTask: URLSessionWebSocketTask? = nil
    private var callback: GeminiMessageCallback? = nil
    private var isReceptionistMode = false
    /// Base WebSocket URL (Kotlin game server, port 8080).
    private var serverUrl: String = "ws://localhost:8080"

    // Audio debug
    private var audioChunkCount = 0
    private var turnCount = 0
    private var micChunkCount = 0

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
    // ~200ms at 24kHz 16-bit mono = 9600 bytes (5 chunks of 1920)
    // Large enough to absorb WebSocket jitter, small enough to keep latency reasonable
    private let audioFlushThreshold = 9600
    // Pre-buffer: accumulate ~400ms before scheduling the first buffer of a new turn,
    // so the player node has a cushion and doesn't drain between arrivals
    private let audioPreBufferThreshold = 19200
    private var isPreBuffering = true

    // MARK: - Configuration

    func configure(serverUrl: String) {
        // Strip trailing slash
        var url = serverUrl.hasSuffix("/") ? String(serverUrl.dropLast()) : serverUrl
        // Ensure ws:// or wss:// prefix
        if url.hasPrefix("http://") {
            url = "ws://" + url.dropFirst("http://".count)
        } else if url.hasPrefix("https://") {
            url = "wss://" + url.dropFirst("https://".count)
        } else if !url.hasPrefix("ws://") && !url.hasPrefix("wss://") {
            url = "ws://" + url
        }
        // TODO: Remap to 8081 once Python bridge is stable
        // url = url.replacingOccurrences(of: ":8080", with: ":8081")
        self.serverUrl = url
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

        // 5. Start engine BEFORE reading input format — VPIO may change the
        //    input node's format when the engine connects to hardware. Reading
        //    the format pre-start and building a converter from it produces
        //    all-zero output because the format is stale by the time audio flows.
        do {
            try engine.start()
        } catch {
            print("SwiftGeminiBridge: Engine start error: \(error)")
            return
        }

        // 6. Now that engine is running, start players
        player.play()
        musicPlayer.play()

        // 7. Read input format AFTER engine.start() — this is the real hardware format
        let inputFormat = inputNode.outputFormat(forBus: 0)
        print("SwiftGeminiBridge: Input format after engine start: \(inputFormat)")

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
        audioQueue.async { [weak self] in
            guard let self = self else { return }

            if !engine.isRunning {
                print("SwiftGeminiBridge: Engine stopped after config change, reconnecting and restarting")
                // Reconnect players — config change invalidates existing connections
                engine.connect(player, to: engine.mainMixerNode, format: format)
                if let musicPlayer = self.musicPlayerNode, let musicFormat = self.musicPlaybackFormat {
                    engine.connect(musicPlayer, to: engine.mainMixerNode, format: musicFormat)
                    musicPlayer.volume = self.musicVolume
                }
                do {
                    try engine.start()
                    player.play()
                    self.musicPlayerNode?.play()
                } catch {
                    print("SwiftGeminiBridge: Engine restart after config change failed: \(error)")
                    return
                }
            }

            // Rebuild audio converter — VPIO config change can alter the input format
            // even while the engine is still running (e.g. when playback drains after
            // model turn completes). The old converter silently produces empty output.
            let newInputFormat = engine.inputNode.outputFormat(forBus: 0)
            print("SwiftGeminiBridge: Post-config-change input format: \(newInputFormat)")
            if let targetFmt = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: true),
               let newConverter = AVAudioConverter(from: newInputFormat, to: targetFmt) {
                self.audioConverter = newConverter
            }

            // Re-install mic tap with the new converter — the old tap's captured converter
            // is stale and will produce silence or garbage with the new input format.
            if self.isRecording {
                self.isRecording = false  // reset so startRecording() re-installs
                self.startRecording()
                print("SwiftGeminiBridge: Re-installed mic tap after config change")
            }
        }
    }

    @objc private func handleAudioInterruption(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: typeValue) else { return }

        switch type {
        case .began:
            let reason = userInfo[AVAudioSessionInterruptionReasonKey] as? UInt
            let wasSuspended = userInfo[AVAudioSessionInterruptionWasSuspendedKey] as? Bool ?? false
            let route = AVAudioSession.sharedInstance().currentRoute.outputs.map { $0.portType.rawValue }.joined(separator: ",")
            print("SwiftGeminiBridge: Audio interruption began — reason=\(reason ?? 999), wasSuspended=\(wasSuspended), route=\(route), engine=\(audioEngine?.isRunning ?? false)")
        case .ended:
            print("SwiftGeminiBridge: Audio interruption ended, restarting engine")
            audioQueue.async { [weak self] in
                guard let self = self, let engine = self.audioEngine else { return }
                do {
                    // Re-activate audio session — it gets deactivated during interruption
                    // (phone call, Siri, etc.) and engine.start() will fail without this.
                    let avSession = AVAudioSession.sharedInstance()
                    try avSession.setActive(true, options: .notifyOthersOnDeactivation)

                    try engine.start()
                    self.playerNode?.play()
                    self.musicPlayerNode?.play()

                    // Rebuild converter — input format may have changed during interruption
                    let newInputFormat = engine.inputNode.outputFormat(forBus: 0)
                    if let targetFmt = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16000, channels: 1, interleaved: true),
                       let newConverter = AVAudioConverter(from: newInputFormat, to: targetFmt) {
                        self.audioConverter = newConverter
                    }

                    // Re-install mic tap — interruption stops engine which removes all taps
                    if self.isRecording {
                        self.isRecording = false
                        self.startRecording()
                        print("SwiftGeminiBridge: Re-installed mic tap after interruption")
                    }
                } catch {
                    print("SwiftGeminiBridge: Failed to restart after interruption: \(error)")
                }
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
                        self.audioChunkCount += 1
                        if self.audioChunkCount % 50 == 1 {
                            // Sample first few Int16 values to verify PCM data isn't silence/garbage
                            var samples: [Int16] = []
                            let sampleCount = min(data.count / 2, 5)
                            data.withUnsafeBytes { raw in
                                let src = raw.bindMemory(to: Int16.self)
                                for i in 0..<sampleCount {
                                    samples.append(src[i])
                                }
                            }
                            let maxAbs = samples.map { abs(Int32($0)) }.max() ?? 0
                            let route = AVAudioSession.sharedInstance().currentRoute.outputs.map { $0.portType.rawValue }.joined(separator: ",")
                            print("SwiftGeminiBridge: Audio chunk #\(self.audioChunkCount), \(data.count)B, samples=\(samples), peak=\(maxAbs), engine=\(self.audioEngine?.isRunning ?? false), player=\(self.playerNode?.isPlaying ?? false), route=\(route), pending=\(self.pendingAudioData.count)")
                        }
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
            // Reset pre-buffer for next turn so it builds a fresh cushion
            audioQueue.async { [weak self] in self?.isPreBuffering = true }
            callback.onTurnComplete()

        case "onboarding_complete":
            let seedId = json["seedId"] as? String ?? "integration"
            let playerName = json["playerName"] as? String ?? "Adventurer"
            let backstory = json["backstory"] as? String ?? ""
            let portraitDesc = json["portraitDescription"] as? String ?? ""
            print("SwiftGeminiBridge: onboarding_complete: name=\(playerName), seed=\(seedId)")
            callback.onOnboardingComplete(seedId: seedId, playerName: playerName, backstory: backstory, portraitDescription: portraitDesc)

        case "connected":
            print("SwiftGeminiBridge: Server confirmed connection")
            callback.onConnected()

        case "error":
            let message = json["message"] as? String ?? "Unknown error"
            print("SwiftGeminiBridge: Server error: \(message)")
            callback.onError(message: message)

        case "interrupted":
            // Stop old audio immediately, but use stop()+play() instead of reset().
            // reset() can leave the node in a broken state; stop()+play() cleanly
            // drains scheduled buffers and re-arms the node for new ones.
            print("SwiftGeminiBridge: INTERRUPTED — dropping pending=\(pendingAudioData.count)B")
            audioQueue.async { [weak self] in
                guard let self = self else { return }
                self.pendingAudioData.removeAll()
                self.isPreBuffering = true  // Reset for next turn
                if let player = self.playerNode, player.isPlaying {
                    player.stop()
                    player.play()
                }
                print("SwiftGeminiBridge: INTERRUPTED cleanup done, player=\(self.playerNode?.isPlaying ?? false)")
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

        case "feed", "feed_sync":
            // Pass feed messages through as raw JSON — Kotlin side parses them
            callback.onFeedMessage(json: text)

        default:
            print("SwiftGeminiBridge: Unknown message type: \(type)")
        }
    }

    // MARK: - Audio Playback

    private func playPcmAudio(_ data: Data) {
        audioQueue.async { [weak self] in
            guard let self = self else { return }
            self.pendingAudioData.append(data)

            // Pre-buffer: accumulate a larger cushion before scheduling the very first
            // buffer of a turn, so the player node doesn't drain between arrivals.
            let threshold = self.isPreBuffering ? self.audioPreBufferThreshold : self.audioFlushThreshold
            if self.pendingAudioData.count >= threshold {
                self.isPreBuffering = false
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

        // If engine stopped (config change, interruption, etc.), try to restart it.
        // Without this, audio silently drops until the session ends.
        if !engine.isRunning {
            print("SwiftGeminiBridge: Engine not running in flushAudioBuffer, attempting restart")
            do {
                try engine.start()
                player.play()
                musicPlayerNode?.play()
                print("SwiftGeminiBridge: Engine restarted successfully")
            } catch {
                print("SwiftGeminiBridge: Engine restart failed: \(error), dropping \(pendingAudioData.count) bytes")
                pendingAudioData.removeAll()
                return
            }
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
            print("SwiftGeminiBridge: playerNode stopped, restarting (engine=\(engine.isRunning))")
            player.play()
        }
        // Log every 50 flushes to track playback health
        if audioChunkCount % 50 == 0 {
            print("SwiftGeminiBridge: flush \(pcmData.count)B → \(frameCount) frames, engine=\(engine.isRunning), player=\(player.isPlaying), vol=\(player.volume)")
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

        // Capture converter locally — if handleEngineConfigChange rebuilds self.audioConverter
        // on audioQueue, the tap callback (which runs on a realtime thread) won't race with it.
        let capturedConverter = converter

        let targetFormat = AVAudioFormat(
            commonFormat: .pcmFormatInt16,
            sampleRate: 16000,
            channels: 1,
            interleaved: true
        )!

        // Use nil format — lets the engine deliver its native post-VPIO format,
        // which matches the converter's input format (both read after engine.start).
        // Passing an explicit format that doesn't match the running input node
        // causes the tap to deliver all-zero buffers.
        let actualFormat = inputNode.outputFormat(forBus: 0)
        print("SwiftGeminiBridge: Installing mic tap, inputNode format=\(actualFormat)")

        inputNode.installTap(onBus: 0, bufferSize: 4096, format: nil) { [weak self] buffer, _ in
            guard let self = self, let task = self.webSocketTask else { return }

            let chunkFrames: AVAudioFrameCount = 1600
            guard let outputBuffer = AVAudioPCMBuffer(pcmFormat: targetFormat, frameCapacity: chunkFrames) else { return }

            var error: NSError?
            capturedConverter.convert(to: outputBuffer, error: &error) { _, outStatus in
                outStatus.pointee = .haveData
                return buffer
            }

            guard error == nil, let channelData = outputBuffer.int16ChannelData else { return }

            let frameLength = Int(outputBuffer.frameLength)
            let data = Data(bytes: channelData[0], count: frameLength * 2)

            self.micChunkCount += 1
            if self.micChunkCount % 100 == 1 {
                // Sample mic output to verify we're capturing real audio, not silence
                var samples: [Int16] = []
                let sampleCount = min(5, frameLength)
                for i in 0..<sampleCount { samples.append(channelData[0][i]) }
                let peak = samples.map { abs(Int32($0)) }.max() ?? 0
                print("SwiftGeminiBridge: Mic chunk #\(self.micChunkCount), \(data.count)B, samples=\(samples), peak=\(peak)")
            }

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
