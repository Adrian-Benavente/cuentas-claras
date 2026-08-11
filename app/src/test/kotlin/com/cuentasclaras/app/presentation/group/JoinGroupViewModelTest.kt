package com.cuentasclaras.app.presentation.group

import androidx.lifecycle.SavedStateHandle
import com.cuentasclaras.app.MainDispatcherRule
import com.cuentasclaras.app.data.group.GroupRepository
import com.cuentasclaras.domain.model.GroupId
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JoinGroupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val groupRepository = mockk<GroupRepository>()
    private val viewModel = JoinGroupViewModel(
        savedStateHandle = SavedStateHandle(mapOf("code" to "")),
        groupRepository = groupRepository,
    )

    @Test
    fun join_shortCode_setsLocalError() = runTest {
        viewModel.onCodeChange("AB")
        viewModel.join()
        advanceUntilIdle()

        assertThat(viewModel.state.value.errorMessage)
            .isEqualTo("Ingresá el código de invitación.")
        assertThat(viewModel.state.value.joinedGroupId).isNull()
    }

    @Test
    fun join_invalidInviteFromRepo_mapsMessage() = runTest {
        coEvery { groupRepository.joinGroup(any()) } throws RuntimeException("invalid invite code")

        viewModel.onCodeChange("AB12CD")
        viewModel.join()
        advanceUntilIdle()

        assertThat(viewModel.state.value.errorMessage)
            .isEqualTo("No encontramos un grupo con ese código.")
        assertThat(viewModel.state.value.joinedGroupId).isNull()
    }

    @Test
    fun join_success_setsJoinedGroupId() = runTest {
        coEvery { groupRepository.joinGroup("AB12CD") } returns GroupId("g1")

        viewModel.onCodeChange("ab12cd")
        viewModel.join()
        advanceUntilIdle()

        assertThat(viewModel.state.value.joinedGroupId).isEqualTo("g1")
        assertThat(viewModel.state.value.errorMessage).isNull()
        assertThat(viewModel.state.value.isLoading).isFalse()
    }
}
