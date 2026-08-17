package com.caloly.app.presentation.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.caloly.app.domain.model.DailySummary
import com.caloly.app.domain.auth.AuthState
import com.caloly.app.domain.model.HealthConnectAvailability
import com.caloly.app.presentation.addfood.AddFoodScreen
import com.caloly.app.presentation.auth.*
import com.caloly.app.presentation.navigation.Routes
import com.caloly.app.presentation.social.SocialScreen
import com.caloly.app.presentation.theme.CalolyGreen
import com.caloly.app.presentation.theme.CalolyLavender
import com.caloly.app.presentation.theme.CalolyLavenderLight
import com.caloly.app.presentation.theme.CalolyLavenderWhite
import com.caloly.app.presentation.theme.CalolyMuted
import kotlin.math.max

@Composable
fun CalolyApp() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val actionState by authViewModel.action.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.AUTH_GATE) {
        composable(Routes.AUTH_GATE) {
            when (val state = authState) {
                AuthState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator(color = CalolyGreen) }
                AuthState.SignedOut -> androidx.compose.runtime.LaunchedEffect(Unit) { navController.navigate(Routes.LOGIN) { popUpTo(Routes.AUTH_GATE) { inclusive = true } } }
                is AuthState.SignedIn -> androidx.compose.runtime.LaunchedEffect(Unit) { navController.navigate(Routes.HOME) { popUpTo(Routes.AUTH_GATE) { inclusive = true } } }
            }
        }
        composable(Routes.LOGIN) {
            LoginScreen(actionState,
                onPasswordLogin = authViewModel::signIn,
                onOtp = { email -> authViewModel.sendOtp(email, createUser = false); if (email.contains('@')) navController.navigate(Routes.OTP + "?email=" + java.net.URLEncoder.encode(email,"UTF-8") + "&signup=0") },
                onGoogle = authViewModel::googleSignIn,
                onRegister = { navController.navigate(Routes.REGISTER) },
                onForgot = { navController.navigate(Routes.FORGOT_PASSWORD) })
        }
        composable(Routes.REGISTER) {
            RegisterScreen(actionState, onRegister = { email,password,name,username ->
                authViewModel.signUp(email,password,name,username) { navController.navigate(Routes.OTP + "?email=" + java.net.URLEncoder.encode(email,"UTF-8") + "&signup=1") }
            }, onBack = { navController.popBackStack() })
        }
        composable(Routes.OTP + "?email={email}&signup={signup}", arguments = listOf(androidx.navigation.navArgument("email") { defaultValue = "" }, androidx.navigation.navArgument("signup") { defaultValue = "0" })) { backStack ->
            val email = java.net.URLDecoder.decode(backStack.arguments?.getString("email").orEmpty(), "UTF-8")
            val isSignup = backStack.arguments?.getString("signup") == "1"
            OtpScreen(email, actionState, onVerify = { token -> authViewModel.verifyOtp(email, token, isSignup) { navController.navigate(Routes.HOME) { popUpTo(0) } } }, onResend = { if (!isSignup) authViewModel.sendOtp(email, createUser = false) }, onBack = { navController.popBackStack() })
        }
        composable(Routes.FORGOT_PASSWORD) { ForgotPasswordScreen(actionState, authViewModel::forgotPassword) { navController.popBackStack() } }
        composable(Routes.CHANGE_PASSWORD) { ChangePasswordScreen(actionState, onChange = { authViewModel.changePassword(it) { navController.popBackStack() } }, onBack = { navController.popBackStack() }) }
        composable(Routes.ACCOUNT) {
            val user = (authState as? AuthState.SignedIn)?.user
            if (user != null) AccountScreen(user, actionState,
                onSave = { name, username -> authViewModel.updateProfile(name, username) {} },
                onAvatar = { bytes, contentType -> authViewModel.uploadAvatar(bytes, contentType) },
                onChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) },
                onSignOut = { authViewModel.signOut(); navController.navigate(Routes.LOGIN) { popUpTo(0) } },
                onBack = { navController.popBackStack() })
        }
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            val summary by viewModel.summary.collectAsStateWithLifecycle()
            val healthState by viewModel.healthState.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current
            val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { viewModel.onHealthPermissionsResult() }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshHealth() }
                lifecycleOwner.lifecycle.addObserver(observer); onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            MainShell(
                summary = summary,
                healthState = healthState,
                user = (authState as? AuthState.SignedIn)?.user,
                authAction = actionState,
                onAddFood = { navController.navigate(Routes.ADD_FOOD) },
                onConnectHealth = { permissionLauncher.launch(viewModel.requiredHealthPermissions) },
                onRefreshHealth = viewModel::refreshHealth,
                onEditAccount = { navController.navigate(Routes.ACCOUNT) },
                onSignOut = { authViewModel.signOut(); navController.navigate(Routes.LOGIN) { popUpTo(0) } },
            )
        }
        composable(Routes.ADD_FOOD) { AddFoodScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SOCIAL) { SocialScreen(onBack = { navController.popBackStack() }) }
    }
}

