package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface LokaFotoDao {
    @Query("SELECT * FROM edited_photos ORDER BY lastEditedAt DESC")
    fun observeEditedPhotos(): Flow<List<EditedPhotoEntity>>

    @Query("SELECT * FROM edited_photos WHERE id = :id LIMIT 1")
    suspend fun getEditedPhotoById(id: String): EditedPhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEditedPhoto(photo: EditedPhotoEntity)

    @Query("DELETE FROM edited_photos WHERE id = :id")
    suspend fun deleteEditedPhotoById(id: String)

    @Query("SELECT * FROM recovery_session WHERE id = 1 LIMIT 1")
    suspend fun getRecoverySession(): RecoverySessionEntity?

    @Query("SELECT * FROM recovery_session WHERE id = 1 LIMIT 1")
    fun observeRecoverySession(): Flow<RecoverySessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecoverySession(session: RecoverySessionEntity)

    @Query("UPDATE recovery_session SET cleanExit = 1 WHERE id = 1")
    suspend fun markRecoveryCleanExit()

    @Query("DELETE FROM recovery_session WHERE id = 1")
    suspend fun clearRecoverySession()
}

@Database(
    entities = [EditedPhotoEntity::class, RecoverySessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LokaFotoDatabase : RoomDatabase() {
    abstract fun dao(): LokaFotoDao

    companion object {
        @Volatile
        private var INSTANCE: LokaFotoDatabase? = null

        fun getInstance(context: Context): LokaFotoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LokaFotoDatabase::class.java,
                    "lokafoto_studio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
