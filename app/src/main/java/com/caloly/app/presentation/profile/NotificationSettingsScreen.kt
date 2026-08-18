package com.caloly.app.presentation.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.caloly.app.notifications.NotificationPreferences
import com.caloly.app.presentation.theme.CalolyBackground
import com.caloly.app.presentation.theme.CalolyGreen
import com.caloly.app.presentation.theme.CalolyLavender
import com.caloly.app.presentation.theme.CalolyMuted
import com.caloly.app.presentation.theme.CalolySurface

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    fun hasPermission() = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var permissionGranted by remember { mutableStateOf(hasPermission()) }
    var enabled by remember { mutableStateOf(NotificationPreferences.isEnabled(context)) }
    var hour by remember { mutableIntStateOf(NotificationPreferences.hour(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }

    Scaffold(
        containerColor = CalolyBackground,
        topBar = {
            TopAppBar(
                title = { Text("Bildirim Ayarları", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Geri") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CalolyBackground),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CalolySurface)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Notifications, null, tint = CalolyLavender)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("Günlük kayıt hatırlatması", fontWeight = FontWeight.Bold)
                        Text("Yediklerini kaydetmen için seçtiğin saatte bildirim gönderir.", color = CalolyMuted)
                    }
                    Switch(checked = enabled, onCheckedChange = {
                        enabled = it
                        NotificationPreferences.setEnabled(context, it)
                    })
                }
            }

            Text("Hatırlatma saati", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(9, 13, 20, 22).forEach { candidate ->
                    FilterChip(
                        selected = hour == candidate,
                        onClick = { hour = candidate; NotificationPreferences.setHour(context, candidate) },
                        label = { Text("%02d:00".format(candidate)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CalolySurface)) {
                Column(Modifier.padding(18.dp)) {
                    Text(if (permissionGranted) "Telefon bildirim izni açık" else "Telefon bildirim izni kapalı", fontWeight = FontWeight.Bold, color = if (permissionGranted) CalolyGreen else MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(6.dp))
                    Text("İzni kapatırsan Caloly hatırlatma gönderemez. Tercihini daha sonra buradan değiştirebilirsin.", color = CalolyMuted)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && !permissionGranted) {
                            NotificationPreferences.markPermissionAsked(context)
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                    }) {
                        Text(if (!permissionGranted && Build.VERSION.SDK_INT >= 33) "Bildirimlere İzin Ver" else "Telefon Ayarlarını Aç")
                    }
                }
            }
        }
    }
}
