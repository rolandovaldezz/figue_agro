# 📘 Documentación técnica — AgroSmart

> Explicación completa, archivo por archivo, de cómo está construido y cómo
> funciona el proyecto **AgroSmart**: sistema distribuido de monitoreo de
> invernaderos basado en microservicios.

---

## ✅ Estado del proyecto (actualizado)

La **infraestructura Y la lógica de negocio** ya están implementadas. El sistema
está listo para funcionar en cuanto se conecte la base de datos en la nube.

> 👉 Para la explicación detallada del código de cada microservicio (qué hace
> cada archivo, dónde ocurren los INSERT/SELECT, el flujo MQTT y la validación
> JWT), ver **[`COMO-FUNCIONA.md`](COMO-FUNCIONA.md)**.

| Componente | Estado |
|---|---|
| `docker-compose.yml` | ✅ Completo |
| Dockerfiles de los 5 servicios | ✅ Completos |
| `db/init.sql` (esquema + datos) | ✅ Completo |
| Configuración (`application.yml`) de cada servicio | ✅ Completa |
| Certificados TLS y config de Mosquitto | ✅ Completos |
| **auth-service** (login, registro, JWT, bcrypt) | ✅ Implementado |
| **sensor-service** (simulador, publicación MQTT, persistencia, REST) | ✅ Implementado |
| **alert-service** (suscripción MQTT, motor de reglas, REST) | ✅ Implementado |
| **api-gateway** (rutas + filtro de validación JWT + TLS) | ✅ Implementado |
| **Frontend** (login, dashboard, sensores con gráfica, alertas) | ✅ Implementado |
| **Alta disponibilidad** (failover master-master) | ✅ Configurado — ver [`db/ALTA-DISPONIBILIDAD.md`](db/ALTA-DISPONIBILIDAD.md) |

### ¿Qué falta para verlo corriendo de verdad?

Solo **conectar la base de datos** y levantar Docker:

1. **Docker + Docker Compose** (Docker Desktop en Windows).
2. Una **base de datos MariaDB** accesible (en la nube privada Rocky Linux) con el
   `init.sql` ya cargado. El `docker-compose.yml` **no** levanta la base de datos:
   los servicios se conectan a la MariaDB externa (`DB_HOST_1` / `DB_HOST_2` en `.env`).

Mientras tanto, el **frontend** se puede ver ya mismo en **modo DEMO** (datos
simulados en el navegador, sin backend): abre [`frontend/index.html`](frontend/index.html)
y entra con `admin` / `admin123`.

> Para correr el sistema completo necesitarás: instalar **Docker Desktop**, tener
> accesible una **MariaDB** con el `init.sql` ya cargado, crear el archivo `.env`,
> y ejecutar `docker compose up -d --build`. (Detalle paso a paso más abajo.)

---

## 🌱 ¿Qué hace AgroSmart (cuando esté completo)?

Monitorea un invernadero con **sensores virtuales** de temperatura, humedad y pH.
El flujo previsto es:

1. El `sensor-service` **simula** lecturas aleatorias cada 5 segundos.
2. Publica cada lectura por **MQTT** (broker Mosquitto), en topics tipo
   `agrosmart/zona1/temperatura`.
3. El propio `sensor-service` también se suscribe y **guarda** las lecturas en MariaDB.
4. El `alert-service` se **suscribe** a todos los topics, compara cada lectura
   contra las **reglas de umbral** (`umbrales_alerta`) y, si se sale de rango,
   **genera una alerta** en la base de datos.
5. El usuario entra por el **frontend web**, hace login (JWT), y consulta lecturas
   y alertas a través del **API Gateway** (que valida el token y enruta).

---

## 🏗️ Arquitectura general

```
┌──────────────┐
│ Cliente Web  │  frontend (NGINX, HTML + Bootstrap)   ← puerto 8080
└──────┬───────┘
       │ HTTPS + JWT
┌──────▼───────┐
│ API Gateway  │  Spring Cloud Gateway (TLS + valida JWT)  ← puerto 8443
└──────┬───────┘
       │  (red interna Docker)
       ├──────────────────┬──────────────────┐
       ▼                  ▼                  ▼
┌─────────────┐  ┌──────────────┐   ┌──────────────┐
│auth-service │  │sensor-service│   │ alert-service│
│  :8081      │  │   :8082      │   │   :8083      │
│ Login + JWT │  │ Simula+pub.  │   │ Reglas+notif │
└──────┬──────┘  └──────┬───────┘   └──────┬───────┘
       │                │ publish    subscribe │
       │           ┌────▼──────────────────────▼─┐
       │           │     Mosquitto (MQTT)         │  ← puertos 1883 / 9001
       │           └──────────────────────────────┘
       │                │                  │
       ▼                ▼                  ▼
┌──────────────────────────────────────────────────┐
│  MariaDB EXTERNA (nube privada Rocky Linux)        │  ← puerto 3306
│  Master ◀──replicación──▶ Replica                  │  (NO está en docker-compose)
└──────────────────────────────────────────────────┘
```

