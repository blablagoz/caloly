package com.caloly.app.presentation.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.window.Dialog
import com.caloly.app.domain.auth.CalolyUser
import com.caloly.app.domain.model.*
import com.caloly.app.presentation.auth.AuthActionState
import com.caloly.app.presentation.social.SocialScreen
import com.caloly.app.presentation.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("Ana Sayfa", Icons.Rounded.Home), NUTRITION("Beslenme", Icons.Rounded.Restaurant),
    ACTIVITY("Aktivite", Icons.Rounded.DirectionsRun), SOCIAL("Takip", Icons.Rounded.Group),
    PROFILE("Profil", Icons.Rounded.Person),
}

private fun formatAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale.forLanguageTag("tr-TR"), "%.2f", value).trimEnd('0').trimEnd(',')

@Composable
fun MainShell(
    summary: DailySummary,
    selectedDate: LocalDate,
    loggedDates: Set<String>,
    templates: List<NutritionTemplate>,
    templateAction: TemplateActionState,
    logAction: LogActionState,
    healthState: HealthUiState,
    user: CalolyUser?,
    authAction: AuthActionState,
    onPreviousDate: () -> Unit,
    onNextDate: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onAddFood: () -> Unit,
    onSaveMeal: (String, MealType, Boolean) -> Unit,
    onSaveDay: (String, Boolean) -> Unit,
    onApplyTemplate: (String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onDeleteLog: (FoodLog) -> Unit,
    onDeleteMeal: (MealType) -> Unit,
    onDeleteDay: () -> Unit,
    onUndoDelete: () -> Unit,
    onUpdateLog: (FoodLog, MealType, Double) -> Unit,
    onConnectHealth: () -> Unit,
    onRefreshHealth: () -> Unit,
    onEditAccount: () -> Unit,
    onEditBody: () -> Unit,
    onSecurity: () -> Unit,
    onSharingSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(logAction.eventId) {
        val message = logAction.message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (logAction.undoAvailable) "Geri Al" else null,
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed && logAction.undoAvailable) onUndoDelete()
    }
    Scaffold(
        containerColor = CalolyBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0E111B), tonalElevation = 0.dp) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick = { selected = tab },
                        icon = { Icon(tab.icon, tab.label) },
                        label = { Text(tab.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CalolyLavenderWhite,
                            selectedTextColor = CalolyLavender,
                            indicatorColor = CalolyPurple.copy(alpha = .35f),
                            unselectedIconColor = CalolyMuted,
                            unselectedTextColor = CalolyMuted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selected) {
                MainTab.HOME -> Dashboard(summary, healthState, selectedDate, loggedDates, user, onPreviousDate, onNextDate, onSelectDate, onAddFood) { selected = MainTab.ACTIVITY }
                MainTab.NUTRITION -> NutritionScreen(summary, selectedDate, loggedDates, templates, templateAction, onPreviousDate, onNextDate, onSelectDate, onAddFood, onSaveMeal, onSaveDay, onApplyTemplate, onDeleteTemplate, onDeleteLog, onDeleteMeal, onDeleteDay, onUpdateLog)
                MainTab.ACTIVITY -> ActivityScreen(summary, healthState, user, onConnectHealth, onRefreshHealth)
                MainTab.SOCIAL -> SocialScreen(onBack = null)
                MainTab.PROFILE -> ProfileScreen(user, authAction, onEditAccount, onEditBody, onSecurity, onSharingSettings, onSignOut)
            }
        }
    }
}

