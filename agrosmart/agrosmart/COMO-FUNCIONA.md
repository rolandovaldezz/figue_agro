# 🧠 Cómo funciona AgroSmart (guía para explicar el proyecto)

Esta guía explica, en orden y con palabras sencillas, **cómo funciona cada parte**
del sistema y **qué archivo hace qué**. Pensada para poder explicárselo al profesor.

---

## 1. La idea en una frase

AgroSmart es un sistema de **microservicios** que monitorea sensores de un
invernadero (temperatura, humedad, pH). Unos sensores virtuales generan datos,
se mandan por **MQTT**, se **guardan en MariaDB**, se **evalúan reglas** para crear
**alertas**, y todo se ve en una **web** protegida con **login JWT** detrás de un
**API Gateway con HTTPS**.

---

## 2. Las 6 piezas y cómo se conectan

```
Navegador (frontend)  ──HTTPS+JWT──►  API Gateway (8443)
                                          │ valida el JWT y enruta
              ┌───────────────────────────┼───────────────────────────┐
              ▼                            ▼                           ▼
     auth-service (8081)        sensor-service (8082)        alert-service (8083)
       login + JWT                simula + publica            consume + reglas
              │                       │     ▲                        ▲
              │                  publica│     │persiste                │consume
              │                        ▼     │                        │
              │                   ┌──────────┴────────────────────────┘
              │                   │        Mosquitto (MQTT, 1883)
              ▼                   ▼
        ┌──────────────────────────────────────────┐
        │   MariaDB master ↔ master  (nube, 3306)    │
        └──────────────────────────────────────────┘
```

- **Lo único expuesto al exterior** es el frontend (8080) y el gateway (8443). Los
  3 microservicios y Mosquitto viven en una red Docker privada.
- **Por qué microservicios:** cada parte hace una sola cosa, se puede escalar o
  reiniciar por separado, y se comunican por HTTP (vía gateway) y por MQTT (eventos).

---

## 3. auth-service — Login y JWT

**Carpeta:** `auth-service/src/main/java/com/agrosmart/auth/`

| Archivo | Qué hace |
|---|---|
| `model/Usuario.java` | Entidad JPA: mapea la tabla `usuarios` a un objeto Java |
| `repository/UsuarioRepository.java` | Acceso a BD: `findByUsername()` = el **SELECT** del login |
| `security/JwtService.java` | **Genera** el token JWT firmado con `JWT_SECRET` |
| `security/SecurityConfig.java` | Activa bcrypt y deja los endpoints abiertos (el gateway es quien protege) |
| `service/AuthService.java` | La lógica: verifica contraseña, hace el **UPDATE** de `ultimo_acceso`, pide el token |
| `controller/AuthController.java` | Endpoints `POST /auth/login` y `POST /auth/register` |
| `dto/*` | Objetos de entrada/salida (LoginRequest, LoginResponse, RegisterRequest) |

**Flujo del login (paso a paso):**
1. El navegador manda `POST /api/auth/login` con `{username, password}`.
2. `AuthService.login()` busca el usuario (**SELECT**) → `UsuarioRepository.findByUsername`.
3. Compara la contraseña con `passwordEncoder.matches(...)` → **bcrypt** (los hashes
   están en `usuarios.password_hash`, creados por `init.sql`).
4. Si coincide, actualiza `ultimo_acceso` (**UPDATE**) y `JwtService` genera el **JWT**.
5. Responde `{ token, username, rol, nombreCompleto }`.

> El JWT lleva dentro el username y el rol, firmado con una clave secreta. Nadie
> puede falsificarlo sin esa clave.

---

## 4. sensor-service — Simulador + MQTT + persistencia

**Carpeta:** `sensor-service/src/main/java/com/agrosmart/sensor/`

| Archivo | Qué hace |
|---|---|
| `model/Sensor.java` | Entidad de la tabla `sensores` (catálogo) |
| `model/Lectura.java` | Entidad de la tabla `lecturas_sensor` (historial) |
| `repository/SensorRepository.java` | `findByActivoTrue()` = sensores activos |
| `repository/LecturaRepository.java` | Última lectura y el historial de un sensor |
| `service/SimuladorService.java` | **Genera** lecturas cada 5 s y las **publica** por MQTT |
| `mqtt/MqttPublisher.java` | Cliente MQTT que publica los mensajes |
| `mqtt/LecturaSubscriber.java` | Se suscribe y **guarda** cada lectura (el **INSERT**) |
| `controller/SensorController.java` | `GET /sensores`, `/sensores/lecturas`, `/sensores/{id}/lecturas` |
| `dto/LecturaMensaje.java` | Forma del mensaje JSON que viaja por MQTT |

