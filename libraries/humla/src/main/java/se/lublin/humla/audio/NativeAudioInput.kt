package se.lublin.humla.audio

internal object NativeAudioInput {
    init {
        System.loadLibrary("humla_audioinput")
    }

    @JvmStatic external fun nativeCreate(
        listener: AudioInput.AudioInputListener,
        sampleRate: Int
    ): Long

    @JvmStatic external fun nativeStart(handle: Long)
    @JvmStatic external fun nativeStop(handle: Long)
    @JvmStatic external fun nativeDestroy(handle: Long)
    @JvmStatic external fun nativeIsRecording(handle: Long): Boolean
    @JvmStatic external fun nativeGetSampleRate(handle: Long): Int
    @JvmStatic external fun nativeGetFrameSize(handle: Long): Int
    @JvmStatic external fun nativeQueueEchoReference(pcm: ShortArray, offset: Int, frameCount: Int)
}
