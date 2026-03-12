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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import org.bigboyapps.rngenerator.network.GameWebSocketClient

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showHud by remember { mutableStateOf(false) }
    var hudInitialTab by remember { mutableStateOf(HudTab.CHARACTER) }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.feedItems.size) {
        if (uiState.feedItems.isNotEmpty()) {
            listState.animateScrollToItem(uiState.feedItems.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .parchmentBackground()
    ) {
        // Reconnecting overlay
        if (uiState.connectionState == GameWebSocketClient.ConnectionState.RECONNECTING) {
            ReconnectingOverlay()
        }

        // Combat overlay at top
        val hasCombat = uiState.combat != null

        // Story feed
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 110.dp),
            contentPadding = PaddingValues(top = if (hasCombat) 80.dp else 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(items = uiState.feedItems, key = { it.id }) { item ->
                FeedItemView(item)
            }
        }

        // Control bar
        ControlBar(
            isListening = uiState.isListening,
            isGeminiSpeaking = uiState.isGeminiSpeaking,
            showTextInput = uiState.showTextInput,
            textInput = uiState.textInput,
            onMicPressed = viewModel::onMicPressed,
            onMenuPressed = { showMenu = true },
            onToggleTextInput = viewModel::onToggleTextInput,
            onTextInputChanged = viewModel::onTextInputChanged,
            onTextInputSubmit = viewModel::onTextInputSubmit,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Combat overlay at top of screen
        AnimatedVisibility(
            visible = uiState.combat != null,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            uiState.combat?.let { combat ->
                CombatOverlay(combat)
            }
        }

        // Error
        uiState.errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("Dismiss", color = AppColors.buttonText)
                    }
                },
                containerColor = AppColors.hpRed
            ) { Text(error, color = AppColors.parchmentLight) }
        }
    }

    if (showMenu) {
        MenuSheet(
            onDismiss = { showMenu = false },
            onOpenHud = { tab ->
                showMenu = false
                hudInitialTab = tab
                showHud = true
            }
        )
    }

    if (showHud) {
        LaunchedEffect(Unit) { viewModel.fetchCharacterSheet() }
        HudPanel(
            stats = uiState.playerStats,
            fullSheet = uiState.fullCharacterSheet,
            inventory = uiState.inventory,
            quests = uiState.quests,
            npcsHere = uiState.npcsHere,
            onNpcClick = { npcId -> viewModel.selectNpc(npcId) },
            onDismiss = { showHud = false },
            initialTab = hudInitialTab
        )
    }

    uiState.selectedNpcDetails?.let { details ->
        NpcDetailSheet(
            details = details,
            onDismiss = { viewModel.dismissNpcDetails() }
        )
    }
}

// ── Parchment Background ─────────────────────────────────────────

fun Modifier.parchmentBackground() = this
    .background(
        Brush.radialGradient(
            colors = listOf(
                AppColors.parchmentLight,
                AppColors.parchment,
                AppColors.parchmentDark
            ),
            radius = 900f
        )
    )
    .drawBehind {
        // Subtle edge darkening (vignette on parchment)
        val edgeBrush = Brush.radialGradient(
            colors = listOf(Color.Transparent, AppColors.parchmentEdge.copy(alpha = 0.15f)),
            center = Offset(size.width / 2, size.height / 2),
            radius = size.maxDimension * 0.7f
        )
        drawRect(edgeBrush)
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
        is FeedItem.XpGain -> XpNotification(item)
        is FeedItem.ItemGained -> ItemNotification(item)
        is FeedItem.GoldGained -> GoldNotification(item)
        is FeedItem.QuestUpdate -> QuestNotification(item)
        is FeedItem.LevelUp -> LevelUpCard(item)
        is FeedItem.SystemNotice -> SystemNoticeBlock(item)
        is FeedItem.CompanionAside -> CompanionAsideBubble(item)
        is FeedItem.CombatStart -> CombatEncounterCard(item)
        is FeedItem.CombatEnd -> CombatEndCard(item)
    }
}

// ── Scene Image ──────────────────────────────────────────────────

