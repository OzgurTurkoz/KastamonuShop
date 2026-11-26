package com.kastamonushop.app

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

internal class MultiFxSlot(
    val kind: String,
    val label: String,
    initialEnabled: Boolean = false,
    initialColor: String = "#8A2BE2",
    initialColor2: String = "#000000",
    initialSize: Float = 18f,
    initialOpacity: Float = 90f,
    initialDistance: Float = 10f,
    initialAngle: Float = 90f,
    initialPosition: String = "outside",
    initialBlend: String = "Normal",
    initialDepth: Float = 120f
) {
    var enabled by mutableStateOf(initialEnabled)
    var colorHex by mutableStateOf(initialColor)
    var color2Hex by mutableStateOf(initialColor2)
    var size by mutableFloatStateOf(initialSize)
    var opacity by mutableFloatStateOf(initialOpacity)
    var distance by mutableFloatStateOf(initialDistance)
    var angle by mutableFloatStateOf(initialAngle)
    var position by mutableStateOf(initialPosition)
    var blend by mutableStateOf(initialBlend)
    var depth by mutableFloatStateOf(initialDepth)
}

internal fun createDefaultMultiFxSlots(): List<MultiFxSlot> = listOf(
    MultiFxSlot("stroke", "Kontur", initialEnabled = true, initialColor = "#8A2BE2", initialSize = 18f, initialOpacity = 100f, initialPosition = "outside", initialBlend = "Normal"),
    MultiFxSlot("outerGlow", "Dış Işıma", initialColor = "#A855F7", initialSize = 26f, initialOpacity = 85f, initialBlend = "Screen"),
    MultiFxSlot("gradientOverlay", "Degrade", initialColor = "#FF2A5F", initialColor2 = "#7B2CFF", initialOpacity = 100f, initialAngle = 90f, initialBlend = "Normal"),
    MultiFxSlot("dropShadow", "Alt Gölge", initialColor = "#000000", initialSize = 20f, initialDistance = 12f, initialOpacity = 80f, initialAngle = 120f, initialBlend = "Multiply"),
    MultiFxSlot("innerShadow", "İç Gölge", initialColor = "#000000", initialSize = 16f, initialDistance = 8f, initialOpacity = 75f, initialAngle = 120f, initialBlend = "Multiply"),
    MultiFxSlot("innerGlow", "İç Işıma", initialColor = "#FFFFFF", initialSize = 18f, initialOpacity = 75f, initialBlend = "Screen"),
    MultiFxSlot("colorOverlay", "Renk", initialColor = "#4F9CFF", initialOpacity = 85f, initialBlend = "Normal"),
    MultiFxSlot("bevelEmboss", "Kabartma", initialSize = 10f, initialDepth = 150f, initialOpacity = 80f, initialAngle = 120f)
)

internal fun buildMultiFxStackJson(slots: List<MultiFxSlot>): String {
    val arr = JSONArray()
    for (fx in slots) {
        if (!fx.enabled) continue
        val p = JSONObject()
        when (fx.kind) {
            "stroke" -> {
                p.put("color", fx.colorHex)
                p.put("size", fx.size.toDouble())
                p.put("opacity", fx.opacity.toDouble())
                p.put("position", fx.position)
                p.put("blend", fx.blend)
            }
            "outerGlow" -> {
                p.put("color", fx.colorHex)
                p.put("size", fx.size.toDouble())
                p.put("opacity", fx.opacity.toDouble())
                p.put("blend", fx.blend)
            }
            "innerGlow" -> {
                p.put("color", fx.colorHex)
                p.put("size", fx.size.toDouble())
                p.put("opacity", fx.opacity.toDouble())
                p.put("source", "edge")
                p.put("blend", fx.blend)
            }
            "gradientOverlay" -> {
                p.put("from", fx.colorHex)
                p.put("to", fx.color2Hex)
                p.put("opacity", fx.opacity.toDouble())
                p.put("angle", fx.angle.toDouble())
                p.put("style", "linear")
                p.put("blend", fx.blend)
            }
            "colorOverlay" -> {
                p.put("color", fx.colorHex)
                p.put("opacity", fx.opacity.toDouble())
                p.put("blend", fx.blend)
            }
            "dropShadow" -> {
                p.put("color", fx.colorHex)
                p.put("size", fx.size.toDouble())
                p.put("distance", fx.distance.toDouble())
                p.put("angle", fx.angle.toDouble())
                p.put("opacity", fx.opacity.toDouble())
                p.put("blend", fx.blend)
            }
            "innerShadow" -> {
                p.put("color", fx.colorHex)
                p.put("size", fx.size.toDouble())
                p.put("distance", fx.distance.toDouble())
                p.put("angle", fx.angle.toDouble())
                p.put("opacity", fx.opacity.toDouble())
                p.put("blend", fx.blend)
            }
            "bevelEmboss" -> {
                p.put("depth", fx.depth.toDouble())
                p.put("size", fx.size.toDouble())
                p.put("angle", fx.angle.toDouble())
                p.put("altitude", 30.0)
                p.put("opacity", fx.opacity.toDouble())
            }
        }
        val item = JSONObject()
        item.put("kind", fx.kind)
        item.put("params", p)
        arr.put(item)
    }
    return arr.toString()
}

