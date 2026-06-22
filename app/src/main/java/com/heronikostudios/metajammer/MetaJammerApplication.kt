package com.heronikostudios.metajammer

import android.app.Application
import timber.log.Timber

class MetaJammerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
    }
}
