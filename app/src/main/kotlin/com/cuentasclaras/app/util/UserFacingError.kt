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
        GroupAvatar,
        GroupTheme,
        GroupName,
        Category,
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
        message.contains("invalid installment count") ->
            "La cantidad de cuotas tiene que ser entre 2 y 48."
        message.contains("invalid installment start index") ->
            "La cuota actual tiene que estar entre 1 y el total de cuotas."
        message.contains("cannot delete installment in closed period") ->
            "No se puede borrar toda la serie: alguna cuota está en un período cerrado. " +
                "Borralas de a una desde los meses abiertos."
        message.contains("only creator or owner can delete installment series") ->
            "Solo quien creó la serie o el administrador puede eliminarla."
        message.contains("installment series not found") ->
            "No encontramos esa serie de cuotas."
        message.contains("period is closed") ->
            "Este período está cerrado. Reabrilo para hacer cambios."
        message.contains("only owner can close") || message.contains("only owner can reopen") ->
            "Solo el administrador puede cerrar o reabrir el período."
        message.contains("only owner can set group avatar") ||
            message.contains("only owner can clear group avatar") ->
            "Solo el administrador puede cambiar la foto del grupo."
        message.contains("only owner can set group theme") ->
            "Solo el administrador puede cambiar el tema del grupo."
        message.contains("only owner can set group name") ->
            "Solo el administrador puede cambiar el nombre del grupo."
        message.contains("invalid name") ->
            "Ingresá un nombre para el grupo."
        message.contains("invalid theme") ->
            "Ese tema no es válido."
        message.contains("category is required") ->
            "Elegí una categoría para el gasto."
        message.contains("category not found") ->
            "Esa categoría ya no existe. Elegí otra."
        message.contains("category name already exists") ->
            "Ya hay una categoría con ese nombre en el grupo."
        message.contains("invalid category name") ->
            "Ingresá un nombre de categoría de hasta 40 caracteres."
        message.contains("invalid category icon") ->
            "Elegí un ícono válido para la categoría."
        message.contains("only creator can edit category") ||
            message.contains("only creator can delete category") ->
            "Solo quien creó la categoría o el administrador puede modificarla o eliminarla."
        message.contains("cannot delete default category") ||
            message.contains("cannot edit default category") ->
            "Sin categoría no se puede modificar ni eliminar."
        message.contains("category is in use") ->
            "No se puede eliminar: hay gastos que usan esta categoría."
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
        Context.GroupAvatar -> "No pudimos actualizar la foto del grupo. Intentá de nuevo."
        Context.GroupTheme -> "No pudimos actualizar el tema del grupo. Intentá de nuevo."
        Context.GroupName -> "No pudimos actualizar el nombre del grupo. Intentá de nuevo."
        Context.Category -> "No pudimos guardar la categoría. Intentá de nuevo."
        Context.Generic -> "Algo salió mal. Intentá de nuevo."
    }
}
