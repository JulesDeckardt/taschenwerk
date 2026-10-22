package de.taschenwerk.app.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import de.taschenwerk.app.R

/** Wählbare App-Symbole. Jedes ist ein activity-alias im Manifest. */
enum class AppIcon(val alias: String, val label: String, val preview: Int) {
    // Alias-Namen bleiben aus früheren Versionen, damit nach dem Update immer ein Symbol aktiv ist.
    NACHT("IconNacht", "Nacht", R.drawable.icon_prev_nacht),
    TAG("IconNeon", "Tag", R.drawable.icon_prev_tag),
    BEIGE("IconBauhaus", "Beige", R.drawable.icon_prev_beige),
    MONO("IconSwInv", "Mono", R.drawable.icon_prev_mono),
    MONO_HELL("IconSw", "Mono hell", R.drawable.icon_prev_monoinv);

    fun component(ctx: Context) = ComponentName(ctx.packageName, "de.taschenwerk.app.$alias")

    companion object {
        fun current(ctx: Context): AppIcon {
            val pm = ctx.packageManager
            entries.forEach {
                if (pm.getComponentEnabledSetting(it.component(ctx)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return it
            }
            return NACHT
        }

        /** Frühere Symbole (P, Pastell) gibt es nicht mehr: wer eins davon gewählt hatte, bekommt „Nacht“. */
        fun migrate(ctx: Context) {
            val pm = ctx.packageManager
            val legacy = listOf("IconP", "IconPastell").map { ComponentName(ctx.packageName, "de.taschenwerk.app.$it") }
            if (legacy.any { runCatching { pm.getComponentEnabledSetting(it) }.getOrNull() == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }) {
                apply(ctx, NACHT)
                legacy.forEach {
                    runCatching {
                        pm.setComponentEnabledSetting(it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                    }
                }
            }
        }

        /** Erst das neue Symbol aktivieren, dann die anderen abschalten, damit nie keins aktiv ist. */
        fun apply(ctx: Context, icon: AppIcon) {
            val pm = ctx.packageManager
            pm.setComponentEnabledSetting(
                icon.component(ctx),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(
                    it.component(ctx),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
