package com.neuralcast.radioplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neuralcast.radioplayer.model.RadioStation
import com.neuralcast.radioplayer.model.ScheduleSegmentKind
import com.neuralcast.radioplayer.model.StationScheduleDayState
import com.neuralcast.radioplayer.model.StationScheduleSegment
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ScheduleScreen(
    station: RadioStation,
    scheduleDays: Map<String, StationScheduleDayState>,
    onLoadDate: (LocalDate, Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    val zoneId = remember(station.timezoneId) { ZoneId.of(station.timezoneId) }
    var selectedDateText by rememberSaveable(station.id) {
        mutableStateOf(LocalDate.now(zoneId).toString())
    }
    val selectedDate = remember(selectedDateText) { LocalDate.parse(selectedDateText) }
    val dayState = scheduleDays["${station.id}|$selectedDate"]
    val isToday = selectedDate == LocalDate.now(zoneId)

    LaunchedEffect(station.id, selectedDate) {
        onLoadDate(selectedDate, false)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = station.name)
                        Text(
                            text = "Schedule",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScheduleDateHeader(
                selectedDate = selectedDate,
                zoneId = zoneId,
                isToday = isToday,
                onPreviousDay = { selectedDateText = selectedDate.minusDays(1).toString() },
                onNextDay = { selectedDateText = selectedDate.plusDays(1).toString() },
                onJumpToToday = { selectedDateText = LocalDate.now(zoneId).toString() }
            )

            ScheduleOverviewCard(
                dayState = dayState,
                zoneId = zoneId,
                isToday = isToday,
                onRefresh = { onLoadDate(selectedDate, true) }
            )

            ScheduleLegend()

            when {
                dayState == null || (dayState.isLoading && dayState.segments.isEmpty()) -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                dayState.errorMessage != null && dayState.segments.isEmpty() -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = dayState.errorMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(onClick = { onLoadDate(selectedDate, true) }) {
                                Text("Try Again")
                            }
                        }
                    }
                }

                else -> {
                    ScheduleTimeline(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        zoneId = zoneId,
                        selectedDate = selectedDate,
                        segments = dayState.segments,
                        showCurrentTimeLine = isToday
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleDateHeader(
    selectedDate: LocalDate,
    zoneId: ZoneId,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onJumpToToday: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousDay) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous day"
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = selectedDate.format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = onNextDay) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next day"
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Station timezone: ${zoneId.id}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isToday) {
                TextButton(onClick = onJumpToToday) {
                    Text("Today")
                }
            }
        }
    }
}

