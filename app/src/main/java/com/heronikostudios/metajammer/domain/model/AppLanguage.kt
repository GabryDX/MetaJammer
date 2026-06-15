package com.heronikostudios.metajammer.domain.model

enum class AppLanguage(val code: String) {
    SYSTEM(""),
    ENGLISH("en"),
    SPANISH("es"),
    ITALIAN("it"),
    FRENCH("fr"),
    GERMAN("de");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.find { it.code == code } ?: SYSTEM
        }
    }
}
