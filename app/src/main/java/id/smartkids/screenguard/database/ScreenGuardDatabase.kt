package id.smartkids.screenguard.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "usage_events")
data class UsageEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val dateString: String, // YYYY-MM-DD
    val hourOfDay: Int,
    val eventType: String, // DISTANCE, POSTURE, EYE_REST, SLEEP, USAGE_MINUTE
    val detail: String
)

@Entity(tableName = "security_audit_logs")
data class SecurityAuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val success: Boolean,
    val note: String
)

data class HourlyUsageStat(
    val hourOfDay: Int,
    val totalEvents: Int
)

@Dao
interface ScreenGuardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageEvent(event: UsageEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSecurityAudit(audit: SecurityAuditEntity)

    @Query("SELECT * FROM usage_events WHERE timestamp >= :sinceMillis ORDER BY timestamp DESC")
    fun observeEventsSince(sinceMillis: Long): Flow<List<UsageEventEntity>>

    @Query("SELECT * FROM security_audit_logs ORDER BY timestamp DESC LIMIT 50")
    fun observeSecurityAudits(): Flow<List<SecurityAuditEntity>>

    @Query("SELECT hourOfDay, COUNT(*) as totalEvents FROM usage_events WHERE timestamp >= :sinceMillis GROUP BY hourOfDay ORDER BY totalEvents DESC LIMIT 1")
    suspend fun getPeakUsageHour(sinceMillis: Long): HourlyUsageStat?

    @Query("DELETE FROM usage_events")
    suspend fun clearAllUsageEvents()
}

@Database(
    entities = [UsageEventEntity::class, SecurityAuditEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ScreenGuardDatabase : RoomDatabase() {
    abstract fun dao(): ScreenGuardDao

    companion object {
        @Volatile
        private var INSTANCE: ScreenGuardDatabase? = null

        fun getInstance(context: Context): ScreenGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ScreenGuardDatabase::class.java,
                    "smart_kids_screen_guard.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
