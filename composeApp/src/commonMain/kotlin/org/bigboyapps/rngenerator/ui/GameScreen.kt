package org.bigboyapps.rngenerator.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bigboyapps.rngenerator.network.GameWebSocketClient

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showHud by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new feed items arrive
    LaunchedEffect(uiState.feedItems.size) {
        if (uiState.feedItems.isNotEmpty()) {
            listState.animateScrollToItem(uiState.feedItems.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {
        // Connection overlay
        if (uiState.connectionState == GameWebSocketClient.ConnectionState.RECONNECTING) {
            ReconnectingOverlay()
        }

        // Story feed
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp), // room for control bar
            contentPadding = PaddingValues(
                top = 48.dp,
                bottom = 16.dp,
                start = 0.dp,
                end = 0.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = uiState.feedItems,
                key = { it.id }
            ) { item ->
                FeedItemView(item)
            }
        }

        // Control bar (bottom)
        ControlBar(
            isListening = uiState.isListening,
            isGeminiSpeaking = uiState.isGeminiSpeaking,
            showTextInput = uiState.showTextInput,
            textInput = uiState.textInput,
            onMicPressed = viewModel::onMicPressed,
            onHudPressed = { showHud = true },
            onToggleTextInput = viewModel::onToggleTextInput,
            onTextInputChanged = viewModel::onTextInputChanged,
            onTextInputSubmit = viewModel::onTextInputSubmit,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Error snackbar
        uiState.errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("Dismiss", color = AppColors.buttonText)
                    }
                },
                containerColor = AppColors.error
            ) {
                Text(error, color = AppColors.buttonText)
            }
        }
    }

    // HUD bottom sheet
    if (showHud) {
        HudPanel(
            stats = uiState.playerStats,
            inventory = uiState.inventory,
            quests = uiState.quests,
            npcsHere = uiState.npcsHere,
            onDismiss = { showHud = false }
        )
    }
}

// ── Feed Item Renderers ──────────────────────────────────────────

@Composable
internal fun PreviewFeedItemView(item: FeedItem) = FeedItemView(item)

@Composable
private fun FeedItemView(item: FeedItem) {
    when (item) {
        is FeedItem.SceneImage -> SceneImageCard(item)
        is FeedItem.Narration -> NarrationBlock(item)
        is FeedItem.PlayerMessage -> PlayerBubble(item)
        is FeedItem.NpcDialogue -> NpcDialogueBubble(item)
        is FeedItem.XpGain -> NotificationCard("+${item.amount} XP", AppColors.xpGreen, "XP")
        is FeedItem.ItemGained -> NotificationCard(
            "${item.itemName}${if (item.quantity > 1) " x${item.quantity}" else ""}",
            AppColors.accent, "ITEM"
        )
        is FeedItem.GoldGained -> NotificationCard("+${item.amount} Gold", AppColors.questGold, "GOLD")
        is FeedItem.QuestUpdate -> QuestNotificationCard(item)
        is FeedItem.LevelUp -> LevelUpCard(item)
        is FeedItem.SystemNotice -> SystemNoticeBlock(item)
        is FeedItem.CompanionAside -> CompanionAsideBubble(item)
    }
}

@Composable
private fun SceneImageCard(item: FeedItem.SceneImage) {
    val bitmap: ImageBitmap? = remember(item.imageBytes) {
        decodeImageBytes(item.imageBytes)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Scene",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .heightIn(min = 160.dp, max = 280.dp),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun NarrationBlock(item: FeedItem.Narration) {
    Text(
        text = item.text,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = AppColors.textPrimary,
            lineHeight = 24.sp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
private fun PlayerBubble(item: FeedItem.PlayerMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            color = AppColors.primary,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AppColors.buttonText
                ),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun NpcDialogueBubble(item: FeedItem.NpcDialogue) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // NPC name tag
        Text(
            text = item.npcName,
            style = MaterialTheme.typography.labelMedium.copy(
                color = AppColors.npcCyan,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )

        Surface(
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            color = AppColors.surfaceCard,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AppColors.textPrimary
                ),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun NotificationCard(text: String, accentColor: Color, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = accentColor.copy(alpha = 0.12f),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = accentColor,
                    modifier = Modifier.size(6.dp)
                ) {}
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun QuestNotificationCard(item: FeedItem.QuestUpdate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AppColors.questGold.copy(alpha = 0.12f),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .border(1.dp, AppColors.questGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = item.status.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AppColors.questGold,
                        letterSpacing = 1.sp
                    )
                )
                Text(
                    text = item.questName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = AppColors.questGold,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun LevelUpCard(item: FeedItem.LevelUp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = AppColors.accent.copy(alpha = 0.15f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LEVEL UP",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AppColors.accent,
                        letterSpacing = 2.sp
                    )
                )
                Text(
                    text = "Level ${item.newLevel}",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = AppColors.accent
                    )
                )
            }
        }
    }
}

