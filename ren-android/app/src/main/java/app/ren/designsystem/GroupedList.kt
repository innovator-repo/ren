package app.ren.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ren.core.designsystem.components.Hairline
import app.ren.core.designsystem.components.rowHighlight
import app.ren.core.designsystem.theme.Ren
import app.ren.core.designsystem.theme.RenShapes

/**
 * Rows separated by inset hairlines. By default they sit in a soft group; [plain] lays them
 * straight on the page, like Sigil's lists.
 */
@Composable
fun GroupedList(modifier: Modifier = Modifier, plain: Boolean = false, build: GroupedListBuilder.() -> Unit) {
    val rows = GroupedListBuilder().apply(build).rows
    Column(
        modifier
            .fillMaxWidth()
            .then(if (plain) Modifier else Modifier.clip(RoundedCornerShape(RenShapes.card)).background(Ren.colors.group)),
    ) {
        rows.forEachIndexed { i, row ->
            if (i > 0) Hairline(Modifier.padding(start = row.inset))
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (row.onClick != null) Modifier.rowHighlight(row.enabled, row.onClick) else Modifier)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = if (plain) 4.dp else 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = row.content,
            )
        }
    }
}

class GroupedListBuilder {
    internal class RowSpec(val inset: Dp, val enabled: Boolean, val onClick: (() -> Unit)?, val content: @Composable RowScope.() -> Unit)

    internal val rows = mutableListOf<RowSpec>()

    fun row(inset: Dp = 14.dp, enabled: Boolean = true, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
        rows += RowSpec(inset, enabled, onClick, content)
    }
}

