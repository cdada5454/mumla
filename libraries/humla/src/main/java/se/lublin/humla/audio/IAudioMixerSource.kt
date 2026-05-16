package se.lublin.humla.audio

interface IAudioMixerSource<T> {
    fun getSamples(): T
    fun getNumSamples(): Int
}