@Composable
private fun Dashboard(
    summary: DailySummary,
    healthState: HealthUiState,
    date: LocalDate,
    loggedDates: Set<String>,
    user: CalolyUser?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onAddFood: () -> Unit,
    onActivity: () -> Unit,
) {
    var calendarOpen by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("CALOLY", fontSize = 12.sp, letterSpacing = 3.sp, color = CalolyLavender, fontWeight = FontWeight.Black)
                    Text("Merhaba, ${user?.displayName?.substringBefore(' ') ?: "sen"}", fontSize = 25.sp, color = CalolyText, fontWeight = FontWeight.ExtraBold)
                }
                GradientBadge(Icons.Rounded.AutoAwesome)
            }
        }
        item { DateNavigator(date, onPrevious, onNext) { calendarOpen = true } }
        item { DailyIntakeCard(summary) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactMetric("Kalori", "${summary.consumedCalories}", "kcal", CalolyPink, Modifier.weight(1f))
                CompactMetric("Protein", "${summary.proteinGrams}", "g", CalolyGreen, Modifier.weight(1f))
                CompactMetric("Karb.", "${summary.carbsGrams}", "g", CalolyBlue, Modifier.weight(1f))
                CompactMetric("Yağ", "${summary.fatGrams}", "g", CalolyOrange, Modifier.weight(1f))
            }
        }
        item {
            Button(onClick = onAddFood, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = CalolyPurple)) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Yemek Ekle", fontWeight = FontWeight.Bold)
            }
        }
        item { SectionLabel("Makro dağılımı", "Bugün yenilenler") }
        item { MacroCard(summary) }
        item { SectionLabel("Günlük aktivite", "Detaylar", onActivity) }
        item { ActivityPreview(summary, healthState) }
        if (summary.logs.isNotEmpty()) {
            item { SectionLabel("Son öğünler", "") }
            items(summary.logs.take(3)) { MealRow(it.foodName, it.mealType.label, it.calories) }
        }
    }
    if (calendarOpen) CalendarDialog(date, loggedDates, { calendarOpen = false }) { onSelect(it); calendarOpen = false }
}

@Composable
private fun DailyIntakeCard(summary: DailySummary) = AppCard {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(164.dp), contentAlignment = Alignment.Center) {
            MacroRing(summary)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${summary.consumedCalories}", fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text("bugünkü kcal", color = CalolyMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            StatLine("Protein", "${summary.proteinGrams} g", CalolyGreen)
            StatLine("Karbonhidrat", "${summary.carbsGrams} g", CalolyBlue)
            StatLine("Yağ", "${summary.fatGrams} g", CalolyOrange)
        }
    }
}

@Composable
private fun MacroRing(summary: DailySummary) {
    val values = listOf(summary.proteinGrams * 4f, summary.carbsGrams * 4f, summary.fatGrams * 9f)
    val total = values.sum()
    Canvas(Modifier.fillMaxSize().padding(12.dp)) {
        val stroke = 13.dp.toPx(); val inset = stroke / 2
        drawArc(Color(0xFF292D3D), -90f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
        if (total > 0f) {
            var start = -90f
            values.zip(listOf(CalolyGreen, CalolyBlue, CalolyOrange)).forEach { (value, color) ->
                val sweep = 360f * value / total
                if (sweep > 1f) drawArc(color, start + 1.5f, (sweep - 3f).coerceAtLeast(.5f), false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
                start += sweep
            }
        }
    }
}

@Composable
private fun MacroCard(summary: DailySummary) = AppCard {
    val proteinEnergy = summary.proteinGrams * 4f
    val carbsEnergy = summary.carbsGrams * 4f
    val fatEnergy = summary.fatGrams * 9f
    val total = proteinEnergy + carbsEnergy + fatEnergy
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().height(11.dp).clip(CircleShape).background(Color(0xFF292D3D))) {
            if (total > 0f) {
                if (carbsEnergy > 0) Box(Modifier.weight(carbsEnergy).fillMaxHeight().background(CalolyBlue))
                if (proteinEnergy > 0) Box(Modifier.weight(proteinEnergy).fillMaxHeight().background(CalolyGreen))
                if (fatEnergy > 0) Box(Modifier.weight(fatEnergy).fillMaxHeight().background(CalolyOrange))
            }
        }
        if (total == 0f) Text("Henüz besin eklenmedi. Günlük dağılım yemek ekledikçe oluşur.", color = CalolyMuted)
        else {
            DistributionLine("Protein", summary.proteinGrams, proteinEnergy / total, CalolyGreen)
            DistributionLine("Karbonhidrat", summary.carbsGrams, carbsEnergy / total, CalolyBlue)
            DistributionLine("Yağ", summary.fatGrams, fatEnergy / total, CalolyOrange)
        }
    }
}

