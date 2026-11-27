// ============================================================================
// Offline push (TIMPush) app-level configuration, centralized here.
//
// Applied from app/build.gradle.kts via:
//     apply(from = "../push-config/push-app.gradle.kts")
//
// This script keeps all push vendor wiring in one place so app/build.gradle.kts
// stays focused on the demo itself. Build behavior is identical to inlining
// these settings directly in the app module.
//
// This script runs with a Project receiver but without AGP types on its
// compile classpath, so the android extension is configured through
// withGroovyBuilder reflection and dependencies use the string-based
// configuration API (same approach as source-build/source-dependencies.gradle.kts).
//
// Related files that MUST stay at their plugin-mandated locations (do NOT move):
//   - app/src/main/assets/timpush-configs.json  (TIMPush vendor business params)
//   - app/google-services.json                  (Firebase / FCM)
//   - app/agconnect-services.json               (Huawei AGConnect)
//   - app/mcs-services.json                     (Honor MCS)
// ============================================================================

apply(plugin = "com.google.gms.google-services")
apply(plugin = "com.huawei.agconnect")
apply(plugin = "com.hihonor.mcs.asplugin")

extensions.getByName("android").withGroovyBuilder {
    "defaultConfig" {
        @Suppress("UNCHECKED_CAST")
        val placeholders = getProperty("manifestPlaceholders") as MutableMap<String, Any>
        placeholders["VIVO_APPKEY"] = "f91c42f5519e50fdad0bb88b2268f632"
        placeholders["VIVO_APPID"] = "103904657"
        placeholders["HONOR_APPID"] = "220765771"
    }
}

dependencies {
    "implementation"("com.tencent.timpush:timpush:9.0.+")
    "implementation"("com.tencent.timpush:huawei:9.0.+")
    "implementation"("com.tencent.timpush:xiaomi:9.0.+")
    "implementation"("com.tencent.timpush:oppo:9.0.+")
    "implementation"("com.tencent.timpush:vivo:9.0.+")
    "implementation"("com.tencent.timpush:honor:9.0.+")
    "implementation"("com.tencent.timpush:meizu:9.0.+")
    "implementation"("com.tencent.timpush:fcm:9.0.+")
}