**Flujo de una lectura (lo más importante para explicar):**
1. `SimuladorService.simular()` corre cada 5 s (gracias a `@Scheduled` + `@EnableScheduling`).
2. Para cada sensor activo, genera un valor (80% dentro del rango, 20% fuera para que
   salten alertas en la demo).
3. Arma un JSON `LecturaMensaje` y lo **publica** en el topic `agrosmart/{zona}/{tipo}`
   (ej. `agrosmart/zona1/temperatura`) → `MqttPublisher.publicar()`.
4. Ese mensaje llega a Mosquitto y se reparte a **dos** suscriptores:
   - `LecturaSubscriber` (de este mismo servicio) → hace `lecturaRepository.save()`
     = **INSERT INTO lecturas_sensor**.
   - `AlertaSubscriber` (del alert-service) → evalúa reglas (ver siguiente sección).

> 💡 ¿Por qué publicar y luego suscribirse para guardar, en vez de guardar directo?
> Para **desacoplar**: el simulador solo "anuncia" la lectura; quién la guarda o quién
> la evalúa son independientes. Así funciona la arquitectura de eventos (pub/sub).

**Endpoints REST** (los consume el dashboard):
- `GET /sensores` → catálogo.
- `GET /sensores/lecturas` → última lectura de cada sensor.
- `GET /sensores/{id}/lecturas?limit=30` → historial para la gráfica.

---

## 5. alert-service — Motor de reglas

**Carpeta:** `alert-service/src/main/java/com/agrosmart/alert/`

| Archivo | Qué hace |
|---|---|
| `model/Umbral.java` | Entidad de `umbrales_alerta` (las reglas) |
| `model/Alerta.java` | Entidad de `alertas` (las alertas generadas) |
| `repository/UmbralRepository.java` | Reglas activas por tipo de sensor |
| `repository/AlertaRepository.java` | Listar alertas y comprobar duplicados |
| `mqtt/AlertaSubscriber.java` | Se suscribe a `agrosmart/+/+` y recibe cada lectura |
| `service/AlertaService.java` | **Motor de reglas**: compara y crea la alerta (**INSERT**) |
| `controller/AlertaController.java` | `GET /alertas`, `GET /alertas?resuelta=false`, `POST /alertas/{id}/resolver` |
| `dto/LecturaMensaje.java` | Misma forma del mensaje MQTT |

**Flujo de una alerta:**
1. `AlertaSubscriber` recibe por MQTT una lectura (sensorId, tipo, zona, valor).
2. `AlertaService.evaluar()` busca las reglas activas de ese tipo (**SELECT** en `umbrales_alerta`).
3. Para cada regla: si `valor < valor_min` o `valor > valor_max`, hay violación.
4. Para no repetir la misma alerta cada 5 s, comprueba si ya existe una **activa**
   para ese sensor+regla (`existsBySensorIdAndUmbralIdAndResueltaFalse`).
5. Si procede, crea la `Alerta` (mensaje, severidad, valor) → `alertaRepository.save()`
   = **INSERT INTO alertas**.
6. El usuario la ve en la web y puede pulsar **Resolver** → `POST /alertas/{id}/resolver`
   → marca `resuelta = true` y la fecha (**UPDATE**).

---

## 6. api-gateway — Puerta única (HTTPS + JWT)

**Carpeta:** `api-gateway/src/main/java/com/agrosmart/gateway/`

| Archivo / Config | Qué hace |
|---|---|
| `application.yml` | **TLS** (HTTPS con el keystore) y las **rutas** a los 3 servicios |
| `security/JwtAuthenticationFilter.java` | **Valida el JWT** en cada petición (salvo login) |
| `security/SecurityConfig.java` | Configura la seguridad reactiva para que el filtro de arriba mande |

**Qué hace en cada petición:**
1. Termina el **HTTPS** (descifra TLS con el certificado).
2. `JwtAuthenticationFilter` mira la ruta:
   - `/api/auth/**` → **pasa sin token** (login/registro son públicos).
   - cualquier otra → exige `Authorization: Bearer <JWT>`; si falta o es inválido → **401**.
