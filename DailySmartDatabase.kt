package com.dailysmart.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dailysmart.app.data.local.dao.FinanceDao
import com.dailysmart.app.data.local.dao.HabitDao
import com.dailysmart.app.data.local.dao.TaskDao
import com.dailysmart.app.data.local.entity.HabitCheckInEntity
import com.dailysmart.app.data.local.entity.HabitEntity
import com.dailysmart.app.data.local.entity.TaskEntity
import com.dailysmart.app.data.local.entity.TransactionEntity

@Database(
    entities = [
        TaskEntity::class,
        HabitEntity::class,
        HabitCheckInEntity::class,
        TransactionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DailySmartDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun financeDao(): FinanceDao

    companion object {
        @Volatile
        private var INSTANCE: DailySmartDatabase? = null

        fun getDatabase(context: Context): DailySmartDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DailySmartDatabase::class.java,
                    "daily_smart_database.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}