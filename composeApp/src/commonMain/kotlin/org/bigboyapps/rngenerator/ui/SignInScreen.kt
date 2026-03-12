package org.bigboyapps.rngenerator.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.mmk.kmpauth.firebase.google.GoogleButtonUiContainerFirebase
import dev.gitlive.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch
import com.rpgenerator.composeapp.generated.resources.Res
import com.rpgenerator.composeapp.generated.resources.cinzel_decorative_bold
import com.rpgenerator.composeapp.generated.resources.ic_google
import com.rpgenerator.composeapp.generated.resources.title_background
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

@Composable
fun SignInScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
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
                .statusBarsPadding()
                .padding(top = 48.dp)
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

        // Google sign-in button pinned to bottom center
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp)
        ) {
            GoogleButtonUiContainerFirebase(
                onResult = { result ->
                    val user: FirebaseUser? = result.getOrNull()
                    if (user != null) {
                        scope.launch {
                            try {
                                val token = user.getIdToken(false) ?: ""
                                viewModel.onSignInComplete(
                                    name = user.displayName ?: "",
                                    email = user.email ?: "",
                                    idToken = token
                                )
                            } catch (e: Exception) {
                                viewModel.onSignInComplete(
                                    name = user.displayName ?: "",
                                    email = user.email ?: "",
                                    idToken = ""
                                )
                            }
                        }
                    }
                }
            ) {
                OutlinedIconButton(
                    onClick = { this.onClick() },
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, AppColors.divider),
                    colors = IconButtonDefaults.outlinedIconButtonColors(
                        containerColor = Color.White
                    )
                ) {
                    Image(
                        painter = painterResource(Res.drawable.ic_google),
                        contentDescription = "Sign in with Google",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

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
