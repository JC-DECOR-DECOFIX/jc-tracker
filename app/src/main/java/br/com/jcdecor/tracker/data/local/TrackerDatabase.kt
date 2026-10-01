package br.com.jcdecor.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PendingLocationEntity::class], version = 1, exportSchema = false)
abstract class TrackerDatabase : RoomDatabase() {
    abstract fun pendingLocationDao(): PendingLocationDao
}
