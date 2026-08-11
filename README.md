# Cuentas Claras

Aplicación Android para gastos compartidos entre parejas, familias y grupos pequeños.

## Stack

- Kotlin, Jetpack Compose, Material 3, Navigation, Hilt
- Dominio puro en módulo `:domain` (Money en unidades menores, sin `Float`/`Double`)
- Backend: Supabase (Auth + PostgreSQL + RLS)

## Estructura

```text
:domain   → modelo y lógica financiera (testeable JVM)
:app      → UI Compose, ViewModels, repositorios Supabase
supabase/migrations → schema, RPCs y políticas RLS
```

## Setup

### 1. Android

1. Abrí el proyecto en Android Studio / Cursor.
2. Copiá `local.properties.example` → `local.properties`.
3. Completá:

```properties
sdk.dir=/path/to/Android/Sdk
supabase.url=https://YOUR_PROJECT.supabase.co
supabase.anon.key=YOUR_ANON_KEY
google.web.client.id=YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com
```

Nunca commits `local.properties` ni la service-role key.

### 2. Supabase

1. Creá un proyecto en [Supabase](https://supabase.com).
2. Ejecutá la migración [`supabase/migrations/20260811120000_init.sql`](supabase/migrations/20260811120000_init.sql) en el SQL Editor.
3. Habilitá Auth:
   - Email/password
   - Google provider (con el mismo Web Client ID de OAuth)
4. En Google Cloud Console, creá OAuth client (Web) + Android client (package `com.cuentasclaras.app` + SHA-1 de debug/release).

### 3. Build y tests

```bash
./gradlew :domain:test
./gradlew :app:assembleDebug
```

### 4. Correr en emulador o dispositivo

`installDebug` necesita un emulador o teléfono con depuración USB.

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH="$ANDROID_HOME/platform-tools:$PATH"

adb devices   # debe listar un device
./gradlew :app:installDebug
adb shell am start -n com.cuentasclaras.app/.MainActivity
```

En Android Studio también podés usar Run ▶.

Para dejar `adb` disponible en zsh, agregá a `~/.zshrc`:

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

## Notas de uso

- Al volver a un grupo tras crear/editar/eliminar un gasto, la app **refresca** resumen e historial automáticamente.
- Tras guardar o eliminar verás un snackbar de confirmación.
- La fecha del gasto se elige con un date picker (no texto libre).

## Reglas de negocio (MVP)

- Split EQUAL entre todos los miembros del grupo.
- Redondeo: resto a los primeros `amount % n` miembros ordenados por `userId`.
- `balance = amountPaid - amountOwed` (positivo = debe recibir).
- Settlements sugeridos = greedy deudor→acreedor (no son pagos registrados).
- Período mensual derivado de `expense.date`.
- Editar/eliminar gasto: autor u OWNER.
- Autorización en RLS / RPCs del servidor.

## Checklist MVP

Ver también [`docs/MVP_CHECKLIST.md`](docs/MVP_CHECKLIST.md) para la verificación en dispositivo.

- [x] Registro / login / logout / reset password
- [x] Google Sign-In (Credential Manager + Supabase ID token)
- [x] Crear grupo + código de invitación
- [x] Unirse con código
- [x] Ver miembros
- [x] Crear / editar / eliminar gastos
- [x] Historial por mes
- [x] Resumen: total, pagó, le corresponde, saldo, quién debe a quién
- [x] Marcar / deshacer deudas saldadas (pagos de settlement por período)
- [x] Tests de dominio financiero

## Seguridad

Un usuario solo accede a grupos donde es miembro. Las policies RLS y las funciones `security definer` son la autoridad; la UI no es el control de acceso.

Más detalle en [`docs/SECURITY.md`](docs/SECURITY.md).
