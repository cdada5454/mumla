package se.lublin.humla.audio.encoder

internal object NativeOpusEncoder {
    init {
        System.loadLibrary("humla_audioinput")
    }

    @JvmStatic external fun nativeCreate(sampleRate: Int, channels: Int, bitrate: Int): Long
    @JvmStatic external fun nativeEncode(
        handle: Long,
        input: ShortArray,
        frameSize: Int,
        output: ByteArray,
        maxOutputSize: Int
    ): Int
    @JvmStatic external fun nativeGetBitrate(handle: Long): Int
    @JvmStatic external fun nativeDestroy(handle: Long)
}
