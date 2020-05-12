package com.example.loupe5

import android.os.Handler
import android.os.HandlerThread
import android.util.Log

class BackgroundThread(private val name: String) {
    private var thread: HandlerThread? = null
    var handler: Handler? = null
        private set

    fun start() {
        Log.d(TAG, "Starting thread $name")
        thread = HandlerThread(name).apply { start() }
        handler = Handler(thread!!.looper)
    }

    fun stop() {
        Log.d(TAG, "Stopping thread $name")
        thread?.quitSafely()
        thread?.join()
        thread = null
        handler = null
    }

    companion object {
        val TAG = this::class.simpleName
    }
}