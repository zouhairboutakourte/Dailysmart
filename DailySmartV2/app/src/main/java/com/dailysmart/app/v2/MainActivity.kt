package com.dailysmart.app.v2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.InputStream
import kotlinx.coroutines.launch

private val Navy = Color(0xFFF4F6FC)
private val Panel = Color(0xFFFFFFFF)
private val PanelRaised = Color(0xFFEDF1FF)
private val Accent = Color(0xFF4F46E5)
private val Lavender = Color(0xFF8B5CF6)
private val Gold = Color(0xFFF59E0B)
private val ExpenseColor = Color(0xFFE85D75)
private val Muted = Color(0xFF64748B)
private val White = Color(0xFF182235)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DailySmartApp() }
    }
}

data class TaskItem(val title: String, val done: Boolean = false)
data class MoneyItem(val title: String, val amount: Double, val income: Boolean, val createdAt: Long = System.currentTimeMillis())
data class HabitItem(val title: String, val completedToday: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySmartApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val geminiConfigured = remember { GeminiAssistant.initialize(context) }
    var geminiLoading by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("dailysmart_v2", Context.MODE_PRIVATE) }
    val database = remember {
        LocalDatabase(context).also { db ->
            db.migrateLegacyOnce(
                loadTasks(prefs), loadMoney(prefs), loadHabits(prefs),
                prefs.getString("habits_date", "") ?: ""
            )
        }
    }
    DisposableEffect(database) {
        onDispose { database.close() }
    }
    var tasks by remember { mutableStateOf(database.loadTasks()) }
    var money by remember { mutableStateOf(database.loadMoney()) }
    var habits by remember { mutableStateOf(database.loadHabitsForToday(todayKey())) }
    var newHabit by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("Assistant") }
    var language by remember { mutableStateOf(prefs.getString("language", "ar") ?: "ar") }
    fun changeLanguage(code: String) {
        language = code
        prefs.edit().putString("language", code).apply()
    }
    var command by remember { mutableStateOf("") }
    var newTitle by remember { mutableStateOf("") }
    var newAmount by remember { mutableStateOf("") }
    var info by remember { mutableStateOf(uiText(language, "Your personal workspace is ready.", "Votre espace est prêt.", "مساحتك الشخصية واجدة.")) }
    var pendingRestore by remember { mutableStateOf<BackupCodec.Snapshot?>(null) }
    var pendingAssistantAction by remember { mutableStateOf<AssistantAction?>(null) }
    var reminders by remember { mutableStateOf(ReminderManager.load(context)) }
    var reminderTitle by remember { mutableStateOf("") }
    var reminderDate by remember { mutableStateOf(LocalDate.now()) }
    val defaultReminderTime = remember { LocalTime.now().plusMinutes(30) }
    var reminderHour by remember { mutableIntStateOf(defaultReminderTime.hour) }
    var reminderMinute by remember { mutableIntStateOf(defaultReminderTime.minute) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingReminder by remember { mutableStateOf<ReminderItem?>(null) }
    var reminderMessage by remember { mutableStateOf("") }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    val timePickerState = rememberTimePickerState(
        initialHour = defaultReminderTime.hour,
        initialMinute = defaultReminderTime.minute,
        is24Hour = true
    )
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val item = pendingReminder
        if (item != null) {
            if (granted) {
                ReminderManager.schedule(context, item)
                reminders = ReminderManager.load(context)
                reminderMessage = uiText(language, "Reminder scheduled.", "Rappel programmé.", "تسجل التذكير.")
                info = reminderMessage
            } else {
                reminderTitle = item.title
                reminderMessage = uiText(language, "Allow notifications to receive reminders.", "Autorisez les notifications pour recevoir les rappels.", "سمح بالإشعارات باش توصلك التذكيرات.")
                info = reminderMessage
            }
            pendingReminder = null
        }
    }
    fun scheduleReminder(item: ReminderItem) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingReminder = item
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ReminderManager.schedule(context, item)
            reminders = ReminderManager.load(context)
            reminderMessage = uiText(language, "Reminder scheduled. Android may deliver it a little later to save battery.", "Rappel programmé. Android peut le livrer avec un léger retard pour économiser la batterie.", "تسجل التذكير. ممكن أندرويد يأخر الإشعار شوية لتوفير البطارية.")
            info = reminderMessage
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            val bytes = BackupCodec.encode(tasks, money, habits)
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("Could not create backup file.")
            info = "Backup created successfully."
        }.onFailure { info = "Backup failed: ${it.localizedMessage}" }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { readBackupBounded(it) }
                ?: throw IllegalStateException("Could not open selected file.")
            pendingRestore = BackupCodec.decode(bytes)
            info = "Backup checked. Confirm restore to replace current records."
        }.onFailure { info = "Restore failed: ${it.localizedMessage}" }
    }

    fun saveTasks() { database.replaceTasks(tasks) }
    fun saveMoney() { database.replaceMoney(money) }
    fun saveHabits() { database.replaceHabits(habits, todayKey()) }
    fun showFinanceReport() {
        val totalIncome = money.filter { it.income }.sumOf { it.amount }
        val totalExpense = money.filterNot { it.income }.sumOf { it.amount }
        val balance = totalIncome - totalExpense
        info = "${uiText(language, "Finance report", "Rapport financier", "التقرير المالي")} · ${uiText(language, "Income", "Revenu", "المداخيل")}: ${"%.2f".format(totalIncome)} MAD · ${uiText(language, "Expenses", "Dépenses", "المصاريف")}: ${"%.2f".format(totalExpense)} MAD · ${uiText(language, "Balance", "Solde", "الرصيد")}: ${"%.2f".format(balance)} MAD"
    }
    LaunchedEffect(Unit) {
        ReminderManager.rescheduleAll(context)
    }
    LaunchedEffect(tab) {
        if (tab == "Reminders") reminders = ReminderManager.load(context)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
                out.appendLine("type,description,amount,currency,date")
                money.forEach { out.appendLine("${if (it.income) "income" else "expense"},\"${it.title.replace("\"", "\"\"")}\",${it.amount},MAD,${FinanceAnalytics.dateString(it.createdAt)}") }
            }
            info = "CSV export created."
        }.onFailure { info = "Export failed: ${it.localizedMessage}" }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val csvText = context.contentResolver.openInputStream(uri)?.use { readBoundedText(it, 5 * 1024 * 1024, "CSV") } ?: throw IllegalStateException("Could not open CSV file.")
            val rows = parseCsv(csvText).drop(1)
            val imported = rows.mapNotNull { p ->
                if (p.size >= 3) {
                    val amount = p[2].trim().toDoubleOrNull() ?: return@mapNotNull null
                    if (!amount.isFinite() || amount <= 0.0) return@mapNotNull null
                    run {
                        val income = listOf("income", "revenu", "recette", "دخل").any { p[0].contains(it, true) }
                        val description = p[1].trim().trim('"').ifBlank { if (income) "Income" else "Expense" }
                        if (description.length > 500) return@mapNotNull null
                        MoneyItem(description, amount, income, FinanceAnalytics.timestampFromDate(p.getOrNull(4).orEmpty()))
                    }
                } else null
            }
            money = money + imported
            saveMoney()
            info = "${imported.size} CSV rows imported."
        }.onFailure { info = "Could not read CSV file." }
    }

    val xlsxExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(ExcelWorkbook.exportFinance(money)) }
            info = "Excel workbook exported successfully."
        }.onFailure { info = "XLSX export failed: ${it.localizedMessage}" }
    }
    val xlsxImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { ExcelWorkbook.readBounded(it) }
            val imported = ExcelWorkbook.importFinance(bytes)
            money = money + imported
            saveMoney()
            info = "${imported.size} Excel rows imported."
        }.onFailure { info = "Could not import workbook: ${it.localizedMessage}" }
    }

    MaterialTheme(colorScheme = lightColorScheme(background = Navy, surface = Panel, primary = Accent, secondary = Lavender, onBackground = White, onSurface = White)) {
      CompositionLocalProvider(LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
        pendingAssistantAction?.let { action ->
        val actionTitle = when (action) {
            is AssistantAction.AddTask -> uiText(language, "Confirm new task", "Confirmer la tâche", "تأكيد المهمة الجديدة")
            is AssistantAction.AddMoney -> uiText(language, "Confirm finance record", "Confirmer l’opération", "تأكيد العملية المالية")
            is AssistantAction.AddReminder -> uiText(language, "Confirm reminder", "Confirmer le rappel", "تأكيد التذكير")
            else -> ""
        }
        val actionMessage = when (action) {
            is AssistantAction.AddTask -> "${uiText(language, "Add this task?", "Ajouter cette tâche ?", "واش نضيف هاد المهمة؟")}\n${action.title}"
            is AssistantAction.AddMoney -> "${uiText(language, if (action.income) "Record this income?" else "Record this expense?", if (action.income) "Enregistrer ce revenu ?" else "Enregistrer cette dépense ?", if (action.income) "واش نسجل هاد المدخول؟" else "واش نسجل هاد المصروف؟")}\n${action.amount} MAD · ${action.description}"
            is AssistantAction.AddReminder -> "${uiText(language, "Schedule this reminder?", "Programmer ce rappel ?", "واش نبرمج هاد التذكير؟")}\n${action.title}\n${SimpleDateFormat("EEE, dd MMM yyyy · HH:mm", Locale.forLanguageTag(language)).format(Date(action.triggerAt))}"
            else -> ""
        }
        AlertDialog(
            onDismissRequest = { pendingAssistantAction = null },
            title = { Text(actionTitle) },
            text = { Text(actionMessage) },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        is AssistantAction.AddTask -> {
                            tasks = tasks + TaskItem(action.title)
                            saveTasks()
                            info = "${uiText(language, "Task added", "Tâche ajoutée", "تمت إضافة المهمة")}: ${action.title}"
                        }
                        is AssistantAction.AddMoney -> {
                            money = money + MoneyItem(action.description, action.amount, action.income)
                            saveMoney()
                            info = "${uiText(language, if (action.income) "Income recorded" else "Expense recorded", if (action.income) "Revenu enregistré" else "Dépense enregistrée", if (action.income) "تسجل المدخول" else "تسجل المصروف")}: ${action.amount} MAD"
                        }
                        is AssistantAction.AddReminder -> {
                            if (action.triggerAt > System.currentTimeMillis()) {
                                scheduleReminder(ReminderItem(System.currentTimeMillis(), action.title, action.triggerAt))
                            } else {
                                info = uiText(language, "That reminder time has passed. Please ask again.", "L’heure du rappel est passée. Veuillez réessayer.", "داك الوقت داز، عاود طلب التذكير بوقت آخر.")
                            }
                        }
                        else -> Unit
                    }
                    pendingAssistantAction = null
                    command = ""
                }) { Text(uiText(language, "Confirm", "Confirmer", "تأكيد")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAssistantAction = null }) { Text(uiText(language, "Cancel", "Annuler", "إلغاء")) }
            }
        )
    }
    pendingRestore?.let { snapshot ->
            AlertDialog(
                onDismissRequest = { pendingRestore = null },
                title = { Text(uiText(language, "Restore DailySmart backup?", "Restaurer la sauvegarde DailySmart ?", "استرجاع نسخة DailySmart الاحتياطية؟")) },
                text = { Text(uiText(language, "This replaces current tasks, finance records and habits with the backup. Export a backup first if you want to keep the current data.", "Cette action remplace les tâches, finances et habitudes actuelles. Créez une sauvegarde avant de continuer si nécessaire.", "سيتم تعويض المهام والمصاريف والعادات الحالية بمحتوى النسخة. أنشئ نسخة احتياطية أولاً إذا أردت الاحتفاظ ببياناتك.")) },
                confirmButton = {
                    TextButton(onClick = {
                        tasks = snapshot.tasks
                        money = snapshot.money
                        habits = snapshot.habits
                        saveTasks(); saveMoney(); saveHabits()
                        pendingRestore = null
                        info = uiText(language, "Backup restored successfully.", "Sauvegarde restaurée.", "تم استرجاع النسخة الاحتياطية بنجاح.")
                    }) { Text(uiText(language, "Restore", "Restaurer", "استرجاع")) }
                },
                dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(uiText(language, "Cancel", "Annuler", "إلغاء")) } }
            )
        }
        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { reminderDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        showDatePicker = false
                    }) { Text(uiText(language, "OK", "OK", "موافق")) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text(uiText(language, "Cancel", "Annuler", "إلغاء")) }
                }
            ) { DatePicker(state = datePickerState) }
        }
        if (showTimePicker) {
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                title = { Text(uiText(language, "Choose reminder time", "Choisir l’heure du rappel", "اختار وقت التذكير")) },
                text = { TimePicker(state = timePickerState) },
                confirmButton = {
                    TextButton(onClick = {
                        reminderHour = timePickerState.hour
                        reminderMinute = timePickerState.minute
                        showTimePicker = false
                    }) { Text(uiText(language, "OK", "OK", "موافق")) }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) { Text(uiText(language, "Cancel", "Annuler", "إلغاء")) }
                }
            )
        }
        Scaffold(containerColor = Color.Transparent, bottomBar = {
            NavigationBar(containerColor = Panel, tonalElevation = 12.dp) {
                listOf("Assistant", "Finance", "Tasks", "Habits", "Reminders").forEach { name ->
                    NavigationBarItem(selected = tab == name, onClick = { tab = name }, icon = {
                        Icon(when(name) { "Assistant" -> Icons.Default.AutoAwesome; "Finance" -> Icons.Default.AccountBalanceWallet; "Tasks" -> Icons.Default.CheckCircle; "Habits" -> Icons.Default.Favorite; "Reminders" -> Icons.Default.Notifications; "Excel" -> Icons.Default.TableChart; else -> Icons.Default.Settings }, null)
                    }, label = { Text(uiText(language, name, when(name) { "Assistant" -> "Assistant"; "Finance" -> "Finances"; "Tasks" -> "Tâches"; "Habits" -> "Habitudes"; "Reminders" -> "Rappels"; "Excel" -> "Excel"; else -> "Paramètres" }, when(name) { "Assistant" -> "المساعد"; "Finance" -> "المالية"; "Tasks" -> "المهام"; "Habits" -> "العادات"; "Reminders" -> "التذكيرات"; "Excel" -> "إكسيل"; else -> "الإعدادات" })) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Navy, unselectedTextColor = Muted))
                }
            }
        }) { pad ->
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Navy, Color(0xFFEAF0FF), Navy))).padding(pad).padding(horizontal = 18.dp).then(if (tab == "Assistant" || tab == "Excel" || tab == "Settings" || tab == "Reminders") Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF203B58), Color(0xFF27345D), Color(0xFF17463F))))
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(uiText(language, "DAILYSMART  •  YOUR PERSONAL SPACE", "DAILYSMART  •  VOTRE ESPACE", "DAILYSMART  •  مساحتك الخاصة"), color = Color(0xFFB9F5E5), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(uiText(language, "Your day, under control.", "Votre journée, bien organisée.", "نهارك منظم وتحت السيطرة."), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(5.dp))
                            Text(SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag(language)).format(Date()), color = Color(0xFFD0D9EC), fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.16f)).clickable { tab = "Excel" }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.TableChart, "Excel", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.16f)).clickable { tab = "Settings" }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Settings, "Settings", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                when (tab) {
                    "Assistant" -> {
                        Text(uiText(language, "AI WORKSPACE", "ESPACE ASSISTANT", "مساحة المساعد"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, null, tint = Accent); Spacer(Modifier.width(8.dp)); Text(uiText(language, "DailySmart Assistant", "Assistant DailySmart", "مساعد DailySmart"), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                                Spacer(Modifier.height(12.dp))
                                Text(if (geminiConfigured) uiText(language, "Gemini can propose tasks, finance actions and reminders; you confirm before saving. Manage reminders in the Reminders tab.", "Gemini peut proposer des tâches, opérations financières et rappels ; confirmez avant l’enregistrement. Gérez aussi les rappels dans l’onglet Rappels.", "Gemini يقدر يقترح مهام ومصاريف ومداخيل وتذكيرات؛ كتأكد قبل التسجيل. تقدر تدبر التذكيرات من الخانة الخاصة بها.") else uiText(language, "Gemini setup is prepared. Connect the Firebase project to activate online AI.", "La connexion Gemini est préparée. Connectez le projet Firebase pour activer l’IA.", "ربط Gemini جاهز برمجياً. خاصنا نربطو مشروع Firebase باش يتفعّل الذكاء الاصطناعي."), color = Muted, fontSize = 13.sp)
                                Spacer(Modifier.height(12.dp))
                                Text(info, color = Accent, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(value = command, onValueChange = { if (it.length <= 1500) command = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(uiText(language, "Ex: add task call the bank", "Ex : ajouter une tâche appeler la banque", "مثال: أضف مهمة الاتصال بالبنك")) }, shape = RoundedCornerShape(16.dp), minLines = 2)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            val c = command.trim()
                            when (val action = AssistantEngine.parse(c)) {
                                is AssistantAction.AddTask, is AssistantAction.AddMoney, is AssistantAction.AddReminder -> {
                                    pendingAssistantAction = action
                                }
                                AssistantAction.FinanceReport -> {
                                    showFinanceReport()
                                    command = ""
                                }
                                AssistantAction.Help -> {
                                    if (!geminiConfigured) {
                                        info = uiText(language, "Gemini is not active yet. Firebase project configuration is required.", "Gemini n’est pas encore actif. La configuration Firebase est nécessaire.", "Gemini مازال ما تفعلش. خاص إعداد مشروع Firebase.")
                                    } else {
                                        geminiLoading = true
                                        scope.launch {
                                            try {
                                                val interpretation = GeminiAssistant.interpret(c, language)
                                                info = interpretation.reply
                                                when (val aiAction = interpretation.action) {
                                                    is AssistantAction.AddTask, is AssistantAction.AddMoney, is AssistantAction.AddReminder -> pendingAssistantAction = aiAction
                                                    AssistantAction.FinanceReport -> showFinanceReport()
                                                    else -> Unit
                                                }
                                            } catch (error: Exception) {
                                                info = uiText(language, "Gemini request failed: ", "Échec de la requête Gemini : ", "وقع مشكل فالاتصال بـ Gemini: ") + (error.localizedMessage ?: "Unknown error")
                                            } finally {
                                                geminiLoading = false
                                            }
                                        }
                                    }
                                    command = ""
                                }
                            }
                        }, enabled = !geminiLoading, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            if (geminiLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(uiText(language, "Gemini is thinking…", "Gemini réfléchit…", "Gemini كيفكر…"))
                            } else {
                                Icon(Icons.Default.Send, null)
                                Spacer(Modifier.width(8.dp))
                                Text(uiText(language, "Ask Gemini / Run command", "Demander à Gemini / Exécuter", "سول Gemini / نفّذ الأمر"))
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(uiText(language, "QUICK OVERVIEW", "APERÇU RAPIDE", "نظرة سريعة"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SummaryCard(uiText(language, "Tasks", "Tâches", "المهام"), "${tasks.count { it.done }}/${tasks.size} ${uiText(language, "done", "terminées", "مكتملة")}", Modifier.weight(1f), Lavender)
                            SummaryCard(uiText(language, "Balance", "Solde", "الرصيد"), "${"%.0f".format(money.sumOf { if (it.income) it.amount else -it.amount })} DH", Modifier.weight(1f), Accent)
                        }
                        val taskProgress = if (tasks.isEmpty()) 0f else tasks.count { it.done }.toFloat() / tasks.size
                        Spacer(Modifier.height(8.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = PanelRaised), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(uiText(language, "Today’s momentum", "Votre progression du jour", "تقدمك اليوم"), color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("${(taskProgress * 100).toInt()}%", color = Accent, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(progress = { taskProgress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Accent, trackColor = Navy)
                                Spacer(Modifier.height(7.dp))
                                Text(if (tasks.isEmpty()) uiText(language, "Add your first task and build a little momentum.", "Ajoutez votre première tâche.", "أضف أول مهمة وابدأ يومك.") else if (taskProgress >= 1f) uiText(language, "Everything is done. Great work!", "Tout est terminé. Bravo !", "أنهيت كل شيء، أحسنت!") else uiText(language, "One step at a time. Keep your streak moving.", "Un pas après l’autre.", "خطوة بخطوة، واصل التقدم."), color = Muted, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val dailyQuote = DailyQuoteProvider.quoteFor(java.time.LocalDate.now().dayOfYear, language)
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF232C4A)), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FormatQuote, null, tint = Lavender)
                                    Spacer(Modifier.width(7.dp))
                                    Text(uiText(language, "DAILY INSPIRATION", "INSPIRATION DU JOUR", "إلهام اليوم"), color = Lavender, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                }
                                Spacer(Modifier.height(7.dp))
                                Text(dailyQuote, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(5.dp))
                                Text(uiText(language, "A fresh thought to help you move forward.", "Une pensée pour avancer chaque jour.", "فكرة صغيرة تعاونك تتقدم كل نهار."), color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                    "Tasks" -> {
                        Text(uiText(language, "TASKS", "TÂCHES", "المهام"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = newTitle, onValueChange = { if (it.length <= 500) newTitle = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(uiText(language, "New task", "Nouvelle tâche", "مهمة جديدة")) }, singleLine = true)
                        Button(onClick = { if (newTitle.isNotBlank()) { tasks = tasks + TaskItem(newTitle.trim()); saveTasks(); newTitle = "" } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text(uiText(language, "Add task", "Ajouter", "إضافة مهمة")) }
                        val doneRatio = if (tasks.isEmpty()) 0f else tasks.count { it.done }.toFloat() / tasks.size
                        Text(uiText(language, "${tasks.count { it.done }} of ${tasks.size} tasks completed", "${tasks.count { it.done }} sur ${tasks.size} tâches terminées", "أكملت ${tasks.count { it.done }} من أصل ${tasks.size} مهام"), color = Muted, fontSize = 12.sp)
                        LinearProgressIndicator(progress = { doneRatio }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Lavender, trackColor = PanelRaised)
                        Spacer(Modifier.height(8.dp))
                        if (tasks.isEmpty()) Text(uiText(language, "No tasks yet. Add one above to get started.", "Aucune tâche pour le moment. Ajoutez-en une ci-dessus.", "ما كايناش مهام دابا. زيد أول مهمة من الفوق."), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 14.dp))
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(tasks) { i, item ->
                                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                                    Row(Modifier.fillMaxWidth().clickable { tasks = tasks.toMutableList().also { it[i] = item.copy(done = !item.done) }; saveTasks() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (item.done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (item.done) Accent else Muted)
                                        Spacer(Modifier.width(10.dp)); Text(item.title, modifier = Modifier.weight(1f), color = if (item.done) Muted else White)
                                        IconButton(onClick = { tasks = tasks.toMutableList().also { it.removeAt(i) }; saveTasks() }) { Icon(Icons.Default.Delete, null, tint = Muted) }
                                    }
                                }
                            }
                        }
                    }
                    "Finance" -> {
                        val income = money.filter { it.income }.sumOf { it.amount }
                        val expense = money.filterNot { it.income }.sumOf { it.amount }
                        val analytics = FinanceAnalytics.summarize(money)
                        Text(uiText(language, "PERSONAL FINANCE", "FINANCES PERSONNELLES", "المالية الشخصية"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { SummaryCard(uiText(language, "Income", "Revenu", "المدخول"), "${"%.2f".format(income)} DH", Modifier.weight(1f), Accent); SummaryCard(uiText(language, "Expenses", "Dépenses", "المصاريف"), "${"%.2f".format(expense)} DH", Modifier.weight(1f), ExpenseColor) }
                        SummaryCard(uiText(language, "Available balance", "Solde disponible", "الرصيد المتاح"), "${"%.2f".format(income-expense)} DH", Modifier.fillMaxWidth())
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(uiText(language, "Spending by period", "Dépenses par période", "المصاريف حسب الفترة"), fontWeight = FontWeight.Bold, color = White)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(uiText(language, "This week", "Cette semaine", "هاد الأسبوع"), color = Muted)
                                    Text("${"%.0f".format(analytics.weekExpense)} DH", color = ExpenseColor, fontWeight = FontWeight.Bold)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(uiText(language, "This month", "Ce mois", "هاد الشهر"), color = Muted)
                                    Text("${"%.0f".format(analytics.monthExpense)} DH", color = ExpenseColor, fontWeight = FontWeight.Bold)
                                }
                                val topCategory = analytics.topExpenseCategory
                                if (topCategory != null) {
                                    Divider(color = Muted.copy(alpha = 0.2f))
                                    Text("${uiText(language, "Top category this month", "Catégorie principale du mois", "أكثر فئة صرفتي فيها هاد الشهر")}: ${financeCategoryText(language, topCategory.first)} · ${"%.0f".format(topCategory.second)} DH", color = Accent, fontSize = 12.sp)
                                }
                                if (analytics.undatedRecords > 0) {
                                    Text(uiText(language, "${analytics.undatedRecords} older records have no saved date and are excluded from period totals.", "${analytics.undatedRecords} anciennes opérations n’ont pas de date enregistrée et sont exclues des totaux par période.", "${analytics.undatedRecords} عمليات قديمة ما عندهاش تاريخ محفوظ، وما دخلناهاش فحساب الفترات."), color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                        OutlinedTextField(value = newTitle, onValueChange = { if (it.length <= 500) newTitle = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(uiText(language, "Description", "Description", "الوصف")) }, singleLine = true)
                        OutlinedTextField(value = newAmount, onValueChange = { newAmount = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(uiText(language, "Amount in MAD", "Montant en MAD", "المبلغ بالدرهم")) }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { val a = newAmount.replace(",", ".").toDoubleOrNull(); if (a != null && a.isFinite() && a > 0) { money = money + MoneyItem(newTitle.ifBlank { uiText(language, "Income", "Revenu", "مدخول") }, a, true); saveMoney(); newTitle = ""; newAmount = "" } else { info = uiText(language, "Enter a valid amount greater than zero.", "Saisissez un montant valide supérieur à zéro.", "دخل مبلغ صحيح أكبر من صفر.") } }, modifier = Modifier.weight(1f)) { Text(uiText(language, "Add income", "Ajouter un revenu", "إضافة مدخول")) }
                            OutlinedButton(onClick = { val a = newAmount.replace(",", ".").toDoubleOrNull(); if (a != null && a.isFinite() && a > 0) { money = money + MoneyItem(newTitle.ifBlank { uiText(language, "Expense", "Dépense", "مصروف") }, a, false); saveMoney(); newTitle = ""; newAmount = "" } else { info = uiText(language, "Enter a valid amount greater than zero.", "Saisissez un montant valide supérieur à zéro.", "دخل مبلغ صحيح أكبر من صفر.") } }, modifier = Modifier.weight(1f)) { Text(uiText(language, "Add expense", "Ajouter une dépense", "إضافة مصروف")) }
                        }
                        if (money.isEmpty()) Text(uiText(language, "No transactions yet. Add your first income or expense.", "Aucune opération pour le moment.", "ما كايناش عمليات مالية دابا. زيد أول مدخول أو مصروف."), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 14.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) { itemsIndexed(money.reversed()) { index, item -> ListItem(headlineContent = { Text(item.title) }, supportingContent = { Text(if(item.income) uiText(language, "Income", "Revenu", "مدخول") else uiText(language, "Expense", "Dépense", "مصروف"), color = Muted) }, trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { Text("${if(item.income) "+" else "-"}${item.amount} DH", color = if(item.income) Accent else ExpenseColor, fontWeight = FontWeight.Bold); IconButton(onClick = { val originalIndex = money.lastIndex - index; money = money.toMutableList().also { it.removeAt(originalIndex) }; saveMoney() }) { Icon(Icons.Default.Delete, uiText(language, "Delete", "Supprimer", "حذف"), tint = Muted) } } }, colors = ListItemDefaults.colors(containerColor = Panel)) } }
                    }
                    "Habits" -> {
                        Text(uiText(language, "DAILY HABITS", "HABITUDES QUOTIDIENNES", "العادات اليومية"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text(uiText(language, "${habits.count { it.completedToday }} / ${habits.size} completed today", "${habits.count { it.completedToday }} / ${habits.size} terminées aujourd’hui", "أنجزت ${habits.count { it.completedToday }} من ${habits.size} اليوم"), color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(uiText(language, "Build consistency one day at a time.", "La régularité se construit chaque jour.", "الاستمرارية كتتبنى كل نهار."), color = Muted, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = newHabit, onValueChange = { if (it.length <= 500) newHabit = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(uiText(language, "New daily habit", "Nouvelle habitude", "عادة يومية جديدة")) }, singleLine = true)
                        Button(onClick = { if (newHabit.isNotBlank()) { habits = habits + HabitItem(newHabit.trim()); saveHabits(); newHabit = "" } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text(uiText(language, "Add habit", "Ajouter une habitude", "إضافة عادة")) }
                        if (habits.isEmpty()) Text(uiText(language, "No habits yet. Add one small daily habit.", "Aucune habitude pour le moment.", "ما كايناش عادات دابا. زيد عادة صغيرة كل نهار."), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 14.dp))
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(habits) { i, habit ->
                                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                                    Row(Modifier.fillMaxWidth().clickable { habits = habits.toMutableList().also { it[i] = habit.copy(completedToday = !habit.completedToday) }; saveHabits() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (habit.completedToday) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (habit.completedToday) Accent else Muted)
                                        Spacer(Modifier.width(10.dp))
                                        Text(habit.title, modifier = Modifier.weight(1f), color = if (habit.completedToday) Muted else White)
                                        IconButton(onClick = { habits = habits.toMutableList().also { it.removeAt(i) }; saveHabits() }) { Icon(Icons.Default.Delete, null, tint = Muted) }
                                    }
                                }
                            }
                        }
                        OutlinedButton(onClick = { habits = habits.map { it.copy(completedToday = false) }; saveHabits() }, modifier = Modifier.fillMaxWidth()) { Text(uiText(language, "Reset daily checkmarks", "Réinitialiser les coches", "إعادة ضبط علامات اليوم")) }
                    }
                    "Reminders" -> {
                        Text(uiText(language, "REMINDERS", "RAPPELS", "التذكيرات"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(uiText(language, "Create a reminder", "Créer un rappel", "إنشاء تذكير"), color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                OutlinedTextField(
                                    value = reminderTitle,
                                    onValueChange = { if (it.length <= 180) reminderTitle = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text(uiText(language, "What should DailySmart remind you?", "Que doit vous rappeler DailySmart ?", "شنو بغيتي DailySmart يفكرك به؟")) }
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.CalendarMonth, null)
                                        Spacer(Modifier.width(5.dp))
                                        Text(DateTimeFormatter.ofPattern("dd MMM", Locale.forLanguageTag(language)).format(reminderDate), fontSize = 12.sp)
                                    }
                                    OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Schedule, null)
                                        Spacer(Modifier.width(5.dp))
                                        Text("${reminderHour.toString().padStart(2, '0')}:${reminderMinute.toString().padStart(2, '0')}", fontSize = 12.sp)
                                    }
                                }
                                Button(
                                    onClick = {
                                        val triggerAt = ReminderManager.epochMillis(reminderDate, reminderHour, reminderMinute, ZoneId.systemDefault())
                                        if (reminderTitle.isBlank()) {
                                            reminderMessage = uiText(language, "Enter a reminder title.", "Saisissez le texte du rappel.", "كتب شنو بغيتي تتذكر.")
                                        } else if (triggerAt <= System.currentTimeMillis()) {
                                            reminderMessage = uiText(language, "Choose a future date and time.", "Choisissez une date et une heure futures.", "اختار تاريخ ووقت فالمستقبل.")
                                        } else {
                                            scheduleReminder(ReminderItem(System.currentTimeMillis(), reminderTitle.trim(), triggerAt))
                                            reminderTitle = ""
                                        }
                                    },
                                    enabled = reminderTitle.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Default.AddAlarm, null)
                                    Spacer(Modifier.width(7.dp))
                                    Text(uiText(language, "Schedule reminder", "Programmer le rappel", "برمج التذكير"))
                                }
                                Text(uiText(language, "Reminders are stored on this device. Android may delay delivery slightly to save battery.", "Les rappels restent sur cet appareil. Android peut retarder légèrement leur livraison pour économiser la batterie.", "التذكيرات كتخزن فهاد الهاتف. ممكن أندرويد يأخر الإشعار شوية لتوفير البطارية."), color = Muted, fontSize = 11.sp)
                                    if (reminderMessage.isNotBlank()) Text(reminderMessage, color = Accent, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(uiText(language, "UPCOMING", "À VENIR", "التذكيرات القادمة"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (reminders.isEmpty()) {
                            Text(uiText(language, "No reminders yet.", "Aucun rappel pour le moment.", "ما كاين حتى تذكير دابا."), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                        } else {
                            reminders.sortedBy { it.triggerAt }.forEach { reminder ->
                                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(reminder.title, color = White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Text(SimpleDateFormat("EEE, dd MMM yyyy · HH:mm", Locale.forLanguageTag(language)).format(Date(reminder.triggerAt)), color = Muted, fontSize = 11.sp)
                                        }
                                        IconButton(onClick = {
                                            ReminderManager.cancel(context, reminder.id)
                                            reminders = ReminderManager.load(context)
                                            reminderMessage = uiText(language, "Reminder cancelled.", "Rappel annulé.", "تلغى التذكير.")
                                        }) { Icon(Icons.Default.Delete, null, tint = Muted) }
                                    }
                                }
                            }
                        }
                    }
                    "Settings" -> {
                        Text(uiText(language, "SETTINGS", "PARAMÈTRES", "الإعدادات"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(uiText(language, "Language", "Langue", "اللغة"), color = Muted, fontSize = 12.sp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("ar" to "العربية", "fr" to "Français", "en" to "English").forEach { (code, label) ->
                                        if (language == code) Button(onClick = { changeLanguage(code) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) { Text(label, fontSize = 11.sp) }
                                        else OutlinedButton(onClick = { changeLanguage(code) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) { Text(label, fontSize = 11.sp) }
                                    }
                                }
                                Divider(color = Muted.copy(alpha = 0.25f))
                                Text("DailySmart V2", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text(uiText(language, "Version 2.6.0 · Android app", "Version 2.6.0 · Application Android", "الإصدار 2.6.0 · تطبيق أندرويد"), color = Muted, fontSize = 12.sp)
                                Divider(color = Muted.copy(alpha = 0.25f))
                                Text(uiText(language, "Currency", "Devise", "العملة"), color = Muted, fontSize = 12.sp)
                                Text(uiText(language, "Moroccan dirham (MAD)", "Dirham marocain (MAD)", "الدرهم المغربي (MAD)"), color = White)
                                Text(uiText(language, "Storage", "Stockage", "التخزين"), color = Muted, fontSize = 12.sp)
                                Text(uiText(language, "Your tasks, habits and finance records are stored locally on this device.", "Vos tâches, habitudes et finances sont stockées sur cet appareil.", "المهام والعادات والسجلات المالية محفوظة محلياً على هذا الهاتف."), color = White)
                                Text(uiText(language, "Privacy", "Confidentialité", "الخصوصية"), color = Muted, fontSize = 12.sp)
                                Text(uiText(language, "No ads. No account required. No AI API key is embedded in this APK.", "Sans publicité ni compte. Aucune clé API IA dans l’application.", "بدون إعلانات أو حساب، ولا توجد مفاتيح ذكاء اصطناعي داخل التطبيق."), color = White)
                                Text(if (geminiConfigured) uiText(language, "Gemini AI client configured via Firebase AI Logic. App Check protects requests.", "Client Gemini configuré via Firebase AI Logic. App Check protège les requêtes.", "تم إعداد Gemini عبر Firebase AI Logic، وApp Check يحمي الطلبات.") else uiText(language, "Gemini client is ready in code; Firebase project setup is still required.", "Le client Gemini est prêt dans le code ; la configuration Firebase reste nécessaire.", "كود Gemini واجد، وباقي خاص إعداد مشروع Firebase."), color = Accent, fontSize = 12.sp)
                                Divider(color = Muted.copy(alpha = 0.25f))
                                Text(uiText(language, "Backup & restore", "Sauvegarde et restauration", "النسخ الاحتياطي والاسترجاع"), color = Muted, fontSize = 12.sp)
                                Text(uiText(language, "Create a portable copy of tasks, finance records and habits. Restoring replaces current data only after confirmation.", "Créez une copie de vos tâches, finances et habitudes. La restauration remplace les données après confirmation.", "أنشئ نسخة من مهامك ومصاريفك وعاداتك. الاسترجاع يعوض البيانات الحالية بعد التأكيد."), color = White, fontSize = 12.sp)
                                Button(onClick = { backupLauncher.launch("DailySmart-backup.json") }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Backup, null); Spacer(Modifier.width(8.dp)); Text(uiText(language, "Create full backup", "Créer une sauvegarde", "إنشاء نسخة احتياطية"))
                                }
                                OutlinedButton(onClick = { restoreLauncher.launch("application/json") }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Restore, null); Spacer(Modifier.width(8.dp)); Text(uiText(language, "Restore from backup", "Restaurer une sauvegarde", "استرجاع نسخة احتياطية"))
                                }
                            }
                        }
                    }
                    else -> {
                        val totalIncome = money.filter { it.income }.sumOf { it.amount }
                        val totalExpense = money.filterNot { it.income }.sumOf { it.amount }
                        val expenseRatio = if (totalIncome > 0.0) (totalExpense / totalIncome).toFloat().coerceIn(0f, 1f) else 0f
                        Text(uiText(language, "SPREADSHEET TOOLS", "OUTILS TABLEUR", "أدوات الجداول"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(uiText(language, "FINANCE SNAPSHOT", "APERÇU FINANCIER", "ملخص المالية"), color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SummaryCard(uiText(language, "Income", "Revenu", "المدخول"), "${"%.0f".format(totalIncome)} DH", Modifier.weight(1f), Accent)
                            SummaryCard(uiText(language, "Expenses", "Dépenses", "المصاريف"), "${"%.0f".format(totalExpense)} DH", Modifier.weight(1f), ExpenseColor)
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text(uiText(language, "Expenses compared with income", "Dépenses par rapport aux revenus", "المصاريف مقارنة بالمداخيل"), color = White, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(progress = { expenseRatio }, modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Navy)
                                Spacer(Modifier.height(6.dp))
                                Text("${if (totalIncome > 0.0) "%.0f".format(expenseRatio * 100) else "0"}% ${uiText(language, "of recorded income", "des revenus enregistrés", "من المداخيل المسجلة")}", color = Muted, fontSize = 12.sp)
                            }
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Icon(Icons.Default.TableChart, null, tint = Accent, modifier = Modifier.size(30.dp))
                                Text(uiText(language, "Excel companion", "Compagnon Excel", "مساعد Excel"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text(uiText(language, "Import and export .xlsx finance workbooks, or use CSV for simple data exchange. This version handles transaction type, description and amount; it does not edit arbitrary workbook formulas or charts.", "Importez et exportez des fichiers financiers .xlsx ou CSV. Cette version gère le type, la description et le montant, mais pas les formules ni les graphiques avancés.", "استورد وصدّر ملفات Excel أو CSV المالية. النسخة الحالية تدعم نوع العملية والوصف والمبلغ، ولا تعدل الصيغ والرسوم البيانية المتقدمة."), color = Muted, fontSize = 13.sp)
                                Spacer(Modifier.height(14.dp))
                                Button(onClick = { exportLauncher.launch("dailysmart-finance.csv") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text(uiText(language, "Export finance to CSV", "Exporter en CSV", "تصدير CSV")) }
                                OutlinedButton(onClick = { importLauncher.launch("text/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text(uiText(language, "Import finance CSV", "Importer CSV", "استيراد CSV")) }
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { xlsxExportLauncher.launch("dailysmart-finance.xlsx") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text(uiText(language, "Export Excel workbook (.xlsx)", "Exporter Excel (.xlsx)", "تصدير Excel (.xlsx)")) }
                                OutlinedButton(onClick = { xlsxImportLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text(uiText(language, "Import Excel workbook (.xlsx)", "Importer Excel (.xlsx)", "استيراد Excel (.xlsx)")) }
                            }
                        }
                        Text(uiText(language, "Supported formats: XLSX and CSV · Currency: MAD", "Formats : XLSX et CSV · Devise : MAD", "الصيغ المدعومة: XLSX وCSV · العملة: MAD"), color = Muted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
      }
    }
}

private fun uiText(language: String, english: String, french: String, arabic: String): String = when (language) {
    "ar" -> arabic
    "fr" -> french
    else -> english
}

private fun financeCategoryText(language: String, category: String): String = when (category) {
    "Alimentation" -> uiText(language, "Food", "Alimentation", "المأكولات")
    "Logement" -> uiText(language, "Housing", "Logement", "السكن")
    "Transport" -> uiText(language, "Transport", "Transport", "النقل")
    "Santé" -> uiText(language, "Health", "Santé", "الصحة")
    "Factures" -> uiText(language, "Bills", "Factures", "الفواتير")
    "Loisirs" -> uiText(language, "Leisure", "Loisirs", "الترفيه")
    "Épargne" -> uiText(language, "Savings", "Épargne", "الادخار")
    else -> uiText(language, "Other", "Divers", "أخرى")
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier, tint: Color = Accent) {
    Card(modifier = modifier.padding(vertical = 5.dp), colors = CardDefaults.cardColors(containerColor = PanelRaised), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(tint))
                Spacer(Modifier.width(7.dp))
                Text(label, color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Prefix-marked URL-safe Base64 keeps delimiters and newlines inside user text
 * from corrupting the lightweight SharedPreferences record format. Legacy
 * unprefixed values remain readable for existing installations.
 */
private fun encodeStoredText(value: String): String =
    "b64:" + android.util.Base64.encodeToString(
        value.toByteArray(Charsets.UTF_8),
        android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
    )

private fun decodeStoredText(value: String): String {
    if (!value.startsWith("b64:")) return value
    return runCatching {
        String(
            android.util.Base64.decode(
                value.removePrefix("b64:"),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
            ),
            Charsets.UTF_8
        )
    }.getOrDefault(value)
}

private fun loadTasks(p: android.content.SharedPreferences): List<TaskItem> =
    p.getString("tasks", "")!!.lines().filter { it.contains("|") }.map { TaskItem(decodeStoredText(it.substringAfter("|")), it.substringBefore("|") == "1") }
private fun loadHabits(p: android.content.SharedPreferences): List<HabitItem> {
    val items = p.getString("habits", "")!!.lines().filter { it.contains("|") }
        .map { HabitItem(decodeStoredText(it.substringAfter("|")), it.substringBefore("|") == "1") }
    return if (p.getString("habits_date", "") == todayKey()) items else items.map { it.copy(completedToday = false) }
}
private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
private fun loadMoney(p: android.content.SharedPreferences): List<MoneyItem> =
    p.getString("money", "")!!.lines().filter { it.count { c -> c == '|' } >= 2 }.mapNotNull {
        val a = it.split("|", limit = 3); MoneyItem(decodeStoredText(a[2]), a[1].toDoubleOrNull() ?: return@mapNotNull null, a[0] == "I", 0L)
    }

private fun readBackupBounded(input: InputStream): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= BackupCodec.MAX_BACKUP_BYTES) { "Backup exceeds the 2 MB safety limit." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}


private fun readBoundedText(input: InputStream, maxBytes: Int, label: String): String {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "$label file exceeds the ${maxBytes / (1024 * 1024)} MB safety limit." }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}
