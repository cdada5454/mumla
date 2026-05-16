package se.lublin.humla.audio.javacpp

import com.googlecode.javacpp.IntPointer
import com.googlecode.javacpp.Loader
import com.googlecode.javacpp.Pointer
import com.googlecode.javacpp.annotation.Cast
import com.googlecode.javacpp.annotation.Platform
import java.nio.ByteBuffer
import se.lublin.humla.audio.IDecoder
import se.lublin.humla.exception.NativeAudioException

@Platform(library = "jniopus", cinclude = ["<opus.h>", "<opus_types.h>"])
class Opus {
    class OpusDecoder(sampleRate: Int, channels: Int) : IDecoder {
        private val state: Pointer

        init {
            val error = IntPointer(1)
            error.put(0)
            state = opus_decoder_create(sampleRate, channels, error)
            if (error.get() < 0) {
                throw NativeAudioException("Opus decoder initialization failed with error: ${error.get()}")
            }
        }

        override fun decodeFloat(input: ByteBuffer?, inputSize: Int, output: FloatArray, frameSize: Int): Int {
            val result = opus_decode_float(state, input, inputSize, output, frameSize, 0)
            if (result < 0) {
                throw NativeAudioException("Opus decoding failed with error: $result")
            }
            return result
        }

        override fun decodeShort(input: ByteBuffer?, inputSize: Int, output: ShortArray, frameSize: Int): Int {
            val result = opus_decode(state, input, inputSize, output, frameSize, 0)
            if (result < 0) {
                throw NativeAudioException("Opus decoding failed with error: $result")
            }
            return result
        }

        override fun destroy() {
            opus_decoder_destroy(state)
        }
    }

    companion object {
        const val OPUS_APPLICATION_VOIP = 2048
        const val OPUS_SET_BITRATE_REQUEST = 4002
        const val OPUS_GET_BITRATE_REQUEST = 4003
        const val OPUS_SET_VBR_REQUEST = 4006

        init {
            Loader.load()
        }

        @JvmStatic external fun opus_decoder_get_size(channels: Int): Int
        @JvmStatic external fun opus_decoder_create(fs: Int, channels: Int, error: IntPointer): Pointer
        @JvmStatic external fun opus_decoder_init(@Cast("OpusDecoder*") state: Pointer, fs: Int, channels: Int): Int
        @JvmStatic external fun opus_decode(@Cast("OpusDecoder*") state: Pointer, @Cast("const unsigned char*") data: ByteBuffer?, len: Int, out: ShortArray, frameSize: Int, decodeFec: Int): Int
        @JvmStatic external fun opus_decode_float(@Cast("OpusDecoder*") state: Pointer, @Cast("const unsigned char*") data: ByteBuffer?, len: Int, out: FloatArray, frameSize: Int, decodeFec: Int): Int
        @JvmStatic external fun opus_decoder_destroy(@Cast("OpusDecoder*") state: Pointer)
        @JvmStatic external fun opus_packet_get_bandwidth(@Cast("const unsigned char*") data: ByteArray): Int
        @JvmStatic external fun opus_packet_get_samples_per_frame(@Cast("const unsigned char*") data: ByteArray, fs: Int): Int
        @JvmStatic external fun opus_packet_get_nb_channels(@Cast("const unsigned char*") data: ByteArray): Int
        @JvmStatic external fun opus_packet_get_nb_frames(@Cast("const unsigned char*") packet: ByteArray, len: Int): Int
        @JvmStatic external fun opus_packet_get_nb_samples(@Cast("const unsigned char*") packet: ByteArray, len: Int, fs: Int): Int
        @JvmStatic external fun opus_encoder_get_size(channels: Int): Int
        @JvmStatic external fun opus_encoder_create(fs: Int, channels: Int, application: Int, error: IntPointer): Pointer
        @JvmStatic external fun opus_encoder_init(@Cast("OpusEncoder*") state: Pointer, fs: Int, channels: Int, application: Int): Int
        @JvmStatic external fun opus_encode(@Cast("OpusEncoder*") state: Pointer, @Cast("const short*") pcm: ShortArray, frameSize: Int, @Cast("unsigned char*") data: ByteArray, maxDataBytes: Int): Int
        @JvmStatic external fun opus_encode_float(@Cast("OpusEncoder*") state: Pointer, @Cast("const float*") pcm: FloatArray, frameSize: Int, @Cast("unsigned char*") data: ByteArray, maxDataBytes: Int): Int
        @JvmStatic external fun opus_encoder_destroy(@Cast("OpusEncoder*") state: Pointer)
        @JvmStatic external fun opus_encoder_ctl(@Cast("OpusEncoder*") state: Pointer, request: Int, value: Pointer): Int
        @JvmStatic external fun opus_encoder_ctl(@Cast("OpusEncoder*") state: Pointer, request: Int, @Cast("opus_int32") value: Int): Int
    }
}
