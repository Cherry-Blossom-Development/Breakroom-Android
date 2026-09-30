package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cherryblossomdev.breakroom.projects.DAY_MS
import com.cherryblossomdev.breakroom.projects.GanttRow
import com.cherryblossomdev.breakroom.projects.GanttScheduler
import com.cherryblossomdev.breakroom.projects.GanttStage
import com.cherryblossomdev.breakroom.projects.HOURS_PER_DAY
import com.cherryblossomdev.breakroom.projects.WorkCalendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

// Web's palette (categorical slots 1-3 validated as a set in both modes;
// Done is recessive neutral context)
private data class GanttColors(
    val backlog: Color, val onDeck: Color, val inProgress: Color, val done: Color,
    val arrow: Color, val weekend: Color
) {
    fun of(stage: GanttStage) = when (stage) {
        GanttStage.BACKLOG -> backlog
        GanttStage.ON_DECK -> onDeck
        GanttStage.IN_PROGRESS -> inProgress
        GanttStage.DONE -> done
    }
}

private val LightGantt = GanttColors(
    Color(0xFF2A78D6), Color(0xFF1BAF7A), Color(0xFFEB6834), Color(0xFFB4B2AB),
    Color(0xFF8A8984), Color(0x09000000)
)
private val DarkGantt = GanttColors(
    Color(0xFF3987E5), Color(0xFF199E70), Color(0xFFD95926), Color(0xFF5C5B56),
    Color(0xFF8F8E87), Color(0x0AFFFFFF)
)

private enum class GanttZoom(val dayWidth: Dp) { DAYS(36.dp), WEEKS(14.dp) }

private val ROW_H = 36.dp
private val BAR_H = 18.dp
private val LABEL_W = 132.dp
private val HEADER_H = 40.dp

private fun ganttDate(time: Long) = SimpleDateFormat("EEE, MMM d", Locale.US).format(Date(time))

// Bars that end exactly at midnight finish at the end of the previous day
private fun formatEnd(time: Long, cal: WorkCalendar): String =
    ganttDate(if (cal.startOfDay(time) == time) cal.addDays(time, -1) else time)

// Scheduled working time: hours under a day, else working days
private fun formatHours(hours: Double): String {
    if (hours < HOURS_PER_DAY) return "${(hours * 100).roundToInt() / 100.0}h".replace(".0h", "h")
    val days = (hours / HOURS_PER_DAY * 10).roundToInt() / 10.0
    val text = if (days % 1.0 == 0.0) days.toLong().toString() else days.toString()
    return "$text working day${if (days == 1.0) "" else "s"}"
}

private fun assigneeName(row: GanttRow) = row.ticket.assigneeName ?: "Unassigned"

private fun rowStatus(row: GanttRow) = when {
    row.overdue -> "! Overdue"
    row.stage == GanttStage.DONE -> "✓ Done"
    else -> row.stage.label
}

// Spoken summary of one bar (web's barLabel)
private fun barLabel(row: GanttRow, cal: WorkCalendar): String {
    val parts = mutableListOf(
        "#${row.ticket.id} ${row.ticket.title}",
        row.stage.label,
        assigneeName(row),
        if (row.unestimated) "no estimate (1 day assumed)" else "${row.ticket.formattedEstimate} estimate",
        "${ganttDate(row.start)} to ${formatEnd(row.end, cal)}"
    )
    if (row.overdue) parts += "overdue"
    return parts.joinToString(", ")
}

