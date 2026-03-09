package org.bigboyapps.rngenerator.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.rpgenerator.composeapp.generated.resources.title_background
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

@Composable
fun SignInScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val titleFont = FontFamily(Font(Res.font.cinzel_decorative_bold, FontWeight.Bold))

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-bleed title art
        Image(
            painter = painterResource(Res.drawable.title_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Title at top
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp)
        ) {
            Text(
                text = "RPGenerator",
                style = TextStyle(
                    fontFamily = titleFont,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.primary
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter the System",
                style = TextStyle(
                    fontFamily = titleFont,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textSecondary,
                    letterSpacing = 3.sp
                ),
                textAlign = TextAlign.Center
            )
        }

        // Google sign-in icon pinned to bottom center
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        ) {
            GoogleSignInButton(viewModel = viewModel)

            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall.copy(color = AppColors.error)
                )
            }
        }
    }
}

/**
 * Platform-specific Google Sign-In button.
 * On Android: uses Credential Manager. On iOS: no-op button that skips.
 */
@Composable
expect fun GoogleSignInButton(viewModel: GameViewModel)
