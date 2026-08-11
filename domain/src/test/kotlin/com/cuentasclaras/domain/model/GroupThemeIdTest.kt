package com.cuentasclaras.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GroupThemeIdTest {
    @Test
    fun fromValue_knownIds() {
        assertThat(GroupThemeId.fromValue("forest")).isEqualTo(GroupThemeId.FOREST)
        assertThat(GroupThemeId.fromValue("ocean")).isEqualTo(GroupThemeId.OCEAN)
        assertThat(GroupThemeId.fromValue("sunset")).isEqualTo(GroupThemeId.SUNSET)
        assertThat(GroupThemeId.fromValue("slate")).isEqualTo(GroupThemeId.SLATE)
        assertThat(GroupThemeId.fromValue("orchid")).isEqualTo(GroupThemeId.ORCHID)
    }

    @Test
    fun fromValue_invalidOrNull_fallsBackToForest() {
        assertThat(GroupThemeId.fromValue(null)).isEqualTo(GroupThemeId.FOREST)
        assertThat(GroupThemeId.fromValue("")).isEqualTo(GroupThemeId.FOREST)
        assertThat(GroupThemeId.fromValue("neon")).isEqualTo(GroupThemeId.FOREST)
        assertThat(GroupThemeId.fromValue("FOREST")).isEqualTo(GroupThemeId.FOREST)
    }
}
