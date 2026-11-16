package com.cartefedelta.app.barcode

import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/** Formati supportati, con i nomi ZXing. */
enum class CodeFormat(val label: String, val twoD: Boolean) {
    EAN_13("EAN-13", false),
    EAN_8("EAN-8", false),
    UPC_A("UPC-A", false),
    UPC_E("UPC-E", false),
    CODE_128("Code 128", false),
    CODE_39("Code 39", false),
    CODE_93("Code 93", false),
    ITF("ITF", false),
    CODABAR("Codabar", false),
    QR_CODE("QR Code", true),
    PDF_417("PDF417", true),
    AZTEC("Aztec", true),
    DATA_MATRIX("Data Matrix", true);

    companion object {
        fun of(name: String?): CodeFormat = entries.firstOrNull { it.name == name } ?: CODE_128

        fun fromMlKit(format: Int): CodeFormat? = when (format) {
            Barcode.FORMAT_EAN_13 -> EAN_13
            Barcode.FORMAT_EAN_8 -> EAN_8
            Barcode.FORMAT_UPC_A -> UPC_A
            Barcode.FORMAT_UPC_E -> UPC_E
            Barcode.FORMAT_CODE_128 -> CODE_128
            Barcode.FORMAT_CODE_39 -> CODE_39
            Barcode.FORMAT_CODE_93 -> CODE_93
            Barcode.FORMAT_ITF -> ITF
            Barcode.FORMAT_CODABAR -> CODABAR
            Barcode.FORMAT_QR_CODE -> QR_CODE
            Barcode.FORMAT_PDF417 -> PDF_417
            Barcode.FORMAT_AZTEC -> AZTEC
            Barcode.FORMAT_DATA_MATRIX -> DATA_MATRIX
            else -> null
        }

        /** Formato più probabile per un numero digitato a mano. */
        fun guess(number: String): CodeFormat {
            val n = number.trim()
            if (n.all { it.isDigit() }) {
                if (n.length == 13 && eanCheckOk(n)) return EAN_13
                if (n.length == 8 && eanCheckOk(n)) return EAN_8
                if (n.length == 12 && eanCheckOk("0$n")) return UPC_A
            }
            return CODE_128
        }

        /** Controlla la cifra di controllo EAN/UPC. */
        fun eanCheckOk(digits: String): Boolean {
            if (digits.isEmpty() || !digits.all { it.isDigit() }) return false
            val body = digits.dropLast(1)
            var sum = 0
            body.reversed().forEachIndexed { i, c -> sum += (c - '0') * if (i % 2 == 0) 3 else 1 }
            return (10 - sum % 10) % 10 == digits.last() - '0'
        }
    }
}

/** Risultato del disegno: la matrice e il formato effettivamente usato. */
data class Encoded(val matrix: BitMatrix, val format: CodeFormat, val fellBack: Boolean)

object BarcodeEncoder {
    fun encode(content: String, format: CodeFormat): Encoded? {
        if (content.isEmpty()) return null
        tryEncode(content, format)?.let { return Encoded(it, format, false) }
        // Contenuto non valido per quel formato: ripiego su Code 128, che accetta tutto.
        if (format != CodeFormat.CODE_128) {
            tryEncode(content, CodeFormat.CODE_128)?.let { return Encoded(it, CodeFormat.CODE_128, true) }
        }
        return null
    }

    private fun tryEncode(content: String, format: CodeFormat): BitMatrix? = try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 0,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        MultiFormatWriter().encode(content, BarcodeFormat.valueOf(format.name), 0, 0, hints)
    } catch (e: Exception) {
        null
    }
}
