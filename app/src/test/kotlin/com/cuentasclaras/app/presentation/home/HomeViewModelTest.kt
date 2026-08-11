package com.cuentasclaras.app.presentation.home

import com.cuentasclaras.app.MainDispatcherRule
import com.cuentasclaras.app.data.group.GroupRepository
import com.cuentasclaras.app.presentation.components.UiState
import com.cuentasclaras.domain.model.Currency
import com.cuentasclaras.domain.model.Group
import com.cuentasclaras.domain.model.GroupId
import com.cuentasclaras.domain.model.UserId
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val groupRepository = mockk<GroupRepository>()
    private val viewModel = HomeViewModel(groupRepository)

    @Test
    fun refresh_successWithGroups_setsContent() = runTest {
        coEvery { groupRepository.listMyGroups() } returns listOf(sampleGroup())

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(UiState.Content::class.java)
        assertThat((state as UiState.Content).data).hasSize(1)
    }

    @Test
    fun refresh_emptyList_setsEmpty() = runTest {
        coEvery { groupRepository.listMyGroups() } returns emptyList()

        viewModel.refresh()
        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(UiState.Empty)
    }

    @Test
    fun refresh_failure_setsError() = runTest {
        coEvery { groupRepository.listMyGroups() } throws RuntimeException("network down")

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(UiState.Error::class.java)
        assertThat((state as UiState.Error).message)
            .isEqualTo("No pudimos conectar. Revisá tu conexión a internet.")
    }

    private fun sampleGroup() = Group(
        id = GroupId("g1"),
        name = "Casa",
        currency = Currency.ARS,
        inviteCode = "AB12CD",
        createdBy = UserId("u1"),
        createdAt = Instant.parse("2026-08-11T12:00:00Z"),
        updatedAt = Instant.parse("2026-08-11T12:00:00Z"),
    )
}
