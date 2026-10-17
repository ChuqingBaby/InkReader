package com.kovak.moyue.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun byId(id: Long): Book?

    @Query("SELECT * FROM books WHERE sourcePath = :path LIMIT 1")
    suspend fun bySourcePath(path: String): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE books SET lastReadAt = :time WHERE id = :id")
    suspend fun touchLastRead(id: Long, time: Long)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun byBook(bookId: Long): ReadingProgress?

    @Query("SELECT * FROM reading_progress")
    fun observeAll(): Flow<List<ReadingProgress>>

    @Upsert
    suspend fun upsert(progress: ReadingProgress)

    @Query("DELETE FROM reading_progress WHERE bookId = :bookId")
    suspend fun delete(bookId: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeByBook(bookId: Long): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks")
    suspend fun all(): List<Bookmark>

    @Insert
    suspend fun insert(bookmark: Bookmark): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: Long)
}

@Dao
interface ConvertDao {
    @Query("SELECT * FROM convert_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ConvertTask>>

    @Query("SELECT * FROM convert_tasks WHERE id = :id")
    suspend fun byId(id: Long): ConvertTask?

    @Query("SELECT * FROM convert_tasks WHERE bookId = :bookId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestByBook(bookId: Long): ConvertTask?

    @Insert
    suspend fun insert(task: ConvertTask): Long

    @Update
    suspend fun update(task: ConvertTask)

    @Query("DELETE FROM convert_tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE convert_tasks SET status = 'CANCELED' WHERE status IN ('PENDING','RUNNING')")
    suspend fun cancelAllStale()
}

@Dao
interface FontDao {
    @Query("SELECT * FROM fonts ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE id = :id")
    suspend fun byId(id: Long): FontEntity?

    @Insert
    suspend fun insert(font: FontEntity): Long

    @Query("DELETE FROM fonts WHERE id = :id")
    suspend fun delete(id: Long)
}
