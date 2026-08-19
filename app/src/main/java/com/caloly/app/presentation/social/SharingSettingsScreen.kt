package com.caloly.app.presentation.social

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caloly.app.domain.social.SharingPermissions
import com.caloly.app.presentation.theme.*

@Composable
fun SharingSettingsScreen(
    onBack: () -> Unit,
    viewModel: SocialViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.connections, state.selected) {
        if (state.selected == null && state.connections.isNotEmpty()) viewModel.openConnection(state.connections.first())
    }
    val connection = state.selected

    Scaffold(
        containerColor = CalolyBackground,
        topBar = {
            TopAppBar(
                title = { Text("Paylaşım Ayarları", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CalolyBackground),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shield, null, tint = CalolyLavender)
                    Spacer(Modifier.width(12.dp))
                    Text("Hangi verilerini hedef arkadaşlarınla ve arkadaşlarınla paylaşacağını kişi bazında sen belirlersin.", color = CalolyMuted)
                }
            }

            if (state.connections.isEmpty() && !state.loading) {
                Text("Henüz paylaşım ayarı yapabileceğin bir hedef arkadaşın veya arkadaşın yok.", color = CalolyMuted)
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.connections.forEach { item ->
                        FilterChip(
                            selected = connection?.relationshipId == item.relationshipId,
                            onClick = { viewModel.openConnection(item) },
                            label = { Text(item.profile.displayName ?: item.profile.username ?: "Caloly kullanıcısı") },
                        )
                    }
                }
                connection?.let { selected ->
                    Text("${selected.profile.displayName ?: selected.profile.username ?: "Bağlantı"} verilerimi görebilir", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    SharingCard(selected.mySharing, viewModel::updateSharing)
                }
            }
            state.message?.let { Text(it, color = if (it.contains("güncellendi")) CalolyGreen else MaterialTheme.colorScheme.error) }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = CalolyLavender)
        }
    }
}

@Composable
private fun SharingCard(value: SharingPermissions, onChange: (SharingPermissions) -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CalolySurface)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            SharingSwitch("Kalori", "Günlük tüketilen toplam", value.calories) { onChange(value.copy(calories = it)) }
            SharingSwitch("Makrolar", "Protein, karbonhidrat ve yağ", value.macros) { onChange(value.copy(macros = it)) }
            SharingSwitch("Adım", "Günlük adım sayısı", value.steps) { onChange(value.copy(steps = it)) }
            SharingSwitch("Aktivite kalorisi", "Aktif ve toplam enerji", value.activity) { onChange(value.copy(activity = it)) }
            SharingSwitch("Kilo", "Hassas veri — varsayılan kapalı", value.weight) { onChange(value.copy(weight = it)) }
            SharingSwitch("Yemek detayları", "Yediğin ürün ve öğünler", value.foodDetails) { onChange(value.copy(foodDetails = it)) }
            SharingSwitch("Geçmiş günler", "Bugünden önceki özetler", value.history) { onChange(value.copy(history = it)) }
        }
    }
}

@Composable
private fun SharingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, color = CalolyMuted, fontSize = 12.sp) }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
