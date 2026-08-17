package com.caloly.app.presentation.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.caloly.app.domain.model.NutritionTemplate
import com.caloly.app.domain.model.TemplateKind
import com.caloly.app.domain.social.*
import com.caloly.app.presentation.theme.*

@Composable
fun SocialScreen(onBack: (() -> Unit)?, viewModel: SocialViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = CalolyBackground,
        topBar = {
            TopAppBar(
                title = { Text("Birlikte", color = CalolyLavender, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Geri") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CalolyBackground),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(state.query, viewModel::setQuery, Modifier.weight(1f), placeholder = { Text("Kullanıcı adı veya isim") }, singleLine = true, shape = RoundedCornerShape(24.dp))
                    Button(onClick = viewModel::search, enabled = !state.loading, shape = RoundedCornerShape(22.dp), colors = ButtonDefaults.buttonColors(containerColor = CalolyGreen)) { Text("Ara") }
                }
            }
            state.message?.let { message ->
                item { Card(colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight), shape = RoundedCornerShape(20.dp)) { Text(message, Modifier.fillMaxWidth().padding(15.dp), color = CalolyMuted) } }
            }
            if (state.searchResults.isNotEmpty()) {
                item { SectionTitle("Kullanıcılar") }
                items(state.searchResults, key = { it.id }) { profile ->
                    ProfileCard(profile, { viewModel.sendRequest(profile, "FRIEND") }, { viewModel.sendRequest(profile, "PARTNER") })
                }
            }
            if (state.requests.isNotEmpty()) {
                item { SectionTitle("Gelen İstekler") }
                items(state.requests, key = { it.requestId }) { request -> RequestCard(request, { viewModel.respond(request, true) }, { viewModel.respond(request, false) }) }
            }
            val goalFriends = state.connections.filter { it.relationshipType == "PARTNER" }
            if (goalFriends.isNotEmpty()) {
                item { SectionTitle("Hedef Arkadaşların") }
                items(goalFriends, key = { it.relationshipId }) { connection -> ConnectionCard(connection, state.summaries[connection.relationshipId], true) { viewModel.openConnection(connection) } }
            }
            val friends = state.connections.filter { it.relationshipType != "PARTNER" }
            if (friends.isNotEmpty()) {
                item { SectionTitle("Arkadaşların") }
                items(friends, key = { it.relationshipId }) { connection -> ConnectionCard(connection, state.summaries[connection.relationshipId], false) { viewModel.openConnection(connection) } }
            }
            if (friends.isEmpty() && goalFriends.isEmpty() && !state.loading) item { EmptySocialCard() }
            if (state.sharedTemplates.isNotEmpty()) {
                item { SectionTitle("Arkadaşlarından Örnek Beslenmeler") }
                items(state.sharedTemplates, key = { it.id }) { template -> SharedTemplateCard(template) { viewModel.saveSharedTemplate(template) } }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = CalolyLavender) }
        }
    }
    state.selected?.let { connection ->
        ConnectionDialog(
            connection = connection,
            summary = state.selectedSummary,
            goals = state.selectedGoals,
            onDismiss = viewModel::closeConnection,
            onPermissionsChanged = viewModel::updateSharing,
            onStepGoal = viewModel::createStepGoal,
        )
    }
}

@Composable private fun SectionTitle(text: String) = Text(text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)

@Composable
private fun ProfileCard(profile: SocialProfile, onFriend: () -> Unit, onGoalFriend: () -> Unit) = Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight)) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(profile); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(profile.displayName ?: profile.username ?: "Caloly kullanıcısı", fontWeight = FontWeight.Bold); profile.username?.let { Text("@$it", color = CalolyMuted) } }
        when (profile.relationshipStatus) {
            "CONNECTED" -> Text("Arkadaş", color = CalolyGreen, fontWeight = FontWeight.Bold)
            "REQUESTED" -> Text("İstek gönderildi", color = CalolyMuted)
            "INCOMING" -> Text("İstek var", color = CalolyLavender)
            else -> Row { IconButton(onClick = onFriend) { Icon(Icons.Rounded.PersonAdd, "Arkadaş olarak ekle", tint = CalolyGreen) }; IconButton(onClick = onGoalFriend) { Icon(Icons.Rounded.Favorite, "Hedef arkadaşı olarak ekle", tint = CalolyLavender) } }
        }
    }
}

