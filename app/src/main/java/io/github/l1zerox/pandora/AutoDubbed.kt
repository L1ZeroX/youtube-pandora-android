package io.github.l1zerox.pandora

// MP4's textual genre is readable by Android's MediaMetadataRetriever, including after import.
internal const val AUTO_DUBBED_MARKER = "YoutubePandora:AutoDubbed:v1"

internal fun autoDubbedMetadataArgs(decision: AudioDecision?, hasAudio: Boolean): List<String> =
    if(hasAudio && decision?.usesJapanese == true && decision.autoDubbed && !decision.original)
        listOf("-metadata", "genre=$AUTO_DUBBED_MARKER") else emptyList()

internal fun isAutoDubbedMetadata(genre: String?): Boolean = genre == AUTO_DUBBED_MARKER
