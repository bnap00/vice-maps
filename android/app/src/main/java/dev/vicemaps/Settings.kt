package dev.vicemaps

import android.content.Context
import androidx.core.content.edit
import java.util.Locale

enum class Units(val valhallaUnits: String) {
  KILOMETERS("kilometers"),
  MILES("miles"),
}

enum class MapLook {
  AUTO,
  DAY,
  NIGHT;

  /** Resolves to a style URL; AUTO follows the phone's (or car's) dark mode. */
  fun styleUrl(systemDark: Boolean): String =
      when (this) {
        DAY -> AppModule.STYLE_DAY
        NIGHT -> AppModule.STYLE_NIGHT
        AUTO -> if (systemDark) AppModule.STYLE_NIGHT else AppModule.STYLE_DAY
      }
}

/** Tiny SharedPreferences wrapper for the few settings the app has. */
object Settings {
  private const val FILE = "vice_settings"

  private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

  fun units(context: Context): Units =
      prefs(context).getString("units", null)?.let { runCatching { Units.valueOf(it) }.getOrNull() }
          ?: if (Locale.getDefault().country in setOf("US", "GB", "LR", "MM")) Units.MILES
          else Units.KILOMETERS

  fun setUnits(context: Context, units: Units) = prefs(context).edit { putString("units", units.name) }

  fun mapLook(context: Context): MapLook =
      prefs(context).getString("look", null)?.let { runCatching { MapLook.valueOf(it) }.getOrNull() }
          ?: MapLook.AUTO

  fun setMapLook(context: Context, look: MapLook) =
      prefs(context).edit { putString("look", look.name) }
}
