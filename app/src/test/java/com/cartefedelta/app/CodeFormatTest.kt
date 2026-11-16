package com.cartefedelta.app

import com.cartefedelta.app.barcode.BarcodeEncoder
import com.cartefedelta.app.barcode.CodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeFormatTest {
    @Test fun eanCheckDigit() {
        assertTrue(CodeFormat.eanCheckOk("4006381333931"))
        assertFalse(CodeFormat.eanCheckOk("4006381333932"))
        assertTrue(CodeFormat.eanCheckOk("96385074"))
    }

    @Test fun guessFormat() {
        assertEquals(CodeFormat.EAN_13, CodeFormat.guess("4006381333931"))
        assertEquals(CodeFormat.EAN_8, CodeFormat.guess("96385074"))
        assertEquals(CodeFormat.CODE_128, CodeFormat.guess("ABC-123"))
    }

    @Test fun encodeWithFallback() {
        val ok = BarcodeEncoder.encode("4006381333931", CodeFormat.EAN_13)
        assertNotNull(ok)
        assertFalse(ok!!.fellBack)
        val bad = BarcodeEncoder.encode("12345", CodeFormat.EAN_13)
        assertNotNull(bad)
        assertTrue(bad!!.fellBack)
        assertEquals(CodeFormat.CODE_128, bad.format)
        CodeFormat.entries.forEach { f ->
            val sample = when (f) {
                CodeFormat.EAN_13 -> "4006381333931"
                CodeFormat.EAN_8 -> "96385074"
                CodeFormat.UPC_A -> "036000291452"
                CodeFormat.UPC_E -> "0123456"
                CodeFormat.ITF -> "12345678"
                CodeFormat.CODABAR -> "A123456A"
                CodeFormat.CODE_39, CodeFormat.CODE_93 -> "CARD123"
                else -> "1234567890"
            }
            val e = BarcodeEncoder.encode(sample, f)
            assertNotNull("formato $f", e)
            assertFalse("formato $f ripiegato", e!!.fellBack)
        }
    }
}
