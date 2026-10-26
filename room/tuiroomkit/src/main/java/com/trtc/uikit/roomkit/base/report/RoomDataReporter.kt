package com.trtc.uikit.roomkit.base.report

import com.tencent.cloud.tuikit.engine.room.TUIRoomEngine

class RoomDataReporter {

    companion object {
        private const val FRAMEWORK_NATIVE = 1
        private const val COMPONENT_ROOM = 18
        private const val LANGUAGE_JAVA = 1

        fun reportComponent() {
            val jsonStr = """
                {
                    "api":"setFramework",
                    "params":{
                        "framework":$FRAMEWORK_NATIVE,
                        "component":$COMPONENT_ROOM,
                        "language":$LANGUAGE_JAVA
                    }
                }
            """.trimIndent()

            TUIRoomEngine.sharedInstance().callExperimentalAPI(jsonStr, null)
        }
    }
}