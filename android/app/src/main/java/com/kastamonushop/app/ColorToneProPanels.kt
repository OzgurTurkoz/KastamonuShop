package com.kastamonushop.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uniffi.kastamonushop_ffi.HistogramData
import kotlin.math.roundToInt

/**
 * Compact OLED live RGB + Luma histogram with shadow (0) & highlight (255) clipping indicators.
 */
@Composable
internal fun LiveHistogramStrip(
    histogram: HistogramData?,
    activeChannel: Int = 0, // 0=RGB, 1=R, 2=G, 3=B
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0B0D12))
            .border(1.dp, Color(0xFF232734), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        if (histogram == null || histogram.luma.isEmpty()) {
            Text(
                "Histogram hazırlanıyor...",
                color = Color(0xFF6B7280),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                fun drawSeries(bins: List<Float>, fill: Color, stroke: Color) {
                    if (bins.size < 2) return
                    val fillPath = Path()
                    val strokePath = Path()
                    val step = w / (bins.size - 1).toFloat()
                    fillPath.moveTo(0f, h)
                    bins.forEachIndexed { i, v ->
                        val x = i * step
                        val y = h - (v.coerceIn(0f, 1f) * (h - 2f))
                        if (i == 0) strokePath.moveTo(x, y) else strokePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    fillPath.lineTo(w, h)
                    fillPath.close()
                    drawPath(fillPath, color = fill)
                    drawPath(strokePath, color = stroke, style = Stroke(width = 1.4f))
                }
                when (activeChannel) {
                    1 -> drawSeries(histogram.red, Color(0x44FF5252), Color(0xFFFF5252))
                    2 -> drawSeries(histogram.green, Color(0x444ADE80), Color(0xFF4ADE80))
                    3 -> drawSeries(histogram.blue, Color(0x4460A5FA), Color(0xFF60A5FA))
                    else -> {
                        drawSeries(histogram.red, Color(0x28FF5252), Color(0x88FF5252))
                        drawSeries(histogram.green, Color(0x284ADE80), Color(0x884ADE80))
                        drawSeries(histogram.blue, Color(0x2860A5FA), Color(0x8860A5FA))
                        drawSeries(histogram.luma, Color(0x36FFFFFF), Color(0xCCFFFFFF))
                    }
                }
                // Subtle quarter grid lines
                for (q in 1..3) {
                    val x = w * (q / 4f)
                    drawLine(Color(0x1EFFFFFF), Offset(x, 0f), Offset(x, h), 1f)
                }
            }
            if (histogram.shadowClipPct >= 0.5f) {
                Text(
                    "S:${histogram.shadowClipPct.roundToInt()}%",
                    color = Color(0xFF60A5FA),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color(0xAA0B0D12), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            if (histogram.highlightClipPct >= 0.5f) {
                Text(
                    "B:${histogram.highlightClipPct.roundToInt()}%",
                    color = Color(0xFFFF6B6B),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color(0xAA0B0D12), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
internal fun LevelsProPanel(
    s: StudioSessionState,
    histogram: HistogramData?,
    onPreview: () -> Unit,
    onAuto: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    val ch = s.activeLevelsChannel()
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        BottomEditorHeader(
            title = "Seviyeler Pro (Levels)",
            subtitle = "Canlı histogram, R/G/B kanalları ve çıkış uçları",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApply
        )
        LiveHistogramStrip(histogram = histogram, activeChannel = s.levelsChannelIdx)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("RGB", "Kırmızı", "Yeşil", "Mavi").forEachIndexed { idx, lbl ->
                ProPill(
                    selected = s.levelsChannelIdx == idx,
                    onClick = { s.levelsChannelIdx = idx },
                    label = { Text(lbl, fontSize = 11.sp) }
                )
            }
            ProPill(
                selected = s.levelsShowOutput,
                onClick = { s.levelsShowOutput = !s.levelsShowOutput },
                label = { Text(if (s.levelsShowOutput) "Çıkış ✓" else "Çıkış", fontSize = 11.sp) }
            )
            ProPill(
                selected = false,
                onClick = onAuto,
                label = { Text("Oto", fontSize = 11.sp, color = Color(0xFFF59E0B)) }
            )
            ProPill(
                selected = false,
                onClick = onReset,
                label = { Text("Sıfırla", fontSize = 11.sp, color = Color(0xFFFF8A8A)) }
            )
        }
        if (!s.levelsShowOutput) {
            CompactSliderRow("Siyah", "${ch.inBlack.roundToInt()}", ch.inBlack, 0f..235f, defaultValue = 0f) {
                s.setActiveLevelsChannel(ch.copy(inBlack = it.coerceAtMost(ch.inWhite - 10f)))
                onPreview()
            }
            CompactSliderRow("Orta Ton", String.format(java.util.Locale.US, "%.2f", ch.gamma), ch.gamma, 0.25f..3.0f, defaultValue = 1f) {
                s.setActiveLevelsChannel(ch.copy(gamma = it))
                onPreview()
            }
            CompactSliderRow("Beyaz", "${ch.inWhite.roundToInt()}", ch.inWhite, 20f..255f, defaultValue = 255f) {
                s.setActiveLevelsChannel(ch.copy(inWhite = it.coerceAtLeast(ch.inBlack + 10f)))
                onPreview()
            }
        } else {
            CompactSliderRow("Çıkış Siyah", "${ch.outBlack.roundToInt()}", ch.outBlack, 0f..255f, defaultValue = 0f) {
                s.setActiveLevelsChannel(ch.copy(outBlack = it))
                onPreview()
            }
            CompactSliderRow("Çıkış Beyaz", "${ch.outWhite.roundToInt()}", ch.outWhite, 0f..255f, defaultValue = 255f) {
                s.setActiveLevelsChannel(ch.copy(outWhite = it))
                onPreview()
            }
        }
    }
}

@Composable
internal fun BlackWhiteProPanel(
    s: StudioSessionState,
    onPreview: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    val channels = listOf(
        "Kırmızı" to Color(0xFFFF5252),
        "Sarı" to Color(0xFFFAAD14),
        "Yeşil" to Color(0xFF4ADE80),
        "Turkuaz" to Color(0xFF22D3EE),
        "Mavi" to Color(0xFF60A5FA),
        "Macenta" to Color(0xFFEC4899)
    )
    val defaults = listOf(40f, 60f, 40f, 60f, 20f, 80f)
    val tintSwatches = listOf("#E1D3B3", "#C9A26B", "#9BB7D4", "#C8A2C8", "#B6C9B0")
    val idx = s.bwSelectedChannel.coerceIn(0, 5)
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BottomEditorHeader(
            title = "Siyah-Beyaz Pro",
            subtitle = "6 renk kanalı ağırlığı + Sepya/Soğuk tonlama",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApply
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            channels.forEachIndexed { i, (lbl, col) ->
                ProPill(
                    selected = idx == i,
                    onClick = { s.bwSelectedChannel = i },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(col))
                            Text(lbl, fontSize = 11.sp)
                        }
                    }
                )
            }
            ProPill(
                selected = s.bwTintEnabled,
                onClick = { s.bwTintEnabled = !s.bwTintEnabled; onPreview() },
                label = { Text(if (s.bwTintEnabled) "Tonlama ✓" else "Tonlama", fontSize = 11.sp) }
            )
            ProPill(selected = false, onClick = onReset, label = { Text("Sıfırla", fontSize = 11.sp, color = Color(0xFFFF8A8A)) })
        }
        if (s.bwTintEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Ton Rengi", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                tintSwatches.forEach { hex ->
                    val col = Color(android.graphics.Color.parseColor(hex))
                    val sel = s.bwTintColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(col)
                            .border(if (sel) 2.dp else 1.dp, if (sel) Color(0xFFF59E0B) else Color(0xFF3E4150), CircleShape)
                            .clickable { s.bwTintColorHex = hex; onPreview() }
                    )
                }
            }
        }
        CompactSliderRow(
            label = channels[idx].first,
            valueText = "${s.bwWeights[idx].roundToInt()}%",
            value = s.bwWeights[idx],
            range = -100f..250f,
            defaultValue = defaults[idx]
        ) { v ->
            s.bwWeights = s.bwWeights.toMutableList().apply { this[idx] = v }
            onPreview()
        }
    }
}