@Composable
private fun SceneImageCard(item: FeedItem.SceneImage) {
    val bitmap: ImageBitmap? = remember(item.imageBytes) {
        decodeImageBytes(item.imageBytes)
    }
    if (bitmap != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = "Scene",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 300.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(2.dp, AppColors.parchmentEdge, RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// ── Combat Encounter Card ────────────────────────────────────────

@Composable
private fun CombatEncounterCard(item: FeedItem.CombatStart) {
    val combat = item.combat
    val portraitRes = combat.portraitResource?.let { monsterPortraits[it] }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A0E0A),
                            Color(0xFF2A1810),
                            Color(0xFF1A0E0A)
                        )
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        listOf(AppColors.bronze, AppColors.gold, AppColors.bronzeLight, AppColors.bronze)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            // Enemy portrait
            if (portraitRes != null) {
                Image(
                    painter = org.jetbrains.compose.resources.painterResource(portraitRes),
                    contentDescription = combat.enemyName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 280.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(
                                listOf(AppColors.bronzeDark, AppColors.gold, AppColors.bronzeDark)
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(10.dp))
            } else if (combat.portraitBytes != null) {
                val bitmap = remember(combat.portraitBytes) {
                    decodeImageBytes(combat.portraitBytes)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = combat.enemyName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 280.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(2.dp, AppColors.bronze, RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            // Enemy name + danger
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = combat.enemyName,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = AppColors.gold,
                        fontWeight = FontWeight.Bold
                    )
                )
                // Danger indicator
                val dangerColor = when {
                    combat.danger >= 8 -> Color(0xFFFF4444)
                    combat.danger >= 5 -> Color(0xFFFF8C00)
                    combat.danger >= 3 -> Color(0xFFFFCC00)
                    else -> Color(0xFF88CC44)
                }
                Text(
                    text = "${"★".repeat(combat.danger.coerceAtMost(5))}",
                    color = dangerColor,
                    fontSize = 16.sp
                )
            }

            // Loot tier badge
            if (combat.lootTier != "normal") {
                val tierColor = when (combat.lootTier) {
                    "boss" -> AppColors.rarityLegendary
                    "elite" -> AppColors.rarityEpic
                    else -> AppColors.rarityUncommon
                }
                Text(
                    text = combat.lootTier.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = tierColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // HP bar
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("HP", color = AppColors.hpRed, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF3A1515))
                ) {
                    val hpFraction = if (combat.enemyMaxHP > 0)
                        combat.enemyHP.toFloat() / combat.enemyMaxHP else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(hpFraction)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(AppColors.hpRedDark, AppColors.hpRed)
                                ),
                                shape = RoundedCornerShape(5.dp)
                            )
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${combat.enemyHP}/${combat.enemyMaxHP}",
                    color = Color(0xFFCCAAAA),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // Description
            if (combat.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = combat.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFFAA9988),
                        fontStyle = FontStyle.Italic,
                        lineHeight = 18.sp
                    )
                )
            }

            // Type resistances/vulnerabilities/immunities
            if (combat.immunities.isNotEmpty() || combat.vulnerabilities.isNotEmpty() || combat.resistances.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F0A08), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (combat.immunities.isNotEmpty()) {
                            DamageTypeRow(
                                label = "IMMUNE",
                                types = combat.immunities,
                                color = Color(0xFF888888)
                            )
                        }
                        if (combat.vulnerabilities.isNotEmpty()) {
                            DamageTypeRow(
                                label = "WEAK",
                                types = combat.vulnerabilities,
                                color = Color(0xFF44CC44)
                            )
                        }
                        if (combat.resistances.isNotEmpty()) {
                            DamageTypeRow(
                                label = "RESIST",
                                types = combat.resistances,
                                color = Color(0xFFCC8844)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DamageTypeRow(label: String, types: List<String>, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = color,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            modifier = Modifier.width(52.dp)
        )
        types.forEach { type ->
            val chipColor = damageTypeColor(type)
            Text(
                text = type.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall.copy(
                    color = chipColor,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier
                    .background(chipColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

private fun damageTypeColor(type: String): Color = when (type.uppercase()) {
    "FIRE" -> Color(0xFFFF6633)
    "ICE" -> Color(0xFF66CCFF)
    "LIGHTNING" -> Color(0xFFFFEE44)
    "POISON" -> Color(0xFF66FF66)
    "DARK" -> Color(0xFFCC66FF)
    "HOLY" -> Color(0xFFFFFFCC)
    "MAGICAL" -> Color(0xFF9999FF)
    "TRUE" -> Color(0xFFFF4444)
    "PHYSICAL" -> Color(0xFFCCBBAA)
    else -> Color(0xFFAAAAAA)
}

// ── Combat End Card ─────────────────────────────────────────────

@Composable
private fun CombatEndCard(item: FeedItem.CombatEnd) {
    val (bgColor, textColor, text) = if (item.victory) {
        Triple(
            Color(0xFF1A2A10),
            AppColors.gold,
            "${item.enemyName} defeated!"
        )
    } else {
        Triple(
            Color(0xFF2A1010),
            AppColors.hpRed,
            "Fled from ${item.enemyName}"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall.copy(
                color = textColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor, RoundedCornerShape(6.dp))
                .border(1.dp, textColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(vertical = 12.dp, horizontal = 16.dp)
        )
    }
}

// ── Combat Overlay (top of screen during combat) ────────────────

@Composable
internal fun CombatOverlay(combat: CombatUi, modifier: Modifier = Modifier) {
    val portraitRes = combat.portraitResource?.let { monsterPortraits[it] }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xDD1A0E0A), Color(0x001A0E0A))
                )
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Small portrait
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(
                    2.dp,
                    Brush.sweepGradient(listOf(AppColors.bronze, AppColors.gold, AppColors.bronze)),
                    CircleShape
                )
                .background(Color(0xFF2A1810), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (portraitRes != null) {
                Image(
                    painter = org.jetbrains.compose.resources.painterResource(portraitRes),
                    contentDescription = combat.enemyName,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else if (combat.portraitBytes != null) {
                val bitmap = remember(combat.portraitBytes) { decodeImageBytes(combat.portraitBytes) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = combat.enemyName,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Text(
                    text = combat.enemyName.take(1),
                    color = AppColors.gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Name + condition
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = combat.enemyName,
                    color = AppColors.gold,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = combat.enemyCondition,
                    color = when (combat.enemyCondition) {
                        "healthy" -> Color(0xFF88CC44)
                        "wounded" -> Color(0xFFFFCC00)
                        "badly wounded" -> Color(0xFFFF8C00)
                        "near death" -> Color(0xFFFF4444)
                        "defeated" -> Color(0xFF888888)
                        else -> Color(0xFFAAAAAA)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic
                )
            }

            Spacer(Modifier.height(4.dp))

            // HP bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF3A1515))
            ) {
                val hpFraction = if (combat.enemyMaxHP > 0)
                    combat.enemyHP.toFloat() / combat.enemyMaxHP else 0f
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(hpFraction)
                        .background(
                            Brush.horizontalGradient(listOf(AppColors.hpRedDark, AppColors.hpRed)),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }

            // Round number
            Text(
                text = "Round ${combat.roundNumber}",
                color = Color(0xFF887766),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// ── Narration ────────────────────────────────────────────────────

@Composable
private fun NarrationBlock(item: FeedItem.Narration) {
    Text(
        text = item.text,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = AppColors.inkDark,
            lineHeight = 26.sp,
            fontSize = 17.sp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

// ── Player Bubble ────────────────────────────────────────────────

@Composable
private fun PlayerBubble(item: FeedItem.PlayerMessage) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(AppColors.leather, AppColors.leatherLight)
                    ),
                    RoundedCornerShape(12.dp, 12.dp, 2.dp, 12.dp)
                )
                .border(1.dp, AppColors.leatherDark.copy(alpha = 0.4f), RoundedCornerShape(12.dp, 12.dp, 2.dp, 12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AppColors.parchmentLight,
                    fontSize = 14.sp
                )
            )
        }
    }
}

// ── NPC Dialogue ─────────────────────────────────────────────────

@Composable
private fun NpcDialogueBubble(item: FeedItem.NpcDialogue) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // NPC portrait
        NpcPortraitImage(
            name = item.npcName,
            size = 44.dp
        )

        Column(modifier = Modifier.weight(1f)) {
            // NPC name plate (banner style)
            Box(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            listOf(AppColors.bronze, AppColors.bronzeLight, AppColors.bronze)
                        ),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.npcName,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = AppColors.parchmentLight,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                    if (item.role.isNotEmpty()) {
                        Text(
                            text = ", ${item.role}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = AppColors.parchmentLight.copy(alpha = 0.8f),
                                fontStyle = FontStyle.Italic,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Dialogue text
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = AppColors.inkDark,
                    lineHeight = 24.sp
                ),
                modifier = Modifier.padding(end = 12.dp)
            )
        }
    }
}

// ── XP Notification ──────────────────────────────────────────────

@Composable
private fun XpNotification(item: FeedItem.XpGain) {
    BracketNotification {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StarIcon(size = 14.dp, color = AppColors.xpGreen)
            Text(
                text = "+${item.amount} XP",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = AppColors.inkDark,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

// ── Item Notification ────────────────────────────────────────────

@Composable
private fun ItemNotification(item: FeedItem.ItemGained) {
    BracketNotification {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChestIcon(size = 14.dp, color = AppColors.bronze)
            Text(
                text = "Item Found: ",
                style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.inkFaded)
            )
            Text(
                text = "${item.itemName}${if (item.quantity > 1) " x${item.quantity}" else ""}",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = AppColors.inkDark,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

// ── Gold Notification ────────────────────────────────────────────

@Composable
private fun GoldNotification(item: FeedItem.GoldGained) {
    BracketNotification {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CoinIcon(size = 14.dp, color = AppColors.gold)
            Text(
                text = "Item Found: ",
                style = MaterialTheme.typography.bodyMedium.copy(color = AppColors.inkFaded)
            )
            Text(
                text = "${item.amount} Gold",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = AppColors.gold,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

// ── Quest Notification ───────────────────────────────────────────

@Composable
private fun QuestNotification(item: FeedItem.QuestUpdate) {
    BracketNotification {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ScrollIcon(size = 14.dp, color = AppColors.gold)
            Text(
                text = "${item.status}: ",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = AppColors.inkDark,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = item.questName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AppColors.inkDark,
                    fontStyle = FontStyle.Italic
                )
            )
        }
    }
}

// ── Level Up ─────────────────────────────────────────────────────

@Composable
private fun LevelUpCard(item: FeedItem.LevelUp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            AppColors.gold.copy(alpha = 0.1f),
                            AppColors.gold.copy(alpha = 0.2f),
                            AppColors.gold.copy(alpha = 0.1f)
                        )
                    ),
                    RoundedCornerShape(8.dp)
                )
                .border(1.dp, AppColors.gold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "LEVEL UP",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AppColors.gold,
                        letterSpacing = 3.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Level ${item.newLevel}",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = AppColors.gold
                    )
                )
            }
        }
    }
}

// ── System Notice ────────────────────────────────────────────────

@Composable
private fun SystemNoticeBlock(item: FeedItem.SystemNotice) {
    Text(
        text = item.text,
        style = MaterialTheme.typography.bodySmall.copy(
            color = AppColors.inkFaded,
            fontStyle = FontStyle.Italic
        ),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp)
    )
}

// ── Companion Aside ──────────────────────────────────────────────

@Composable
private fun CompanionAsideBubble(item: FeedItem.CompanionAside) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Companion portrait
        NpcPortraitImage(
            name = item.companionName,
            size = 36.dp
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.companionName,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = AppColors.bronzeLight,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(
                        AppColors.parchmentDark.copy(alpha = 0.5f),
                        RoundedCornerShape(2.dp, 12.dp, 12.dp, 12.dp)
                    )
                    .border(1.dp, AppColors.parchmentEdge.copy(alpha = 0.5f), RoundedCornerShape(2.dp, 12.dp, 12.dp, 12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AppColors.inkDark,
                        fontStyle = FontStyle.Italic
                    )
                )
            }
        }
    }
}

// ── Bracket Notification (ornamental) ────────────────────────────

@Composable
private fun BracketNotification(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .drawBehind {
                val color = AppColors.parchmentEdge
                val bracketW = 8f
                val strokeW = 1.5f
                val h = size.height
                val topY = 0f
                val botY = h
                val midY = h / 2f

                // Left bracket
                drawLine(color, Offset(bracketW, topY), Offset(0f, topY), strokeWidth = strokeW)
                drawLine(color, Offset(0f, topY), Offset(0f, botY), strokeWidth = strokeW)
                drawLine(color, Offset(0f, botY), Offset(bracketW, botY), strokeWidth = strokeW)

                // Right bracket
                val rX = size.width
                drawLine(color, Offset(rX - bracketW, topY), Offset(rX, topY), strokeWidth = strokeW)
                drawLine(color, Offset(rX, topY), Offset(rX, botY), strokeWidth = strokeW)
                drawLine(color, Offset(rX, botY), Offset(rX - bracketW, botY), strokeWidth = strokeW)
            }
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        content()
    }
}