@Composable
private fun DistributionLine(label: String, grams: Int, ratio: Float, color: Color) = Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(8.dp)); Text(label, color = CalolyMuted) }
    Text("$grams g · ${(ratio * 100).toInt()}%", fontWeight = FontWeight.Bold)
}

private data class SaveRequest(val mealType: MealType?)

@Composable
private fun NutritionScreen(
    summary: DailySummary,
    date: LocalDate,
    loggedDates: Set<String>,
    templates: List<NutritionTemplate>,
    templateAction: TemplateActionState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onAdd: () -> Unit,
    onSaveMeal: (String, MealType, Boolean) -> Unit,
    onSaveDay: (String, Boolean) -> Unit,
    onApplyTemplate: (String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onDeleteLog: (FoodLog) -> Unit,
    onDeleteMeal: (MealType) -> Unit,
    onDeleteDay: () -> Unit,
    onUpdateLog: (FoodLog, MealType, Double) -> Unit,
) {
    var calendarOpen by remember { mutableStateOf(false) }
    var saveRequest by remember { mutableStateOf<SaveRequest?>(null) }
    var editingLog by remember { mutableStateOf<FoodLog?>(null) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { PageHeader("Beslenme", "Seçili günün kayıtları"); GradientBadge(Icons.Rounded.Restaurant) } }
        item { DateNavigator(date, onPrevious, onNext) { calendarOpen = true } }
        item { MacroCard(summary) }
        MealType.entries.forEach { meal ->
            val logs = summary.logs.filter { it.mealType == meal }
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(meal.label, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Row {
                        if (logs.isNotEmpty()) TextButton(onClick = { saveRequest = SaveRequest(meal) }) { Text("Öğünü Kaydet", color = CalolyLavender) }
                        if (logs.isNotEmpty()) IconButton(onClick = { onDeleteMeal(meal) }) { Icon(Icons.Rounded.DeleteSweep, "Öğünü sil", tint = CalolyMuted) }
                        TextButton(onClick = onAdd) { Text("+ Ekle", color = CalolyGreen) }
                    }
                }
            }
            if (logs.isEmpty()) item { EmptyMealRow() }
            else items(logs, key = { it.id }) { log ->
                EditableFoodLogRow(log, onEdit = { editingLog = log }, onDelete = { onDeleteLog(log) })
            }
        }
        if (summary.logs.isNotEmpty()) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { saveRequest = SaveRequest(null) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Rounded.BookmarkAdd, null); Spacer(Modifier.width(8.dp)); Text("Günü Kaydet")
                }
                OutlinedButton(onClick = onDeleteDay, shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Rounded.DeleteSweep, "Günü sil")
                }
            }
        }
        item { SectionLabel("Kayıtlı öğünler ve günler", "${templates.size} şablon") }
        if (templates.isEmpty()) item { Text("Henüz kayıtlı bir öğün veya gün yok.", color = CalolyMuted) }
        else items(templates, key = { it.id }) { template -> TemplateCard(template, onApplyTemplate, onDeleteTemplate) }
        templateAction.message?.let { item { Text(it, color = CalolyGreen) } }
    }
    if (calendarOpen) CalendarDialog(date, loggedDates, { calendarOpen = false }) { onSelect(it); calendarOpen = false }
    saveRequest?.let { request ->
        SaveTemplateDialog(
            defaultName = request.mealType?.let { "${it.label} şablonum" } ?: "Günlük beslenme şablonum",
            onDismiss = { saveRequest = null },
        ) { name, share ->
            request.mealType?.let { onSaveMeal(name, it, share) } ?: onSaveDay(name, share)
            saveRequest = null
        }
    }
    editingLog?.let { log ->
        EditFoodLogDialog(
            log = log,
            onDismiss = { editingLog = null },
            onSave = { mealType, amount -> onUpdateLog(log, mealType, amount); editingLog = null },
        )
    }
}

