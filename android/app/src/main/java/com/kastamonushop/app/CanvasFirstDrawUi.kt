package com.kastamonushop.app

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kastamonushop.app.viewmodel.ActiveTool
import com.kastamonushop.app.viewmodel.EditorUiState
import com.kastamonushop.app.viewmodel.EditorViewModel
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// ==================== PROCREATE-STYLE LEFT EDGE VERTICAL SIDEBAR ====================

@Composable
internal fun LeftBrushSidebar(
    vm: EditorViewModel,
    state: EditorUiState,
    bucketTolerance: Float,
    onBucketTolerance: (Float) -> Unit,
    onBrushSizePreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tool = state.activeTool
    val showSize = tool != ActiveTool.NONE &&
        tool != ActiveTool.EYEDROPPER &&
        tool != ActiveTool.BUCKET &&
        tool != ActiveTool.PATCH &&
        tool != ActiveTool.RED_EYE &&
        tool != ActiveTool.SELECT_RECT &&
        tool != ActiveTool.SELECT_WAND

    val (secondaryLabel, secondaryNorm, secondaryReadout) = when (tool) {
        ActiveTool.BRUSH, ActiveTool.ERASER, ActiveTool.CLONE_STAMP ->
            Triple("Opaklık", ((state.brushOpacity - 0.05f) / 0.95f).coerceIn(0f, 1f), "%${(state.brushOpacity * 100f).roundToInt()}")
        ActiveTool.MIXER_BRUSH ->
            Triple("Islaklık", (state.mixerWet / 100f).coerceIn(0f, 1f), "%${state.mixerWet.roundToInt()}")
        ActiveTool.COLOR_REPLACE ->
            Triple("Tolerans", ((state.colorReplaceTolerance - 1f) / 99f).coerceIn(0f, 1f), "%${state.colorReplaceTolerance.roundToInt()}")
        ActiveTool.BUCKET ->
            Triple("Tolerans", (bucketTolerance / 200f).coerceIn(0f, 1f), "${bucketTolerance.roundToInt()}")
        ActiveTool.SELECT_WAND ->
            Triple("Tolerans", (state.wandTolerance / 120f).coerceIn(0f, 1f), "${state.wandTolerance.roundToInt()}")
        ActiveTool.RED_EYE ->
            Triple("Karartma", ((state.redEyeDarken - 10f) / 90f).coerceIn(0f, 1f), "%${state.redEyeDarken.roundToInt()}")
        ActiveTool.SMUDGE, ActiveTool.DODGE, ActiveTool.BURN,
        ActiveTool.SPONGE_SAT, ActiveTool.SPONGE_DESAT,
        ActiveTool.BLUR_BRUSH, ActiveTool.SHARPEN_BRUSH ->
            Triple("Güç", ((state.retouchStrength - 10f) / 90f).coerceIn(0f, 1f), "%${state.retouchStrength.roundToInt()}")
        else -> Triple("", 0f, "")
    }
    val showSecondary = secondaryLabel.isNotEmpty()
    if (!showSize && !showSecondary) return

    var activeDragBadge by remember { mutableStateOf<String?>(null) }

    // Logarithmic mapping for brush size: 1..250px feels silky at both small & large sizes
    val minLog = remember { ln(1f) }
    val maxLog = remember { ln(250f) }
    val sizeNorm = ((ln(state.brushSize.coerceIn(1f, 250f)) - minLog) / (maxLog - minLog)).coerceIn(0f, 1f)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xCC17181E),
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(34.dp)
                .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showSize) {
                    Text(
                        text = "${state.brushSize.roundToInt()}",
                        color = Color(0xFFDCE4F5),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    VerticalEdgeSlider(
                        fraction = sizeNorm,
                        accentColor = Color(0xFF5CA8FF),
                        modifier = Modifier
                            .width(24.dp)
                            .height(104.dp),
                        onChange = { f ->
                            val px = kotlin.math.exp(minLog + f * (maxLog - minLog)).coerceIn(1f, 250f)
                            vm.updateBrush(size = px)
                            onBrushSizePreview()
                            activeDragBadge = "Boyut: ${px.roundToInt()}px"
                        },
                        onDragEnd = { activeDragBadge = null }
                    )
                }

                if (showSize && showSecondary) {
                    HorizontalDivider(
                        color = Color(0x33FFFFFF),
                        modifier = Modifier.width(18.dp)
                    )
                }

                if (showSecondary) {
                    VerticalEdgeSlider(
                        fraction = secondaryNorm,
                        accentColor = Color(0xFF8B7BFF),
                        modifier = Modifier
                            .width(24.dp)
                            .height(92.dp),
                        onChange = { f ->
                            when (tool) {
                                ActiveTool.BRUSH, ActiveTool.ERASER, ActiveTool.CLONE_STAMP -> {
                                    val op = (0.05f + f * 0.95f).coerceIn(0.05f, 1f)
                                    vm.updateBrush(opacity = op)
                                    activeDragBadge = "Opaklık: %${(op * 100f).roundToInt()}"
                                }
                                ActiveTool.MIXER_BRUSH -> {
                                    val wet = (f * 100f).coerceIn(0f, 100f)
                                    vm.updateMixerParams(wet = wet)
                                    activeDragBadge = "Islaklık: %${wet.roundToInt()}"
                                }
                                ActiveTool.COLOR_REPLACE -> {
                                    val tol = (1f + f * 99f).coerceIn(1f, 100f)
                                    vm.updateColorReplaceParams(tolerance = tol)
                                    activeDragBadge = "Tolerans: %${tol.roundToInt()}"
                                }
                                ActiveTool.BUCKET -> {
                                    val tol = (f * 200f).coerceIn(0f, 200f)
                                    onBucketTolerance(tol)
                                    activeDragBadge = "Tolerans: ${tol.roundToInt()}"
                                }
                                ActiveTool.SELECT_WAND -> {
                                    val tol = (f * 120f).coerceIn(1f, 120f)
                                    vm.updateWandTolerance(tol)
                                    activeDragBadge = "Tolerans: ${tol.roundToInt()}"
                                }
                                ActiveTool.RED_EYE -> {
                                    val dk = (10f + f * 90f).coerceIn(10f, 100f)
                                    vm.updateRedEyeParams(darken = dk)
                                    activeDragBadge = "Karartma: %${dk.roundToInt()}"
                                }
                                else -> {
                                    val str = (10f + f * 90f).coerceIn(10f, 100f)
                                    vm.updateRetouchStrength(str)
                                    activeDragBadge = "Güç: %${str.roundToInt()}"
                                }
                            }
                        },
                        onDragEnd = { activeDragBadge = null }
                    )
                    Text(
                        text = secondaryReadout,
                        color = Color(0xFFC5CBE0),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        activeDragBadge?.let { badge ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xEE14161D),
                tonalElevation = 6.dp
            ) {
                Text(
                    text = badge,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun VerticalEdgeSlider(
    fraction: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val latestOnChange by rememberUpdatedState(onChange)
    val latestOnEnd by rememberUpdatedState(onDragEnd)
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    latestOnChange((1f - pos.y / h).coerceIn(0f, 1f))
                    latestOnEnd()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        latestOnChange((1f - pos.y / h).coerceIn(0f, 1f))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        latestOnChange((1f - change.position.y / h).coerceIn(0f, 1f))
                    },
                    onDragEnd = { latestOnEnd() },
                    onDragCancel = { latestOnEnd() }
                )
            }
    ) {
        val trackW = 6.dp.toPx()
        val trackX = (size.width - trackW) * 0.5f
        val r = CornerRadius(trackW * 0.5f, trackW * 0.5f)
        // Background track
        drawRoundRect(
            color = Color(0xFF343846),
            topLeft = Offset(trackX, 0f),
            size = Size(trackW, size.height),
            cornerRadius = r
        )
        // Active fill from bottom
        val fillH = (fraction.coerceIn(0f, 1f) * size.height)
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(trackX, size.height - fillH),
            size = Size(trackW, fillH),
            cornerRadius = r
        )
        // Thumb knob
        val thumbY = (size.height - fillH).coerceIn(7.dp.toPx(), size.height - 7.dp.toPx())
        drawCircle(
            color = Color.White,
            radius = 7.dp.toPx(),
            center = Offset(size.width * 0.5f, thumbY)
        )
        drawCircle(
            color = accentColor,
            radius = 4.dp.toPx(),
            center = Offset(size.width * 0.5f, thumbY)
        )
    }
}

