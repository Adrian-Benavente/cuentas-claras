package com.cuentasclaras.domain.model

enum class GroupThemeId(val value: String) {
    FOREST("forest"),
    OCEAN("ocean"),
    SUNSET("sunset"),
    SLATE("slate"),
    ORCHID("orchid"),
    ;

    companion object {
        const val DEFAULT_VALUE = "forest"

        fun fromValue(raw: String?): GroupThemeId {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.value == normalized } ?: FOREST
        }
    }
}
