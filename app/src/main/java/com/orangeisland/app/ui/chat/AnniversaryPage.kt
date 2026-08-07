package com.orangeisland.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orangeisland.app.data.AnniversaryEntry
import com.orangeisland.app.data.AnniversaryUtils
import com.orangeisland.app.ui.components.CircularBackButton
import com.orangeisland.app.viewmodel.ChatViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnniversaryPage(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val entries by viewModel.settings.anniversaries.collectAsState()
    val sorted = remember(entries) { entries.sortedBy { AnniversaryUtils.daysUntilNext(it) } }

    var showEditor by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<AnniversaryEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<AnniversaryEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("纪念日") },
                navigationIcon = { CircularBackButton(onClick = onBack) },
                actions = {
                    IconButton(onClick = { editingEntry = null; showEditor = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加纪念日")
                    }
                }
            )
        }
    ) { padding ->
        if (sorted.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Favorite, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    Spacer(Modifier.height(12.dp))
                    Text("还没有纪念日，点右上角加一个吧", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp) + padding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sorted, key = { it.id }) { entry ->
                    AnniversaryCard(
                        entry = entry,
                        onClick = { editingEntry = entry; showEditor = true },
                        onDelete = { deleteTarget = entry }
                    )
                }
            }
        }
    }

    if (showEditor) {
        AnniversaryEditorDialog(
            initial = editingEntry,
            onDismiss = { showEditor = false },
            onSave = { newEntry ->
                val current = viewModel.settings.anniversaries.value
                val updated = if (editingEntry != null) {
                    current.map { if (it.id == editingEntry!!.id) newEntry.copy(id = editingEntry!!.id) else it }
                } else {
                    current + newEntry
                }
                viewModel.settings.setAnniversaries(updated)
                showEditor = false
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${target.name}」？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.settings.setAnniversaries(viewModel.settings.anniversaries.value - target)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun AnniversaryCard(
    entry: AnniversaryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val days = remember(entry) { AnniversaryUtils.daysUntilNext(entry) }
    val isToday = days == 0L
    val isPastOneTime = !entry.recurring && days < 0L
    val accent = when {
        isToday -> MaterialTheme.colorScheme.primary
        isPastOneTime -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.secondary
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (isToday) 3.dp else 1.dp,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (entry.recurring) Icons.Default.Favorite else Icons.Default.Event,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                val yearNote = if (entry.recurring) " · 第${AnniversaryUtils.yearsSince(entry)}年" else ""
                Text(
                    AnniversaryUtils.formatDate(entry) + yearNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(100),
                color = if (isToday) MaterialTheme.colorScheme.primary else accent.copy(alpha = 0.12f)
            ) {
                Text(
                    text = when {
                        isToday -> "今天！"
                        days > 0 -> "还有${days}天"
                        else -> "${-days}天前"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimary else accent,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun AnniversaryEditorDialog(
    initial: AnniversaryEntry?,
    onDismiss: () -> Unit,
    onSave: (AnniversaryEntry) -> Unit
) {
    val today = remember { LocalDate.now() }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var year by remember { mutableStateOf((initial?.year ?: today.year).toString()) }
    var month by remember { mutableStateOf((initial?.month ?: today.monthValue).toString()) }
    var day by remember { mutableStateOf((initial?.day ?: today.dayOfMonth).toString()) }
    var recurring by remember { mutableStateOf(initial?.recurring ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "编辑纪念日" else "添加纪念日", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("名称") },
                    placeholder = { Text("例如：在一起纪念日") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = year, onValueChange = { year = it; error = null },
                        label = { Text("年") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = month, onValueChange = { month = it; error = null },
                        label = { Text("月") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = day, onValueChange = { day = it; error = null },
                        label = { Text("日") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { recurring = !recurring }
                ) {
                    Switch(checked = recurring, onCheckedChange = { recurring = it })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("每年循环", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (recurring) "每年这一天都会提醒" else "只算这一次，不循环",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val y = year.toIntOrNull()
                val m = month.toIntOrNull()
                val d = day.toIntOrNull()
                when {
                    name.isBlank() -> error = "名称不能为空"
                    y == null -> error = "年份不对"
                    m !in 1..12 -> error = "月份要在 1-12"
                    d !in 1..31 -> error = "日期要在 1-31"
                    else -> onSave(AnniversaryEntry(name = name.trim(), year = y, month = m!!, day = d!!, recurring = recurring))
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    start = this.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
        other.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    top = this.calculateTopPadding() + other.calculateTopPadding(),
    end = this.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
        other.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    bottom = this.calculateBottomPadding() + other.calculateBottomPadding()
)
