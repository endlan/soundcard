package com.endlan.soundcardfx.audio.effects

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Biquad filter generik (formula dari Audio EQ Cookbook - Robert Bristow-Johnson).
 * Dipakai untuk bikin low-shelf, peaking, dan high-shelf filter.
 */
private class Biquad {
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f
    private var x1 = 0f; private var x2 = 0f
    private var y1 = 0f; private var y2 = 0f

    fun setLowShelf(sampleRate: Int, freq: Float, gainDb: Float) {
        val a = 10f.pow(gainDb / 40f)
        val w0 = 2f * PI.toFloat() * freq / sampleRate
        val cosW0 = cos(w0); val sinW0 = sin(w0)
        val alpha = sinW0 / 2f * sqrt((a + 1f / a) * (1f / 0.9f - 1f) + 2f)
        val twoSqrtAAlpha = 2f * sqrt(a) * alpha

        val b0n = a * ((a + 1f) - (a - 1f) * cosW0 + twoSqrtAAlpha)
        val b1n = 2f * a * ((a - 1f) - (a + 1f) * cosW0)
        val b2n = a * ((a + 1f) - (a - 1f) * cosW0 - twoSqrtAAlpha)
        val a0n = (a + 1f) + (a - 1f) * cosW0 + twoSqrtAAlpha
        val a1n = -2f * ((a - 1f) + (a + 1f) * cosW0)
        val a2n = (a + 1f) + (a - 1f) * cosW0 - twoSqrtAAlpha
        normalize(b0n, b1n, b2n, a0n, a1n, a2n)
    }

    fun setHighShelf(sampleRate: Int, freq: Float, gainDb: Float) {
        val a = 10f.pow(gainDb / 40f)
        val w0 = 2f * PI.toFloat() * freq / sampleRate
        val cosW0 = cos(w0); val sinW0 = sin(w0)
        val alpha = sinW0 / 2f * sqrt((a + 1f / a) * (1f / 0.9f - 1f) + 2f)
        val twoSqrtAAlpha = 2f * sqrt(a) * alpha

        val b0n = a * ((a + 1f) + (a - 1f) * cosW0 + twoSqrtAAlpha)
        val b1n = -2f * a * ((a - 1f) + (a + 1f) * cosW0)
        val b2n = a * ((a + 1f) + (a - 1f) * cosW0 - twoSqrtAAlpha)
        val a0n = (a + 1f) - (a - 1f) * cosW0 + twoSqrtAAlpha
        val a1n = 2f * ((a - 1f) - (a + 1f) * cosW0)
        val a2n = (a + 1f) - (a - 1f) * cosW0 - twoSqrtAAlpha
        normalize(b0n, b1n, b2n, a0n, a1n, a2n)
    }

    fun setPeaking(sampleRate: Int, freq: Float, gainDb: Float, q: Float) {
        val a = 10f.pow(gainDb / 40f)
        val w0 = 2f * PI.toFloat() * freq / sampleRate
        val cosW0 = cos(w0); val sinW0 = sin(w0)
        val alpha = sinW0 / (2f * q)

        val b0n = 1f + alpha * a
        val b1n = -2f * cosW0
        val b2n = 1f - alpha * a
        val a0n = 1f + alpha / a
        val a1n = -2f * cosW0
        val a2n = 1f - alpha / a
        normalize(b0n, b1n, b2n, a0n, a1n, a2n)
    }

    private fun normalize(b0n: Float, b1n: Float, b2n: Float, a0n: Float, a1n: Float, a2n: Float) {
        b0 = b0n / a0n; b1 = b1n / a0n; b2 = b2n / a0n
        a1 = a1n / a0n; a2 = a2n / a0n
    }

    fun process(x0: Float): Float {
        val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x0
        y2 = y1; y1 = y0
        return y0
    }
}

/**
 * Equalizer 3-band sederhana: bass (low-shelf), mid (peaking), treble (high-shelf).
 * Gain per band dalam rentang -12dB s/d +12dB, kontrol via [setBass]/[setMid]/[setTreble]
 * dengan nilai 0..100 (50 = flat/0dB).
 */
class Equalizer(private val sampleRate: Int) {
    private val bassFilter = Biquad()
    private val midFilter = Biquad()
    private val trebleFilter = Biquad()

    companion object {
        private const val BASS_FREQ = 150f
        private const val MID_FREQ = 1000f
        private const val TREBLE_FREQ = 6000f
        private const val MAX_GAIN_DB = 12f
    }

    init {
        setBass(50); setMid(50); setTreble(50)
    }

    private fun sliderToDb(v: Int): Float = ((v - 50) / 50f) * MAX_GAIN_DB

    fun setBass(v: Int) = bassFilter.setLowShelf(sampleRate, BASS_FREQ, sliderToDb(v))
    fun setMid(v: Int) = midFilter.setPeaking(sampleRate, MID_FREQ, sliderToDb(v), 0.8f)
    fun setTreble(v: Int) = trebleFilter.setHighShelf(sampleRate, TREBLE_FREQ, sliderToDb(v))

    fun process(sample: Float): Float {
        var s = bassFilter.process(sample)
        s = midFilter.process(s)
        s = trebleFilter.process(s)
        return s
    }
}
