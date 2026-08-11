package com.cuentasclaras.app.util

object UserFacingError {
    enum class Context {
        Auth,
        JoinGroup,
        CreateGroup,
        SaveExpense,
        DeleteExpense,
        LoadGroups,
        LoadGroup,
        RotateInvite,
        Settlement,
        RemoveMember,
        PeriodClose,
        Generic,
    }

    fun from(error: Throwable, context: Context = Context.Generic): String {
        val message = error.message.orEmpty().lowercase()
        return matchKnown(message) ?: fallback(context)
    }

    fun fromMessage(raw: String?, context: Context = Context.Generic): String {
        val message = raw.orEmpty().lowercase()
        return matchKnown(message) ?: fallback(context)
    }

    private fun matchKnown(message: String): String? = when {
        message.contains("invalid invite code") ->
            "No encontramos un grupo con ese código."
        message.contains("group needs at least two members") ->
            "Necesitás al menos otra persona en el grupo para cargar un gasto."
        message.contains("period is closed") ->
            "Este período está cerrado. Reabrilo para hacer cambios."
        message.contains("only owner can close") || message.contains("only owner can reopen") ->
            "Solo el administrador puede cerrar o reabrir el período."
        message.contains("not authenticated") ||
            message.contains("jwt") ||
            message.contains("session") && message.contains("expired") ->
            "Tu sesión expiró. Volvé a iniciar sesión."
        message.contains("only owner can remove") || message.contains("only owner can rotate") ->
            "Solo el administrador puede hacer eso."
        message.contains("cannot remove owner") ->
            "No se puede eliminar al administrador."
        message.contains("cannot remove yourself") ->
            "No podés eliminarte a vos mismo."
        message.contains("member not found") ->
            "Ese miembro ya no está en el grupo."
        message.contains("permission") ||
            message.contains("row-level security") ||
            message.contains("not allowed") ||
            message.contains("forbidden") ||
            message.contains("42501") ->
            "No tenés permiso para esa acción."
        message.contains("foreign key") || message.contains("profiles") ->
            "Tu perfil no está completo. Cerrá sesión y volvé a entrar."
        message.contains("invalid login") ||
            message.contains("invalid_credentials") ||
            message.contains("invalid email or password") ->
            "Email o contraseña incorrectos."
        message.contains("user already") || message.contains("already registered") ->
            "Ya existe una cuenta con ese email."
        message.contains("supabase.url") || message.contains("local.properties") ->
            "Falta configurar Supabase en local.properties."
        message.contains("network") ||
            message.contains("unable to resolve") ||
            message.contains("timeout") ||
            message.contains("failed to connect") ->
            "No pudimos conectar. Revisá tu conexión a internet."
        else -> null
    }

    private fun fallback(context: Context): String = when (context) {
        Context.Auth -> "No se pudo completar la autenticación. Intentá de nuevo."
        Context.JoinGroup -> "No pudimos unirte al grupo. Intentá de nuevo."
        Context.CreateGroup -> "No pudimos crear el grupo. Intentá de nuevo."
        Context.SaveExpense -> "No pudimos guardar el gasto. Intentá de nuevo."
        Context.DeleteExpense -> "No pudimos eliminar el gasto."
        Context.LoadGroups -> "No pudimos cargar tus grupos. Intentá de nuevo."
        Context.LoadGroup -> "No pudimos cargar el grupo. Intentá de nuevo."
        Context.RotateInvite -> "No pudimos generar un nuevo código. Intentá de nuevo."
        Context.Settlement -> "No pudimos registrar el pago. Intentá de nuevo."
        Context.RemoveMember -> "No pudimos eliminar al miembro. Intentá de nuevo."
        Context.PeriodClose -> "No pudimos cambiar el estado del período. Intentá de nuevo."
        Context.Generic -> "Algo salió mal. Intentá de nuevo."
    }
}
