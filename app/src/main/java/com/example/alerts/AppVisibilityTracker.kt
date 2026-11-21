package com.example.alerts

import java.util.concurrent.atomic.AtomicBoolean

object AppVisibilityTracker {
    private val foreground = AtomicBoolean(false)

    val isAppInForeground: Boolean
        get() = foreground.get()

    fun setForeground(isVisible: Boolean) {
        foreground.set(isVisible)
    }
}
