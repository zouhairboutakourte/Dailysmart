from pathlib import Path
import sys

project = Path(sys.argv[1])

def read(rel):
    path = project / rel
    return path, path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}; refusing to continue.")
    return text.replace(old, new, 1)

database = project / "app/src/main/java/com/dailysmart/app/data/local/DailySmartDatabase.kt"
dao = project / "app/src/main/java/com/dailysmart/app/data/local/dao/TaskDao.kt"
manifest = project / "app/src/main/AndroidManifest.xml"

db_text = database.read_text(encoding="utf-8")
db_text = db_text.replace("                .fallbackToDestructiveMigration()\n", "")
database.write_text(db_text, encoding="utf-8")

dao_text = dao.read_text(encoding="utf-8")
old_query = "ORDER BY isCompleted ASC, priority DESC, dueDateMillis ASC, id DESC"
new_query = "ORDER BY isCompleted ASC, CASE priority WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END DESC, dueDateMillis ASC, id DESC"
if old_query in dao_text:
    dao_text = dao_text.replace(old_query, new_query)
elif new_query not in dao_text:
    raise SystemExit("Task priority query did not match the expected source; refusing to continue.")
dao.write_text(dao_text, encoding="utf-8")

manifest_text = manifest.read_text(encoding="utf-8")
manifest_text = manifest_text.replace('android:allowBackup="true"', 'android:allowBackup="false"')
manifest.write_text(manifest_text, encoding="utf-8")

repo_rel = "app/src/main/java/com/dailysmart/app/data/repository/DailySmartRepository.kt"
path, s = read(repo_rel)
s = replace_once(s, "import com.dailysmart.app.data.local.entity.TaskEntity\n",
                 "import com.dailysmart.app.data.local.entity.TaskEntity\nimport com.dailysmart.app.data.local.entity.TaskPriority\n",
                 "Repository priority import")
s = replace_once(s, "    suspend fun addTask(title: String) {\n        taskDao.insertTask(TaskEntity(title = title))\n    }",
                 "    suspend fun addTask(title: String, priority: TaskPriority) {\n        taskDao.insertTask(TaskEntity(title = title, priority = priority))\n    }",
                 "Repository addTask")
path.write_text(s, encoding="utf-8")

vm_rel = "app/src/main/java/com/dailysmart/app/ui/viewmodel/DailySmartViewModel.kt"
path, s = read(vm_rel)
s = replace_once(s, "import com.dailysmart.app.data.local.entity.TaskEntity\n",
                 "import com.dailysmart.app.data.local.entity.TaskEntity\nimport com.dailysmart.app.data.local.entity.TaskPriority\n",
                 "ViewModel priority import")
s = replace_once(s, "    fun addTask(title: String) {\n        viewModelScope.launch { repository.addTask(title) }\n    }",
                 "    fun addTask(title: String, priority: TaskPriority) {\n        viewModelScope.launch { repository.addTask(title, priority) }\n    }",
                 "ViewModel addTask")
path.write_text(s, encoding="utf-8")

tasks_rel = "app/src/main/java/com/dailysmart/app/ui/screens/TasksScreen.kt"
tasks_source = """package com.dailysmart.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dailysmart.app.R
import com.dailysmart.app.data.local.entity.TaskPriority
import com.dailysmart.app.ui.viewmodel.DailySmartViewModel

@Composable
fun TasksScreen(viewModel: DailySmartViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (tasks.isEmpty()) {
            Text(
                text = stringResource(R.string.tasks_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { viewModel.setTaskCompleted(task, it) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                                )
                                val priorityLabel = when (task.priority) {
                                    TaskPriority.HIGH -> stringResource(R.string.priority_high)
                                    TaskPriority.MEDIUM -> stringResource(R.string.priority_medium)
                                    TaskPriority.LOW -> stringResource(R.string.priority_low)
                                }
                                Text(
                                    text = priorityLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.deleteTask(task) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete)
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_task))
        }
    }

    if (showDialog) {
        AddTaskDialog(
            onDismiss = { showDialog = false },
            onConfirm = { title, priority ->
                viewModel.addTask(title, priority)
                showDialog = false
            }
        )
    }
}

@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, TaskPriority) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_task)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.task_title_hint)) },
                    singleLine = true
                )
                Text(stringResource(R.string.task_priority))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = priority == TaskPriority.LOW,
                        onClick = { priority = TaskPriority.LOW },
                        label = { Text(stringResource(R.string.priority_low)) }
                    )
                    FilterChip(
                        selected = priority == TaskPriority.MEDIUM,
                        onClick = { priority = TaskPriority.MEDIUM },
                        label = { Text(stringResource(R.string.priority_medium)) }
                    )
                    FilterChip(
                        selected = priority == TaskPriority.HIGH,
                        onClick = { priority = TaskPriority.HIGH },
                        label = { Text(stringResource(R.string.priority_high)) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank()) onConfirm(title.trim(), priority) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
"""
path, _ = read(tasks_rel)
path.write_text(tasks_source, encoding="utf-8")

translations = {
    "app/src/main/res/values/strings.xml": [
        ('    <string name="task_priority">Priority</string>',
         '    <string name="priority_low">Low</string>',
         '    <string name="priority_medium">Medium</string>',
         '    <string name="priority_high">High</string>'),
    ],
    "app/src/main/res/values-fr/strings.xml": [
        ('    <string name="task_priority">Priorité</string>',
         '    <string name="priority_low">Faible</string>',
         '    <string name="priority_medium">Moyenne</string>',
         '    <string name="priority_high">Élevée</string>'),
    ],
    "app/src/main/res/values-ar/strings.xml": [
        ('    <string name="task_priority">الأولوية</string>',
         '    <string name="priority_low">منخفضة</string>',
         '    <string name="priority_medium">متوسطة</string>',
         '    <string name="priority_high">مرتفعة</string>'),
    ],
}
for rel, lines in translations.items():
    path, s = read(rel)
    if 'name="task_priority"' not in s:
        s = s.replace("</resources>", "\n" + "\n".join(lines[0]) + "\n</resources>")
    path.write_text(s, encoding="utf-8")

if ".fallbackToDestructiveMigration()" in database.read_text(encoding="utf-8"):
    raise SystemExit("Destructive Room migration fallback is still present.")
if new_query not in dao.read_text(encoding="utf-8"):
    raise SystemExit("Explicit priority sorting was not applied.")
if 'android:allowBackup="true"' in manifest.read_text(encoding="utf-8"):
    raise SystemExit("Automatic Android backup is still enabled.")
for rel in translations:
    s = (project / rel).read_text(encoding="utf-8")
    for key in ("task_priority", "priority_low", "priority_medium", "priority_high"):
        if f'name="{key}"' not in s:
            raise SystemExit(f"Missing localization {key} in {rel}")

print("DailySmart safety fixes and task-priority feature applied.")
