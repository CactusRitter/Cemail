package com.cemail.app.data

/** What swiping a message in the list can do. The same action may be chosen for both sides. */
enum class SwipeAction {
    /** Archive; from Archive, Trash or Spam it moves the message back to the Inbox instead. */
    ARCHIVE,
    TRASH,
    TOGGLE_READ,
    SPAM,
    STAR,
    SNOOZE,
    /** Nothing: that direction does not swipe at all. */
    NONE,
}

/** How far a message has to be dragged (as a share of the screen width) before letting go does the action. */
enum class SwipeDistance(val fraction: Float) { SHORT(0.3f), NORMAL(0.45f), LONG(0.6f) }

/** The gesture settings, as the message list uses them. [right] is a swipe from left to right. */
data class SwipeConfig(
    val right: SwipeAction = SwipeAction.ARCHIVE,
    val left: SwipeAction = SwipeAction.TRASH,
    val distance: SwipeDistance = SwipeDistance.NORMAL,
)
