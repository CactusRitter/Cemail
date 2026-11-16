package com.cemail.app.ui.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cemail.app.R
import com.cemail.app.data.mail.Label
import com.cemail.app.data.mail.LabelNode
import com.cemail.app.data.mail.Mail
import com.cemail.app.data.mail.buildLabelTree
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.LocalListOnRight
import com.cemail.app.ui.glass.floatingCorner
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.glass.popIn
import com.cemail.app.ui.icons.CemailIcons
import com.cemail.app.ui.theme.LocalSkin

/**
 * The user's labels with a check for each one the messages have; a tap puts it on or takes it off.
 *
 * Labels belong to one account (only Gmail has them), so [labelsByAccount] holds the labels of each account among
 * [mails], and a label only touches the messages of its own account. With messages of several accounts, each
 * account's labels come under its name. One message ([keepOpen]): several labels can be changed in a row. Several
 * messages (the selection in the list): the card drops from the top bar and closes after one change.
 */
@Composable
internal fun LabelsPopup(
    mails: List<Mail>,
    labelsByAccount: Map<String, List<Label>>,
    vm: MailViewModel,
    onDismiss: () -> Unit,
    keepOpen: Boolean = true,
    fromTop: Boolean = false,
) {
    val accounts = labelsByAccount.filterValues { it.isNotEmpty() }
    val origin = TransformOrigin(if (LocalListOnRight.current) 0f else 1f, if (fromTop) 0f else 1f)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(if (fromTop) Alignment.TopEnd else floatingCorner())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(
                    top = if (fromTop) 80.dp else 0.dp,
                    bottom = if (fromTop) 16.dp else 156.dp,
                    end = 16.dp,
                    start = 16.dp,
                )
                .popIn(initialScale = 0.3f, origin = origin)
                .widthIn(max = 320.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .glass(RoundedCornerShape(28.dp), GlassKind.Menu, elevation = 12.dp)
                .padding(12.dp),
        ) {
            Text(
                stringResource(R.string.labels_title),
                Modifier.padding(start = 8.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (accounts.isEmpty()) {
                Text(stringResource(R.string.labels_none), Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                accounts.forEach { (accountId, labels) ->
                    val own = mails.filter { it.accountId == accountId }
                    val ids = own.map { it.id }.toSet()
                    if (accounts.size > 1) {
                        Text(
                            accountId,
                            Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Like the folders in the menu: "Banks/La Caixa" is "La Caixa" under "Banks", never the name with "/".
                    LabelChoices(buildLabelTree(labels), 0, own) { id, on ->
                        vm.setLabel(ids, id, on)
                        if (!keepOpen) {
                            vm.clearSelection()
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

/** The label tree, sub-labels indented under their parent; a parent that is not a label itself cannot be ticked. */
@Composable
private fun LabelChoices(nodes: List<LabelNode>, depth: Int, mails: List<Mail>, onSet: (id: String, on: Boolean) -> Unit) {
    nodes.forEach { node ->
        val id = node.id
        // With several messages the check means "all of them have it"; a tap then puts it on all.
        val on = id != null && mails.isNotEmpty() && mails.all { id in it.labelIds }
        LabelChoice(node.name, on, depth, enabled = id != null) { if (id != null) onSet(id, !on) }
        LabelChoices(node.children, depth + 1, mails, onSet)
    }
}

@Composable
private fun LabelChoice(name: String, on: Boolean, depth: Int, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.padding(start = (depth * 16).dp).fillMaxWidth().clip(LocalSkin.current.shape(RoundedCornerShape(16.dp)))
            .toggleable(value = on, enabled = enabled, role = Role.Checkbox) { onToggle() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            CemailIcons.Folder, null,
            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            name,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (on) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}