// ── Reconnecting Overlay ─────────────────────────────────────────

@Composable
private fun ReconnectingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(AppColors.overlay),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = AppColors.gold)
            Text("Reconnecting...", style = MaterialTheme.typography.bodyLarge.copy(color = AppColors.parchmentLight))
        }
    }
}

// ── Menu Sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuSheet(
    onDismiss: () -> Unit,
    onOpenHud: (HudTab) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.parchment,
        contentColor = AppColors.inkDark,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            MenuItem(
                icon = Icons.Default.Person,
                label = "Character",
                onClick = { onOpenHud(HudTab.CHARACTER) }
            )
            MenuItem(
                icon = Icons.Default.Inventory2,
                label = "Inventory",
                onClick = { onOpenHud(HudTab.INVENTORY) }
            )
            MenuItem(
                icon = Icons.Default.Assignment,
                label = "Quests",
                onClick = { onOpenHud(HudTab.QUESTS) }
            )
        }
    }
}

@Composable
private fun MenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = AppColors.bronze,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = AppColors.inkDark,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
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
    onMenuPressed: () -> Unit,
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
                    listOf(
                        AppColors.parchmentLight.copy(alpha = 0f),
                        AppColors.parchment,
                        AppColors.parchmentDark
                    )
                )
            )
            .padding(bottom = 16.dp)
    ) {
        // Thin ornamental line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(1.dp)
                .background(AppColors.parchmentEdge.copy(alpha = 0.5f))
        )

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
                    placeholder = { Text("Aa...", color = AppColors.inkMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onTextInputSubmit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.inkDark,
                        unfocusedTextColor = AppColors.inkMedium,
                        focusedBorderColor = AppColors.parchmentEdge,
                        unfocusedBorderColor = AppColors.parchmentEdge.copy(alpha = 0.5f),
                        cursorColor = AppColors.bronze,
                        focusedContainerColor = AppColors.parchmentLight,
                        unfocusedContainerColor = AppColors.parchmentLight
                    ),
                    shape = RoundedCornerShape(6.dp)
                )
            }
        }

        // Button row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text toggle
            TextButton(onClick = onToggleTextInput) {
                Text(
                    text = "Aa",
                    color = AppColors.inkFaded,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Session button — shows listening state, tap to stop
            val ringColor = when {
                isGeminiSpeaking -> AppColors.gold
                isListening -> AppColors.hpRed
                else -> AppColors.bronze
            }
            val fillColor = when {
                isGeminiSpeaking -> AppColors.leather
                isListening -> AppColors.hpRed.copy(alpha = 0.9f)
                else -> AppColors.leatherDark
            }

            Box(contentAlignment = Alignment.Center) {
                // Outer decorative ring
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .drawBehind {
                            drawCircle(
                                color = ringColor.copy(alpha = 0.3f),
                                radius = size.minDimension / 2
                            )
                            drawCircle(
                                color = ringColor,
                                radius = size.minDimension / 2,
                                style = Stroke(width = 3f)
                            )
                            drawCircle(
                                color = ringColor,
                                radius = size.minDimension / 2 - 4f,
                                style = Stroke(width = 1f)
                            )
                        }
                )

                Button(
                    onClick = onMicPressed,
                    modifier = Modifier.size(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = fillColor),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    if (isListening) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "End Session",
                            tint = AppColors.parchmentLight,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        MicrophoneIcon(
                            size = 24.dp,
                            color = AppColors.parchmentLight
                        )
                    }
                }
            }

            // Menu button
            IconButton(onClick = onMenuPressed) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = AppColors.inkFaded
                )
            }
        }
    }
}
