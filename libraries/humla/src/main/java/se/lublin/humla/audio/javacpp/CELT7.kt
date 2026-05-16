package se.lublin.humla.audio.javacpp

import com.googlecode.javacpp.IntPointer
import com.googlecode.javacpp.Loader
import com.googlecode.javacpp.Pointer
import com.googlecode.javacpp.annotation.Cast
import com.googlecode.javacpp.annotation.Platform
import java.nio.ByteBuffer
import se.lublin.humla.audio.IDecoder
import se.lublin.humla.exception.NativeAudioException
import se.lublin.humla.protocol.AudioHandler

@Platform(library = "jnicelt7", cinclude = ["<celt.h>", "<celt_types.h>"])
class CELT7 {
    class CELT7Decoder(sampleRate: Int, frameSize: Int, channels: Int) : IDecoder {
        private val mode: Pointer
        private val state: Pointer

        init {
            val error = IntPointer(1)
            error.put(0)
            mode = celt_mode_create(sampleRate, frameSize, error)
            if (error.get() < 0) {
                throw NativeAudioException("CELT 0.7.0 decoder initialization failed with error: ${error.get()}")
            }
            state = celt_decoder_create(mode, channels, error)
            if (error.get() < 0) {
                throw NativeAudioException("CELT 0.7.0 decoder initialization failed with error: ${error.get()}")
            }
        }

        override fun decodeFloat(input: ByteBuffer?, inputSize: Int, output: FloatArray, frameSize: Int): Int {
            val result = celt_decode_float(state, input, inputSize, output)
            if (result < 0) {
                throw NativeAudioException("CELT 0.7.0 decoding failed with error: $result")
            }
            return frameSize
        }

        override fun decodeShort(input: ByteBuffer?, inputSize: Int, output: ShortArray, frameSize: Int): Int {
            val result = celt_decode(state, input, inputSize, output)
            if (result < 0) {
                throw NativeAudioException("CELT 0.7.0 decoding failed with error: $result")
            }
            return frameSize
        }

        override fun destroy() {
            celt_decoder_destroy(state)
            celt_mode_destroy(mode)
        }
    }

    companion object {
        const val CELT_GET_BITSTREAM_VERSION = 2000
        const val CELT_SET_VBR_RATE_REQUEST = 6
        const val CELT_SET_PREDICTION_REQUEST = 4

        init {
            Loader.load()
        }

        @JvmStatic external fun celt_mode_create(sampleRate: Int, frameSize: Int, error: IntPointer?): Pointer
        @JvmStatic external fun celt_mode_info(@Cast("const CELTMode*") mode: Pointer, request: Int, value: IntPointer): Int
        @JvmStatic external fun celt_mode_destroy(@Cast("CELTMode*") mode: Pointer)
        @JvmStatic external fun celt_decoder_create(@Cast("CELTMode*") mode: Pointer, channels: Int, error: IntPointer): Pointer
        @JvmStatic external fun celt_decode(@Cast("CELTDecoder*") state: Pointer, @Cast("const unsigned char*") data: ByteBuffer?, len: Int, pcm: ShortArray): Int
        @JvmStatic external fun celt_decode_float(@Cast("CELTDecoder*") state: Pointer, @Cast("const unsigned char*") data: ByteBuffer?, len: Int, pcm: FloatArray): Int
        @JvmStatic external fun celt_decoder_ctl(@Cast("CELTDecoder*") state: Pointer, request: Int, value: Pointer): Int
        @JvmStatic external fun celt_decoder_destroy(@Cast("CELTDecoder*") state: Pointer)
        @JvmStatic external fun celt_encoder_create(@Cast("const CELTMode *") mode: Pointer, channels: Int, error: IntPointer): Pointer
        @JvmStatic external fun celt_encoder_ctl(@Cast("CELTEncoder*") state: Pointer, request: Int, value: Pointer): Int
        @JvmStatic external fun celt_encoder_ctl(@Cast("CELTEncoder*") state: Pointer, request: Int, value: Int): Int
        @JvmStatic external fun celt_encode(@Cast("CELTEncoder *") state: Pointer, @Cast("const short *") pcm: ShortArray, @Cast("short *") optionalSynthesis: ShortArray?, @Cast("unsigned char *") compressed: ByteArray, nbCompressedBytes: Int): Int
        @JvmStatic external fun celt_encoder_destroy(@Cast("CELTEncoder *") state: Pointer)

        @JvmStatic
        fun getBitstreamVersion(): Int {
            val versionPointer = IntPointer()
            val modePointer = celt_mode_create(AudioHandler.SAMPLE_RATE, AudioHandler.FRAME_SIZE, null)
            celt_mode_info(modePointer, CELT_GET_BITSTREAM_VERSION, versionPointer)
            celt_mode_destroy(modePointer)
            return versionPointer.get()
        }
    }
}
