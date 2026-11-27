package com.kastamonushop.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * v1.8.0 Content-Aware Studio Panels (100% PhotoCraft desktop parity):
 * - ContentAwareFillPanel (`edit.contentAwareFill`)
 * - ContentAwareMovePanel (`paint.contentAwareMove`)
 * - ContentAwareScalePanel (`edit.contentAwareScale`)
 * Built strictly with v1.7.5 Pro Design System (`ProSleekTrackSlider` / `CompactSliderRow`, `ProPill`).
 */

@Composable
internal fun ContentAwareFillPanel(
    s: StudioSessionState,
    onPreview: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        BottomEditorHeader(
            title = "İçerik Duyarlı Dolgu",
            subtitle = "",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApply
        )

        // Row 1: Sampling + Color Adaptation + Rotation Adaptation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Örnek:", fontSize = 10.sp, color = Color(0xFF9AA4BD))
            listOf("auto" to "Otomatik", "rectangular" to "Dikdörtgen").forEach { (id, lbl) ->
                ProPill(
                    selected = s.cafSampling == id,
                    onClick = { s.cafSampling = id; onPreview() },
                    label = { Text(lbl, fontSize = 11.sp, maxLines = 1) }
                )
            }
            Text("Renk:", fontSize = 10.sp, color = Color(0xFF9AA4BD))
            listOf("none" to "Yok", "default" to "Normal", "high" to "Yüksek", "veryHigh" to "Çok Yük.").forEach { (id, lbl) ->
                ProPill(
                    selected = s.cafColorAdaptation == id,
                    onClick = { s.cafColorAdaptation = id; onPreview() },
                    label = { Text(lbl, fontSize = 11.sp, maxLines = 1) }
                )
            }
        }

        // Row 2: Rotation + Scale/Mirror/All Layers + Output
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dönüş:", fontSize = 10.sp, color = Color(0xFF9AA4BD))
            listOf("none" to "Yok", "low" to "Düşük", "medium" to "Orta", "high" to "Yüksek", "full" to "Tam").forEach { (id, lbl) ->
                ProPill(
                    selected = s.cafRotationAdaptation == id,
                    onClick = { s.cafRotationAdaptation = id; onPreview() },
                    label = { Text(lbl, fontSize = 11.sp, maxLines = 1) }
                )
            }
            ProPill(
                selected = s.cafScale,
                onClick = { s.cafScale = !s.cafScale; onPreview() },
                label = { Text("Ölçek", fontSize = 11.sp, maxLines = 1) }
            )
            ProPill(
                selected = s.cafMirror,
                onClick = { s.cafMirror = !s.cafMirror; onPreview() },
                label = { Text("Ayna", fontSize = 11.sp, maxLines = 1) }
            )
            ProPill(
                selected = s.cafSampleAllLayers,
                onClick = { s.cafSampleAllLayers = !s.cafSampleAllLayers; onPreview() },
                label = { Text("Tüm Katm.", fontSize = 11.sp, maxLines = 1) }
            )
        }

        // Row 3: Output target + Reset
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Çıktı:", fontSize = 10.sp, color = Color(0xFF9AA4BD))
            listOf("current" to "Mevcut Katman", "new" to "Yeni Katman", "duplicate" to "Katmanı Çoğalt").forEach { (id, lbl) ->
                ProPill(
                    selected = s.cafOutput == id,
                    onClick = { s.cafOutput = id },
                    label = { Text(lbl, fontSize = 11.sp, maxLines = 1) }
                )
            }
            ProPill(
                selected = false,
                onClick = onReset,
                label = { Text("Sıfırla", fontSize = 11.sp, color = Color(0xFFFF8A8A)) }
            )
        }
    }
}