@Composable
internal fun PhotoFilterProPanel(
    s: StudioSessionState,
    onPreview: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    val presets = listOf(
        "Isıtma 85" to "#EC8A00",
        "Isıtma 81" to "#EBB113",
        "Soğutma 80" to "#006DFF",
        "Soğutma 82" to "#00B5FF",
        "Sepya" to "#AC7A33",
        "Sualtı" to "#00C2B1",
        "Kırmızı" to "#EA1A1A",
        "Turuncu" to "#F38417",
        "Yeşil" to "#19C919",
        "Mor" to "#9B1DEA"
    )
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BottomEditorHeader(
            title = "Fotoğraf Filtresi",
            subtitle = "Optik ısınma, soğutma ve renk filtreleri",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApply
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            presets.forEach { (lbl, hex) ->
                val col = Color(android.graphics.Color.parseColor(hex))
                val sel = s.pfColorHex.equals(hex, ignoreCase = true)
                ProPill(
                    selected = sel,
                    onClick = { s.pfColorHex = hex; onPreview() },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(col))
                            Text(lbl, fontSize = 11.sp)
                        }
                    }
                )
            }
            ProPill(
                selected = s.pfPreserveLum,
                onClick = { s.pfPreserveLum = !s.pfPreserveLum; onPreview() },
                label = { Text(if (s.pfPreserveLum) "Parlaklık Koru ✓" else "Parlaklık Koru", fontSize = 11.sp) }
            )
            ProPill(selected = false, onClick = onReset, label = { Text("Sıfırla", fontSize = 11.sp, color = Color(0xFFFF8A8A)) })
        }
        CompactSliderRow(
            label = "Yoğunluk",
            valueText = "${s.pfDensity.roundToInt()}%",
            value = s.pfDensity,
            range = 0f..100f,
            defaultValue = 25f
        ) { s.pfDensity = it; onPreview() }
    }
}

