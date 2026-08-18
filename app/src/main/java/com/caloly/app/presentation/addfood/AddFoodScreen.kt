package com.caloly.app.presentation.addfood

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Image
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caloly.app.domain.model.Food
import com.caloly.app.domain.model.FoodUnit
import com.caloly.app.domain.model.MealType
import com.caloly.app.domain.model.DetectedFood
import com.caloly.app.domain.model.NutritionSource
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
import kotlin.math.roundToInt

private const val AI_FOOD_NOT_FOUND = "Yemek bulunamadı"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFoodScreen(
    dateKey: String,
    onBack: () -> Unit,
    viewModel: AddFoodViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAiOptions by remember { mutableStateOf(false) }
    var showManualAiEntry by remember { mutableStateOf(false) }
    var showAiCamera by remember { mutableStateOf(false) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            showAiCamera = false
            viewModel.analyzePhoto(it.toString())
        }
    }
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
            .addOnFailureListener {
                viewModel.onScannerError("Barkod tarayıcı açılamadı.")
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

            val currentAiAnalysis = state.aiAnalysis
            if (currentAiAnalysis != null) {
                item {
                    AiMealReview(
                        analysis = currentAiAnalysis,
                        isSaving = state.isAiSaving,
                        onConfirm = { viewModel.confirmAiMeal(onBack) },
                        onReject = viewModel::dismissAiFlow,
                        onUpdate = viewModel::updateDetectedFood,
                        onRemove = viewModel::removeDetectedFood,
                        onManualEntry = { showManualAiEntry = true },
                    )
                }
            } else if (state.isAiLoading) {
                item { AiLoadingCard() }
            } else if (state.selectedFood == null) {
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
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { viewModel.searchOnline() },
                            enabled = !state.isLoading,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CalolyGreen,
                                contentColor = CalolyLavenderWhite,
                            ),
                        ) {
                            if (state.isOnlineLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
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

                item {
                    Button(
                        onClick = { showAiOptions = true },
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CalolyLavender,
                            contentColor = CalolyLavenderWhite,
                        ),
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Column {
                            Text("Yapay Zekâ", fontWeight = FontWeight.ExtraBold)
                            Text("Kamerayla yemeği tanı", fontSize = 10.sp)
                        }
                    }
                }

                state.aiErrorMessage?.takeUnless { it == AI_FOOD_NOT_FOUND }?.let { message ->
                    item {
                        AiErrorCard(
                            message = message,
                            canRetry = state.lastAiPhotoUri != null,
                            onRetry = viewModel::retryPhotoAnalysis,
                            onDismiss = viewModel::dismissAiFlow,
                        )
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(
                                if (state.query.isBlank()) "Favoriler, son kullanılanlar ve hızlı ekle" else "Eşleşmeler",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                            )
                            Text(
                                if (state.hasSearchedOnline) {
                                    "${state.visibleResults.size} ürün · Sayfa ${state.onlinePage}/${state.totalOnlinePages}"
                                } else {
                                    "${state.visibleResults.size} ürün"
                                },
                                color = CalolyMuted,
                                fontSize = 11.sp,
                            )
                        }
                        if (state.isLocalLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = CalolyLavender)
                    }
                }
                items(state.visibleResults, key = { "result:${it.barcode ?: it.id}" }) { food ->
                    FoodResultCard(
                        food = food,
                        isFavorite = food.id in state.favoriteIds,
                        onFavorite = { viewModel.toggleFavorite(food) },
                        onClick = { viewModel.onFoodSelected(food) },
                    )
                }
                if (state.hasSearchedOnline && state.totalOnlinePages > 1) {
                    item {
                        SearchPagination(
                            currentPage = state.onlinePage,
                            totalPages = state.totalOnlinePages,
                            enabled = !state.isOnlineLoading,
                            onPageSelected = viewModel::searchOnline,
                        )
                    }
                }
                if (state.hasSearchedOnline && state.visibleResults.isNotEmpty()) {
                    item {
                        Text(
                            "Besin değerleri ürünün porsiyonuna ve hazırlanışına göre değişebilir. Kaydetmeden önce miktarı kontrol et.",
                            color = CalolyMuted,
                            fontSize = 10.sp,
                        )
                    }
                }
                if (state.allResultsEmpty && !state.isLocalLoading && state.errorMessage == null) {
                    item {
                        Text(
                            "Ürün bulunamadı.",
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

    if (showAiCamera) {
        AiCameraScreen(
            onClose = { showAiCamera = false },
            onPhotoCaptured = { uri ->
                showAiCamera = false
                viewModel.analyzePhoto(uri.toString())
            },
            onOpenGallery = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
        return
    }

    state.missingBarcode?.let { barcode ->
        ManualFoodDialog(
            barcode = barcode,
            onDismiss = viewModel::dismissManualFood,
            onSave = viewModel::createCustomFood,
        )
    }

    if (state.aiErrorMessage == AI_FOOD_NOT_FOUND) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAiFlow,
            title = { Text(AI_FOOD_NOT_FOUND, fontWeight = FontWeight.ExtraBold) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissAiFlow) {
                    Text("Tamam")
                }
            },
        )
    }

    if (showAiOptions) {
        AiOptionsDialog(
            onDismiss = { showAiOptions = false },
            onCamera = {
                showAiOptions = false
                showAiCamera = true
            },
            onGallery = {
                showAiOptions = false
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onManual = {
                showAiOptions = false
                showManualAiEntry = true
            },
        )
    }

    if (showManualAiEntry) {
        ManualAiMealDialog(
            onDismiss = { showManualAiEntry = false },
            onCalculate = { description ->
                showManualAiEntry = false
                viewModel.analyzeDescriptionAndSave(description, onBack)
            },
        )
    }
}

@Composable
private fun AiOptionsDialog(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onManual: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = CalolySurface),
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = CalolyLavender, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.size(10.dp))
                    Column {
                        Text("Yapay Zekâ ile Tanı", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Gemini 3.5 Flash-Lite", color = CalolyMuted, fontSize = 11.sp)
                    }
                }
                Text(
                    "Fotoğraf analiz için Google Gemini hizmetine gönderilir; Caloly fotoğrafı kalıcı olarak saklamaz.",
                    color = CalolyMuted,
                    fontSize = 12.sp,
                )
                Text("Uyarı : Kota Saat Başı 3 keredir", color = CalolyPink, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onCamera,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = CalolyLavender),
                ) {
                    Icon(Icons.Rounded.CameraAlt, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Kamerayı Aç", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(50)) {
                    Icon(Icons.Rounded.Image, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Galeriden Seç")
                }
                HorizontalDivider(color = CalolyMuted.copy(alpha = .25f))
                TextButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                    Text("Yazarak oto hesapla", color = CalolyGreen, fontWeight = FontWeight.ExtraBold)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Vazgeç", color = CalolyMuted) }
            }
        }
    }
}

