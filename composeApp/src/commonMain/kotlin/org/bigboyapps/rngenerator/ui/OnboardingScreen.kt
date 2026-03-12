package org.bigboyapps.rngenerator.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@Composable
fun OnboardingScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val hasTranscript = uiState.onboardingTranscript.isNotBlank()

    // Auto-scroll when transcript updates
    LaunchedEffect(uiState.onboardingTranscript) {
        scrollState.animateScrollTo(scrollState.maxValue)
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
                .padding(bottom = if (hasTranscript) 120.dp else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Back button
            Box(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp)
            ) {
                IconButton(onClick = { viewModel.stopOnboarding() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = AppColors.textPrimary
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

            // Transcript area
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(12.dp),
                color = AppColors.surface
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!hasTranscript) {
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
                        Text(
                            text = uiState.onboardingTranscript,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = AppColors.textPrimary,
                                lineHeight = 26.sp
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(16.dp)
                        )
                    }
                }
            }
        }

        // Bottom stop button — only show once there's a transcript (session is active)
        if (hasTranscript) {
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
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { viewModel.stopOnboarding() },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(2.dp, AppColors.hpRed, CircleShape),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.hpRed.copy(alpha = 0.9f)),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "End Session",
                        tint = AppColors.parchmentLight,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "End Session",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AppColors.textMuted
                    )
                )
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
