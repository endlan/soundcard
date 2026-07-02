package com.endlan.soundcardfx

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.endlan.soundcardfx.audio.AudioEngineService
import com.endlan.soundcardfx.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var service: AudioEngineService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as AudioEngineService.LocalBinder).getService()
            bound = true
            applyAllSlidersToEngine()
            updateStatusUi(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startEngine()
        } else {
            Toast.makeText(this, getString(R.string.error_no_permission), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnToggle.setOnClickListener { toggleEngine() }
        setupSliders()
        updateStatusUi(false)
    }

    private fun toggleEngine() {
        if (bound) {
            stopEngine()
        } else {
            ensureMicPermissionThenStart()
        }
    }

    private fun ensureMicPermissionThenStart() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            startEngine()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startEngine() {
        val intent = Intent(this, AudioEngineService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun stopEngine() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        stopService(Intent(this, AudioEngineService::class.java))
        service = null
        updateStatusUi(false)
    }

    private fun updateStatusUi(running: Boolean) {
        binding.tvStatus.text = getString(if (running) R.string.status_running else R.string.status_idle)
        binding.tvStatus.setTextColor(
            ContextCompat.getColor(this, if (running) R.color.power_on else R.color.text_secondary)
        )
        binding.btnToggle.setBackgroundResource(
            if (running) R.drawable.power_ring_on else R.drawable.power_ring_off
        )
        binding.ivPowerIcon.imageTintList = ContextCompat.getColorStateList(
            this, if (running) R.color.power_on else R.color.power_off
        )
    }

    private fun setupSliders() {
        binding.sliderEcho.addOnChangeListener { _, value, _ ->
            val v = value.toInt()
            binding.tvEchoValue.text = getString(R.string.percent_format, v)
            service?.engine?.echo?.mix = v / 100f
        }
        binding.sliderReverb.addOnChangeListener { _, value, _ ->
            val v = value.toInt()
            binding.tvReverbValue.text = getString(R.string.percent_format, v)
            service?.engine?.reverb?.mix = v / 100f
        }
        binding.sliderBass.addOnChangeListener { _, value, _ ->
            val v = value.toInt()
            binding.tvBassValue.text = formatDb(v)
            service?.engine?.equalizer?.setBass(v)
        }
        binding.sliderMid.addOnChangeListener { _, value, _ ->
            val v = value.toInt()
            binding.tvMidValue.text = formatDb(v)
            service?.engine?.equalizer?.setMid(v)
        }
        binding.sliderTreble.addOnChangeListener { _, value, _ ->
            val v = value.toInt()
            binding.tvTrebleValue.text = formatDb(v)
            service?.engine?.equalizer?.setTreble(v)
        }

        // set nilai awal biar readout kebaca bener sebelum ada interaksi
        binding.tvEchoValue.text = getString(R.string.percent_format, binding.sliderEcho.value.toInt())
        binding.tvReverbValue.text = getString(R.string.percent_format, binding.sliderReverb.value.toInt())
        binding.tvBassValue.text = formatDb(binding.sliderBass.value.toInt())
        binding.tvMidValue.text = formatDb(binding.sliderMid.value.toInt())
        binding.tvTrebleValue.text = formatDb(binding.sliderTreble.value.toInt())
    }

    /** Slider EQ 0..100 (50 = flat/0dB) diformat jadi label dB buat readout, misal "+6dB" / "-3dB" */
    private fun formatDb(sliderValue: Int): String {
        val db = ((sliderValue - 50) / 50f) * 12f
        val rounded = Math.round(db)
        return when {
            rounded > 0 -> "+${rounded}dB"
            rounded < 0 -> "${rounded}dB"
            else -> "0dB"
        }
    }

    private fun applyAllSlidersToEngine() {
        service?.engine?.echo?.mix = binding.sliderEcho.value / 100f
        service?.engine?.reverb?.mix = binding.sliderReverb.value / 100f
        service?.engine?.equalizer?.setBass(binding.sliderBass.value.toInt())
        service?.engine?.equalizer?.setMid(binding.sliderMid.value.toInt())
        service?.engine?.equalizer?.setTreble(binding.sliderTreble.value.toInt())
    }

    override fun onDestroy() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }
}
