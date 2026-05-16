package se.lublin.humla.audio.encoder

import com.googlecode.javacpp.IntPointer
import se.lublin.humla.audio.javacpp.Speex
import se.lublin.humla.net.PacketBuffer

class PreprocessingEncoder(
    encoder: IEncoder,
    frameSize: Int,
    sampleRate: Int
) : IEncoder {
    private var encoder: IEncoder? = encoder
    private var preprocessor: Speex.SpeexPreprocessState? = Speex.SpeexPreprocessState(frameSize, sampleRate)

    init {
        val argument = IntPointer(1)
        argument.put(0)
        preprocessor!!.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_VAD, argument)
        argument.put(1)
        preprocessor!!.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_AGC, argument)
        preprocessor!!.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_DENOISE, argument)
        preprocessor!!.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_DEREVERB, argument)
        argument.put(30000)
        preprocessor!!.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_SET_AGC_TARGET, argument)
        argument.put(99)
        preprocessor!!.control(Speex.SpeexPreprocessState.SPEEX_PREPROCESS_GET_PROB_START, argument)
    }

    override fun encode(input: ShortArray, inputSize: Int): Int {
        preprocessor!!.preprocess(input)
        return encoder!!.encode(input, inputSize)
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
        preprocessor?.destroy()
        encoder?.destroy()
        preprocessor = null
        encoder = null
    }
}
