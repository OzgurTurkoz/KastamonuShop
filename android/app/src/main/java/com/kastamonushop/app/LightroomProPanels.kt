package com.kastamonushop.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

internal fun buildCameraRawProJson(s: StudioSessionState): String {
    val root = org.json.JSONObject()
    root.put("exposure", (s.camExposureVal * 100.0).roundToInt() / 100.0)
    root.put("contrast", s.camContrastVal.roundToInt())
    root.put("highlights", s.camHighlightsVal.roundToInt())
    root.put("shadows", s.camShadowsVal.roundToInt())
    root.put("whites", s.camWhitesVal.roundToInt())
    root.put("blacks", s.camBlacksVal.roundToInt())
    root.put("temperature", s.camTempVal.roundToInt())
    root.put("tint", s.camTintVal.roundToInt())
    root.put("vibrance", s.camVibranceVal.roundToInt())
    root.put("saturation", s.camSatVal.roundToInt())
    root.put("texture", s.camTextureVal.roundToInt())
    root.put("clarity", s.camClarityVal.roundToInt())
    root.put("dehaze", s.camDehazeVal.roundToInt())
    root.put("sharpenAmount", s.camSharpenVal.roundToInt())
    root.put("noiseLuminance", s.camNoiseLumVal.roundToInt())
    root.put("noiseColor", s.camNoiseColVal.roundToInt())
    root.put("gradeShadows", org.json.JSONObject().apply {
        put("hue", s.camGradeShadowHue.roundToInt())
        put("sat", s.camGradeShadowSat.roundToInt())
        put("lum", 0)
    })
    root.put("gradeMidtones", org.json.JSONObject().apply {
        put("hue", s.camGradeMidHue.roundToInt())
        put("sat", s.camGradeMidSat.roundToInt())
        put("lum", 0)
    })
    root.put("gradeHighlights", org.json.JSONObject().apply {
        put("hue", s.camGradeHighHue.roundToInt())
        put("sat", s.camGradeHighSat.roundToInt())
        put("lum", 0)
    })
    root.put("calibrationHue", org.json.JSONArray().apply {
        put(s.camCalRedHue.roundToInt())
        put(s.camCalGreenHue.roundToInt())
        put(s.camCalBlueHue.roundToInt())
    })
    root.put("calibrationSat", org.json.JSONArray().apply {
        put(s.camCalRedSat.roundToInt())
        put(s.camCalGreenSat.roundToInt())
        put(s.camCalBlueSat.roundToInt())
    })
    root.put("vignetteAmount", s.camVignetteVal.roundToInt())
    root.put("grainAmount", s.camGrainVal.roundToInt())
    return root.toString()
}

internal fun resetCameraRawProState(s: StudioSessionState) {
    s.camExposureVal = 0f; s.camContrastVal = 0f; s.camHighlightsVal = 0f; s.camShadowsVal = 0f
    s.camWhitesVal = 0f; s.camBlacksVal = 0f; s.camTempVal = 0f; s.camTintVal = 0f
    s.camVibranceVal = 0f; s.camSatVal = 0f; s.camTextureVal = 0f; s.camClarityVal = 0f
    s.camDehazeVal = 0f; s.camSharpenVal = 0f; s.camNoiseLumVal = 0f; s.camNoiseColVal = 0f
    s.camGradeShadowSat = 0f; s.camGradeMidSat = 0f; s.camGradeHighSat = 0f
    s.camCalRedHue = 0f; s.camCalRedSat = 0f; s.camCalGreenHue = 0f; s.camCalGreenSat = 0f
    s.camCalBlueHue = 0f; s.camCalBlueSat = 0f; s.camVignetteVal = 0f; s.camGrainVal = 0f
}

