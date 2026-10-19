package de.taschenwerk.app.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.taschenwerk.app.data.Workout
import de.taschenwerk.app.ui.LocalPal

/** Anleitung zu einem Workout: Titel + Abschnitte (Überschrift, Text). */
data class Guide(val title: String, val sections: List<Pair<String, String>>)

private val apneaCommon = listOf(
    "Sicherheit zuerst" to
        "• Nur im Trockenen üben – im Liegen oder bequem sitzend. Niemals im Wasser, in der Badewanne oder unter der Dusche.\n" +
        "• Niemals beim Autofahren, Gehen oder Stehen.\n" +
        "• Am besten mit jemandem in der Nähe, der weiß, was du machst.\n" +
        "• Nicht hyperventilieren: vor dem Halten ruhig und langsam atmen, nicht schnell und tief pumpen.\n" +
        "• Abbrechen bei Schwindel, Kribbeln, Sehstörungen oder wenn dir unwohl ist.\n" +
        "• Nicht direkt nach dem Essen.",
    "Ablauf" to
        "• „Atmen“: entspannt und ruhig atmen, Ausatmen etwas länger als Einatmen, Körper locker lassen.\n" +
        "• „Luft anhalten“: am Ende ein normales, tiefes (nicht maximal gepresstes) Einatmen, dann halten. Gesicht, Schultern und Bauch entspannt.\n" +
        "• Nach jedem Halten: 2–3 kräftige Erholungsatemzüge (kurz ausatmen, schnell tief einatmen, kurz halten, ausatmen).\n" +
        "• Zwerchfellkontraktionen gegen Ende sind normal – ruhig bleiben, aber nicht mit Gewalt durchziehen.\n" +
        "• Nach der Tabelle ein paar Minuten liegen bleiben und normal atmen.",
    "Wie oft" to
        "• Höchstens eine Tabelle pro Tag, CO2 und O2 nicht am selben Tag.\n" +
        "• 2–4 Tabellen pro Woche reichen.\n" +
        "• Maximal 8 Runden pro Tabelle.",
    "Neue Tabelle berechnen" to
        "Zuerst die Bestzeit (PB) neu messen: nach ein paar lockeren Aufwärm-Haltezügen einmal maximal, aber entspannt halten. Etwa alle 6–8 Wochen neu testen.\n\n" +
        "CO2-Tabelle:\n" +
        "• Halten: immer 50 % der Bestzeit.\n" +
        "• Atmen: beginnt bei 2:00 und wird jede Runde 15 s kürzer, nicht unter 0:30 (geübt bis 0:15).\n" +
        "• Beispiel PB 2:30 → Halten 1:15 · Atmen 2:00, 1:45, 1:30, 1:15, 1:00, 0:45, 0:30, 0:30.\n\n" +
        "O2-Tabelle:\n" +
        "• Atmen: immer 2:00.\n" +
        "• Halten: beginnt bei etwa einem Drittel der Bestzeit und steigt in 10–15-s-Schritten bis höchstens 80 % der Bestzeit.\n" +
        "• Beispiel PB 2:30 (80 % = 2:00) → Halten 0:50, 1:00, 1:10, 1:20, 1:30, 1:40, 1:50, 2:00.\n\n" +
        "Die Zeiten trägst du im Workout-Editor ein (Tabelle öffnen, Sekunden mit − / + anpassen)."
)

fun guideFor(w: Workout): Guide = when (w.id) {
    "apnea_co2" -> Guide(
        "Apnoe CO2-Tabelle",
        listOf(
            "Worum es geht" to
                "Die CO2-Tabelle trainiert die Toleranz gegen den Atemreiz (steigendes CO2). Das Halten bleibt gleich, die Atempausen werden kürzer – dadurch wird es von Runde zu Runde härter.\n\n" +
                "Deine Tabelle (Bestzeit 2:00): Halten 1:00 · Atmen 2:00 → 1:45 → 1:30 → 1:15 → 1:00 → 0:45 → 0:30 → 0:30."
        ) + apneaCommon
    )
    "apnea_o2" -> Guide(
        "Apnoe O2-Tabelle",
        listOf(
            "Worum es geht" to
                "Die O2-Tabelle trainiert den Umgang mit sinkendem Sauerstoff. Die Atempausen bleiben gleich, das Halten wird länger.\n\n" +
                "Deine Tabelle (Bestzeit 2:00): Atmen immer 2:00 · Halten 0:40 → 0:50 → 1:00 → 1:10 → 1:20 → 1:30 → 1:35 → 1:35 (80 % = 1:36)."
        ) + apneaCommon
    )
    else -> Guide(
        "Anleitung: ${w.name}",
        listOf(
            "Vorher" to
                "• 5 Minuten aufwärmen: Arme kreisen, Hüfte kreisen, lockeres Hampeln oder Gehen auf der Stelle.\n" +
                "• Etwas trinken, nicht mit vollem Magen trainieren.",
            "Während der Übungen" to
                "• Saubere Technik vor Tempo. Lieber weniger Wiederholungen, dafür richtig.\n" +
                "• Bei der Anstrengung ausatmen, beim Nachlassen einatmen – nie die Luft anhalten.\n" +
                "• Liegestütze: Körper gerade wie ein Brett, Ellbogen etwa 45° zum Körper.\n" +
                "• Crunches: Kinn nicht auf die Brust ziehen, aus dem Bauch heraus hochrollen, unterer Rücken bleibt am Boden.\n" +
                "• Squats: Füße schulterbreit, Knie zeigen in Richtung Zehen, Gewicht auf den Fersen, Rücken gerade.",
            "In den Pausen" to
                "• Nicht hinsetzen oder hinlegen – locker stehen oder langsam gehen.\n" +
                "• Ruhig und tief durch die Nase einatmen, langsam durch den Mund ausatmen.\n" +
                "• Arme und Beine ausschütteln.",
            "Danach" to
                "• 3–5 Minuten locker gehen, bis sich der Puls beruhigt.\n" +
                "• Dann dehnen: Brust, Hüftbeuger, Oberschenkel, Waden – je 20–30 Sekunden.\n" +
                "• Erst danach hinlegen, tief durchatmen, trinken.",
            "Abbrechen, wenn …" to
                "… Schmerzen in Brust oder Gelenken auftreten, dir schwindlig oder übel wird."
        )
    )
}

@Composable
fun GuideDialog(w: Workout, onClose: () -> Unit) {
    val p = LocalPal.current
    val g = guideFor(w)
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = p.card,
        title = { Text(g.title, color = p.ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                g.sections.forEachIndexed { i, (h, body) ->
                    if (i > 0) Spacer(Modifier.height(14.dp))
                    Text(h, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(body, color = p.sub, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Verstanden", color = p.ink, fontWeight = FontWeight.SemiBold) } }
    )
}
