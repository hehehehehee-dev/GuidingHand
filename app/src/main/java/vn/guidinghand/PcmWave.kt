package vn.guidinghand

import java.nio.ByteBuffer
import java.nio.ByteOrder

object PcmWave {
    const val MODEL_NATIVE_RATE = 24000
    fun encode(pcm: ByteArray, rate: Int = MODEL_NATIVE_RATE): ByteArray {
        require(rate in setOf(16000, 24000) && pcm.size % 2 == 0 && pcm.size in rate * 2 / 5..rate * 2 * 20)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(pcm.size + 36).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1.toShort()).putShort(1.toShort()).putInt(rate).putInt(rate * 2).putShort(2.toShort()).putShort(16.toShort())
            .put("data".toByteArray()).putInt(pcm.size)
        return header.array() + pcm
    }
}
