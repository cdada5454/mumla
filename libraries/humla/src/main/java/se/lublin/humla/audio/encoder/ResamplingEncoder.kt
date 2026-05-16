package se.lublin.humla.audio.encoder

import se.lublin.humla.audio.javacpp.Speex
import se.lublin.humla.net.PacketBuffer

class ResamplingEncoder(
    encoder: IEncoder,
    channels: Int,
    inputSampleRate: Int,
    private val targetFrameSize: Int,
    targetSampleRate: Int
) : IEncoder {
    private var encoder: IEncoder? = encoder
    private var resampler: Speex.SpeexResampler? =
        Speex.SpeexResampler(channels, inputSampleRate, targetSampleRate, SPEEX_RESAMPLE_QUALITY)
    private val resampleBuffer = ShortArray(targetFrameSize)

    override fun encode(input: ShortArray, inputSize: Int): Int {
        resampler!!.resample(input, resampleBuffer)
        return encoder!!.encode(resampleBuffer, targetFrameSize)
    }

    override fun getBufferedFrames(): Int = encoder!!.getBufferedFrames()

    override fun isReady(): Boolean = encoder!!.isReady()

    override fun getEncodedData(packetBuffer: PacketBuffer) {
        encoder!!.getEncodedData(packetBuffer)
    }

    override fun terminate() {
        encoder!!.terminate()
    }

    fun setEncoder(encoder: IEncoder) {
        this.encoder?.destroy()
        this.encoder = encoder
    }

    override fun destroy() {
        resampler?.destroy()
        encoder?.destroy()
        resampler = null
        encoder = null
    }

    companion object {
        private const val SPEEX_RESAMPLE_QUALITY = 3
    }
}