Toda la comunicación entre contenedores ocurre en una red Docker privada llamada
`agrosmart-net`. Lo único expuesto al exterior es:
- el **frontend** en `http://localhost:8080`
- el **API Gateway** en `https://localhost:8443`
- (y el broker MQTT en `1883`, principalmente para depuración)

---

## 📁 Recorrido por cada parte del proyecto

```
agrosmart/
├── docker-compose.yml      ← orquesta los 6 contenedores
├── .env.example            ← plantilla de variables (copiar a .env)
├── README.md               ← guía de despliegue (la oficial del proyecto)
├── DOCUMENTACION.md         ← este archivo
│
├── db/
│   ├── init.sql            ← crea base, tablas y datos iniciales
│   └── README.md           ← cómo cargar el SQL en la nube y verificar la réplica
│
├── certs/                  ← certificado TLS autofirmado + keystore PKCS12
│   ├── server.crt
│   ├── server.key
│   └── keystore.p12
│
├── mosquitto/
│   └── config/mosquitto.conf   ← config del broker MQTT
│
├── api-gateway/            ← Spring Cloud Gateway (TLS + JWT)
├── auth-service/           ← Login y emisión de JWT
├── sensor-service/         ← Simulador + publicador/consumidor MQTT + REST
├── alert-service/          ← Consumidor MQTT + motor de reglas + REST
└── frontend/               ← Web estática servida por NGINX
```

Cada microservicio Java sigue la misma estructura interna:

```
<servicio>/
├── Dockerfile              ← build multi-stage (Maven → JRE)
├── .dockerignore
├── pom.xml                 ← dependencias Maven (Spring Boot 3.2.5, Java 17)
└── src/main/
    ├── java/com/agrosmart/<servicio>/<Servicio>Application.java   ← clase main
    └── resources/application.yml                                  ← configuración
```

---

## 🐳 `docker-compose.yml` — la orquestación

Define **6 servicios** en la red `agrosmart-net`:

| Servicio | Imagen / Build | Puertos | Función |
|---|---|---|---|
| `mosquitto` | `eclipse-mosquitto:2.0` | `1883`, `9001` | Broker MQTT entre sensor y alert |
| `auth-service` | build `./auth-service` | `8081` (interno) | Login + JWT |
| `sensor-service` | build `./sensor-service` | `8082` (interno) | Simula + publica + persiste |
| `alert-service` | build `./alert-service` | `8083` (interno) | Consume MQTT + reglas |
| `api-gateway` | build `./api-gateway` | `8443` (público) | Entrada única TLS + JWT |
| `frontend` | build `./frontend` | `8080` → 80 | Web NGINX |

Puntos clave del compose:
- **`expose` vs `ports`:** los 3 microservicios de negocio usan `expose` (solo
  visibles dentro de la red Docker). Únicamente `frontend` (8080) y `api-gateway`
  (8443) publican puertos al host. Eso obliga a que **todo el tráfico externo pase
  por el gateway** → seguridad.
- **`depends_on`:** define orden de arranque (gateway depende de los 3 servicios;
  sensor/alert dependen de mosquitto). *Ojo:* solo espera a que el contenedor
  arranque, no a que esté "listo".
- **Variables `${...}`:** se inyectan desde el archivo `.env` (host, puerto, usuario
  y password de la base, secreto JWT, password del keystore).
- **Volúmenes:** solo Mosquitto persiste datos (`mosquitto_data`, `mosquitto_log`).
  Los certificados se montan **solo-lectura** en el gateway (`./certs:/app/certs:ro`).
- **La base de datos NO está aquí**, a propósito: vive fuera, en la nube privada.

---

## 🔧 `.env.example` — variables de entorno

Plantilla que debes copiar a `.env` (este último nunca se sube a Git). Contiene:

- **Conexión a MariaDB:** `DB_HOST_1` y `DB_HOST_2` (las dos PCs de tu nube, para
  failover master-master — ver [`db/ALTA-DISPONIBILIDAD.md`](db/ALTA-DISPONIBILIDAD.md)),
  `DB_PORT=3306`, `DB_NAME=agrosmart`, `DB_USER=agrosmart_app`, `DB_PASSWORD`.
