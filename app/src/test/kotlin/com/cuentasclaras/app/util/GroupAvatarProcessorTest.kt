package com.cuentasclaras.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GroupAvatarProcessorTest {

    @Test
    fun centerCropRect_landscape_cropsHorizontally() {
        val rect = GroupAvatarProcessor.centerCropRect(width = 200, height = 100)
        assertThat(rect.size).isEqualTo(100)
        assertThat(rect.x).isEqualTo(50)
        assertThat(rect.y).isEqualTo(0)
    }

    @Test
    fun centerCropRect_portrait_cropsVertically() {
        val rect = GroupAvatarProcessor.centerCropRect(width = 80, height = 200)
        assertThat(rect.size).isEqualTo(80)
        assertThat(rect.x).isEqualTo(0)
        assertThat(rect.y).isEqualTo(60)
    }

    @Test
    fun centerCropRect_alreadySquare() {
        val rect = GroupAvatarProcessor.centerCropRect(width = 150, height = 150)
        assertThat(rect).isEqualTo(GroupAvatarProcessor.CropRect(0, 0, 150))
    }
}
