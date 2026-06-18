package com.tencent.rtcube.v2.mine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import com.tencent.rtcube.v2.mine.ui.MineScreen

internal class MineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                MineScreen(
                    onBack = { finish() },
                    onLogoutSuccess = {
                        MineCallbacks.onLogout?.invoke()
                        finish()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MineCallbacks.clear()
    }
}
