package com.caloly.app.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caloly.app.domain.auth.CalolyUser
import com.caloly.app.domain.model.calculateBmi
import com.caloly.app.presentation.theme.*

private data class Choice(val value: String, val label: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    user: CalolyUser,
    state: AuthActionState,
    onSave: (String, Int, Double, String) -> Unit,
    onSkip: () -> Unit,
) {
    var birthDate by remember(user.id) { mutableStateOf(user.birthDate.orEmpty()) }
    var height by remember(user.id) { mutableStateOf(user.heightCm?.toString().orEmpty()) }
    var weight by remember(user.id) { mutableStateOf(user.weightKg?.toString().orEmpty()) }
    var gender by remember(user.id) { mutableStateOf(user.gender ?: "UNDISCLOSED") }
    val bmi = calculateBmi(height.toIntOrNull(), weight.replace(',', '.').toDoubleOrNull())

    Scaffold(containerColor = CalolyBackground) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                Modifier.size(58.dp).background(Brush.linearGradient(listOf(CalolyPurple, CalolyPink)), RoundedCornerShape(19.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.MonitorHeart, null, tint = CalolyLavenderWhite) }
            Text("Vücut Bilgileri", fontSize = 31.sp, fontWeight = FontWeight.Black)
            Text("İstersen boy ve kilo bilgilerinle vücut kitle indeksini görebilirsin. Bu alan beslenme hedefi oluşturmaz ve daha sonra profilden güncellenebilir.", color = CalolyMuted)

            ProfileField(birthDate, { birthDate = it.filter { c -> c.isDigit() || c == '-' }.take(10) }, "Doğum tarihi (YYYY-AA-GG)")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileField(height, { height = it.filter(Char::isDigit).take(3) }, "Boy (cm)", Modifier.weight(1f), KeyboardType.Number)
                ProfileField(weight, { weight = decimal(it) }, "Kilo (kg)", Modifier.weight(1f), KeyboardType.Decimal)
            }
            ChoiceSection(
                "Cinsiyet",
                listOf(Choice("FEMALE", "Kadın"), Choice("MALE", "Erkek"), Choice("UNDISCLOSED", "Belirtmek istemiyorum")),
                gender,
            ) { gender = it }

            bmi?.let {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text("Vücut kitle indeksi", color = CalolyMuted); Text("$it", fontSize = 32.sp, fontWeight = FontWeight.Black, color = CalolyLavender) }
                        Text("Bilgilendirme amaçlı", color = CalolyMuted, fontSize = 12.sp)
                    }
                }
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    onSave(
                        birthDate,
                        height.toIntOrNull() ?: 0,
                        weight.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        gender,
                    )
                },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(19.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CalolyPurple),
            ) {
                if (state.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Bilgileri Kaydet", fontWeight = FontWeight.ExtraBold)
            }
            TextButton(onClick = onSkip, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                Text("Şimdilik Atla", color = CalolyMuted, fontWeight = FontWeight.Bold)
            }
            Text("VKİ tek başına sağlık değerlendirmesi veya tıbbi tanı değildir.", color = CalolyMuted, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProfileField(value: String, onValue: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(), keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value, onValue, modifier, label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(18.dp), keyboardOptions = KeyboardOptions(keyboardType = keyboardType))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceSection(title: String, choices: List<Choice>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice -> FilterChip(selected = selected == choice.value, onClick = { onSelect(choice.value) }, label = { Text(choice.label) }) }
        }
    }
}

private fun decimal(value: String): String = value.filter { it.isDigit() || it == ',' || it == '.' }.take(6)