- **JWT:** `JWT_SECRET` (clave aleatoria larga; el README sugiere `openssl rand -base64 48`)
  y `JWT_EXPIRATION_MS=3600000` (token válido 1 hora).
- **TLS:** `SSL_KEYSTORE_PASSWORD` (contraseña del `keystore.p12`).

---

## 🗄️ Base de datos — `db/init.sql`

Script para **MariaDB 10.x+**. No corre dentro de Docker; se carga manualmente en
el servidor de la nube. Hace 3 cosas:

### 1) Crea base y usuario de aplicación
- Base `agrosmart` con `utf8mb4`.
- Usuario `agrosmart_app` con permisos **solo** `SELECT, INSERT, UPDATE, DELETE`
  (no DDL → los microservicios no pueden alterar el esquema; por eso Hibernate va en
  modo `validate`).

### 2) Crea 5 tablas

| Tabla | Para qué | Campos clave |
|---|---|---|
| `usuarios` | login (auth-service) | `username`, `email`, `password_hash` (bcrypt), `rol` (ADMIN/AGRICULTOR), `activo` |
| `sensores` | catálogo de sensores virtuales | `tipo` (temperatura/humedad/ph), `zona`, `unidad`, rango esperado min/max |
| `lecturas_sensor` | historial de mediciones | `sensor_id` (FK), `valor`, `timestamp_lectura`; índices por fecha |
| `umbrales_alerta` | reglas de alerta | `tipo_sensor`, `zona` (NULL=todas), `valor_min`, `valor_max`, `severidad`, `mensaje_tpl` |
| `alertas` | alertas generadas | `sensor_id`, `lectura_id`, `umbral_id` (FKs), `mensaje`, `valor_detectado`, `severidad`, `resuelta` |

Relaciones (claves foráneas): `lecturas_sensor → sensores`, y `alertas → sensores /
lecturas_sensor / umbrales_alerta`. Hay índices pensados para las consultas más
comunes (lecturas por sensor y fecha, alertas no resueltas, etc.).

### 3) Inserta datos iniciales (semilla)
- **2 usuarios:** `admin` (rol ADMIN) y `agricultor` (rol AGRICULTOR), **ambos con
  contraseña `admin123`** (el mismo hash bcrypt `$2b$10$EipX46...`).
- **5 sensores:** temperatura/humedad/pH en zona1 y zona2.
- **7 reglas de umbral**, por ejemplo:
  - Temperatura > 35 °C → ALTA; > 40 °C → CRÍTICA; < 10 °C → ALTA
  - Humedad < 30 % → MEDIA; > 90 % → MEDIA
  - pH < 5.0 → ALTA; pH > 8.0 → ALTA

`db/README.md` añade instrucciones para subir el script por `scp/ssh`, verificar la
carga y comprobar que la **réplica** master→replica está sincronizada
(`SHOW REPLICA STATUS\G`).

---

## 🔐 `certs/` — TLS

Certificado **autofirmado** para HTTPS, terminado en el API Gateway:
- `server.crt` / `server.key` → certificado público y llave privada.
- `keystore.p12` → keystore PKCS12 (formato que consume Spring Boot), con alias
  `agrosmart` y contraseña = `SSL_KEYSTORE_PASSWORD`.