@Composable
private fun AiLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight.copy(alpha = .55f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(color = CalolyLavender)
            Text("Caloly yemeğini analiz ediyor…", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text("Yiyecekler ve tahmini porsiyonlar ayrı ayrı hesaplanıyor.", color = CalolyMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AiErrorCard(message: String, canRetry: Boolean, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CalolyPink.copy(alpha = .12f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(message, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canRetry) TextButton(onClick = onRetry) { Text("Tekrar Dene") }
                TextButton(onClick = onDismiss) { Text("Kapat", color = CalolyMuted) }
            }
        }
    }
}

@Composable
private fun AiMealReview(
    analysis: com.caloly.app.domain.model.AiMealAnalysis,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onUpdate: (Int, DetectedFood) -> Unit,
    onRemove: (Int) -> Unit,
    onManualEntry: () -> Unit,
) {
    var editingIndex by remember(analysis.foods) { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight.copy(alpha = .55f)),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = CalolyLavender)
                    Spacer(Modifier.size(8.dp))
                    Text("Caloly AI", color = CalolyLavender, fontWeight = FontWeight.ExtraBold)
                }
                Text(analysis.confirmationQuestion, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                analysis.remainingPhotoScans?.let {
                    Text("Bu saat içinde kalan fotoğraf hakkı: $it", color = CalolyMuted, fontSize = 11.sp)
                }
            }
        }

        analysis.foods.forEachIndexed { index, food ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CalolySurface),
            ) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(food.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                        food.brand?.let { Text(it, color = CalolyLavender, fontSize = 12.sp) }
                        Text("${food.displayGrams}  •  ${food.displayCalories}", color = CalolyMuted)
                        Text(
                            "${food.proteinGrams.roundToInt()}P · ${food.carbsGrams.roundToInt()}K · ${food.fatGrams.roundToInt()}Y",
                            color = CalolyGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            when (food.nutritionSource) {
                                NutritionSource.VERIFIED_DATABASE -> "Doğrulanmış besin verisi"
                                NutritionSource.USER_EDITED -> "Senin düzenlediğin değer"
                                NutritionSource.AI_ESTIMATE -> "Yapay zekâ tahmini"
                            },
                            color = CalolyMuted,
                            fontSize = 10.sp,
                        )
                    }
                    IconButton(onClick = { editingIndex = index }) { Icon(Icons.Rounded.Edit, "Düzenle", tint = CalolyLavender) }
                    IconButton(onClick = { onRemove(index) }) { Icon(Icons.Rounded.DeleteOutline, "Çıkar", tint = CalolyPink) }
                }
            }
        }

        val totalCalories = analysis.foods.sumOf(DetectedFood::calories).roundToInt()
        val totalProtein = analysis.foods.sumOf(DetectedFood::proteinGrams).roundToInt()
        val totalCarbs = analysis.foods.sumOf(DetectedFood::carbsGrams).roundToInt()
        val totalFat = analysis.foods.sumOf(DetectedFood::fatGrams).roundToInt()
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CalolyLavenderLight.copy(alpha = .45f)),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Tahmini toplam: $totalCalories kcal", color = CalolyLavender, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("$totalProtein g protein · $totalCarbs g karbonhidrat · $totalFat g yağ", color = CalolyMuted, fontSize = 12.sp)
            }
        }

        Text(
            "Tek fotoğraftan porsiyon kesin ölçülemez. Kaydetmeden önce tahminleri kontrol edip düzenleyebilirsin.",
            color = CalolyMuted,
            fontSize = 11.sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(50)) {
                Icon(Icons.Rounded.Close, null, tint = CalolyPink)
                Spacer(Modifier.size(6.dp))
                Text("Hayır", color = CalolyPink)
            }
            Button(
                onClick = onConfirm,
                enabled = analysis.foods.isNotEmpty() && !isSaving,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = CalolyGreen),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                else {
                    Icon(Icons.Rounded.Check, null)
                    Spacer(Modifier.size(6.dp))
                    Text("Evet, Ekle", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        TextButton(onClick = onManualEntry, modifier = Modifier.fillMaxWidth()) {
            Text("Yazarak oto hesapla", color = CalolyGreen, fontWeight = FontWeight.ExtraBold)
        }
    }

    editingIndex?.let { index ->
        analysis.foods.getOrNull(index)?.let { food ->
            DetectedFoodEditDialog(
                food = food,
                onDismiss = { editingIndex = null },
                onSave = {
                    onUpdate(index, it)
                    editingIndex = null
                },
            )
        }
    }
}

