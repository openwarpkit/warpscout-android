package io.github.openwarpkit.warpscout.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scan_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operation: String,
    val protocol: String,
    val preset: String,
    val status: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val progressCompleted: Int,
    val progressTotal: Int,
    val workingCount: Int,
    val tornDownCount: Int,
    val bestEndpoint: String?,
    val optionsJson: String,
    val resultJson: String?
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM scan_history WHERE id = :id")
    suspend fun find(id: Long): HistoryEntity?

    @Insert
    suspend fun insert(item: HistoryEntity): Long

    @Update
    suspend fun update(item: HistoryEntity)

    @Query("UPDATE scan_history SET status = 'Interrupted', finishedAt = :finishedAt WHERE status = 'Running'")
    suspend fun interruptUnfinished(finishedAt: Long)

    @Query("DELETE FROM scan_history")
    suspend fun clearAll()
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = true)
abstract class WarpScoutDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
