package de.taschenwerk.app.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.taschenwerk.app.Nav
import de.taschenwerk.app.ui.LocalPal
import de.taschenwerk.app.ui.num
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Lauscht auf einen Sensor, solange der Bildschirm offen ist. Gibt false zurück, wenn es ihn nicht gibt. */
@Composable
private fun rememberSensor(vararg types: Int, onValues: (Int, FloatArray) -> Unit): Boolean {
    val ctx = LocalContext.current
    val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensors = remember { types.mapNotNull { sm.getDefaultSensor(it) } }
    DisposableEffect(Unit) {
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) = onValues(e.sensor.type, e.values)
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        // Nur den ersten verfügbaren Sensor der Liste verwenden
        sensors.firstOrNull()?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(l) }
    }
    return sensors.isNotEmpty()
}

private val DIRS = listOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")

/** Kompass: Rose dreht sich, die Marke oben zeigt die Blickrichtung. */
@Composable
fun CompassScreen(nav: Nav) {
    val p = LocalPal.current
    BackHandler { nav.overlay = null }
    var az by remember { mutableFloatStateOf(0f) }
    val rot = remember { FloatArray(9) }
    val ori = remember { FloatArray(3) }
    val has = rememberSensor(Sensor.TYPE_ROTATION_VECTOR) { _, v ->
        SensorManager.getRotationMatrixFromVector(rot, v)
        SensorManager.getOrientation(rot, ori)
        val target = ((Math.toDegrees(ori[0].toDouble()) + 360.0) % 360.0).toFloat()
        // sanft nachführen, auch über 0°/360° hinweg
        var d = target - az
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        az = (az + d * 0.2f + 360f) % 360f
    }
    val tm = rememberTextMeasurer()
    Column(Modifier.fillMaxSize()) {
        SubHeader("Kompass", { nav.overlay = null })
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!has) {
                Text("Dieses Handy hat keinen Kompass-Sensor.", color = p.sub, fontSize = 15.sp)
                return@Column
            }
            val deg = az.roundToInt() % 360
            Text("$deg°", color = p.ink, style = num(64.sp, FontWeight.Bold))
            Text(DIRS[((deg + 22) % 360) / 45], color = p.sub, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(28.dp))
            Canvas(Modifier.size(280.dp)) {
                val r = size.minDimension / 2
                val c = Offset(size.width / 2, size.height / 2)
                // feste Marke oben
                val mark = Path().apply {
                    moveTo(c.x, c.y - r - 2f)
                    lineTo(c.x - 14f, c.y - r + 22f)
                    lineTo(c.x + 14f, c.y - r + 22f)
                    close()
                }
                drawPath(mark, p.accent)
                drawCircle(p.line, radius = r - 30f, center = c, style = Stroke(2f))
                rotate(-az, c) {
                    for (i in 0 until 72) {
                        val a = Math.toRadians(i * 5.0)
                        val long = i % 6 == 0
                        val r1 = r - 30f
                        val r2 = r1 - if (long) 26f else 12f
                        drawLine(
                            if (long) p.ink else p.muted,
                            Offset(c.x + (r1 * sin(a)).toFloat(), c.y - (r1 * cos(a)).toFloat()),
                            Offset(c.x + (r2 * sin(a)).toFloat(), c.y - (r2 * cos(a)).toFloat()),
                            strokeWidth = if (long) 4f else 2f
                        )
                    }
                    listOf("N" to 0.0, "O" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (t, d) ->
                        val a = Math.toRadians(d)
                        val rr = r - 92f
                        val lay = tm.measure(
                            t, TextStyle(color = if (t == "N") p.accent else p.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        )
                        val x = c.x + (rr * sin(a)).toFloat() - lay.size.width / 2
                        val y = c.y - (rr * cos(a)).toFloat() - lay.size.height / 2
                        drawText(lay, topLeft = Offset(x, y))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Handy flach halten. Zeigt die Richtung grob falsch, eine Acht in die Luft malen.", color = p.sub, fontSize = 13.sp)
        }
    }
}

/** Wasserwaage: flach eine Blase, hochkant der Neigungswinkel der Kante. */
@Composable
fun LevelScreen(nav: Nav) {
    val p = LocalPal.current
    BackHandler { nav.overlay = null }
    var gx by remember { mutableFloatStateOf(0f) }
    var gy by remember { mutableFloatStateOf(0f) }
    var gz by remember { mutableFloatStateOf(9.81f) }
    val has = rememberSensor(Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER) { _, v ->
        gx = gx * 0.8f + v[0] * 0.2f
        gy = gy * 0.8f + v[1] * 0.2f
        gz = gz * 0.8f + v[2] * 0.2f
    }
    Column(Modifier.fillMaxSize()) {
        SubHeader("Wasserwaage", { nav.overlay = null })
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!has) {
                Text("Dieses Handy hat keinen Lage-Sensor.", color = p.sub, fontSize = 15.sp)
                return@Column
            }
            val flat = abs(gz) > 6.5f
            if (flat) {
                val tiltX = Math.toDegrees(atan2(gx.toDouble(), gz.toDouble())).toFloat()
                val tiltY = Math.toDegrees(atan2(gy.toDouble(), gz.toDouble())).toFloat()
                val level = abs(tiltX) < 1f && abs(tiltY) < 1f
                val total = sqrt(tiltX * tiltX + tiltY * tiltY)
                Text(
                    "${fmtDeg(total)}°", color = if (level) p.accent else p.ink, style = num(64.sp, FontWeight.Bold)
                )
                Text(if (level) "Waagerecht" else "Neigung", color = p.sub, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(28.dp))
                Canvas(Modifier.size(280.dp)) {
                    val r = size.minDimension / 2
                    val c = Offset(size.width / 2, size.height / 2)
                    drawCircle(p.track, radius = r, center = c)
                    drawCircle(p.line, radius = r * 0.33f, center = c, style = Stroke(3f))
                    drawLine(p.line, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 2f)
                    drawLine(p.line, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = 2f)
                    // Blase wandert zur höheren Seite – max. 20° bis an den Rand
                    val k = (r - 44f) / 20f
                    val bx = (-tiltX).coerceIn(-20f, 20f) * k
                    val by = (tiltY).coerceIn(-20f, 20f) * k
                    drawCircle(if (level) p.accent else p.fill, radius = 40f, center = Offset(c.x + bx, c.y + by))
                }
                Spacer(Modifier.height(20.dp))
                Text("X ${fmtDeg(abs(tiltX))}°  ·  Y ${fmtDeg(abs(tiltY))}°", color = p.sub, style = num(15.sp, FontWeight.Normal))
            } else {
                // Hochkant: Winkel der Handykante zur Waagerechten
                var a = Math.toDegrees(atan2(gx.toDouble(), gy.toDouble())).toFloat()
                if (a > 45f) a -= 90f
                if (a < -45f) a += 90f
                val level = abs(a) < 0.5f
                Text("${fmtDeg(abs(a))}°", color = if (level) p.accent else p.ink, style = num(64.sp, FontWeight.Bold))
                Text(if (level) "Gerade" else "Kante neigen", color = p.sub, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(28.dp))
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    val c = Offset(size.width / 2, size.height / 2)
                    drawLine(p.line, Offset(0f, c.y), Offset(size.width, c.y), strokeWidth = 3f)
                    rotate(a, c) {
                        drawLine(if (level) p.accent else p.ink, Offset(c.x - size.width * 0.4f, c.y), Offset(c.x + size.width * 0.4f, c.y), strokeWidth = 8f)
                    }
                }
                Text("Handy mit der langen oder kurzen Kante anlegen.", color = p.sub, fontSize = 13.sp)
            }
        }
    }
}

private fun fmtDeg(v: Float): String = String.format(java.util.Locale.GERMAN, "%.1f", v)
