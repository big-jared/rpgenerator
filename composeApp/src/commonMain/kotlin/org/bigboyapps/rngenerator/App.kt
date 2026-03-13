package org.bigboyapps.rngenerator

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import com.mmk.kmpauth.google.GoogleAuthCredentials
import com.mmk.kmpauth.google.GoogleAuthProvider
import org.bigboyapps.rngenerator.ui.*

private const val TRANSITION_DURATION = 300
private const val GOOGLE_WEB_CLIENT_ID = "931553644155-ot19ogbgohlvh4r4pgbn6h8nnl6bm9sk.apps.googleusercontent.com"

private val AdventureColorScheme = lightColorScheme(
    primary = AppColors.primary,
    secondary = AppColors.primaryLight,
    tertiary = AppColors.accent,
    background = AppColors.background,
    surface = AppColors.surface,
    onPrimary = AppColors.buttonText,
    onSecondary = AppColors.textPrimary,
    onTertiary = AppColors.textPrimary,
    onBackground = AppColors.textPrimary,
    onSurface = AppColors.textPrimary,
    error = AppColors.error,
    onError = AppColors.buttonText
)

// Type-safe route definitions
sealed interface Route {
    @Serializable data object SignIn : Route
    @Serializable data object Lobby : Route
    @Serializable data object Onboarding : Route
    @Serializable data object LoadingGame : Route
    @Serializable data object Game : Route
}

@Composable
fun App(connectionFactory: (() -> GameConnection)? = null) {
    // Initialize KMPAuth Google Sign-In (must complete before SignInScreen renders)
    remember {
        GoogleAuthProvider.create(GoogleAuthCredentials(serverId = GOOGLE_WEB_CLIENT_ID))
    }

    MaterialTheme(
        colorScheme = AdventureColorScheme,
        typography = appTypography()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val navController = rememberNavController()
            val viewModel = remember { GameViewModel() }

            // When the connection factory becomes available (service bound), update the ViewModel
            LaunchedEffect(connectionFactory) {
                if (connectionFactory != null) {
                    viewModel.updateConnectionFactory(connectionFactory)
                }
            }

            // Listen for screen changes from ViewModel and navigate
            val uiState by viewModel.uiState.collectAsState()
            LaunchedEffect(uiState.screen) {
                val targetRoute: Route = when (uiState.screen) {
                    Screen.SIGN_IN -> Route.SignIn
                    Screen.LOBBY -> Route.Lobby
                    Screen.ONBOARDING -> Route.Onboarding
                    Screen.LOADING_GAME -> Route.LoadingGame
                    Screen.GAME -> Route.Game
                }
                val currentRoute = navController.currentDestination?.route
                val targetRouteClass = targetRoute::class.qualifiedName
                if (currentRoute != targetRouteClass) {
                    when (uiState.screen) {
                        // These reset the stack (no back gesture desired)
                        Screen.SIGN_IN, Screen.LOBBY -> navController.navigate(targetRoute) {
                            popUpTo(0) { inclusive = true }
                        }
                        // These push onto the stack (back gesture pops to previous)
                        Screen.ONBOARDING, Screen.LOADING_GAME, Screen.GAME -> navController.navigate(targetRoute)
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = Route.SignIn,
                enterTransition = { slideInHorizontally(tween(TRANSITION_DURATION)) { it } + fadeIn(tween(TRANSITION_DURATION)) },
                exitTransition = { slideOutHorizontally(tween(TRANSITION_DURATION)) { -it } + fadeOut(tween(TRANSITION_DURATION)) },
                popEnterTransition = { slideInHorizontally(tween(TRANSITION_DURATION)) { -it } + fadeIn(tween(TRANSITION_DURATION)) },
                popExitTransition = { slideOutHorizontally(tween(TRANSITION_DURATION)) { it } + fadeOut(tween(TRANSITION_DURATION)) }
            ) {
                composable<Route.SignIn> {
                    SignInScreen(viewModel)
                }
                composable<Route.Lobby> {
                    LobbyScreen(viewModel)
                }
                composable<Route.Onboarding> {
                    // Sync ViewModel when user swipes back
                    DisposableEffect(Unit) {
                        onDispose { viewModel.stopOnboarding() }
                    }
                    OnboardingScreen(viewModel)
                }
                composable<Route.LoadingGame> {
                    LoadingGameScreen(viewModel)
                }
                composable<Route.Game> {
                    GameScreen(viewModel)
                }
            }
        }
    }
}
