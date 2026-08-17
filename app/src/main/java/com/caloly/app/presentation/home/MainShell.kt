package com.caloly.app.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caloly.app.domain.auth.CalolyUser
import com.caloly.app.domain.model.DailySummary
import com.caloly.app.presentation.auth.AuthActionState
import com.caloly.app.presentation.social.SocialScreen
import com.caloly.app.presentation.theme.*
import kotlin.math.max
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Ana Sayfa", Icons.Rounded.Home), NUTRITION("Beslenme", Icons.Rounded.Restaurant),
    ACTIVITY("Aktivite", Icons.Rounded.DirectionsRun), SOCIAL("Takip", Icons.Rounded.Group),
    PROFILE("Profil", Icons.Rounded.Person)
}

@Composable
fun MainShell(
    summary: DailySummary,
    healthState: HealthUiState,
    user: CalolyUser?,
    authAction: AuthActionState,
    onAddFood: () -> Unit,
    onConnectHealth: () -> Unit,
    onRefreshHealth: () -> Unit,
    onEditAccount: () -> Unit,
    onEditGoals: () -> Unit,
    onSecurity: () -> Unit,
    onSharingSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(MainTab.HOME) }
    Scaffold(
        containerColor = CalolyBackground,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0E111B), tonalElevation = 0.dp) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, tab.label) },
                        label = { Text(tab.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CalolyLavenderWhite, selectedTextColor = CalolyLavender,
                            indicatorColor = CalolyPurple.copy(alpha = .35f),
                            unselectedIconColor = CalolyMuted, unselectedTextColor = CalolyMuted,
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selected) {
                MainTab.HOME -> Dashboard(summary, user, onAddFood) { selected = MainTab.ACTIVITY }
                MainTab.NUTRITION -> NutritionScreen(summary, onAddFood)
                MainTab.ACTIVITY -> ActivityScreen(summary, healthState, onConnectHealth, onRefreshHealth)
                MainTab.SOCIAL -> SocialScreen(onBack = null)
                MainTab.PROFILE -> ProfileScreen(user, authAction, onEditAccount, onEditGoals, onSecurity, onSharingSettings, onSignOut)
            }
        }
    }
}

@Composable private fun PageHeader(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black, color = CalolyText)
        Text(subtitle, color = CalolyMuted, fontSize = 14.sp)
    }
}

@Composable
private fun Dashboard(summary: DailySummary, user: CalolyUser?, onAddFood: () -> Unit, onActivity: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("CALOLY", fontSize = 12.sp, letterSpacing = 3.sp, color = CalolyLavender, fontWeight = FontWeight.Black)
                    Text("Merhaba, ${user?.displayName?.substringBefore(' ') ?: "sen"}", fontSize = 25.sp, color = CalolyText, fontWeight = FontWeight.ExtraBold)
                    Text(LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale.forLanguageTag("tr-TR"))), color = CalolyMuted)
                }
                GradientBadge(Icons.Rounded.AutoAwesome)
            }
        }
        item { CalorieRingCard(summary) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactMetric("Tüketilen", "${summary.consumedCalories}", "kcal", CalolyPink, Modifier.weight(1f))
                CompactMetric("Hedef", "${summary.calorieGoal}", "kcal", CalolyLavender, Modifier.weight(1f))
                CompactMetric("Protein", "${summary.proteinGrams}/${summary.proteinGoal}", "g", CalolyGreen, Modifier.weight(1f))
                CompactMetric("Adım", "${summary.steps}", "", CalolyBlue, Modifier.weight(1f))
            }
        }
        item {
            Button(onClick = onAddFood, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CalolyPurple)) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Yemek Ekle", fontWeight = FontWeight.Bold)
            }
        }
        item { SectionLabel("Makro dağılımı", "Günlük hedef") }
        item { MacroCard(summary) }
        item { SectionLabel("Günlük aktivite", "Detaylar", onActivity) }
        item { ActivityPreview(summary) }
        if (summary.logs.isNotEmpty()) {
            item { SectionLabel("Son öğünler", "Tümünü gör") }
            items(summary.logs.take(3)) { MealRow(it.foodName, it.mealType.label, it.calories) }
        }
    }
}

