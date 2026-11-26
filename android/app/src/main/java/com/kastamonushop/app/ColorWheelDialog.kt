package com.kastamonushop.app

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private const val COLOR_FAVS_PREFS = "stonebridge_color_prefs"
private const val COLOR_FAVS_KEY = "favorite_colors_v1"

private val DEFAULT_FAVORITE_COLORS = listOf(
    "#53FFA5", "#FF4D4F", "#FFA940", "#FFEC3D",
    "#52C41A", "#13C2C2", "#1677FF", "#722ED1",
    "#EB2F96", "#F1E78D", "#FFFFFF", "#000000"
)

private fun loadFavoriteColors(context: Context): List<String> {
    val prefs = context.getSharedPreferences(COLOR_FAVS_PREFS, Context.MODE_PRIVATE)
    val raw = prefs.getString(COLOR_FAVS_KEY, null) ?: return DEFAULT_FAVORITE_COLORS
    val list = raw.split(",").map { it.trim().uppercase() }.filter { it.matches(Regex("^#[0-9A-F]{6}$")) }
    return if (list.isEmpty()) DEFAULT_FAVORITE_COLORS else list.distinct()
}

private fun saveFavoriteColors(context: Context, list: List<String>) {
    val prefs = context.getSharedPreferences(COLOR_FAVS_PREFS, Context.MODE_PRIVATE)
    prefs.edit().putString(COLOR_FAVS_KEY, list.distinct().joinToString(",")).apply()
}

private fun hexToHsvTriple(hex: String): Triple<Float, Float, Float> {
    return try {
        val c = AndroidColor.parseColor(hex)
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(c, hsv)
        Triple(hsv[0], hsv[1], hsv[2])
    } catch (_: Throwable) {
        Triple(150f, 0.7f, 1.0f)
    }
}

private fun hsvTripleToHex(h: Float, s: Float, v: Float): String {
    val argb = AndroidColor.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
    return String.format("#%06X", 0xFFFFFF and argb)
}

private fun isLightColorHex(hex: String): Boolean {
    return try {
        val c = AndroidColor.parseColor(hex)
        val r = AndroidColor.red(c)
        val g = AndroidColor.green(c)
        val b = AndroidColor.blue(c)
        (0.299 * r + 0.587 * g + 0.114 * b) > 145.0
    } catch (_: Throwable) {
        false
    }
}

