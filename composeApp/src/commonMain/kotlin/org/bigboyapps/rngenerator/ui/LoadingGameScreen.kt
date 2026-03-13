package org.bigboyapps.rngenerator.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.rpgenerator.composeapp.generated.resources.Res
import com.rpgenerator.composeapp.generated.resources.cinzel_decorative_bold
import com.rpgenerator.composeapp.generated.resources.lobby_background
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

/**
 * Preview-friendly loading screen content (no ViewModel dependency).
 */
@Composable
internal fun LoadingGameContent(
    seedId: String? = "integration",
    loadingStatus: String? = "Creating game session...",
    errorMessage: String? = null
) {
    val titleFont = FontFamily(Font(Res.font.cinzel_decorative_bold, FontWeight.Bold))

    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val flavorTexts = remember {
        listOf(
            "Calibrating reality parameters...",
            "Summoning your companion...",
            "Generating terrain data...",
            "Aligning narrative threads...",
            "Preparing your adventure..."
        )
    }
    var flavorIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3000)
            flavorIndex = (flavorIndex + 1) % flavorTexts.size
        }
    }

    val loadingTitle = remember(seedId) {
        when (seedId) {
            "integration" -> "Entering the System"
            "tabletop" -> "Entering the Realm"
            "crawler" -> "Entering the Dungeon"
            "quiet_life" -> "Entering the Valley"
            else -> "Entering the World"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(Res.drawable.lobby_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.7f),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = loadingTitle,
                style = TextStyle(
                    fontFamily = titleFont,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.parchmentLight
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = AppColors.bronzeLight,
                strokeWidth = 3.dp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = flavorTexts[flavorIndex],
                style = TextStyle(
                    fontSize = 16.sp,
                    color = AppColors.parchmentLight.copy(alpha = pulseAlpha),
                    letterSpacing = 1.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            loadingStatus?.let { status ->
                Text(
                    text = status,
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = AppColors.parchmentLight.copy(alpha = 0.6f)
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                containerColor = AppColors.error
            ) {
                Text(error, color = AppColors.buttonText)
            }
        }
    }
}

@Composable
fun LoadingGameScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Trigger loading when we land on this screen
    LaunchedEffect(Unit) {
        viewModel.performLoadingSequence()
    }

    val titleFont = FontFamily(Font(Res.font.cinzel_decorative_bold, FontWeight.Bold))

    // Pulsing animation for the subtitle
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Rotating flavor texts
    val flavorTexts = remember {
        listOf(
            "Calibrating reality parameters...",
            "Summoning your companion...",
            "Generating terrain data...",
            "Aligning narrative threads...",
            "Preparing your adventure..."
        )
    }
    var flavorIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3000)
            flavorIndex = (flavorIndex + 1) % flavorTexts.size
        }
    }

    // Seed-themed title text
    val loadingTitle = remember(uiState.loadingSeedId) {
        when (uiState.loadingSeedId) {
            "integration" -> "Entering the System"
            "tabletop" -> "Entering the Realm"
            "crawler" -> "Entering the Dungeon"
            "quiet_life" -> "Entering the Valley"
            else -> "Entering the World"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image (same as lobby)
        Image(
            painter = painterResource(Res.drawable.lobby_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.7f),
            contentScale = ContentScale.Crop
        )

        // Dark overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Main title
            Text(
                text = loadingTitle,
                style = TextStyle(
                    fontFamily = titleFont,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.parchmentLight
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Progress indicator
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = AppColors.bronzeLight,
                strokeWidth = 3.dp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Flavor text (rotating)
            Text(
                text = flavorTexts[flavorIndex],
                style = TextStyle(
                    fontSize = 16.sp,
                    color = AppColors.parchmentLight.copy(alpha = pulseAlpha),
                    letterSpacing = 1.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Loading status
            uiState.loadingStatus?.let { status ->
                Text(
                    text = status,
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = AppColors.parchmentLight.copy(alpha = 0.6f)
                    ),
                    textAlign = TextAlign.Center
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
