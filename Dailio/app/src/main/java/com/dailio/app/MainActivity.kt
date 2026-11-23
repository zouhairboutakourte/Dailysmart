package com.dailio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dailio.app.data.FinanceCalculator
import com.dailio.app.data.FinanceDatabase
import com.dailio.app.data.FinanceRepository
import com.dailio.app.data.FinanceTransaction
import com.dailio.app.data.MoneyAmount
import com.dailio.app.data.TransactionType
import java.util.Locale

private val Navy = Color(0xFF0A0F1D)
private val Panel = Color(0xFF131C31)
private val Raised = Color(0xFF1B2942)
private val Cyan = Color(0xFF00E5FF)
private val Green = Color(0xFF35D0BA)
private val Red = Color(0xFFFF718B)
private val MainText = Color(0xFFF7F8FC)
private val Muted = Color(0xFFB8CDE2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DailioApp() }
    }
}

@Composable
private fun DailioApp() {
    val context = LocalContext.current
    val repository = remember { FinanceRepository(FinanceDatabase(context)) }
    DisposableEffect(repository) { onDispose { repository.close() } }

    var transactions by remember { mutableStateOf(repository.all()) }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Divers") }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var feedback by remember { mutableStateOf("Your money, clearly organized.") }
    val summary = remember(transactions) { FinanceCalculator.summarize(transactions) }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Cyan, secondary = Green, background = Navy, surface = Panel,
        onPrimary = Navy, onSecondary = Navy, onBackground = MainText, onSurface = MainText
    )) {
        Scaffold(containerColor = Navy) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Navy, Color(0xFF0D1729), Navy)))
                    .padding(padding).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Raised), shape = RoundedCornerShape(24.dp)) {
                        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("DAILIO", color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                                Text("Your Life. Under Control.", color = MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("Finance first · Offline by default", color = Muted, fontSize = 13.sp)
                            }
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Cyan, modifier = Modifier.size(38.dp))
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryCard("Income", summary.allIncomeMinor, Green, Modifier.weight(1f))
                        SummaryCard("Expenses", summary.allExpenseMinor, Red, Modifier.weight(1f))
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Available balance", color = Muted, fontSize = 14.sp)
                            Text(formatMad(summary.balanceMinor), color = if (summary.balanceMinor >= 0) Cyan else Red, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("This month", color = Muted, fontWeight = FontWeight.SemiBold)
                                Text("Net " + formatMad(summary.monthNetMinor), color = if (summary.monthNetMinor >= 0) Green else Red, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(7.dp))
                            Text("Income  " + formatMad(summary.monthIncomeMinor), color = Green, fontSize = 13.sp)
                            Text("Expenses " + formatMad(summary.monthExpenseMinor), color = Red, fontSize = 13.sp)
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Add a transaction", color = MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = selectedType == TransactionType.EXPENSE,
                                    onClick = { selectedType = TransactionType.EXPENSE },
                                    label = { Text("Expense") },
                                    leadingIcon = { Icon(Icons.Default.TrendingDown, null) }
                                )
                                FilterChip(
                                    selected = selectedType == TransactionType.INCOME,
                                    onClick = { selectedType = TransactionType.INCOME },
                                    label = { Text("Income") },
                                    leadingIcon = { Icon(Icons.Default.TrendingUp, null) }
                                )
                            }
                            OutlinedTextField(
                                value = description, onValueChange = { description = it.take(120) },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text("Description") }, placeholder = { Text("e.g. Groceries, salary, fuel") }
                            )
                            OutlinedTextField(
                                value = amount, onValueChange = { amount = it.take(24) },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text("Amount (MAD)") }, placeholder = { Text("0.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                            OutlinedTextField(
                                value = category, onValueChange = { category = it.take(40) },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text("Category") }, placeholder = { Text("Food, transport, bills…") }
                            )
                            Button(
                                onClick = {
                                    val minor = MoneyAmount.parseMinorUnits(amount)
                                    if (description.isBlank() || category.isBlank() || minor == null) {
                                        feedback = "Enter a description, category and valid amount (e.g. 125.50)."
                                    } else {
                                        runCatching {
                                            repository.add(description, minor, selectedType, category)
                                            transactions = repository.all()
                                            description = ""
                                            amount = ""
                                            feedback = "Transaction saved on this device."
                                        }.onFailure {
                                            feedback = "Could not save the transaction. Existing records were kept."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Save transaction")
                            }
                            Text(feedback, color = Cyan, fontSize = 13.sp)
                        }
                    }
                }
                item { Text("Recent transactions", color = MainText, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                if (transactions.isEmpty()) {
                    item { Text("No transactions yet. Add your first income or expense.", color = Muted) }
                } else {
                    items(transactions, key = { it.id }) { transaction ->
                        TransactionRow(transaction) {
                            if (repository.delete(transaction.id)) {
                                transactions = repository.all()
                                feedback = "Transaction deleted."
                            } else feedback = "Could not delete this transaction."
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, minor: Long, tint: Color, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Raised), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Muted, fontSize = 13.sp)
            Text(formatMad(minor), color = tint, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TransactionRow(transaction: FinanceTransaction, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(transaction.description, color = MainText, fontWeight = FontWeight.SemiBold)
                Text(transaction.category + " · " + java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(java.util.Date(transaction.occurredAt)), color = Muted, fontSize = 12.sp)
            }
            Text(
                (if (transaction.type == TransactionType.INCOME) "+" else "−") + formatMad(transaction.amountMinor),
                color = if (transaction.type == TransactionType.INCOME) Green else Red,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Muted) }
        }
    }
}

private fun formatMad(minor: Long): String = MoneyAmount.formatMinorUnits(minor) + " MAD"
