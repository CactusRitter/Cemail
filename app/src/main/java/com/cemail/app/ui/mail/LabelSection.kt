package com.cemail.app.ui.mail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cemail.app.R
import com.cemail.app.data.mail.LabelNode
import com.cemail.app.data.mail.buildLabelTree
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.icons.CemailIcons

/** The user's Gmail labels as folders in the drawer; folders with sub-folders can be expanded and collapsed. */
@Composable
fun LabelSection(state: MailUiState, onPick: (String) -> Unit, onToggle: (String) -> Unit) {
    val tree = remember(state.labels) { buildLabelTree(state.labels) }
    if (tree.isEmpty()) return
    Text(
        stringResource(R.string.labels_title),
        Modifier.padding(start = 12.dp, top = 12.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    tree.forEach { LabelRow(it, 0, state, onPick, onToggle) }
}

@Composable
private fun LabelRow(node: LabelNode, depth: Int, state: MailUiState, onPick: (String) -> Unit, onToggle: (String) -> Unit) {
    val expanded = node.fullName in state.expanded
    val selected = node.id != null && node.id == state.labelId
    val unread = node.id?.let { state.unreadByLabel[it] } ?: 0
    Row(
        Modifier
            .padding(start = (depth * 16).dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(50), if (selected) GlassKind.Pinned else GlassKind.Card)
            .clickable(role = Role.Button) { if (node.id != null) onPick(node.id) else onToggle(node.fullName) }
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            CemailIcons.Folder, null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            node.name,
            Modifier.weight(1f).padding(vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (unread > 0) Text(unread.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (node.children.isNotEmpty()) {
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                stringResource(if (expanded) R.string.label_collapse else R.string.label_expand),
                Modifier.clickable(role = Role.Button) { onToggle(node.fullName) }.padding(12.dp),
            )
        } else {
            Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.padding(12.dp), tint = androidx.compose.ui.graphics.Color.Transparent)
        }
    }
    if (expanded) node.children.forEach { LabelRow(it, depth + 1, state, onPick, onToggle) }
}
