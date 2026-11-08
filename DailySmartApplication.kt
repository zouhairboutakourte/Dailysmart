package com.dailysmart.app

import android.app.Application
import com.dailysmart.app.data.local.DailySmartDatabase
import com.dailysmart.app.data.repository.DailySmartRepository

class DailySmartApplication : Application() {

    val database: DailySmartDatabase by lazy {
        DailySmartDatabase.getDatabase(this)
    }

    val repository: DailySmartRepository by lazy {
        DailySmartRepository(
            taskDao = database.taskDao(),
            habitDao = database.habitDao(),
            financeDao = database.financeDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: DailySmartApplication
            private set
    }
}