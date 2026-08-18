package com.caloly.app.presentation.addfood

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caloly.app.domain.model.Food
import com.caloly.app.domain.model.FoodSource
import com.caloly.app.domain.model.FoodUnit
import com.caloly.app.domain.model.MealType
import com.caloly.app.presentation.theme.CalolyGreen
import com.caloly.app.presentation.theme.CalolyLavender
import com.caloly.app.presentation.theme.CalolyLavenderLight
import com.caloly.app.presentation.theme.CalolyLavenderWhite
import com.caloly.app.presentation.theme.CalolyMuted
import com.caloly.app.presentation.theme.CalolyPink
import com.caloly.app.presentation.theme.CalolySurface
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFoodScreen(
    dateKey: String,
    onBack: () -> Unit,
    viewModel: AddFoodViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(dateKey) { viewModel.setDate(dateKey) }

    fun launchBarcodeScanner() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                barcode.rawValue?.let(viewModel::onBarcodeScanned)
                    ?: viewModel.onScannerError("Barkod okunamadı.")
            }
            .addOnFailureListener { error ->
                viewModel.onScannerError(error.message ?: "Barkod tarayıcı açılamadı.")
            }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Column { Text("Yemek Ekle", fontWeight = FontWeight.ExtraBold, color = CalolyLavender); Text(dateKey, color = CalolyMuted, fontSize = 11.sp) } },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Geri")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Öğün", fontSize = 14.sp, color = CalolyMuted, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.entries.forEach { meal ->
                        FilterChip(
                            selected = state.mealType == meal,
                            onClick = { viewModel.onMealSelected(meal) },
                            label = { Text(meal.label) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CalolyLavenderLight,
                                selectedLabelColor = CalolyLavender,
                            ),
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Albeni, Burçak, yumurta, pilav...") },
                    shape = RoundedCornerShape(28.dp),
                )
            }

            if (state.selectedFood == null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = viewModel::searchOnline,
                            enabled = !state.isLoading,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CalolyGreen,
                                contentColor = CalolyLavenderWhite,
                            ),
                        ) {
                            if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else {
                                Icon(Icons.Rounded.Search, null)
                                Spacer(Modifier.size(7.dp))
                                Text("İnternette Ara", fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedButton(
                            onClick = ::launchBarcodeScanner,
                            enabled = !state.isLoading,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(50),
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, null, tint = CalolyLavender)
                            Spacer(Modifier.size(7.dp))
                            Text("Barkod Tara", color = CalolyLavender, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                state.errorMessage?.let { message ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight.copy(alpha = .55f)),
                        ) {
                            Text(message, Modifier.padding(16.dp), color = CalolyMuted)
                        }
                    }
                }

                item {
                    Text(
                        when {
                            state.showingRemoteResults -> "İnternet sonuçları"
                            state.query.isBlank() -> "Favoriler, son kullanılanlar ve hızlı ekle"
                            else -> "Caloly sonuçları"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                    )
                    Text(
                        "Cihaz diline göre sıralanır · ${state.catalogSize} çevrimdışı ürün",
                        color = CalolyMuted,
                        fontSize = 11.sp,
                    )
                }
                items(state.results, key = { it.id }) { food ->
                    FoodResultCard(
                        food = food,
                        isFavorite = food.id in state.favoriteIds,
                        onFavorite = { viewModel.toggleFavorite(food) },
                        onClick = { viewModel.onFoodSelected(food) },
                    )
                }
                if (state.showingRemoteResults) {
                    item {
                        Text(
                            "Paketli ürün verileri: Open Food Facts (ODbL). Etiket değerlerini kaydetmeden önce kontrol et.",
                            color = CalolyMuted,
                            fontSize = 10.sp,
                        )
                    }
                }
                if (state.results.isEmpty() && state.errorMessage == null) {
                    item {
                        Text(
                            "Yerel katalogda bulunamadı. İnternette Ara veya Barkod Tara ile paketli ürün veritabanını kullanabilirsin.",
                            color = CalolyMuted,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            } else {
                item {
                    SelectedFoodEditor(
                        state = state,
                        onAmountChange = viewModel::onAmountChange,
                        onUnitSelected = viewModel::onUnitSelected,
                        onClear = viewModel::clearSelection,
                        onSave = { viewModel.save(onBack) },
                    )
                }
            }
        }
    }

    state.missingBarcode?.let { barcode ->
        ManualFoodDialog(
            barcode = barcode,
            onDismiss = viewModel::dismissManualFood,
            onSave = viewModel::createCustomFood,
        )
    }
}

@Composable
private fun FoodResultCard(food: Food, isFavorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CalolySurface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(food.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (food.brand != null) Text(food.brand, color = CalolyLavender, fontSize = 13.sp)
                Text("100 g • ${food.caloriesPer100g.toInt()} kcal", color = CalolyMuted, fontSize = 13.sp)
                Text("${food.source.label}${food.barcode?.let { " • $it" } ?: ""}", color = CalolyMuted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (isFavorite) "Favorilerden çıkar" else "Favorilere ekle",
                        tint = if (isFavorite) CalolyPink else CalolyMuted,
                    )
                }
                Text("+", color = CalolyGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedFoodEditor(
    state: AddFoodUiState,
    onAmountChange: (String) -> Unit,
    onUnitSelected: (FoodUnit) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
) {
    val food = state.selectedFood ?: return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = CalolySurface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(food.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    if (food.brand != null) Text(food.brand, color = CalolyLavender, fontWeight = FontWeight.SemiBold)
                    if (food.source == FoodSource.OPEN_FOOD_FACTS) Text("Open Food Facts", color = CalolyMuted, fontSize = 12.sp)
                }
                IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = "Seçimi kaldır") }
            }

            OutlinedTextField(
                value = state.amountText,
                onValueChange = onAmountChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Miktar") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                suffix = { Text(state.unit.label) },
            )

            Text("Ölçü birimi", color = CalolyMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                availableUnits(food).forEach { unit ->
                    FilterChip(
                        selected = state.unit == unit,
                        onClick = { onUnitSelected(unit) },
                        label = { Text(unit.label) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CalolyLavenderLight,
                            selectedLabelColor = CalolyLavender,
                        ),
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight.copy(alpha = 0.55f)),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("${state.previewCalories} kcal", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = CalolyLavender)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Protein ${state.previewProtein} g   •   Karb. ${state.previewCarbs} g   •   Yağ ${state.previewFat} g",
                        color = CalolyMuted,
                        fontSize = 13.sp,
                    )
                }
            }

            Button(
                onClick = onSave,
                enabled = state.amount > 0,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CalolyGreen,
                    contentColor = CalolyLavenderWhite,
                ),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Öğüne Ekle", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun ManualFoodDialog(
    barcode: String,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Double, Double) -> Unit,
) {
    var name by remember(barcode) { mutableStateOf("") }
    var calories by remember(barcode) { mutableStateOf("") }
    var protein by remember(barcode) { mutableStateOf("") }
    var carbs by remember(barcode) { mutableStateOf("") }
    var fat by remember(barcode) { mutableStateOf("") }
    fun number(value: String) = value.replace(',', '.').toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ürünü Caloly'ye ekle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Barkod: $barcode", color = CalolyMuted)
                Text("Besin değerlerini ambalajdaki 100 g / 100 ml bilgisine göre gir.", color = CalolyMuted, fontSize = 12.sp)
                OutlinedTextField(name, { name = it }, label = { Text("Ürün adı") }, singleLine = true)
                OutlinedTextField(calories, { calories = it }, label = { Text("Kalori") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(protein, { protein = it }, label = { Text("Protein") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(carbs, { carbs = it }, label = { Text("Karb.") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(fat, { fat = it }, label = { Text("Yağ") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, number(calories), number(protein), number(carbs), number(fat)) }, enabled = name.isNotBlank()) { Text("Ürünü kullan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

private fun availableUnits(food: Food): List<FoodUnit> = when (food.defaultUnit) {
    FoodUnit.MILLILITER -> listOf(FoodUnit.MILLILITER)
    FoodUnit.PIECE -> listOf(FoodUnit.PIECE, FoodUnit.GRAM)
    FoodUnit.SLICE -> listOf(FoodUnit.SLICE, FoodUnit.GRAM)
    FoodUnit.PACKAGE -> listOf(FoodUnit.PACKAGE, FoodUnit.GRAM)
    FoodUnit.GRAM -> listOf(FoodUnit.GRAM)
}
