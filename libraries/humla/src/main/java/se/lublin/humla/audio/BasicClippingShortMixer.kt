package se.lublin.humla.audio

class BasicClippingShortMixer : IAudioMixer<FloatArray, ShortArray> {
    override fun mix(
        sources: Collection<IAudioMixerSource<FloatArray>>,
        buffer: ShortArray,
        bufferOffset: Int,
        bufferLength: Int,
    ) {
        for (index in 0 until bufferLength) {
            var mix = 0f
            for (source in sources) {
                mix += source.getSamples()[index]
            }
            if (mix > 1) {
                mix = 1f
            } else if (mix < -1) {
                mix = -1f
            }
            buffer[index + bufferOffset] = (mix * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
