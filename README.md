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
supabase/functions → Edge Functions (push FCM)
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

### 3. Notificaciones push (FCM)

Hace falta un proyecto Firebase (puede ser el mismo Google Cloud del OAuth). El backend sigue siendo Supabase.

1. En Firebase Console: agregá una app Android `com.cuentasclaras.app` con el SHA-1 de debug.
2. Descargá `google-services.json` y reemplazá [`app/google-services.json`](app/google-services.json) (el del repo es un placeholder para CI).
3. Ejecutá la migración [`supabase/migrations/20260813140000_push_notifications.sql`](supabase/migrations/20260813140000_push_notifications.sql) en el SQL Editor.
4. Deploy de la Edge Function:

```bash
supabase functions deploy notify-expense --no-verify-jwt
```

5. Secrets de la function (Dashboard → Edge Functions → Secrets, o CLI):

```bash
supabase secrets set PUSH_WEBHOOK_SECRET=un-secreto-largo
supabase secrets set FIREBASE_SERVICE_ACCOUNT='{"type":"service_account",...}'
```

`FIREBASE_SERVICE_ACCOUNT` es el JSON de una service account de Firebase/Google Cloud con permiso de Firebase Cloud Messaging. No lo pongas en la APK.

`SUPABASE_URL` y `SUPABASE_SERVICE_ROLE_KEY` los inyecta el runtime.

6. Database Webhook (Dashboard → Database → Webhooks):
   - Table: `push_jobs`
   - Events: Insert
   - URL: `https://YOUR_PROJECT.supabase.co/functions/v1/notify-expense`
   - HTTP header: `Authorization` = `Bearer un-secreto-largo` (el mismo `PUSH_WEBHOOK_SECRET`)

Al iniciar sesión la app pide permiso de notificaciones (Android 13+) y registra el token. Logout borra el token de este dispositivo.

### 4. Build y tests

```bash
./gradlew :domain:test
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

En GitHub Actions (`.github/workflows/ci.yml`) se corren esos mismos checks en cada push/PR a `main`.

### 5. Correr en emulador o dispositivo

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
- Tras crear un grupo, la app abre **Configuración** para invitar (hace falta ≥2 miembros para cargar gastos).
- Invitaciones: copiar/compartir código, o abrir `cuentasclaras://join/{CODIGO}` si la otra persona ya tiene la app.
- Un gasto creado o editado por otro miembro del grupo dispara una notificación push. Tocarla abre `cuentasclaras://group/{groupId}/expense/{expenseId}`.

## Reglas de negocio (MVP)

- Crear un gasto requiere al menos **2 miembros** en el grupo (invitar antes de cargar).
- Split EQUAL entre todos los miembros del grupo al momento de guardar.
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
