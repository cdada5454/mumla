package se.lublin.humla.audio

interface IAudioMixer<T, U> {
    fun mix(sources: Collection<IAudioMixerSource<T>>, buffer: U, bufferOffset: Int, bufferLength: Int)
}
