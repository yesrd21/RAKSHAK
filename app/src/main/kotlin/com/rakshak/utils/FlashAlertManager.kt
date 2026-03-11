package com.rakshak.utils

import android.content.Context
import android.hardware.camera2.CameraManager
import kotlinx.coroutines.*

object FlashAlertManager {

    private var job: Job? = null

    fun startBlinking(context: Context) {

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[0]

        job = CoroutineScope(Dispatchers.IO).launch {
            repeat(12) {

                cameraManager.setTorchMode(cameraId, true)
                delay(250)

                cameraManager.setTorchMode(cameraId, false)
                delay(250)
            }
        }
    }

    fun stopBlinking(context: Context) {

        job?.cancel()

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[0]

        cameraManager.setTorchMode(cameraId, false)
    }
}