@Composable
private fun ScheduleOverviewCard(
    dayState: StationScheduleDayState?,
    zoneId: ZoneId,
    isToday: Boolean,
    onRefresh: () -> Unit
) {
    val nowMillis by produceState(
        initialValue = Instant.now().toEpochMilli(),
        key1 = zoneId,
        key2 = isToday
    ) {
        value = Instant.now().toEpochMilli()
        while (isToday) {
            delay(60_000L)
            value = Instant.now().toEpochMilli()
        }
    }

    val segments = dayState?.segments.orEmpty()
    val liveSegment = if (isToday) {
        segments.firstOrNull { nowMillis in it.startMillis until it.endMillis }
    } else {
        null
    }
    val upNextSegment = when {
        liveSegment != null -> segments.firstOrNull { it.startMillis >= liveSegment.endMillis }
        isToday -> segments.firstOrNull { it.startMillis > nowMillis }
        else -> segments.firstOrNull()
    }
    val primaryLabel = when {
        liveSegment != null -> "Live now"
        upNextSegment != null && isToday -> "Next up"
        upNextSegment != null -> "Starts with"
        else -> null
    }
    val scheduledCount = segments.count { it.kind == ScheduleSegmentKind.Scheduled }
    val openCount = segments.size - scheduledCount

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Day overview",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${segments.size} blocks · $scheduledCount scheduled · $openCount open",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onRefresh) {
                    Text(if (dayState?.isLoading == true) "Refreshing..." else "Refresh")
                }
            }

            when {
                dayState == null || (dayState.isLoading && segments.isEmpty()) -> {
                    Text(
                        text = "Loading schedule data for this station.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                primaryLabel != null && (liveSegment != null || upNextSegment != null) -> {
                    ScheduleOverviewRow(
                        label = primaryLabel,
                        segment = liveSegment ?: upNextSegment,
                        zoneId = zoneId
                    )
                    if (liveSegment != null) {
                        upNextSegment?.let { segment ->
                            ScheduleOverviewRow(
                                label = "Then",
                                segment = segment,
                                zoneId = zoneId
                            )
                        }
                    }
                }

                else -> {
                    Text(
                        text = "No schedule blocks are published for this day yet.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            dayState?.errorMessage?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = "All times shown in ${zoneId.id}.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScheduleOverviewRow(
    label: String,
    segment: StationScheduleSegment?,
    zoneId: ZoneId
) {
    if (segment == null) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = scheduleSegmentTitle(segment),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = formatScheduleTimeRange(segment, zoneId),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        scheduleSegmentDetail(segment, maxPlaylistNames = 3)?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun ScheduleLegend() {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LegendChip(
            label = "Scheduled",
            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        LegendChip(
            label = "Open slot",
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LegendChip(
            label = "Open rotation",
            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun LegendChip(
    label: String,
    backgroundColor: Color,
    contentColor: Color
) {
    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun ScheduleTimeline(
    modifier: Modifier = Modifier,
    zoneId: ZoneId,
    selectedDate: LocalDate,
    segments: List<StationScheduleSegment>,
    showCurrentTimeLine: Boolean
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val hourHeight = 88.dp
    val timeColumnWidth = 68.dp
    val totalHeight = hourHeight * 24
    val currentMinute by produceState(
        initialValue = currentMinuteOfDay(zoneId),
        key1 = zoneId,
        key2 = showCurrentTimeLine
    ) {
        while (showCurrentTimeLine) {
            value = currentMinuteOfDay(zoneId)
            delay(60_000L)
        }
    }

    LaunchedEffect(selectedDate, showCurrentTimeLine, segments.size) {
        val targetOffset = with(density) {
            if (showCurrentTimeLine) {
                ((currentMinute / 60f) * hourHeight.toPx() - 220.dp.toPx()).roundToInt()
            } else {
                0
            }
        }
        scrollState.animateScrollTo(targetOffset.coerceAtLeast(0))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight)
        ) {
            val blockOffsetX = timeColumnWidth + 8.dp
            val blockWidth = maxWidth - blockOffsetX - 16.dp

            Column(modifier = Modifier.fillMaxWidth()) {
                repeat(24) { hour ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(hourHeight)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(timeColumnWidth)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Text(
                                text = "%02d:00".format(hour),
                                modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.align(Alignment.TopCenter),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            HorizontalDivider(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(end = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }

            segments.forEach { segment ->
                val startMinutes = minutesFromStartOfDay(segment.startMillis, zoneId, selectedDate)
                val endMinutes = minutesFromStartOfDay(segment.endMillis, zoneId, selectedDate)
                val topOffset = hourHeight * (startMinutes / 60f)
                val blockHeight = hourHeight * ((endMinutes - startMinutes) / 60f)

                ScheduleSegmentCard(
                    modifier = Modifier
                        .offset(x = blockOffsetX, y = topOffset)
                        .width(blockWidth)
                        .height(blockHeight)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    zoneId = zoneId,
                    segment = segment
                )
            }

            if (showCurrentTimeLine) {
                val lineOffset = hourHeight * (currentMinute / 60f)
                val currentTimeText = remember(currentMinute) {
                    Instant.now().atZone(zoneId).format(DateTimeFormatter.ofPattern("HH:mm"))
                }

                Surface(
                    modifier = Modifier.offset(x = 8.dp, y = lineOffset - 10.dp),
                    color = Color(0xFFC62828),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = currentTimeText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .offset(x = blockOffsetX, y = lineOffset)
                        .width(blockWidth)
                        .height(2.dp)
                        .background(Color(0xFFC62828))
                )
            }
        }
    }
}

@Composable
private fun ScheduleSegmentCard(
    modifier: Modifier,
    zoneId: ZoneId,
    segment: StationScheduleSegment
) {
    val (containerColor, contentColor) = when (segment.kind) {
        ScheduleSegmentKind.Scheduled -> {
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        }
        ScheduleSegmentKind.OpenSlot -> {
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
        ScheduleSegmentKind.OpenRotation -> {
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val compactBlock = maxHeight < 84.dp
        val showDetail = maxHeight >= 104.dp
        val contentPadding = if (compactBlock) 8.dp else 12.dp

        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(if (compactBlock) 2.dp else 4.dp)
            ) {
                Text(
                    text = formatScheduleTimeRange(segment, zoneId),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.82f),
                    maxLines = 1
                )
                Text(
                    text = scheduleSegmentTitle(segment),
                    style = if (compactBlock) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = if (compactBlock) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (showDetail) {
                    scheduleSegmentDetail(
                        segment = segment,
                        maxPlaylistNames = 4,
                        multiline = true
                    )?.let { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.88f),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun minutesFromStartOfDay(epochMillis: Long, zoneId: ZoneId, selectedDate: LocalDate): Float {
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
    if (dateTime.toLocalDate().isAfter(selectedDate)) {
        return 24f * 60f
    }
    if (dateTime.toLocalDate().isBefore(selectedDate)) {
        return 0f
    }
    return dateTime.hour * 60f + dateTime.minute + (dateTime.second / 60f)
}

private fun currentMinuteOfDay(zoneId: ZoneId): Float {
    val now = Instant.now().atZone(zoneId)
    return now.hour * 60f + now.minute + (now.second / 60f)
}
