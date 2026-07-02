package com.endlan.soundcardfx

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.endlan.soundcardfx.audio.AudioEngineService
import com.endlan.soundcardfx.audio.OverlayControlService
import com.endlan.soundcardfx.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var service: AudioEngineService? = null

    /** true kalau Activity ini sedang bind ke service (berarti wajib unbind biar gak leak). */
    private var serviceBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as AudioEngineService.LocalBinder).getService()
            applyAllSlidersToEngine()
            updateStatusUi(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            updateStatusUi(false)
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

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        startOverlayControlIfPermitted()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnToggle.setOnClickListener { toggleEngine() }
        setupSliders()
        updateStatusUi(false)
    }

    override fun onStart() {
        super.onStart()
        // Setiap kali Activity ini muncul lagi (misal habis di-minimize), cek apakah
        // service masih jalan di background. Kalau iya, nempel ke situ (TANPA nyalain
        // service baru) biar status UI sinkron dengan kondisi aslinya.
        attachToRunningServiceIfAny()
        ensureOverlayPermissionThenShowBubble()
    }

    override fun onStop() {
        super.onStop()
        detachFromService()
    }

    private fun attachToRunningServiceIfAny() {
        if (serviceBound) return
        val intent = Intent(this, AudioEngineService::class.java)
        // flag 0 (bukan BIND_AUTO_CREATE) -> cuma connect kalau service-nya udah exist/jalan.
        serviceBound = bindService(intent, connection, 0)
    }

    private fun detachFromService() {
        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }
        service = null
    }

    private fun ensureOverlayPermissionThenShowBubble() {
        if (Settings.canDrawOverlays(this)) {
            startOverlayControlIfPermitted()
        } else {
            Toast.makeText(this, getString(R.string.overlay_permission_prompt), Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun startOverlayControlIfPermitted() {
        if (!Settings.canDrawOverlays(this)) return
        val intent = Intent(this, OverlayControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun toggleEngine() {
        if (service != null) {
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
        if (!serviceBound) {
            serviceBound = bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun stopEngine() {
        stopService(Intent(this, AudioEngineService::class.java))
        detachFromService()
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

        binding.tvEchoValue.text = getString(R.string.percent_format, binding.sliderEcho.value.toInt())
        binding.tvReverbValue.text = getString(R.string.percent_format, binding.sliderReverb.value.toInt())
        binding.tvBassValue.text = formatDb(binding.sliderBass.value.toInt())
        binding.tvMidValue.text = formatDb(binding.sliderMid.value.toInt())
        binding.tvTrebleValue.text = formatDb(binding.sliderTreble.value.toInt())
    }

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
        detachFromService()
        super.onDestroy()
    }
}
