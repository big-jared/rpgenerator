package org.bigboyapps.rngenerator.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rpgenerator.composeapp.generated.resources.Res
import com.rpgenerator.composeapp.generated.resources.cinzel_decorative_bold
import com.rpgenerator.composeapp.generated.resources.lobby_background
import org.bigboyapps.rngenerator.network.SavedGameInfo
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

// ── Screen ──────────────────────────────────────────────────────

@Composable
fun LobbyScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    LobbyContent(
        errorMessage = uiState.errorMessage,
        onStartNewGame = { viewModel.startNewGame() },
        onLoadGame = { viewModel.showLoadGameDialog() },
        onOptions = { /* TODO */ },
        onSignOut = { viewModel.signOut() },
        onDismissError = { viewModel.dismissError() }
    )

    // Load Game dialog
    if (uiState.showLoadGameDialog) {
        LoadGameDialog(
            saves = uiState.savedGames,
            isLoading = uiState.isLoadingSaves,
            onSelectSave = { viewModel.loadSavedGame(it.gameId) },
            onDismiss = { viewModel.dismissLoadGameDialog() }
        )
    }
}

@Composable
fun LobbyContent(
    errorMessage: String? = null,
    onStartNewGame: () -> Unit = {},
    onLoadGame: () -> Unit = {},
    onOptions: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onDismissError: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Full-size background image at ~95% opacity
        Image(
            painter = painterResource(Res.drawable.lobby_background),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.85f),
            contentScale = ContentScale.Crop
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.15f))

            // Title — Cinzel Decorative (matches sign-in screen)
            val titleFont = FontFamily(Font(Res.font.cinzel_decorative_bold, FontWeight.Bold))
            Text(
                text = "RPGenerator",
                style = TextStyle(
                    fontFamily = titleFont,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.parchmentLight
                ),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Enter the System",
                style = TextStyle(
                    fontFamily = titleFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.parchmentLight.copy(alpha = 0.7f),
                    letterSpacing = 3.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(0.1f))

            // Card wrapping all menu buttons
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.9f),
                shadowElevation = 4.dp,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    LobbyMenuItem(label = "Create Game", onClick = onStartNewGame)
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = AppColors.parchmentDark.copy(alpha = 0.4f)
                    )
                    LobbyMenuItem(label = "Load Game", onClick = onLoadGame)
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = AppColors.parchmentDark.copy(alpha = 0.4f)
                    )
                    LobbyMenuItem(label = "Options", onClick = onOptions)
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = AppColors.parchmentDark.copy(alpha = 0.4f)
                    )
                    LobbyMenuItem(
                        label = "Sign Out",
                        onClick = onSignOut,
                        tint = AppColors.inkMuted
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.25f))
        }

        // Error snackbar
        errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = onDismissError) {
                        Text("Dismiss", color = AppColors.buttonText)
                    }
                },
                containerColor = AppColors.hpRed
            ) { Text(error, color = AppColors.parchmentLight) }
        }
    }
}

// ── Load Game Dialog ─────────────────────────────────────────────

@Composable
private fun LoadGameDialog(
    saves: List<SavedGameInfo>,
    isLoading: Boolean,
    onSelectSave: (SavedGameInfo) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = AppColors.surface,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Load Game",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = AppColors.primary,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AppColors.bronzeLight)
                    }
                } else if (saves.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No saved games found.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = AppColors.textMuted
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(saves) { save ->
                            SaveGameCard(save = save, onClick = { onSelectSave(save) })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = AppColors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun SaveGameCard(save: SavedGameInfo, onClick: () -> Unit) {
    val worldLabel = when (save.systemType) {
        "SYSTEM_INTEGRATION" -> "System Integration"
        "HIGH_FANTASY" -> "High Fantasy"
        "DUNGEON_CRAWLER" -> "Dungeon Crawler"
        "QUIET_LIFE" -> "Quiet Life"
        else -> save.systemType
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = AppColors.parchmentLight,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = save.playerName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = AppColors.inkDark
                    )
                )
                Text(
                    text = "$worldLabel  -  Level ${save.level}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AppColors.inkMuted
                    )
                )
            }
        }
    }
}

// ── Menu Item ───────────────────────────────────────────────────

@Composable
private fun LobbyMenuItem(
    label: String,
    onClick: () -> Unit,
    tint: Color = AppColors.inkDark
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                color = tint,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )
    }
}
