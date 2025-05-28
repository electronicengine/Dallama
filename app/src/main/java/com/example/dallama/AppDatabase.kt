package com.example.dallama
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import androidx.room.TypeConverters


@TypeConverters(FloatArrayConverter::class)
@Entity(tableName = "pdf_chunks")
data class PdfChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val text: String,
    val embedding: FloatArray // Store as CSV string
)

class FloatArrayConverter {
    @TypeConverter
    fun fromFloatArray(array: FloatArray): String =
        array.joinToString(",")

    @TypeConverter
    fun toFloatArray(data: String): FloatArray =
        if (data.isEmpty()) FloatArray(0)
        else data.split(",").map { it.toFloat() }.toFloatArray()
}

@Dao
interface PdfDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdfChunk(chunk: PdfChunkEntity)

    @Query("SELECT * FROM pdf_chunks WHERE fileName = :fileName")
    suspend fun getChunksByFileName(fileName: String): List<PdfChunkEntity>

    @Query("SELECT DISTINCT fileName FROM pdf_chunks")
    suspend fun getAllPdfNames(): List<String>
}


@Database(entities = [PdfChunkEntity::class], version = 1)
@TypeConverters(FloatArrayConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfDao(): PdfDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).allowMainThreadQueries().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