@Composable
private fun DetectedFoodEditDialog(food: DetectedFood, onDismiss: () -> Unit, onSave: (DetectedFood) -> Unit) {
    var name by remember(food) { mutableStateOf(food.name) }
    var grams by remember(food) { mutableStateOf(food.estimatedGrams.roundToInt().toString()) }
    var calories by remember(food) { mutableStateOf(food.calories.roundToInt().toString()) }
    var protein by remember(food) { mutableStateOf(food.proteinGrams.roundToInt().toString()) }
    var carbs by remember(food) { mutableStateOf(food.carbsGrams.roundToInt().toString()) }
    var fat by remember(food) { mutableStateOf(food.fatGrams.roundToInt().toString()) }
    fun number(value: String) = value.replace(',', '.').toDoubleOrNull()
    val valid = name.isNotBlank() && number(grams) != null && number(calories) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yemeği Düzenle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Yemek adı") }, singleLine = true)
                OutlinedTextField(grams, { grams = it }, label = { Text("Tahmini gram") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(calories, { calories = it }, label = { Text("Kalori") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(protein, { protein = it }, label = { Text("Protein") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(carbs, { carbs = it }, label = { Text("Karb.") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(fat, { fat = it }, label = { Text("Yağ") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val newGrams = number(grams)!!.coerceAtLeast(1.0)
                    val newCalories = number(calories)!!.coerceAtLeast(0.0)
                    onSave(
                        food.copy(
                            name = name.trim(),
                            estimatedGrams = newGrams,
                            gramsMin = newGrams,
                            gramsMax = newGrams,
                            calories = newCalories,
                            caloriesMin = newCalories,
                            caloriesMax = newCalories,
                            proteinGrams = number(protein)?.coerceAtLeast(0.0) ?: 0.0,
                            carbsGrams = number(carbs)?.coerceAtLeast(0.0) ?: 0.0,
                            fatGrams = number(fat)?.coerceAtLeast(0.0) ?: 0.0,
                            nutritionSource = NutritionSource.USER_EDITED,
                        )
                    )
                },
            ) { Text("Kaydet") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun ManualAiMealDialog(onDismiss: () -> Unit, onCalculate: (String) -> Unit) {
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yazarak oto hesapla") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Örneğin: 1 tabak pilav üstü kuru fasulye ve 1 kâse yoğurt", color = CalolyMuted, fontSize = 12.sp)
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 600) description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Öğünü yaz") },
                    minLines = 3,
                )
                Text("Yazılı hesaplama kamera kotasını kullanmaz.", color = CalolyMuted, fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onCalculate(description) }, enabled = description.trim().length >= 3) { Text("Hesapla ve Ekle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
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
private fun SearchPagination(
    currentPage: Int,
    totalPages: Int,
    enabled: Boolean,
    onPageSelected: (Int) -> Unit,
) {
    val pages = paginationWindow(currentPage, totalPages)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Diğer sonuçlar", color = CalolyMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            TextButton(
                onClick = { onPageSelected(currentPage - 1) },
                enabled = enabled && currentPage > 1,
            ) { Text("Önceki") }

            pages.forEachIndexed { index, page ->
                if (index > 0 && page - pages[index - 1] > 1) {
                    Text("…", Modifier.padding(horizontal = 3.dp, vertical = 12.dp), color = CalolyMuted)
                }
                if (page == currentPage) {
                    Button(onClick = {}, enabled = false, shape = RoundedCornerShape(50)) { Text(page.toString()) }
                } else {
                    OutlinedButton(
                        onClick = { onPageSelected(page) },
                        enabled = enabled,
                        shape = RoundedCornerShape(50),
                    ) { Text(page.toString()) }
                }
            }

            TextButton(
                onClick = { onPageSelected(currentPage + 1) },
                enabled = enabled && currentPage < totalPages,
            ) { Text("Sonraki") }
        }
    }
}

internal fun paginationWindow(currentPage: Int, totalPages: Int): List<Int> {
    if (totalPages <= 1) return listOf(1)
    if (totalPages <= 5) return (1..totalPages).toList()
    return setOf(1, currentPage - 1, currentPage, currentPage + 1, totalPages)
        .filter { it in 1..totalPages }
        .sorted()
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
    FoodUnit.SERVING -> listOf(FoodUnit.SERVING)
    FoodUnit.PIECE -> listOf(FoodUnit.PIECE, FoodUnit.GRAM)
    FoodUnit.SLICE -> listOf(FoodUnit.SLICE, FoodUnit.GRAM)
    FoodUnit.PACKAGE -> listOf(FoodUnit.PACKAGE, FoodUnit.GRAM)
    FoodUnit.GRAM -> listOf(FoodUnit.GRAM)
}
