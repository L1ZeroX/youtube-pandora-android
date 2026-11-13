package io.github.l1zerox.pandora

import java.text.Normalizer
import java.util.Locale

internal val LIBRARY_INDEX_LABELS=listOf("#","あ","か","さ","た","な","は","ま","や","ら","わ","漢")+('A'..'Z').map(Char::toString)
internal fun libraryIndexLabel(title: String): String {
    val text=Normalizer.normalize(title,Normalizer.Form.NFKC)
    val point=text.codePoints().filter { Character.isLetterOrDigit(it) }.findFirst().orElse(-1)
    if(point < 0) return "#"
    if(Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN) return "漢"
    if(point > Char.MAX_VALUE.code) return "#"
    val char=point.toChar()
    val hira=if(char in 'ァ'..'ヶ') char-0x60 else char
    if(hira == 'ゔ') return "あ"
    if(hira == 'ゕ' || hira == 'ゖ') return "か"
    if(hira in 'ぁ'..'ゖ') return when {
        hira < 'か' -> "あ"; hira < 'さ' -> "か"; hira < 'た' -> "さ"; hira < 'な' -> "た"
        hira < 'は' -> "な"; hira < 'ま' -> "は"; hira < 'ゃ' -> "ま"; hira < 'ら' -> "や"
        hira < 'ゎ' -> "ら"; else -> "わ"
    }
    return char.uppercaseChar().takeIf { it in 'A'..'Z' }?.toString() ?: "#"
}
internal fun libraryTitleKey(title: String): String =
    LIBRARY_INDEX_LABELS.indexOf(libraryIndexLabel(title)).toString().padStart(2,'0')+Normalizer.normalize(title,Normalizer.Form.NFKC).lowercase(Locale.ROOT)
internal data class LibraryIndexRow(val key: String, val title: String)
internal fun libraryIndexTargets(rows: List<LibraryIndexRow>): Map<String,Int> {
    val first=mutableMapOf<String,Int>()
    rows.forEachIndexed { index,row -> if(row.title.isNotBlank()) first.putIfAbsent(libraryIndexLabel(row.title),index) }
    return LIBRARY_INDEX_LABELS.mapNotNull { label -> first[label]?.let { label to it } }.toMap()
}
internal fun libraryIndexAt(labels: List<String>, y: Float, height: Int): String? =
    if(labels.isEmpty() || height <= 0 || !y.isFinite()) null else labels[(y/height*labels.size).toInt().coerceIn(labels.indices)]