@Composable
private fun CalorieRingCard(summary: DailySummary) {
    val progress = (summary.consumedCalories.toFloat() / summary.calorieGoal.coerceAtLeast(1)).coerceIn(0f, 1f)
    val remaining = max(0, summary.calorieGoal - summary.consumedCalories)
    AppCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
                ProgressRing(progress, CalolyLavender, CalolyPink)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$remaining", fontSize = 38.sp, fontWeight = FontWeight.Black)
                    Text("kcal kaldı", color = CalolyMuted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StatLine("Tüketilen", "${summary.consumedCalories} kcal", CalolyPink)
                StatLine("Günlük hedef", "${summary.calorieGoal} kcal", CalolyLavender)
                StatLine("Aktif yakılan", "${summary.activeCalories} kcal", CalolyOrange)
            }
        }
    }
}

@Composable private fun ProgressRing(progress: Float, start: Color, end: Color, modifier: Modifier = Modifier.fillMaxSize()) {
    Canvas(modifier.padding(12.dp)) {
        val stroke = 13.dp.toPx(); val inset = stroke / 2
        drawArc(Color(0xFF292D3C), -90f, 360f, false, Offset(inset, inset), Size(size.width-stroke, size.height-stroke), style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(Brush.sweepGradient(listOf(start, end, start)), -90f, 360f * progress, false, Offset(inset, inset), Size(size.width-stroke, size.height-stroke), style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable private fun MacroCard(s: DailySummary) = AppCard {
    Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) {
            Box(Modifier.weight((s.carbsGrams * 4).coerceAtLeast(1).toFloat()).fillMaxHeight().background(CalolyBlue))
            Box(Modifier.weight((s.proteinGrams * 4).coerceAtLeast(1).toFloat()).fillMaxHeight().background(CalolyPink))
            Box(Modifier.weight((s.fatGrams * 9).coerceAtLeast(1).toFloat()).fillMaxHeight().background(CalolyOrange))
        }
        MacroBar("Protein", s.proteinGrams, s.proteinGoal, CalolyPink)
        MacroBar("Karbonhidrat", s.carbsGrams, s.carbsGoal, CalolyBlue)
        MacroBar("Yağ", s.fatGrams, s.fatGoal, CalolyOrange)
    }
}

@Composable private fun MacroBar(label: String, value: Int, goal: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(label, fontWeight = FontWeight.SemiBold); Text("$value / $goal g", color = CalolyMuted) }
        LinearProgressIndicator(progress = { (value.toFloat()/goal.coerceAtLeast(1)).coerceIn(0f,1f) }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = color, trackColor = Color(0xFF292D3C))
    }
}