@Composable
internal fun SelectiveColorProPanel(
    s: StudioSessionState,
    onPreview: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    val ranges = listOf("Kırmızılar", "Sarılar", "Yeşiller", "Turkuazlar", "Maviler", "Macentalar", "Beyazlar", "Nötrler", "Siyahlar")
    val cmyk = listOf("Camgöbeği (C)", "Macenta (M)", "Sarı (Y)", "Siyah (K)")
    val rIdx = s.selColRangeIdx.coerceIn(0, 8)
    val pIdx = s.selColParamIdx.coerceIn(0, 3)
    val curVal = s.selColMatrix[rIdx][pIdx]

    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        BottomEditorHeader(
            title = "Seçici Renk (${ranges[rIdx]})",
            subtitle = "CMYK ince renk düzeltmesi",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApply
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ranges.forEachIndexed { idx, lbl ->
                ProPill(
                    selected = rIdx == idx,
                    onClick = { s.selColRangeIdx = idx },
                    label = { Text(lbl, fontSize = 10.5.sp) }
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Cyan", "Macenta", "Sarı", "Siyah").forEachIndexed { idx, lbl ->
                ProPill(
                    selected = pIdx == idx,
                    onClick = { s.selColParamIdx = idx },
                    label = { Text("$lbl ${s.selColMatrix[rIdx][idx].roundToInt()}", fontSize = 10.5.sp) }
                )
            }
            ProPill(
                selected = s.selColRelative,
                onClick = { s.selColRelative = !s.selColRelative; onPreview() },
                label = { Text(if (s.selColRelative) "Göreli" else "Mutlak", fontSize = 10.5.sp) }
            )
            ProPill(selected = false, onClick = onReset, label = { Text("Sıfırla", fontSize = 10.5.sp, color = Color(0xFFFF8A8A)) })
        }
        CompactSliderRow(
            label = cmyk[pIdx],
            valueText = "${curVal.roundToInt()}%",
            value = curVal,
            range = -100f..100f,
            defaultValue = 0f
        ) { v ->
            val updatedRow = s.selColMatrix[rIdx].toMutableList().apply { this[pIdx] = v }
            s.selColMatrix = s.selColMatrix.toMutableList().apply { this[rIdx] = updatedRow }
            onPreview()
        }
    }
}

