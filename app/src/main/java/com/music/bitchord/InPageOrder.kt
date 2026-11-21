package com.music.bitchord

/**
 * Whether [keys] is a subsequence of [pageKeys] without changing the page's
 * order. Filtering may remove rows, but it must not rearrange the ones left.
 */
fun inPageOrder(keys: List<String>, pageKeys: List<String>): Boolean {
    var nextKey = 0
    for (pageKey in pageKeys) {
        if (nextKey < keys.size && keys[nextKey] == pageKey) {
            nextKey++
        }
    }
    return nextKey == keys.size
}