@Composable
private fun EditableFoodLogRow(log: FoodLog, onEdit: () -> Unit, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sil", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Delete, "Sil", tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CalolySurface),
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = CalolyLavenderLight) {
                    Icon(Icons.Rounded.Restaurant, null, tint = CalolyLavender, modifier = Modifier.padding(12.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(log.foodName, fontWeight = FontWeight.Bold)
                    Text("${formatAmount(log.amount)} ${log.unit.label} · Düzenlemek için dokun", color = CalolyMuted, fontSize = 12.sp)
                }
                Text("${log.calories} kcal", color = CalolyGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EditFoodLogDialog(log: FoodLog, onDismiss: () -> Unit, onSave: (MealType, Double) -> Unit) {
    var amountText by remember(log.id) { mutableStateOf(formatAmount(log.amount)) }
    var mealType by remember(log.id) { mutableStateOf(log.mealType) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(log.foodName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { value -> if (value.matches(Regex("^\\d{0,5}([.,]\\d{0,2})?$"))) amountText = value },
                    label = { Text("Miktar (${log.unit.label})") },
                    singleLine = true,
                )
                Text("Öğün", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealType.entries.forEach { meal ->
                        FilterChip(selected = mealType == meal, onClick = { mealType = meal }, label = { Text(meal.label) })
                    }
                }
            }
        },
        confirmButton = {
            val amount = amountText.replace(',', '.').toDoubleOrNull()
            TextButton(onClick = { if (amount != null) onSave(mealType, amount) }, enabled = amount != null && amount > 0) { Text("Güncelle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun DateNavigator(date: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit, onCalendar: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = CalolySurfaceHigh) {
        Row(Modifier.fillMaxWidth().padding(8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) { Icon(Icons.Rounded.ChevronLeft, "Önceki gün", tint = CalolyMuted) }
            Column(Modifier.clickable(onClick = onCalendar).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr-TR"))), fontWeight = FontWeight.Bold)
                Text(if (date == LocalDate.now()) "Bugün" else "Takvimi aç", color = CalolyLavender, fontSize = 11.sp)
            }
            IconButton(onClick = onNext) { Icon(Icons.Rounded.ChevronRight, "Sonraki gün", tint = CalolyMuted) }
        }
    }
}

@Composable
private fun CalendarDialog(selected: LocalDate, loggedDates: Set<String>, onDismiss: () -> Unit, onSelect: (LocalDate) -> Unit) {
    val firstYear = 1900
    val lastYear = 2100
    val pageCount = (lastYear - firstYear + 1) * 12
    val initialMonth = YearMonth.from(selected)
    val initialPage = ((initialMonth.year - firstYear) * 12 + initialMonth.monthValue - 1).coerceIn(0, pageCount - 1)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val visibleMonth by remember {
        derivedStateOf {
            val page = pagerState.currentPage
            YearMonth.of(firstYear + page / 12, page % 12 + 1)
        }
    }
    var pickerMode by remember { mutableStateOf<CalendarPickerMode?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = CalolySurface) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
                        enabled = pagerState.currentPage > 0,
                    ) { Icon(Icons.Rounded.ChevronLeft, "Önceki ay") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { pickerMode = CalendarPickerMode.MONTH }) {
                            Text(visibleMonth.atDay(1).format(DateTimeFormatter.ofPattern("MMMM", Locale.forLanguageTag("tr-TR"))), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                        TextButton(onClick = { pickerMode = CalendarPickerMode.YEAR }) {
                            Text("${visibleMonth.year}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        }
                    }
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pageCount - 1)) } },
                        enabled = pagerState.currentPage < pageCount - 1,
                    ) { Icon(Icons.Rounded.ChevronRight, "Sonraki ay") }
                }
                Row(Modifier.fillMaxWidth()) { listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz").forEach { Text(it, Modifier.weight(1f), color = CalolyMuted, fontSize = 11.sp) } }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(330.dp)) { page ->
                    CalendarMonthGrid(
                        month = YearMonth.of(firstYear + page / 12, page % 12 + 1),
                        selected = selected,
                        loggedDates = loggedDates,
                        onSelect = onSelect,
                    )
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Aylar arasında kaydırabilirsin", color = CalolyMuted, fontSize = 11.sp)
                    TextButton(onClick = { onSelect(LocalDate.now()) }) { Text("Bugüne Git") }
                }
            }
        }
    }
    pickerMode?.let { mode ->
        CalendarMonthYearPicker(
            mode = mode,
            month = visibleMonth,
            firstYear = firstYear,
            lastYear = lastYear,
            onDismiss = { pickerMode = null },
        ) { chosen ->
            val page = (chosen.year - firstYear) * 12 + chosen.monthValue - 1
            scope.launch { pagerState.scrollToPage(page) }
            pickerMode = null
        }
    }
}

