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
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
        binding.btnToggle.text = getString(if (running) R.string.btn_stop else R.string.btn_start)
    }

    private fun setupSliders() {
        binding.sliderEcho.setOnSeekBarChangeListener(simpleListener { v ->
            service?.engine?.echo?.mix = v / 100f
        })
        binding.sliderReverb.setOnSeekBarChangeListener(simpleListener { v ->
            service?.engine?.reverb?.mix = v / 100f
        })
        binding.sliderBass.setOnSeekBarChangeListener(simpleListener { v ->
            service?.engine?.equalizer?.setBass(v)
        })
        binding.sliderMid.setOnSeekBarChangeListener(simpleListener { v ->
            service?.engine?.equalizer?.setMid(v)
        })
        binding.sliderTreble.setOnSeekBarChangeListener(simpleListener { v ->
            service?.engine?.equalizer?.setTreble(v)
        })
    }

    private fun applyAllSlidersToEngine() {
        service?.engine?.echo?.mix = binding.sliderEcho.progress / 100f
        service?.engine?.reverb?.mix = binding.sliderReverb.progress / 100f
        service?.engine?.equalizer?.setBass(binding.sliderBass.progress)
        service?.engine?.equalizer?.setMid(binding.sliderMid.progress)
        service?.engine?.equalizer?.setTreble(binding.sliderTreble.progress)
    }

    private fun simpleListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            onChange(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    override fun onDestroy() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }
}
