package li.gkd.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun <K> RowScope.MultiSelectionActions(
    selectionState: MultiSelectionState<K>,
    keys: Set<K>,
    enabled: Boolean = true,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedKeys = selectionState.selectedKeys intersect keys
    LaunchedEffect(enabled, selectedKeys.isEmpty()) {
        if (!enabled || selectedKeys.isEmpty()) expanded = false
    }
    PerfIconButton(
        imageVector = Icons.Outlined.SelectAll,
        contentDescription = "全选",
        enabled = enabled && keys.isNotEmpty() && selectedKeys != keys,
        onClick = { selectionState.selectAll(keys) },
    )
    PerfIconButton(
        imageVector = Icons.Outlined.FlipToBack,
        contentDescription = "反选",
        enabled = enabled && keys.isNotEmpty(),
        onClick = { selectionState.invert(keys) },
    )
    Box {
        PerfIconButton(
            imageVector = PerfIcon.MoreVert,
            contentDescription = "更多",
            enabled = enabled && selectedKeys.isNotEmpty(),
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded && enabled && selectedKeys.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            menuContent { expanded = false }
        }
    }
}

@Composable
fun BatchActionMenuItem(
    text: String,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    DropdownMenuItem(
        text = { Text(text) },
        enabled = enabled,
        colors = if (destructive) {
            MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
        } else {
            MenuDefaults.itemColors()
        },
        onClick = {
            onDismiss()
            onClick()
        },
    )
}

@Composable
fun RuleBatchMenuItems(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onUpdate: (Boolean?) -> Unit,
) {
    BatchActionMenuItem("全部启用", onDismiss, { onUpdate(true) }, enabled)
    BatchActionMenuItem("全部关闭", onDismiss, { onUpdate(false) }, enabled)
    BatchActionMenuItem("重置开关", onDismiss, { onUpdate(null) }, enabled)
}
