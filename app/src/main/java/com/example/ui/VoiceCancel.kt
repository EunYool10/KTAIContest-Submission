package com.example.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

private val OK_WORDS = listOf("괜찮", "취소", "그만", "멈춰", "해제", "cancel")
private val HELP_WORDS = listOf("도와", "살려", "아파", "119")

/** 띄어쓰기를 없앤 인식 결과로 판단. "안 괜찮아" 는 괜찮다는 뜻이 아닙니다. */
internal fun voiceIntent(phrases: List<String>): Boolean? {
  for (raw in phrases) {
    val s = raw.replace(" ", "").lowercase()
    if (s.contains("안아파") || s.contains("안다쳤")) return true
    if (HELP_WORDS.any { s.contains(it) } || s.contains("안괜찮")) return false
    if (OK_WORDS.any { s.contains(it) }) return true
  }
  return null
}

/** 경보 중에만 마이크로 '괜찮아' / '도와줘' 를 듣습니다. 녹음은 저장하지 않습니다. */
@Composable
fun VoiceCancelEffect(active: Boolean, onOkay: () -> Unit, onHelp: () -> Unit, onListening: (Boolean) -> Unit) {
  val context = LocalContext.current
  val ok by rememberUpdatedState(onOkay)
  val help by rememberUpdatedState(onHelp)
  val listening by rememberUpdatedState(onListening)

  DisposableEffect(active) {
    if (!active || !context.granted(Manifest.permission.RECORD_AUDIO) || !SpeechRecognizer.isRecognitionAvailable(context)) {
      listening(false)
      return@DisposableEffect onDispose { }
    }
    val handler = Handler(Looper.getMainLooper())
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    var done = false
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
      .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
      .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
      .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)

    fun listen() {
      if (!done) runCatching { recognizer.startListening(intent) }
    }

    fun handle(bundle: Bundle?): Boolean {
      val phrases = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return false
      when (voiceIntent(phrases)) {
        true -> { done = true; ok() }
        false -> { done = true; help() }
        null -> return false
      }
      return true
    }

    recognizer.setRecognitionListener(object : RecognitionListener {
      override fun onReadyForSpeech(params: Bundle?) = listening(true)
      override fun onBeginningOfSpeech() = Unit
      override fun onRmsChanged(rmsdB: Float) = Unit
      override fun onBufferReceived(buffer: ByteArray?) = Unit
      override fun onEndOfSpeech() = Unit
      override fun onEvent(eventType: Int, params: Bundle?) = Unit
      override fun onPartialResults(partialResults: Bundle?) {
        handle(partialResults)
      }
      override fun onResults(results: Bundle?) {
        if (!handle(results)) handler.postDelayed({ listen() }, 250)
      }
      override fun onError(error: Int) {
        listening(false)
        if (!done) handler.postDelayed({ listen() }, if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200 else 600)
      }
    })
    listen()

    onDispose {
      done = true
      handler.removeCallbacksAndMessages(null)
      runCatching { recognizer.cancel() }
      runCatching { recognizer.destroy() }
      listening(false)
    }
  }
}
