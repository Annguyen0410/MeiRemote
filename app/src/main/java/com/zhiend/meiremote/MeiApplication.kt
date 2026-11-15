package com.zhiend.meiremote

import android.app.Application
import com.zhiend.meiremote.data.AppState

/**
 * Single app-wide [AppState] (connection, outbox, theme) shared by every entry
 * point — the main activity and the floating quick-note bubble.
 *
 * The auto-sync loop lives here (not in an activity) so queued items keep
 * flushing even while only the bubble service keeps the process alive.
 */
class MeiApplication : Application() {
    val state: AppState by lazy { AppState(this) }

    override fun onCreate() {
        super.onCreate()
        state.startAutoSync()
    }
}
