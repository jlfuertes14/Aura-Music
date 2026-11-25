package com.auramusic.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auramusic.app.core.media.AutoEqProfile
import com.auramusic.app.core.media.AutoEqProfiles
import com.auramusic.app.domain.model.EQUALIZER_BAND_LABELS
import com.auramusic.app.domain.model.EqualizerPreset
import com.auramusic.app.domain.model.SIMPMUSIC_ALL_EQ_PRESETS
import com.auramusic.app.domain.model.findMatchingPreset
import com.auramusic.app.ui.theme.LocalAuraColors
import com.auramusic.app.ui.theme.auraGlassSheetColor
import java.util.Locale
import kotlin.math.roundToInt

private const val BAND_RANGE_DB = 12f
private const val PREAMP_MIN_DB = -15f
private val CURVE_HEIGHT = 200.dp

@Composable
fun EqualizerWaveSection(
    bands: List<Float>,
    preampDb: Float,
    presetName: String,
    onBandsChange: (List<Float>) -> Unit,
    onPreampChange: (Float) -> Unit,
    onPresetSelect: (EqualizerPreset) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    activeAutoEqProfileId: String? = null,
    onSelectAutoEqProfile: ((String?) -> Unit)? = null
) {
    val auraColors = LocalAuraColors.current
    val accentTint = auraReadableAccent(auraColors.accent, auraColors.isDark)
    var draftPreamp by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(preampDb) { draftPreamp = null }

    var presetMenuOpen by remember { mutableStateOf(false) }
    var autoEqSheetOpen by remember { mutableStateOf(false) }
    val activePreset = remember(bands) { findMatchingPreset(bands) }
    val activeAutoEqProfile = remember(activeAutoEqProfileId) {
        activeAutoEqProfileId?.let { id -> AutoEqProfiles.ALL_PROFILES.firstOrNull { it.id == id } }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = auraColors.surfaceCard,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 1. Preset Selector Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Curve Preset",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = auraColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(auraColors.circleButtonBg)
                                .clickable { presetMenuOpen = true }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = activePreset?.name ?: presetName.ifBlank { "Custom" },
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = auraColors.textPrimary
                            )
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Select Preset",
                                tint = accentTint,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = presetMenuOpen,
                            onDismissRequest = { presetMenuOpen = false },
                            modifier = Modifier.background(auraColors.surfaceElevated)
                        ) {
                            SIMPMUSIC_ALL_EQ_PRESETS.forEach { preset ->
                                val isSelected = (activePreset?.name ?: presetName) == preset.name
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = preset.name,
                                                color = if (isSelected) accentTint else auraColors.textPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = preset.subtitle,
                                                color = auraColors.textSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = accentTint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            Box(modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    onClick = {
                                        onPresetSelect(preset)
                                        presetMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Reset button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onReset() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = "Reset EQ",
                        tint = auraColors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Reset",
                        fontSize = 12.sp,
                        color = auraColors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Preamp Headroom Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Preamp",
                    fontSize = 12.sp,
                    color = auraColors.textSecondary,
                    modifier = Modifier.width(52.dp)
                )
                Slider(
                    value = draftPreamp ?: preampDb,
                    onValueChange = { draftPreamp = it.roundToInt().toFloat() },
                    onValueChangeFinished = { draftPreamp?.let(onPreampChange) },
                    valueRange = PREAMP_MIN_DB..0f,
                    colors = SliderDefaults.colors(
                        thumbColor = accentTint,
                        activeTrackColor = accentTint,
                        inactiveTrackColor = auraColors.pillInactiveBg
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(draftPreamp ?: preampDb).roundToInt()} dB",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = auraColors.textPrimary,
                    textAlign = TextAlign.End,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    modifier = Modifier.width(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Interactive Frequency Wave Curve
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                // dB Scale labels on left
                Column(
                    modifier = Modifier
                        .width(42.dp)
                        .height(CURVE_HEIGHT)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "+${BAND_RANGE_DB.roundToInt()}dB",
                        fontSize = 10.sp,
                        color = auraColors.textSecondary
                    )
                    Text(
                        text = "0dB",
                        fontSize = 10.sp,
                        color = auraColors.textMuted
                    )
                    Text(
                        text = "-${BAND_RANGE_DB.roundToInt()}dB",
                        fontSize = 10.sp,
                        color = auraColors.textSecondary
                    )
                }

                EqualizerWavePlot(
                    bands = bands,
                    curveColor = accentTint,
                    gridColor = if (auraColors.isDark) Color(0xFF252936) else Color.Black.copy(alpha = 0.09f),
                    handleCenterColor = auraColors.surfaceCard,
                    onBandsChange = onBandsChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(CURVE_HEIGHT)
                        .padding(start = 8.dp)
                )
            }

            // Frequency Labels at the bottom aligned with grid steps
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 50.dp, top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                EQUALIZER_BAND_LABELS.forEach { label ->
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        color = auraColors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (onSelectAutoEqProfile != null) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = auraColors.divider, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { autoEqSheetOpen = true }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = if (activeAutoEqProfile != null) accentTint else auraColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "AutoEq Headphone Profile",
                                fontSize = 11.5.sp,
                                color = auraColors.textSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = activeAutoEqProfile?.name ?: "None (Manual)",
                                fontSize = 14.sp,
                                color = if (activeAutoEqProfile != null) accentTint else auraColors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Select AutoEq Headphone Profile",
                        tint = accentTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (autoEqSheetOpen && onSelectAutoEqProfile != null) {
        AutoEqProfilePickerSheet(
            activeProfileId = activeAutoEqProfileId,
            onSelectProfile = { profileId ->
                onSelectAutoEqProfile(profileId)
                autoEqSheetOpen = false
            },
            onDismiss = { autoEqSheetOpen = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoEqProfilePickerSheet(
    activeProfileId: String?,
    onSelectProfile: (String?) -> Unit,
    onDismiss: () -> Unit,
    profiles: List<AutoEqProfile> = AutoEqProfiles.ALL_PROFILES,
    containerColor: Color = auraGlassSheetColor(LocalAuraColors.current.sheetBackground)
) {
    val auraColors = LocalAuraColors.current
    val accentTint = auraReadableAccent(auraColors.accent, auraColors.isDark)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    val filteredProfiles = remember(profiles, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) {
            profiles
        } else {
            profiles.filter {
                it.name.lowercase(Locale.ROOT).contains(q) ||
                    it.source.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = containerColor,
        scrimColor = auraColors.scrim,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(auraColors.dragHandle)
            )
        }
    ) {
        ForceWhiteSystemBarsEffect()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AutoEq Headphone Profile",
                        style = MaterialTheme.typography.titleMedium,
                        color = auraColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Harman target compensation for popular IEMs & headphones",
                        style = MaterialTheme.typography.bodySmall,
                        color = auraColors.textSecondary
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close AutoEq Picker",
                        tint = auraColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Borderless search field
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    color = auraColors.textPrimary,
                    fontWeight = FontWeight.Normal
                ),
                singleLine = true,
                cursorBrush = SolidColor(accentTint),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(auraColors.surfaceCard)
                    .padding(horizontal = 12.dp),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = auraColors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search headphones or IEMs…",
                                    color = auraColors.textSecondary,
                                    fontSize = 13.5.sp
                                )
                            }
                            innerTextField()
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = auraColors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(key = "__none__") {
                    val isNoneSelected = activeProfileId.isNullOrBlank()
                    AutoEqProfileRow(
                        title = "None (Manual)",
                        subtitle = "Use your manual 10-band EQ curve without AutoEq compensation",
                        isSelected = isNoneSelected,
                        onClick = { onSelectProfile(null) }
                    )
                }

                items(filteredProfiles, key = { it.id }) { profile ->
                    val isSelected = activeProfileId == profile.id
                    val preampLabel = String.format(Locale.US, "%.1f dB preamp", profile.preampDb)
                    AutoEqProfileRow(
                        title = profile.name,
                        subtitle = "${profile.source} · $preampLabel",
                        isSelected = isSelected,
                        onClick = { onSelectProfile(profile.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoEqProfileRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val auraColors = LocalAuraColors.current
    val accentTint = auraReadableAccent(auraColors.accent, auraColors.isDark)
    val selectedBg = auraColors.accent.copy(alpha = if (auraColors.isDark) 0.14f else 0.18f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) selectedBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isSelected) accentTint else auraColors.textPrimary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = auraColors.textSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isSelected) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = accentTint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun EqualizerWavePlot(
    bands: List<Float>,
    curveColor: Color,
    gridColor: Color,
    handleCenterColor: Color,
    onBandsChange: (List<Float>) -> Unit,
    modifier: Modifier = Modifier
) {
    val count = EQUALIZER_BAND_LABELS.size
    var draft by remember { mutableStateOf<List<Float>?>(null) }
    val shown = draft ?: bands

    val currentBands by rememberUpdatedState(bands)
    val commit by rememberUpdatedState(onBandsChange)

    LaunchedEffect(bands) { draft = null }

    Box(
        modifier = modifier
            .pointerInput(count) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var working = currentBands.withBandAt(down.position, size.width, size.height, count)
                    draft = working
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        working = working.withBandAt(change.position, size.width, size.height, count)
                        draft = working
                        change.consume()
                    }
                    commit(working)
                }
            }
            .drawBehind {
                val step = size.width / (count - 1)
                val midY = size.height / 2f

                // Draw center 0 dB horizontal line
                drawLine(
                    color = gridColor.copy(alpha = 0.8f),
                    start = Offset(0f, midY),
                    end = Offset(size.width, midY),
                    strokeWidth = 1.5f
                )

                // Draw vertical grid lines at each frequency band
                repeat(count) { i ->
                    val x = step * i
                    drawLine(
                        color = gridColor.copy(alpha = 0.45f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                }

                // Compute points for the 10 bands
                val points = List(count) { i ->
                    Offset(step * i, gainToY(shown.getOrElse(i) { 0f }, size.height))
                }

                // Catmull-Rom cubic Bézier smooth line
                val line = smoothPath(points)

                // Shaded gradient area under the curve
                val area = Path().apply {
                    addPath(line)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }

                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            curveColor.copy(alpha = 0.28f),
                            curveColor.copy(alpha = 0.02f)
                        )
                    )
                )

                // Vivid stroke line
                drawPath(
                    path = line,
                    color = curveColor,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Draggable circular handles at each band
                points.forEach { pt ->
                    drawCircle(
                        color = handleCenterColor,
                        radius = 6.dp.toPx(),
                        center = pt
                    )
                    drawCircle(
                        color = curveColor,
                        radius = 4.5.dp.toPx(),
                        center = pt
                    )
                }
            }
    )
}

private fun gainToY(gainDb: Float, height: Float): Float =
    height / 2f - (gainDb / BAND_RANGE_DB) * (height / 2f)

private fun yToGain(y: Float, height: Float): Float =
    (((height / 2f - y) / (height / 2f)) * BAND_RANGE_DB).roundToInt().toFloat().coerceIn(-BAND_RANGE_DB, BAND_RANGE_DB)

private fun List<Float>.withBandAt(
    position: Offset,
    width: Int,
    height: Int,
    count: Int
): List<Float> {
    if (width <= 0 || height <= 0) return this
    val step = width.toFloat() / (count - 1)
    val index = (position.x / step).roundToInt().coerceIn(0, count - 1)
    val gain = yToGain(position.y, height.toFloat())
    return List(count) { i -> if (i == index) gain else getOrElse(i) { 0f } }
}

private fun smoothPath(points: List<Offset>): Path =
    Path().apply {
        if (points.isEmpty()) return@apply
        moveTo(points.first().x, points.first().y)
        for (i in 0 until points.size - 1) {
            val p0 = points[(i - 1).coerceAtLeast(0)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
            val lo = minOf(p1.y, p2.y)
            val hi = maxOf(p1.y, p2.y)
            cubicTo(
                p1.x + (p2.x - p0.x) / 6f,
                (p1.y + (p2.y - p0.y) / 6f).coerceIn(lo, hi),
                p2.x - (p3.x - p1.x) / 6f,
                (p2.y - (p3.y - p1.y) / 6f).coerceIn(lo, hi),
                p2.x,
                p2.y
            )
        }
    }
