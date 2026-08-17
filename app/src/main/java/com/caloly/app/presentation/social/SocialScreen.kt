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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.caloly.app.domain.social.*
import com.caloly.app.presentation.theme.*
import kotlin.math.roundToInt

@Composable
fun SocialScreen(
    onBack: (() -> Unit)?,
    viewModel: SocialViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Birlikte", color = CalolyLavender, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Beslenme hedeflerini yalnız yürütmek zorunda değilsin.", color = CalolyMuted)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Kullanıcı adı veya isim") },
                    shape = RoundedCornerShape(24.dp),
                    trailingIcon = {
                        Button(
                            onClick = viewModel::search,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = CalolyGreen, contentColor = CalolyLavenderWhite),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) { Text("Ara") }
                    }
                )
            }
            if (state.message != null) item { Text(state.message!!, color = CalolyLavender, fontWeight = FontWeight.SemiBold) }
            if (state.searchResults.isNotEmpty()) {
                item { SectionTitle("Kullanıcılar") }
                items(state.searchResults, key = { it.id }) { profile ->
                    ProfileCard(profile, onFriend = { viewModel.sendRequest(profile, "FRIEND") }, onPartner = { viewModel.sendRequest(profile, "PARTNER") })
                }
            }
            if (state.requests.isNotEmpty()) {
                item { SectionTitle("Takip istekleri") }
                items(state.requests, key = { it.requestId }) { request ->
                    RequestCard(request, onAccept = { viewModel.respond(request, true) }, onReject = { viewModel.respond(request, false) })
                }
            }

            val partners = state.connections.filter { it.relationshipType == "PARTNER" }
            if (partners.isNotEmpty()) {
                item { SectionTitle("Partner") }
                items(partners, key = { it.relationshipId }) { connection ->
                    PartnerCard(connection, state.summaries[connection.relationshipId]) { viewModel.openConnection(connection) }
                }
            }

            item { SectionTitle("Takip ettiklerin") }
            val friends = state.connections.filter { it.relationshipType != "PARTNER" }
            if (friends.isEmpty() && partners.isEmpty()) item { EmptySocialCard() }
            else items(friends, key = { it.relationshipId }) { connection ->
                ConnectionCard(connection, state.summaries[connection.relationshipId]) { viewModel.openConnection(connection) }
            }
        }
        if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CalolyGreen) }
    }

    state.selected?.let { connection ->
        ConnectionDialog(
            connection = connection,
            summary = state.selectedSummary,
            goals = state.selectedGoals,
            onDismiss = viewModel::closeConnection,
            onPermissionsChanged = viewModel::updateSharing,
            onStepGoal = viewModel::createStepGoal,
            onCalorieGoal = viewModel::createCalorieGoal,
        )
    }
}

@Composable private fun SectionTitle(text: String) = Text(text, color = CalolyLavender, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)

@Composable
private fun ProfileCard(profile: SocialProfile, onFriend: () -> Unit, onPartner: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(profile)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.displayName ?: profile.username ?: "Caloly kullanıcısı", fontWeight = FontWeight.Bold)
                profile.username?.let { Text("@$it", color = CalolyMuted) }
            }
            when (profile.relationshipStatus) {
                "CONNECTED" -> Text("Bağlı", color = CalolyGreen, fontWeight = FontWeight.Bold)
                "REQUESTED" -> Text("İstek gönderildi", color = CalolyMuted)
                "INCOMING" -> Text("İstek var", color = CalolyLavender)
                else -> Row {
                    IconButton(onClick = onFriend) { Icon(Icons.Rounded.PersonAdd, "Takip et", tint = CalolyGreen) }
                    IconButton(onClick = onPartner) { Icon(Icons.Rounded.Favorite, "Partner olarak ekle", tint = CalolyLavender) }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(request: IncomingFollowRequest, onAccept: () -> Unit, onReject: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(request.requester)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(request.requester.displayName ?: request.requester.username ?: "Caloly kullanıcısı", fontWeight = FontWeight.Bold)
                    request.requester.username?.let { Text("@$it", color = CalolyMuted) }
                    Text(if (request.relationshipType == "PARTNER") "Partner isteği" else "Takip isteği", color = CalolyLavender)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAccept, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = CalolyGreen, contentColor = CalolyLavenderWhite)) { Text("Kabul Et") }
                OutlinedButton(onClick = onReject, shape = CircleShape) { Text("Reddet") }
            }
        }
    }
}