private enum class CalendarPickerMode { MONTH, YEAR }

@Composable
private fun CalendarMonthGrid(month: YearMonth, selected: LocalDate, loggedDates: Set<String>, onSelect: (LocalDate) -> Unit) {
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cells = leading + month.lengthOfMonth()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val day = week * 7 + column - leading + 1
                    if (week * 7 + column >= cells || day !in 1..month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(day)
                        val isSelected = date == selected
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                .background(if (isSelected) CalolyPurple else Color.Transparent)
                                .clickable { onSelect(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$day", fontWeight = if (date == LocalDate.now() || isSelected) FontWeight.Bold else FontWeight.Normal)
                                if (date.toString() in loggedDates) {
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (isSelected) CalolyGreen else CalolyLavender))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthYearPicker(
    mode: CalendarPickerMode,
    month: YearMonth,
    firstYear: Int,
    lastYear: Int,
    onDismiss: () -> Unit,
    onSelect: (YearMonth) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = CalolySurface) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (mode == CalendarPickerMode.MONTH) "Ay seç" else "Yıl seç", fontSize = 21.sp, fontWeight = FontWeight.Black)
                if (mode == CalendarPickerMode.MONTH) {
                    val monthFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.forLanguageTag("tr-TR"))
                    repeat(4) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) { column ->
                                val monthNumber = row * 3 + column + 1
                                val candidate = month.withMonth(monthNumber)
                                FilterChip(
                                    selected = month.monthValue == monthNumber,
                                    onClick = { onSelect(candidate) },
                                    label = { Text(candidate.atDay(1).format(monthFormatter).replaceFirstChar { it.uppercase() }, maxLines = 1) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                } else {
                    val listState = rememberLazyListState((month.year - firstYear - 3).coerceAtLeast(0))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp), state = listState) {
                        items((firstYear..lastYear).toList()) { year ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onSelect(month.withYear(year)) }.padding(15.dp),
                                Arrangement.SpaceBetween,
                                Alignment.CenterVertically,
                            ) {
                                Text("$year", fontSize = 18.sp, fontWeight = if (year == month.year) FontWeight.Black else FontWeight.Normal)
                                if (year == month.year) Icon(Icons.Rounded.Check, "Seçili yıl", tint = CalolyGreen)
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Vazgeç") }
            }
        }
    }
}

@Composable
private fun SaveTemplateDialog(defaultName: String, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    var share by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daha sonra kullan") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Şablon adı") }, singleLine = true)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Arkadaşlarımla paylaş"); Text("Yalnız yemek detaylarına izin verdiğin kişiler görebilir.", color = CalolyMuted, fontSize = 11.sp) }; Switch(share, { share = it }) }
        } },
        confirmButton = { TextButton(onClick = { onSave(name, share) }, enabled = name.isNotBlank()) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

@Composable
private fun TemplateCard(template: NutritionTemplate, onApply: (String) -> Unit, onDelete: (String) -> Unit) = AppCard {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (template.kind == TemplateKind.DAY) Icons.Rounded.CalendarMonth else Icons.Rounded.Restaurant, null, tint = CalolyLavender)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(template.name, fontWeight = FontWeight.Bold); Text("${template.items.size} kalem · ${template.totalCalories} kcal${template.sourceOwnerName?.let { " · $it" }.orEmpty()}", color = CalolyMuted, fontSize = 12.sp) }
        IconButton(onClick = { onApply(template.id) }) { Icon(Icons.Rounded.AddCircle, "Seçili güne ekle", tint = CalolyGreen) }
        IconButton(onClick = { onDelete(template.id) }) { Icon(Icons.Rounded.DeleteOutline, "Sil", tint = CalolyMuted) }
    }
}

