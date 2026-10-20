package app.ren.ecosystem

import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LumeIpcTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun installKasane() {
        val info = PackageInfo().apply { packageName = LumeIpc.KASANE }
        shadowOf(context.packageManager).installPackage(info)
    }

    @Test fun nothingIsSentWhenKasaneIsAbsent() {
        LumeIpc.logCheck(context, done = true)
        LumeIpc.ecoMetrics(context, EcoMetrics(80, 2000, 10))
        assertTrue(shadowOf(context as android.app.Application).broadcastIntents.isEmpty())
        assertFalse(LumeIpc.kasaneInstalled(context))
    }

    @Test fun logCheckIsExplicitAndPermissionGuarded() {
        installKasane()
        LumeIpc.logCheck(context, done = true)
        val sent = shadowOf(context as android.app.Application).broadcastIntents.single()
        assertEquals("app.kasane.action.LOG_CHECK", sent.action)
        assertEquals("app.kasane", sent.`package`)
        assertEquals("ren", sent.getStringExtra("item"))
        assertTrue(sent.getBooleanExtra("done", false))
    }

    @Test fun ecoMetricsCarriesTheJsonPayload() {
        installKasane()
        LumeIpc.ecoMetrics(context, EcoMetrics(75, 3000, 12))
        val sent = shadowOf(context as android.app.Application).broadcastIntents.single()
        assertEquals("app.kasane.action.ECO_METRICS", sent.action)
        assertEquals("app.kasane", sent.`package`)
        assertEquals("ren", sent.getStringExtra("source"))
        assertEquals("""{"recallAccuracy":75,"meanLatencyMs":3000,"dueCount":12}""", sent.getStringExtra("payload"))
        assertNull(sent.getStringExtra("item"))
    }
}