@Composable
internal fun ContentAwareMovePanel(
    s: StudioSessionState,
    onPreview: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BottomEditorHeader(
            title = "İçerik Duyarlı Taşıma",
            subtitle = "Seçili nesneyi tuvalde sürükle (${s.camMoveDx.roundToInt()}px, ${s.camMoveDy.roundToInt()}px)",
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
            ProPill(
                selected = s.camMoveMode == "move",
                onClick = { s.camMoveMode = "move"; onPreview() },
                label = { Text("Taşı", fontSize = 11.sp) }
            )
            ProPill(
                selected = s.camMoveMode == "extend",
                onClick = { s.camMoveMode = "extend"; onPreview() },
                label = { Text("Çoğalt (Extend)", fontSize = 11.sp) }
            )
            ProPill(
                selected = s.camMoveSampleAll,
                onClick = { s.camMoveSampleAll = !s.camMoveSampleAll; onPreview() },
                label = { Text("Tüm Katmanlar", fontSize = 11.sp) }
            )
            ProPill(
                selected = false,
                onClick = onReset,
                label = { Text("Sıfırla", fontSize = 11.sp, color = Color(0xFFFF8A8A)) }
            )
        }

        CompactSliderRow(
            label = "Yapı",
            valueText = "${s.camMoveStructure.roundToInt()}",
            value = s.camMoveStructure,
            range = 1f..7f,
            defaultValue = 4f
        ) { s.camMoveStructure = it; onPreview() }

        CompactSliderRow(
            label = "Renk Uyumu",
            valueText = "${s.camMoveColor.roundToInt()}",
            value = s.camMoveColor,
            range = 0f..10f,
            defaultValue = 0f
        ) { s.camMoveColor = it }

        CompactSliderRow(
            label = "Ölçek",
            valueText = "%${s.camMoveScale.roundToInt()}",
            value = s.camMoveScale,
            range = 25f..250f,
            defaultValue = 100f
        ) { s.camMoveScale = it }

        CompactSliderRow(
            label = "Döndür",
            valueText = "${s.camMoveRotate.roundToInt()}°",
            value = s.camMoveRotate,
            range = -180f..180f,
            defaultValue = 0f
        ) { s.camMoveRotate = it }
    }
}

@Composable
internal fun ContentAwareScalePanel(
    s: StudioSessionState,
    onPreview: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BottomEditorHeader(
            title = "İçerik Duyarlı Ölçekleme",
            subtitle = "",
            applyLabel = "Uygula",
            onClose = onClose,
            onApply = onApply
        )

        CompactSliderRow(
            label = "Genişlik",
            valueText = "%${s.casScaleX.roundToInt()}",
            value = s.casScaleX,
            range = 40f..200f,
            defaultValue = 100f
        ) { s.casScaleX = it; onPreview() }

        CompactSliderRow(
            label = "Yükseklik",
            valueText = "%${s.casScaleY.roundToInt()}",
            value = s.casScaleY,
            range = 40f..200f,
            defaultValue = 100f
        ) { s.casScaleY = it; onPreview() }

        CompactSliderRow(
            label = "Koruma",
            valueText = "%${s.casAmount.roundToInt()}",
            value = s.casAmount,
            range = 0f..100f,
            defaultValue = 100f
        ) { s.casAmount = it; onPreview() }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProPill(
                selected = s.casProtectSkinTones,
                onClick = { s.casProtectSkinTones = !s.casProtectSkinTones; onPreview() },
                label = { Text("Ten Tonlarını Koru", fontSize = 11.sp, maxLines = 1) }
            )
            ProPill(
                selected = false,
                onClick = { s.casScaleX = 125f; s.casScaleY = 100f; onPreview() },
                label = { Text("Yatay +25%", fontSize = 11.sp) }
            )
            ProPill(
                selected = false,
                onClick = { s.casScaleX = 80f; s.casScaleY = 100f; onPreview() },
                label = { Text("Daralt -20%", fontSize = 11.sp) }
            )
            ProPill(
                selected = false,
                onClick = { s.casScaleX = 100f; s.casScaleY = 125f; onPreview() },
                label = { Text("Dikey +25%", fontSize = 11.sp) }
            )
            ProPill(
                selected = false,
                onClick = onReset,
                label = { Text("Sıfırla", fontSize = 11.sp, color = Color(0xFFFF8A8A)) }
            )
        }
    }
}
