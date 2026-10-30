package com.nightrider.driver
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.io.File
class NavigationTelemetryTest {
 @Test fun journalPersistsInventoryAndClosureAndRejectsLateWrites() {
   val instrumentation = InstrumentationRegistry.getInstrumentation()
   val context = instrumentation.targetContext
   val id = "nightrider-field-1234567890123"
   val file = File(context.noBackupFilesDir,"$id.jsonl")
   try {
     instrumentation.runOnMainSync {
       val log = NavigationTelemetry(context,id)
       log.event("fixture")
       log.close("test_complete")
       val length = file.length()
       log.event("late_write")
       assertEquals(length,file.length())
     }
     val events = file.readLines().map { JSONObject(it) }
     assertEquals("session_start",events.first().getString("event"))
     assertEquals(10,events.count { it.getString("event") == "sensor_inventory" })
     assertEquals("session_end",events.last().getString("event"))
     assertTrue(events.all { it.getLong("elapsed_ns") > 0 && it.getLong("wall_ms") > 0 })
   } finally { file.delete() }
 }
}
