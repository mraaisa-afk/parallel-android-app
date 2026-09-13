package com.parallel.app

import android.app.Application
import com.parallel.app.hub.LocalHubServer

class ParallelApplication : Application() {
    lateinit var localHubServer: LocalHubServer
        private set

    override fun onCreate() {
        super.onCreate()
        localHubServer = LocalHubServer(this)
    }
}
