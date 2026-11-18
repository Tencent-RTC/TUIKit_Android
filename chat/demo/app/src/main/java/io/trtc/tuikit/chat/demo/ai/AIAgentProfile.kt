package io.trtc.tuikit.chat.demo.ai

import android.content.Context
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.uikit.components.aiassistant.AIAssistantAgent

/**
 * 语音首屏、通话页和文字页按助手展示的副标题、示例问题与快捷提问。
 *
 * 语音示例问题尽量是纯中文，避免英文和型号被实时识别听错；文字页快捷提问点一下直接发送，可以带术语。
 * 服务器内置的演示助手按 ID 配专属问题，用户自定义的助手用通用问法。
 */
class AIAgentProfile(val subtitle: String, val samples: List<String>, val quickQuestions: List<String>) {

    companion object {
        /** 内置的腾讯云 IM 文档助手。 */
        private const val IM_DOC_BOT_USER_ID = "@RBT#AIAssistant"

        /** 内置的空调知识库演示助手。 */
        private const val AIRCON_BOT_USER_ID = "@RBT#AIAssistant_air"

        fun of(context: Context, agent: AIAssistantAgent?): AIAgentProfile {
            // 用自己的 SDKAppID 登录、还没配置助手时，不展示内置 IM 文档助手的介绍和示例问题。
            val imUserId = agent?.imUserId
                ?: if (SupportAgentCatalog.needsOwnAssistant) "" else null
            val (subtitleRes, samplesRes, quickRes) = when (imUserId) {
                null, IM_DOC_BOT_USER_ID -> Triple(
                    R.string.demo_ai_voice_subtitle, R.array.demo_ai_voice_samples, R.array.demo_ai_quick_questions,
                )
                AIRCON_BOT_USER_ID -> Triple(
                    R.string.demo_ai_voice_subtitle_aircon,
                    R.array.demo_ai_voice_samples_aircon,
                    R.array.demo_ai_quick_questions_aircon,
                )
                else -> Triple(
                    R.string.demo_ai_voice_subtitle_default,
                    R.array.demo_ai_voice_samples_default,
                    R.array.demo_ai_voice_samples_default,
                )
            }
            val res = context.resources
            return AIAgentProfile(
                context.getString(subtitleRes),
                res.getStringArray(samplesRes).toList(),
                res.getStringArray(quickRes).toList(),
            )
        }
    }
}