@Composable
internal fun MultiFxLayerStyleStudioPanel(
    slots: List<MultiFxSlot>,
    onPreviewStack: (String) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
    onApply: (String) -> Unit
) {
    var selectedIdx by remember { mutableIntStateOf(0) }
    val cur = slots[selectedIdx.coerceIn(0, slots.lastIndex)]
    var colorWheelTarget by remember { mutableIntStateOf(0) } // 0 = closed, 1 = primary color, 2 = gradient end color

    fun pushPreview() {
        onPreviewStack(buildMultiFxStackJson(slots))
    }

    LaunchedEffect(Unit) {
        pushPreview()
    }

    if (colorWheelTarget != 0) {
        val initHex = if (colorWheelTarget == 2) cur.color2Hex else cur.colorHex
        ProColorWheelDialog(
            initialHex = initHex,
            showOpacity = false,
            onDismiss = { colorWheelTarget = 0 },
            onConfirm = { picked, _ ->
                if (colorWheelTarget == 2) cur.color2Hex = picked else cur.colorHex = picked
                cur.enabled = true
                colorWheelTarget = 0
                pushPreview()
            }
        )
    }

    val activeCount = slots.count { it.enabled }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Compact header: Back + Title + active badge + Clear + Apply
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Katman Stilleri (fx)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (activeCount == 0) "Efekt seçip üst üste ekle" else "$activeCount efekt üst üste aktif",
                    color = Color(0xFF9EA5BA),
                    fontSize = 10.sp
                )
            }
            TextButton(
                onClick = {
                    slots.forEach { it.enabled = false }
                    onClearAll()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Temizle", fontSize = 11.sp, color = Color(0xFFFF8A8A))
            }
            Button(
                onClick = { onApply(buildMultiFxStackJson(slots)) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Uygula", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Multi-FX checkbox chip strip (stack multiple effects at once!)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            slots.forEachIndexed { idx, fx ->
                val isSel = idx == selectedIdx
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isSel -> Color(0xFF34568B)
                        fx.enabled -> Color(0xFF25324B)
                        else -> Color(0xFF222530)
                    },
                    modifier = Modifier
                        .height(34.dp)
                        .border(
                            width = if (isSel) 1.5.dp else 1.dp,
                            color = if (isSel) Color(0xFF6BB5FF) else if (fx.enabled) Color(0xFF4C72B0) else Color(0xFF333747),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            selectedIdx = idx
                            if (!fx.enabled) {
                                fx.enabled = true
                                pushPreview()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (fx.enabled) Color(0xFF4F9CFF) else Color(0xFF15171F))
                                .border(1.dp, if (fx.enabled) Color(0xFF8CC5FF) else Color(0xFF555B70), RoundedCornerShape(4.dp))
                                .clickable {
                                    fx.enabled = !fx.enabled
                                    selectedIdx = idx
                                    pushPreview()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (fx.enabled) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                            }
                        }
                        Text(
                            text = fx.label,
                            color = if (isSel || fx.enabled) Color.White else Color(0xFFB5BCCF),
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Color & Blend Mode row for selected effect
        if (cur.kind != "bevelEmboss") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val c1 = remember(cur.colorHex) { runCatching { Color(AndroidColor.parseColor(cur.colorHex)) }.getOrDefault(Color.Magenta) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF232632))
                        .clickable { colorWheelTarget = 1 }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(c1).border(1.5.dp, Color.White, CircleShape))
                    Text(if (cur.kind == "gradientOverlay") "Renk 1" else "Renk", color = Color.White, fontSize = 10.sp)
                }

                if (cur.kind == "gradientOverlay") {
                    val c2 = remember(cur.color2Hex) { runCatching { Color(AndroidColor.parseColor(cur.color2Hex)) }.getOrDefault(Color.Black) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF232632))
                            .clickable { colorWheelTarget = 2 }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(c2).border(1.5.dp, Color.White, CircleShape))
                        Text("Renk 2", color = Color.White, fontSize = 10.sp)
                    }
                }

                if (cur.kind == "stroke") {
                    listOf("outside" to "Dış", "center" to "Orta", "inside" to "İç").forEach { (posKey, posLbl) ->
                        FilterChip(
                            selected = cur.position == posKey,
                            onClick = { cur.position = posKey; cur.enabled = true; pushPreview() },
                            label = { Text(posLbl, fontSize = 10.sp) }
                        )
                    }
                }

                listOf("Normal" to "Normal", "Screen" to "Ekran", "Multiply" to "Çarpma").forEach { (bKey, bLbl) ->
                    FilterChip(
                        selected = cur.blend == bKey,
                        onClick = { cur.blend = bKey; cur.enabled = true; pushPreview() },
                        label = { Text(bLbl, fontSize = 10.sp) }
                    )
                }
            }
        }

        // Sliders tailored to the currently selected effect
        when (cur.kind) {
            "stroke" -> {
                CompactSliderRow("Boyut", "${cur.size.roundToInt()}px", cur.size, 1f..80f, defaultValue = 18f) {
                    cur.size = it; cur.enabled = true; pushPreview()
                }
                CompactSliderRow("Opaklık", "%${cur.opacity.roundToInt()}", cur.opacity, 5f..100f, defaultValue = 100f) {
                    cur.opacity = it; cur.enabled = true; pushPreview()
                }
            }
            "outerGlow", "innerGlow" -> {
                CompactSliderRow("Boyut", "${cur.size.roundToInt()}px", cur.size, 1f..120f, defaultValue = 26f) {
                    cur.size = it; cur.enabled = true; pushPreview()
                }
                CompactSliderRow("Opaklık", "%${cur.opacity.roundToInt()}", cur.opacity, 5f..100f, defaultValue = 85f) {
                    cur.opacity = it; cur.enabled = true; pushPreview()
                }
            }
            "gradientOverlay" -> {
                CompactSliderRow("Açı", "${cur.angle.roundToInt()}°", cur.angle, 0f..360f, defaultValue = 90f) {
                    cur.angle = it; cur.enabled = true; pushPreview()
                }
                CompactSliderRow("Opaklık", "%${cur.opacity.roundToInt()}", cur.opacity, 5f..100f, defaultValue = 100f) {
                    cur.opacity = it; cur.enabled = true; pushPreview()
                }
            }
            "colorOverlay" -> {
                CompactSliderRow("Opaklık", "%${cur.opacity.roundToInt()}", cur.opacity, 5f..100f, defaultValue = 85f) {
                    cur.opacity = it; cur.enabled = true; pushPreview()
                }
            }
            "dropShadow", "innerShadow" -> {
                CompactSliderRow("Boyut", "${cur.size.roundToInt()}px", cur.size, 1f..100f, defaultValue = 20f) {
                    cur.size = it; cur.enabled = true; pushPreview()
                }
                CompactSliderRow("Uzaklık", "${cur.distance.roundToInt()}px", cur.distance, 0f..100f, defaultValue = 12f) {
                    cur.distance = it; cur.enabled = true; pushPreview()
                }
                CompactSliderRow("Opaklık", "%${cur.opacity.roundToInt()}", cur.opacity, 5f..100f, defaultValue = 80f) {
                    cur.opacity = it; cur.enabled = true; pushPreview()
                }
            }
            "bevelEmboss" -> {
                CompactSliderRow("Derinlik", "%${cur.depth.roundToInt()}", cur.depth, 10f..400f, defaultValue = 150f) {
                    cur.depth = it; cur.enabled = true; pushPreview()
                }
                CompactSliderRow("Boyut", "${cur.size.roundToInt()}px", cur.size, 1f..60f, defaultValue = 10f) {
                    cur.size = it; cur.enabled = true; pushPreview()
                }
                CompactSliderRow("Opaklık", "%${cur.opacity.roundToInt()}", cur.opacity, 5f..100f, defaultValue = 80f) {
                    cur.opacity = it; cur.enabled = true; pushPreview()
                }
            }
        }
    }
}
