package com.cherryblossomdev.breakroom.ui.scroll

/**
 * Shared state for coordinating scroll priority between the outer page LazyColumn
 * and inner widget scroll views. Mirrors the iPhone ScrollCoordinator design.
 *
 * Three rules:
 * 1. Touch location determines scroll target (natural Compose behavior — no code needed)
 * 2. Outer fast fling: inner drags are blocked until the fling fully settles
 * 3. Edge blocking: inner scroll stops at its edge without passing through to the outer page
 *
 * How it works:
 * - outerTrackingConnection (on the Box wrapping the outer LazyColumn in HomeScreen) watches
 *   for Fling source events from the outer page. It sets outerIsFlinging and schedules a
 *   300ms clear job so the flag doesn't linger.
 * - innerScrollConnection (on each widget's content Box in BreakroomWidget) does two things:
 *   (a) onPreScroll: if outerIsFlinging, consume the drag so the inner widget doesn't scroll
 *   (b) onPostScroll: consume all remaining scroll — prevents edge pass-through to the outer page
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
