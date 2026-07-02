package com.endlan.soundcardfx.audio.effects

/**
 * Efek echo sederhana: delay line dengan feedback.
 * mix 0f = tidak ada efek (dry 100%), mix 1f = efek penuh.
 */
class Echo(sampleRate: Int, delayMs: Int = 280) {
    private val delaySamples = (sampleRate * delayMs / 1000f).toInt().coerceAtLeast(1)
    private val buffer = FloatArray(delaySamples)
    private var writeIndex = 0

    @Volatile var mix: Float = 0f       // proporsi sinyal echo yang dicampur ke output
    @Volatile var feedback: Float = 0.35f // seberapa besar echo mengulang ke dirinya sendiri

    fun process(input: Float): Float {
        val delayed = buffer[writeIndex]
        val toStore = input + delayed * feedback
        buffer[writeIndex] = toStore
        writeIndex = (writeIndex + 1) % delaySamples
        return input * (1f - mix) + delayed * mix
    }

    fun reset() {
        buffer.fill(0f)
        writeIndex = 0
    }
}
