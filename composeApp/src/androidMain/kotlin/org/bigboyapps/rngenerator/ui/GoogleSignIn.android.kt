package org.bigboyapps.rngenerator.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.rpgenerator.composeapp.generated.resources.Res
import com.rpgenerator.composeapp.generated.resources.ic_google
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private const val TAG = "GoogleSignIn"

// Set this to your Google OAuth Web Client ID
private const val GOOGLE_WEB_CLIENT_ID = "931553644155-ot19ogbgohlvh4r4pgbn6h8nnl6bm9sk.apps.googleusercontent.com"

@Composable
actual fun GoogleSignInButton(viewModel: GameViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    OutlinedIconButton(
        onClick = {
            isLoading = true
            scope.launch {
                try {
                    signInWithGoogle(context, viewModel)
                } catch (e: GetCredentialCancellationException) {
                    Log.d(TAG, "Sign-in cancelled by user")
                } catch (e: Exception) {
                    Log.e(TAG, "Sign-in failed", e)
                    viewModel.onSignInError("Sign-in failed: ${e.message}")
                } finally {
                    isLoading = false
                }
            }
        },
        modifier = Modifier.size(64.dp),
        enabled = !isLoading,
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

private suspend fun signInWithGoogle(context: Context, viewModel: GameViewModel) {
    Log.d(TAG, "Starting Google sign-in with client ID: ${GOOGLE_WEB_CLIENT_ID.take(20)}...")
    val credentialManager = CredentialManager.create(context)

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(GOOGLE_WEB_CLIENT_ID)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    Log.d(TAG, "Requesting credential...")
    val result = credentialManager.getCredential(
        request = request,
        context = context
    )

    val credential = result.credential
    Log.d(TAG, "Got credential type: ${credential.type}")

    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        Log.d(TAG, "Sign-in successful: ${googleCredential.displayName} (${googleCredential.id})")
        viewModel.onSignInComplete(
            name = googleCredential.displayName ?: "Adventurer",
            email = googleCredential.id,
            idToken = googleCredential.idToken
        )
    } else {
        Log.e(TAG, "Unexpected credential type: ${credential.type}")
        viewModel.onSignInError("Unexpected credential type: ${credential.type}")
    }
}