@Composable
private fun ActivityScreen(summary: DailySummary, state: HealthUiState, user: CalolyUser?, onConnect: () -> Unit, onRefresh: () -> Unit) {
    val estimatedBasal = calculateBasalMetabolism(user?.birthDate, user?.heightCm, user?.weightKg, user?.gender)
    val basalValue = state.health.basalCalories?.let { "$it kcal" } ?: estimatedBasal?.displayValue ?: "Bilgi gerekli"
    val basalSource = when {
        state.health.basalCalories != null -> "Health Connect"
        estimatedBasal != null -> if (estimatedBasal.isRange) "Tahmini aralık · cinsiyet belirtilmedi" else "Tahmini · vücut bilgilerinden"
        else -> "Profilde boy, kilo ve doğum tarihini ekle"
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { PageHeader("Aktivite", "Health Connect verilerin"); GradientBadge(Icons.Rounded.DirectionsRun) } }
        item { AppCard { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.DirectionsWalk, null, tint = CalolyGreen, modifier = Modifier.size(40.dp)); Text("%,d".format(summary.steps), fontSize = 42.sp, fontWeight = FontWeight.Black); Text("adım", color = CalolyMuted) } } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile("Aktif kalori", if (state.health.hasActiveCaloriesData) "${summary.activeCalories} kcal" else "Veri yok", Icons.Rounded.LocalFireDepartment, CalolyOrange, Modifier.weight(1f))
            MetricTile("Toplam enerji", if (state.health.hasTotalCaloriesData) "${summary.totalCaloriesBurned} kcal" else "Veri yok", Icons.Rounded.Bolt, CalolyPink, Modifier.weight(1f))
        } }
        item { AppCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MonitorHeart, null, tint = CalolyLavender, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bazal metabolizma", color = CalolyMuted, fontSize = 12.sp)
                    Text(basalValue, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(basalSource, color = CalolyLavender, fontSize = 11.sp)
                }
            }
        } }
        item { HealthCard(state, onConnect, onRefresh) }
    }
}

@Composable
private fun ProfileScreen(user: CalolyUser?, state: AuthActionState, onEdit: () -> Unit, onBody: () -> Unit, onSecurity: () -> Unit, onSharing: () -> Unit, onSignOut: () -> Unit) {
    val bmi = calculateBmi(user?.heightCm, user?.weightKg)
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader("Profil", "Hesap ve paylaşım ayarları") }
        item { AppCard { Row(verticalAlignment = Alignment.CenterVertically) { Surface(shape = CircleShape, color = CalolyPurple) { Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) { Text((user?.displayName ?: user?.username ?: "C").take(1).uppercase(), fontSize = 28.sp, fontWeight = FontWeight.Black) } }; Spacer(Modifier.width(15.dp)); Column { Text(user?.displayName ?: "Caloly kullanıcısı", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text(user?.email.orEmpty(), color = CalolyMuted); user?.username?.let { Text("@$it", color = CalolyLavender) } } } } }
        item { AppCard { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Column { Text("Vücut Kitle İndeksi", color = CalolyMuted); Text(bmi?.toString() ?: "Bilgi girilmedi", fontSize = 25.sp, fontWeight = FontWeight.Black, color = CalolyLavender) }; TextButton(onClick = onBody) { Text(if (bmi == null) "Bilgi Ekle" else "Güncelle") } } } }
        item { SettingsGroup("Hesap", listOf(SettingItem("Profil bilgilerini düzenle", Icons.Rounded.Edit, onEdit), SettingItem("Vücut bilgileri ve VKİ", Icons.Rounded.MonitorHeart, onBody), SettingItem("Şifre ve güvenlik", Icons.Rounded.Lock, onSecurity))) }
        item { SettingsGroup("Paylaşım", listOf(SettingItem("Hedef arkadaşı paylaşım izinleri", Icons.Rounded.Favorite, onSharing), SettingItem("Aktivite görünürlüğü", Icons.Rounded.Visibility, onSharing), SettingItem("Beslenme detayları", Icons.Rounded.Restaurant, onSharing))) }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item { OutlinedButton(onClick = onSignOut, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Rounded.Logout, null); Text(" Çıkış Yap") } }
    }
}

