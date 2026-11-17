package com.cartefedelta.app

import com.cartefedelta.app.share.ShareCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCodecTest {
    private fun sample(i: Int) = ShareCodec.SharedCard(
        name = "Negozio $i àèé", number = "40063813339${i % 10}1", format = "EAN_13",
        color = 0xFFE30613.toInt(), storeKey = if (i % 2 == 0) "conad" else null,
        note = "nota | con pipe $i", displayNumber = "", favorite = i % 3 == 0
    )

    @Test fun roundTripManyParts() {
        val cards = (1..80).map { sample(it) }
        val qrs = ShareCodec.encode(cards)
        assertTrue(qrs.size > 1)
        val c = ShareCodec.Collector()
        // ordine sparso e duplicati, come dalla fotocamera
        (qrs.reversed() + qrs).forEach { assertTrue(c.add(it)) }
        assertTrue(c.isComplete())
        assertEquals(cards, c.result())
    }

    @Test fun singleCard() {
        val qrs = ShareCodec.encode(listOf(sample(1)))
        assertEquals(1, qrs.size)
        val c = ShareCodec.Collector()
        assertFalse(c.add("4006381333931"))
        c.add(qrs[0])
        assertEquals(listOf(sample(1)), c.result())
    }
}
