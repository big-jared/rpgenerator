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
import org.bigboyapps.rngenerator.ui.*

private const val TRANSITION_DURATION = 300

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
    @Serializable data object Game : Route
}

@Composable
fun App(connectionFactory: (() -> GameConnection)? = null) {
    MaterialTheme(
        colorScheme = AdventureColorScheme,
        typography = appTypography()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val navController = rememberNavController()
            val viewModel = remember {
                if (connectionFactory != null) GameViewModel(connectionFactory)
                else GameViewModel()
            }

            // Listen for screen changes from ViewModel and navigate
            val uiState by viewModel.uiState.collectAsState()
            LaunchedEffect(uiState.screen) {
                val targetRoute: Route = when (uiState.screen) {
                    Screen.SIGN_IN -> Route.SignIn
                    Screen.GAME -> Route.Game
                }
                val currentRoute = navController.currentDestination?.route
                val targetRouteClass = targetRoute::class.qualifiedName
                if (currentRoute != targetRouteClass) {
                    navController.navigate(targetRoute) {
                        popUpTo(0) { inclusive = true }
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
                composable<Route.Game> {
                    GameScreen(viewModel)
                }
            }
        }
    }
}
