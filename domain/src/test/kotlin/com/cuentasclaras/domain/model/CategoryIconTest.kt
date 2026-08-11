package com.cuentasclaras.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class CategoryIconTest {

    @Test
    fun fromValue_mapsKnownKeys() {
        assertThat(CategoryIcon.fromValue("bolt")).isEqualTo(CategoryIcon.BOLT)
        assertThat(CategoryIcon.fromValue("WATER_DROP")).isEqualTo(CategoryIcon.WATER_DROP)
    }

    @Test
    fun fromValue_fallsBackToCategory() {
        assertThat(CategoryIcon.fromValue("nope")).isEqualTo(CategoryIcon.CATEGORY)
        assertThat(CategoryIcon.fromValue(null)).isEqualTo(CategoryIcon.CATEGORY)
    }

    @Test
    fun requireValid_rejectsUnknown() {
        assertThrows(IllegalArgumentException::class.java) {
            CategoryIcon.requireValid("nope")
        }
    }

    @Test
    fun allowedValues_coverAllEntries() {
        assertThat(CategoryIcon.ALLOWED_VALUES).hasSize(CategoryIcon.entries.size)
    }
}
