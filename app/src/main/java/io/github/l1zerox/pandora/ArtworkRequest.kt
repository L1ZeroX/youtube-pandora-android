package io.github.l1zerox.pandora

/** Main-thread ownership: a repeated URI must not make an earlier A→B→A load current again. */
internal class ArtworkRequest {
    private var entry: LibraryEntry?=null
    private var revision: Any?=null
    private var generation=0L
    fun begin(next: LibraryEntry, revision: Any? = null): Long? {
        if(entry == next && this.revision == revision) return null
        entry=next; this.revision=revision
        return ++generation
    }
    fun invalidate() { entry=null; revision=null; generation++ }
    fun accepts(ticket: Long)=ticket == generation
}
