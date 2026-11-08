package com.example

import com.example.engine.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** version.json 을 믿을 만할 때만 받아들이는지 확인합니다. (org.json 때문에 Robolectric 으로 실행) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateInfoTest {
  private val apk = "https://github.com/EunYool10/KTAIContest-Submission/releases/download/v9.0.0/ai-shock-alert.apk"

  private fun json(code: Any = 9, name: String = "9.0.0", apkUrl: String = apk, extra: String = "") =
    """{"versionCode":$code,"versionName":"$name","date":"2026-11-08","apk":"$apkUrl","notes":["좋아졌어요","빨라졌어요"]$extra}"""

  @Test
  fun `정상적인 버전 파일을 읽는다`() {
    val info = assertNotNullAndGet(UpdateInfo.parse(json()))
    assertEquals(9, info.versionCode)
    assertEquals("9.0.0", info.versionName)
    assertEquals(listOf("좋아졌어요", "빨라졌어요"), info.notes)
    assertEquals(UpdateInfo.SITE, info.pageUrl)
  }

  @Test
  fun `우리 저장소가 아닌 내려받기 주소는 거절한다`() {
    assertNull(UpdateInfo.parse(json(apkUrl = "https://evil.example.com/ai-shock-alert.apk")))
    assertNull(UpdateInfo.parse(json(apkUrl = "https://github.com/someone/else/releases/download/v1/ai-shock-alert.apk")))
    assertNull(UpdateInfo.parse(json(apkUrl = UpdateInfo.RELEASES + "../../evil.apk")))
    assertNull(UpdateInfo.parse(json(apkUrl = UpdateInfo.RELEASES + "v9.0.0/readme.txt")))
  }

  @Test
  fun `다른 사이트로 보내는 page 는 거절한다`() {
    assertNull(UpdateInfo.parse(json(extra = ""","page":"https://evil.example.com/"""")))
  }

  @Test
  fun `깨진 파일이나 빠진 값은 무시한다`() {
    assertNull(UpdateInfo.parse("not json"))
    assertNull(UpdateInfo.parse(json(code = 0)))
    assertNull(UpdateInfo.parse(json(code = "\"abc\"")))
    assertNull(UpdateInfo.parse(json(name = "")))
  }

  private fun assertNotNullAndGet(info: UpdateInfo?): UpdateInfo {
    assertNotNull(info)
    return info!!
  }
}
