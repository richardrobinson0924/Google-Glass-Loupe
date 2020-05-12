package com.example.loupe5

import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import kotlin.math.max

class ZoomController(characteristics: CameraCharacteristics) {
    private val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!

    private val maxZoom = max(
        characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 0F,
        DEFAULT_FACTOR
    )

    /**
     * Ideally, your Camera fragment or activity should have its own `zoom` function, which calls
     * this function on a Builder property, and then sets a repeating request of a
     * `CameraCaptureSession` for such a builder
     *
     * @param builder the `CaptureRequest.Builder` to modify
     * @param zoomLevel the absolute zoom level to set, which is ultimately clamped to the range
     * produced by `DEFAULT_FACTOR` and the maximum possible zoom of the camera
     */
    fun setZoomFor(builder: CaptureRequest.Builder, zoomLevel: Float) {
        val clamped = zoomLevel.coerceIn(DEFAULT_FACTOR, maxZoom)

        val cx = sensorSize.width() / 2
        val cy = sensorSize.height() / 2
        val dx = (0.5F * sensorSize.width() / clamped).toInt()
        val dy = (0.5F * sensorSize.height() / clamped).toInt()

        val rect = Rect(cx - dx, cy - dy, cx + dx, cy + dy)
        builder.set(CaptureRequest.SCALER_CROP_REGION, rect)
    }

    companion object {
        const val DEFAULT_FACTOR = 1F
    }
}