3. Si el token es válido, **reenvía** la petición al microservicio interno
   (quitando el prefijo `/api` con `StripPrefix=1`):
   - `/api/sensores/**` → `sensor-service:8082`
   - `/api/alertas/**` → `alert-service:8083`

> Por eso los microservicios internos no necesitan validar el token: confían en que,
> si la petición les llega, el gateway ya la revisó. Y como no exponen puertos al
> exterior, **solo se puede entrar por el gateway**.

---

## 7. Mosquitto y la base de datos

- **Mosquitto** (`mosquitto/config/mosquitto.conf`) es el **broker MQTT**: el "cartero"
  que recibe los mensajes que publica el sensor-service y los entrega a los suscriptores.
- **MariaDB** vive en la nube (no en Docker). El esquema y los datos los crea
  `db/init.sql`. Está en **replicación master-master** entre dos PCs para que, si se
  apaga una, el sistema siga (ver `db/ALTA-DISPONIBILIDAD.md`).

---

## 8. El frontend

Web estática (HTML + Bootstrap + JS) servida por NGINX. Tiene login, dashboard
(tarjetas en vivo cada 5 s), sensores (tabla + gráfica de historial) y alertas
(con botón resolver). Habla **solo con el gateway**, mandando el JWT en cada llamada.
Detalle completo en `frontend/README.md`.

Tiene un **modo DEMO** (`frontend/config.js`) para verlo funcionar sin backend.
Cuando la nube esté lista: `DEMO_MODE: false` y `API_BASE_URL` con la IP del gateway.

---

## 9. Seguridad (resumen para el profe)

| Tema | Cómo se implementa |
|---|---|
| Cifrado en tránsito | **TLS/HTTPS** en el gateway (puerto 8443) |
| Autenticación | **JWT** firmado por auth-service, validado por el gateway |
| Contraseñas | **bcrypt** (no se guardan en texto plano) |
| Aislamiento | Los microservicios y la BD no se exponen; red Docker privada |
| Alta disponibilidad | Replicación **master-master** + failover en la conexión JDBC |

---

## 10. Cómo levantarlo mañana (cuando conectes la BD)

```powershell
# 1) En la nube: cargar el esquema (una vez)
#    mysql -u root -p < db/init.sql   (y configurar master-master: db/ALTA-DISPONIBILIDAD.md)

# 2) Crear el .env con las dos IPs de la nube y un JWT_SECRET largo
Copy-Item .env.example .env
#    edita .env -> DB_HOST_1, DB_HOST_2, JWT_SECRET (openssl rand -base64 48)

# 3) Levantar todo
docker compose up -d --build

# 4) Apuntar el frontend al backend real
#    frontend/config.js -> DEMO_MODE: false  y  API_BASE_URL con la IP del gateway
```

- Frontend: `http://localhost:8080`
- Gateway: `https://localhost:8443`  (acepta el certificado autofirmado la 1ª vez)
- Login: `admin` / `admin123`

**Prueba rápida por consola:**
```powershell
# Login -> token
curl -k -X POST https://localhost:8443/api/auth/login -H "Content-Type: application/json" -d '{\"username\":\"admin\",\"password\":\"admin123\"}'

# Lecturas (con el token)
curl -k https://localhost:8443/api/sensores/lecturas -H "Authorization: Bearer <TOKEN>"

# Alertas activas
curl -k "https://localhost:8443/api/alertas?resuelta=false" -H "Authorization: Bearer <TOKEN>"
```

---

## 11. Notas técnicas (por si pregunta el profe)

- **JPA/Hibernate** traduce objetos Java ↔ filas de la BD. No escribimos SQL a mano;
  los métodos de los repositorios (`save`, `findBy...`) generan el SQL automáticamente.
- `ddl-auto: none` → la app **no** crea ni modifica tablas; las crea `init.sql`. Así
  evitamos que Hibernate falle al arrancar por el típico `tinyint(1)` vs `boolean`.
- **MQTT con QoS 1** → garantiza que el mensaje llega al menos una vez.
- El simulador usa `@Scheduled(fixedRate=5000)` para correr cada 5 segundos.
- La conexión JDBC usa `sequential://host1,host2` → si la primera PC cae, salta a la
  segunda automáticamente (failover).
