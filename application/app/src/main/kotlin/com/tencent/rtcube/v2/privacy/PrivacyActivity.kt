package com.tencent.rtcube.v2.privacy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import com.tencent.rtcube.v2.privacy.ui.PrivacyScreen

internal class PrivacyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                PrivacyScreen(
                    onBack = { finish() },
                )
            }
        }
    }
}
