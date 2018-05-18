package tus1327.coco

import android.app.Application
import timber.log.Timber

class CocoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}
