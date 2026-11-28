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
    var tab by remember { mutableIntStateOf(0) } // 0=Yazı, 1=Biçim, 2=Efekt, 3=Bükme
    var activePreset by remember { mutableStateOf("Sade") }
    var formatParam by remember { mutableIntStateOf(0) } // Single-slider selector in Biçim tab!

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        BottomEditorHeader(
            title = if (s.textEditLayerId != null) "Metni Düzenle" else "Metin Stüdyosu",
            subtitle = "Sürükle · Merkezde mıknatıslanır",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApply
        )

        // 4 Clean Studio v2.0 Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Yazı", "Biçim", "Efektler", "Bükme").forEachIndexed { idx, lbl ->
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
                        singleLine = true,
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

                // One-Tap Pro Text Presets — all rendered LIVE on canvas at 60 FPS!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProPill(selected = activePreset == "Sade", onClick = {
                        activePreset = "Sade"
                        s.textColorHex = "#FFFFFF"; s.textBold = false; s.textCaps = "normal"; s.textTracking = 0f
                        s.textStrokeWidth = 0f; s.textShadowEnabled = false; s.textGlowSize = 0f
                        s.textGradientEnabled = false; s.textBgEnabled = false; s.textRotationDeg = 0f; s.textOpacity = 1f
                    }, label = { Text("Sade", fontSize = 10.5.sp) })
                    ProPill(selected = activePreset == "YouTube", onClick = {
                        activePreset = "YouTube"
                        s.textColorHex = "#FDE047"; s.textBold = true; s.textCaps = "all"; s.textTracking = 0f
                        s.textStrokeWidth = 10f; s.textStrokeColor = "#111318"
                        s.textShadowEnabled = true; s.textShadowDist = 10f; s.textShadowBlur = 12f; s.textShadowColor = "#000000"
                        s.textGlowSize = 0f; s.textGradientEnabled = false; s.textBgEnabled = false; s.textOpacity = 1f
                    }, label = { Text("YouTube", fontSize = 10.5.sp) })
                    ProPill(selected = activePreset == "Neon", onClick = {
                        activePreset = "Neon"
                        s.textColorHex = "#FFFFFF"; s.textBold = true; s.textCaps = "normal"; s.textTracking = 40f
                        s.textStrokeWidth = 0f; s.textShadowEnabled = false
                        s.textGlowSize = 28f; s.textGlowColor = "#38BDF8"
                        s.textGradientEnabled = false; s.textBgEnabled = false; s.textOpacity = 1f
                    }, label = { Text("Neon", fontSize = 10.5.sp) })
                    ProPill(selected = activePreset == "Etiket", onClick = {
                        activePreset = "Etiket"
                        s.textColorHex = "#FFFFFF"; s.textBold = true; s.textCaps = "normal"; s.textTracking = 0f
                        s.textStrokeWidth = 0f; s.textShadowEnabled = false; s.textGlowSize = 0f; s.textGradientEnabled = false
                        s.textBgEnabled = true; s.textBgColor = "#111318"; s.textBgOpacity = 0.85f; s.textBgRadius = 16f; s.textBgPadding = 18f; s.textOpacity = 1f
                    }, label = { Text("Etiket", fontSize = 10.5.sp) })
                    ProPill(selected = activePreset == "Altın", onClick = {
                        activePreset = "Altın"
                        s.textColorHex = "#FDE047"; s.textBold = true; s.textCaps = "all"; s.textTracking = 20f
                        s.textGradientEnabled = true; s.textGradientEndColor = "#F97316"; s.textGradientAngle = 90f
                        s.textStrokeWidth = 4f; s.textStrokeColor = "#111318"
                        s.textShadowEnabled = true; s.textShadowDist = 8f; s.textShadowBlur = 10f; s.textShadowColor = "#000000"
                        s.textGlowSize = 0f; s.textBgEnabled = false; s.textOpacity = 1f
                    }, label = { Text("Altın", fontSize = 10.5.sp) })
                    ProPill(selected = activePreset == "Sinema", onClick = {
                        activePreset = "Sinema"
                        s.textColorHex = "#FFFFFF"; s.textBold = false; s.textCaps = "all"; s.textTracking = 200f
                        s.textStrokeWidth = 0f; s.textShadowEnabled = true; s.textShadowDist = 4f; s.textShadowBlur = 16f; s.textShadowColor = "#000000"
                        s.textGlowSize = 0f; s.textGradientEnabled = false; s.textBgEnabled = false; s.textOpacity = 0.95f
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

                ColorSwatchRowV2(selectedHex = s.textColorHex, onSelect = { s.textColorHex = it })
            }

            // ================================================================
            // TAB 1: BİÇİM — ULTRA-COMPACT SINGLE-SLIDER DESIGN (Photo stays 78%+ visible!)
            // ================================================================
            1 -> {
                // Row 1: Style toggles
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

                // Row 2: Parameter selector pills (only 1 slider shows at a time!)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Boyut", "Döndür", "Opaklık", "Harf Ara", "Satır Ara", "Kutu Gen.", "Taban", "Karışım").forEachIndexed { idx, lbl ->
                        ProPill(
                            selected = formatParam == idx,
                            onClick = { formatParam = idx },
                            label = { Text(lbl, fontSize = 11.sp) }
                        )
                    }
                }

                // Row 3: Active single control
                val defSize = (docW * 0.055f).coerceIn(24f, 180f)
                when (formatParam) {
                    0 -> CompactSliderRow("Boyut", "${s.textSizePx.roundToInt()}px", s.textSizePx, 8f..420f, defSize) { s.textSizePx = it }
                    1 -> CompactSliderRow("Döndür", "${s.textRotationDeg.roundToInt()}°", s.textRotationDeg, -180f..180f, 0f) { s.textRotationDeg = it }
                    2 -> CompactSliderRow("Opaklık", "%${(s.textOpacity * 100).roundToInt()}", s.textOpacity, 0.05f..1f, 1f) { s.textOpacity = it }
                    3 -> CompactSliderRow("Harf Ara", "${s.textTracking.roundToInt()}", s.textTracking, -60f..360f, 0f) { s.textTracking = it }
                    4 -> CompactSliderRow("Satır Ara", String.format("%.2f", s.textLineHeight), s.textLineHeight, 0.8f..2.4f, 1.2f) { s.textLineHeight = it }
                    5 -> CompactSliderRow("Kutu Gen.", if (s.textBoxWidthNorm < 0.05f) "Serbest" else "%${(s.textBoxWidthNorm * 100).roundToInt()}", s.textBoxWidthNorm, 0f..0.95f, 0f) { s.textBoxWidthNorm = it }
                    6 -> CompactSliderRow("Taban", "${s.textBaselineShift.roundToInt()}px", s.textBaselineShift, -60f..60f, 0f) { s.textBaselineShift = it }
                    else -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TEXT_PRO_BLENDS.forEach { (id, lbl) ->
                            ProPill(selected = s.textBlendMode == id, onClick = { s.textBlendMode = id }, label = { Text(lbl, fontSize = 11.sp) })
                        }
                    }
                }
            }

            // ================================================================
            // TAB 2: EFEKTLER — COMPACT & 100% LIVE ON CANVAS
            // ================================================================
            2 -> {
                var fxSub by remember { mutableIntStateOf(0) }
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
                    0 -> {
                        CompactSliderRow("Kalınlık", "${s.textStrokeWidth.roundToInt()}px", s.textStrokeWidth, 0f..36f, 0f) { s.textStrokeWidth = it }
                        ColorSwatchRowV2(selectedHex = s.textStrokeColor, onSelect = { s.textStrokeColor = it; if (s.textStrokeWidth < 1f) s.textStrokeWidth = 6f })
                    }
                    1 -> {
                        var shadowParam by remember { mutableIntStateOf(0) }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProPill(selected = s.textShadowEnabled, onClick = { s.textShadowEnabled = !s.textShadowEnabled }, label = { Text(if (s.textShadowEnabled) "Açık" else "Kapalı", fontSize = 11.sp) })
                            if (s.textShadowEnabled) {
                                ProPill(selected = shadowParam == 0, onClick = { shadowParam = 0 }, label = { Text("Mesafe", fontSize = 10.5.sp) })
                                ProPill(selected = shadowParam == 1, onClick = { shadowParam = 1 }, label = { Text("Bulanık", fontSize = 10.5.sp) })
                                ProPill(selected = shadowParam == 2, onClick = { shadowParam = 2 }, label = { Text("Açı", fontSize = 10.5.sp) })
                            }
                        }
                        if (s.textShadowEnabled) {
                            when (shadowParam) {
                                0 -> CompactSliderRow("Mesafe", "${s.textShadowDist.roundToInt()}px", s.textShadowDist, 0f..50f, 10f) { s.textShadowDist = it }
                                1 -> CompactSliderRow("Bulanık", "${s.textShadowBlur.roundToInt()}px", s.textShadowBlur, 0f..40f, 12f) { s.textShadowBlur = it }
                                else -> CompactSliderRow("Açı", "${s.textShadowAngle.roundToInt()}°", s.textShadowAngle, -180f..180f, 120f) { s.textShadowAngle = it }
                            }
                            ColorSwatchRowV2(selectedHex = s.textShadowColor, onSelect = { s.textShadowColor = it })
                        }
                    }
                    2 -> {
                        CompactSliderRow("Işıma", "${s.textGlowSize.roundToInt()}px", s.textGlowSize, 0f..60f, 0f) { s.textGlowSize = it }
                        ColorSwatchRowV2(selectedHex = s.textGlowColor, onSelect = { s.textGlowColor = it; if (s.textGlowSize < 1f) s.textGlowSize = 24f })
                    }
                    3 -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProPill(selected = s.textGradientEnabled, onClick = { s.textGradientEnabled = !s.textGradientEnabled }, label = { Text(if (s.textGradientEnabled) "Degrade: AÇIK" else "Degrade: KAPALI", fontSize = 11.sp) })
                        }
                        if (s.textGradientEnabled) {
                            CompactSliderRow("Açı", "${s.textGradientAngle.roundToInt()}°", s.textGradientAngle, -180f..180f, 90f) { s.textGradientAngle = it }
                            ColorSwatchRowV2(selectedHex = s.textGradientEndColor, onSelect = { s.textGradientEndColor = it })
                        }
                    }
                    4 -> {
                        var bgParam by remember { mutableIntStateOf(0) }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProPill(selected = s.textBgEnabled, onClick = { s.textBgEnabled = !s.textBgEnabled }, label = { Text(if (s.textBgEnabled) "Açık" else "Kapalı", fontSize = 11.sp) })
                            if (s.textBgEnabled) {
                                ProPill(selected = bgParam == 0, onClick = { bgParam = 0 }, label = { Text("Opaklık", fontSize = 10.5.sp) })
                                ProPill(selected = bgParam == 1, onClick = { bgParam = 1 }, label = { Text("Köşe", fontSize = 10.5.sp) })
                                ProPill(selected = bgParam == 2, onClick = { bgParam = 2 }, label = { Text("Boşluk", fontSize = 10.5.sp) })
                            }
                        }
                        if (s.textBgEnabled) {
                            when (bgParam) {
                                0 -> CompactSliderRow("Opaklık", "%${(s.textBgOpacity * 100).roundToInt()}", s.textBgOpacity, 0.1f..1f, 0.85f) { s.textBgOpacity = it }
                                1 -> CompactSliderRow("Köşe", "${s.textBgRadius.roundToInt()}px", s.textBgRadius, 0f..60f, 16f) { s.textBgRadius = it }
                                else -> CompactSliderRow("Boşluk", "${s.textBgPadding.roundToInt()}px", s.textBgPadding, 6f..72f, 18f) { s.textBgPadding = it }
                            }
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
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProPill(selected = false, onClick = onCreateSelection, label = { Text("Seçim Maskesi", fontSize = 11.sp) })
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
