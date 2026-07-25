package com.orangeisland.app.ui.health

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orangeisland.app.data.gadgetbridge.DailySummary
import com.orangeisland.app.data.gadgetbridge.HealthUiState
import com.orangeisland.app.data.gadgetbridge.SleepSummary
import com.orangeisland.app.data.gadgetbridge.StepsRange
import com.orangeisland.app.ui.components.CircularBackButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Merge two [PaddingValues] by adding their respective sides. */
operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    start = this.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
        other.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    top = this.calculateTopPadding() + other.calculateTopPadding(),
    end = this.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
        other.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    bottom = this.calculateBottomPadding() + other.calculateBottomPadding()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthPage(
    viewModel: HealthViewModel = viewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onPermissionResult(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.os.Environment.isExternalStorageManager()
            } else true
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("健康数据") },
                navigationIcon = {
                    CircularBackButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            !viewModel.hasManageStoragePermission() -> {
                PermissionRequiredContent(
                    modifier = Modifier.padding(padding),
                    onRequestPermission = {
                        viewModel.requestStoragePermissionIntent()?.let { permissionLauncher.launch(it) }
                    }
                )
            }
            !state.dbFileExists -> {
                DbNotFoundContent(
                    modifier = Modifier.padding(padding),
                    diagnosticInfo = state.error,
                    onRetry = { viewModel.checkAndLoad() }
                )
            }
            state.error != null -> {
                ErrorContent(
                    modifier = Modifier.padding(padding),
                    error = state.error!!,
                    onRetry = { viewModel.checkAndLoad() }
                )
            }
            else -> {
                HealthContent(
                    state = state,
                    padding = padding,
                    onStepsRangeChange = { viewModel.setStepsRange(it) },
                )
            }
        }
    }
}

@Composable
private fun PermissionRequiredContent(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("需要存储权限读取健康数据", style = MaterialTheme.typography.titleMedium)
            Text(
                "用于读取Gadgetbridge导出的数据库文件",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRequestPermission) { Text("授予权限") }
        }
    }
}