@Composable private fun ActivityPreview(s: DailySummary) = AppCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            ProgressRing((s.steps / 10000f).coerceIn(0f,1f), CalolyGreen, CalolyBlue)
            Icon(Icons.Rounded.DirectionsWalk, null, tint = CalolyGreen)
        }
        Spacer(Modifier.width(18.dp))
        Column { Text("%,d adım".format(s.steps), fontSize = 24.sp, fontWeight = FontWeight.Black); Text("10.000 günlük hedef", color = CalolyMuted); Spacer(Modifier.height(8.dp)); Text("${s.totalCaloriesBurned} kcal toplam yakım", color = CalolyGreen, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun NutritionScreen(s: DailySummary, onAdd: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { PageHeader("Beslenme", "Bugünün öğünleri"); GradientBadge(Icons.Rounded.Restaurant) } }
        item { Surface(shape = RoundedCornerShape(18.dp), color = CalolySurfaceHigh) { Row(Modifier.fillMaxWidth().padding(14.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Icon(Icons.Rounded.ChevronLeft, null, tint = CalolyMuted); Text(LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr-TR"))), fontWeight = FontWeight.Bold); Icon(Icons.Rounded.ChevronRight, null, tint = CalolyMuted) } } }
        item { MacroCard(s) }
        val groups = listOf("Kahvaltı", "Öğle", "Akşam", "Ara Öğün")
        groups.forEach { meal ->
            val logs = s.logs.filter { it.mealType.label.equals(meal, true) }
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(meal, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); Text("${logs.sumOf { it.calories }} kcal", color = CalolyLavender) }
                        if (logs.isEmpty()) Text("Henüz yemek eklenmedi", color = CalolyMuted) else logs.forEach { MealRow(it.foodName, it.mealType.label, it.calories, false) }
                        TextButton(onClick = onAdd) { Icon(Icons.Rounded.Add, null); Text(" Yemek ekle") }
                    }
                }
            }
        }
    }
}

@Composable private fun ActivityScreen(s: DailySummary, health: HealthUiState, onConnect: () -> Unit, onRefresh: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader("Aktivite", "Hareket, enerji ve sağlık") }
        item { AppCard { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) { ProgressRing((s.steps/10000f).coerceIn(0f,1f), CalolyGreen, CalolyBlue); Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.DirectionsWalk, null, tint=CalolyGreen, modifier=Modifier.size(34.dp)); Text("%,d".format(s.steps), fontSize=36.sp, fontWeight=FontWeight.Black); Text("/ 10.000 adım", color=CalolyMuted) } } } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { MetricTile("Aktif enerji", "${s.activeCalories} kcal", Icons.Rounded.LocalFireDepartment, CalolyOrange, Modifier.weight(1f)); MetricTile("Toplam yakım", "${s.totalCaloriesBurned} kcal", Icons.Rounded.Bolt, CalolyPink, Modifier.weight(1f)) } }
        item { AppCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Health Connect", fontWeight=FontWeight.ExtraBold, fontSize=18.sp); Text(if (health.hasPermissions) "Sağlık verilerin bağlı ve güncel." else "Adım ve enerji verilerini güvenle eşitle.", color=CalolyMuted); Button(onClick = if (health.hasPermissions) onRefresh else onConnect, modifier=Modifier.fillMaxWidth(), colors=ButtonDefaults.buttonColors(containerColor=CalolyPurple)) { Icon(if (health.hasPermissions) Icons.Rounded.Sync else Icons.Rounded.HealthAndSafety, null); Text(if (health.hasPermissions) " Verileri yenile" else " Health Connect'e bağlan") } } } }
    }
}

private data class SettingItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable private fun ProfileScreen(user: CalolyUser?, state: AuthActionState, onEdit: () -> Unit, onGoals: () -> Unit, onSecurity: () -> Unit, onSharing: () -> Unit, onSignOut: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader("Profil", "Hesap ve paylaşım ayarları") }
        item { AppCard { Row(verticalAlignment=Alignment.CenterVertically) { Surface(shape=CircleShape, color=CalolyPurple) { Box(Modifier.size(70.dp), contentAlignment=Alignment.Center) { Text((user?.displayName ?: user?.username ?: "C").take(1).uppercase(), fontSize=28.sp, fontWeight=FontWeight.Black) } }; Spacer(Modifier.width(15.dp)); Column { Text(user?.displayName ?: "Caloly kullanıcısı", fontSize=20.sp, fontWeight=FontWeight.ExtraBold); Text(user?.email.orEmpty(), color=CalolyMuted); user?.username?.let { Text("@$it", color=CalolyLavender) } } } } }
        item { SettingsGroup("Hesap", listOf(SettingItem("Profil bilgilerini düzenle", Icons.Rounded.Edit, onEdit), SettingItem("Kişisel bilgiler ve hedefler", Icons.Rounded.TrackChanges, onGoals), SettingItem("Şifre ve güvenlik", Icons.Rounded.Lock, onSecurity))) }
        item { SettingsGroup("Paylaşım", listOf(SettingItem("Partner paylaşım izinleri", Icons.Rounded.Favorite, onSharing), SettingItem("Aktivite görünürlüğü", Icons.Rounded.Visibility, onSharing), SettingItem("Beslenme detayları", Icons.Rounded.Restaurant, onSharing))) }
        state.error?.let { item { Text(it, color=MaterialTheme.colorScheme.error) } }
        item { OutlinedButton(onClick=onSignOut, Modifier.fillMaxWidth().height(52.dp), shape=RoundedCornerShape(18.dp)) { Icon(Icons.Rounded.Logout, null); Text(" Çıkış Yap") } }
    }
}

