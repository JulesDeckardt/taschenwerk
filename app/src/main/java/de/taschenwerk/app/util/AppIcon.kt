package de.taschenwerk.app.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import de.taschenwerk.app.R

/** Wählbare App-Symbole. Jedes ist ein activity-alias im Manifest. */
enum class AppIcon(val alias: String, val label: String, val preview: Int) {
    NEON("IconNeon", "Neon", R.drawable.icon_prev_neon),
    BAUHAUS("IconBauhaus", "Bauhaus", R.drawable.icon_prev_bauhaus),
    P("IconP", "P", R.drawable.icon_prev_p),
    NACHT("IconNacht", "Nacht", R.drawable.icon_prev_nacht),
    PASTELL("IconPastell", "Pastell", R.drawable.icon_prev_pastell),
    SW("IconSw", "Schwarz-Weiß", R.drawable.icon_prev_sw),
    SWINV("IconSwInv", "Invertiert", R.drawable.icon_prev_swinv);

    fun component(ctx: Context) = ComponentName(ctx.packageName, "de.taschenwerk.app.$alias")

    companion object {
        fun current(ctx: Context): AppIcon {
            val pm = ctx.packageManager
            entries.forEach {
                if (pm.getComponentEnabledSetting(it.component(ctx)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return it
            }
            return NEON
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
