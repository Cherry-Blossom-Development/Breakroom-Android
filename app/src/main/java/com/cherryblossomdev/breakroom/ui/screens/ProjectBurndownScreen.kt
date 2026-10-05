package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cherryblossomdev.breakroom.projects.BurndownDay
import com.cherryblossomdev.breakroom.projects.BurndownMeasure
import com.cherryblossomdev.breakroom.projects.BurndownResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

// Series colors from web (emphasis form: actual is the one accent series,
// ideal is recessive gray context)
private val ActualLight = Color(0xFF2A78D6)
private val ActualDark = Color(0xFF3987E5)
private val IdealLight = Color(0xFF8A8984)
private val IdealDark = Color(0xFF8F8E87)

private fun round1(n: Double) = (n * 10).roundToInt() / 10.0

private fun num(n: Double): String = round1(n).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }

private fun formatAmount(n: Double?, measure: BurndownMeasure): String {
    if (n == null) return "—"
    val v = round1(n)
    val unit = if (measure == BurndownMeasure.TICKETS) "ticket" else "day"
    return "${num(v)} $unit${if (v == 1.0) "" else "s"}"
}

private fun formatShort(n: Double, measure: BurndownMeasure) =
    if (measure == BurndownMeasure.TICKETS) num(n) else "${num(n)}d"

private fun formatDate(time: Long, pattern: String): String =
    SimpleDateFormat(pattern, Locale.US).format(Date(time))

@Composable
fun ProjectBurndownScreen(
    viewModel: ProjectBurndownViewModel,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val b = state.burndown
    val sprint = state.sprint

    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.error != null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
        }
        b == null || sprint == null -> Unit
        else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("project-burndown"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SprintNav(state = state, viewModel = viewModel)
            Controls(state = state, viewModel = viewModel)
            Summary(b = b, measure = state.measure)

            val hasTickets = state.data?.tickets?.isNotEmpty() == true
            if (!hasTickets) {
                Text("This project has no tickets yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (state.view == BurndownView.CHART) {
                BurndownChart(
                    b = b,
                    start = sprint.start,
                    end = sprint.end,
                    now = state.now,
                    measure = state.measure,
                    sprintNumber = state.sprintIndex + 1,
                    selectedDay = state.selectedDay,
                    onSelectDay = { viewModel.selectDay(it) }
                )
                state.selectedDay?.let { i -> b.days.getOrNull(i)?.let { DayDetail(it, state.measure) } }
            } else {
                BurndownTable(days = b.days, measure = state.measure)
            }

            if (hasTickets) {
                Notes(state = state, b = b, onOpenSettings = onOpenSettings)
            }
        }
    }
}

