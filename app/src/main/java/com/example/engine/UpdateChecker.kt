package com.example.engine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

/** 설치 사이트의 version.json 한 개 */
data class UpdateInfo(
  val versionCode: Int,
  val versionName: String,
  val date: String,
  val apkUrl: String,
  val pageUrl: String,
  val notes: List<String>,
) {
  companion object {
    const val SITE = "https://eunyool10.github.io/KTAIContest-Submission/"
    const val RELEASES = "https://github.com/EunYool10/KTAIContest-Submission/releases/download/"

    /** 내용을 검사해서 믿을 수 있는 값만 돌려줍니다. 내려받기 주소는 우리 저장소 릴리스만 허용합니다. */
    fun parse(json: String): UpdateInfo? = try {
      val o = JSONObject(json)
      val code = o.optInt("versionCode", -1)
      val name = o.optString("versionName").trim()
      val apk = o.optString("apk").trim()
      val page = o.optString("page", SITE).trim()
      val notes = o.optJSONArray("notes")
      when {
        code <= 0 || name.isEmpty() || name.length > 20 -> null
        !apk.startsWith(RELEASES) || !apk.endsWith(".apk") || apk.contains("..") -> null
        !page.startsWith(SITE) -> null
        else -> UpdateInfo(
          code, name, o.optString("date").trim().take(20), apk, page,
          buildList {
            if (notes != null) for (i in 0 until min(notes.length(), 8)) {
              notes.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it.take(140)) }
            }
          },
        )
      }
    } catch (_: Exception) {
      null
    }
  }
}

enum class UpdateCheckResult { UPDATE, LATEST, FAILED }

/**
 * 새 버전이 있는지 설치 사이트의 version.json 으로 확인합니다.
 * 보내는 것은 파일 요청 하나뿐이고, 센서 값이나 개인 정보는 보내지 않습니다.
 */
class UpdateChecker(context: Context, private val scope: CoroutineScope, private val currentCode: Int) {
  private val prefs = context.getSharedPreferences("update_pref", Context.MODE_PRIVATE)
  private val mutex = Mutex()

  private val _available = MutableStateFlow(cached())
  /** 지금 버전보다 새 버전이 있으면 그 정보 */
  val available: StateFlow<UpdateInfo?> = _available.asStateFlow()

  private val _dismissed = MutableStateFlow(prefs.getInt(K_DISMISSED, 0))
  /** '나중에'를 누른 버전 (그 버전까지는 팝업을 다시 띄우지 않음) */
  val dismissedCode: StateFlow<Int> = _dismissed.asStateFlow()

  private val _checking = MutableStateFlow(false)
  val checking: StateFlow<Boolean> = _checking.asStateFlow()

  private fun cached(): UpdateInfo? =
    prefs.getString(K_JSON, null)?.let(UpdateInfo::parse)?.takeIf { it.versionCode > currentCode }

  /** [force] 가 아니면 6시간에 한 번만 실제로 확인합니다. */
  suspend fun check(force: Boolean = false): UpdateCheckResult = mutex.withLock {
    val now = System.currentTimeMillis()
    if (!force && now - prefs.getLong(K_LAST, 0L) < INTERVAL_MS) {
      return if (_available.value != null) UpdateCheckResult.UPDATE else UpdateCheckResult.LATEST
    }
    _checking.value = true
    try {
      val body = withContext(Dispatchers.IO) { fetch() } ?: return UpdateCheckResult.FAILED
      val info = UpdateInfo.parse(body) ?: return UpdateCheckResult.FAILED
      prefs.edit().putLong(K_LAST, now).putString(K_JSON, body).apply()
      _available.value = info.takeIf { it.versionCode > currentCode }
      return if (_available.value != null) UpdateCheckResult.UPDATE else UpdateCheckResult.LATEST
    } finally {
      _checking.value = false
    }
  }

  fun checkIfDue() {
    scope.launch { check(force = false) }
  }

  fun dismiss(code: Int) {
    prefs.edit().putInt(K_DISMISSED, code).apply()
    _dismissed.value = code
  }

  /** 알림은 버전마다 한 번만 보냅니다. */
  fun claimNotification(info: UpdateInfo): Boolean {
    if (info.versionCode <= prefs.getInt(K_NOTIFIED, 0)) return false
    prefs.edit().putInt(K_NOTIFIED, info.versionCode).apply()
    return true
  }

  private fun fetch(): String? {
    val conn = URL(UpdateInfo.SITE + "version.json?t=" + System.currentTimeMillis() / 60_000).openConnection() as HttpURLConnection
    return try {
      conn.connectTimeout = 8_000
      conn.readTimeout = 8_000
      conn.useCaches = false
      conn.setRequestProperty("Cache-Control", "no-cache")
      if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
      conn.inputStream.use { input ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (true) {
          val n = input.read(buf)
          if (n < 0) break
          out.write(buf, 0, n)
          if (out.size() > 64 * 1024) return null
        }
        String(out.toByteArray(), Charsets.UTF_8)
      }
    } catch (_: Exception) {
      null
    } finally {
      conn.disconnect()
    }
  }

  private companion object {
    const val INTERVAL_MS = 6 * 60 * 60 * 1000L
    const val K_LAST = "last_check"
    const val K_JSON = "last_json"
    const val K_DISMISSED = "dismissed_code"
    const val K_NOTIFIED = "notified_code"
  }
}