@Composable
private fun SystemNoticeBlock(item: FeedItem.SystemNotice) {
    Text(
        text = item.text,
        style = MaterialTheme.typography.bodySmall.copy(
            color = AppColors.textMuted,
            fontStyle = FontStyle.Italic
        ),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 4.dp)
    )
}

@Composable
private fun CompanionAsideBubble(item: FeedItem.CompanionAside) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = item.companionName,
            style = MaterialTheme.typography.labelMedium.copy(
                color = AppColors.primaryLight,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
        Surface(
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            color = AppColors.primaryLight.copy(alpha = 0.12f),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AppColors.textPrimary,
                    fontStyle = FontStyle.Italic
                ),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

// ── Reconnecting Overlay ─────────────────────────────────────────

@Composable
private fun ReconnectingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.overlay),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = AppColors.accent)
            Text(
                text = "Reconnecting...",
                style = MaterialTheme.typography.bodyLarge.copy(color = AppColors.buttonText)
            )
        }
    }
}

// ── Control Bar ──────────────────────────────────────────────────

@Composable
private fun ControlBar(
    isListening: Boolean,
    isGeminiSpeaking: Boolean,
    showTextInput: Boolean,
    textInput: String,
    onMicPressed: () -> Unit,
    onHudPressed: () -> Unit,
    onToggleTextInput: () -> Unit,
    onTextInputChanged: (String) -> Unit,
    onTextInputSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AppColors.background.copy(alpha = 0f),
                        AppColors.background.copy(alpha = 0.95f),
                        AppColors.background
                    )
                )
            )
            .padding(bottom = 24.dp)
    ) {
        // Text input (expandable)
        AnimatedVisibility(visible = showTextInput) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = onTextInputChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type an action...", color = AppColors.textMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onTextInputSubmit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.textPrimary,
                        unfocusedTextColor = AppColors.textSecondary,
                        focusedBorderColor = AppColors.primary,
                        unfocusedBorderColor = AppColors.divider,
                        cursorColor = AppColors.primary,
                        focusedContainerColor = AppColors.surface,
                        unfocusedContainerColor = AppColors.surface
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onTextInputSubmit,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = AppColors.primary,
                        contentColor = AppColors.buttonText
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                    Text(">", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }

        // Button row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text input toggle
            IconButton(onClick = onToggleTextInput) {
                Text(
                    text = if (showTextInput) "x" else "Aa",
                    color = AppColors.textMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Mic button
            val micColor = when {
                isListening -> AppColors.micActive
                isGeminiSpeaking -> AppColors.micSpeaking
                else -> AppColors.micIdle
            }
            val micBorderColor = when {
                isListening -> AppColors.micActive
                isGeminiSpeaking -> AppColors.accent
                else -> AppColors.divider
            }

            Button(
                onClick = onMicPressed,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(2.dp, micBorderColor, CircleShape),
                colors = ButtonDefaults.buttonColors(containerColor = micColor),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = if (isListening) "||" else "MIC",
                    color = Color.White,
                    fontSize = if (isListening) 22.sp else 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // HUD button
            IconButton(onClick = onHudPressed) {
                Text(
                    text = "HUD",
                    color = AppColors.textMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
