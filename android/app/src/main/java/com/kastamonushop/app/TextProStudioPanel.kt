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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kastamonushop.app.viewmodel.EditorViewModel
import kotlin.math.roundToInt

private val TEXT_PRO_SWATCHES = listOf(
    "#FFFFFF", "#111318", "#F59E0B", "#EF4444", "#38BDF8",
    "#10B981", "#A855F7", "#F43F5E", "#FDE047", "#FB923C"
)

private val TEXT_PRO_BLENDS = listOf(
    "Normal" to "Normal",
    "Multiply" to "Çarp",
    "Screen" to "Ekran",
    "Overlay" to "Kaplama",
    "SoftLight" to "Yumuşak",
    "HardLight" to "Sert"
)

private val TEXT_WARP_STYLES = listOf(
    "none" to "Yok",
    "arc" to "Yay",
    "arcLower" to "Alt Yay",
    "arcUpper" to "Üst Yay",
    "arch" to "Kemer",
    "bulge" to "Şişkin",
    "flag" to "Bayrak",
    "wave" to "Dalga",
    "rise" to "Yüksel",
    "fisheye" to "Balıkgözü"
)

@Composable
internal fun TextProStudioPanel(
    s: StudioSessionState,
    docW: Float,
    studioFonts: List<EditorViewModel.StudioFontOption>,
    onImportFont: () -> Unit,
    onCreateSelection: () -> Unit,
    onRasterize: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) } // 0=Yazı & Stil, 1=Biçim, 2=Efektler, 3=Bükme & Maske

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BottomEditorHeader(
            title = if (s.textEditLayerId != null) "Metni Düzenle (Pro)" else "Metin Stüdyosu Pro",
            subtitle = "Sürükle · Merkezde mıknatıslanır · 4 sekme",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApply
        )

        // 4 Clean Studio v2.0 Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Yazı & Stil", "Biçim", "Efektler", "Bükme").forEachIndexed { idx, lbl ->
                ProPill(
                    selected = tab == idx,
                    onClick = { tab = idx },
                    label = { Text(lbl, fontSize = 11.sp, fontWeight = if (tab == idx) FontWeight.Bold else FontWeight.Medium) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when (tab) {
            // ================================================================
            // TAB 0: YAZI, HAZIR ŞABLONLAR, FONT & RENK
            // ================================================================
            0 -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = s.textDraft,
                        onValueChange = { s.textDraft = it },
                        placeholder = { Text("Yazını buraya yaz...", fontSize = 12.sp, color = StudioV2Colors.TextMuted) },
                        singleLine = false,
                        maxLines = 2,
                        textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                        modifier = Modifier.weight(1f)
                    )
                    if (s.textEditLayerId != null) {
                        ProPill(
                            selected = false,
                            onClick = { s.textEditLayerId = null },
                            label = { Text("+ Yeni", fontSize = 11.sp) }
                        )
                    }
                }

                // One-Tap Pro Text Presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Şablon:", color = StudioV2Colors.TextMuted, fontSize = 10.5.sp)
                    ProPill(selected = false, onClick = {
                        s.textColorHex = "#FFFFFF"; s.textBold = false; s.textStrokeWidth = 0f
                        s.textShadowEnabled = false; s.textGlowSize = 0f; s.textGradientEnabled = false
                        s.textBgEnabled = false; s.textRotationDeg = 0f; s.textOpacity = 1f
                    }, label = { Text("Sade", fontSize = 10.5.sp) })
                    ProPill(selected = false, onClick = {
                        s.textColorHex = "#FDE047"; s.textBold = true; s.textCaps = "all"
                        s.textStrokeWidth = 8f; s.textStrokeColor = "#111318"
                        s.textShadowEnabled = true; s.textShadowDist = 10f; s.textShadowBlur = 12f
                        s.textGlowSize = 0f; s.textGradientEnabled = false; s.textBgEnabled = false
                    }, label = { Text("YouTube", fontSize = 10.5.sp) })
                    ProPill(selected = false, onClick = {
                        s.textColorHex = "#FFFFFF"; s.textBold = true
                        s.textStrokeWidth = 0f; s.textShadowEnabled = false
                        s.textGlowSize = 26f; s.textGlowColor = "#38BDF8"
                        s.textGradientEnabled = false; s.textBgEnabled = false
                    }, label = { Text("Neon", fontSize = 10.5.sp) })
                    ProPill(selected = false, onClick = {
                        s.textColorHex = "#FFFFFF"; s.textBold = true
                        s.textStrokeWidth = 0f; s.textShadowEnabled = false; s.textGlowSize = 0f
                        s.textGradientEnabled = false
                        s.textBgEnabled = true; s.textBgColor = "#111318"; s.textBgOpacity = 0.82f; s.textBgRadius = 18f; s.textBgPadding = 20f
                    }, label = { Text("Etiket", fontSize = 10.5.sp) })
                    ProPill(selected = false, onClick = {
                        s.textColorHex = "#FDE047"; s.textBold = true
                        s.textGradientEnabled = true; s.textGradientEndColor = "#F97316"; s.textGradientAngle = 90f
                        s.textStrokeWidth = 3f; s.textStrokeColor = "#111318"
                        s.textShadowEnabled = true; s.textShadowDist = 8f; s.textShadowBlur = 10f
                        s.textGlowSize = 0f; s.textBgEnabled = false
                    }, label = { Text("Altın", fontSize = 10.5.sp) })
                    ProPill(selected = false, onClick = {
                        s.textColorHex = "#FFFFFF"; s.textBold = false; s.textCaps = "all"; s.textTracking = 180f
                        s.textStrokeWidth = 0f; s.textShadowEnabled = true; s.textShadowDist = 4f; s.textShadowBlur = 14f
                        s.textGlowSize = 0f; s.textGradientEnabled = false; s.textBgEnabled = false
                    }, label = { Text("Sinema", fontSize = 10.5.sp) })
                }

                // Font Strip + Import .ttf/.otf
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProPill(selected = false, onClick = onImportFont, label = {
                        Text("+ Font", fontSize = 11.sp, color = StudioV2Colors.Amber, fontWeight = FontWeight.Bold)
                    })
                    studioFonts.forEach { f ->
                        ProPill(
                            selected = s.textFontFamily.equals(f.family, true),
                            onClick = { s.textFontFamily = f.family },
                            label = { Text(f.displayName, fontSize = 11.sp, fontFamily = f.composeFont) }
                        )
                    }
                }

                // Color Swatches
                ColorSwatchRowV2(selectedHex = s.textColorHex, onSelect = { s.textColorHex = it })
            }

            // ================================================================
            // TAB 1: BİÇİM (Boyut, Döndürme, Opaklık, Kutu Genişliği, Karışım, Harf/Satır)
            // ================================================================
            1 -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProPill(selected = s.textBold, onClick = { s.textBold = !s.textBold }, label = { Text("K", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp) })
                    ProPill(selected = s.textItalic, onClick = { s.textItalic = !s.textItalic }, label = { Text("İ", fontStyle = FontStyle.Italic, fontSize = 12.sp) })
                    ProPill(selected = s.textUnderline, onClick = { s.textUnderline = !s.textUnderline }, label = { Text("A̲", fontSize = 12.sp) })
                    ProPill(selected = s.textStrike, onClick = { s.textStrike = !s.textStrike }, label = { Text("S̶", fontSize = 12.sp) })
                    ProPill(selected = s.textCaps == "all", onClick = { s.textCaps = if (s.textCaps == "all") "normal" else "all" }, label = { Text("AA", fontSize = 11.sp) })
                    ProPill(selected = s.textVertical, onClick = { s.textVertical = !s.textVertical }, label = { Text("Dikey", fontSize = 11.sp) })
                    ProPill(selected = s.textAlign == "left", onClick = { s.textAlign = "left" }, label = { Icon(Icons.Default.FormatAlignLeft, null, modifier = Modifier.size(15.dp)) })
                    ProPill(selected = s.textAlign == "center", onClick = { s.textAlign = "center" }, label = { Icon(Icons.Default.FormatAlignCenter, null, modifier = Modifier.size(15.dp)) })
                    ProPill(selected = s.textAlign == "right", onClick = { s.textAlign = "right" }, label = { Icon(Icons.Default.FormatAlignRight, null, modifier = Modifier.size(15.dp)) })
                }

                val defSize = (docW * 0.055f).coerceIn(24f, 180f)
                CompactSliderRow("Boyut", "${s.textSizePx.roundToInt()}px", s.textSizePx, 8f..420f, defSize) { s.textSizePx = it }
                CompactSliderRow("Döndür", "${s.textRotationDeg.roundToInt()}°", s.textRotationDeg, -180f..180f, 0f) { s.textRotationDeg = it }
                CompactSliderRow("Opaklık", "%${(s.textOpacity * 100).roundToInt()}", s.textOpacity, 0.05f..1f, 1f) { s.textOpacity = it }
                CompactSliderRow("Harf Ara", "${s.textTracking.roundToInt()}", s.textTracking, -60f..360f, 0f) { s.textTracking = it }
                CompactSliderRow("Satır Ara", String.format("%.2f", s.textLineHeight), s.textLineHeight, 0.8f..2.4f, 1.2f) { s.textLineHeight = it }
                CompactSliderRow("Kutu Gen.", if (s.textBoxWidthNorm < 0.05f) "Serbest" else "%${(s.textBoxWidthNorm * 100).roundToInt()}", s.textBoxWidthNorm, 0f..0.95f, 0f) { s.textBoxWidthNorm = it }
                CompactSliderRow("Taban", "${s.textBaselineShift.roundToInt()}px", s.textBaselineShift, -60f..60f, 0f) { s.textBaselineShift = it }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Karışım:", color = StudioV2Colors.TextMuted, fontSize = 10.5.sp)
                    TEXT_PRO_BLENDS.forEach { (id, lbl) ->
                        ProPill(selected = s.textBlendMode == id, onClick = { s.textBlendMode = id }, label = { Text(lbl, fontSize = 10.5.sp) })
                    }
                }
            }

            // ================================================================
            // TAB 2: EFEKTLER (Kontur, Gölge, Neon Işıma, Degrade Dolgu, Arka Plan Kutusu)
            // ================================================================
            2 -> {
                var fxSub by remember { mutableIntStateOf(0) } // 0=Kontur, 1=Gölge, 2=Işıma, 3=Degrade, 4=Kutu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "Kontur" + if (s.textStrokeWidth > 0.4f) " ●" else "",
                        "Gölge" + if (s.textShadowEnabled) " ●" else "",
                        "Neon" + if (s.textGlowSize > 0.4f) " ●" else "",
                        "Degrade" + if (s.textGradientEnabled) " ●" else "",
                        "Kutu" + if (s.textBgEnabled) " ●" else ""
                    ).forEachIndexed { idx, lbl ->
                        ProPill(selected = fxSub == idx, onClick = { fxSub = idx }, label = { Text(lbl, fontSize = 11.sp) })
                    }
                }

                when (fxSub) {
                    0 -> { // Kontur (Stroke)
                        CompactSliderRow("Kalınlık", "${s.textStrokeWidth.roundToInt()}px", s.textStrokeWidth, 0f..36f, 0f) { s.textStrokeWidth = it }
                        ColorSwatchRowV2(selectedHex = s.textStrokeColor, onSelect = { s.textStrokeColor = it; if (s.textStrokeWidth < 1f) s.textStrokeWidth = 6f })
                    }
                    1 -> { // Gölge (Drop Shadow)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProPill(selected = s.textShadowEnabled, onClick = { s.textShadowEnabled = !s.textShadowEnabled }, label = { Text(if (s.textShadowEnabled) "Gölge: AÇIK" else "Gölge: KAPALI", fontSize = 11.sp) })
                        }
                        if (s.textShadowEnabled) {
                            CompactSliderRow("Mesafe", "${s.textShadowDist.roundToInt()}px", s.textShadowDist, 0f..50f, 10f) { s.textShadowDist = it }
                            CompactSliderRow("Bulanık", "${s.textShadowBlur.roundToInt()}px", s.textShadowBlur, 0f..40f, 12f) { s.textShadowBlur = it }
                            CompactSliderRow("Açı", "${s.textShadowAngle.roundToInt()}°", s.textShadowAngle, -180f..180f, 120f) { s.textShadowAngle = it }
                            ColorSwatchRowV2(selectedHex = s.textShadowColor, onSelect = { s.textShadowColor = it })
                        }
                    }
                    2 -> { // Neon Işıma (Outer Glow)
                        CompactSliderRow("Işıma", "${s.textGlowSize.roundToInt()}px", s.textGlowSize, 0f..60f, 0f) { s.textGlowSize = it }
                        ColorSwatchRowV2(selectedHex = s.textGlowColor, onSelect = { s.textGlowColor = it; if (s.textGlowSize < 1f) s.textGlowSize = 20f })
                    }
                    3 -> { // Degrade Dolgu (Gradient Text)
                        ProPill(selected = s.textGradientEnabled, onClick = { s.textGradientEnabled = !s.textGradientEnabled }, label = { Text(if (s.textGradientEnabled) "Degrade Dolgu: AÇIK" else "Degrade Dolgu: KAPALI", fontSize = 11.sp) })
                        if (s.textGradientEnabled) {
                            CompactSliderRow("Açı", "${s.textGradientAngle.roundToInt()}°", s.textGradientAngle, -180f..180f, 90f) { s.textGradientAngle = it }
                            ColorSwatchRowV2(selectedHex = s.textGradientEndColor, onSelect = { s.textGradientEndColor = it })
                        }
                    }
                    4 -> { // Arka Plan Kutusu (Background Pill)
                        ProPill(selected = s.textBgEnabled, onClick = { s.textBgEnabled = !s.textBgEnabled }, label = { Text(if (s.textBgEnabled) "Arka Plan Kutusu: AÇIK" else "Arka Plan Kutusu: KAPALI", fontSize = 11.sp) })
                        if (s.textBgEnabled) {
                            CompactSliderRow("Opaklık", "%${(s.textBgOpacity * 100).roundToInt()}", s.textBgOpacity, 0.1f..1f, 0.8f) { s.textBgOpacity = it }
                            CompactSliderRow("Köşe", "${s.textBgRadius.roundToInt()}px", s.textBgRadius, 0f..60f, 16f) { s.textBgRadius = it }
                            CompactSliderRow("Boşluk", "${s.textBgPadding.roundToInt()}px", s.textBgPadding, 6f..72f, 18f) { s.textBgPadding = it }
                            ColorSwatchRowV2(selectedHex = s.textBgColor, onSelect = { s.textBgColor = it })
                        }
                    }
                }
            }

            // ================================================================
            // TAB 3: BÜKME (WARP) & MASKE
            // ================================================================
            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TEXT_WARP_STYLES.forEach { (id, lbl) ->
                        ProPill(selected = s.textWarpStyle == id, onClick = { s.textWarpStyle = id }, label = { Text(lbl, fontSize = 11.sp) })
                    }
                }
                if (s.textWarpStyle != "none") {
                    CompactSliderRow("Büküm", "${s.textWarpBend.roundToInt()}%", s.textWarpBend, -100f..100f, 50f) { s.textWarpBend = it }
                    CompactSliderRow("Yatay", "${s.textWarpHDist.roundToInt()}%", s.textWarpHDist, -100f..100f, 0f) { s.textWarpHDist = it }
                    CompactSliderRow("Dikey", "${s.textWarpVDist.roundToInt()}%", s.textWarpVDist, -100f..100f, 0f) { s.textWarpVDist = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProPill(selected = false, onClick = onCreateSelection, label = { Text("Seçim Maskesi Oluştur", fontSize = 11.sp) })
                    ProPill(selected = false, onClick = onRasterize, label = { Text("Rasterize Et", fontSize = 11.sp) })
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchRowV2(selectedHex: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TEXT_PRO_SWATCHES.forEach { hex ->
            val col = remember(hex) { runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(Color.White) }
            val sel = selectedHex.equals(hex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(col)
                    .border(
                        width = if (sel) 2.5.dp else 1.dp,
                        color = if (sel) StudioV2Colors.Amber else Color(0x44FFFFFF),
                        shape = CircleShape
                    )
                    .clickable { onSelect(hex) }
            )
        }
    }
}

internal fun wrapTextForBox(raw: String, maxCharsPerLine: Int): String {
    if (maxCharsPerLine <= 2) return raw
    val out = StringBuilder()
    raw.split("\n").forEachIndexed { lineIdx, para ->
        if (lineIdx > 0) out.append('\n')
        var curLen = 0
        para.split(" ").forEach { word ->
            if (curLen > 0 && curLen + 1 + word.length > maxCharsPerLine) {
                out.append('\n')
                curLen = 0
            } else if (curLen > 0) {
                out.append(' ')
                curLen += 1
            }
            out.append(word)
            curLen += word.length
        }
    }
    return out.toString()
}
