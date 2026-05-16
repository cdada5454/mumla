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

@Platform(library = "jnicelt11", cinclude = ["<celt.h>", "<celt_types.h>"])
class CELT11 {
    class CELT11Decoder(sampleRate: Int, channels: Int) : IDecoder {
        private val state: Pointer

        init {
            val error = IntPointer(1)
            error.put(0)
            state = celt_decoder_create(sampleRate, channels, error)
            if (error.get() < 0) {
                throw NativeAudioException("CELT 0.11.0 decoder initialization failed with error: ${error.get()}")
            }
        }

        override fun decodeFloat(input: ByteBuffer?, inputSize: Int, output: FloatArray, frameSize: Int): Int {
            val result = celt_decode_float(state, input, inputSize, output, frameSize)
            if (result < 0) {
                throw NativeAudioException("CELT 0.11.0 decoding failed with error: $result")
            }
            return frameSize
        }

        override fun decodeShort(input: ByteBuffer?, inputSize: Int, output: ShortArray, frameSize: Int): Int {
            val result = celt_decode(state, input, inputSize, output, frameSize)
            if (result < 0) {
                throw NativeAudioException("CELT 0.11.0 decoding failed with error: $result")
            }
            return frameSize
        }

        override fun destroy() {
            celt_decoder_destroy(state)
        }
    }

    companion object {
        const val CELT_GET_BITSTREAM_VERSION = 2000
        const val CELT_SET_BITRATE_REQUEST = 6
        const val CELT_SET_PREDICTION_REQUEST = 4

        init {
            Loader.load()
        }

        @JvmStatic external fun celt_mode_create(sampleRate: Int, frameSize: Int, error: IntPointer?): Pointer
        @JvmStatic external fun celt_mode_info(@Cast("const CELTMode*") mode: Pointer, request: Int, value: IntPointer): Int
        @JvmStatic external fun celt_mode_destroy(@Cast("CELTMode*") mode: Pointer)
        @JvmStatic external fun celt_decoder_create(sampleRate: Int, channels: Int, error: IntPointer): Pointer
        @JvmStatic external fun celt_decode(@Cast("CELTDecoder*") state: Pointer, @Cast("const unsigned char*") data: ByteBuffer?, len: Int, pcm: ShortArray, frameSize: Int): Int
        @JvmStatic external fun celt_decode_float(@Cast("CELTDecoder*") state: Pointer, @Cast("const unsigned char*") data: ByteBuffer?, len: Int, pcm: FloatArray, frameSize: Int): Int
        @JvmStatic external fun celt_decoder_ctl(@Cast("CELTDecoder*") state: Pointer, request: Int, value: Pointer): Int
        @JvmStatic external fun celt_decoder_destroy(@Cast("CELTDecoder*") state: Pointer)
        @JvmStatic external fun celt_encoder_create(sampleRate: Int, channels: Int, error: IntPointer): Pointer
        @JvmStatic external fun celt_encoder_ctl(@Cast("CELTEncoder*") state: Pointer, request: Int, value: Pointer): Int
        @JvmStatic external fun celt_encoder_ctl(@Cast("CELTEncoder*") state: Pointer, request: Int, value: Int): Int
        @JvmStatic external fun celt_encode(@Cast("CELTEncoder*") state: Pointer, @Cast("const short*") pcm: ShortArray, frameSize: Int, @Cast("unsigned char*") compressed: ByteArray, maxCompressedBytes: Int): Int
        @JvmStatic external fun celt_encoder_destroy(@Cast("CELTEncoder*") state: Pointer)

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
