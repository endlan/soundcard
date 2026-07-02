package com.endlan.soundcardfx.audio.effects

/** Comb filter dengan feedback, salah satu building block Schroeder reverb. */
private class CombFilter(delaySamples: Int, private val feedback: Float) {
    private val buffer = FloatArray(delaySamples.coerceAtLeast(1))
    private var index = 0

    fun process(input: Float): Float {
        val output = buffer[index]
        buffer[index] = input + output * feedback
        index = (index + 1) % buffer.size
        return output
    }
}

/** All-pass filter, dipakai buat "menghaluskan" hasil comb filter. */
private class AllPassFilter(delaySamples: Int, private val gain: Float) {
    private val buffer = FloatArray(delaySamples.coerceAtLeast(1))
    private var index = 0

    fun process(input: Float): Float {
        val bufOut = buffer[index]
        val output = -gain * input + bufOut
        buffer[index] = input + bufOut * gain
        index = (index + 1) % buffer.size
        return output
    }
}

/**
 * Reverb ala Schroeder: 4 comb filter paralel lalu 2 allpass filter berurutan.
 * mix 0f = dry penuh, mix 1f = wet penuh.
 */
class Reverb(sampleRate: Int) {
    @Volatile var mix: Float = 0f

    // Delay time comb filter dalam ms (nilai klasik Schroeder, di-scale ke sampleRate)
    private val combs = listOf(29.7f, 37.1f, 41.1f, 43.7f).map {
        CombFilter((sampleRate * it / 1000f).toInt(), 0.77f)
    }
    private val allpasses = listOf(5.0f, 1.7f).map {
        AllPassFilter((sampleRate * it / 1000f).toInt(), 0.7f)
    }

    fun process(input: Float): Float {
        var wet = 0f
        for (c in combs) wet += c.process(input)
        wet /= combs.size
        for (ap in allpasses) wet = ap.process(wet)
        return input * (1f - mix) + wet * mix
    }
}