@Composable
private fun DbNotFoundContent(
    modifier: Modifier = Modifier,
    diagnosticInfo: String? = null,
    onRetry: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text("未找到Gadgetbridge数据库", style = MaterialTheme.typography.titleMedium)
            Text(
                "请在Gadgetbridge设置中开启自动导出数据库",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                "预期路径: /sdcard/Download/手环/Gadgetbridge.db",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (diagnosticInfo != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        diagnosticInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun ErrorContent(
    modifier: Modifier = Modifier,
    error: String,
    onRetry: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun HealthContent(
    state: HealthUiState,
    padding: PaddingValues,
    onStepsRangeChange: (StepsRange) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = padding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RealTimeHeartRateCard(
                heartRate = state.currentHeartRate,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            TodayOverviewCard(
                steps = state.todaySteps,
                calories = state.todayCalories,
                spo2 = state.latestSpo2,
                stress = state.latestStress,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            StepsBarChartCard(
                summaries = if (state.stepsRange == StepsRange.SEVEN_DAYS) state.dailySummaries7 else state.dailySummaries30,
                range = state.stepsRange,
                onRangeChange = onStepsRangeChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            HeartRateLineChartCard(
                summaries = state.dailySummaries7,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            SleepSummaryCard(
                summaries = state.sleepSummaries,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Spo2AndStressCard(
                spo2 = state.latestSpo2,
                stress = state.latestStress,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ============ Real-time Heart Rate Card ============
@Composable
private fun RealTimeHeartRateCard(
    heartRate: Int?,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("实时心率", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (heartRate != null && heartRate > 0) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$heartRate", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.error)
                        Text("BPM", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                    }
                } else {
                    Text("暂无数据", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ============ Today Overview Card ============
@Composable
private fun TodayOverviewCard(
    steps: Int,
    calories: Int?,
    spo2: Int?,
    stress: Int?,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("今日概览", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OverviewItem(icon = Icons.Filled.DirectionsWalk, label = "步数", value = steps.toString(), tint = MaterialTheme.colorScheme.primary)
                OverviewItem(icon = Icons.Filled.LocalFireDepartment, label = "卡路里", value = calories?.toString() ?: "--", tint = MaterialTheme.colorScheme.error)
                OverviewItem(icon = Icons.Filled.WaterDrop, label = "血氧", value = if (spo2 != null) "${spo2}%" else "--", tint = MaterialTheme.colorScheme.tertiary)
                OverviewItem(icon = Icons.Filled.Psychology, label = "压力", value = stress?.toString() ?: "--", tint = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun OverviewItem(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = tint)
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============ Steps Bar Chart Card ============
@Composable
private fun StepsBarChartCard(
    summaries: List<DailySummary>,
    range: StepsRange,
    onRangeChange: (StepsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Filled.DirectionsWalk, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("步数统计", style = MaterialTheme.typography.titleMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = range == StepsRange.SEVEN_DAYS, onClick = { onRangeChange(StepsRange.SEVEN_DAYS) }, label = { Text("7天") })
                    FilterChip(selected = range == StepsRange.THIRTY_DAYS, onClick = { onRangeChange(StepsRange.THIRTY_DAYS) }, label = { Text("30天") })
                }
            }
            if (summaries.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                StepsBarChart(summaries = summaries, modifier = Modifier.fillMaxWidth().height(180.dp))
            }
        }
    }
}

@Composable
private fun StepsBarChart(
    summaries: List<DailySummary>,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M/d") }
    val maxSteps = summaries.maxOfOrNull { it.steps }?.coerceAtLeast(1) ?: 1

    Canvas(modifier = modifier) {
        val barAreaHeight = size.height - 24f
        val barWidth = (size.width / summaries.size) * 0.6f
        val gapWidth = (size.width / summaries.size) * 0.4f

        summaries.forEachIndexed { index, summary ->
            val x = index * (barWidth + gapWidth) + gapWidth / 2
            val barHeight = (summary.steps.toFloat() / maxSteps) * barAreaHeight * 0.9f

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, barAreaHeight - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4f, 4f),
            )

            val dateText = summary.date.format(dateFormatter)
            val textLayout = textMeasurer.measure(
                dateText,
                style = labelStyle.copy(color = labelColor),
            )
            drawText(
                textLayout,
                topLeft = Offset(x + barWidth / 2 - textLayout.size.width / 2, barAreaHeight + 4f),
            )

            if (barHeight > 20f && summaries.size <= 15) {
                val stepText = if (summary.steps >= 1000) "${(summary.steps / 100f).roundToInt() / 10f}k" else summary.steps.toString()
                val stepLayout = textMeasurer.measure(
                    stepText,
                    style = labelStyle.copy(color = labelColor),
                )
                drawText(
                    stepLayout,
                    topLeft = Offset(x + barWidth / 2 - stepLayout.size.width / 2, barAreaHeight - barHeight - 14f),
                )
            }
        }
    }
}

// ============ Heart Rate Line Chart Card ============
@Composable
private fun HeartRateLineChartCard(
    summaries: List<DailySummary>,
    modifier: Modifier = Modifier,
) {
    // Three semantic tones keep the max/avg/min lines distinguishable: error / primary / tertiary.
    val maxColor = MaterialTheme.colorScheme.error
    val avgColor = MaterialTheme.colorScheme.primary
    val minColor = MaterialTheme.colorScheme.tertiary
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                Text("心率趋势", style = MaterialTheme.typography.titleMedium)
            }
            if (summaries.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Legend: three colored dots mapping to the three lines below.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeartRateLegendItem(color = maxColor, label = "最高")
                    HeartRateLegendItem(color = avgColor, label = "平均")
                    HeartRateLegendItem(color = minColor, label = "最低")
                }
                HeartRateLineChart(
                    summaries = summaries,
                    maxColor = maxColor,
                    avgColor = avgColor,
                    minColor = minColor,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
            }
        }
    }
}

@Composable
private fun HeartRateLegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeartRateLineChart(
    summaries: List<DailySummary>,
    maxColor: Color,
    avgColor: Color,
    minColor: Color,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val dateLabelStyle = MaterialTheme.typography.labelSmall
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M/d") }

    // All three series share ONE Y axis: flatten every hrMax/hrAvg/hrMin value together, then clamp
    // the floor (<=40) and ceiling (>=100) so the lines never hug the top edge or the date labels.
    val hrValues = summaries.flatMap { listOfNotNull(it.hrMax, it.hrAvg, it.hrMin) }
    val maxHr = (hrValues.maxOrNull() ?: 100).coerceAtLeast(100)
    val minHr = (hrValues.minOrNull() ?: 40).coerceAtMost(40)
    val rangeHr = (maxHr - minHr).coerceAtLeast(1)

    Canvas(modifier = modifier) {
        val chartHeight = size.height - 24f
        val stepX = size.width / (summaries.size.coerceAtLeast(1))
        val markerRadius = 4f          // raw px — small, original-sized node
        val lineStrokeWidth = 3f       // raw px — matches the original line weight

        fun yFor(value: Int?): Float {
            if (value == null) return Float.NaN
            val ratio = (value - minHr).toFloat() / rangeHr
            return chartHeight - ratio * chartHeight
        }

        // Horizontal reference grid lines (~15% alpha) to help gauge values.
        val gridCount = 4
        for (i in 0..gridCount) {
            val y = chartHeight * (i.toFloat() / gridCount)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
        }

        // Max line + per-day markers
        val maxPoints = summaries.mapIndexedNotNull { idx, s ->
            s.hrMax?.let { Offset(idx * stepX + stepX / 2, yFor(it)) }
        }
        for (i in 0 until maxPoints.size - 1) {
            drawLine(
                color = maxColor,
                start = maxPoints[i],
                end = maxPoints[i + 1],
                strokeWidth = lineStrokeWidth,
                cap = StrokeCap.Round,
            )
        }
        maxPoints.forEach { p -> drawCircle(color = maxColor, radius = markerRadius, center = p) }

        // Avg line + per-day markers
        val avgPoints = summaries.mapIndexedNotNull { idx, s ->
            s.hrAvg?.let { Offset(idx * stepX + stepX / 2, yFor(it)) }
        }
        for (i in 0 until avgPoints.size - 1) {
            drawLine(
                color = avgColor,
                start = avgPoints[i],
                end = avgPoints[i + 1],
                strokeWidth = lineStrokeWidth,
                cap = StrokeCap.Round,
            )
        }
        avgPoints.forEach { p -> drawCircle(color = avgColor, radius = markerRadius, center = p) }

        // Min line + per-day markers
        val minPoints = summaries.mapIndexedNotNull { idx, s ->
            s.hrMin?.let { Offset(idx * stepX + stepX / 2, yFor(it)) }
        }
        for (i in 0 until minPoints.size - 1) {
            drawLine(
                color = minColor,
                start = minPoints[i],
                end = minPoints[i + 1],
                strokeWidth = lineStrokeWidth,
                cap = StrokeCap.Round,
            )
        }
        minPoints.forEach { p -> drawCircle(color = minColor, radius = markerRadius, center = p) }

        // Date labels (aligned with the steps bar chart: labelSmall, centered on each point)
        summaries.forEachIndexed { index, summary ->
            val dateText = summary.date.format(dateFormatter)
            val textLayout = textMeasurer.measure(
                dateText,
                style = dateLabelStyle.copy(color = labelColor),
            )
            drawText(
                textLayout,
                topLeft = Offset(index * stepX + stepX / 2 - textLayout.size.width / 2, chartHeight + 4f),
            )
        }
    }
}

// ============ Sleep Summary Card ============
@Composable
private fun SleepSummaryCard(
    summaries: List<SleepSummary>,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("睡眠记录", style = MaterialTheme.typography.titleMedium)
            if (summaries.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), contentAlignment = Alignment.Center) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val dateFormatter = remember { DateTimeFormatter.ofPattern("M/d") }
                summaries.take(5).forEach { sleep ->
                    val date = Instant.ofEpochMilli(sleep.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "${date.format(dateFormatter)}  ·  ${sleep.totalDuration}分钟",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "深${sleep.deepSleep}  浅${sleep.lightSleep}  REM${sleep.remSleep}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (sleep.isNap) {
                            Text(
                                "小睡",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

// ============ Spo2 And Stress Card ============
@Composable
private fun Spo2AndStressCard(
    spo2: Int?,
    stress: Int?,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("血氧 & 压力", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RingIndicator(
                    icon = Icons.Filled.WaterDrop,
                    label = "血氧",
                    value = spo2,
                    suffix = "%",
                    maxValue = 100,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                RingIndicator(
                    icon = Icons.Filled.Psychology,
                    label = "压力",
                    value = stress,
                    suffix = "",
                    maxValue = 100,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RingIndicator(
    icon: ImageVector,
    label: String,
    value: Int?,
    suffix: String,
    maxValue: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            val progress = ((value ?: 0).toFloat() / maxValue).coerceIn(0f, 1f)
            Canvas(modifier = Modifier.size(80.dp)) {
                val strokeWidth = 8.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val center = Offset(size.width / 2, size.height / 2)

                // Background ring
                drawCircle(
                    color = tint.copy(alpha = 0.15f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )

                // Progress arc
                if (value != null) {
                    drawArc(
                        color = tint,
                        startAngle = -90f,
                        sweepAngle = progress * 360f,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = tint)
        }
        Text(
            if (value != null) "$value$suffix" else "--",
            style = MaterialTheme.typography.titleLarge
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
