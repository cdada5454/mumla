package se.lublin.humla.audio.encoder

import java.nio.BufferOverflowException
import java.nio.BufferUnderflowException
import java.util.Arrays
import se.lublin.humla.exception.NativeAudioException
import se.lublin.humla.net.PacketBuffer

class OpusEncoder(
    sampleRate: Int,
    channels: Int,
    private val frameSize: Int,
    private val framesPerPacket: Int,
    bitrate: Int,
    maxBufferSize: Int
) : IEncoder {
    private val buffer = ByteArray(maxBufferSize)
    private val audioBuffer = ShortArray(framesPerPacket * frameSize)
    private var bufferedFrames = 0
    private var encodedLength = 0
    private var terminated = false
    private var state: Long

    init {
        state = try {
            NativeOpusEncoder.nativeCreate(sampleRate, channels, bitrate)
        } catch (e: IllegalStateException) {
            throw NativeAudioException(e)
        } catch (e: UnsatisfiedLinkError) {
            throw NativeAudioException(e)
        }
        if (state == 0L) {
            throw NativeAudioException("Opus encoder initialization returned a null native handle.")
        }
    }

    override fun encode(input: ShortArray, inputSize: Int): Int {
        if (bufferedFrames >= framesPerPacket) {
            throw BufferOverflowException()
        }
        if (inputSize != frameSize) {
            throw IllegalArgumentException("This Opus encoder implementation requires a constant frame size.")
        }
        terminated = false
        System.arraycopy(input, 0, audioBuffer, frameSize * bufferedFrames, frameSize)
        bufferedFrames++
        return if (bufferedFrames == framesPerPacket) encode() else 0
    }

    private fun encode(): Int {
        if (bufferedFrames < framesPerPacket) {
            Arrays.fill(audioBuffer, frameSize * bufferedFrames, audioBuffer.size, 0.toShort())
            bufferedFrames = framesPerPacket
        }
        val result = NativeOpusEncoder.nativeEncode(
            state,
            audioBuffer,
            frameSize * bufferedFrames,
            buffer,
            buffer.size
        )
        if (result < 0) {
            throw NativeAudioException("Opus encoding failed with error: $result")
        }
        encodedLength = result
        return result
    }

    override fun getBufferedFrames(): Int = bufferedFrames

    override fun isReady(): Boolean = encodedLength > 0

    override fun getEncodedData(packetBuffer: PacketBuffer) {
        if (!isReady()) {
            throw BufferUnderflowException()
        }
        var size = encodedLength
        if (terminated) {
            size = size or (1 shl 13)
        }
        packetBuffer.writeLong(size.toLong())
        packetBuffer.append(buffer, encodedLength)
        bufferedFrames = 0
        encodedLength = 0
        terminated = false
    }

    override fun terminate() {
        terminated = true
        if (bufferedFrames > 0 && !isReady()) {
            encode()
        }
    }

    val bitrate: Int
        get() {
            val result = NativeOpusEncoder.nativeGetBitrate(state)
            if (result < 0) {
                throw NativeAudioException("Opus bitrate query failed with error: $result")
            }
            return result
        }

    override fun destroy() {
        if (state != 0L) {
            NativeOpusEncoder.nativeDestroy(state)
            state = 0L
        }
    }
}