@Composable
internal fun CameraRawProPanel(
    s: StudioSessionState,
    onPreview: () -> Unit,
    onImportXmp: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        BottomEditorHeader(
            title = "Kamera RAW Pro",
            subtitle = "Lightroom kalitesinde ışık, doku, 3-yollu grading & kalibrasyon",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApply
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Işık", "Doku & Gürültü", "Renk Grading", "Kalibrasyon").forEachIndexed { idx, lbl ->
                FilterChip(
                    selected = s.camTabIdx == idx,
                    onClick = { s.camTabIdx = idx },
                    label = { Text(lbl, fontSize = 11.sp) }
                )
            }
            OutlinedButton(
                onClick = onImportXmp,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(".xmp Yükle", fontSize = 11.sp, color = Color(0xFF4F9CFF))
            }
            TextButton(onClick = onReset) {
                Text("Sıfırla", fontSize = 11.sp, color = Color(0xFFFF8A80))
            }
        }
        when (s.camTabIdx) {
            0 -> {
                CompactSliderRow("Pozlama", "${"%.2f".format(s.camExposureVal)} EV", s.camExposureVal, -3f..3f, defaultValue = 0f) { s.camExposureVal = it; onPreview() }
                CompactSliderRow("Kontrast", "${s.camContrastVal.roundToInt()}", s.camContrastVal, -100f..100f, defaultValue = 0f) { s.camContrastVal = it; onPreview() }
                CompactSliderRow("Gölgeler", "${s.camShadowsVal.roundToInt()}", s.camShadowsVal, -100f..100f, defaultValue = 0f) { s.camShadowsVal = it; onPreview() }
                CompactSliderRow("Parlaklar", "${s.camHighlightsVal.roundToInt()}", s.camHighlightsVal, -100f..100f, defaultValue = 0f) { s.camHighlightsVal = it; onPreview() }
                CompactSliderRow("Beyazlar", "${s.camWhitesVal.roundToInt()}", s.camWhitesVal, -100f..100f, defaultValue = 0f) { s.camWhitesVal = it; onPreview() }
                CompactSliderRow("Siyahlar", "${s.camBlacksVal.roundToInt()}", s.camBlacksVal, -100f..100f, defaultValue = 0f) { s.camBlacksVal = it; onPreview() }
                CompactSliderRow("Sıcaklık", "${s.camTempVal.roundToInt()}", s.camTempVal, -100f..100f, defaultValue = 0f) { s.camTempVal = it; onPreview() }
                CompactSliderRow("Canlılık", "${s.camVibranceVal.roundToInt()}", s.camVibranceVal, -100f..100f, defaultValue = 0f) { s.camVibranceVal = it; onPreview() }
            }
            1 -> {
                CompactSliderRow("Doku (Texture)", "${s.camTextureVal.roundToInt()}", s.camTextureVal, -100f..100f, defaultValue = 0f) { s.camTextureVal = it; onPreview() }
                CompactSliderRow("Berraklık", "${s.camClarityVal.roundToInt()}", s.camClarityVal, -100f..100f, defaultValue = 0f) { s.camClarityVal = it; onPreview() }
                CompactSliderRow("Sis Giderme", "${s.camDehazeVal.roundToInt()}", s.camDehazeVal, -100f..100f, defaultValue = 0f) { s.camDehazeVal = it; onPreview() }
                CompactSliderRow("Keskinlik", "${s.camSharpenVal.roundToInt()}", s.camSharpenVal, 0f..150f, defaultValue = 0f) { s.camSharpenVal = it; onPreview() }
                CompactSliderRow("Gürültü Azalt", "${s.camNoiseLumVal.roundToInt()}", s.camNoiseLumVal, 0f..100f, defaultValue = 0f) { s.camNoiseLumVal = it; onPreview() }
                CompactSliderRow("Renk Gürültüsü", "${s.camNoiseColVal.roundToInt()}", s.camNoiseColVal, 0f..100f, defaultValue = 0f) { s.camNoiseColVal = it; onPreview() }
            }
            2 -> {
                CompactSliderRow("Gölge Tonu", "${s.camGradeShadowHue.roundToInt()}°", s.camGradeShadowHue, 0f..360f, defaultValue = 220f) { s.camGradeShadowHue = it; onPreview() }
                CompactSliderRow("Gölge Doyg.", "${s.camGradeShadowSat.roundToInt()}", s.camGradeShadowSat, 0f..100f, defaultValue = 0f) { s.camGradeShadowSat = it; onPreview() }
                CompactSliderRow("Orta Ton", "${s.camGradeMidHue.roundToInt()}°", s.camGradeMidHue, 0f..360f, defaultValue = 40f) { s.camGradeMidHue = it; onPreview() }
                CompactSliderRow("Orta Doyg.", "${s.camGradeMidSat.roundToInt()}", s.camGradeMidSat, 0f..100f, defaultValue = 0f) { s.camGradeMidSat = it; onPreview() }
                CompactSliderRow("Aydınlık Tonu", "${s.camGradeHighHue.roundToInt()}°", s.camGradeHighHue, 0f..360f, defaultValue = 45f) { s.camGradeHighHue = it; onPreview() }
                CompactSliderRow("Aydınlık Doyg.", "${s.camGradeHighSat.roundToInt()}", s.camGradeHighSat, 0f..100f, defaultValue = 0f) { s.camGradeHighSat = it; onPreview() }
            }
            else -> {
                CompactSliderRow("Kırmızı Ton", "${s.camCalRedHue.roundToInt()}", s.camCalRedHue, -100f..100f, defaultValue = 0f) { s.camCalRedHue = it; onPreview() }
                CompactSliderRow("Kırmızı Doyg.", "${s.camCalRedSat.roundToInt()}", s.camCalRedSat, -100f..100f, defaultValue = 0f) { s.camCalRedSat = it; onPreview() }
                CompactSliderRow("Yeşil Ton", "${s.camCalGreenHue.roundToInt()}", s.camCalGreenHue, -100f..100f, defaultValue = 0f) { s.camCalGreenHue = it; onPreview() }
                CompactSliderRow("Mavi Ton", "${s.camCalBlueHue.roundToInt()}", s.camCalBlueHue, -100f..100f, defaultValue = 0f) { s.camCalBlueHue = it; onPreview() }
                CompactSliderRow("Vinyet", "${s.camVignetteVal.roundToInt()}", s.camVignetteVal, -100f..100f, defaultValue = 0f) { s.camVignetteVal = it; onPreview() }
                CompactSliderRow("Film Greni", "${s.camGrainVal.roundToInt()}", s.camGrainVal, 0f..100f, defaultValue = 0f) { s.camGrainVal = it; onPreview() }
            }
        }
    }
}