@Composable
private fun HomeScreen(
    summary: DailySummary,
    healthState: HealthUiState,
    onAddFood: () -> Unit,
    onAccount: () -> Unit,
    onSocial: () -> Unit,
    onConnectHealth: () -> Unit,
    onRefreshHealth: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Caloly", color = CalolyLavender, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Bugün", color = CalolyMuted, fontSize = 15.sp)
                    }
                    Row {
                        IconButton(onClick = onSocial) { Icon(Icons.Rounded.Group, contentDescription = "Takip", tint = CalolyGreen) }
                        IconButton(onClick = onAccount) { Icon(Icons.Rounded.Person, contentDescription = "Hesabım", tint = CalolyLavender) }
                    }
                }
            }
            item { CalorieHero(summary) }
            item {
                Button(
                    onClick = onAddFood,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = CalolyGreen, contentColor = CalolyLavenderWhite),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Yemek Ekle", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            item {
                HealthConnectCard(healthState, onConnectHealth, onRefreshHealth)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("Protein", "${summary.proteinGrams}/${summary.proteinGoal} g", Modifier.weight(1f))
                    MetricCard("Karbonhidrat", "${summary.carbsGrams}/${summary.carbsGoal} g", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("Yağ", "${summary.fatGrams}/${summary.fatGoal} g", Modifier.weight(1f))
                    MetricCard("Adım", summary.steps.toString(), Modifier.weight(1f), icon = "walk")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BurnCard("Aktif kalori", summary.activeCalories, Modifier.weight(1f))
                    BurnCard("Toplam yakılan", summary.totalCaloriesBurned, Modifier.weight(1f))
                }
            }
            if (summary.logs.isNotEmpty()) {
                item { Text("Bugünün öğünleri", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
                items(summary.logs.size) { index ->
                    val log = summary.logs[index]
                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(log.foodName, fontWeight = FontWeight.Bold)
                                Text("${log.mealType.label} • ${formatAmount(log.amount)} ${log.unit.label}", color = CalolyMuted, fontSize = 13.sp)
                            }
                            Text("${log.calories} kcal", color = CalolyGreen, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthConnectCard(
    state: HealthUiState,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.HealthAndSafety, null, tint = CalolyLavender)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Health Connect", fontWeight = FontWeight.ExtraBold)
                    Text(
                        when {
                            state.availability == HealthConnectAvailability.UNAVAILABLE -> "Bu cihazda kullanılamıyor"
                            state.availability == HealthConnectAvailability.UPDATE_REQUIRED -> "Health Connect güncellemesi gerekiyor"
                            state.hasPermissions -> "Adım ve kalori verileri bağlı"
                            else -> "Samsung Health ve diğer uyumlu uygulamalardan veri al"
                        },
                        color = CalolyMuted,
                        fontSize = 13.sp,
                    )
                }
            }
            if (state.availability == HealthConnectAvailability.AVAILABLE) {
                if (!state.hasPermissions) {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = CalolyGreen, contentColor = CalolyLavenderWhite),
                    ) { Text("Health Connect'e Bağlan", fontWeight = FontWeight.Bold) }
                } else {
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(Icons.Rounded.Sync, null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (state.loading) "Güncelleniyor…" else "Verileri Yenile")
                    }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }
}

private fun formatAmount(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

@Composable
private fun CalorieHero(summary: DailySummary) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = CalolyLavender)
                Spacer(Modifier.size(8.dp))
                Text("Günlük hedef", color = CalolyMuted, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
            Text(summary.consumedCalories.toString(), fontSize = 46.sp, fontWeight = FontWeight.ExtraBold)
            Text("/ ${summary.calorieGoal} kcal", color = CalolyMuted, fontSize = 17.sp)
            Spacer(Modifier.height(14.dp))
            Text("${max(0, summary.calorieGoal - summary.consumedCalories)} kcal kaldı", color = CalolyGreen, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BurnCard(title: String, calories: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp)) {
            Icon(Icons.Rounded.LocalFireDepartment, null, tint = CalolyLavender)
            Spacer(Modifier.height(8.dp))
            Text(title, color = CalolyMuted, fontSize = 13.sp)
            Text("$calories kcal", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier, icon: String? = null) {
    Card(modifier = modifier, shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(18.dp)) {
            if (icon == "walk") {
                Icon(Icons.Rounded.DirectionsWalk, null, tint = CalolyLavender)
                Spacer(Modifier.height(8.dp))
            }
            Text(title, color = CalolyMuted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
        }
    }
}