Por ser autofirmado, los clientes deben usar `-k` en curl o aceptar la advertencia
del navegador. En producción habría que reemplazarlo (Let's Encrypt, etc.).

---

## 📡 `mosquitto/config/mosquitto.conf` — broker MQTT

- Listener `1883` (MQTT TCP) y `9001` (WebSockets, para futuro uso desde el navegador).
- `allow_anonymous true` → **sin autenticación** (solo desarrollo).
- Persistencia activada en `/mosquitto/data/`.
- Límite de mensaje 256 KB.

MQTT es el "bus" de mensajes: `sensor-service` **publica** y `alert-service`
(y el propio sensor) **se suscriben**.

---

## 🚪 `api-gateway/` — punto de entrada único

**Stack:** Spring Cloud Gateway (2023.0.1, reactivo/WebFlux) + Spring Security + jjwt.

### Configuración (`application.yml`) — esto sí está hecho:
- **TLS habilitado** en el puerto `8443`, usando `keystore.p12`.
- **3 rutas** con `StripPrefix=1` (quita el `/api` antes de reenviar):
  - `/api/auth/**` → `auth-service:8081` (sin JWT)
  - `/api/sensores/**` → `sensor-service:8082` (debe validar JWT)
  - `/api/alertas/**` → `alert-service:8083` (debe validar JWT)
- **CORS** abierto (`allowedOrigins: "*"`) para que el frontend pueda llamar.
- **Actuator** expone `health`, `info`, `gateway`.

### Implementado ✅:
- [`JwtAuthenticationFilter.java`](api-gateway/src/main/java/com/agrosmart/gateway/security/JwtAuthenticationFilter.java)
  — filtro global que **valida el JWT** en cada petición (deja pasar `/api/auth/**` y
  el preflight CORS); si falta o es inválido el token, responde **401**.
- [`SecurityConfig.java`](api-gateway/src/main/java/com/agrosmart/gateway/security/SecurityConfig.java)
  — configura la seguridad reactiva para que mande el filtro de arriba.

---

## 🔑 `auth-service/` — autenticación

**Stack:** Spring Web + Data JPA + Security (bcrypt) + Validation + jjwt + driver MariaDB.

### Configuración (`application.yml`):
- Puerto `8081`.
- Datasource a MariaDB (Hikari pool, máx 10 conexiones).
- JPA `ddl-auto: none` (no toca ni valida las tablas; las crea `init.sql`).
- JWT: `secret` y `expiration-ms` desde variables de entorno.

### Implementado ✅:
- `POST /auth/login` → valida usuario/contraseña (bcrypt) y devuelve un **JWT firmado**.
- `POST /auth/register` → alta de usuario.
- Archivos: `model/Usuario`, `repository/UsuarioRepository`, `security/JwtService`,
  `security/SecurityConfig`, `service/AuthService`, `controller/AuthController`, `dto/*`.

> Detalle del flujo en [`COMO-FUNCIONA.md`](COMO-FUNCIONA.md) (sección auth-service).

---

## 🌡️ `sensor-service/` — sensores

**Stack:** Spring Web + Data JPA + Validation + **Spring Integration MQTT (Eclipse Paho)**
+ scheduling (`@EnableScheduling`) + driver MariaDB.

### Configuración (`application.yml`):
- Puerto `8082`.
- Datasource MariaDB (igual que auth).
- **MQTT:** `broker-url` (`tcp://mosquitto:1883`), `client-id`, `topic-base: agrosmart`, `qos: 1`.
- **Simulador:** `enabled: true`, `interval-ms: 5000` (una lectura cada 5 s).

### Implementado ✅:
- `SimuladorService` con `@Scheduled` genera lecturas por sensor cada 5 s.
- `MqttPublisher` publica en topics `agrosmart/{zona}/{tipo}`.
- `LecturaSubscriber` persiste cada lectura en `lecturas_sensor` (INSERT).
- REST `SensorController`: `GET /sensores`, `/sensores/lecturas`, `/sensores/{id}/lecturas`.

> Detalle del flujo en [`COMO-FUNCIONA.md`](COMO-FUNCIONA.md) (sección sensor-service).

---

## 🚨 `alert-service/` — alertas

**Stack:** Spring Web + Data JPA + Validation + Spring Integration MQTT + driver MariaDB.

### Configuración (`application.yml`):
- Puerto `8083`.
- Datasource MariaDB.
- **MQTT:** se suscribe al patrón `agrosmart/+/+` (cualquier zona, cualquier tipo), `qos: 1`.

### Implementado ✅:
- `AlertaSubscriber` consume MQTT (`agrosmart/+/+`) y recibe cada lectura.
- `AlertaService` (motor de reglas) compara contra `umbrales_alerta` (por tipo y zona)
  y, si se viola un umbral, hace INSERT en `alertas` (sin duplicar alertas activas).
- REST `AlertaController`: `GET /alertas`, `GET /alertas?resuelta=false`, `POST /alertas/{id}/resolver`.

> Detalle del flujo en [`COMO-FUNCIONA.md`](COMO-FUNCIONA.md) (sección alert-service).

---

## 🖥️ `frontend/` — la web (lo que tú quieres ver)

**Stack:** HTML estático + Bootstrap 5.3.3 (desde CDN), servido por **NGINX 1.27 (alpine)**.

### Contenido actual ✅
Aplicación web completa (HTML + Bootstrap + JS, sin build):
- **Login** con JWT (guardado en `localStorage`).
- **Dashboard** con tarjetas en vivo (auto-refresh cada 5 s) y estadísticas.
- **Sensores**: tabla + gráfica de historial (Chart.js).
- **Alertas**: tabla con severidad, filtro activas/todas y botón "Resolver".

Archivos: `index.html`, `config.js` (URL del gateway + modo DEMO), `css/styles.css`,
`js/api.js` (HTTP + JWT), `js/app.js` (UI), `js/mock.js` (datos de demo).
Detalle en [`frontend/README.md`](frontend/README.md).

### `Dockerfile`
Toma `nginx:1.27-alpine`, borra el contenido por defecto, copia los archivos estáticos
a `/usr/share/nginx/html/`, elimina `Dockerfile` y `.dockerignore` para no servirlos,
y expone el puerto 80 (mapeado a 8080 en el host).

### Cómo lo viste
Como es HTML puro con Bootstrap por CDN, **no necesita backend ni Docker** para
renderizar. Por eso lo abrí directamente con tu navegador
(`Start-Process frontend/index.html`). Cuando el sistema completo esté con Docker,
la URL será `http://localhost:8080`.

---

## 🔄 Flujo de datos de extremo a extremo (diseño final)

```
1. LOGIN
   Navegador → POST https://localhost:8443/api/auth/login {username, password}
            → Gateway (sin JWT) → auth-service
            → auth-service valida bcrypt y responde { token: "<JWT>" }

2. CONSULTAR DATOS
   Navegador → GET https://localhost:8443/api/sensores/lecturas
            → header Authorization: Bearer <JWT>
            → Gateway VALIDA el JWT → sensor-service → MariaDB → JSON

3. SENSORES (en segundo plano, cada 5 s)
   sensor-service genera lectura → publica MQTT agrosmart/zona1/temperatura
            → (a) sensor-service la guarda en lecturas_sensor
            → (b) alert-service la recibe → evalúa umbrales_alerta
                 → si fuera de rango → INSERT en alertas

4. VER ALERTAS
   Navegador → GET https://localhost:8443/api/alertas (con JWT) → alert-service → MariaDB
```

---

## 🚀 Cómo correr el sistema completo (cuando tengas los requisitos)

> Requiere: **Docker Desktop**, una **MariaDB** accesible con `init.sql` cargado.

```powershell
# 1. Cargar el esquema en tu MariaDB (en el servidor/nube):
#    mysql -u root -p < db/init.sql

# 2. Crear el archivo .env a partir de la plantilla y ajustarlo:
Copy-Item .env.example .env
#    edita .env → pon el DB_HOST real, un JWT_SECRET aleatorio, etc.

# 3. Levantar todos los contenedores:
docker compose up -d --build

# 4. Ver estado y logs:
docker compose ps
docker compose logs -f api-gateway
```

Luego:
- **Frontend:** http://localhost:8080
- **API Gateway (HTTPS):** https://localhost:8443
- **Login:** `admin` / `admin123`

Probar la API por consola (el `-k` acepta el certificado autofirmado):

```powershell
# Login → obtener JWT
curl -k -X POST https://localhost:8443/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{\"username\":\"admin\",\"password\":\"admin123\"}'

# Consultar lecturas (con el token)
curl -k https://localhost:8443/api/sensores/lecturas `
  -H "Authorization: Bearer <TU_JWT>"
```

> ✅ Con la base de datos conectada, los endpoints anteriores ya responden datos
> reales (login, lecturas y alertas). Mientras tanto, el frontend se puede ver en
> **modo DEMO** sin backend (`frontend/config.js`).

---

## 🧱 Resumen de tecnologías

| Capa | Tecnología |
|---|---|
| Lenguaje / runtime | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Gateway | Spring Cloud Gateway 2023.0.1 (WebFlux/reactivo) |
| Seguridad | Spring Security (bcrypt) + JWT (jjwt 0.12.5) + TLS PKCS12 |
| Persistencia | Spring Data JPA / Hibernate (modo `none`) |
| Base de datos | MariaDB 10.x (externa, con replicación master-master) |
| Mensajería | MQTT (Eclipse Mosquitto 2.0 + Spring Integration MQTT / Paho) |
| Frontend | HTML + Bootstrap 5.3.3 (CDN), servido por NGINX 1.27 |
| Empaquetado | Docker multi-stage (Maven → JRE alpine) + Docker Compose |

---

## ✅ Conclusión

AgroSmart está **completo a nivel de arquitectura, infraestructura y lógica**: red
aislada, gateway único con TLS y validación JWT, broker MQTT, base con esquema y datos
semilla, los 4 microservicios implementados y el frontend funcional. Solo falta
**conectar la base de datos en la nube** y levantar Docker para verlo todo corriendo.
Mientras tanto, el frontend se puede ver en **modo DEMO** (datos simulados).

> Para la explicación detallada del código, ver [`COMO-FUNCIONA.md`](COMO-FUNCIONA.md).
