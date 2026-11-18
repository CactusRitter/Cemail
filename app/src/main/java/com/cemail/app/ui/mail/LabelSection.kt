package com.cemail.app.ui.mail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.key
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
import com.ce.kit.ui.glass.GlassKind
import com.ce.kit.ui.glass.glass
import com.ce.kit.ui.glass.popIn
import com.ce.kit.ui.glass.pressScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import com.cemail.app.ui.icons.CemailIcons

/** The user's Gmail labels as folders in the drawer; folders with sub-folders can be expanded and collapsed. */
@Composable
fun LabelSection(
    state: MailUiState,
    onPick: (String) -> Unit,
    onToggle: (String) -> Unit,
    revealed: Boolean = true,
    delayStart: Int = 0,
) {
    val tree = remember(state.labels) { buildLabelTree(state.labels) }
    if (tree.isEmpty()) return
    // Like the folders above: the labels pop in one after the other when the menu opens, when another account's
    // labels arrive (a new tree, so everything is drawn anew) and, for sub-labels, when a label is expanded.
    key(tree) {
        Text(
            stringResource(R.string.labels_title),
            Modifier.popIn(revealed, delayMillis = delayStart, initialScale = 0.8f).padding(start = 12.dp, top = 12.dp, bottom = 2.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        tree.forEachIndexed { i, node -> LabelRow(node, 0, state, onPick, onToggle, revealed, stagger(delayStart, i + 1)) }
    }
}

/** The delay of the [n]th row after [start]: 22 ms apart like the folders, but never a long wait on a long list. */
private fun stagger(start: Int, n: Int) = minOf(start + n * 22, start + 330)

@Composable
private fun LabelRow(
    node: LabelNode,
    depth: Int,
    state: MailUiState,
    onPick: (String) -> Unit,
    onToggle: (String) -> Unit,
    revealed: Boolean,
    delayMillis: Int,
) {
    val expanded = node.fullName in state.expanded
    val selected = node.id != null && node.id == state.labelId
    val unread = node.id?.let { state.unreadByLabel[it] } ?: 0
    // The same row as the folders above (MailApp's DrawerContent): same padding, icon colour, press and count. Only a
    // label with sub-labels adds its small arrow at the end.
    Row(
        Modifier
            .popIn(revealed, delayMillis = delayMillis, initialScale = 0.8f)
            .padding(start = (depth * 16).dp)
            .pressScale(0.96f)
            .fillMaxWidth()
            .glass(RoundedCornerShape(50), if (selected) GlassKind.Pinned else GlassKind.Card)
            .clickable(role = Role.Button) { if (node.id != null) onPick(node.id) else onToggle(node.fullName) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            CemailIcons.Folder, null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            node.name,
            Modifier.weight(1f),
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
                Modifier.clip(CircleShape).clickable(role = Role.Button) { onToggle(node.fullName) },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    // Sub-labels: the room opens and closes smoothly (as Cc and Bcc do in Compose), the rows pop in on opening and fade
    // out on closing.
    // While it opens or closes, the block is drawn cut to its own size, which took away the solid shadow (Brutalist) of
    // the sub-labels on the end side and under the last one. The block holds that room inside it and is laid out as if
    // it did not: the rows stay where they were and their shadow is inside what is cut. The room is 0 in other looks.
    val room = com.ce.kit.ui.theme.LocalSkin.current.hardShadow
    AnimatedVisibility(
        expanded,
        modifier = Modifier.layout { measurable, constraints ->
            val r = room.roundToPx()
            val wide = if (constraints.hasBoundedWidth) constraints.copy(maxWidth = constraints.maxWidth + r) else constraints
            val p = measurable.measure(wide)
            layout((p.width - r).coerceIn(constraints.minWidth, constraints.maxWidth), (p.height - r).coerceAtLeast(0)) {
                p.placeRelative(0, 0)
            }
        },
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(Modifier.padding(end = room, bottom = room), verticalArrangement = Arrangement.spacedBy(4.dp) /* the gap of the menu itself */) {
            node.children.forEachIndexed { i, child -> LabelRow(child, depth + 1, state, onPick, onToggle, revealed, stagger(0, i)) }
        }
    }
}
