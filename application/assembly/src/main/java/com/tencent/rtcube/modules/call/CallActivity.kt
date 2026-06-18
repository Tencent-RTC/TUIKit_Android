package com.tencent.rtcube.modules.call

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tencent.rtcube.assembly.AppTarget
import com.tencent.rtcube.modules.call.lab.LabCallEntrance
import com.tencent.rtcube.modules.call.online.CallEntrance

class CallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TARGET = "extra_app_target"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val targetName = intent.getStringExtra(EXTRA_TARGET)
        val appTarget = runCatching { AppTarget.valueOf(targetName ?: "") }.getOrDefault(AppTarget.DOMESTIC)
        setContent {
            if (appTarget == AppTarget.LAB) {
                LabCallEntrance(onBack = { finish() })
            } else {
                CallEntrance(onFinish = { finish() })
            }
        }
    }
}