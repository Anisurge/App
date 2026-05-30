package to.kuudere.anisuge.screens.settings.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.kuudere.anisuge.data.models.LayoutRow
import to.kuudere.anisuge.data.models.RowId
import to.kuudere.anisuge.i18n.AppStrings
import to.kuudere.anisuge.i18n.LocalAppStrings

@Composable
fun LayoutEditorRow(
    row: LayoutRow,
    index: Int,
    total: Int,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDragEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val title = strings.layoutRowTitle(row.id)
    val visibility = if (row.visible) strings.visibleLabel else strings.hiddenLabel
    var dragY by remember { mutableStateOf(0f) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
            .semantics {
                stateDescription = "$title, ${index + 1} of $total, ${if (row.visible) strings.shownLabel else strings.hiddenLabel}"
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.DragIndicator,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.pointerInput(row.id, index) {
                detectDragGestures(
                    onDrag = { _, amount -> dragY += amount.y },
                    onDragEnd = {
                        onDragEnd(dragY)
                        dragY = 0f
                    },
                    onDragCancel = { dragY = 0f },
                )
            },
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(visibility, color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
        }
        Switch(checked = row.visible, onCheckedChange = onToggle)
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = onMoveUp,
            enabled = index > 0,
            modifier = Modifier.semantics { if (index == 0) disabled() },
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = strings.moveUp, tint = Color.White)
        }
        IconButton(
            onClick = onMoveDown,
            enabled = index < total - 1,
            modifier = Modifier.semantics { if (index == total - 1) disabled() },
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = strings.moveDown, tint = Color.White)
        }
    }
}

fun AppStrings.layoutRowTitle(id: RowId): String = when (id) {
    RowId.CONTINUE_WATCHING -> continueWatchingTitle
    RowId.LATEST_EPISODES -> latestEpisodesTitle
    RowId.TRENDING_WEEK -> trendingWeekTitle
    RowId.NEW_SEASONS -> newSeasonsTitle
    RowId.NEW_ON_APP -> newOnAppTitle
    RowId.RECOMMENDED -> recommendedTitle
    RowId.UPCOMING -> upcomingTitle
    RowId.HIDDEN_GEMS -> hiddenGemsTitle
} ?: id.storageId
