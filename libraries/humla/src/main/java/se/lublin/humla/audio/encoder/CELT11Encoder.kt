package se.lublin.humla.audio.encoder

import com.googlecode.javacpp.IntPointer
import com.googlecode.javacpp.Pointer
import java.nio.BufferOverflowException
import java.nio.BufferUnderflowException
import se.lublin.humla.audio.javacpp.CELT11
import se.lublin.humla.exception.NativeAudioException
import se.lublin.humla.net.PacketBuffer

class CELT11Encoder(
    sampleRate: Int,
    channels: Int,
    private val framesPerPacket: Int
) : IEncoder {
    private val bufferSize = sampleRate / 800
    private val buffer = Array(framesPerPacket) { ByteArray(bufferSize) }
    private var bufferedFrames = 0
    private val state: Pointer

    init {
        val error = IntPointer(1)
        error.put(0)
        state = CELT11.celt_encoder_create(sampleRate, channels, error)
        if (error.get() < 0) {
            throw NativeAudioException("CELT 0.11.0 encoder initialization failed with error: ${error.get()}")
        }
    }

    override fun encode(input: ShortArray, frameSize: Int): Int {
        if (bufferedFrames >= framesPerPacket) {
            throw BufferOverflowException()
        }
        val result = CELT11.celt_encode(state, input, frameSize, buffer[bufferedFrames], bufferSize)
        if (result < 0) {
            throw NativeAudioException("CELT 0.11.0 encoding failed with error: $result")
        }
        bufferedFrames++
        return result
    }

    override fun getBufferedFrames(): Int = bufferedFrames

    override fun isReady(): Boolean = bufferedFrames == framesPerPacket

    override fun getEncodedData(packetBuffer: PacketBuffer) {
        if (bufferedFrames < framesPerPacket) {
            throw BufferUnderflowException()
        }
        for (index in 0 until bufferedFrames) {
            val frame = buffer[index]
            var head = frame.size
            if (index < bufferedFrames - 1) {
                head = head or 0x80
            }
            packetBuffer.append(head.toLong())
            packetBuffer.append(frame, frame.size)
        }
        bufferedFrames = 0
    }

    override fun terminate() = Unit

    override fun destroy() {
        CELT11.celt_encoder_destroy(state)
    }
}
