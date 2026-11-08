package io.github.l1zerox.pandora

import kotlinx.coroutines.CancellationException

/** Enqueue and polling share the same one-way delta -> full recovery path. */
internal fun runApkUpdateStep(operation: ()->Unit, canFallback: ()->Boolean,
    fallback: (Exception)->Unit, failed: (Exception)->Unit) {
    try { operation() }
    catch(error: Exception) {
        if(error is CancellationException) throw error
        if(!canFallback()) { failed(error); return }
        try { fallback(error) }
        catch(fallbackError: Exception) {
            if(fallbackError is CancellationException) throw fallbackError
            failed(fallbackError)
        }
    }
}