// GANTT section of the project workspace (web: ProjectGanttPage.vue). The
// scheduling rules live in projects/GanttSchedule.kt; this only draws. It
// reads the board's already-loaded tickets, dependencies and timeline.
@Composable
fun ProjectGanttScreen(state: ProjectTicketsUiState) {
    val cal = remember { WorkCalendar() }
    val scheduler = remember { GanttScheduler(cal) }
    var includeDone by rememberSaveable { mutableStateOf(false) }
    var zoomName by rememberSaveable { mutableStateOf(GanttZoom.DAYS.name) }
    var tableView by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    val zoom = GanttZoom.valueOf(zoomName)
    val now = remember { System.currentTimeMillis() }

    val schedule = remember(state.tickets, state.dependencies, state.timeline, includeDone) {
        scheduler.build(state.tickets, state.dependencies, state.timeline, now, includeDone)
    }
    val rows = schedule.rows
    val summary = schedule.summary
    val colors = if (isSystemInDarkTheme()) DarkGantt else LightGantt

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
            .testTag("project-gantt"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryTile(
                    "Remaining work", formatHours(summary.remainingHours),
                    "across ${summary.activeCount} ticket${if (summary.activeCount == 1) "" else "s"}",
                    Modifier.weight(1f)
                )
                SummaryTile(
                    "Projected finish", summary.projectedFinish?.let { formatEnd(it, cal) } ?: "—", null,
                    Modifier.weight(1f)
                )
            }
            if (summary.unestimatedCount > 0 || summary.overdueCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (summary.unestimatedCount > 0) {
                        SummaryTile("Unestimated", "${summary.unestimatedCount}", "at 1 day each", Modifier.weight(1f))
                    }
                    if (summary.overdueCount > 0) {
                        SummaryTile(
                            "Overdue", "${summary.overdueCount}", null, Modifier.weight(1f),
                            valueColor = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        GanttControls(
            includeDone = includeDone,
            onIncludeDone = { includeDone = it },
            zoom = zoom,
            onZoom = { zoomName = it.name },
            tableView = tableView,
            onTableView = { tableView = it }
        )
        GanttLegend(colors = colors, includeDone = includeDone)

        when {
            rows.isEmpty() -> Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    if (state.tickets.isEmpty()) "No tickets in this project yet."
                    else "Nothing left to schedule — every ticket is done.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.tickets.isNotEmpty() && !includeDone) {
                    TextButton(onClick = { includeDone = true }) { Text("Show completed") }
                }
            }
            tableView -> GanttTable(rows = rows, cal = cal)
            else -> {
                GanttChart(
                    rows = rows,
                    anchor = schedule.anchor,
                    now = now,
                    zoom = zoom,
                    colors = colors,
                    cal = cal,
                    selectedId = selectedId,
                    onSelect = { selectedId = if (selectedId == it) null else it }
                )
                rows.firstOrNull { it.ticket.id == selectedId }?.let { BarDetail(it, cal, colors) }
            }
        }
    }
}

@Composable
private fun SummaryTile(
    label: String,
    value: String,
    sub: String?,
    modifier: Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GanttControls(
    includeDone: Boolean,
    onIncludeDone: (Boolean) -> Unit,
    zoom: GanttZoom,
    onZoom: (GanttZoom) -> Unit,
    tableView: Boolean,
    onTableView: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onIncludeDone(!includeDone) }
        ) {
            Checkbox(checked = includeDone, onCheckedChange = onIncludeDone)
            Text("Show completed")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                listOf(GanttZoom.DAYS to "Days", GanttZoom.WEEKS to "Weeks").forEachIndexed { i, (z, label) ->
                    SegmentedButton(
                        selected = zoom == z,
                        onClick = { onZoom(z) },
                        enabled = !tableView,
                        shape = SegmentedButtonDefaults.itemShape(i, 2)
                    ) { Text(label) }
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                listOf(false to "Chart", true to "Table").forEachIndexed { i, (table, label) ->
                    SegmentedButton(
                        selected = tableView == table,
                        onClick = { onTableView(table) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2)
                    ) { Text(label) }
                }
            }
        }
    }
}