@Composable
internal fun ProColorWheelDialog(
    initialHex: String,
    initialOpacity: Float = 1f,
    showOpacity: Boolean = true,
    onPickEyedropper: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (hex: String, opacity: Float) -> Unit
) {
    val context = LocalContext.current
    val initialHsv = remember(initialHex) { hexToHsvTriple(initialHex) }
    var hue by remember { mutableFloatStateOf(initialHsv.first) }
    var sat by remember { mutableFloatStateOf(initialHsv.second) }
    var value by remember { mutableFloatStateOf(initialHsv.third) }
    var opacity by remember { mutableFloatStateOf(initialOpacity.coerceIn(0.05f, 1f)) }
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Çark, 1 = Favoriler
    var showHexInput by remember { mutableStateOf(false) }
    var hexInputDraft by remember { mutableStateOf(initialHex.uppercase()) }
    var favorites by remember { mutableStateOf(loadFavoriteColors(context)) }

    val currentHex = remember(hue, sat, value) { hsvTripleToHex(hue, sat, value) }
    val currentColor = remember(currentHex) {
        runCatching { Color(AndroidColor.parseColor(currentHex)) }.getOrDefault(Color.White)
    }
    val alpha255 = (opacity * 255f).roundToInt().coerceIn(1, 255)
    val isFav = favorites.any { it.equals(currentHex, ignoreCase = true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF23242A),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header: Renk + [Çark] [Favoriler]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Renk",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF17181D))
                            .border(1.dp, Color(0xFF3A3D4A), RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (activeTab == 0) Color(0xFF3A3E4C) else Color.Transparent)
                                .clickable { activeTab = 0 }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Renk Çarkı",
                                tint = if (activeTab == 0) Color(0xFF6BB5FF) else Color(0xFFA0A5B5),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (activeTab == 1) Color(0xFF3A3E4C) else Color.Transparent)
                                .clickable { activeTab = 1 }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Favoriler",
                                color = if (activeTab == 1) Color.White else Color(0xFFB5BAC9),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Preview bar: [#HEX - 255] [★] [+] [Damlalık]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(currentColor.copy(alpha = opacity))
                            .border(1.dp, Color(0xFF555A6B), RoundedCornerShape(8.dp))
                            .clickable {
                                hexInputDraft = currentHex
                                showHexInput = !showHexInput
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$currentHex - $alpha255",
                            color = if (isLightColorHex(currentHex) && opacity > 0.45f) Color(0xFF14151A) else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Favorite star button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1B1C22))
                            .border(1.dp, Color(0xFF3D404D), RoundedCornerShape(8.dp))
                            .clickable {
                                val next = if (isFav) {
                                    favorites.filterNot { it.equals(currentHex, ignoreCase = true) }
                                } else {
                                    listOf(currentHex) + favorites
                                }
                                favorites = next
                                saveFavoriteColors(context, next)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Favori",
                            tint = if (isFav) Color(0xFFFFD644) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Manual HEX + button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (showHexInput) Color(0xFF394158) else Color(0xFF1B1C22))
                            .border(1.dp, Color(0xFF3D404D), RoundedCornerShape(8.dp))
                            .clickable {
                                hexInputDraft = currentHex
                                showHexInput = !showHexInput
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "HEX Gir",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Eyedropper button
                    if (onPickEyedropper != null) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1B1C22))
                                .border(1.dp, Color(0xFF3D404D), RoundedCornerShape(8.dp))
                                .clickable {
                                    onDismiss()
                                    onPickEyedropper()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Colorize,
                                contentDescription = "Damlalık",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Optional inline HEX input
                if (showHexInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = hexInputDraft,
                            onValueChange = { raw ->
                                hexInputDraft = raw.uppercase().take(7)
                            },
                            singleLine = true,
                            label = { Text("HEX Kodu (#RRGGBB)", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White)
                        )
                        Button(
                            onClick = {
                                val normalized = if (hexInputDraft.startsWith("#")) hexInputDraft else "#$hexInputDraft"
                                if (normalized.matches(Regex("^#[0-9A-F]{6}$"))) {
                                    val (nh, ns, nv) = hexToHsvTriple(normalized)
                                    hue = nh; sat = ns; value = nv
                                    showHexInput = false
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("Ayarla", fontSize = 12.sp)
                        }
                    }
                }

                if (activeTab == 0) {
                    // Main HSV Wheel + Vertical Brightness Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(236.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Circular HSV Wheel
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .size(226.dp)
                                    .pointerInput(Unit) {
                                        fun updateWheel(pos: Offset) {
                                            val cx = size.width * 0.5f
                                            val cy = size.height * 0.5f
                                            val radius = (minOf(size.width, size.height) * 0.5f).coerceAtLeast(1f)
                                            val dx = pos.x - cx
                                            val dy = pos.y - cy
                                            val dist = hypot(dx, dy).coerceAtMost(radius)
                                            var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                            if (deg < 0f) deg += 360f
                                            hue = deg
                                            sat = (dist / radius).coerceIn(0f, 1f)
                                        }
                                        detectTapGestures { pos -> updateWheel(pos) }
                                    }
                                    .pointerInput(Unit) {
                                        fun updateWheel(pos: Offset) {
                                            val cx = size.width * 0.5f
                                            val cy = size.height * 0.5f
                                            val radius = (minOf(size.width, size.height) * 0.5f).coerceAtLeast(1f)
                                            val dx = pos.x - cx
                                            val dy = pos.y - cy
                                            val dist = hypot(dx, dy).coerceAtMost(radius)
                                            var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                            if (deg < 0f) deg += 360f
                                            hue = deg
                                            sat = (dist / radius).coerceIn(0f, 1f)
                                        }
                                        detectDragGestures(
                                            onDragStart = { pos -> updateWheel(pos) },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                updateWheel(change.position)
                                            }
                                        )
                                    }
                            ) {
                                val radius = size.minDimension * 0.5f
                                val center = Offset(size.width * 0.5f, size.height * 0.5f)
                                // 1. Hue sweep gradient (0° = Red at 3 o'clock matching atan2)
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            Color(0xFFFF0000),
                                            Color(0xFFFFFF00),
                                            Color(0xFF00FF00),
                                            Color(0xFF00FFFF),
                                            Color(0xFF0000FF),
                                            Color(0xFFFF00FF),
                                            Color(0xFFFF0000)
                                        ),
                                        center = center
                                    ),
                                    radius = radius,
                                    center = center
                                )
                                // 2. Radial white center -> transparent edge (Saturation)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color.White, Color.Transparent),
                                        center = center,
                                        radius = radius
                                    ),
                                    radius = radius,
                                    center = center
                                )
                                // 3. Subtle brightness dim overlay reflecting Value bar
                                if (value < 0.99f) {
                                    drawCircle(
                                        color = Color.Black.copy(alpha = (1f - value).coerceIn(0f, 0.85f)),
                                        radius = radius,
                                        center = center
                                    )
                                }
                                // 4. Selector thumb
                                val rad = Math.toRadians(hue.toDouble())
                                val tx = center.x + (cos(rad) * sat * radius).toFloat()
                                val ty = center.y + (sin(rad) * sat * radius).toFloat()
                                drawCircle(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    radius = 10.dp.toPx(),
                                    center = Offset(tx, ty),
                                    style = Stroke(width = 3.dp.toPx())
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 8.5.dp.toPx(),
                                    center = Offset(tx, ty),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }

                        // Right Vertical Brightness (Value) Bar
                        Canvas(
                            modifier = Modifier
                                .width(28.dp)
                                .height(226.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF454957), RoundedCornerShape(6.dp))
                                .pointerInput(Unit) {
                                    fun updateVal(y: Float) {
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        value = (1f - (y / h)).coerceIn(0f, 1f)
                                    }
                                    detectTapGestures { pos -> updateVal(pos.y) }
                                }
                                .pointerInput(Unit) {
                                    fun updateVal(y: Float) {
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        value = (1f - (y / h)).coerceIn(0f, 1f)
                                    }
                                    detectDragGestures(
                                        onDragStart = { pos -> updateVal(pos.y) },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            updateVal(change.position.y)
                                        }
                                    )
                                }
                        ) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.White, Color.Black)
                                )
                            )
                            val thumbY = ((1f - value) * size.height).coerceIn(6.dp.toPx(), size.height - 6.dp.toPx())
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(1.dp.toPx(), thumbY - 6.dp.toPx()),
                                size = Size(size.width - 2.dp.toPx(), 12.dp.toPx()),
                                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawRoundRect(
                                color = Color.Black.copy(alpha = 0.7f),
                                topLeft = Offset(2.5.dp.toPx(), thumbY - 4.5.dp.toPx()),
                                size = Size(size.width - 5.dp.toPx(), 9.dp.toPx()),
                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                                style = Stroke(width = 1.2.dp.toPx())
                            )
                        }
                    }
                } else {
                    // Favorites Grid Tab
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(236.dp)
                    ) {
                        if (favorites.isEmpty()) {
                            Text(
                                text = "Henüz favori renk yok.\nYukarıdaki ★ simgesiyle renk ekleyebilirsin.",
                                color = Color(0xFF9AA0B2),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(favorites, key = { it }) { favHex ->
                                    val favColor = remember(favHex) {
                                        runCatching { Color(AndroidColor.parseColor(favHex)) }.getOrDefault(Color.Gray)
                                    }
                                    val selected = favHex.equals(currentHex, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .height(52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(favColor)
                                            .border(
                                                width = if (selected) 2.5.dp else 1.dp,
                                                color = if (selected) Color.White else Color(0xFF454957),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                val (nh, ns, nv) = hexToHsvTriple(favHex)
                                                hue = nh; sat = ns; value = nv
                                                activeTab = 0
                                            }
                                    ) {
                                        Text(
                                            text = favHex.removePrefix("#"),
                                            color = if (isLightColorHex(favHex)) Color(0xFF14151A) else Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(4.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(3.dp)
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x99000000))
                                                .clickable {
                                                    val next = favorites.filterNot { it.equals(favHex, ignoreCase = true) }
                                                    favorites = next
                                                    saveFavoriteColors(context, next)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Sil",
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Opacity Slider
                if (showOpacity) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF17181D)
                        ) {
                            Text(
                                text = "Opaklık",
                                color = Color(0xFFD6DAE6),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Slider(
                            value = opacity,
                            onValueChange = { opacity = it },
                            valueRange = 0.05f..1f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Action Buttons: İPTAL / TAMAM
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("İPTAL", color = Color(0xFF9AC2FF), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onConfirm(currentHex, opacity) }) {
                        Text("TAMAM", color = Color(0xFF6BB5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
