package com.endlan.soundcardfx.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.endlan.soundcardfx.audio.effects.Echo
import com.endlan.soundcardfx.audio.effects.Equalizer
import com.endlan.soundcardfx.audio.effects.Reverb

/**
 * Mesin audio real-time: mic -> EQ -> Echo -> Reverb -> speaker/output.
 * Jalan di thread terpisah supaya UI thread gak keblokir.
 */
class AudioEngine {

    companion object {
        private const val SAMPLE_RATE = 44100
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var processingThread: Thread? = null

    @Volatile private var isRunning = false

    val equalizer = Equalizer(SAMPLE_RATE)
    val echo = Echo(SAMPLE_RATE)
    val reverb = Reverb(SAMPLE_RATE)

    /** @return true kalau berhasil start, false kalau gagal (misal permission belum ada / device gak support) */
    fun start(): Boolean {
        if (isRunning) return true

        val minRecordBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minRecordBuf <= 0) return false
        val recordBufSize = minRecordBuf * 2

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                recordBufSize
            )
        } catch (e: SecurityException) {
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        val minTrackBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minTrackBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioRecord = record
        audioTrack = track
        isRunning = true

        record.startRecording()
        track.play()

        processingThread = Thread { processingLoop(recordBufSize) }.apply {
            name = "AudioEngine-Processing"
            priority = Thread.MAX_PRIORITY
            start()
        }
        return true
    }

    private fun processingLoop(bufSizeBytes: Int) {
        val shortBufSize = bufSizeBytes / 2
        val pcmBuffer = ShortArray(shortBufSize)
        val record = audioRecord ?: return
        val track = audioTrack ?: return

        while (isRunning) {
            val readCount = record.read(pcmBuffer, 0, pcmBuffer.size)
            if (readCount <= 0) continue

            for (i in 0 until readCount) {
                var sample = pcmBuffer[i] / 32768f

                sample = equalizer.process(sample)
                sample = echo.process(sample)
                sample = reverb.process(sample)

                // clamp biar gak clipping/overflow
                sample = sample.coerceIn(-1f, 1f)
                pcmBuffer[i] = (sample * 32767f).toInt().toShort()
            }

            track.write(pcmBuffer, 0, readCount)
        }
    }

    fun stop() {
        isRunning = false
        processingThread?.join(500)
        processingThread = null

        audioRecord?.apply {
            try { stop() } catch (e: IllegalStateException) { /* sudah berhenti */ }
            release()
        }
        audioRecord = null

        audioTrack?.apply {
            try { stop() } catch (e: IllegalStateException) { /* sudah berhenti */ }
            release()
        }
        audioTrack = null

        echo.reset()
    }

    fun isActive() = isRunning
}
