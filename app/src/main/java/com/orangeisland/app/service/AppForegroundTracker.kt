package com.orangeisland.app.service

import java.util.concurrent.CopyOnWriteArraySet

object AppForegroundTracker {
    private val listeners = CopyOnWriteArraySet<(Boolean) -> Unit>()

    @Volatile
    var isInForeground: Boolean = false
        private set

    fun setInForeground(inForeground: Boolean) {
        if (isInForeground == inForeground) return
        isInForeground = inForeground
        listeners.forEach { it(inForeground) }
    }

    fun addListener(listener: (Boolean) -> Unit) {
        listeners.add(listener)
        listener(isInForeground)
    }

    fun removeListener(listener: (Boolean) -> Unit) {
        listeners.remove(listener)
    }
}
