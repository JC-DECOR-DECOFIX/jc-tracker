package br.com.jcdecor.tracker.util

import android.util.Log
import br.com.jcdecor.tracker.tracking.TrackerLog
import br.com.jcdecor.tracker.tracking.TrackingConfig

class AndroidTrackerLog : TrackerLog {
    override fun info(message: String) {
        Log.i(TrackingConfig.LOG_TAG, message)
    }
}
