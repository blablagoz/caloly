package com.caloly.app.domain.social

fun Throwable.toSocialUserMessage(): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("PGRST202", ignoreCase = true) || raw.contains("schema cache", ignoreCase = true) ->
            "Arkadaş özellikleri şu anda hazırlanıyor. Biraz sonra tekrar dene."
        raw.contains("timeout", ignoreCase = true) || raw.contains("timed out", ignoreCase = true) ->
            "Bağlantı zaman aşımına uğradı. Tekrar deneyebilirsin."
        raw.contains("network", ignoreCase = true) || raw.contains("Unable to resolve host", ignoreCase = true) ->
            "İnternet bağlantısı kurulamadı."
        else -> "İşlem şu anda tamamlanamadı. Tekrar deneyebilirsin."
    }
}
