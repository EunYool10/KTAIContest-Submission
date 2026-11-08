package com.example.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/** 경보음, 진동, 음성 안내. 메인 스레드에서 호출하세요. */
class Alarm(private val context: Context) {
  private var player: MediaPlayer? = null
  private var tts: TextToSpeech? = null
  private var ttsReady = false
  private var pendingSpeech: String? = null

  private val alarmAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

  private val vibrator: Vibrator? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  }

  fun startSound() {
    if (player != null) return
    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
      ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
      ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      ?: return
    try {
      player = MediaPlayer().apply {
        setAudioAttributes(alarmAttributes)
        setDataSource(context, uri)
        isLooping = true
        prepare()
        start()
      }
    } catch (e: Exception) {
      Log.w(TAG, "경보음 재생 실패", e)
      player?.release()
      player = null
    }
  }

  fun startVibration() {
    val v = vibrator ?: return
    if (!v.hasVibrator()) return
    try {
      val pattern = longArrayOf(0, 700, 500)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        v.vibrate(
          VibrationEffect.createWaveform(pattern, 0),
          android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_ALARM),
        )
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        @Suppress("DEPRECATION")
        v.vibrate(VibrationEffect.createWaveform(pattern, 0), alarmAttributes)
      } else {
        @Suppress("DEPRECATION")
        v.vibrate(pattern, 0)
      }
    } catch (e: Exception) {
      Log.w(TAG, "진동 실패", e)
    }
  }

  fun pulse() {
    val v = vibrator ?: return
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))
      } else {
        @Suppress("DEPRECATION")
        v.vibrate(longArrayOf(0, 250, 150, 250), -1)
      }
    } catch (_: Exception) {
    }
  }

  fun stop() {
    player?.let {
      runCatching { if (it.isPlaying) it.stop() }
      it.release()
    }
    player = null
    runCatching { vibrator?.cancel() }
    runCatching { tts?.stop() }
  }

  fun speak(text: String) {
    val engine = tts
    if (engine == null) {
      pendingSpeech = text
      tts = TextToSpeech(context.applicationContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
          tts?.language = Locale.KOREAN
          tts?.setAudioAttributes(alarmAttributes)
          pendingSpeech?.let { say(it) }
        }
        pendingSpeech = null
      }
      return
    }
    if (ttsReady) say(text) else pendingSpeech = text
  }

  private fun say(text: String) {
    runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guard") }
  }

  fun shutdown() {
    stop()
    runCatching { tts?.shutdown() }
    tts = null
    ttsReady = false
  }

  private companion object {
    const val TAG = "Alarm"
  }
}
