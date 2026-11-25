# Ordenier (backend)

## Variables de entorno

El backend toma su configuración de base de datos y de firma de JWT solo de estas cuatro variables. No uses otros nombres (por ejemplo `SPRING_DATASOURCE_URL`).

| Nombre | Formato | Ejemplo |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://<host>:<puerto>/<base>` | `jdbc:postgresql://localhost:5432/ordenier` |
| `DB_USER` | texto no vacío | `postgres` |
| `DB_PASSWORD` | texto no vacío | `cambia-esto` |
| `JWT_SECRET` | al menos 32 bytes en UTF-8 | (genera uno, ver abajo) |

### Pasos en PowerShell (desde `ordenier/`)

1. Copia el ejemplo: `Copy-Item .env.example .env`
2. Edita `.env` con tus valores (una línea `NOMBRE=valor`, sin comillas). `.env` está ignorado por git: no lo subas.
3. Carga las variables en la sesión actual (nota el punto inicial): `. .\scripts\load-env.ps1`
4. Con PostgreSQL arriba (Docker), arranca el backend: `.\mvnw spring-boot:run`

El script solo define variables de esta sesión de PowerShell (reemplaza las que ya existan) y no toca las variables permanentes de Windows. Hay que volver a cargarlas en cada terminal nueva.

### Generar un `JWT_SECRET`

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

Pega el resultado como valor de `JWT_SECRET` en `.env`.

### Pruebas

`.\mvnw.cmd test` no necesita `.env`, el script ni las cuatro variables. Solo requiere Docker (Testcontainers).

### Errores comunes

- `'url' must start with "jdbc"`: `DB_URL` está mal escrita, normalmente una variable permanente de Windows. Corrige `DB_URL` en `.env` (debe empezar con `jdbc:postgresql://`) y vuelve a cargar con `. .\scripts\load-env.ps1`.
- Si el backend dice que falta una variable o que una es inválida, el mensaje nombra cuál; corrígela en `.env` y carga de nuevo.
- Si PowerShell bloquea el script por la política de ejecución, en esa sesión: `Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned`
