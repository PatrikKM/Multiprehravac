package com.example.multiprehrava

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log

object AudioFxManager {
    private const val TAG = "AudioFxManager"

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var virtualizer: Virtualizer? = null

    private var currentSessionId: Int = 0

    var isEnabled: Boolean = false
        private set

    var currentPreset: Short = -1
    val bandLevels = mutableMapOf<Short, Short>()
    var bassBoostStrength: Short = 0
    var loudnessGainMb: Int = 0
    var virtualizerStrength: Short = 0

    fun init(sessionId: Int) {
        if (sessionId == 0) return
        if (sessionId == currentSessionId && equalizer != null) return

        release()
        currentSessionId = sessionId

        try {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = isEnabled
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Equalizer: ${e.localizedMessage}")
        }

        try {
            bassBoost = BassBoost(0, sessionId).apply {
                enabled = isEnabled
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating BassBoost: ${e.localizedMessage}")
        }

        try {
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                enabled = isEnabled
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating LoudnessEnhancer: ${e.localizedMessage}")
        }

        try {
            virtualizer = Virtualizer(0, sessionId).apply {
                enabled = isEnabled
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Virtualizer: ${e.localizedMessage}")
        }

        applyState()
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            loudnessEnhancer?.enabled = enabled
            virtualizer?.enabled = enabled
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling AudioFX: ${e.localizedMessage}")
        }
    }

    fun getNumberOfBands(): Short {
        return try {
            equalizer?.numberOfBands ?: 5
        } catch (e: Exception) {
            5
        }
    }

    fun getBandLevelRange(): Pair<Short, Short> {
        return try {
            val range = equalizer?.bandLevelRange
            if (range != null && range.size >= 2) Pair(range[0], range[1]) else Pair(-1500, 1500)
        } catch (e: Exception) {
            Pair(-1500, 1500)
        }
    }

    fun getCenterFreq(band: Short): Int {
        return try {
            equalizer?.getCenterFreq(band) ?: (1000000 * (band + 1))
        } catch (e: Exception) {
            1000000
        }
    }

    fun setBandLevel(band: Short, level: Short) {
        bandLevels[band] = level
        currentPreset = -1
        try {
            equalizer?.setBandLevel(band, level)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting band level: ${e.localizedMessage}")
        }
    }

    fun getBandLevel(band: Short): Short {
        return try {
            equalizer?.getBandLevel(band) ?: bandLevels[band] ?: 0
        } catch (e: Exception) {
            bandLevels[band] ?: 0
        }
    }

    fun getPresets(): List<String> {
        val list = mutableListOf<String>()
        val eq = equalizer ?: return list
        try {
            val numPresets = eq.numberOfPresets
            for (i in 0 until numPresets) {
                list.add(eq.getPresetName(i.toShort()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting presets: ${e.localizedMessage}")
        }
        return list
    }

    fun usePreset(presetIndex: Short) {
        currentPreset = presetIndex
        try {
            equalizer?.usePreset(presetIndex)
            val numBands = getNumberOfBands()
            for (i in 0 until numBands) {
                val b = i.toShort()
                bandLevels[b] = equalizer?.getBandLevel(b) ?: 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error using preset: ${e.localizedMessage}")
        }
    }

    fun setBassBoost(strength: Short) {
        bassBoostStrength = strength
        try {
            if (bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(strength)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting bass boost: ${e.localizedMessage}")
        }
    }

    fun setLoudnessEnhancer(gainMb: Int) {
        loudnessGainMb = gainMb
        try {
            loudnessEnhancer?.setTargetGain(gainMb)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting loudness enhancer: ${e.localizedMessage}")
        }
    }

    fun setVirtualizer(strength: Short) {
        virtualizerStrength = strength
        try {
            if (virtualizer?.strengthSupported == true) {
                virtualizer?.setStrength(strength)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting virtualizer: ${e.localizedMessage}")
        }
    }

    private fun applyState() {
        try {
            equalizer?.enabled = isEnabled
            bassBoost?.enabled = isEnabled
            loudnessEnhancer?.enabled = isEnabled
            virtualizer?.enabled = isEnabled

            if (currentPreset >= 0) {
                equalizer?.usePreset(currentPreset)
            } else {
                bandLevels.forEach { (band, level) ->
                    equalizer?.setBandLevel(band, level)
                }
            }

            if (bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(bassBoostStrength)
            }

            loudnessEnhancer?.setTargetGain(loudnessGainMb)

            if (virtualizer?.strengthSupported == true) {
                virtualizer?.setStrength(virtualizerStrength)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying AudioFX state: ${e.localizedMessage}")
        }
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            loudnessEnhancer?.release()
            virtualizer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioFX: ${e.localizedMessage}")
        }
        equalizer = null
        bassBoost = null
        loudnessEnhancer = null
        virtualizer = null
        currentSessionId = 0
    }
}
