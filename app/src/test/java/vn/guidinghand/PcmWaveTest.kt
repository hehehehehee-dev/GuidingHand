package vn.guidinghand

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class PcmWaveTest {
    @Test fun recordingHasModelCompatibleMonoPcm16WaveHeader() {
        val wave = PcmWave.encode(ByteArray(48000))
        val data = ByteBuffer.wrap(wave).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wave.copyOfRange(0,4)))
        assertEquals("WAVE", String(wave.copyOfRange(8,12)))
        assertEquals(24000,data.getInt(24)); assertEquals(1,data.getShort(22).toInt())
        assertEquals(16,data.getShort(34).toInt()); assertEquals(48044,wave.size)
    }
    @Test fun unsupportedRatesAndUnboundedAudioAreRejected() {
        for ((pcm,rate) in listOf(ByteArray(10) to 24000,ByteArray(960002) to 24000,ByteArray(32000) to 8000)) {
            try { PcmWave.encode(pcm,rate); fail("Expected bounded recording") } catch (_: IllegalArgumentException) {}
        }
    }
}