@Composable private fun SettingsGroup(title: String, rows: List<SettingItem>) = AppCard { Column { Text(title.uppercase(), color=CalolyLavender, fontSize=11.sp, letterSpacing=2.sp, fontWeight=FontWeight.Bold); rows.forEach { item -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick=item.onClick).padding(vertical=14.dp), verticalAlignment=Alignment.CenterVertically) { Icon(item.icon,null,tint=CalolyMuted); Spacer(Modifier.width(12.dp)); Text(item.label,Modifier.weight(1f)); Icon(Icons.Rounded.ChevronRight,null,tint=CalolyMuted) } } } }

@Composable private fun AppCard(content: @Composable ColumnScope.() -> Unit) = Card(Modifier.fillMaxWidth(), shape=RoundedCornerShape(24.dp), colors=CardDefaults.cardColors(containerColor=CalolySurface), border=CardDefaults.outlinedCardBorder()) { Column(Modifier.padding(18.dp), content=content) }
@Composable private fun GradientBadge(icon: ImageVector) = Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(CalolyPurple,CalolyPink))), contentAlignment=Alignment.Center) { Icon(icon,null,tint=Color.White) }
@Composable private fun SectionLabel(title:String, action:String, onClick:()->Unit={}) = Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(title,fontSize=19.sp,fontWeight=FontWeight.ExtraBold); TextButton(onClick=onClick) { Text(action,color=CalolyLavender) } }
@Composable private fun StatLine(label:String,value:String,color:Color)=Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).clip(CircleShape).background(color));Spacer(Modifier.width(9.dp));Column{Text(label,color=CalolyMuted,fontSize=12.sp);Text(value,fontWeight=FontWeight.Bold)}}
@Composable
private fun MealRow(name: String, meal: String, calories: Int, card: Boolean = true) {
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(if (card) 16.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = CalolyLavenderLight) {
                Icon(Icons.Rounded.Restaurant, null, tint = CalolyLavender, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold)
                Text(meal, color = CalolyMuted, fontSize = 12.sp)
            }
            Text("$calories kcal", color = CalolyGreen, fontWeight = FontWeight.Bold)
        }
    }
    if (card) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CalolySurface)) { content() }
    } else {
        content()
    }
}
@Composable private fun MetricTile(title:String,value:String,icon:ImageVector,color:Color,modifier:Modifier)=Card(modifier,shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=CalolySurface)){Column(Modifier.padding(17.dp)){Icon(icon,null,tint=color);Spacer(Modifier.height(10.dp));Text(title,color=CalolyMuted,fontSize=12.sp);Text(value,fontWeight=FontWeight.ExtraBold)}}
@Composable private fun CompactMetric(title:String,value:String,unit:String,color:Color,modifier:Modifier)=Card(modifier,shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=CalolySurface)){Column(Modifier.padding(horizontal=8.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(6.dp).clip(CircleShape).background(color));Spacer(Modifier.height(6.dp));Text(title,color=CalolyMuted,fontSize=9.sp,maxLines=1);Text(value,fontWeight=FontWeight.Black,fontSize=14.sp,maxLines=1);if(unit.isNotEmpty())Text(unit,color=CalolyMuted,fontSize=9.sp)}}
