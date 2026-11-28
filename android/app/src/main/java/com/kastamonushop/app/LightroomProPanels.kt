package com.kastamonushop.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    s.camFocusParamIdx = 0
}

private data class CamSliderSpec(
    val label: String,
    val valueText: String,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val defaultValue: Float,
    val onChange: (Float) -> Unit
)

/**
 * v2.3.0 Parça 2: Compact OLED single-focus-slider Camera RAW Pro panel (never covers the photo).
 * Includes previously missing Tint, Saturation, Film Grain, and Green/Blue Calibration Saturation.
 */
@Composable
internal fun CameraRawProPanel(
    s: StudioSessionState,
    onPreview: () -> Unit,
    onImportXmp: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    val specs: List<CamSliderSpec> = when (s.camTabIdx) {
        0 -> listOf(
            CamSliderSpec("Pozlama", "${"%.2f".format(s.camExposureVal)} EV", s.camExposureVal, -3f..3f, 0f) { s.camExposureVal = it; onPreview() },
            CamSliderSpec("Kontrast", "${s.camContrastVal.roundToInt()}", s.camContrastVal, -100f..100f, 0f) { s.camContrastVal = it; onPreview() },
            CamSliderSpec("Gölgeler", "${s.camShadowsVal.roundToInt()}", s.camShadowsVal, -100f..100f, 0f) { s.camShadowsVal = it; onPreview() },
            CamSliderSpec("Parlaklar", "${s.camHighlightsVal.roundToInt()}", s.camHighlightsVal, -100f..100f, 0f) { s.camHighlightsVal = it; onPreview() },
            CamSliderSpec("Beyazlar", "${s.camWhitesVal.roundToInt()}", s.camWhitesVal, -100f..100f, 0f) { s.camWhitesVal = it; onPreview() },
            CamSliderSpec("Siyahlar", "${s.camBlacksVal.roundToInt()}", s.camBlacksVal, -100f..100f, 0f) { s.camBlacksVal = it; onPreview() },
            CamSliderSpec("Sıcaklık", "${s.camTempVal.roundToInt()}", s.camTempVal, -100f..100f, 0f) { s.camTempVal = it; onPreview() },
            CamSliderSpec("Renk Tonu", "${s.camTintVal.roundToInt()}", s.camTintVal, -100f..100f, 0f) { s.camTintVal = it; onPreview() },
            CamSliderSpec("Canlılık", "${s.camVibranceVal.roundToInt()}", s.camVibranceVal, -100f..100f, 0f) { s.camVibranceVal = it; onPreview() },
            CamSliderSpec("Doygunluk", "${s.camSatVal.roundToInt()}", s.camSatVal, -100f..100f, 0f) { s.camSatVal = it; onPreview() }
        )
        1 -> listOf(
            CamSliderSpec("Doku", "${s.camTextureVal.roundToInt()}", s.camTextureVal, -100f..100f, 0f) { s.camTextureVal = it; onPreview() },
            CamSliderSpec("Berraklık", "${s.camClarityVal.roundToInt()}", s.camClarityVal, -100f..100f, 0f) { s.camClarityVal = it; onPreview() },
            CamSliderSpec("Sis Gider", "${s.camDehazeVal.roundToInt()}", s.camDehazeVal, -100f..100f, 0f) { s.camDehazeVal = it; onPreview() },
            CamSliderSpec("Keskinlik", "${s.camSharpenVal.roundToInt()}", s.camSharpenVal, 0f..150f, 0f) { s.camSharpenVal = it; onPreview() },
            CamSliderSpec("Gürültü", "${s.camNoiseLumVal.roundToInt()}", s.camNoiseLumVal, 0f..100f, 0f) { s.camNoiseLumVal = it; onPreview() },
            CamSliderSpec("Renk Gürült.", "${s.camNoiseColVal.roundToInt()}", s.camNoiseColVal, 0f..100f, 0f) { s.camNoiseColVal = it; onPreview() },
            CamSliderSpec("Vinyet", "${s.camVignetteVal.roundToInt()}", s.camVignetteVal, -100f..100f, 0f) { s.camVignetteVal = it; onPreview() },
            CamSliderSpec("Film Greni", "${s.camGrainVal.roundToInt()}", s.camGrainVal, 0f..100f, 0f) { s.camGrainVal = it; onPreview() }
        )
        2 -> listOf(
            CamSliderSpec("Gölge Tonu", "${s.camGradeShadowHue.roundToInt()}°", s.camGradeShadowHue, 0f..360f, 220f) { s.camGradeShadowHue = it; onPreview() },
            CamSliderSpec("Gölge Doyg.", "${s.camGradeShadowSat.roundToInt()}", s.camGradeShadowSat, 0f..100f, 0f) { s.camGradeShadowSat = it; onPreview() },
            CamSliderSpec("Orta Ton", "${s.camGradeMidHue.roundToInt()}°", s.camGradeMidHue, 0f..360f, 40f) { s.camGradeMidHue = it; onPreview() },
            CamSliderSpec("Orta Doyg.", "${s.camGradeMidSat.roundToInt()}", s.camGradeMidSat, 0f..100f, 0f) { s.camGradeMidSat = it; onPreview() },
            CamSliderSpec("Aydınlık Ton", "${s.camGradeHighHue.roundToInt()}°", s.camGradeHighHue, 0f..360f, 45f) { s.camGradeHighHue = it; onPreview() },
            CamSliderSpec("Aydınlık Doyg.", "${s.camGradeHighSat.roundToInt()}", s.camGradeHighSat, 0f..100f, 0f) { s.camGradeHighSat = it; onPreview() }
        )
        else -> listOf(
            CamSliderSpec("Kırmızı Ton", "${s.camCalRedHue.roundToInt()}", s.camCalRedHue, -100f..100f, 0f) { s.camCalRedHue = it; onPreview() },
            CamSliderSpec("Kırmızı Doyg.", "${s.camCalRedSat.roundToInt()}", s.camCalRedSat, -100f..100f, 0f) { s.camCalRedSat = it; onPreview() },
            CamSliderSpec("Yeşil Ton", "${s.camCalGreenHue.roundToInt()}", s.camCalGreenHue, -100f..100f, 0f) { s.camCalGreenHue = it; onPreview() },
            CamSliderSpec("Yeşil Doyg.", "${s.camCalGreenSat.roundToInt()}", s.camCalGreenSat, -100f..100f, 0f) { s.camCalGreenSat = it; onPreview() },
            CamSliderSpec("Mavi Ton", "${s.camCalBlueHue.roundToInt()}", s.camCalBlueHue, -100f..100f, 0f) { s.camCalBlueHue = it; onPreview() },
            CamSliderSpec("Mavi Doyg.", "${s.camCalBlueSat.roundToInt()}", s.camCalBlueSat, -100f..100f, 0f) { s.camCalBlueSat = it; onPreview() }
        )
    }
    val fIdx = s.camFocusParamIdx.coerceIn(0, specs.lastIndex)
    val active = specs[fIdx]

    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        BottomEditorHeader(
            title = "Kamera RAW Pro",
            subtitle = "",
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
            listOf("Işık & Renk", "Doku & Gren", "Renk Grading", "Kalibrasyon").forEachIndexed { idx, lbl ->
                ProPill(
                    selected = s.camTabIdx == idx,
                    onClick = { s.camTabIdx = idx; s.camFocusParamIdx = 0 },
                    label = { Text(lbl, fontSize = 11.sp) }
                )
            }
            OutlinedButton(
                onClick = onImportXmp,
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(".xmp", fontSize = 10.5.sp, color = Color(0xFFF59E0B))
            }
            ProPill(selected = false, onClick = onReset, label = { Text("Sıfırla", fontSize = 10.5.sp, color = Color(0xFFFF8A8A)) })
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            specs.forEachIndexed { idx, sp ->
                val modified = kotlin.math.abs(sp.value - sp.defaultValue) > 0.01f
                ProPill(
                    selected = fIdx == idx,
                    onClick = { s.camFocusParamIdx = idx },
                    label = {
                        Text(
                            if (modified) "${sp.label} •" else sp.label,
                            fontSize = 10.5.sp,
                            color = if (fIdx == idx) Color.White else if (modified) Color(0xFFF59E0B) else Color(0xFFB4B9C7)
                        )
                    }
                )
            }
        }
        CompactSliderRow(
            label = active.label,
            valueText = active.valueText,
            value = active.value,
            range = active.range,
            defaultValue = active.defaultValue,
            onChange = active.onChange
        )
    }
}
