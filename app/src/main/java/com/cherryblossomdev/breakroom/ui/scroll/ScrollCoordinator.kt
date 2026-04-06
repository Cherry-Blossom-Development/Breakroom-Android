package com.cherryblossomdev.breakroom.ui.scroll

/**
 * Shared state for coordinating scroll priority between the outer page LazyColumn
 * and inner widget scroll views. Mirrors the iPhone ScrollCoordinator design.
 *
 * Two rules:
 * 1. Touch location determines scroll target (natural Compose behavior — no code needed)
 * 2. Outer fast fling: inner drags are blocked until the fling fully settles
 * 3. Edge bubble-up: when inner scroll hits its edge, remaining scroll passes to the outer
 *    page (natural Compose behavior — no code needed, just don't consume in onPostScroll)
 *
 * How it works:
 * - outerTrackingConnection (on the Box wrapping the outer LazyColumn in HomeScreen) watches
 *   for Fling source events from the outer page. It sets outerIsFlinging and schedules a
 *   300ms clear job so the flag doesn't linger.
 * - innerScrollConnection (on each widget's content Box in BreakroomWidget):
 *   onPreScroll: if outerIsFlinging and source is Drag, consume the drag so the inner widget
 *   doesn't move while the page is in flight.
 * - innerIsDispatching is set/cleared on every inner pre/post scroll cycle so the outer tracking
 *   connection can ignore events that originated from inner widgets rather than the page itself.
 */
class ScrollCoordinator {
    /** True while the outer LazyColumn (page) is in a fast fling. */
    @Volatile var outerIsFlinging: Boolean = false

    /**
     * True synchronously during an inner widget scroll's pre→post cycle.
     * Used by the outer tracking connection to skip inner-originated scroll events.
     */
    @Volatile var innerIsDispatching: Boolean = false
}
