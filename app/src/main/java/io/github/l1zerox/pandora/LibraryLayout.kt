package io.github.l1zerox.pandora

internal enum class LibraryLayout(val id: String, val label: String, val columns: Int) {
    STANDARD("standard","標準リスト",1),
    COMPACT("compact","コンパクト",1),
    DETAILS("details","詳細リスト",1),
    GRID_TWO("grid_two","2列グリッド",2),
    GRID_THREE("grid_three","3列グリッド",3),
    LARGE("large","大きいサムネイル",1);

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}
