package com.omnicontrol.feature.accessibility

/**
 * Implemented by the overlay. Lets the action engine show the "touching ball" and make our own
 * floating windows click-through / invisible while it touches or screenshots other apps.
 */
interface TouchFeedback {
    /** Animate the ball to (x, y) and play the tap ripple. Suspends until the ball has arrived. */
    suspend fun showTouch(x: Int, y: Int, longPress: Boolean = false)

    /** Show a swipe trail from one point to another. */
    suspend fun showSwipe(fromX: Int, fromY: Int, toX: Int, toY: Int)

    /** true = our windows ignore touches so injected gestures reach the app underneath. */
    fun setPassthrough(enabled: Boolean)

    /** Hide our windows for a moment (screenshots must not contain the assistant itself). */
    suspend fun <T> withOverlaysHidden(block: suspend () -> T): T
}
