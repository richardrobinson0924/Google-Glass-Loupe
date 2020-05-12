package com.example.loupe5

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.loupe5.GlassGestureDetector.Gesture
import com.example.loupe5.GlassGestureDetector.OnGestureListener

class MainActivity : AppCompatActivity() {
    private lateinit var previewTexture: TextureView
    private lateinit var cameraManager: CameraManager
    private lateinit var zoomController: ZoomController
    private lateinit var gestureDetector: GlassGestureDetector

    private var zoomLevel: Float = ZoomController.DEFAULT_FACTOR

    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    private val cameraThread = BackgroundThread("camera_bg_thread")
    private var previewSize: Size? = null

    /**
     * Assuming the camera is at absolute zoom position `x`, this function sets the zoom position
     * to `x + factor`
     */
    private fun zoomBy(factor: Float) {
        zoomLevel += factor
        Log.d(TAG, "zoom level = $zoomLevel")
        zoomController.setZoomFor(builder = captureRequestBuilder!!, zoomLevel = zoomLevel)

        captureSession?.stopRepeating()
        captureSession?.setRepeatingRequest(captureRequestBuilder!!.build(), null, cameraThread.handler)
    }

    private val gestureListener = object : OnGestureListener {
        override fun onGesture(gesture: Gesture): Boolean {
            Log.d(TAG, "gesture: $gesture")
            return when (gesture) {
                Gesture.TAP -> {
                    zoomBy(-zoomLevel + 1)
                    true
                }
                Gesture.SWIPE_FORWARD -> {
                    zoomBy(1F)
                    true
                }
                Gesture.SWIPE_BACKWARD -> {
                    zoomBy(-1F)
                    true
                }
                Gesture.SWIPE_DOWN -> {
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "camera opened")
            cameraDevice = camera
            createPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "camera disconnected")
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
        }
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {}

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?) = false

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            setupCamera()
            openCamera()
        }
    }

    private val captureStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigureFailed(session: CameraCaptureSession) {
        }

        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(TAG, "camera capture session configured")
            if (cameraDevice == null) return
            captureSession = session.apply {
                setRepeatingRequest(captureRequestBuilder!!.build(), null, cameraThread.handler)
            }
        }
    }

    fun createPreviewSession() {
        Log.d(TAG, "creating preview surface")
        val previewSurface = previewTexture.surfaceTexture.let {
            it.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
            return@let Surface(it)
        }

        Log.d(TAG, "creating capture request builder")
        captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
            addTarget(previewSurface)
        }

        Log.d(TAG, "creating capture session")
        cameraDevice?.createCaptureSession(
            listOf(previewSurface),
            captureStateCallback,
            cameraThread.handler
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        gestureDetector = GlassGestureDetector(this, gestureListener)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        previewTexture = findViewById(R.id.preview)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "resuming")
        cameraThread.start()
        if (previewTexture.isAvailable) {
            setupCamera()
            openCamera()
        } else {
            previewTexture.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "stopping")
        captureSession?.close(); captureSession = null
        cameraDevice?.close(); cameraDevice = null
        cameraThread.stop()
    }

    private fun setupCamera() {
        val characteristics = cameraManager.getCameraCharacteristics(cameraManager.cameraIdList.first())
        previewSize = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(SurfaceTexture::class.java)
            ?.first()
            ?.also { Log.d(TAG, "preview size = $it") }

        zoomController = ZoomController(characteristics)
    }

    private fun openCamera() {
        val permission = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISSION_CODE)
        } else {
            cameraManager.openCamera(cameraManager.cameraIdList.first(), cameraStateCallback, cameraThread.handler)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            cameraManager.openCamera(
                cameraManager.cameraIdList.first(),
                cameraStateCallback,
                cameraThread.handler
            )
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent) =
        if (gestureDetector.onTouchEvent(ev)) {
            true
        } else super.dispatchTouchEvent(ev)

    companion object {
        private val TAG = this::class.simpleName
        private const val PERMISSION_CODE = 105
    }
}
