package com.example.loupe5

import android.os.Handler
import android.os.HandlerThread

class BackgroundThread(private val name: String) {
    private var thread: HandlerThread? = null
    var handler: Handler? = null
        private set

    fun start() {
        thread = HandlerThread(name).apply { start() }
        handler = Handler(thread!!.looper)
    }

    fun stop() {
        thread?.quitSafely()
        thread = null
        handler = null
    }

}