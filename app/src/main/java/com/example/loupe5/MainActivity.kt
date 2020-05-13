package com.example.loupe5

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.loupe5.GlassGestureDetector.Gesture
import com.example.loupe5.GlassGestureDetector.OnGestureListener

class MainActivity : AppCompatActivity(), GlassGestureDetector.OnGestureListener {
    private lateinit var gestureDetector: GlassGestureDetector
    private lateinit var cameraFragment: CameraFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setContentView(R.layout.activity_main)
        gestureDetector = GlassGestureDetector(this, this)
        cameraFragment = supportFragmentManager.findFragmentById(R.id.camera_fragment) as CameraFragment
    }

    override fun dispatchTouchEvent(ev: MotionEvent) =
        if (gestureDetector.onTouchEvent(ev)) {
            true
        } else super.dispatchTouchEvent(ev)

    override fun onGesture(gesture: Gesture): Boolean {
        return cameraFragment.onGesture(gesture)
    }

    companion object {
        private val TAG = this::class.simpleName
    }
}
