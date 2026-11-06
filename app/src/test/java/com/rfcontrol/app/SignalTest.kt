package com.rfcontrol.app

import org.junit.Assert.*
import org.junit.Test

class SignalTest {
    @Test fun strongerSignalsAlwaysBeepFaster() {
        for (rssi in -100 until -40) assertTrue(Signal.interval(rssi + 1) < Signal.interval(rssi))
        assertEquals(250L, Signal.interval(-20))
        assertEquals(3000L, Signal.interval(-120))
    }
    @Test fun staleAndInvalidReadingsNeverQualify() {
        assertFalse(Signal.valid(127)); assertFalse(Signal.valid(0)); assertTrue(Signal.valid(-65))
        assertFalse(Signal.fresh(0, 100)); assertFalse(Signal.fresh(100, 5101))
        assertTrue(Signal.fresh(100, 5100)); assertFalse(Signal.fresh(200, 100))
    }
    @Test fun smoothingDampensSingleSpikes() {
        assertEquals(-70.0, Signal.smooth(null, -70), 0.001)
        assertEquals(-63.0, Signal.smooth(-70.0, -50), 0.001)
    }
}
