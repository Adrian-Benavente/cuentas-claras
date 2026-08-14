package com.cuentasclaras.domain.finance

import com.cuentasclaras.domain.model.UserId
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategoryGateTest {

    private val owner = UserId("owner")
    private val creator = UserId("creator")
    private val other = UserId("other")

    @Test
    fun ownerCanManageAnyCategory() {
        assertThat(CategoryGate.canManage(creator, owner, isOwner = true)).isTrue()
        assertThat(CategoryGate.canManage(owner, owner, isOwner = true)).isTrue()
    }

    @Test
    fun creatorCanManageOwnCategory() {
        assertThat(CategoryGate.canManage(creator, creator, isOwner = false)).isTrue()
    }

    @Test
    fun otherMemberCannotManage() {
        assertThat(CategoryGate.canManage(creator, other, isOwner = false)).isFalse()
    }

    @Test
    fun missingCurrentUserCannotManage() {
        assertThat(CategoryGate.canManage(creator, null, isOwner = false)).isFalse()
        assertThat(CategoryGate.canManage(creator, null, isOwner = true)).isFalse()
    }

    @Test
    fun uncategorizedCannotBeManagedEvenByOwner() {
        assertThat(
            CategoryGate.canManage(creator, owner, isOwner = true, isUncategorized = true),
        ).isFalse()
        assertThat(
            CategoryGate.canManage(creator, creator, isOwner = false, isUncategorized = true),
        ).isFalse()
    }
}
