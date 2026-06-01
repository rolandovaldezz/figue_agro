# 🌱 AgroSmart — Sistema de Monitoreo Inteligente para Invernaderos

Sistema distribuido basado en **microservicios** que monitorea sensores virtuales
(temperatura, humedad y pH) de un invernadero y genera alertas en tiempo real.

---

## 🏗️ Arquitectura

```
┌──────────────┐
│ Cliente Web  │ (HTML + JS + Bootstrap)
└──────┬───────┘
       │ HTTPS + JWT
┌──────▼───────┐
│ API Gateway  │ (Spring Cloud Gateway, TLS, valida JWT)
└──────┬───────┘
       │
       ├──────────────────┬──────────────────┐
       ▼                  ▼                  ▼
┌─────────────┐  ┌──────────────┐   ┌──────────────┐
│auth-service │  │sensor-service│   │ alert-service│
│ Login + JWT │  │ Simula+publ. │   │  Reglas+notif│
└──────┬──────┘  └──────┬───────┘   └──────┬───────┘
       │                │                  │
       │           ┌────▼─────┐            │
       │           │Mosquitto │◀───────────┘
       │           │  (MQTT)  │  (subscribe)
       │           └──────────┘
       │
       ▼
┌──────────────────────────────────────────────────┐
│ Nube privada (Rocky Linux) — ya implementada     │
│  ┌──────────────┐         ┌──────────────┐       │
│  │MariaDB Master│◀───────▶│MariaDB Replica│      │
│  └──────────────┘  repl.  └──────────────┘       │
└──────────────────────────────────────────────────┘
```

---

## 📋 Requisitos

- **Docker** 20.10+ y **Docker Compose** 2.0+
- **Java 17** y **Maven 3.8+** (solo si quieres construir manualmente)
- Acceso a tu **base MariaDB** en la nube privada (puerto 3306 abierto)

---

## 🚀 Primer despliegue (paso a paso)

### 1. Inicializar la base de datos en tu nube

Conéctate a tu servidor Rocky Linux y carga el script SQL:

```bash
mysql -u root -p < db/init.sql
```

Esto crea:
- La base `agrosmart`
- 5 tablas con sus índices y relaciones
- 1 usuario admin (user: `admin`, pass: `admin123`)
- 5 sensores virtuales y 7 reglas de alerta

Verifica:
```sql
USE agrosmart;
SHOW TABLES;
SELECT username, rol FROM usuarios;
```

### 2. Configurar variables de entorno

```bash
cp .env.example .env
nano .env       # Ajusta DB_HOST con la IP de tu nube
```

Genera una clave JWT segura:
```bash
openssl rand -base64 48
```

Y pégala en `JWT_SECRET` dentro de `.env`.

### 3. Levantar todos los servicios

```bash
docker compose up -d --build
```

Espera ~1 minuto a que todos los contenedores arranquen. Verifica:

```bash
docker compose ps
docker compose logs -f api-gateway
```

### 4. Acceder al sistema

- **Frontend:** http://localhost:8080
- **API Gateway (HTTPS):** https://localhost:8443
- **Credenciales:** `admin` / `admin123`

---

## 📁 Estructura del proyecto

```
agrosmart/
├── docker-compose.yml          ← orquesta todos los contenedores
├── .env.example                ← plantilla de variables (renombrar a .env)
│
├── db/
│   └── init.sql                ← script para crear la BD en la nube
│
├── api-gateway/                ← Spring Cloud Gateway (TLS + JWT)
├── auth-service/               ← Login y emisión de JWT
├── sensor-service/             ← Simulador + MQTT publisher + REST
├── alert-service/              ← MQTT subscriber + reglas
├── frontend/                   ← Web HTML + Bootstrap
│
├── mosquitto/
│   └── config/mosquitto.conf   ← config del broker MQTT
│
└── certs/
    ├── server.crt              ← certificado público
    ├── server.key              ← llave privada
    └── keystore.p12            ← keystore PKCS12 para Spring Boot
```

---

## 🔐 Seguridad

| Elemento | Implementación |
|---|---|
| **TLS/SSL** | Certificado autofirmado en `/certs`, terminado en el API Gateway |
| **Autenticación** | JWT emitido por `auth-service`, validado en el Gateway |
| **Contraseñas** | Hash bcrypt (cost 10) en la columna `password_hash` |
| **Comunicación interna** | Red Docker aislada (`agrosmart-net`) |

> ⚠️ Para **producción** reemplaza el certificado autofirmado por uno real (Let's Encrypt, etc.) y habilita autenticación en Mosquitto.

---

## 🧪 Probar manualmente

### Hacer login y obtener un JWT
```bash
curl -k -X POST https://localhost:8443/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### Consultar lecturas (autenticado)
```bash
TOKEN="<pega-tu-jwt-aqui>"
curl -k https://localhost:8443/api/sensores/lecturas \
  -H "Authorization: Bearer $TOKEN"
```

### Ver alertas activas
```bash
curl -k https://localhost:8443/api/alertas \
  -H "Authorization: Bearer $TOKEN"
```

---

## 🛑 Detener todo

```bash
docker compose down              # detiene contenedores
docker compose down -v           # detiene Y borra volúmenes (mosquitto data)
```

---

## 📝 Notas

- La base de datos **NO** se levanta con docker-compose — se asume que vive en la nube privada con replicación master-replica ya configurada.
- Los sensores son **simulados** por el `sensor-service`, que genera lecturas aleatorias cada 5 segundos y las publica vía MQTT.
- El `alert-service` se suscribe a los topics MQTT y evalúa las reglas definidas en la tabla `umbrales_alerta`.
