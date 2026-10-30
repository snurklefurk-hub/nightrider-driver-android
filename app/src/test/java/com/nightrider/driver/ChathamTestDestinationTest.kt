package com.nightrider.driver
import org.junit.Test
import org.junit.Assert.*
import kotlin.random.Random
import app.vela.core.model.LatLng
class ChathamTestDestinationTest {
 @Test fun generatedPinsStayInsideTheInlandTestArea() {
   val random = Random(45)
   val pins = (1..10000).map { ChathamTestDestination.random(random) }
   assertTrue(pins.all { ChathamTestDestination.contains(it) })
   assertTrue(pins.distinct().size > 9900)
   assertFalse(ChathamTestDestination.contains(LatLng(32.0,-80.0)))
   assertFalse(ChathamTestDestination.contains(LatLng(Double.NaN,-81.1)))
 }
}
