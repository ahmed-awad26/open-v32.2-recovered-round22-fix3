package com.opencontacts.feature.contacts.fastscroll

import kotlin.math.floor

fun mapTouchYToLetterIndex(
    touchY: Float,
    containerHeightPx: Float,
    letterCount: Int,
): Int {
    if (letterCount <= 1 || containerHeightPx <= 0f) return 0
    val clampedY = touchY.coerceIn(0f, containerHeightPx - 1f)
    val fraction = clampedY / containerHeightPx
    return floor(fraction * letterCount)
        .toInt()
        .coerceIn(0, letterCount - 1)
}