@Composable
private fun RequestCard(request: IncomingFollowRequest, onAccept: () -> Unit, onReject: () -> Unit) = Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Avatar(request.requester); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(request.requester.displayName ?: request.requester.username ?: "Caloly kullanıcısı", fontWeight = FontWeight.Bold); Text(if (request.relationshipType == "PARTNER") "Hedef arkadaşı isteği" else "Arkadaşlık isteği", color = CalolyLavender) } }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = onAccept, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = CalolyGreen)) { Text("Kabul Et") }; OutlinedButton(onClick = onReject, shape = CircleShape) { Text("Reddet") } }
    }
}

@Composable
private fun ConnectionCard(connection: SocialConnection, summary: SharedDailySummary?, goalFriend: Boolean, onOpen: () -> Unit) = Card(onClick = onOpen, shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight)) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Avatar(connection.profile); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(connection.profile.displayName ?: connection.profile.username ?: "Caloly kullanıcısı", fontWeight = FontWeight.Bold); Text(if (goalFriend) "Hedef arkadaşın" else "Arkadaşın", color = CalolyMuted, fontSize = 12.sp) }; Text("Bugün", color = CalolyLavender, fontWeight = FontWeight.Bold) }
        SharedMetrics(summary)
    }
}

@Composable
private fun SharedMetrics(summary: SharedDailySummary?) {
    if (summary == null) { Text("Bugün için paylaşılmış veri yok", color = CalolyMuted); return }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        summary.consumedCalories?.let { MiniMetric("Kalori", "$it", Modifier.weight(1f)) }
        summary.steps?.let { MiniMetric("Adım", "%,d".format(it), Modifier.weight(1f)) }
        summary.proteinGrams?.let { MiniMetric("Protein", "${it}g", Modifier.weight(1f)) }
    }
}

@Composable private fun MiniMetric(label: String, value: String, modifier: Modifier = Modifier) = Surface(modifier, RoundedCornerShape(18.dp), color = CalolyBackground) { Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontWeight = FontWeight.ExtraBold, color = CalolyLavender); Text(label, color = CalolyMuted, style = MaterialTheme.typography.labelSmall) } }

@Composable
private fun SharedTemplateCard(template: NutritionTemplate, onSave: () -> Unit) = Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CalolySurface)) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (template.kind == TemplateKind.DAY) Icons.Rounded.CalendarMonth else Icons.Rounded.Restaurant, null, tint = CalolyLavender); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(template.name, fontWeight = FontWeight.Bold); Text("${template.sourceOwnerName ?: "Arkadaş"} · ${template.items.size} kalem · ${template.totalCalories} kcal", color = CalolyMuted, fontSize = 12.sp) }
        Button(onClick = onSave, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = CalolyPurple)) { Text("Kaydet") }
    }
}

@Composable
private fun EmptySocialCard() = Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight)) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.Group, null, tint = CalolyLavender, modifier = Modifier.size(42.dp)); Spacer(Modifier.height(8.dp)); Text("Henüz arkadaşın yok", fontWeight = FontWeight.Bold); Text("Üstte kullanıcı adıyla arama yapabilirsin.", color = CalolyMuted) }
}

