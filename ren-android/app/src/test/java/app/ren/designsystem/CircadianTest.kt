package app.ren.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CircadianTest {
    @Test fun hoursMapToFourPhases() {
        assertEquals(CircadianPhase.NIGHT, CircadianPhase.at(0))
        assertEquals(CircadianPhase.NIGHT, CircadianPhase.at(4))
        assertEquals(CircadianPhase.DAWN, CircadianPhase.at(5))
        assertEquals(CircadianPhase.DAWN, CircadianPhase.at(8))
        assertEquals(CircadianPhase.DAY, CircadianPhase.at(9))
        assertEquals(CircadianPhase.DAY, CircadianPhase.at(16))
        assertEquals(CircadianPhase.DUSK, CircadianPhase.at(17))
        assertEquals(CircadianPhase.DUSK, CircadianPhase.at(20))
        assertEquals(CircadianPhase.NIGHT, CircadianPhase.at(21))
        assertEquals(CircadianPhase.NIGHT, CircadianPhase.at(23))
    }

    @Test fun dayKeepsTheBaselineAndNightDiffers() {
        assertEquals(Color(0xFFFFFFFF), LumeColors.surfaces(CircadianPhase.DAY, dark = false).canvas)
        assertEquals(Color(0xFF1C1C1E), LumeColors.surfaces(CircadianPhase.DAY, dark = true).canvas)
        assertNotEquals(LumeColors.surfaces(CircadianPhase.DAY, false), LumeColors.surfaces(CircadianPhase.NIGHT, false))
        assertNotEquals(LumeColors.surfaces(CircadianPhase.DAY, true), LumeColors.surfaces(CircadianPhase.NIGHT, true))
    }
}
