package io.github.l1zerox.pandora

/** Main-thread ownership: a repeated URI must not make an earlier A→B→A load current again. */
internal class ArtworkRequest {
    private var entry: LibraryEntry?=null
    private var generation=0L
    fun begin(next: LibraryEntry): Long? {
        if(entry == next) return null
        entry=next
        return ++generation
    }
    fun invalidate() { entry=null; generation++ }
    fun accepts(ticket: Long)=ticket == generation
}