@Composable
internal fun ChannelMixerProPanel(
    s: StudioSessionState,
    onPreview: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    val outChannels = listOf("Kırmızı Çıkış", "Yeşil Çıkış", "Mavi Çıkış")
    val inParams = listOf("Kırmızı", "Yeşil", "Mavi", "Sabit")
    val oIdx = if (s.chMixMono) 0 else s.chMixOutIdx.coerceIn(0, 2)
    val pIdx = s.chMixParamIdx.coerceIn(0, 3)
    val curVal = s.chMixRows[oIdx][pIdx]
    val defVal = if (!s.chMixMono && pIdx == oIdx) 100f else if (s.chMixMono && pIdx == 0) 40f else if (s.chMixMono && pIdx == 1) 40f else if (s.chMixMono && pIdx == 2) 20f else 0f

    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        BottomEditorHeader(
            title = if (s.chMixMono) "Kanal Karıştırıcı (Monokrom)" else "Kanal Karıştırıcı",
            subtitle = "RGB çıkış kanallarını çapraz karıştır",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApply
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!s.chMixMono) {
                outChannels.forEachIndexed { idx, lbl ->
                    ProPill(selected = oIdx == idx, onClick = { s.chMixOutIdx = idx }, label = { Text(lbl, fontSize = 10.5.sp) })
                }
            }
            ProPill(
                selected = s.chMixMono,
                onClick = {
                    s.chMixMono = !s.chMixMono
                    if (s.chMixMono) {
                        s.chMixRows = s.chMixRows.toMutableList().apply { this[0] = listOf(40f, 40f, 20f, 0f) }
                    } else {
                        s.chMixRows = listOf(listOf(100f, 0f, 0f, 0f), listOf(0f, 100f, 0f, 0f), listOf(0f, 0f, 100f, 0f))
                    }
                    onPreview()
                },
                label = { Text(if (s.chMixMono) "Monokrom ✓" else "Monokrom", fontSize = 10.5.sp) }
            )
            ProPill(selected = false, onClick = onReset, label = { Text("Sıfırla", fontSize = 10.5.sp, color = Color(0xFFFF8A8A)) })
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            inParams.forEachIndexed { idx, lbl ->
                ProPill(
                    selected = pIdx == idx,
                    onClick = { s.chMixParamIdx = idx },
                    label = { Text("$lbl ${s.chMixRows[oIdx][idx].roundToInt()}%", fontSize = 10.5.sp) }
                )
            }
        }
        CompactSliderRow(
            label = inParams[pIdx],
            valueText = "${curVal.roundToInt()}%",
            value = curVal,
            range = -200f..200f,
            defaultValue = defVal
        ) { v ->
            val updatedRow = s.chMixRows[oIdx].toMutableList().apply { this[pIdx] = v }
            s.chMixRows = s.chMixRows.toMutableList().apply { this[oIdx] = updatedRow }
            onPreview()
        }
    }
}

@Composable
internal fun GradientMapProPanel(
    s: StudioSessionState,
    onPreview: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    data class GmPreset(val name: String, val shadow: String, val mid: String?, val high: String)
    val presets = listOf(
        GmPreset("Teal-Turuncu", "#141B3A", "#B84A39", "#F7D488"),
        GmPreset("Sepya Klasik", "#1E140B", "#8C5A2B", "#F4E5C2"),
        GmPreset("Gece Mavisi", "#080D1F", "#1E4B8F", "#B5E2FF"),
        GmPreset("Altın Saat", "#231123", "#C75B39", "#FFE599"),
        GmPreset("Kızıl Noir", "#0A0A0E", "#8B1E3F", "#F2E9E4"),
        GmPreset("Zümrüt", "#071E1B", "#14746F", "#D9F99D"),
        GmPreset("Gün Batımı", "#2B1055", "#D63384", "#FFD166"),
        GmPreset("Siyah-Beyaz", "#000000", null, "#FFFFFF")
    )
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BottomEditorHeader(
            title = "Degrade Eşleme (Gradient Map)",
            subtitle = "Gölge ve parlak tonlara sinematik duotone eşleme",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApply
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            presets.forEachIndexed { idx, p ->
                val colors = buildList {
                    add(Color(android.graphics.Color.parseColor(p.shadow)))
                    if (p.mid != null) add(Color(android.graphics.Color.parseColor(p.mid)))
                    add(Color(android.graphics.Color.parseColor(p.high)))
                }
                val sel = s.gmPresetIdx == idx
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (sel) Color(0xFF232734) else Color(0xFF141720))
                        .border(if (sel) 1.5.dp else 1.dp, if (sel) Color(0xFFF59E0B) else Color(0xFF272B38), RoundedCornerShape(10.dp))
                        .clickable {
                            s.gmPresetIdx = idx
                            s.gmShadowHex = p.shadow
                            s.gmUseMid = p.mid != null
                            if (p.mid != null) s.gmMidHex = p.mid
                            s.gmHighHex = p.high
                            onPreview()
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(46.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.horizontalGradient(if (s.gmReverse) colors.reversed() else colors))
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(p.name, fontSize = 10.sp, color = if (sel) Color.White else Color(0xFFB4B9C7))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProPill(
                selected = s.gmReverse,
                onClick = { s.gmReverse = !s.gmReverse; onPreview() },
                label = { Text(if (s.gmReverse) "Ters Çevir ✓" else "Ters Çevir", fontSize = 11.sp) }
            )
            ProPill(selected = false, onClick = onReset, label = { Text("Sıfırla", fontSize = 11.sp, color = Color(0xFFFF8A8A)) })
        }
    }
}
