package com.rfcontrol.app

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HistoryStoreTest {
    @Test fun historySurvivesReopenAndKeepsSessionNumbersAndTimestamps() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("detections.db")
        var store = HistoryStore(context)
        val first = store.begin("BLE", 1000)
        store.record(first, "BLE", "AA", "Headphones", "services", 1010, -80)
        store.record(first, "BLE", "BB", "", "services", 1015, -65)
        store.record(first, "BLE", "AA", "", "services", 1020, -50)
        val second = store.begin("Wi-Fi", 2000)
        store.record(second, "Wi-Fi", "AP", "Router", "CACHED", 1500, -70)
        store.close()
        store = HistoryStore(context)
        val ble = store.rows(first)
        assertEquals(listOf(1, 2), ble.map { it.number })
        assertEquals("Headphones", ble[0].name)
        assertEquals(1010L, ble[0].first); assertEquals(1020L, ble[0].last)
        assertEquals(2, ble[0].samples); assertEquals(-50, ble[0].rssi)
        val wifi = store.rows(second).single()
        assertEquals(1, wifi.number); assertEquals(1500L, wifi.last)
        assertEquals(second, store.rows().first().session)
        store.clear(); assertTrue(store.rows().isEmpty()); store.close()
    }
    @Test fun largeScansDoNotReuseNumbersAfterFiveHundredDevices() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("detections.db")
        HistoryStore(context).withClosed { store ->
            val scan = store.begin("BLE")
            for (n in 1..505) store.record(scan, "BLE", "id$n", "", "", n.toLong(), -60)
            val all = store.rows(scan, 1000)
            assertEquals(505, all.size)
            assertEquals(505, all.last().number)
            store.record(scan, "BLE", "id505", "New name", "", 900, -40)
            assertEquals(2, store.rows(scan, 1000).last().samples)
        }
    }
}

// SQLiteOpenHelper only implements AutoCloseable on newer Android versions.
internal inline fun <T> HistoryStore.withClosed(block: (HistoryStore) -> T): T =
    try { block(this) } finally { close() }
