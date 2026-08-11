package com.cuentasclaras.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class UserFacingErrorTest(
    private val rawMessage: String,
    private val context: UserFacingError.Context,
    private val expected: String,
) {
    @Test
    fun mapsKnownOrFallback() {
        val error = RuntimeException(rawMessage)
        assertThat(UserFacingError.from(error, context)).isEqualTo(expected)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} → {2}")
        fun data(): Collection<Array<Any>> = listOf(
            arrayOf(
                "invalid invite code",
                UserFacingError.Context.JoinGroup,
                "No encontramos un grupo con ese código.",
            ),
            arrayOf(
                "group needs at least two members",
                UserFacingError.Context.SaveExpense,
                "Necesitás al menos otra persona en el grupo para cargar un gasto.",
            ),
            arrayOf(
                "not authenticated",
                UserFacingError.Context.Generic,
                "Tu sesión expiró. Volvé a iniciar sesión.",
            ),
            arrayOf(
                "invalid_credentials",
                UserFacingError.Context.Auth,
                "Email o contraseña incorrectos.",
            ),
            arrayOf(
                "network unreachable",
                UserFacingError.Context.LoadGroups,
                "No pudimos conectar. Revisá tu conexión a internet.",
            ),
            arrayOf(
                "cannot remove owner",
                UserFacingError.Context.RemoveMember,
                "No se puede eliminar al administrador.",
            ),
            arrayOf(
                "something weird from supabase",
                UserFacingError.Context.CreateGroup,
                "No pudimos crear el grupo. Intentá de nuevo.",
            ),
            arrayOf(
                "permission denied for table",
                UserFacingError.Context.Generic,
                "No tenés permiso para esa acción.",
            ),
        )
    }
}
