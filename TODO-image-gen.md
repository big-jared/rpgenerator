# Native Gemini Image Generation — Implementation Steps

## Goal
Replace Imagen API calls with Gemini's native interleaved text+image output.
The GM (narrator) generates images inline with narration in a single `generateContent` call.
Satisfies hackathon requirement: "Must use Gemini's interleaved/mixed output capabilities."

## Architecture

```
Player speaks → Companion (Live API) calls send_player_input tool → Server
  → Phase 1+2: Decide agent calls tools (including generate_scene_art flag)
  → Phase 3: Narrator calls generateContent with ["TEXT", "IMAGE"] if flagged
  → Returns narration text + inline image in one response
  → Companion reads text aloud, image appears in feed
```

Image gen only happens on significant moments (new locations, boss encounters, quest milestones).
Most turns are text-only (fast). The GM decides when via `generate_scene_art` tool call.

## Steps

### 1. Add `AgentChunk` sealed class to `LLMInterface.kt`
```kotlin
sealed class AgentChunk {
    data class Text(val content: String) : AgentChunk()
    data class Image(val data: ByteArray, val mimeType: String = "image/png") : AgentChunk()
}
```
Change `AgentStream.sendMessage()` return type: `Flow<String>` → `Flow<AgentChunk>`
Add backward-compat default for `sendMessageWithTools` too.

### 2. Update `GeminiLLM.kt` (server)
- In `sendMessage()`: add `responseModalities: ["TEXT", "IMAGE"]` when image gen is requested
- Extract image `Part`s from response alongside text
- Emit `AgentChunk.Image` for image parts, `AgentChunk.Text` for text parts
- Use `gemini-2.0-flash` for image-capable calls (check model support)

### 3. Update `GameOrchestrator.kt`
- Phase 1+2: When `generate_scene_art` tool is called, record the description but DON'T generate image
- Pass a flag/description to Phase 3 narrator
- Phase 3: If image flag set, tell narrator to use ["TEXT", "IMAGE"] modalities
- Collect `AgentChunk.Image` from narrator flow → emit as `GameEvent.SceneImage` with real image data
- If no image flag, use ["TEXT"] only (fast path)

### 4. Update `TrackingLLMInterface.kt`
- Pass through `Flow<AgentChunk>` instead of `Flow<String>`
- Track text content for logging, pass images through

### 5. Update all callers of `sendMessage()`
These currently do `.toList().joinToString("")` — need to filter to text chunks:
- `GameOrchestrator.kt` (narrator, decide agent)
- `NarratorAgent.kt` (opening, death, respawn)
- `NPCAgent.kt`
- `QuestGeneratorAgent.kt`
- `LocationGeneratorAgent.kt`
- `PlannerAgent.kt`
- `SystemAgent.kt`
- `NPCArchetypeGenerator.kt`
- `StoryPlanningService.kt`

Helper: `Flow<AgentChunk>.textOnly(): Flow<String> = filterIsInstance<AgentChunk.Text>().map { it.content }`

### 6. Update `MockLLMProvider.kt` (tests)
Wrap string emissions in `AgentChunk.Text()`.

### 7. Update narrator prompt (GMPromptBuilder)
When image flag is set, append instruction:
"Generate a scene image that matches the narration. The image should be a digital painting, fantasy concept art style."

### 8. Client: handle image data in tool response
- Server `/tool` endpoint: serialize `GameEvent.SceneImage` with base64 image data
- `GeminiLiveConnection`: extract image data from events, emit `ServerMessage.SceneImage`
- Already handled in `GameViewModel.handleServerMessage()` → adds to feed

## Latency Notes
- Image gen adds ~5-10s to narrator response
- Only triggered on scene transitions (GM decides via generate_scene_art tool)
- Most turns are text-only (< 2s)
- Streaming (`generateContentStream`) could help: text arrives first, image follows
- Consider: companion starts reading text while image is still generating

## GitHub Secret Scanning
- Dismiss the `google-services.json` alert in GitHub Security tab
- Mark as "false positive" — Firebase client keys are not secrets (they ship in every APK)
