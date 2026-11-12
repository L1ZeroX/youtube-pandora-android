package io.github.l1zerox.pandora

/** Only user scrolling arms collapse; list reloads and programmatic navigation never do. */
internal class LibraryControlsScroll(private val threshold: Float) {
    private var topPull=0f
    private var upward=0f
    fun scroll(expanded: Boolean, atTop: Boolean, atBottom: Boolean, consumedY: Float, availableY: Float, userInput: Boolean): Boolean? {
        if(!userInput) return null
        val movement=consumedY+availableY
        if(movement > 0) upward=0f
        if(expanded && movement < 0) {
            upward-=movement
            if(atBottom && upward >= threshold) { reset(); return false }
        }
        if(!expanded && atTop && availableY > 0) {
            topPull+=availableY
            if(topPull >= threshold) { reset(); return true }
        } else topPull=0f
        return null
    }
    fun endFling(expanded: Boolean, atBottom: Boolean): Boolean? {
        val collapse=expanded && atBottom && upward >= threshold
        reset()
        return if(collapse) false else null
    }
    private fun reset() { topPull=0f; upward=0f }
}
