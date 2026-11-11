package com.dailysmart.app.v2

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.InputStream

private val Navy = Color(0xFF080F1F)
private val Panel = Color(0xFF121E34)
private val PanelRaised = Color(0xFF1A2944)
private val Accent = Color(0xFF76E6C6)
private val Lavender = Color(0xFF9C9BFF)
private val Gold = Color(0xFFFFC978)
private val ExpenseColor = Color(0xFFFF8D9B)
private val Muted = Color(0xFF9AABC4)
private val White = Color(0xFFF4F7FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DailySmartApp() }
    }
}

data class TaskItem(val title: String, val done: Boolean = false)
data class MoneyItem(val title: String, val amount: Double, val income: Boolean)
data class HabitItem(val title: String, val completedToday: Boolean = false)

@Composable
fun DailySmartApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("dailysmart_v2", Context.MODE_PRIVATE) }
    val database = remember {
        LocalDatabase(context).also { db ->
            db.migrateLegacyOnce(
                loadTasks(prefs), loadMoney(prefs), loadHabits(prefs),
                prefs.getString("habits_date", "") ?: ""
            )
        }
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
    var info by remember { mutableStateOf("Your personal workspace is ready.") }
    var pendingRestore by remember { mutableStateOf<BackupCodec.Snapshot?>(null) }

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

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
                out.appendLine("type,description,amount,currency")
                money.forEach { out.appendLine("${if (it.income) "income" else "expense"},\"${it.title.replace("\"", "\"\"")}\",${it.amount},MAD") }
            }
            info = "CSV export created."
        }.onFailure { info = "Export failed: ${it.localizedMessage}" }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val csvText = context.contentResolver.openInputStream(uri)?.use { readBoundedText(it, 5 * 1024 * 1024, "CSV") } ?: throw IllegalStateException("Could not open CSV file.")
            val rows = csvText.lineSequence().drop(1).toList()
            val imported = rows.mapNotNull { line ->
                val p = parseCsvLine(line)
                if (p.size >= 3) {
                    val amount = p[2].trim().toDoubleOrNull() ?: return@mapNotNull null
                    if (!amount.isFinite() || amount <= 0.0) return@mapNotNull null
                    MoneyItem(p[1].trim().trim('"'), amount, listOf("income", "revenu", "recette", "دخل").any { p[0].contains(it, true) })
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

    MaterialTheme(colorScheme = darkColorScheme(background = Navy, surface = Panel, primary = Accent, onBackground = White, onSurface = White)) {
      CompositionLocalProvider(LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
        pendingRestore?.let { snapshot ->
            AlertDialog(
                onDismissRequest = { pendingRestore = null },
                title = { Text("Restore DailySmart backup?") },
                text = { Text("This replaces current tasks, finance records and habits with the backup. Export a backup first if you want to keep the current data.") },
                confirmButton = {
                    TextButton(onClick = {
                        tasks = snapshot.tasks
                        money = snapshot.money
                        habits = snapshot.habits
                        saveTasks(); saveMoney(); saveHabits()
                        pendingRestore = null
                        info = "Backup restored successfully."
                    }) { Text("Restore") }
                },
                dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } }
            )
        }
        Scaffold(containerColor = Color.Transparent, bottomBar = {
            NavigationBar(containerColor = Panel, tonalElevation = 12.dp) {
                listOf("Assistant", "Finance", "Tasks", "Habits", "Excel", "Settings").forEach { name ->
                    NavigationBarItem(selected = tab == name, onClick = { tab = name }, icon = {
                        Icon(when(name) { "Assistant" -> Icons.Default.AutoAwesome; "Finance" -> Icons.Default.AccountBalanceWallet; "Tasks" -> Icons.Default.CheckCircle; "Habits" -> Icons.Default.Favorite; "Excel" -> Icons.Default.TableChart; else -> Icons.Default.Settings }, null)
                    }, label = { Text(uiText(language, name, when(name) { "Assistant" -> "Assistant"; "Finance" -> "Finances"; "Tasks" -> "Tâches"; "Habits" -> "Habitudes"; "Excel" -> "Excel"; else -> "Settings" }, when(name) { "Assistant" -> "المساعد"; "Finance" -> "المالية"; "Tasks" -> "المهام"; "Habits" -> "العادات"; "Excel" -> "إكسيل"; else -> "الإعدادات" })) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Navy, unselectedTextColor = Muted))
                }
            }
        }) { pad ->
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Navy, Color(0xFF101B31), Navy))).padding(pad).padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF203B58), Color(0xFF27345D), Color(0xFF17463F))))
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("DAILYSMART  •  YOUR PERSONAL SPACE", color = Color(0xFFB9F5E5), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("Your day, under control.", color = White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(5.dp))
                            Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), color = Color(0xFFD0D9EC), fontSize = 12.sp)
                        }
                        Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Bolt, null, tint = Gold, modifier = Modifier.size(30.dp))
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                when (tab) {
                    "Assistant" -> {
                        Text("AI WORKSPACE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, null, tint = Accent); Spacer(Modifier.width(8.dp)); Text("DailySmart Assistant", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                                Spacer(Modifier.height(12.dp))
                                Text("Tell me what you want to do. I can execute quick actions locally; secure online AI will be connected through a backend, never an API key inside the app.", color = Muted, fontSize = 13.sp)
                                Spacer(Modifier.height(12.dp))
                                Text(info, color = Accent, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(value = command, onValueChange = { command = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Ex: add task call the bank") }, shape = RoundedCornerShape(16.dp), minLines = 2)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            val c = command.trim()
                            when (val action = AssistantEngine.parse(c)) {
                                is AssistantAction.AddTask -> {
                                    tasks = tasks + TaskItem(action.title)
                                    saveTasks()
                                    info = "Task added: ${action.title}"
                                }
                                is AssistantAction.AddMoney -> {
                                    money = money + MoneyItem(action.description, action.amount, action.income)
                                    saveMoney()
                                    info = "${if (action.income) "Income" else "Expense"} recorded: ${action.amount} MAD"
                                }
                                AssistantAction.FinanceReport -> {
                                    val totalIncome = money.filter { it.income }.sumOf { it.amount }
                                    val totalExpense = money.filterNot { it.income }.sumOf { it.amount }
                                    val balance = totalIncome - totalExpense
                                    info = "Finance report · Income: ${"%.2f".format(totalIncome)} MAD · Expenses: ${"%.2f".format(totalExpense)} MAD · Balance: ${"%.2f".format(balance)} MAD"
                                }
                                AssistantAction.Help -> info = AssistantEngine.helpText()
                            }
                            command = ""
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(8.dp)); Text("Run command") }
                        Spacer(Modifier.height(14.dp))
                        Text("QUICK OVERVIEW", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SummaryCard("Tasks", "${tasks.count { it.done }}/${tasks.size} done", Modifier.weight(1f), Lavender)
                            SummaryCard("Balance", "${"%.0f".format(money.sumOf { if (it.income) it.amount else -it.amount })} DH", Modifier.weight(1f), Accent)
                        }
                        val taskProgress = if (tasks.isEmpty()) 0f else tasks.count { it.done }.toFloat() / tasks.size
                        Spacer(Modifier.height(8.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = PanelRaised), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Today’s momentum", color = White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("${(taskProgress * 100).toInt()}%", color = Accent, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(progress = { taskProgress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Accent, trackColor = Navy)
                                Spacer(Modifier.height(7.dp))
                                Text(if (tasks.isEmpty()) "Add your first task and build a little momentum." else if (taskProgress >= 1f) "Everything is done. Great work!" else "One step at a time. Keep your streak moving.", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                    "Tasks" -> {
                        Text("TASKS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("New task") }, singleLine = true)
                        Button(onClick = { if (newTitle.isNotBlank()) { tasks = tasks + TaskItem(newTitle.trim()); saveTasks(); newTitle = "" } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("Add task") }
                        val doneRatio = if (tasks.isEmpty()) 0f else tasks.count { it.done }.toFloat() / tasks.size
                        Text("${tasks.count { it.done }} of ${tasks.size} tasks completed", color = Muted, fontSize = 12.sp)
                        LinearProgressIndicator(progress = { doneRatio }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Lavender, trackColor = PanelRaised)
                        Spacer(Modifier.height(8.dp))
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
                        Text("PERSONAL FINANCE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { SummaryCard("Income", "${"%.2f".format(income)} DH", Modifier.weight(1f), Accent); SummaryCard("Expenses", "${"%.2f".format(expense)} DH", Modifier.weight(1f), ExpenseColor) }
                        SummaryCard("Available balance", "${"%.2f".format(income-expense)} DH", Modifier.fillMaxWidth())
                        OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Description") }, singleLine = true)
                        OutlinedTextField(value = newAmount, onValueChange = { newAmount = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Amount in MAD") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { val a = newAmount.toDoubleOrNull(); if (a != null && a > 0) { money = money + MoneyItem(newTitle.ifBlank { "Income" }, a, true); saveMoney(); newTitle = ""; newAmount = "" } }, modifier = Modifier.weight(1f)) { Text("Add income") }
                            OutlinedButton(onClick = { val a = newAmount.toDoubleOrNull(); if (a != null && a > 0) { money = money + MoneyItem(newTitle.ifBlank { "Expense" }, a, false); saveMoney(); newTitle = ""; newAmount = "" } }, modifier = Modifier.weight(1f)) { Text("Add expense") }
                        }
                        LazyColumn(modifier = Modifier.weight(1f)) { itemsIndexed(money.reversed()) { _, item -> ListItem(headlineContent = { Text(item.title) }, supportingContent = { Text(if(item.income) "Income" else "Expense", color = Muted) }, trailingContent = { Text("${if(item.income) "+" else "-"}${item.amount} DH", color = if(item.income) Accent else ExpenseColor, fontWeight = FontWeight.Bold) }, colors = ListItemDefaults.colors(containerColor = Panel)) } }
                    }
                    "Habits" -> {
                        Text("DAILY HABITS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("${habits.count { it.completedToday }} / ${habits.size} completed today", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Build consistency one day at a time.", color = Muted, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = newHabit, onValueChange = { newHabit = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("New daily habit") }, singleLine = true)
                        Button(onClick = { if (newHabit.isNotBlank()) { habits = habits + HabitItem(newHabit.trim()); saveHabits(); newHabit = "" } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("Add habit") }
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
                        OutlinedButton(onClick = { habits = habits.map { it.copy(completedToday = false) }; saveHabits() }, modifier = Modifier.fillMaxWidth()) { Text("Reset daily checkmarks") }
                    }
                    "Settings" -> {
                        Text("SETTINGS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                Text("Version 2.4.0 · Independent Android application", color = Muted, fontSize = 12.sp)
                                Divider(color = Muted.copy(alpha = 0.25f))
                                Text("Currency", color = Muted, fontSize = 12.sp)
                                Text("Moroccan dirham (MAD)", color = White)
                                Text("Storage", color = Muted, fontSize = 12.sp)
                                Text("Your tasks, habits and finance records are stored locally on this device.", color = White)
                                Text("Privacy", color = Muted, fontSize = 12.sp)
                                Text("No ads. No account required. No AI API key is embedded in this APK.", color = White)
                                Text("Online AI is not connected yet. XLSX finance import/export is available.", color = Accent, fontSize = 12.sp)
                                Divider(color = Muted.copy(alpha = 0.25f))
                                Text("Backup & restore", color = Muted, fontSize = 12.sp)
                                Text("Create a portable copy of tasks, finance records and habits. Restoring replaces current data only after confirmation.", color = White, fontSize = 12.sp)
                                Button(onClick = { backupLauncher.launch("DailySmart-backup.json") }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Backup, null); Spacer(Modifier.width(8.dp)); Text("Create full backup")
                                }
                                OutlinedButton(onClick = { restoreLauncher.launch("application/json") }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Restore, null); Spacer(Modifier.width(8.dp)); Text("Restore from backup")
                                }
                            }
                        }
                    }
                    else -> {
                        val totalIncome = money.filter { it.income }.sumOf { it.amount }
                        val totalExpense = money.filterNot { it.income }.sumOf { it.amount }
                        val expenseRatio = if (totalIncome > 0.0) (totalExpense / totalIncome).toFloat().coerceIn(0f, 1f) else 0f
                        Text("SPREADSHEET TOOLS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("FINANCE SNAPSHOT", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SummaryCard("Income", "${"%.0f".format(totalIncome)} DH", Modifier.weight(1f))
                            SummaryCard("Expenses", "${"%.0f".format(totalExpense)} DH", Modifier.weight(1f))
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("Expenses compared with income", color = White, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(progress = { expenseRatio }, modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Navy)
                                Spacer(Modifier.height(6.dp))
                                Text("${if (totalIncome > 0.0) "%.0f".format(expenseRatio * 100) else "0"}% of recorded income", color = Muted, fontSize = 12.sp)
                            }
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Icon(Icons.Default.TableChart, null, tint = Accent, modifier = Modifier.size(30.dp))
                                Text("Excel companion", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("Import and export .xlsx finance workbooks, or use CSV for simple data exchange. This version handles transaction type, description and amount; it does not edit arbitrary workbook formulas or charts.", color = Muted, fontSize = 13.sp)
                                Spacer(Modifier.height(14.dp))
                                Button(onClick = { exportLauncher.launch("dailysmart-finance.csv") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Export finance to CSV") }
                                OutlinedButton(onClick = { importLauncher.launch("text/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text("Import finance CSV") }
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { xlsxExportLauncher.launch("dailysmart-finance.xlsx") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Export Excel workbook (.xlsx)") }
                                OutlinedButton(onClick = { xlsxImportLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text("Import Excel workbook (.xlsx)") }
                            }
                        }
                        Text("Supported formats: XLSX and CSV · Currency: MAD", color = Muted, fontSize = 12.sp)
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
        val a = it.split("|", limit = 3); MoneyItem(decodeStoredText(a[2]), a[1].toDoubleOrNull() ?: return@mapNotNull null, a[0] == "I")
    }

private fun parseCsvLine(line: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var index = 0
    while (index < line.length) {
        val char = line[index]
        when {
            char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                current.append('"')
                index++
            }
            char == '"' -> quoted = !quoted
            char == ',' && !quoted -> {
                fields += current.toString()
                current.clear()
            }
            else -> current.append(char)
        }
        index++
    }
    fields += current.toString()
    return fields
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
