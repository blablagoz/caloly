package com.caloly.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.caloly.app.notifications.CalolyNotificationScheduler

@HiltAndroidApp
class CalolyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CalolyNotificationScheduler.schedule(this)
    }
}
