package com.plocoli.yomp

import com.plocoli.yomp.data.batteryPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryPercentTest {

    @Test
    fun `converts level over scale to percent`() {
        assertEquals(100, batteryPercent(100, 100))
        assertEquals(42, batteryPercent(42, 100))
        assertEquals(0, batteryPercent(0, 100))
    }

    /** Scale is not always 100 — some devices report 1000, so it must not be assumed. */
    @Test
    fun `handles a non-100 scale`() {
        assertEquals(50, batteryPercent(500, 1000))
        assertEquals(7, batteryPercent(75, 1000))
    }

    /** Missing extras come back as -1; the indicator should stay hidden rather than show junk. */
    @Test
    fun `returns null when the values are missing`() {
        assertNull(batteryPercent(-1, 100))
        assertNull(batteryPercent(50, -1))
        assertNull(batteryPercent(50, 0))
    }

    @Test
    fun `clamps out-of-range readings`() {
        assertEquals(100, batteryPercent(150, 100))
    }
}
