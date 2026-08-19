package com.escape99.minimalpiano

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    companion object {
        init {
            System.loadLibrary("minimalpiano")
        }
    }

    private external fun startEngine()
    private external fun stopEngine()
    private external fun loadSound(soundId: Int, data: ByteArray)
    private external fun playSound(soundId: Int)

    private lateinit var keyPreferences: SharedPreferences
    private var keyCount = 0
    private var keySpan = 0
    private var displayWidth = 0
    private var keyPosition = 0
    private val minSpan = 7
    private lateinit var scrollLeft: ImageButton
    private lateinit var scrollRight: ImageButton
    private lateinit var decreaseSpan: ImageButton
    private lateinit var increaseSpan: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestInitialKeyPreferences()
        setFullscreenMode()
        setContentView(R.layout.activity_main)
        setInitialKeyParams()
        setKeyWidths()
        setButtonActions()
        setButtonPermissions()
        initializeAudio()
        Handler(Looper.getMainLooper()).postDelayed({
            setKeyPosition()
        }, 100)
    }

    override fun onResume() {
        super.onResume()
        startEngine()
    }

    override fun onPause() {
        super.onPause()
        stopEngine()
    }

    private fun requestInitialKeyPreferences() {
        keyPreferences = getPreferences(Context.MODE_PRIVATE) ?: return
        val defaultKeySpan = resources.getInteger(R.integer.default_key_span)
        val defaultKeyPosition = resources.getInteger(R.integer.default_key_position)
        keySpan = keyPreferences.getInt("key_span", defaultKeySpan)
        keyPosition = keyPreferences.getInt("key_position", defaultKeyPosition)
    }

    private fun setFullscreenMode() {
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        // Configure the behavior of the hidden system bars
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Hide both the status bar and the navigation bar
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setInitialKeyParams() {
        val keys: ConstraintLayout = findViewById(R.id.keys)
        keyCount = keys.children.filter { it.tag == "whiteKey" }.count()
        displayWidth = Resources.getSystem().displayMetrics.widthPixels
        scrollLeft = findViewById(R.id.scroll_left)
        scrollRight = findViewById(R.id.scroll_right)
        decreaseSpan = findViewById(R.id.decrease_span)
        increaseSpan = findViewById(R.id.increase_span)
    }

    private fun updateKeyPreferences(valueName: String, newValue: Int) {
        keyPreferences.edit {
            putInt(valueName, newValue)
        }
    }

    private fun setButtonActions() {
        val scrollBar: LockableScrollView = findViewById(R.id.scrollBar)

        scrollLeft.setOnClickListener {
            if (keyPosition > 0 ) {
                keyPosition -= 1
                scrollBar.smoothScrollBy(- (displayWidth / keySpan + 2), 0)
                setButtonPermissions()
                updateKeyPreferences("key_position", keyPosition)
            }
        }
        scrollRight.setOnClickListener {
            if (keyPosition < keyCount - keySpan) {
                keyPosition += 1
                scrollBar.smoothScrollBy(displayWidth / keySpan + 2, 0)
                setButtonPermissions()
                updateKeyPreferences("key_position", keyPosition)
            }
        }
        increaseSpan.setOnClickListener {
            if (keySpan > minSpan) {
                keySpan -= 1
                setKeyWidths()
                setKeyPosition()
                setButtonPermissions()
                updateKeyPreferences("key_span", keySpan)
            }
        }
        decreaseSpan.setOnClickListener {
            if (keySpan < keyCount) {
                keySpan += 1
                setKeyWidths()
                setKeyPosition()
                setButtonPermissions()
                updateKeyPreferences("key_span", keySpan)
            }
        }
    }

    private fun setButtonPermissions() {
        scrollLeft.isEnabled = keyPosition > 0
        scrollRight.isEnabled = keyPosition < keyCount - keySpan
        increaseSpan.isEnabled = keySpan > minSpan
        decreaseSpan.isEnabled = keySpan < keyCount
    }

    private fun setKeyWidths() {
        val keys: ConstraintLayout = findViewById(R.id.keys)
        for (key in keys.children) {
            val params = key.layoutParams
            if (key.tag == "whiteKey") {
                params.width = displayWidth / keySpan - 2   // 2 = key border
            }
            if (key.tag == "blackKey") {
                params.width = (displayWidth / keySpan * 0.65).toInt()
            }
            key.layoutParams = params
        }
    }

    private fun setKeyPosition() {
        // val contentWidth = (displayWidth / keySpan.toFloat() * keyCount).toInt()
        // println("Content width: $contentWidth")
        val scrollBar: LockableScrollView = findViewById(R.id.scrollBar)
        scrollBar.scrollTo((displayWidth / keySpan + 2) * keyPosition, 0)
    }

    private fun initializeAudio() {
        // Load sounds from keys defined in xml
        val keys: ConstraintLayout = findViewById(R.id.keys)
        for (key in keys.children) {
            if (key.tag == "whiteKey" || key.tag == "blackKey") {
                val keyName = resources.getResourceEntryName(key.id)
                val soundId = resources.getIdentifier("acoustic_grand_piano_$keyName", "raw", packageName)

                val bytes = resources.openRawResource(soundId).use { it.readBytes() }
                loadSound(soundId, bytes)

                key.setOnTouchListener { view, event ->
                    when(event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            playSound(soundId)
                        }
                        MotionEvent.ACTION_MOVE -> { }
                        MotionEvent.ACTION_UP -> {
                            view.performClick()
                        }
                    }
                    false
                }
            }
        }
    }

}