@Composable
private fun ConnectionDialog(connection: SocialConnection, summary: SharedDailySummary?, goals: List<RelationshipGoal>, onDismiss: () -> Unit, onPermissionsChanged: (SharingPermissions) -> Unit, onStepGoal: (Int) -> Unit) {
    var stepTarget by remember { mutableStateOf("8000") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Avatar(connection.profile, 48); Spacer(Modifier.width(10.dp)); Text(connection.profile.displayName ?: connection.profile.username ?: "Arkadaş", color = CalolyLavender, fontWeight = FontWeight.ExtraBold) } },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(max = 560.dp)) {
            item { Text("Bugünkü paylaşımı", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); if (summary == null) Text("Bugün için paylaşılmış veri yok veya bu bilgiler paylaşılmıyor.", color = CalolyMuted) else { summary.consumedCalories?.let { MetricLine("Kalori", "$it kcal") }; summary.proteinGrams?.let { MetricLine("Protein", "$it g") }; summary.carbsGrams?.let { MetricLine("Karbonhidrat", "$it g") }; summary.fatGrams?.let { MetricLine("Yağ", "$it g") }; summary.steps?.let { MetricLine("Adım", "%,d".format(it)) } } }
            item { HorizontalDivider(); Text("Ortak adım hedefleri", fontWeight = FontWeight.Bold) }
            if (goals.isEmpty()) item { Text("Henüz ortak adım hedefiniz yok.", color = CalolyMuted) }
            items(goals.filter { it.metric == GoalMetric.STEPS_DAILY }, key = { it.id }) { GoalCard(it, connection.profile.displayName ?: connection.profile.username ?: "Arkadaşın") }
            item { OutlinedTextField(stepTarget, { stepTarget = it.filter(Char::isDigit).take(5) }, label = { Text("Günlük ortak adım") }, singleLine = true, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp)); Button(onClick = { stepTarget.toIntOrNull()?.let(onStepGoal) }, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = CalolyGreen)) { Text("Adım hedefi ekle") } }
            item { HorizontalDivider(); Text("Benim verilerimi görmesine izin ver", fontWeight = FontWeight.Bold) }
            item { PermissionSwitch("Kalori", connection.mySharing.calories) { onPermissionsChanged(connection.mySharing.copy(calories = it)) } }
            item { PermissionSwitch("Makrolar", connection.mySharing.macros) { onPermissionsChanged(connection.mySharing.copy(macros = it)) } }
            item { PermissionSwitch("Adımlar", connection.mySharing.steps) { onPermissionsChanged(connection.mySharing.copy(steps = it)) } }
            item { PermissionSwitch("Aktivite kalorisi", connection.mySharing.activity) { onPermissionsChanged(connection.mySharing.copy(activity = it)) } }
            item { PermissionSwitch("Kilo", connection.mySharing.weight) { onPermissionsChanged(connection.mySharing.copy(weight = it)) } }
            item { PermissionSwitch("Yemek detayları", connection.mySharing.foodDetails) { onPermissionsChanged(connection.mySharing.copy(foodDetails = it)) } }
            item { PermissionSwitch("Geçmiş günler", connection.mySharing.history) { onPermissionsChanged(connection.mySharing.copy(history = it)) } }
        } },
    )
}

@Composable
private fun GoalCard(goal: RelationshipGoal, friendName: String) = Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = CalolyBackground)) {
    val mine = goal.myValue ?: 0; val friend = goal.partnerValue ?: 0
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(goal.title, fontWeight = FontWeight.ExtraBold, color = CalolyLavender); Text("Sen  %,d / %,d".format(mine, goal.targetValue), fontSize = 12.sp); LinearProgressIndicator(progress = { (mine.toFloat() / goal.targetValue).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = CalolyGreen); Text("$friendName  %,d / %,d".format(friend, goal.targetValue), fontSize = 12.sp); LinearProgressIndicator(progress = { (friend.toFloat() / goal.targetValue).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = CalolyLavender) }
}

@Composable private fun MetricLine(label: String, value: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = CalolyMuted); Text(value, fontWeight = FontWeight.Bold) }
@Composable private fun PermissionSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onChecked) }
@Composable private fun Avatar(profile: SocialProfile, size: Int = 44) { if (!profile.avatarUrl.isNullOrBlank()) AsyncImage(profile.avatarUrl, profile.displayName, Modifier.size(size.dp).clip(CircleShape).background(CalolyLavenderLight), contentScale = ContentScale.Crop) else Surface(shape = CircleShape, color = CalolyLavender) { Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) { Text((profile.displayName ?: profile.username ?: "?").trim().take(1).uppercase(), color = CalolyLavenderWhite, fontWeight = FontWeight.ExtraBold) } } }
