package app.ren.designsystem

import androidx.compose.ui.graphics.Color
import java.time.LocalTime

/** Four phases of the day; surfaces warm and dim as it goes on. [DAY] is the baseline palette. */
enum class CircadianPhase {
    DAWN, DAY, DUSK, NIGHT;

    companion object {
        /** 05:00-09:00 dawn, 09:00-17:00 day, 17:00-21:00 dusk, otherwise night. */
        fun at(hour: Int): CircadianPhase = when (hour) {
            in 5..8 -> DAWN
            in 9..16 -> DAY
            in 17..20 -> DUSK
            else -> NIGHT
        }

        fun now(): CircadianPhase = at(LocalTime.now().hour)
    }
}

/** The page, grouped-row and raised-surface fills for one phase. */
data class CircadianSurfaces(val canvas: Color, val surface: Color, val group: Color)

object LumeColors {
    /** Phase-adjusted surfaces. Light mode warms toward paper at dusk; dark mode deepens toward ink at night. */
    fun surfaces(phase: CircadianPhase, dark: Boolean): CircadianSurfaces = if (dark) {
        when (phase) {
            CircadianPhase.DAWN -> CircadianSurfaces(Color(0xFF1C1C1E), Color(0xFF2C2C2E), Color(0xFF252527))
            CircadianPhase.DAY -> CircadianSurfaces(Color(0xFF1C1C1E), Color(0xFF2C2C2E), Color(0xFF252527))
            CircadianPhase.DUSK -> CircadianSurfaces(Color(0xFF1C1B1B), Color(0xFF2B2A29), Color(0xFF242322))
            CircadianPhase.NIGHT -> CircadianSurfaces(Color(0xFF141416), Color(0xFF232325), Color(0xFF1C1C1E))
        }
    } else {
        when (phase) {
            CircadianPhase.DAWN -> CircadianSurfaces(Color(0xFFFFFDFA), Color(0xFFFFFFFF), Color(0xFFF5F3F2))
            CircadianPhase.DAY -> CircadianSurfaces(Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFF4F4F6))
            CircadianPhase.DUSK -> CircadianSurfaces(Color(0xFFFFFBF5), Color(0xFFFFFDF9), Color(0xFFF6F1EA))
            CircadianPhase.NIGHT -> CircadianSurfaces(Color(0xFFFAF6F0), Color(0xFFFCF9F4), Color(0xFFF0EAE2))
        }
    }
}
