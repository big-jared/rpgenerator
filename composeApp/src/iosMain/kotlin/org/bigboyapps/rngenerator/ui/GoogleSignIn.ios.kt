package org.bigboyapps.rngenerator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rpgenerator.composeapp.generated.resources.Res
import com.rpgenerator.composeapp.generated.resources.ic_google
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun GoogleSignInButton(viewModel: GameViewModel) {
    OutlinedIconButton(
        onClick = { viewModel.onSkipSignIn() },
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
