package com.example.breakroom.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CalendarWidget(
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Update time every second
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    val calendar = remember(currentTime) {
        Calendar.getInstance().apply { timeInMillis = currentTime }
    }

    // Gradient background colors (purple theme matching web)
    val gradientColors = listOf(
        Color(0xFF667EEA),
        Color(0xFF764BA2)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(colors = gradientColors),
                shape = RoundedCornerShape(0.dp)
            )
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Time Section - more compact
            TimeSection(calendar = calendar)

            // Calendar Grid Section
            CalendarGridSection(
                calendar = calendar,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TimeSection(calendar: Calendar) {
    val timeFormat = remember { SimpleDateFormat("h:mm:ss a", Locale.US) }
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.US) }
    val tzFormat = remember { SimpleDateFormat("zzz", Locale.US) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            // Time display
            Text(
                text = timeFormat.format(calendar.time),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 0.5.sp
            )

            // Date display
            Text(
                text = dateFormat.format(calendar.time),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 11.sp
            )
        }

        // Timezone badge
        Box(
            modifier = Modifier
                .background(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = tzFormat.format(calendar.time),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CalendarGridSection(
    calendar: Calendar,
    modifier: Modifier = Modifier
) {
    val calendarData = remember(calendar.timeInMillis) {
        calculateCalendarData(calendar)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        // Month header
        Text(
            text = calendarData.monthName,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        )

        // Weekday headers
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { day ->
                Text(
                    text = day,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Calendar weeks - fill remaining space evenly
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            calendarData.weeks.forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    week.forEach { day ->
                        DayCell(day = day)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(day: DayInfo) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = if (day.isToday) {
                Modifier
                    .size(22.dp)
                    .background(
                        color = Color.White,
                        shape = CircleShape
                    )
            } else {
                Modifier
            },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.dayNumber.toString(),
                color = when {
                    day.isToday -> Color(0xFF764BA2)
                    day.isCurrentMonth -> Color.White
                    else -> Color.White.copy(alpha = 0.4f)
                },
                fontSize = 11.sp,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Data classes
private data class CalendarData(
    val monthName: String,
    val weeks: List<List<DayInfo>>
)

private data class DayInfo(
    val dayNumber: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean
)

private fun calculateCalendarData(calendar: Calendar): CalendarData {
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val today = calendar.get(Calendar.DAY_OF_MONTH)

    // Get month name
    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
    val monthCalendar = Calendar.getInstance().apply {
        set(year, month, 1)
    }
    val monthName = monthFormat.format(monthCalendar.time)

    // First day of month (0 = Sunday, 6 = Saturday)
    val firstDayOfMonth = Calendar.getInstance().apply {
        set(year, month, 1)
    }
    val startDay = firstDayOfMonth.get(Calendar.DAY_OF_WEEK) - 1 // Convert to 0-indexed

    // Days in current month
    val daysInMonth = Calendar.getInstance().apply {
        set(year, month + 1, 0)
    }.get(Calendar.DAY_OF_MONTH)

    // Days in previous month
    val daysInPrevMonth = Calendar.getInstance().apply {
        set(year, month, 0)
    }.get(Calendar.DAY_OF_MONTH)

    val weeks = mutableListOf<List<DayInfo>>()
    var dayCounter = 1
    var nextMonthDay = 1

    // Always generate 6 weeks for consistent layout
    for (week in 0 until 6) {
        val days = mutableListOf<DayInfo>()
        for (d in 0 until 7) {
            when {
                week == 0 && d < startDay -> {
                    // Previous month
                    days.add(
                        DayInfo(
                            dayNumber = daysInPrevMonth - startDay + d + 1,
                            isCurrentMonth = false,
                            isToday = false
                        )
                    )
                }
                dayCounter > daysInMonth -> {
                    // Next month
                    days.add(
                        DayInfo(
                            dayNumber = nextMonthDay++,
                            isCurrentMonth = false,
                            isToday = false
                        )
                    )
                }
                else -> {
                    // Current month
                    days.add(
                        DayInfo(
                            dayNumber = dayCounter,
                            isCurrentMonth = true,
                            isToday = dayCounter == today
                        )
                    )
                    dayCounter++
                }
            }
        }
        weeks.add(days)
    }

    return CalendarData(monthName = monthName, weeks = weeks)
}
