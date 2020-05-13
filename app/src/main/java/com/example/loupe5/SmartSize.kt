/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.loupe5

import android.graphics.Point
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Size
import android.view.Display
import kotlin.math.max
import kotlin.math.min

data class SmartSize(val width: Int, val height: Int) {
    val toSize = Size(width, height)
    val long = max(toSize.width, toSize.height)
    val short = min(toSize.width, toSize.height)

    companion object {
        val SIZE_HD = SmartSize(1920, 1080)
        val SIZE_4K = SmartSize(3840, 2160)
        val SIZE_ZERO = SmartSize(0, 0)

        fun fromDisplay(display: Display): SmartSize {
            val out = Point()
            display.getRealSize(out)
            return SmartSize(out.x, out.y)
        }

        inline fun <reified T> previewSizeFor(
            display: Display,
            characteristics: CameraCharacteristics,
            format: Int? = null
        ): SmartSize {
            val screenSize = fromDisplay(display)
            val isHD = screenSize.long >= SIZE_HD.long || screenSize.short >= SIZE_HD.short
            val maxSize = if (isHD) SIZE_HD else screenSize

            val config = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            if (format == null) {
                assert(StreamConfigurationMap.isOutputSupportedFor(T::class.java))
            } else {
                assert(config.isOutputSupportedFor(format))
            }

            val allSizes = if (format == null)
                config.getOutputSizes(T::class.java) else
                config.getOutputSizes(format)

            return allSizes
                .sortedWith(compareBy { it.height * it.width })
                .map { SmartSize(it.width, it.height) }
                .reversed()
                .first { it.long <= maxSize.long && it.short <= maxSize.short }
        }
    }
}