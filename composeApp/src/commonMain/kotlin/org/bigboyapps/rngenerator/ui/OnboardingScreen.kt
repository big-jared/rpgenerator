package org.bigboyapps.rngenerator.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Preview-friendly onboarding content (no ViewModel dependency).
 */
@Composable
internal fun OnboardingContent(
    messages: List<OnboardingMessage> = emptyList(),
    isMusicEnabled: Boolean = true,
    errorMessage: String? = null,
    onBack: () -> Unit = {},
    onMusicToggle: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val hasMessages = messages.isNotEmpty()

    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(bottom = if (hasMessages) 140.dp else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AppColors.textPrimary
                    )
                }
                IconButton(onClick = onMusicToggle) {
                    Icon(
                        imageVector = if (isMusicEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (isMusicEnabled) "Mute Music" else "Unmute Music",
                        tint = if (isMusicEnabled) AppColors.bronze else AppColors.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            NpcPortraitImage(name = "Receptionist", size = 140.dp)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "The Receptionist",
                style = MaterialTheme.typography.headlineMedium.copy(color = AppColors.primary)
            )

            Text(
                text = "Front Desk of Reality",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AppColors.textMuted,
                    fontStyle = FontStyle.Italic
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(12.dp),
                color = AppColors.surface
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!hasMessages) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                                .alpha(pulseAlpha),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = AppColors.bronzeLight,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Connecting...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = AppColors.textMuted,
                                    fontStyle = FontStyle.Italic
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        OnboardingChatList(messages = messages, listState = listState)
                    }
                }
            }
        }

        if (hasMessages) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
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
                    .padding(bottom = 24.dp, top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        tint = AppColors.hpRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "End Session",
                        style = MaterialTheme.typography.bodySmall.copy(color = AppColors.hpRed)
                    )
                }
            }
        }

        errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = {}) {
                        Text("Dismiss", color = AppColors.buttonText)
                    }
                },
                containerColor = AppColors.error
            ) {
                Text(error, color = AppColors.buttonText)
            }
        }
    }
}

@Composable
private fun OnboardingChatList(
    messages: List<OnboardingMessage>,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(messages) { msg ->
            val isUser = msg.role == "user"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isUser) 12.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 12.dp
                    ),
                    color = if (isUser) AppColors.bronze.copy(alpha = 0.15f) else AppColors.background,
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = AppColors.textPrimary,
                            lineHeight = 22.sp
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val messages = uiState.onboardingMessages
    val hasMessages = messages.isNotEmpty()

    // Auto-scroll when messages update
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) {
            kotlinx.coroutines.delay(50)
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Pulsing alpha for "Connecting..." text
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(bottom = if (hasMessages) 140.dp else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar — back + music toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.stopOnboarding() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AppColors.textPrimary
                    )
                }
                IconButton(onClick = { viewModel.toggleMusic() }) {
                    Icon(
                        imageVector = if (uiState.isMusicEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (uiState.isMusicEnabled) "Mute Music" else "Unmute Music",
                        tint = if (uiState.isMusicEnabled) AppColors.bronze else AppColors.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Receptionist portrait
            NpcPortraitImage(
                name = "Receptionist",
                size = 140.dp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "The Receptionist",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = AppColors.primary
                )
            )

            Text(
                text = "Front Desk of Reality",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = AppColors.textMuted,
                    fontStyle = FontStyle.Italic
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Chat messages area
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(12.dp),
                color = AppColors.surface
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!hasMessages) {
                        // Connecting state — pulsing indicator
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                                .alpha(pulseAlpha),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = AppColors.bronzeLight,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Connecting...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = AppColors.textMuted,
                                    fontStyle = FontStyle.Italic
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        OnboardingChatList(messages = messages, listState = listState)
                    }
                }
            }
        }

        // Bottom bar — stop button
        if (hasMessages) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
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
                    .padding(bottom = 24.dp, top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // End session button
                TextButton(onClick = { viewModel.stopOnboarding() }) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        tint = AppColors.hpRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "End Session",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = AppColors.hpRed
                        )
                    )
                }
            }
        }

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
}