@Composable
internal fun LensCorrectionPanel(
    s: StudioSessionState,
    onPreview: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        BottomEditorHeader(
            title = "Lens Düzeltme",
            subtitle = "Fıçı/yastık bükülme, renk saçılması ve perspektif düzelt",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApply
        )
        CompactSliderRow("Bükülme", "${s.lensDistortion.roundToInt()}", s.lensDistortion, -100f..100f, defaultValue = 0f) { s.lensDistortion = it; onPreview() }
        CompactSliderRow("Vinyet", "${s.lensVignette.roundToInt()}", s.lensVignette, -100f..100f, defaultValue = 0f) { s.lensVignette = it; onPreview() }
        CompactSliderRow("Kırmızı/Cyan", "${s.lensRedCyan.roundToInt()}", s.lensRedCyan, -100f..100f, defaultValue = 0f) { s.lensRedCyan = it; onPreview() }
        CompactSliderRow("Mavi/Sarı", "${s.lensBlueYellow.roundToInt()}", s.lensBlueYellow, -100f..100f, defaultValue = 0f) { s.lensBlueYellow = it; onPreview() }
        CompactSliderRow("Dikey Eğim", "${s.lensVertical.roundToInt()}", s.lensVertical, -100f..100f, defaultValue = 0f) { s.lensVertical = it; onPreview() }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onReset) { Text("Sıfırla", fontSize = 11.sp, color = Color(0xFFFF8A80)) }
        }
    }
}

@Composable
internal fun CubeLutPanel(
    fileName: String,
    opacityPct: Float,
    onPickFile: () -> Unit,
    onOpacity: (Float) -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BottomEditorHeader(
            title = "3D LUT (.cube / .3dl)",
            subtitle = if (fileName.isBlank()) "Sinematik LUT dosyası seçerek anında uygula" else "Yüklü LUT: $fileName",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApply
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onPickFile, shape = RoundedCornerShape(12.dp)) {
                Text(if (fileName.isBlank()) ".cube / .3dl Seç" else "Başka LUT Seç", fontSize = 12.sp)
            }
            if (fileName.isNotBlank()) {
                Text(fileName, fontSize = 11.sp, color = Color(0xFF4F9CFF), fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        if (fileName.isNotBlank()) {
            CompactSliderRow("Yoğunluk", "%${opacityPct.roundToInt()}", opacityPct, 0f..100f, defaultValue = 100f, onChange = onOpacity)
        }
    }
}
