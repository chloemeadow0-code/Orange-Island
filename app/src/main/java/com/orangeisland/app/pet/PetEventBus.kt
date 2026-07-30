package com.orangeisland.app.pet

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Process-wide event bus for the desktop pet. Decouples producers (the chat
 * ViewModel emitting a bubble on an assistant reply, the navigation layer, etc.)
 * from the single consumer ([com.orangeisland.app.service.DesktopPetService]),
 * which owns the overlay window and is only alive while the pet is on.
 *
 * Producers always fire-and-forget via [emit]; if no pet is active the event is
 * simply dropped (replay=0, extraBufferCapacity=8, DROP_OLDEST). This is the
 * desired behaviour — a stale "reply finished" bubble from before the user
 * turned the pet on should not surface retroactively.
 *
 * Replay is intentionally 0: when the service (re)starts we don't want it to be
 * flooded by the entire history of events that happened while it was down.
 */
object PetEventBus {

    sealed class Event {
        /** Show a speech bubble next to the pet. [text] is already shortened. */
        data class Bubble(val text: String) : Event()
        /** Force a named expression (e.g. "13_heart"). Falls back to idle if unknown. */
        data class Expression(val name: String) : Event()
        /** Pet waves — a friendly one-shot greeting. */
        object Wave : Event()
    }

    private val _events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: kotlinx.coroutines.flow.SharedFlow<Event> = _events

    /** Fire-and-forget. Safe to call from any thread; never suspends. */
    fun emit(event: Event) {
        _events.tryEmit(event)
    }
}