@Composable private fun ActivityPreview(summary: DailySummary, state: HealthUiState) = AppCard { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.DirectionsWalk, null, tint = CalolyGreen); Spacer(Modifier.width(10.dp)); Column { Text("Günlük adım", color = CalolyMuted); Text("%,d".format(summary.steps), fontWeight = FontWeight.ExtraBold, fontSize = 21.sp) } }; Text(if (state.health.hasActiveCaloriesData) "${summary.activeCalories} aktif kcal" else "Aktif kalori verisi yok", color = CalolyOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp) } }

@Composable private fun HealthCard(state: HealthUiState, onConnect: () -> Unit, onRefresh: () -> Unit) = AppCard { when { state.availability != HealthConnectAvailability.AVAILABLE -> Text("Health Connect bu cihazda kullanılamıyor.", color = CalolyMuted); !state.hasPermissions -> Button(onClick = onConnect, colors = ButtonDefaults.buttonColors(containerColor = CalolyGreen)) { Text("Health Connect'e Bağlan") }; else -> Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Column { Text("Health Connect bağlı", color = CalolyGreen, fontWeight = FontWeight.Bold); state.lastUpdatedAt?.let { Text("Son yenileme: ${java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}", color = CalolyMuted, fontSize = 11.sp) } }; IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Yenile") } } } }
@Composable private fun EmptyMealRow() = Surface(shape = RoundedCornerShape(20.dp), color = CalolySurface) { Text("Bu öğüne henüz besin eklenmedi.", Modifier.fillMaxWidth().padding(16.dp), color = CalolyMuted) }
@Composable private fun PageHeader(title: String, subtitle: String) = Column { Text(title, fontSize = 28.sp, fontWeight = FontWeight.Black, color = CalolyText); Text(subtitle, color = CalolyMuted, fontSize = 14.sp) }
private data class SettingItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)
@Composable private fun SettingsGroup(title: String, rows: List<SettingItem>) = AppCard { Column { Text(title.uppercase(), color = CalolyLavender, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold); rows.forEach { item -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = item.onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(item.icon, null, tint = CalolyMuted); Spacer(Modifier.width(12.dp)); Text(item.label, Modifier.weight(1f)); Icon(Icons.Rounded.ChevronRight, null, tint = CalolyMuted) } } } }
@Composable private fun AppCard(content: @Composable ColumnScope.() -> Unit) = Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = CalolySurface), border = CardDefaults.outlinedCardBorder()) { Column(Modifier.padding(18.dp), content = content) }
@Composable private fun GradientBadge(icon: ImageVector) = Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(CalolyPurple, CalolyPink))), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White) }
@Composable private fun SectionLabel(title: String, action: String, onClick: () -> Unit = {}) = Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold); if (action.isNotBlank()) TextButton(onClick = onClick) { Text(action, color = CalolyLavender) } }
@Composable private fun StatLine(label: String, value: String, color: Color) = Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(9.dp)); Column { Text(label, color = CalolyMuted, fontSize = 12.sp); Text(value, fontWeight = FontWeight.Bold) } }
@Composable private fun MealRow(name: String, meal: String, calories: Int) = Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CalolySurface)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Surface(shape = RoundedCornerShape(14.dp), color = CalolyLavenderLight) { Icon(Icons.Rounded.Restaurant, null, tint = CalolyLavender, modifier = Modifier.padding(12.dp)) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(meal, color = CalolyMuted, fontSize = 12.sp) }; Text("$calories kcal", color = CalolyGreen, fontWeight = FontWeight.Bold) } }
@Composable private fun MetricTile(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) = Card(modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = CalolySurface)) { Column(Modifier.padding(17.dp)) { Icon(icon, null, tint = color); Spacer(Modifier.height(10.dp)); Text(title, color = CalolyMuted, fontSize = 12.sp); Text(value, fontWeight = FontWeight.ExtraBold) } }
@Composable private fun CompactMetric(title: String, value: String, unit: String, color: Color, modifier: Modifier) = Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = CalolySurface)) { Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(6.dp).clip(CircleShape).background(color)); Spacer(Modifier.height(6.dp)); Text(title, color = CalolyMuted, fontSize = 9.sp, maxLines = 1); Text(value, fontWeight = FontWeight.Black, fontSize = 14.sp, maxLines = 1); Text(unit, color = CalolyMuted, fontSize = 9.sp) } }
