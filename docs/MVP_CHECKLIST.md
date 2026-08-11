# Checklist MVP — verificación en dispositivo

Usá dos cuentas (email o Google) en emulador/dispositivo.

## Auth

- [ ] Registrarse con email y contraseña
- [ ] Cerrar sesión
- [ ] Iniciar sesión de nuevo
- [ ] (Opcional) Continuar con Google
- [ ] (Opcional) Olvidé mi contraseña

## Grupos e invitaciones

- [ ] Usuario A crea un grupo
- [ ] En Configuración ve el código de invitación
- [ ] Compartir código (share sheet)
- [ ] Usuario B se une con el código
- [ ] Ambos ven al otro en Miembros

## Gastos

- [ ] Agregar gasto (concepto, monto, quién pagó, fecha con date picker)
- [ ] Al guardar aparece snackbar "Gasto guardado"
- [ ] El gasto figura en Gastos del mes actual
- [ ] Resumen actualiza total / pagó / le corresponde / saldo
- [ ] Abrir detalle, editar, ver snackbar "Gasto actualizado"
- [ ] Eliminar gasto (confirmación) y ver snackbar "Gasto eliminado"
- [ ] Saldos recalculados tras editar/eliminar

## Período

- [ ] Navegar mes anterior / siguiente en Resumen
- [ ] Un gasto con fecha de otro mes aparece solo en ese período

## Cierre

- [ ] Cerrar sesión desde Home o Configuración del grupo

Si algo falla, anotá pantalla + mensaje + Logcat (`cuentasclaras` / `supabase`).
