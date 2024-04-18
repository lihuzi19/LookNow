package com.lihuzi.looknow

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

/**
 *@Author lining
 *@Date 2024/4/18
 *@DESC TODO
 **/

class CameraPreviewActivity : AppCompatActivity() {
    private lateinit var textureView: TextureView
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textureView = TextureView(this)
        setContentView(textureView)

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList[0]

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                if (ActivityCompat.checkSelfPermission(
                        this@CameraPreviewActivity,
                        Manifest.permission.CAMERA
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this@CameraPreviewActivity,
                        arrayOf(Manifest.permission.CAMERA),
                        100
                    )
                    return
                }
                startCameraPreview(surface)
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true
        }
    }

    @SuppressLint("MissingPermission")
    private fun startCameraPreview(surface: SurfaceTexture) {
        val surface = Surface(surface)
        val captureRequestBuilder =
            cameraManager.getCameraCharacteristics(cameraId!!)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                .getOutputSizes(SurfaceTexture::class.java)[0]

        cameraManager.openCamera(cameraId!!, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                val captureRequest =
                    camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(surface)
                    }.build()

                camera.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.setRepeatingRequest(captureRequest, null, null)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {}
                    },
                    null
                )
            }

            override fun onDisconnected(camera: CameraDevice) {}
            override fun onError(camera: CameraDevice, error: Int) {}
        }, null)
    }
}