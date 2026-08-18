package com.caloly.app.domain.auth

import java.text.Normalizer
import java.util.Locale

private val usernameRegex = Regex("[\\p{L}\\p{N}._]{3,24}")

fun isValidUsername(value: String): Boolean =
    usernameRegex.matches(Normalizer.normalize(value.trim(), Normalizer.Form.NFC))

fun normalizeUsername(value: String): String =
    Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
        .lowercase(Locale.forLanguageTag("tr-TR"))

