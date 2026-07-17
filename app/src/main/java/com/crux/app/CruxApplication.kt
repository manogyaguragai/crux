package com.crux.app

import android.app.Application
import com.crux.app.data.AppContainer

/** Owns the manual dependency container for the whole process. */
class CruxApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