// Identity is never color alone -- each row also names its status
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GanttLegend(colors: GanttColors, includeDone: Boolean) {
    val critical = MaterialTheme.colorScheme.error
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val stages = listOf(GanttStage.BACKLOG, GanttStage.ON_DECK, GanttStage.IN_PROGRESS) +
            if (includeDone) listOf(GanttStage.DONE) else emptyList()
        stages.forEach { LegendItem(it.label) { Box(Modifier.size(12.dp).background(colors.of(it), MaterialTheme.shapes.extraSmall)) } }
        LegendItem("No estimate (1 day assumed)") {
            Box(Modifier.size(12.dp).border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.shapes.extraSmall))
        }
        LegendItem("Overdue") { Box(Modifier.size(12.dp).border(2.dp, critical, MaterialTheme.shapes.extraSmall)) }
        LegendItem("Depends on") { Box(Modifier.size(width = 18.dp, height = 1.5.dp).background(colors.arrow)) }
        Text(
            "${HOURS_PER_DAY.toInt()}h = 1 working day · weekends skipped",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun GanttChart(
    rows: List<GanttRow>,
    anchor: Long,
    now: Long,
    zoom: GanttZoom,
    colors: GanttColors,
    cal: WorkCalendar,
    selectedId: Int?,
    onSelect: (Int) -> Unit
) {
    val density = LocalDensity.current
    val dayW = zoom.dayWidth
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val critical = MaterialTheme.colorScheme.error
    val today = MaterialTheme.colorScheme.error
    val selectedColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    val smallStyle = TextStyle(fontSize = 10.sp, color = labelColor)

    // Calendar axis: from the Monday on/before the day before the earliest bar
    val earliest = rows.minOf { it.start }.coerceAtMost(anchor)
    val chartStart = cal.startOfDay(cal.addDays(earliest, -1)).let { d ->
        cal.addDays(d, -((cal.dayOfWeek(d) + 6) % 7))
    }
    val latest = rows.maxOf { it.end }.coerceAtLeast(anchor)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val toFill = ceil(((maxWidth - LABEL_W) / dayW).toDouble()).toInt()
        val dayCount = maxOf(cal.daysBetween(chartStart, latest) + 8, 21, toFill)
        val timelineW = dayW * dayCount
        val bodyH = ROW_H * maxOf(rows.size, 1)
        val dayWpx = with(density) { dayW.toPx() }
        fun xOf(time: Long): Float {
            val day = cal.startOfDay(time)
            return (cal.daysBetween(chartStart, day) + (time - day).toFloat() / DAY_MS) * dayWpx
        }
        val scroll = rememberScrollState()
        // Open scrolled to a few days before today
        LaunchedEffect(zoom) { scroll.scrollTo(maxOf((xOf(now) - 3 * dayWpx).toInt(), 0)) }

        Row(modifier = Modifier.fillMaxWidth()) {
            // Fixed ticket labels; each is the bar's TalkBack/tap target too
            Column(modifier = Modifier.width(LABEL_W)) {
                Box(Modifier.height(HEADER_H).fillMaxWidth().padding(start = 16.dp), contentAlignment = Alignment.BottomStart) {
                    Text("Ticket", style = MaterialTheme.typography.labelMedium, color = labelColor)
                }
                HorizontalDivider()
                rows.forEach { row ->
                    Column(
                        modifier = Modifier
                            .height(ROW_H)
                            .fillMaxWidth()
                            .background(if (row.ticket.id == selectedId) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .clickable(onClickLabel = "Show details") { onSelect(row.ticket.id) }
                            .clearAndSetSemantics { contentDescription = barLabel(row, cal) }
                            .padding(start = 16.dp, end = 4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "#${row.ticket.id} ${row.ticket.title}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            rowStatus(row),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (row.overdue) critical else labelColor
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).horizontalScroll(scroll)) {
                Canvas(
                    modifier = Modifier
                        .size(width = timelineW, height = HEADER_H + 1.dp + bodyH)
                        .semantics { contentDescription = "GANTT chart. Tap a ticket name for details, or switch to Table." }
                        .pointerInput(rows, dayCount, zoom) {
                            detectTapGestures { pos ->
                                val top = (HEADER_H + 1.dp).toPx()
                                val i = ((pos.y - top) / ROW_H.toPx()).toInt()
                                if (pos.y >= top && i in rows.indices) onSelect(rows[i].ticket.id)
                            }
                        }
                ) {
                    val headerH = HEADER_H.toPx()
                    val top = headerH + 1.dp.toPx()
                    val rowH = ROW_H.toPx()
                    val barH = BAR_H.toPx()

                    // Header: month labels and day numbers (Mondays only when zoomed out)
                    var lastMonth = ""
                    for (i in 0 until dayCount) {
                        val date = cal.addDays(chartStart, i)
                        val x = i * dayWpx
                        val month = SimpleDateFormat("MMM yyyy", Locale.US).format(Date(date))
                        if (month != lastMonth) {
                            drawText(textMeasurer.measure(month, smallStyle.copy(fontWeight = FontWeight.SemiBold)), topLeft = Offset(x + 2f, 2f))
                            if (i > 0) drawLine(grid, Offset(x, 0f), Offset(x, headerH), strokeWidth = 1f)
                            lastMonth = month
                        }
                        val isMonday = cal.dayOfWeek(date) == 1
                        if (zoom == GanttZoom.DAYS || isMonday) {
                            val label = if (zoom == GanttZoom.DAYS) {
                                SimpleDateFormat("EEEEE", Locale.US).format(Date(date)) + " " + cal.dayOfMonth(date)
                            } else "${cal.dayOfMonth(date)}"
                            val layout = textMeasurer.measure(label, smallStyle)
                            val lx = if (zoom == GanttZoom.DAYS) x + (dayWpx - layout.size.width) / 2 else x + 2f
                            drawText(layout, topLeft = Offset(lx, headerH - layout.size.height - 2f))
                        }
                        if (cal.isWeekend(date)) {
                            drawRect(colors.weekend, topLeft = Offset(x, top), size = Size(dayWpx, size.height - top))
                        }
                    }
                    drawLine(grid, Offset(0f, headerH), Offset(size.width, headerH), strokeWidth = 1.dp.toPx())

                    // Row rules
                    rows.indices.forEach { i ->
                        val y = top + (i + 1) * rowH - 1f
                        drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }

                    // Bars
                    val bars = rows.mapIndexed { i, r ->
                        val x = xOf(r.start)
                        BarGeometry(x, maxOf(xOf(r.end) - x, 4f), top + i * rowH + (rowH - barH) / 2)
                    }
                    val barById = rows.indices.associate { rows[it].ticket.id to bars[it] }

                    // Finish-to-start connectors with a short elbow
                    rows.forEachIndexed { i, r ->
                        val bar = bars[i]
                        r.dependsOn.forEach { predId ->
                            val pred = barById[predId] ?: return@forEach
                            drawArrow(pred, bar, barH, colors.arrow)
                        }
                    }

                    // Today
                    val tx = xOf(now)
                    drawLine(today, Offset(tx, top), Offset(tx, size.height), strokeWidth = 1.5.dp.toPx())
                    drawText(textMeasurer.measure("Today", smallStyle.copy(color = today)), topLeft = Offset(tx + 3f, top + 1f))

                    rows.forEachIndexed { i, r ->
                        val bar = bars[i]
                        val color = colors.of(r.stage)
                        val corner = CornerRadius(3.dp.toPx())
                        val rect = Offset(bar.x, bar.y) to Size(bar.w, barH)
                        if (r.unestimated) {
                            // Hatched + dashed outline: an assumed length
                            drawRoundRect(color.copy(alpha = 0.35f), rect.first, rect.second, corner)
                            clipRect(bar.x, bar.y, bar.x + bar.w, bar.y + barH) {
                                var hx = bar.x - barH
                                while (hx < bar.x + bar.w) {
                                    drawLine(color, Offset(hx, bar.y + barH), Offset(hx + barH, bar.y), strokeWidth = 1.5f)
                                    hx += 6.dp.toPx()
                                }
                            }
                            drawRoundRect(
                                labelColor, rect.first, rect.second, corner,
                                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                            )
                        } else {
                            drawRoundRect(color.copy(alpha = if (r.startKnown) 1f else 0.6f), rect.first, rect.second, corner)
                        }
                        if (r.overdue) drawRoundRect(critical, rect.first, rect.second, corner, style = Stroke(2.dp.toPx()))
                        if (r.ticket.id == selectedId) {
                            drawRoundRect(
                                selectedColor, Offset(bar.x - 2f, bar.y - 2f), Size(bar.w + 4f, barH + 4f), corner,
                                style = Stroke(1.5.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    }
}

// Out of the predecessor's end, into the dependent's start; wraps back
// when the bars overlap
private fun DrawScope.drawArrow(p: BarGeometry, b: BarGeometry, barH: Float, color: Color) {
    val x1 = p.x + p.w
    val y1 = p.y + barH / 2
    val x2 = b.x
    val y2 = b.y + barH / 2
    val path = Path().apply {
        moveTo(x1, y1)
        if (x2 - 6 >= x1 + 6) {
            val midX = maxOf(x1 + 6, minOf(x2 - 6, x1 + 12))
            lineTo(midX, y1)
            lineTo(midX, y2)
        } else {
            lineTo(x1 + 6, y1)
            lineTo(x1 + 6, (y1 + y2) / 2)
            lineTo(x2 - 8, (y1 + y2) / 2)
            lineTo(x2 - 8, y2)
        }
        lineTo(x2 - 1, y2)
    }
    drawPath(path, color, style = Stroke(1.5f * density))
    // Arrowhead
    val head = Path().apply {
        moveTo(x2, y2)
        lineTo(x2 - 7, y2 - 4)
        lineTo(x2 - 7, y2 + 4)
        close()
    }
    drawPath(head, color)
}

// A bar's position in the timeline canvas, in px
private data class BarGeometry(val x: Float, val w: Float, val y: Float)

@Composable
private fun BarDetail(row: GanttRow, cal: WorkCalendar, colors: GanttColors) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("gantt-bar-detail")) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("#${row.ticket.id} ${row.ticket.title}", fontWeight = FontWeight.SemiBold)
            GanttDetailRow("Status") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(colors.of(row.stage), MaterialTheme.shapes.extraSmall))
                    Spacer(Modifier.width(4.dp))
                    Text(row.stage.label + if (row.overdue) " · overdue" else "", style = MaterialTheme.typography.bodyMedium)
                }
            }
            GanttDetailRow("Assignee", assigneeName(row))
            val estimate = (if (row.unestimated) "None — 1 day assumed" else row.ticket.formattedEstimate) +
                if (row.stage == GanttStage.IN_PROGRESS && !row.overdue) " · ${formatHours(row.remainingHours)} left" else ""
            GanttDetailRow("Estimate", estimate)
            GanttDetailRow(
                if (row.stage == GanttStage.DONE) "Worked" else "Scheduled",
                "${ganttDate(row.start)}${if (!row.startKnown) " (est.)" else ""} → ${formatEnd(row.end, cal)}"
            )
            if (row.dependsOn.isNotEmpty()) GanttDetailRow("Depends on", row.dependsOn.joinToString(", ") { "#$it" })
            if (row.externalBlockers.isNotEmpty()) {
                GanttDetailRow(
                    "Waiting on",
                    row.externalBlockers.joinToString(", ") { "#$it" } + " (another project — not scheduled here)"
                )
            }
        }
    }
}

@Composable
private fun GanttDetailRow(label: String, value: String) = GanttDetailRow(label) {
    Text(value, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun GanttDetailRow(label: String, value: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Box(modifier = Modifier.weight(1f)) { value() }
    }
}

// Same schedule, readable without color (one card per ticket on a phone
// instead of web's 7-column table)
@Composable
private fun GanttTable(rows: List<GanttRow>, cal: WorkCalendar) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp).testTag("gantt-table"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { r ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("#${r.ticket.id} ${r.ticket.title}", fontWeight = FontWeight.SemiBold)
                    GanttDetailRow("Status", if (r.overdue) "! Overdue" else r.stage.label)
                    GanttDetailRow("Assignee", assigneeName(r))
                    GanttDetailRow("Estimate", if (r.unestimated) "— (1d)" else r.ticket.formattedEstimate)
                    GanttDetailRow("Start", ganttDate(r.start) + if (!r.startKnown) " (est.)" else "")
                    GanttDetailRow("Finish", formatEnd(r.end, cal))
                    GanttDetailRow(
                        "Depends on",
                        (if (r.dependsOn.isEmpty()) "—" else r.dependsOn.joinToString(", ") { "#$it" }) +
                            if (r.externalBlockers.isNotEmpty()) {
                                " (other project: ${r.externalBlockers.joinToString(", ") { "#$it" }})"
                            } else ""
                    )
                }
            }
        }
    }
}
