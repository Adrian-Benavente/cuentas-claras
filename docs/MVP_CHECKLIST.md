# Checklist MVP — verificación en dispositivo

Usá dos cuentas (email o Google) en emulador/dispositivo.

## Auth

- [ ] Registrarse con email y contraseña
- [ ] Cerrar sesión
- [ ] Iniciar sesión de nuevo
- [ ] (Opcional) Continuar con Google
- [ ] (Opcional) Olvidé mi contraseña

## Grupos e invitaciones

- [ ] Usuario A crea un grupo y aterriza en **Configuración** (invitar)
- [ ] OWNER puede **Agregar/Cambiar foto** del grupo (galería); se ve circular junto al nombre en Home y en la barra del grupo
- [ ] OWNER puede **Quitar foto**
- [ ] OWNER puede elegir **Tema del grupo** (5 swatches en Configuración); el color se aplica en pantallas del grupo/gastos
- [ ] En Home, cada grupo muestra un **swatch** del color de acento junto al avatar
- [ ] Con un solo miembro: no aparece FAB de gastos; Resumen/Gastos orientan a invitar
- [ ] Ve el código, **Copiar** muestra snackbar "Código copiado"
- [ ] **Compartir** abre share sheet con pasos + link `cuentasclaras://join/…`
- [ ] Owner: **Nuevo código** pide confirmación; código viejo deja de funcionar
- [ ] Usuario B se une pegando el código (o escribiéndolo)
- [ ] (Opcional) Deep link: `adb shell am start -a android.intent.action.VIEW -d "cuentasclaras://join/CODIGO"`
- [ ] Ambos ven al otro en Miembros
- [ ] OWNER puede **Eliminar** a un MEMBER (confirmación); no aparece acción sobre el admin
- [ ] Tras eliminar: desaparece de Miembros; gastos previos siguen en historial/resumen (como Ex-miembro si aplica)

## Gastos

- [ ] Flujo feliz: crear grupo → invitar → unirse → primer gasto
- [ ] Agregar gasto (concepto, monto, quién pagó, fecha con date picker)
- [ ] Al guardar aparece snackbar "Gasto guardado"
- [ ] El gasto figura en Gastos del mes actual
- [ ] Resumen actualiza total / pagó / le corresponde / saldo
- [ ] Abrir detalle, editar, ver snackbar "Gasto actualizado"
- [ ] Eliminar gasto (confirmación) y ver snackbar "Gasto eliminado"
- [ ] Saldos recalculados tras editar/eliminar
- [ ] (Legacy) Si hay un gasto creado solo, el aviso de reparto incompleto y re-guardar lo redistribuye
- [ ] **En cuotas**: switch al crear → total + K de N; se crean gastos `(K/N)`…`(N/N)` en meses sucesivos
- [ ] Preview muestra rango de cuotas, monto por cuota y hasta qué mes
- [ ] Empezar en K>1 no crea cuotas pasadas; montos siguen el plan del total original
- [ ] Navegar a un mes futuro y ver la cuota correspondiente en Resumen/Gastos
- [ ] Detalle de cuota: eliminar **Solo esta** o **Toda la serie**

## Período

- [ ] Navegar mes anterior / siguiente en Resumen (incluye meses futuros)
- [ ] Tap en mes/año abre selector de período (incluye futuros)
- [ ] Un gasto con fecha de otro mes aparece solo en ese período
- [ ] Resumen muestra chip **Abierto** / **Cerrado**
- [ ] OWNER: **Cerrar período** (confirmación) → chip Cerrado; MEMBER no ve la acción
- [ ] Con período cerrado: no se puede Marcar saldado / Deshacer
- [ ] Con período cerrado: no se puede editar ni eliminar un gasto de ese mes
- [ ] OWNER: **Reabrir período** vuelve a permitir mutaciones

## Deudas saldadas

- [ ] En Resumen, una sugerencia muestra **Marcar saldado**
- [ ] Al marcar, desaparece de “Para saldar” y aparece en **Saldados**
- [ ] Los saldos Pagó / Le corresponde / Saldo (por gastos) no cambian al marcar
- [ ] **Deshacer** vuelve a mostrar la sugerencia
- [ ] Un pago de otro mes no afecta el período actual

## Cierre

- [ ] Cerrar sesión desde Home o Configuración del grupo

## Offline (lectura)

- [ ] Con datos ya vistos: modo avión → Home y Grupo muestran banner “Sin conexión · mostrando datos guardados”
- [ ] Resumen / Gastos del grupo siguen visibles offline
- [ ] Intentar crear/editar gasto o marcar saldado offline → mensaje de que hace falta conexión
- [ ] Tras logout, la cache local no reaparece al volver a entrar (hasta sincronizar de nuevo)

Si algo falla, anotá pantalla + mensaje + Logcat (`cuentasclaras` / `supabase`).
