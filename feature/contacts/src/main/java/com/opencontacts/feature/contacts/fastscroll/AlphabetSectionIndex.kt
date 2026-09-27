package com.opencontacts.feature.contacts.fastscroll

val FastScrollLetters: List<String> = buildList {
    add("#")
    ('A'..'Z').forEach { add(it.toString()) }
}

data class AlphabetSectionIndex(
    val letters: List<String> = FastScrollLetters,
    val firstPositionByLetter: Map<String, Int>,
) {
    val availableLetters: Set<String> = firstPositionByLetter.keys

    fun resolveTargetPosition(selectedLetter: String): Int? {
        val normalized = selectedLetter.uppercase()
        val selectedIndex = letters.indexOf(normalized).takeIf { it >= 0 } ?: return null
        for (index in selectedIndex until letters.size) {
            firstPositionByLetter[letters[index]]?.let { return it }
        }
        for (index in selectedIndex - 1 downTo 0) {
            firstPositionByLetter[letters[index]]?.let { return it }
        }
        return null
    }
}

fun normalizeSectionLetter(rawValue: String?): String {
    val firstChar = rawValue
        ?.trim()
        ?.firstOrNull()
        ?.uppercaseChar()
        ?: return "#"
    return if (firstChar in 'A'..'Z') firstChar.toString() else "#"
}

fun <T> buildAlphabetSectionIndex(
    items: List<T>,
    labelProvider: (T) -> String?,
): AlphabetSectionIndex {
    val firstPositionByLetter = linkedMapOf<String, Int>()
    items.forEachIndexed { index, item ->
        val letter = normalizeSectionLetter(labelProvider(item))
        firstPositionByLetter.putIfAbsent(letter, index)
    }
    return AlphabetSectionIndex(firstPositionByLetter = firstPositionByLetter)
}
