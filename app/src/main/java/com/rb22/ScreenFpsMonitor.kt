package com.rb22

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * Estimates the frame cadence visible on the device display using MediaProjection.
 * This is intentionally labelled an estimate: Android does not expose another
 * application's internal renderer FPS to ordinary third-party apps.
 */
class ScreenFpsMonitor(
    private val context: Context,
    private val onFps: (Int) -> Unit
) {
    companion object {
        const val EXTRA_RESULT_CODE = "rb22_projection_result_code"
        const val EXTRA_RESULT_DATA = "rb22_projection_result_data"
    }

    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var frames = 0
    private var windowStartNs = 0L

    fun start(resultCode: Int, data: Intent) {
        stop()

        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(resultCode, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stop()
            }
        }, main)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.getRealMetrics(metrics)

        // Quarter-resolution capture keeps the monitor lightweight.
        val width = (metrics.widthPixels / 2).coerceAtLeast(320)
        val height = (metrics.heightPixels / 2).coerceAtLeast(240)
        val dpi = metrics.densityDpi.coerceAtLeast(160)

        reader = ImageReader.newInstance(
            width, height, android.graphics.PixelFormat.RGBA_8888, 3
        )

        reader?.setOnImageAvailableListener({ source ->
            var image: android.media.Image? = null
            try {
                image = source.acquireLatestImage()
                if (image != null) {
                    val now = System.nanoTime()
                    if (windowStartNs == 0L) windowStartNs = now
                    frames++

                    val elapsed = now - windowStartNs
                    if (elapsed >= 1_000_000_000L) {
                        val fps = (frames * 1_000_000_000.0 / elapsed).roundToInt()
                            .coerceIn(1, 240)
                        onFps(fps)
                        frames = 0
                        windowStartNs = now
                    }
                }
            } finally {
                image?.close()
            }
        }, main)

        display = projection?.createVirtualDisplay(
            "RB22-FPS",
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface,
            null,
            main
        )
    }

    fun stop() {
        reader?.setOnImageAvailableListener(null, null)
        display?.release()
        reader?.close()
        projection?.stop()
        display = null
        reader = null
        projection = null
        frames = 0
        windowStartNs = 0L
    }
}