// ==================== SLIM 1-ROW CANVAS-FIRST DRAW & RETOUCH BARS ====================

@Composable
internal fun CompactDrawBottomBar(
    vm: EditorViewModel,
    state: EditorUiState,
    popoverOpen: Boolean,
    onTogglePopover: () -> Unit,
    onOpenColorWheel: () -> Unit,
    onClose: () -> Unit
) {
    val activeColor = remember(state.colorHex) {
        runCatching { Color(AndroidColor.parseColor(state.colorHex)) }.getOrDefault(Color.White)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White, modifier = Modifier.size(20.dp))
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SlimToolChip(Icons.Default.Brush, "Fırça", state.activeTool == ActiveTool.BRUSH) {
                vm.setTool(ActiveTool.BRUSH)
            }
            SlimToolChip(Icons.Default.AutoFixNormal, "Silgi", state.activeTool == ActiveTool.ERASER) {
                vm.setTool(ActiveTool.ERASER)
            }
            SlimToolChip(Icons.Default.WaterDrop, "Karıştır", state.activeTool == ActiveTool.MIXER_BRUSH) {
                vm.setTool(ActiveTool.MIXER_BRUSH)
            }
            SlimToolChip(Icons.Default.InvertColors, "Renk Değ.", state.activeTool == ActiveTool.COLOR_REPLACE) {
                vm.setTool(ActiveTool.COLOR_REPLACE)
            }
            SlimToolChip(Icons.Default.FormatColorFill, "Kova", state.activeTool == ActiveTool.BUCKET) {
                vm.setTool(ActiveTool.BUCKET)
            }
            SlimToolChip(Icons.Default.Colorize, "Damlalık", state.activeTool == ActiveTool.EYEDROPPER) {
                vm.setTool(if (state.activeTool == ActiveTool.EYEDROPPER) ActiveTool.BRUSH else ActiveTool.EYEDROPPER)
            }
            SlimToolChip(Icons.Default.Straighten, "Cetvel", state.rulerEnabled) {
                vm.toggleRuler()
            }
            SlimToolChip(
                Icons.Default.Flip,
                when (state.symmetryMode) {
                    "vertical" -> "Ayna V"
                    "horizontal" -> "Ayna H"
                    "dual" -> "Ayna 4x"
                    else -> "Ayna"
                },
                state.symmetryMode != "off"
            ) {
                vm.cycleSymmetry()
            }
        }

        // Direct 1-tap Pro HSV Color Wheel swatch
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(activeColor)
                .border(2.5.dp, Color.White, CircleShape)
                .clickable { onOpenColorWheel() }
        )

        // Popover toggle button (Brushes / Dynamics / Tool Options)
        Surface(
            shape = CircleShape,
            color = if (popoverOpen) Color(0xFF4F9CFF) else Color(0xFF282B36),
            modifier = Modifier
                .size(36.dp)
                .clickable { onTogglePopover() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Fırça & Ayarlar",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun CompactRetouchBottomBar(
    vm: EditorViewModel,
    state: EditorUiState,
    popoverOpen: Boolean,
    onTogglePopover: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White, modifier = Modifier.size(20.dp))
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SlimToolChip(Icons.Default.Healing, "Leke", state.activeTool == ActiveTool.SPOT_HEAL) {
                vm.setTool(ActiveTool.SPOT_HEAL)
            }
            SlimToolChip(Icons.Default.Texture, "Yama", state.activeTool == ActiveTool.PATCH) {
                vm.setTool(ActiveTool.PATCH)
            }
            SlimToolChip(Icons.Default.RemoveRedEye, "Kır. Göz", state.activeTool == ActiveTool.RED_EYE) {
                vm.setTool(ActiveTool.RED_EYE)
            }
            SlimToolChip(Icons.Default.Gesture, "Dağıt", state.activeTool == ActiveTool.SMUDGE) {
                vm.setTool(ActiveTool.SMUDGE)
            }
            SlimToolChip(Icons.Default.WbSunny, "Aydınlat", state.activeTool == ActiveTool.DODGE) {
                vm.setTool(ActiveTool.DODGE)
            }
            SlimToolChip(Icons.Default.NightsStay, "Koyulaştır", state.activeTool == ActiveTool.BURN) {
                vm.setTool(ActiveTool.BURN)
            }
            SlimToolChip(Icons.Default.Tonality, "Sünger+", state.activeTool == ActiveTool.SPONGE_SAT) {
                vm.setTool(ActiveTool.SPONGE_SAT)
            }
            SlimToolChip(Icons.Default.FilterBAndW, "Sünger-", state.activeTool == ActiveTool.SPONGE_DESAT) {
                vm.setTool(ActiveTool.SPONGE_DESAT)
            }
            SlimToolChip(Icons.Default.BlurOn, "Bulanık", state.activeTool == ActiveTool.BLUR_BRUSH) {
                vm.setTool(ActiveTool.BLUR_BRUSH)
            }
            SlimToolChip(Icons.Default.Details, "Keskin", state.activeTool == ActiveTool.SHARPEN_BRUSH) {
                vm.setTool(ActiveTool.SHARPEN_BRUSH)
            }
            SlimToolChip(Icons.Default.ContentCopy, "Klonla", state.activeTool == ActiveTool.CLONE_STAMP) {
                vm.setTool(ActiveTool.CLONE_STAMP)
            }
        }

        Surface(
            shape = CircleShape,
            color = if (popoverOpen) Color(0xFF4F9CFF) else Color(0xFF282B36),
            modifier = Modifier
                .size(36.dp)
                .clickable { onTogglePopover() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Rötuş Ayarları",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun SlimToolChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFF3A7AFE) else Color(0xFF232630),
        modifier = Modifier
            .height(42.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else Color(0xFFC8CEE0),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = if (selected) Color.White else Color(0xFFD5DAE8),
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

// ==================== FLOATING BRUSH / TOOL SETTINGS POPOVER (AUTO-CLOSES ON DRAW) ====================

@Composable
internal fun ToolSettingsPopoverCard(
    vm: EditorViewModel,
    state: EditorUiState,
    bucketTolerance: Float,
    onBucketTolerance: (Float) -> Unit,
    bucketContiguous: Boolean,
    onBucketContiguous: (Boolean) -> Unit,
    onImportAbr: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var brushTab by remember { mutableIntStateOf(0) } // 0 = Uçlar & .abr, 1 = Dinamikler

    LaunchedEffect(state.activeTool, brushTab) {
        if (state.activeTool == ActiveTool.BRUSH && brushTab == 0 && state.brushPresets.isEmpty()) {
            vm.loadBrushPresets()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xF21B1D25),
        tonalElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when (state.activeTool) {
                        ActiveTool.BRUSH -> "Fırça Stüdyosu"
                        ActiveTool.ERASER -> "Silgi Ayarları"
                        ActiveTool.MIXER_BRUSH -> "Karıştırıcı Fırça"
                        ActiveTool.COLOR_REPLACE -> "Renk Değiştirici"
                        ActiveTool.BUCKET -> "Boya Kovası"
                        ActiveTool.PATCH -> "Yama Aracı"
                        ActiveTool.RED_EYE -> "Kırmızı Göz"
                        ActiveTool.CLONE_STAMP -> "Klonlama Damgası"
                        else -> "Rötuş Ayarları"
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color(0xFF9EA4B8), modifier = Modifier.size(16.dp))
                }
            }

            when (state.activeTool) {
                ActiveTool.BRUSH -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProPill(
                            selected = brushTab == 0,
                            onClick = { brushTab = 0 },
                            label = { Text("Fırça Uçları", fontSize = 11.sp) }
                        )
                        ProPill(
                            selected = brushTab == 1,
                            onClick = { brushTab = 1 },
                            label = { Text("Dinamikler", fontSize = 11.sp) }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = onImportAbr,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(".abr Yükle", fontSize = 10.sp)
                        }
                    }

                    if (brushTab == 0) {
                        CompactSliderRow("Sertlik", "%${(state.brushHardness * 100f).roundToInt()}", state.brushHardness, 0f..1f, defaultValue = 0.85f) {
                            vm.updateBrush(hardness = it)
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 210.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                VisualBrushStrokeItem(
                                    name = "Yuvarlak (Varsayılan)",
                                    hardness = state.brushHardness,
                                    isSampled = false,
                                    selected = state.activeBrushPresetName == null,
                                    onClick = {
                                        vm.resetBrushToRound()
                                        onDismiss()
                                    }
                                )
                            }
                            items(state.brushPresets.take(35), key = { "${it.index}_${it.name}" }) { preset ->
                                VisualBrushStrokeItem(
                                    name = preset.name,
                                    hardness = preset.hardness,
                                    isSampled = preset.hasSampledTip,
                                    selected = state.activeBrushPresetName.equals(preset.name, ignoreCase = true),
                                    onClick = {
                                        vm.selectBrushPreset(preset.name)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    } else {
                        CompactSliderRow("Akış", "%${(state.brushFlow * 100f).roundToInt()}", state.brushFlow, 0.05f..1f, defaultValue = 1f) {
                            vm.updateBrush(flow = it)
                        }
                        CompactSliderRow("Aralık", "%${(state.brushSpacing * 100f).roundToInt()}", state.brushSpacing, 0.05f..2.0f, defaultValue = 0.15f) {
                            vm.updateBrushDynamics(spacing = it)
                        }
                        CompactSliderRow("Dağılma", "%${(state.brushScatter * 100f).roundToInt()}", state.brushScatter, 0f..3.0f, defaultValue = 0f) {
                            vm.updateBrushDynamics(scatter = it)
                        }
                        CompactSliderRow("Boyut Tit.", "%${(state.brushSizeJitter * 100f).roundToInt()}", state.brushSizeJitter, 0f..1f, defaultValue = 0f) {
                            vm.updateBrushDynamics(sizeJitter = it)
                        }
                        CompactSliderRow("Açı Tit.", "%${(state.brushAngleJitter * 100f).roundToInt()}", state.brushAngleJitter, 0f..1f, defaultValue = 0f) {
                            vm.updateBrushDynamics(angleJitter = it)
                        }
                        CompactSliderRow("Opak. Tit.", "%${(state.brushOpacityJitter * 100f).roundToInt()}", state.brushOpacityJitter, 0f..1f, defaultValue = 0f) {
                            vm.updateBrushDynamics(opacityJitter = it)
                        }
                    }
                }
                ActiveTool.MIXER_BRUSH -> {
                    CompactSliderRow("Islaklık", "%${state.mixerWet.roundToInt()}", state.mixerWet, 0f..100f, defaultValue = 55f) {
                        vm.updateMixerParams(wet = it)
                    }
                    CompactSliderRow("Yük", "%${state.mixerLoad.roundToInt()}", state.mixerLoad, 0f..100f, defaultValue = 50f) {
                        vm.updateMixerParams(load = it)
                    }
                    CompactSliderRow("Karışım", "%${state.mixerMix.roundToInt()}", state.mixerMix, 0f..100f, defaultValue = 50f) {
                        vm.updateMixerParams(mix = it)
                    }
                    ProPill(
                        selected = state.mixerSampleAll,
                        onClick = { vm.updateMixerParams(sampleAll = !state.mixerSampleAll) },
                        label = { Text(if (state.mixerSampleAll) "Tüm Katmanlardan Örnekle ✓" else "Sadece Aktif Katman", fontSize = 11.sp) }
                    )
                }
                ActiveTool.COLOR_REPLACE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("color" to "Renk", "hue" to "Ton", "saturation" to "Doygunluk", "luminosity" to "Parlaklık").forEach { (k, lbl) ->
                            ProPill(
                                selected = state.colorReplaceMode == k,
                                onClick = { vm.updateColorReplaceParams(mode = k) },
                                label = { Text(lbl, fontSize = 10.sp) }
                            )
                        }
                    }
                    CompactSliderRow("Tolerans", "%${state.colorReplaceTolerance.roundToInt()}", state.colorReplaceTolerance, 1f..100f, defaultValue = 32f) {
                        vm.updateColorReplaceParams(tolerance = it)
                    }
                    CompactSliderRow("Sertlik", "%${(state.brushHardness * 100f).roundToInt()}", state.brushHardness, 0f..1f, defaultValue = 0.85f) {
                        vm.updateBrush(hardness = it)
                    }
                }
                ActiveTool.BUCKET -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProPill(
                            selected = bucketContiguous,
                            onClick = { onBucketContiguous(true) },
                            label = { Text("Bitişik Alan", fontSize = 11.sp) }
                        )
                        ProPill(
                            selected = !bucketContiguous,
                            onClick = { onBucketContiguous(false) },
                            label = { Text("Tüm Benzer Renkler", fontSize = 11.sp) }
                        )
                    }
                    CompactSliderRow("Tolerans", "${bucketTolerance.roundToInt()}", bucketTolerance, 0f..200f, defaultValue = 36f) {
                        onBucketTolerance(it)
                    }
                }
                ActiveTool.PATCH -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProPill(
                            selected = state.patchMode == "source",
                            onClick = { vm.updatePatchMode("source") },
                            label = { Text("Kaynak Modu", fontSize = 11.sp) }
                        )
                        ProPill(
                            selected = state.patchMode == "destination",
                            onClick = { vm.updatePatchMode("destination") },
                            label = { Text("Hedef Modu", fontSize = 11.sp) }
                        )
                    }
                    Text(
                        text = if (state.selectionBounds == null)
                            "Önce Seçim aracıyla kusurlu alanı seç, sonra Yama ile temiz dokuya sürükle."
                        else
                            "Seçim hazır · seçili alanı temiz doku üzerine sürükleyip bırak.",
                        color = Color(0xFFAEB4C4),
                        fontSize = 11.sp
                    )
                }
                ActiveTool.RED_EYE -> {
                    CompactSliderRow("Göz Bebeği", "%${state.redEyePupilSize.roundToInt()}", state.redEyePupilSize, 10f..100f, defaultValue = 50f) {
                        vm.updateRedEyeParams(pupilSize = it)
                    }
                    CompactSliderRow("Karartma", "%${state.redEyeDarken.roundToInt()}", state.redEyeDarken, 10f..100f, defaultValue = 55f) {
                        vm.updateRedEyeParams(darken = it)
                    }
                }
                ActiveTool.CLONE_STAMP -> {
                    OutlinedButton(
                        onClick = {
                            vm.requestPickCloneSource()
                            onDismiss()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (state.cloneSourceDoc == null) "Kaynak Noktası Seç" else "Kaynağı Değiştir", fontSize = 11.sp)
                    }
                    CompactSliderRow("Sertlik", "%${(state.brushHardness * 100f).roundToInt()}", state.brushHardness, 0f..1f, defaultValue = 0.85f) {
                        vm.updateBrush(hardness = it)
                    }
                }
                else -> {
                    CompactSliderRow("Sertlik", "%${(state.brushHardness * 100f).roundToInt()}", state.brushHardness, 0f..1f, defaultValue = 0.85f) {
                        vm.updateBrush(hardness = it)
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualBrushStrokeItem(
    name: String,
    hardness: Float,
    isSampled: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color(0xFF2B436E) else Color(0xFF232632),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) Color(0xFF6BB5FF) else Color(0xFF323646),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Live stylized stroke-wave preview (Photoshop/PhotoCraft style)
            Canvas(
                modifier = Modifier
                    .width(96.dp)
                    .height(26.dp)
            ) {
                val path = Path()
                val steps = 28
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val x = 6f + t * (size.width - 12f)
                    val y = size.height * 0.5f + sin(t * 2.0 * PI).toFloat() * (size.height * 0.28f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (isSampled) {
                    // Textured / sampled brush wave
                    drawPath(
                        path = path,
                        color = Color(0x66DDE6FF),
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    for (i in 0..14) {
                        val t = i / 14f
                        val x = 6f + t * (size.width - 12f)
                        val y = size.height * 0.5f + sin(t * 2.0 * PI).toFloat() * (size.height * 0.28f)
                        drawCircle(Color.White, radius = (2.5f + (i % 3) * 1.2f).dp.toPx(), center = Offset(x, y))
                    }
                } else {
                    val softHalo = (1f - hardness.coerceIn(0f, 1f))
                    if (softHalo > 0.1f) {
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = 0.22f * softHalo),
                            style = Stroke(width = (10f + softHalo * 6f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = 0.45f),
                            style = Stroke(width = (7f + softHalo * 2f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = Stroke(width = (5f + hardness * 3f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isSampled) "Örnekli Uç (.abr)" else "Sertlik %${(hardness * 100f).roundToInt()}",
                    color = Color(0xFF9EA5BA),
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }
    }
}
