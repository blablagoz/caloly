package com.caloly.app.presentation.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.caloly.app.domain.auth.AuthState
import com.caloly.app.presentation.addfood.AddFoodScreen
import com.caloly.app.presentation.auth.AccountScreen
import com.caloly.app.presentation.auth.AuthViewModel
import com.caloly.app.presentation.auth.ChangePasswordScreen
import com.caloly.app.presentation.auth.ForgotPasswordScreen
import com.caloly.app.presentation.auth.LoginScreen
import com.caloly.app.presentation.auth.OnboardingScreen
import com.caloly.app.presentation.auth.OtpScreen
import com.caloly.app.presentation.auth.RegisterScreen
import com.caloly.app.presentation.navigation.Routes
import com.caloly.app.presentation.social.SharingSettingsScreen
import com.caloly.app.presentation.social.SocialScreen
import com.caloly.app.presentation.theme.CalolyGreen

@Composable
fun CalolyApp() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val actionState by authViewModel.action.collectAsStateWithLifecycle()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    LaunchedEffect(authState, currentRoute) {
        when (val state = authState) {
            AuthState.Loading -> Unit
            AuthState.SignedOut -> {
                val publicRoute = currentRoute == Routes.LOGIN || currentRoute == Routes.REGISTER ||
                    currentRoute == Routes.FORGOT_PASSWORD || currentRoute?.startsWith(Routes.OTP) == true
                if (!publicRoute) navController.navigate(Routes.LOGIN) { popUpTo(0) }
            }
            is AuthState.SignedIn -> {
                val destination = if (state.user.onboardingCompleted) Routes.HOME else Routes.ONBOARDING
                val authRoute = currentRoute == Routes.AUTH_GATE || currentRoute == Routes.LOGIN ||
                    currentRoute == Routes.REGISTER || currentRoute == Routes.FORGOT_PASSWORD ||
                    currentRoute?.startsWith(Routes.OTP) == true
                if (authRoute || (destination == Routes.ONBOARDING && currentRoute != Routes.ONBOARDING)) {
                    navController.navigate(destination) { popUpTo(0) }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.AUTH_GATE) {
        composable(Routes.AUTH_GATE) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = CalolyGreen)
            }
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                actionState,
                onPasswordLogin = authViewModel::signIn,
                onOtp = { email ->
                    authViewModel.sendOtp(email, createUser = false)
                    if (email.contains('@')) {
                        navController.navigate(Routes.OTP + "?email=" + java.net.URLEncoder.encode(email, "UTF-8") + "&signup=0")
                    }
                },
                onGoogle = authViewModel::googleSignIn,
                onRegister = { navController.navigate(Routes.REGISTER) },
                onForgot = { navController.navigate(Routes.FORGOT_PASSWORD) },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                actionState,
                onRegister = { email, password, name, username ->
                    authViewModel.signUp(email, password, name, username) {
                        navController.navigate(Routes.OTP + "?email=" + java.net.URLEncoder.encode(email, "UTF-8") + "&signup=1")
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Routes.OTP + "?email={email}&signup={signup}",
            arguments = listOf(
                androidx.navigation.navArgument("email") { defaultValue = "" },
                androidx.navigation.navArgument("signup") { defaultValue = "0" },
            ),
        ) { backStack ->
            val email = java.net.URLDecoder.decode(backStack.arguments?.getString("email").orEmpty(), "UTF-8")
            val isSignup = backStack.arguments?.getString("signup") == "1"
            OtpScreen(
                email,
                actionState,
                onVerify = { token ->
                    authViewModel.verifyOtp(email, token, isSignup) {
                        navController.navigate(Routes.HOME) { popUpTo(0) }
                    }
                },
                onResend = { if (!isSignup) authViewModel.sendOtp(email, createUser = false) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(actionState, authViewModel::forgotPassword) { navController.popBackStack() }
        }
        composable(Routes.CHANGE_PASSWORD) {
            ChangePasswordScreen(
                actionState,
                onChange = { authViewModel.changePassword(it) { navController.popBackStack() } },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ONBOARDING) {
            val user = (authState as? AuthState.SignedIn)?.user
            if (user != null) {
                OnboardingScreen(
                    user,
                    actionState,
                    onSave = { birthDate, heightCm, weightKg, gender ->
                        authViewModel.updateHealthProfile(birthDate, heightCm, weightKg, gender) {
                            navController.navigate(Routes.HOME) { popUpTo(0) }
                        }
                    },
                    onSkip = {
                        authViewModel.skipHealthProfile {
                            navController.navigate(Routes.HOME) { popUpTo(0) }
                        }
                    },
                )
            }
        }
        composable(Routes.ACCOUNT) {
            val user = (authState as? AuthState.SignedIn)?.user
            if (user != null) {
                AccountScreen(
                    user,
                    actionState,
                    onSave = { name, username -> authViewModel.updateProfile(name, username) {} },
                    onAvatar = authViewModel::uploadAvatar,
                    onChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) },
                    onSignOut = {
                        authViewModel.signOut()
                        navController.navigate(Routes.LOGIN) { popUpTo(0) }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            val summary by viewModel.summary.collectAsStateWithLifecycle()
            val healthState by viewModel.healthState.collectAsStateWithLifecycle()
            val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
            val loggedDates by viewModel.loggedDates.collectAsStateWithLifecycle()
            val templates by viewModel.templates.collectAsStateWithLifecycle()
            val templateAction by viewModel.templateAction.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current
            val permissionLauncher = rememberLauncherForActivityResult(
                PermissionController.createRequestPermissionResultContract(),
            ) { viewModel.onHealthPermissionsResult() }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshHealth()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            MainShell(
                summary = summary,
                selectedDate = selectedDate,
                loggedDates = loggedDates,
                templates = templates,
                templateAction = templateAction,
                healthState = healthState,
                user = (authState as? AuthState.SignedIn)?.user,
                authAction = actionState,
                onPreviousDate = viewModel::previousDay,
                onNextDate = viewModel::nextDay,
                onSelectDate = viewModel::selectDate,
                onAddFood = { navController.navigate(Routes.addFood(selectedDate.toString())) },
                onSaveMeal = viewModel::saveMealTemplate,
                onSaveDay = viewModel::saveDayTemplate,
                onApplyTemplate = viewModel::applyTemplate,
                onDeleteTemplate = viewModel::deleteTemplate,
                onConnectHealth = { permissionLauncher.launch(viewModel.requiredHealthPermissions) },
                onRefreshHealth = viewModel::refreshHealth,
                onEditAccount = { navController.navigate(Routes.ACCOUNT) },
                onEditBody = { navController.navigate(Routes.ONBOARDING) },
                onSecurity = { navController.navigate(Routes.CHANGE_PASSWORD) },
                onSharingSettings = { navController.navigate(Routes.SHARING_SETTINGS) },
                onSignOut = {
                    authViewModel.signOut()
                    navController.navigate(Routes.LOGIN) { popUpTo(0) }
                },
            )
        }
        composable(
            Routes.ADD_FOOD + "/{date}",
            arguments = listOf(
                androidx.navigation.navArgument("date") { defaultValue = java.time.LocalDate.now().toString() },
            ),
        ) { entry ->
            AddFoodScreen(
                dateKey = entry.arguments?.getString("date").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SOCIAL) { SocialScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SHARING_SETTINGS) { SharingSettingsScreen(onBack = { navController.popBackStack() }) }
    }
}
