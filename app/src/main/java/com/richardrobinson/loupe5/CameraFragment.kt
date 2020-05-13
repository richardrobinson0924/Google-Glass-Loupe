package com.richardrobinson.loupe5

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine


/**
 * A simple [Fragment] subclass.
 */
class CameraFragment : Fragment(), GlassGestureDetector.OnGestureListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private val cameraManager: CameraManager by lazy {
        requireContext().applicationContext
            .getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(cameraManager.cameraIdList.first())
    }

    private val zoomController: ZoomController by lazy {
        ZoomController(characteristics)
    }

    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private var zoomLevel: Float = ZoomController.DEFAULT_FACTOR

    private lateinit var viewFinder: AutoFitSurfaceView
    private lateinit var camera: CameraDevice
    private lateinit var session: CameraCaptureSession
    private lateinit var captureRequest: CaptureRequest.Builder
    private lateinit var gestureDetector: GlassGestureDetector

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onGesture(gesture: GlassGestureDetector.Gesture)=  when (gesture) {
        GlassGestureDetector.Gesture.SWIPE_FORWARD -> {
            zoomBy(1F)
            true
        }
        GlassGestureDetector.Gesture.SWIPE_BACKWARD -> {
            zoomBy(-1F)
            true
        }
        GlassGestureDetector.Gesture.SWIPE_DOWN -> {
            requireActivity().finish()
            true
        }
        else -> false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewFinder = view.findViewById(R.id.view_finder)
        gestureDetector = GlassGestureDetector(requireContext(), this)

        viewFinder.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceChanged(
                holder: SurfaceHolder?,
                format: Int,
                width: Int,
                height: Int) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder?) = Unit

            override fun surfaceCreated(holder: SurfaceHolder?) {
                val previewSize = SmartSize.previewSizeFor<SurfaceHolder>(
                    viewFinder.display,
                    characteristics
                )

                Log.d(TAG, "View finder size: ${viewFinder.width} x ${viewFinder.height}")
                Log.d(TAG, "Selected preview size: $previewSize")

                viewFinder.setAspectRatio(previewSize.width, previewSize.height)
                view.post { getPermissions() }
            }
        })
    }

    private fun getPermissions() {
        if (hasPermissions(requireContext())) {
            initializeCamera()
        } else {
            requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.first() == PackageManager.PERMISSION_GRANTED) {
                initializeCamera()
            }
        } else {
            Toast.makeText(context, "Permissions Denied", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        super.onStop()
        try { camera.close() } catch (exc: Throwable) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
    }

    private fun zoomBy(factor: Float) {
        zoomLevel += factor
        Log.d(TAG, "zoom level = $zoomLevel")
        zoomController.setZoomFor(builder = captureRequest, zoomLevel = zoomLevel)

        session.stopRepeating()
        session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
    }

    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        val permission = ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        )
        assert(permission != PackageManager.PERMISSION_GRANTED)

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) = cont.resume(camera)

            override fun onDisconnected(camera: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
                requireActivity().finish()
            }

            override fun onError(camera: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }

        }, handler)
    }

    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        camera = openCamera(cameraManager, cameraManager.cameraIdList.first(), cameraHandler)
        val targets = listOf(viewFinder.holder.surface)

        session = createCaptureSession(camera, targets, cameraHandler)
        captureRequest = camera
            .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            .apply { addTarget(viewFinder.holder.surface) }

        session.setRepeatingRequest(captureRequest.build(), null, cameraHandler)
    }

    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->

        device.createCaptureSession(targets, object: CameraCaptureSession.StateCallback() {
            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }

            override fun onConfigured(session: CameraCaptureSession) {
                cont.resume(session)
            }

        }, handler)
    }

    companion object {
        private val TAG = this::class.simpleName

        private const val PERMISSIONS_REQUEST_CODE = 10
        private val PERMISSIONS_REQUIRED = arrayOf(Manifest.permission.CAMERA)

        fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
