package com.trtc.uikit.roomkit.base.util

import io.trtc.tuikit.atomicxcore.api.room.RoomStore

class RoomDataReporter {

    companion object {
        private const val FRAMEWORK_NATIVE = 1
        private const val COMPONENT_ROOM = 18
        private const val LANGUAGE_JAVA = 1

        fun reportComponent() {
            val jsonStr = """
                {
                    "api":"LoginModule.setFramework",
                    "params":{
                        "framework":$FRAMEWORK_NATIVE,
                        "component":$COMPONENT_ROOM,
                        "language":$LANGUAGE_JAVA
                    }
                }
            """.trimIndent()
            RoomStore.shared().callExperimentalAPI(jsonStr, null)
        }
    }
}
