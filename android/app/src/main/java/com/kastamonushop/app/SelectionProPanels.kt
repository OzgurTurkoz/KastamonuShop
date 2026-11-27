package com.kastamonushop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * v1.8.2 Advanced Selection & Mask Panels (100% PhotoCraft desktop parity):
 * - ColorRangePanel (`select.colorRange`)
 * - SelectSkyPanel (`select.sky`)
 * - FocusAreaPanel (`select.focusArea`)
 * - SelectModifyPanel (`select.modify.expand/contract/border/smooth/feather` + `select.grow` + `select.similar`)
 */

@Composable
internal fun ColorRangePanel(
    s: StudioSessionState,
    onPreview: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val presets = listOf(
        "sampledColors" to "Örnek Renk",
        "skinTones" to "Ten Tonu",
        "highlights" to "Aydınlık",
        "midtones" to "Orta Ton",
        "shadows" to "Gölge",
        "reds" to "Kırmızı",
        "yellows" to "Sarı",
        "greens" to "Yeşil",
        "cyans" to "Cyan",
        "blues" to "Mavi",
        "magentas" to "Magenta"
    )
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BottomEditorHeader(
            title = "Renk Aralığı Seçimi",
            subtitle = "",
            applyLabel = "Seç",
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
            if (s.crPreset == "sampledColors") {
                val parsedColor = remember(s.crColorHex) {
                    runCatching { Color(android.graphics.Color.parseColor(s.crColorHex)) }.getOrDefault(Color(0xFF4F9CFF))
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(parsedColor)
                        .border(1.5.dp, Color.White, CircleShape)
                        .clickable { showColorPicker = true }
                )
            }
            presets.forEach { (id, lbl) ->
                ProPill(
                    selected = s.crPreset == id,
                    onClick = { s.crPreset = id; onPreview() },
                    label = { Text(lbl, fontSize = 11.sp, maxLines = 1) }
                )
            }
        }

        CompactSliderRow(
            label = "Bulanıklık",
            valueText = "${s.crFuzziness.roundToInt()}",
            value = s.crFuzziness,
            range = 0f..200f,
            defaultValue = 40f
        ) { s.crFuzziness = it; onPreview() }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("replace" to "Yeni", "add" to "Ekle", "subtract" to "Çıkar").forEach { (id, lbl) ->
                ProPill(
                    selected = s.crMode == id,
                    onClick = { s.crMode = id; onPreview() },
                    label = { Text(lbl, fontSize = 11.sp) }
                )
            }
            ProPill(
                selected = s.crInvert,
                onClick = { s.crInvert = !s.crInvert; onPreview() },
                label = { Text("Ters Çevir", fontSize = 11.sp) }
            )
            ProPill(
                selected = s.crSampleAll,
                onClick = { s.crSampleAll = !s.crSampleAll; onPreview() },
                label = { Text("Tüm Katm.", fontSize = 11.sp) }
            )
        }
    }

    if (showColorPicker) {
        ProColorWheelDialog(
            initialHex = s.crColorHex,
            onDismiss = { showColorPicker = false },
            onConfirm = { hex, _ ->
                s.crColorHex = hex
                showColorPicker = false
                onPreview()
            }
        )
    }
}

@Composable
internal fun SelectSkyPanel(
    s: StudioSessionState,
    onPreview: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BottomEditorHeader(
            title = "Gökyüzü Seç",
            subtitle = "",
            applyLabel = "Seç",
            onClose = onClose,
            onApply = onApply
        )

        CompactSliderRow(
            label = "Eşik",
            valueText = "${s.skyThreshold.roundToInt()}",
            value = s.skyThreshold,
            range = 0f..100f,
            defaultValue = 50f
        ) { s.skyThreshold = it; onPreview() }

        CompactSliderRow(
            label = "Yumuşaklık",
            valueText = "${s.skySoftness.roundToInt()}",
            value = s.skySoftness,
            range = 0f..100f,
            defaultValue = 50f
        ) { s.skySoftness = it; onPreview() }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("replace" to "Yeni", "add" to "Ekle", "subtract" to "Çıkar").forEach { (id, lbl) ->
                ProPill(
                    selected = s.skyMode == id,
                    onClick = { s.skyMode = id; onPreview() },
                    label = { Text(lbl, fontSize = 11.sp) }
                )
            }
            ProPill(
                selected = s.skySampleAll,
                onClick = { s.skySampleAll = !s.skySampleAll; onPreview() },
                label = { Text("Tüm Katmanlar", fontSize = 11.sp) }
            )
        }
    }
}

@Composable
internal fun FocusAreaPanel(
    s: StudioSessionState,
    onPreview: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BottomEditorHeader(
            title = "Odak Alanı Seçimi",
            subtitle = "Net özneyi bulanık arka plandan ayırır",
            applyLabel = "Seç",
            onClose = onClose,
            onApply = onApply
        )

        CompactSliderRow(
            label = "Odak Aralığı",
            valueText = "%${s.focusRange.roundToInt()}",
            value = s.focusRange,
            range = 0f..100f,
            defaultValue = 50f
        ) { s.focusRange = it; onPreview() }

        CompactSliderRow(
            label = "Gürültü",
            valueText = "%${s.focusNoise.roundToInt()}",
            value = s.focusNoise,
            range = 0f..100f,
            defaultValue = 0f
        ) { s.focusNoise = it; onPreview() }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("replace" to "Yeni", "add" to "Ekle", "subtract" to "Çıkar").forEach { (id, lbl) ->
                ProPill(
                    selected = s.focusMode == id,
                    onClick = { s.focusMode = id; onPreview() },
                    label = { Text(lbl, fontSize = 11.sp) }
                )
            }
            ProPill(
                selected = s.focusSampleAll,
                onClick = { s.focusSampleAll = !s.focusSampleAll; onPreview() },
                label = { Text("Tüm Katmanlar", fontSize = 11.sp) }
            )
        }
    }
}

@Composable
internal fun SelectModifyPanel(
    s: StudioSessionState,
    onApplyModify: () -> Unit,
    onGrow: () -> Unit,
    onSimilar: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BottomEditorHeader(
            title = "Seçimi Değiştir",
            subtitle = "",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApplyModify
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                "expand" to "Genişlet",
                "contract" to "Daralt",
                "border" to "Kenarlık",
                "smooth" to "Pürüzsüz",
                "feather" to "Yumuşat"
            ).forEach { (id, lbl) ->
                ProPill(
                    selected = s.selModifyOp == id,
                    onClick = { s.selModifyOp = id },
                    label = { Text(lbl, fontSize = 11.sp) }
                )
            }
        }

        CompactSliderRow(
            label = "Yarıçap",
            valueText = "${s.selModifyRadius.roundToInt()}px",
            value = s.selModifyRadius,
            range = 1f..100f,
            defaultValue = 8f
        ) { s.selModifyRadius = it }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Renk Genişletme:", fontSize = 10.sp, color = Color(0xFF9AA4BD))
            ProPill(
                selected = false,
                onClick = onGrow,
                label = { Text("Büyüt (Grow)", fontSize = 11.sp) }
            )
            ProPill(
                selected = false,
                onClick = onSimilar,
                label = { Text("Benzerini Seç", fontSize = 11.sp) }
            )
        }

        CompactSliderRow(
            label = "Tolerans",
            valueText = "${s.selGrowTolerance.roundToInt()}",
            value = s.selGrowTolerance,
            range = 0f..255f,
            defaultValue = 32f
        ) { s.selGrowTolerance = it }
    }
}
