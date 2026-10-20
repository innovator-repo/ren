package app.ren.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import app.ren.R
import app.ren.designsystem.TabBarHeight
import app.ren.core.designsystem.components.Txt
import app.ren.core.designsystem.theme.Ren
import app.ren.core.designsystem.theme.RenTheme
import app.ren.feature.matrix.MatrixScreen
import app.ren.feature.path.PathScreen
import app.ren.feature.reader.LibraryScreen
import app.ren.feature.reader.StoryScreen
import app.ren.feature.session.ReviewPhase
import app.ren.feature.session.SessionScreen
import app.ren.feature.today.TodayScreen
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * Renders the Play Store graphics at their exact pixel sizes into app/build/store-graphics:
 * the 512 px icon, the 1024×500 feature graphic and 1080×1920 phone screenshots.
 * Paparazzi's own snapshots are downscaled, so this test writes full-size frames itself.
 * `tools/store/export.sh` then flattens them to opaque RGB and copies them to docs/play-store.
 */
class StoreGraphicsTest {
    private var name = "frame"
    private val out = File("build/store-graphics").apply { mkdirs() }

    // java.awt is hidden from the Android unit-test compile classpath, so frames are handled reflectively.
    private val writer = object : SnapshotHandler {
        override fun newFrameHandler(snapshot: Snapshot, frameCount: Int, fps: Int): SnapshotHandler.FrameHandler {
            val target = File(out, "$name.png")
            return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SnapshotHandler.FrameHandler::class.java)) { _, method, args ->
                if (method.name == "handle") {
                    val image = args!![0]
                    Class.forName("javax.imageio.ImageIO")
                        .getMethod("write", Class.forName("java.awt.image.RenderedImage"), String::class.java, File::class.java)
                        .invoke(null, image, "png", target)
                }
                null
            } as SnapshotHandler.FrameHandler
        }
        override fun close() = Unit
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
        snapshotHandler = writer,
    )

    private val scenes = ScreenshotTest()

    /**
     * Paparazzi downscales any frame larger than 1000 px, so big graphics render as tiles of at most
     * 1000 px a side, each a window onto the full layout; export.sh stitches them back together.
     */
    private fun render(file: String, widthPx: Int, heightPx: Int, dpi: Density, content: @Composable () -> Unit) {
        val cols = (widthPx + 999) / 1000
        val rows = (heightPx + 999) / 1000
        val tileW = widthPx / cols
        val tileH = heightPx / rows
        val scale = dpi.dpiValue / 160f
        for (r in 0 until rows) for (col in 0 until cols) {
            name = if (cols * rows == 1) file else "$file.tile-$r-$col"
            paparazzi.unsafeUpdateConfig(
                deviceConfig = DeviceConfig.PIXEL_5.copy(
                    screenWidth = tileW, screenHeight = tileH, xdpi = dpi.dpiValue, ydpi = dpi.dpiValue, density = dpi,
                    orientation = if (tileW > tileH) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
                ),
            )
            paparazzi.snapshot {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    RenTheme(dark = false, glass = false) {
                        Box(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                                    .offset { IntOffset(-col * tileW, -r * tileH) }
                                    .requiredSize((widthPx / scale).dp, (heightPx / scale).dp),
                            ) { content() }
                        }
                    }
                }
            }
        }
    }

    private val brand = Brush.linearGradient(listOf(Color(0xFFF0574F), Color(0xFFC9302A)))

    @Test fun icon() = render("icon-512", 512, 512, Density.MEDIUM) {
        Box(Modifier.fillMaxSize().background(brand), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().scale(1.2f))
        }
    }

    @Test fun featureGraphic() = render("feature-graphic-1024x500", 1024, 500, Density.MEDIUM) {
        Row(
            Modifier.fillMaxSize().background(brand).padding(horizontal = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(300.dp).scale(1.35f))
            Column(Modifier.padding(start = 40.dp)) {
                Txt("Ren", Ren.type.largeTitle.copy(fontSize = 96.sp, lineHeight = 100.sp), color = Color.White)
                Txt(
                    "Japanese from zero to N3.",
                    Ren.type.title2.copy(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Txt(
                    "Radical-first kanji, real stories, ten minutes a day.",
                    Ren.type.body.copy(fontSize = 22.sp, lineHeight = 30.sp),
                    color = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }

    // ------------------------------------------------------------------ phone screenshots

    private fun phone(file: String, caption: String, line: String, tab: Int?, screen: @Composable () -> Unit) =
        render(file, 1080, 1920, Density.create(420)) { Marketing(caption, line, tab, screen) }

    @Test fun shot1Today() = phone("screenshot-1-today", "Ten calm minutes a day", "Reviews and new cards, planned for you.", 0) {
        TodayScreen(scenes.todayState(), TabBarHeight) {}
    }

    @Test fun shot2PullApart() = phone("screenshot-2-radicals", "Every kanji, pulled apart", "Learn the pieces first, then the picture.", null) {
        SessionScreen(scenes.lessonState(0)) {}
    }

    @Test fun shot3Recall() = phone("screenshot-3-recall", "Type it. Never pick it.", "Answers are drawn or typed, so recall is real.", null) {
        SessionScreen(scenes.reviewState("休日", ReviewPhase.Result(true, "きゅうじつ", app.ren.domain.fsrs.Rating.Good))) {}
    }

    @Test fun shot4Reader() = phone("screenshot-4-reader", "Stories at your level", "Only words you know, plus one new one.", 3) {
        LibraryScreen(scenes.libraryState(), TabBarHeight) {}
    }

    @Test fun shot5Story() = phone("screenshot-5-story", "Tap any word", "Readings, parts and pitch accent in one sheet.", null) {
        StoryScreen(scenes.storyState("konbini", "休日", null), {}, {})
    }

    @Test fun shot6Matrix() = phone("screenshot-6-matrix", "See how kanji connect", "Your knowledge, as a living map.", 2) {
        MatrixScreen(scenes.matrixState(), TabBarHeight) {}
    }

    @Test fun shot7Path() = phone("screenshot-7-path", "A clear path to N3", "Kana, radicals, then kanji that build on each other.", 1) {
        PathScreen(scenes.pathState(), TabBarHeight) {}
    }
}

/** A caption over a framed, slightly scaled phone screen, on a soft page. */
@Composable
private fun Marketing(caption: String, line: String, tab: Int?, screen: @Composable () -> Unit) {
    val c = Ren.colors
    val k = 0.74f
    val w = 393f
    val h = 780f
    Column(
        Modifier.fillMaxSize().background(c.group).padding(top = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Txt(caption, Ren.type.largeTitle.copy(fontSize = 28.sp, lineHeight = 34.sp, textAlign = TextAlign.Center))
        Txt(
            line, Ren.type.body.copy(fontSize = 16.sp, textAlign = TextAlign.Center), color = c.ink2,
            modifier = Modifier.padding(top = 6.dp, start = 32.dp, end = 32.dp),
        )
        Box(
            Modifier.padding(top = 22.dp).size((w * k).dp, (h * k).dp)
                .shadow(24.dp, RoundedCornerShape(30.dp), ambientColor = c.shadow, spotColor = c.shadow)
                .clip(RoundedCornerShape(30.dp)),
        ) {
            Box(
                Modifier.requiredSize(w.dp, h.dp).graphicsLayer { scaleX = k; scaleY = k },
            ) { PhoneFrame(tab) { screen() } }
        }
    }
}