@Composable
private fun PartnerCard(connection: SocialConnection, summary: SharedDailySummary?, onOpen: () -> Unit) {
    Card(onClick = onOpen, shape = RoundedCornerShape(30.dp), colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(connection.profile, 54)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Favorite, null, tint = CalolyLavender, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(connection.profile.displayName ?: connection.profile.username ?: "Partner", fontWeight = FontWeight.ExtraBold)
                    }
                    connection.profile.username?.let { Text("@$it", color = CalolyMuted) }
                }
                GoalBadge(summary)
            }
            SharedMetrics(summary)
        }
    }
}

@Composable
private fun ConnectionCard(connection: SocialConnection, summary: SharedDailySummary?, onOpen: () -> Unit) {
    Card(onClick = onOpen, shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(connection.profile)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(connection.profile.displayName ?: connection.profile.username ?: "Caloly kullanıcısı", fontWeight = FontWeight.Bold)
                    connection.profile.username?.let { Text("@$it", color = CalolyMuted) }
                }
                GoalBadge(summary)
            }
            SharedMetrics(summary)
        }
    }
}

@Composable
private fun SharedMetrics(summary: SharedDailySummary?) {
    if (summary == null) {
        Text("Bugün için paylaşılmış veri yok", color = CalolyMuted)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        summary.consumedCalories?.let { MiniMetric("Kalori", "$it", Modifier.weight(1f)) }
        summary.steps?.let { MiniMetric("Adım", "%,d".format(it), Modifier.weight(1f)) }
        summary.proteinGrams?.let { MiniMetric("Protein", "${it}g", Modifier.weight(1f)) }
    }
}

