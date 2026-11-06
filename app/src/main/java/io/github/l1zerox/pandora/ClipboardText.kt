package io.github.l1zerox.pandora

internal fun appendClipboardText(current: String, copied: String?): String {
    val addition = copied?.trim().orEmpty()
    if(addition.isEmpty()) return current
    if(current.isBlank()) return addition
    return current + (if(current.endsWith("\n")) "" else "\n") + addition
}
