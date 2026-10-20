package pro.dasoft.ownsonic

import android.app.Application

class OwnsonicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AutoSyncScheduler.schedule(this)
    }
}
