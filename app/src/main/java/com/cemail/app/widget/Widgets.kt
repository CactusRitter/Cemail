package com.cemail.app.widget

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.cemail.app.CemailApplication
import com.cemail.app.LaunchRequest
import com.cemail.app.Launches
import com.cemail.app.R
import com.cemail.app.data.mail.MailBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The widgets cannot blur what is behind them, so they use a translucent panel in the accent's soft colours
// (light and dark), with the same rounded corners as the app.
private val Panel = ColorProvider(day = Color(0xEBF3EEF8), night = Color(0xEB1F2133))
private val Chip = ColorProvider(day = Color(0xCCFFFFFF), night = Color(0x33FFFFFF))
private val Ink = ColorProvider(day = Color(0xFF1B1B1F), night = Color(0xFFE6E1E5))
private val InkSoft = ColorProvider(day = Color(0xFF5B5B66), night = Color(0xFFB4B0BA))
private val Accent = ColorProvider(day = Color(0xFF4A4FD8), night = Color(0xFFA9ADFF))

/** Redraws every Cemail widget (after new mail or a change in the Inbox). */
suspend fun updateWidgets(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    if (manager.getGlanceIds(InboxWidget::class.java).isNotEmpty()) InboxWidget().updateAll(context)
    if (manager.getGlanceIds(ToolsWidget::class.java).isNotEmpty()) ToolsWidget().updateAll(context)
}

/** Widget 1: the newest messages of the Inbox (all accounts), a button to write and the unread count. */
class InboxWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = (context.applicationContext as CemailApplication).database.messages()
        val now = System.currentTimeMillis()
        val rows = withContext(Dispatchers.IO) { dao.recentInbox(now, 15) }
        val unread = withContext(Dispatchers.IO) { dao.unreadInboxCount(now) }
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(Panel).cornerRadius(28.dp).padding(12.dp)) {
                Row(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        GlanceModifier.defaultWeight().clickable(actionStartActivity(Launches.intent(context, LaunchRequest.OpenBox(MailBox.INBOX)))),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(context.getString(R.string.box_inbox), style = TextStyle(color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                        if (unread > 0) {
                            Spacer(GlanceModifier.width(8.dp))
                            Text(unread.toString(), style = TextStyle(color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                    ActionButton(context, R.drawable.ic_action_search, LaunchRequest.Search)
                    Spacer(GlanceModifier.width(8.dp))
                    ActionButton(context, R.drawable.ic_action_compose, LaunchRequest.Compose())
                }
                if (rows.isEmpty()) {
                    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(context.getString(R.string.empty_title), style = TextStyle(color = InkSoft, fontSize = 14.sp))
                    }
                } else {
                    LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(rows, itemId = { it.localId }) { mail ->
                            Row(
                                GlanceModifier.fillMaxWidth().padding(vertical = 5.dp)
                                    .clickable(actionStartActivity(Launches.intent(context, LaunchRequest.OpenMail(mail.accountId, mail.localId)))),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(GlanceModifier.defaultWeight()) {
                                    Text(
                                        mail.senderName,
                                        maxLines = 1,
                                        style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = if (mail.read) FontWeight.Normal else FontWeight.Bold),
                                    )
                                    Text(
                                        mail.subject.ifBlank { context.getString(R.string.no_subject) },
                                        maxLines = 1,
                                        style = TextStyle(color = if (mail.read) InkSoft else Ink, fontSize = 13.sp),
                                    )
                                }
                                Spacer(GlanceModifier.width(8.dp))
                                Text(timeLabel(context, mail.timeMillis), style = TextStyle(color = InkSoft, fontSize = 12.sp))
                            }
                        }
                    }
                }
            }
        }
    }
}

class InboxWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = InboxWidget()
}

/** Widget 2: round buttons to write, open the Inbox, search and open Starred, with the unread count. */
class ToolsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = (context.applicationContext as CemailApplication).database.messages()
        val unread = withContext(Dispatchers.IO) { dao.unreadInboxCount(System.currentTimeMillis()) }
        provideContent {
            Row(
                GlanceModifier.fillMaxSize().background(Panel).cornerRadius(28.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tool(context, R.drawable.ic_action_compose, LaunchRequest.Compose(), GlanceModifier.defaultWeight())
                Tool(context, R.drawable.ic_action_inbox, LaunchRequest.OpenBox(MailBox.INBOX), GlanceModifier.defaultWeight(), badge = unread)
                Tool(context, R.drawable.ic_action_search, LaunchRequest.Search, GlanceModifier.defaultWeight())
                Tool(context, R.drawable.ic_action_star, LaunchRequest.OpenBox(MailBox.STARRED), GlanceModifier.defaultWeight())
            }
        }
    }
}

class ToolsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ToolsWidget()
}

@Composable
private fun Tool(context: Context, icon: Int, request: LaunchRequest, modifier: GlanceModifier, badge: Int = 0) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            GlanceModifier.size(48.dp).background(Chip).cornerRadius(24.dp)
                .clickable(actionStartActivity(Launches.intent(context, request))),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(24.dp))
        }
        if (badge > 0) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                Text(
                    if (badge > 99) "99+" else badge.toString(),
                    modifier = GlanceModifier.padding(end = 2.dp),
                    style = TextStyle(color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun ActionButton(context: Context, icon: Int, request: LaunchRequest) {
    Box(
        GlanceModifier.size(36.dp).background(Chip).cornerRadius(18.dp)
            .clickable(actionStartActivity(Launches.intent(context, request))),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), contentDescription = null, modifier = GlanceModifier.size(20.dp))
    }
}

private fun timeLabel(context: Context, millis: Long): String =
    if (DateUtils.isToday(millis)) {
        DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)
    } else {
        DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }
