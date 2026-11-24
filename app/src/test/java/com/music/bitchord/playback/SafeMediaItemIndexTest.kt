package com.music.bitchord.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeMediaItemIndexTest {

    @Test
    fun returnsValidQueueNeighbour() {
        assertEquals(2, safeMediaItemIndex(queueSize = 4) { 2 })
    }

    @Test
    fun treatsUnsetAndOutOfRangeIndicesAsUnavailable() {
        assertNull(safeMediaItemIndex(queueSize = 4) { -1 })
        assertNull(safeMediaItemIndex(queueSize = 1) { 4 })
    }

    @Test
    fun catchesMedia3TimelineIndexRace() {
        assertNull(
            safeMediaItemIndex(queueSize = 1) {
                throw ArrayIndexOutOfBoundsException("transient PlaylistTimeline mapping")
            },
        )
    }
}