@Composable
private fun MiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = CalolyBackground) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.ExtraBold, color = CalolyLavender)
            Text(label, color = CalolyMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun GoalBadge(summary: SharedDailySummary?) {
    val consumed = summary?.consumedCalories
    val goal = summary?.calorieGoal
    val onTarget = consumed != null && goal != null && consumed <= goal
    Surface(shape = CircleShape, color = if (onTarget) CalolyGreen else CalolyBackground) {
        Text(if (onTarget) "Hedefte" else "Bugün", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = if (onTarget) CalolyLavenderWhite else CalolyMuted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Avatar(profile: SocialProfile, size: Int = 44) {
    if (!profile.avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = profile.avatarUrl,
            contentDescription = profile.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape).background(CalolyLavenderLight),
        )
    } else {
        Surface(shape = CircleShape, color = CalolyLavender) {
            Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
                val text = profile.displayName ?: profile.username ?: "?"
                Text(text.trim().take(1).uppercase(), color = CalolyLavenderWhite, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun EmptySocialCard() {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Group, null, tint = CalolyLavender, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(8.dp))
            Text("Henüz kimseyi takip etmiyorsun", fontWeight = FontWeight.Bold)
            Text("Üstte kullanıcı adıyla arama yapabilirsin.", color = CalolyMuted)
        }
    }
}

@Composable
private fun ConnectionDialog(
    connection: SocialConnection,
    summary: SharedDailySummary?,
    goals: List<RelationshipGoal>,
    onDismiss: () -> Unit,
    onPermissionsChanged: (SharingPermissions) -> Unit,
    onStepGoal: (Int) -> Unit,
    onCalorieGoal: () -> Unit,
) {
    var stepTarget by remember { mutableStateOf("8000") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
        shape = RoundedCornerShape(30.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(connection.profile, 48); Spacer(Modifier.width(10.dp))
                Column { Text(connection.profile.displayName ?: connection.profile.username ?: "Profil", color = CalolyLavender, fontWeight = FontWeight.ExtraBold); connection.profile.username?.let { Text("@$it", color = CalolyMuted, style = MaterialTheme.typography.bodySmall) } }
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(max = 560.dp)) {
                item {
                    Text("Bugünkü paylaşımı", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (summary == null) Text("Bugün için paylaşılmış veri yok veya karşı taraf bu verileri paylaşmıyor.", color = CalolyMuted)
                    else {
                        summary.consumedCalories?.let { MetricLine("Kalori", "$it / ${summary.calorieGoal ?: "—"} kcal") }
                        summary.proteinGrams?.let { MetricLine("Protein", "$it g") }
                        summary.carbsGrams?.let { MetricLine("Karbonhidrat", "$it g") }
                        summary.fatGrams?.let { MetricLine("Yağ", "$it g") }
                        summary.steps?.let { MetricLine("Adım", "%,d".format(it)) }
                        summary.activeCalories?.let { MetricLine("Aktif kalori", "$it kcal") }
                    }
                }
                item { HorizontalDivider(); Text("Ortak hedefler", fontWeight = FontWeight.Bold) }
                if (goals.isEmpty()) item { Text("Henüz ortak hedefiniz yok.", color = CalolyMuted) }
                items(goals, key = { it.id }) { GoalCard(it, connection.profile.displayName ?: connection.profile.username ?: "Partner") }
                item {
                    OutlinedTextField(value = stepTarget, onValueChange = { stepTarget = it.filter(Char::isDigit).take(5) }, label = { Text("Günlük ortak adım") }, singleLine = true, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { stepTarget.toIntOrNull()?.let(onStepGoal) }, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = CalolyGreen, contentColor = CalolyLavenderWhite)) { Text("Adım hedefi ekle") }
                        OutlinedButton(onClick = onCalorieGoal, shape = CircleShape) { Text("Kalori hedefi") }
                    }
                }
                item { HorizontalDivider(); Text("Benim verilerimi görmesine izin ver", fontWeight = FontWeight.Bold) }
                item { PermissionSwitch("Kalori", connection.mySharing.calories) { onPermissionsChanged(connection.mySharing.copy(calories = it)) } }
                item { PermissionSwitch("Makrolar", connection.mySharing.macros) { onPermissionsChanged(connection.mySharing.copy(macros = it)) } }
                item { PermissionSwitch("Adımlar", connection.mySharing.steps) { onPermissionsChanged(connection.mySharing.copy(steps = it)) } }
                item { PermissionSwitch("Aktivite kalorisi", connection.mySharing.activity) { onPermissionsChanged(connection.mySharing.copy(activity = it)) } }
                item { PermissionSwitch("Kilo", connection.mySharing.weight) { onPermissionsChanged(connection.mySharing.copy(weight = it)) } }
                item { PermissionSwitch("Yemek detayları", connection.mySharing.foodDetails) { onPermissionsChanged(connection.mySharing.copy(foodDetails = it)) } }
                item { PermissionSwitch("Geçmiş günler", connection.mySharing.history) { onPermissionsChanged(connection.mySharing.copy(history = it)) } }
            }
        }
    )
}

@Composable
private fun GoalCard(goal: RelationshipGoal, partnerName: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = CalolyBackground)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(goal.title, fontWeight = FontWeight.ExtraBold, color = CalolyLavender)
            if (goal.metric == GoalMetric.STEPS_DAILY) {
                val my = goal.myValue ?: 0; val partner = goal.partnerValue ?: 0
                val mineProgress = (my.toFloat() / goal.targetValue).coerceIn(0f, 1f)
                val partnerProgress = (partner.toFloat() / goal.targetValue).coerceIn(0f, 1f)
                Text("Sen  %,d / %,d".format(my, goal.targetValue), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { mineProgress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = CalolyGreen)
                Text("$partnerName  %,d / %,d".format(partner, goal.targetValue), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { partnerProgress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = CalolyLavender)
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (goal.myCompleted) "✓ Sen hedeftesin" else "Sen henüz tamamlamadın", color = if (goal.myCompleted) CalolyGreen else CalolyMuted)
                    Text(if (goal.partnerCompleted) "✓ $partnerName" else partnerName, color = if (goal.partnerCompleted) CalolyGreen else CalolyMuted)
                }
            }
            if (goal.myCompleted && goal.partnerCompleted) Text("🔥 İkiniz de hedefinizdesiniz", color = CalolyGreen, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable private fun MetricLine(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = CalolyMuted); Text(value, fontWeight = FontWeight.Bold) } }
@Composable private fun PermissionSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChecked) } }
