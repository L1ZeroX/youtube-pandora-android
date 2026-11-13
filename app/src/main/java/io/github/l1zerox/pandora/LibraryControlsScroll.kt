package io.github.l1zerox.pandora

/** Collapse immediately when the user scrolls down the list; programmatic movement is ignored. */
internal class LibraryControlsScroll(private val threshold: Float) {
    private var topPull=0f
    fun scroll(expanded: Boolean, atTop: Boolean, consumedY: Float, availableY: Float, userInput: Boolean): Boolean? {
        if(!userInput) return null
        val movement=consumedY+availableY
        if(expanded && movement < 0) { endGesture(); return false }
        if(!expanded && atTop && availableY > 0) {
            topPull+=availableY
            if(topPull >= threshold) { endGesture(); return true }
        } else topPull=0f
        return null
    }
    fun endGesture() { topPull=0f }
}
