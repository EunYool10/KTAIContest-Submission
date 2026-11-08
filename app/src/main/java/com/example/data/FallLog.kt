package com.example.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** 기록 종류. 이전 버전 기록은 빈 문자열이라 [LogKind.of] 가 내용으로 추정합니다. */
enum class LogKind(val key: String, val title: String) {
  EMERGENCY("emergency", "큰 충격"),
  WARNING("warning", "충격 주의"),
  SOS("sos", "직접 도움 요청");

  companion object {
    fun of(log: FallLog): LogKind =
      entries.firstOrNull { it.key == log.kind }
        ?: if (log.aiResult.contains("안내")) WARNING else EMERGENCY
  }
}

@Entity(tableName = "fall_logs")
data class FallLog(
  @PrimaryKey(autoGenerate = true) val id: Int = 0,
  val timestamp: Long = System.currentTimeMillis(),
  val latitude: Double = 0.0,
  val longitude: Double = 0.0,
  /** AI 또는 기기 규칙의 판정 ("정상", "낙상 의심", "응급상황 가능성 높음") */
  val aiResult: String = "",
  /** 사용자가 어떻게 대응했는지 ("괜찮아요", "도움 요청", "응답 없음 → 자동 문자" …) */
  val userResponse: String = "",
  val isSimulated: Boolean = false,
  /** 최대 가속도 (m/s²) */
  val maxAccel: Float = 0f,
  @ColumnInfo(defaultValue = "0") val confidence: Double = 0.0,
  @ColumnInfo(defaultValue = "") val reason: String = "",
  @ColumnInfo(defaultValue = "") val address: String = "",
  @ColumnInfo(defaultValue = "1") val hasLocation: Boolean = true,
  @ColumnInfo(defaultValue = "") val kind: String = "",
  /** 판단한 주체 (이전 버전 기록에는 "Gemini" 가 남아 있을 수 있음) */
  @ColumnInfo(defaultValue = "") val analyzer: String = "",
)

@Dao
interface FallLogDao {
  @Query("SELECT * FROM fall_logs ORDER BY timestamp DESC")
  fun getAllLogs(): Flow<List<FallLog>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLog(log: FallLog): Long

  @Query("UPDATE fall_logs SET userResponse = :response WHERE id = :id")
  suspend fun updateResponse(id: Int, response: String)

  /** 앱이 경보 도중 종료돼 '확인 중'으로 남은 기록을 정리합니다. 앱 시작 때 한 번 부릅니다. */
  @Query("UPDATE fall_logs SET userResponse = '확인 도중 앱이 종료됨' WHERE userResponse = '확인 중'")
  suspend fun closeUnfinished(): Int

  @Query("DELETE FROM fall_logs")
  suspend fun clearAllLogs()

  @Query("DELETE FROM fall_logs WHERE id = :id")
  suspend fun deleteLogById(id: Int)
}

@Database(entities = [FallLog::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun fallLogDao(): FallLogDao

  companion object {
    /** v1 → v2: 판단 근거, 신뢰도, 주소, 종류 칸 추가. 기존 기록은 그대로 둡니다. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE fall_logs ADD COLUMN confidence REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE fall_logs ADD COLUMN reason TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE fall_logs ADD COLUMN address TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE fall_logs ADD COLUMN hasLocation INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE fall_logs ADD COLUMN kind TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE fall_logs ADD COLUMN analyzer TEXT NOT NULL DEFAULT ''")
      }
    }

    @Volatile private var INSTANCE: AppDatabase? = null

    fun get(context: Context): AppDatabase =
      INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "fall_detector_db")
          .addMigrations(MIGRATION_1_2)
          .build()
          .also { INSTANCE = it }
      }
  }
}
