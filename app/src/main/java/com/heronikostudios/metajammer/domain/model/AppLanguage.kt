package com.heronikostudios.metajammer.domain.model

enum class AppLanguage(val code: String) {
    SYSTEM(""),
    ENGLISH("en"),
    CHINESE("zh"),
    SPANISH("es"),
    HINDI("hi"),
    ARABIC("ar"),
    FRENCH("fr"),
    PORTUGUESE("pt"),
    RUSSIAN("ru"),
    JAPANESE("ja"),
    GERMAN("de"),
    KOREAN("ko"),
    INDONESIAN("in"),
    ITALIAN("it"),
    TURKISH("tr"),
    VIETNAMESE("vi"),
    THAI("th"),
    POLISH("pl"),
    UKRAINIAN("uk"),
    DUTCH("nl"),
    ROMANIAN("ro"),
    PERSIAN("fa"),
    GREEK("el"),
    HEBREW("iw"),
    LATIN("la");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.find { it.code == code } ?: SYSTEM
        }
    }
}