@Composable
private fun SprintNav(state: ProjectBurndownUiState, viewModel: ProjectBurndownViewModel) {
    val sprint = state.sprint ?: return
    val label = "${formatDate(sprint.start, "MMM d")} – ${formatDate(sprint.end - 1, "MMM d, yyyy")}"
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.previousSprint() }, enabled = state.sprintIndex > 0) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous sprint")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text("Sprint ${state.sprintIndex + 1}", fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { viewModel.nextSprint() }, enabled = state.sprintIndex < state.currentIndex) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next sprint")
        }
    }
    if (state.sprintIndex != state.currentIndex) {
        TextButton(onClick = { viewModel.currentSprint() }) { Text("Current sprint") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Controls(state: ProjectBurndownUiState, viewModel: ProjectBurndownViewModel) {
    val categories = state.data?.tickets.orEmpty().filter { it.split_mode == "category" }
    if (categories.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        val selected = categories.firstOrNull { it.id == state.categoryId }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected?.let { "#${it.id} ${it.title}" } ?: "All work",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .testTag("burndown-category")
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("All work") }, onClick = {
                    viewModel.setCategory(null)
                    expanded = false
                })
                categories.forEach { c ->
                    DropdownMenuItem(text = { Text("#${c.id} ${c.title}") }, onClick = {
                        viewModel.setCategory(c.id)
                        expanded = false
                    })
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            listOf(BurndownMeasure.WORK to "Work", BurndownMeasure.TICKETS to "Tickets").forEachIndexed { i, (m, label) ->
                SegmentedButton(
                    selected = state.measure == m,
                    onClick = { viewModel.setMeasure(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2)
                ) { Text(label) }
            }
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            listOf(BurndownView.CHART to "Chart", BurndownView.TABLE to "Table").forEachIndexed { i, (v, label) ->
                SegmentedButton(
                    selected = state.view == v,
                    onClick = { viewModel.setView(v) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2)
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun Summary(b: BurndownResult, measure: BurndownMeasure) {
    // How the actual line compares with the ideal line right now
    val pace: Pair<String, String>? = if (!b.started) null else {
        val diff = round1(b.remaining - b.idealNow)
        when {
            abs(diff) < 0.1 -> "On track" to "matching the ideal line"
            diff > 0 -> "Behind" to "${formatAmount(diff, measure)} above the ideal line"
            else -> "Ahead" to "${formatAmount(-diff, measure)} below the ideal line"
        }
    }
    val stats = listOfNotNull(
        Triple(if (b.finished) "Left at sprint end" else "Remaining", formatAmount(b.remaining, measure),
            "of ${formatAmount(b.startRemaining, measure)} at start"),
        Triple("Completed", formatAmount(b.completed, measure), null),
        Triple("Added", formatAmount(b.added, measure), "new or reopened"),
        pace?.let { Triple("Pace", it.first, it.second) }
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value, sub) ->
                    Card(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            sub?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// "Nice" y ticks from 0 to just above the highest value
private fun yTicks(b: BurndownResult): List<Double> {
    val max = maxOf(b.startRemaining, b.days.maxOfOrNull { it.remaining ?: 0.0 } ?: 0.0, 1.0)
    val rough = max / 4
    val mag = 10.0.pow(floor(log10(rough)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= rough }
    val top = ceil(max / step) * step
    return (0..(top / step).roundToInt()).map { round1(it * step) }
}

@Composable
private fun BurndownChart(
    b: BurndownResult,
    start: Long,
    end: Long,
    now: Long,
    measure: BurndownMeasure,
    sprintNumber: Int,
    selectedDay: Int?,
    onSelectDay: (Int?) -> Unit
) {
    val dark = isSystemInDarkTheme()
    val actual = if (dark) ActualDark else ActualLight
    val ideal = if (dark) IdealDark else IdealLight
    val grid = MaterialTheme.colorScheme.outlineVariant
    val weekend = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.04f else 0.035f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val todayColor = MaterialTheme.colorScheme.error
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = labelColor)
    val ticks = remember(b) { yTicks(b) }
    val days = b.days
    val todayInSprint = now in start until end

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Legend
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendKey(actual, "Actual remaining")
            LegendKey(ideal, "Ideal")
            if (todayInSprint) LegendKey(todayColor, "Today")
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .semantics {
                    contentDescription = "Burndown for sprint $sprintNumber: ${formatAmount(b.remaining, measure)} " +
                        "remaining of ${formatAmount(b.startRemaining, measure)}. Switch to Table for day-by-day numbers."
                }
                .pointerInput(days.size) {
                    val left = 44.dp.toPx()
                    val right = 44.dp.toPx()
                    fun dayAt(x: Float): Int? {
                        val plotW = size.width - left - right
                        val i = floor((x - left) / plotW * days.size).toInt()
                        return i.takeIf { it in days.indices }
                    }
                    detectTapGestures { onSelectDay(dayAt(it.x)) }
                }
                .pointerInput(days.size) {
                    val left = 44.dp.toPx()
                    val right = 44.dp.toPx()
                    detectHorizontalDragGestures { change, _ ->
                        val plotW = size.width - left - right
                        val i = floor((change.position.x - left) / plotW * days.size).toInt()
                        if (i in days.indices) onSelectDay(i)
                    }
                }
        ) {
            val mLeft = 44.dp.toPx()
            val mRight = 44.dp.toPx()
            val mTop = 16.dp.toPx()
            val mBottom = 28.dp.toPx()
            val plotW = size.width - mLeft - mRight
            val plotH = size.height - mTop - mBottom
            val span = (end - start).toFloat()
            val yMax = ticks.last().toFloat()
            fun xOf(t: Long) = mLeft + (t - start) / span * plotW
            fun yOf(v: Double) = mTop + plotH - (v.toFloat() / yMax) * plotH
            val dayWidth = plotW / days.size

            // Weekends
            days.filter { it.weekend }.forEach {
                drawRect(weekend, topLeft = Offset(xOf(it.date), mTop), size = Size(dayWidth, plotH))
            }
            // Grid + y labels
            ticks.forEach { v ->
                val y = yOf(v)
                drawLine(grid, Offset(mLeft, y), Offset(mLeft + plotW, y), strokeWidth = 1.dp.toPx())
                val layout = textMeasurer.measure(num(v), labelStyle)
                drawText(layout, topLeft = Offset(mLeft - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2))
            }
            // x labels: every day when there's room, else Mondays
            val roomy = dayWidth >= 30.dp.toPx()
            days.forEachIndexed { i, d ->
                val isMonday = i == 0 || formatDate(d.date, "EEE") == "Mon"
                if (!roomy && !isMonday) return@forEachIndexed
                val text = if (roomy) formatDate(d.date, "d") else formatDate(d.date, "MMM d")
                val layout = textMeasurer.measure(text, labelStyle)
                val x = if (roomy) xOf(d.date) + dayWidth / 2 - layout.size.width / 2 else xOf(d.date)
                drawText(layout, topLeft = Offset(x, mTop + plotH + 6.dp.toPx()))
            }

            // Ideal: flat across weekends, so draw it through every day boundary
            val idealPath = Path().apply {
                moveTo(xOf(start), yOf(b.startRemaining))
                days.forEach { lineTo(xOf(it.end), yOf(it.ideal)) }
            }
            drawPath(
                idealPath, ideal,
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            )
            // Direct label on the ideal line's end
            val idealLabel = textMeasurer.measure("Ideal", labelStyle.copy(color = ideal))
            drawText(idealLabel, topLeft = Offset(xOf(end) + 4.dp.toPx(), yOf(days.last().ideal) - idealLabel.size.height))

            // Today
            if (todayInSprint) {
                val x = xOf(now)
                drawLine(
                    todayColor, Offset(x, mTop), Offset(x, mTop + plotH), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
            }

            // Actual: from the sprint start through each day that has begun
            if (b.started) {
                val points = listOf(Offset(xOf(start), yOf(b.startRemaining))) +
                    days.filter { it.remaining != null }.map { Offset(xOf(it.at!!), yOf(it.remaining!!)) }
                if (points.size >= 2) {
                    val base = yOf(0.0)
                    val area = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, base)
                        lineTo(points.first().x, base)
                        close()
                    }
                    drawPath(area, actual.copy(alpha = 0.12f))
                }
                val line = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(line, actual, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val last = points.last()
                drawCircle(actual, radius = 4.5.dp.toPx(), center = last)
                val endLabel = textMeasurer.measure(formatShort(b.remaining, measure), labelStyle.copy(color = actual, fontWeight = FontWeight.SemiBold))
                drawText(endLabel, topLeft = Offset(last.x + 8.dp.toPx(), last.y - endLabel.size.height / 2))
            }

            // Selected day crosshair
            selectedDay?.let { days.getOrNull(it) }?.let { d ->
                val x = xOf(d.date) + dayWidth / 2
                drawLine(labelColor, Offset(x, mTop), Offset(x, mTop + plotH), strokeWidth = 1.dp.toPx())
                if (d.remaining != null && d.at != null) {
                    drawCircle(actual, radius = 4.5.dp.toPx(), center = Offset(xOf(d.at), yOf(d.remaining)))
                }
            }
        }
        Text(
            "Tap or drag across the chart to see a day.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendKey(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(width = 16.dp, height = 3.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DayDetail(day: BurndownDay, measure: BurndownMeasure) {
    Card(modifier = Modifier.fillMaxWidth().testTag("burndown-day-detail")) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatDate(day.date, "EEE, MMM d") + if (day.isToday) " · today" else "",
                fontWeight = FontWeight.SemiBold
            )
            DetailRow("Remaining", if (day.remaining == null) "Not yet" else formatAmount(day.remaining, measure))
            DetailRow("Ideal", formatAmount(day.ideal, measure))
            if (day.remaining != null) {
                DetailRow("Completed", formatAmount(day.completed, measure))
                DetailRow("Added", formatAmount(day.added, measure))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BurndownTable(days: List<BurndownDay>, measure: BurndownMeasure) {
    val header = listOf("Day", "Remaining", "Ideal", "Completed", "Added")
    Card(modifier = Modifier.fillMaxWidth().testTag("burndown-table")) {
        Column(modifier = Modifier.padding(12.dp)) {
            TableRow(header, bold = true)
            HorizontalDivider()
            days.forEach { d ->
                val future = d.remaining == null
                TableRow(
                    listOf(
                        formatDate(d.date, "EEE, MMM d") + if (d.isToday) " (today)" else "",
                        d.remaining?.let { formatShort(it, measure) } ?: "—",
                        formatShort(d.ideal, measure),
                        if (future) "—" else formatShort(d.completed, measure),
                        if (future) "—" else formatShort(d.added, measure)
                    ),
                    bold = d.isToday,
                    dim = future
                )
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, bold: Boolean = false, dim: Boolean = false) {
    val color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        cells.forEachIndexed { i, text ->
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (bold) FontWeight.SemiBold else null,
                color = color,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(if (i == 0) 1.6f else 1f)
            )
        }
    }
}

@Composable
private fun Notes(state: ProjectBurndownUiState, b: BurndownResult, onOpenSettings: () -> Unit) {
    val style = MaterialTheme.typography.bodySmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "${state.sprintDays / 7}-week sprints, starting ${formatDate(state.anchor, "EEEE, MMM d, yyyy")}.",
            style = style, color = color
        )
        TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(0.dp)) { Text("Change sprint length") }
        if (state.measure == BurndownMeasure.WORK) {
            val unestimated = if (b.unestimatedCount > 0) {
                " ${b.unestimatedCount} unestimated ticket${if (b.unestimatedCount == 1) "" else "s"} counted as 1 day each."
            } else ""
            Text("Work uses each ticket's current estimate, in 8-hour working days.$unestimated", style = style, color = color)
        }
        if (b.approximate) {
            Text(
                "Status history is only recorded from Sep 28, 2026; earlier days are reconstructed from resolved dates.",
                style = style, color = color
            )
        }
    }
}
