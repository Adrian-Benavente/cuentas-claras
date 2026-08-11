package com.cuentasclaras.app.ui.theme

import com.cuentasclaras.domain.model.GroupThemeId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GroupThemesTest {
    @Test
    fun catalog_containsExactlyFiveStableIds() {
        assertThat(GroupThemes.all.map { it.id }).containsExactly(
            GroupThemeId.FOREST,
            GroupThemeId.OCEAN,
            GroupThemeId.SUNSET,
            GroupThemeId.SLATE,
            GroupThemeId.ORCHID,
        ).inOrder()
        assertThat(GroupThemes.all.map { it.id.value }).containsExactly(
            "forest",
            "ocean",
            "sunset",
            "slate",
            "orchid",
        ).inOrder()
    }

    @Test
    fun of_unknownFallsBackToForest() {
        assertThat(GroupThemes.ofValue("neon").id).isEqualTo(GroupThemeId.FOREST)
        assertThat(GroupThemes.ofValue(null).id).isEqualTo(GroupThemeId.FOREST)
        assertThat(GroupThemes.of(GroupThemeId.OCEAN).id).isEqualTo(GroupThemeId.OCEAN)
    }
}
