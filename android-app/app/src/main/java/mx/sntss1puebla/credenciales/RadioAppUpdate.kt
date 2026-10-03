package mx.sntss1puebla.credenciales

import java.net.URI

data class RadioAppUpdate(val versionCode: Int, val versionName: String, val apkUrl: String) {
    fun isNewerThan(installedVersionCode: Int): Boolean = versionCode > installedVersionCode

    companion object {
        private val VERSION_CODE = Regex(""""versionCode"\\s*:\\s*(\\d+)""")
        private val VERSION_NAME = Regex(""""versionName"\\s*:\\s*"([^"\\r\\n]{1,32})"""")
        private val APK_URL = Regex(""""apkUrl"\\s*:\\s*"([^"\\r\\n]{1,300})"""")

        fun parse(json: String): RadioAppUpdate? {
            val code = VERSION_CODE.find(json)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val name = VERSION_NAME.find(json)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: return null
            val url = APK_URL.find(json)?.groupValues?.get(1) ?: return null
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (uri.scheme != "https" || uri.host != "raw.githubusercontent.com" ||
                uri.path != "/cncdigital/AndroidRadio/apk/RadioSindical.apk" || code < 1
            ) return null
            return RadioAppUpdate(code, name, url)
        }
    }
}
