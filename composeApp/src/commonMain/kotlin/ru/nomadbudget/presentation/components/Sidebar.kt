package ru.nomadbudget.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class SidebarEntry(
    val title: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

data class SidebarGroup(val title: String?, val entries: List<SidebarEntry>)

@Composable
fun Sidebar(
    collapsed: Boolean,
    onToggle: () -> Unit,
    onCreate: () -> Unit,
    groups: List<SidebarGroup>,
    footer: List<SidebarEntry>,
) {
    val width by animateDpAsState(if (collapsed) COLLAPSED_WIDTH else EXPANDED_WIDTH, label = "sidebar")
    Row(modifier = Modifier.fillMaxHeight()) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(width).fillMaxHeight()) {
            Column(modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp, vertical = 12.dp)) {
                CreateButton(collapsed, onCreate)
                Spacer(Modifier.height(12.dp))
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    groups.forEachIndexed { index, group ->
                        if (index > 0) Spacer(Modifier.height(GROUP_GAP))
                        GroupTitle(group.title, collapsed)
                        group.entries.forEach { SidebarRow(it, collapsed) }
                    }
                }
                footer.forEach { SidebarRow(it, collapsed) }
                SidebarRow(
                    SidebarEntry(if (collapsed) "Развернуть" else "Свернуть", if (collapsed) AppIcons.ChevronRight else AppIcons.ChevronLeft, false, onToggle),
                    collapsed,
                    muted = true,
                )
            }
        }
        VerticalDivider()
    }
}

@Composable
private fun CreateButton(collapsed: Boolean, onCreate: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT + 4.dp)
            .clip(MaterialTheme.shapes.small)
            .background(colors.primary)
            .clickable(onClick = onCreate)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(10.dp),
    ) {
        Icon(AppIcons.Add, contentDescription = "Новая запись", tint = colors.onPrimary, modifier = Modifier.size(ICON_SIZE))
        if (!collapsed) Text("Новая запись", color = colors.onPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun ColumnScope.GroupTitle(title: String?, collapsed: Boolean) {
    when {
        title == null -> Unit
        collapsed -> Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(20.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        else -> Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SidebarRow(entry: SidebarEntry, collapsed: Boolean, muted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val content = when {
        entry.selected -> colors.primary
        muted -> colors.onSurfaceVariant
        else -> colors.onSurface
    }
    val row: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT)
                .clip(MaterialTheme.shapes.small)
                .background(if (entry.selected) colors.primaryContainer else Color.Transparent)
                .clickable(onClick = entry.onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(12.dp),
        ) {
            Icon(entry.icon, contentDescription = entry.title, tint = content, modifier = Modifier.size(ICON_SIZE))
            if (!collapsed) Text(entry.title, color = content, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (collapsed) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Right),
            tooltip = { PlainTooltip { Text(entry.title) } },
            state = rememberTooltipState(),
        ) { row() }
    } else {
        row()
    }
}

private val EXPANDED_WIDTH = 216.dp
private val COLLAPSED_WIDTH = 68.dp
private val ROW_HEIGHT = 36.dp
private val ICON_SIZE = 20.dp
private val GROUP_GAP = 14.dp
