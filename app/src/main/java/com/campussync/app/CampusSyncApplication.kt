package com.campussync.app

import android.app.Application
import com.campussync.app.core.notifications.MessageNotifier
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CampusSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MessageNotifier.createChannel(this)
    }
}
