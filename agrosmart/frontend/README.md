# 🖥️ Frontend — AgroSmart

Interfaz web (HTML + Bootstrap 5 + JavaScript puro, sin build) servida por NGINX.
Consume el backend **a través del API Gateway** (`https://host:8443/api`).

Tiene 3 secciones funcionales: **Dashboard**, **Sensores** y **Alertas**, además
del **Login** con JWT.

---

## 🚀 Cómo verlo

### Opción 1 — Modo DEMO (sin backend, ahora mismo)
Abre [`index.html`](index.html) directamente en el navegador (doble clic) o sírvelo
con NGINX. Con `DEMO_MODE: true` en [`config.js`](config.js) verás **todo funcionando
con datos simulados**: login (`admin` / `admin123`), lecturas que cambian cada 5 s,
gráficas de historial y alertas que puedes resolver.

### Opción 2 — Con Docker (junto al resto del sistema)
```bash
docker compose up -d --build
# Frontend: http://localhost:8080
```

---

## 🔌 Conectar con tu nube (cuando el backend esté listo)

Edita **solo** [`config.js`](config.js):

```js
window.AGROSMART_CONFIG = {
  API_BASE_URL: "https://TU_IP_O_DOMINIO:8443/api",  // ← tu API Gateway
  DEMO_MODE: false,                                   // ← apaga la demo
  REFRESH_INTERVAL_MS: 5000,
  HISTORY_POINTS: 30
};
```

> ⚠️ **Certificado autofirmado:** la primera vez, abre `https://TU_IP:8443` en el
> navegador y acepta la advertencia de seguridad. Si no, el navegador bloqueará
> (por TLS) las peticiones del frontend al gateway.

No hay nada más que cambiar: toda la app ya está programada contra los endpoints
reales. El modo DEMO y el modo real comparten exactamente el mismo código de UI.

---

## 📑 Contrato de API que el backend debe cumplir

El frontend espera estos endpoints (vía gateway, con prefijo `/api`). Acepta tanto
`camelCase` como `snake_case` en los campos JSON, así que el backend puede usar el
que prefiera.

### 🔑 Autenticación — `auth-service`

**`POST /api/auth/login`** — público (sin token)
```jsonc
// request
{ "username": "admin", "password": "admin123" }

// response 200
{
  "token": "<JWT>",
  "username": "admin",
  "rol": "ADMIN",                 // o "AGRICULTOR"
  "nombre_completo": "Administrador del Sistema"
}
// response 401 si las credenciales son inválidas
```
A partir de aquí, el frontend envía `Authorization: Bearer <JWT>` en todo lo demás.

### 🌡️ Sensores y lecturas — `sensor-service`

**`GET /api/sensores`** → catálogo de sensores
```jsonc
[
  {
    "id": 1,
    "nombre": "Sensor de Temperatura Norte",
    "tipo": "temperatura",         // temperatura | humedad | ph
    "zona": "zona1",
    "unidad": "C",                 // C | % | pH
    "valor_min_esperado": 18.0,
    "valor_max_esperado": 30.0,
    "activo": true
  }
]
```

**`GET /api/sensores/lecturas`** → última lectura de cada sensor
```jsonc
[
  { "id": 101, "sensor_id": 1, "valor": 24.5, "timestamp_lectura": "2026-05-30T18:00:00Z" }
]
```

**`GET /api/sensores/{id}/lecturas?limit=30`** → historial reciente de un sensor
(mismo formato que el anterior, ordenado del más antiguo al más reciente).

### 🚨 Alertas — `alert-service`

**`GET /api/alertas`** → todas las alertas
**`GET /api/alertas?resuelta=false`** → solo las activas
```jsonc
[
  {
    "id": 5,
    "sensor_id": 1,
    "tipo_alerta": "temperatura",
    "mensaje": "Temperatura demasiado alta en zona1: 37°C",
    "valor_detectado": 37.0,
    "severidad": "ALTA",           // BAJA | MEDIA | ALTA | CRITICA
    "fecha_generacion": "2026-05-30T18:00:00Z",
    "resuelta": false,
    "fecha_resolucion": null
  }
]
```

**`POST /api/alertas/{id}/resolver`** → marca la alerta como resuelta
(puede devolver la alerta actualizada o `204 No Content`).

---

## 📂 Estructura de archivos

```
frontend/
├── index.html        ← estructura de la UI (login + las 3 secciones)
├── config.js         ← ÚNICO archivo a editar para conectar la nube
├── css/
│   └── styles.css    ← estilos propios (sobre Bootstrap)
└── js/
    ├── api.js        ← capa de acceso al backend (fetch + JWT + normaliza campos)
    ├── mock.js       ← backend simulado (solo en DEMO_MODE)
    └── app.js        ← lógica de UI: login, navegación y render de cada sección
```

### Responsabilidad de cada archivo
- **`api.js`** decide, según `DEMO_MODE`, si llama al gateway real o a `mock.js`.
  También guarda el JWT en `localStorage`, lo adjunta en cada petición y cierra
  sesión automáticamente ante un `401`.
- **`app.js`** no sabe nada de HTTP: solo llama a `Api.*` y pinta los resultados.
  Así, el día de mañana, cambiar el backend no toca la UI.
- **`mock.js`** reproduce los datos de `db/init.sql` y simula el sensor-service
  (una lectura nueva por sensor cada 5 s) y el motor de reglas del alert-service.

---

## 🔐 Notas de seguridad
- El token se guarda en `localStorage` bajo `agrosmart_token`.
- CORS ya está habilitado en el gateway (`allowedOrigins: "*"`).
- Cambia las credenciales por defecto (`admin/admin123`) tras el primer login.
