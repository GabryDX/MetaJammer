package com.heronikostudios.metajammer.domain.model

enum class AppLanguage(val code: String) {
    SYSTEM(""),
    ENGLISH("en"),
    SPANISH("es"),
    ITALIAN("it"),
    FRENCH("fr"),
    GERMAN("de"),
    CHINESE("zh"),
    HINDI("hi"),
    PORTUGUESE("pt"),
    RUSSIAN("ru"),
    GREEK("el"),
    ARABIC("ar"),
    JAPANESE("ja"),
    INDONESIAN("in"),
    TURKISH("tr"),
    KOREAN("ko"),
    VIETNAMESE("vi"),
    THAI("th"),
    POLISH("pl"),
    DUTCH("nl"),
    UKRAINIAN("uk"),
    PERSIAN("fa"),
    HEBREW("he"),
    LATIN("la");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.find { it.code == code } ?: SYSTEM
        }
    }
}
