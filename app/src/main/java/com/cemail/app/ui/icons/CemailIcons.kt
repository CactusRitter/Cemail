package com.cemail.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Extra Material-style icons that material-icons-core does not include (single 24dp paths). */
object CemailIcons {
    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            PathParser().parsePathString(path).toNodes(),
            fill = SolidColor(Color.Black),
        ).build()

    val Archive = icon(
        "Archive",
        "M20.54,5.23l-1.39,-1.68C18.88,3.21 18.47,3 18,3L6,3c-0.47,0 -0.88,0.21 -1.16,0.55L3.46,5.23C3.17,5.57 3,6.02 3,6.5L3,19c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2L21,6.5c0,-0.48 -0.17,-0.93 -0.46,-1.27zM12,17.5L6.5,12L10,12v-2h4v2h3.5L12,17.5zM5.12,5l0.81,-1h12l0.94,1L5.12,5z",
    )
    val Pin = icon(
        "Pin",
        "M16,9V4l1,0c0.55,0 1,-0.45 1,-1v0c0,-0.55 -0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3v0c0,0.55 0.45,1 1,1l1,0v5c0,1.66 -1.34,3 -3,3h0v2h5.97v7l1,1l1,-1v-7H19v-2h0C17.34,12 16,10.66 16,9z",
    )
    val Snooze = icon(
        "Snooze",
        "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z",
    )
    val Inbox = icon(
        "Inbox",
        "M19,3H4.99c-1.11,0 -1.98,0.89 -1.98,2L3,19c0,1.1 0.88,2 1.99,2H19c1.1,0 2,-0.9 2,-2V5c0,-1.11 -0.9,-2 -2,-2zM19,15h-4c0,1.66 -1.35,3 -3,3s-3,-1.34 -3,-3H4.99V5H19v10z",
    )
    val Attachment = icon(
        "Attachment",
        "M16.5,6v11.5c0,2.21 -1.79,4 -4,4s-4,-1.79 -4,-4V5c0,-1.38 1.12,-2.5 2.5,-2.5s2.5,1.12 2.5,2.5v10.5c0,0.55 -0.45,1 -1,1s-1,-0.45 -1,-1V6H10v9.5c0,1.38 1.12,2.5 2.5,2.5s2.5,-1.12 2.5,-2.5V5c0,-2.21 -1.79,-4 -4,-4S7,2.79 7,5v12.5c0,3.04 2.46,5.5 5.5,5.5s5.5,-2.46 5.5,-5.5V6h-1.5z",
    )
    val Reply = icon("Reply", "M10,9V5l-7,7 7,7v-4.1c5,0 8.5,1.6 11,5.1 -1,-5 -4,-10 -11,-11z")
    val ReplyAll = icon(
        "ReplyAll",
        "M7,8V5l-7,7 7,7v-3l-4,-4 4,-4zM13,9V5l-7,7 7,7v-4.1c5,0 8.5,1.6 11,5.1 -1,-5 -4,-10 -11,-11z",
    )
    val Forward = icon("Forward", "M12,8V4l8,8 -8,8v-4H4V8z")
    val StarOutline = icon(
        "StarOutline",
        "M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24zM12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z",
    )
    val Drafts = icon(
        "Drafts",
        "M21.99,8c0,-0.72 -0.37,-1.35 -0.94,-1.7L12,1 2.95,6.3C2.38,6.65 2,7.28 2,8v10c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2l-0.01,-10zM12,13L3.74,7.84 12,3l8.26,4.84L12,13z",
    )
    val Mail = icon(
        "Mail",
        "M20,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM20,8l-8,5 -8,-5V6l8,5 8,-5v2z",
    )
}
