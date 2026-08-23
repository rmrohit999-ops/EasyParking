package com.parkease.feature.auth

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.parkease.feature.auth.ui.ForgotPasswordScreen
import com.parkease.feature.auth.ui.LoginScreen
import com.parkease.feature.auth.ui.OtpLoginScreen
import com.parkease.feature.auth.ui.RegisterScreen

object AuthRoutes {
    const val GRAPH = "auth"
    const val LOGIN = "auth/login"
    const val OTP_LOGIN = "auth/otp"
    const val REGISTER = "auth/register"
    const val FORGOT_PASSWORD = "auth/forgot-password"
}

/**
 * The app's public entry point into this module: the app-level RootNavHost
 * calls `navGraphBuilder.authGraph(navController, onAuthenticated = ...)`
 * without needing to know this module's internal route names or screens.
 */
fun NavGraphBuilder.authGraph(navController: NavController, onAuthenticated: () -> Unit) {
    navigation(startDestination = AuthRoutes.LOGIN, route = AuthRoutes.GRAPH) {
        composable(AuthRoutes.LOGIN) {
            LoginScreen(
                onLoggedIn = onAuthenticated,
                onNavigateToOtpLogin = { navController.navigate(AuthRoutes.OTP_LOGIN) },
                onNavigateToRegister = { navController.navigate(AuthRoutes.REGISTER) },
                onNavigateToForgotPassword = { navController.navigate(AuthRoutes.FORGOT_PASSWORD) },
            )
        }
        composable(AuthRoutes.OTP_LOGIN) {
            OtpLoginScreen(onLoggedIn = onAuthenticated, onBack = { navController.popBackStack() })
        }
        composable(AuthRoutes.REGISTER) {
            RegisterScreen(onRegistered = onAuthenticated, onBack = { navController.popBackStack() })
        }
        composable(AuthRoutes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(onBack = { navController.popBackStack() })
        }
    }
}
