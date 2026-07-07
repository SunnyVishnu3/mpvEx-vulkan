package app.gyrolet.mpvrx.utils.media

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer

object SubtitleNormalizer {
    /**
     * Safely attempts to normalize a subtitle byte array to NFC (Normalization Form Composed).
     * It checks if the bytes are valid UTF-8. If they are, it normalizes them.
     * If they are not (e.g., they are in a legacy encoding), it returns the original bytes untouched
     * to prevent corruption.
     */
    fun normalizeToNfcIfNeeded(bytes: ByteArray): ByteArray {
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
            decoder.onMalformedInput(CodingErrorAction.REPORT)
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT)

            val decodedString = decoder.decode(ByteBuffer.wrap(bytes)).toString()

            if (Normalizer.isNormalized(decodedString, Normalizer.Form.NFC)) {
                bytes
            } else {
                val normalizedString = Normalizer.normalize(decodedString, Normalizer.Form.NFC)
                normalizedString.toByteArray(StandardCharsets.UTF_8)
            }
        } catch (e: CharacterCodingException) {
            // Not valid UTF-8, probably another encoding like EUC-KR or CP1252.
            // Returning original bytes to avoid corrupting the file.
            bytes
        } catch (e: Exception) {
            bytes
        }
    }
}
