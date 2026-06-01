-- ============================================================
-- AgroSmart - Script de inicialización de Base de Datos
-- Motor: MariaDB 10.x+
-- Sistema operativo destino: Rocky Linux
-- ============================================================
-- Este script:
--   1. Crea la base de datos
--   2. Crea las tablas con sus índices y relaciones
--   3. Inserta datos iniciales (usuario admin, sensores y umbrales)
-- ============================================================

-- 1) CREACIÓN DE BASE DE DATOS Y USUARIO DE APLICACIÓN
-- ============================================================

-- Crea la base de datos si no existe; utf8mb4 admite tildes, enes y emojis
CREATE DATABASE IF NOT EXISTS agrosmart
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Selecciona la base recien creada para que las siguientes ordenes apliquen sobre ella
USE agrosmart;

-- Usuario que usarán los microservicios para conectarse.
-- Cambia la contraseña antes de subir a producción.
-- '@%' permite la conexion desde cualquier host (los contenedores)
CREATE USER IF NOT EXISTS 'agrosmart_app'@'%' IDENTIFIED BY 'CambiaEstoEnProd_2026';
-- Concede solo permisos de datos (sin DDL) sobre la base agrosmart por seguridad
GRANT SELECT, INSERT, UPDATE, DELETE ON agrosmart.* TO 'agrosmart_app'@'%';
-- Aplica de inmediato los cambios de privilegios
FLUSH PRIVILEGES;


-- 2) TABLAS
-- ============================================================

-- Usuarios del sistema (utilizada por auth-service)
-- Almacena las cuentas que pueden iniciar sesion en el sistema
CREATE TABLE IF NOT EXISTS usuarios (
    id              BIGINT       NOT NULL AUTO_INCREMENT,        -- identificador unico autoincremental
    username        VARCHAR(50)  NOT NULL,                       -- nombre de usuario para iniciar sesion
    email           VARCHAR(100) NOT NULL,                       -- correo del usuario
    password_hash   VARCHAR(255) NOT NULL,   -- bcrypt           -- contrasena cifrada (nunca en texto plano)
    nombre_completo VARCHAR(150) NULL,                           -- nombre real del usuario (opcional)
    rol             VARCHAR(20)  NOT NULL DEFAULT 'AGRICULTOR',  -- rol de permisos (ADMIN / AGRICULTOR)
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,          -- si la cuenta esta habilitada
    fecha_registro  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- fecha de alta automatica
    ultimo_acceso   DATETIME     NULL,                           -- fecha del ultimo inicio de sesion
    PRIMARY KEY (id),                                            -- clave primaria
    UNIQUE KEY uq_usuarios_username (username),                  -- no se repite el username
    UNIQUE KEY uq_usuarios_email (email),                        -- no se repite el email
    INDEX idx_usuarios_rol (rol)                                 -- indice para filtrar por rol
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- Catálogo de sensores virtuales (utilizada por sensor-service)
-- Define cada sensor simulado y sus rangos normales de medicion
CREATE TABLE IF NOT EXISTS sensores (
    id              BIGINT       NOT NULL AUTO_INCREMENT,        -- identificador unico del sensor
    nombre          VARCHAR(100) NOT NULL,                       -- nombre descriptivo del sensor
    tipo            VARCHAR(50)  NOT NULL,   -- temperatura, humedad, ph    -- magnitud que mide
    zona            VARCHAR(50)  NOT NULL,   -- zona1, zona2, invernadero_norte  -- ubicacion fisica
    unidad          VARCHAR(20)  NOT NULL,   -- C, %, pH         -- unidad de medida
    valor_min_esperado DOUBLE    NULL,       -- referencia inferior normal
    valor_max_esperado DOUBLE    NULL,       -- referencia superior normal
    activo          BOOLEAN      NOT NULL DEFAULT TRUE,          -- si el sensor esta en funcionamiento
    fecha_creacion  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- fecha de alta automatica
    PRIMARY KEY (id),                                            -- clave primaria
    INDEX idx_sensores_tipo (tipo),                              -- indice para filtrar por tipo
    INDEX idx_sensores_zona (zona),                              -- indice para filtrar por zona
    INDEX idx_sensores_activo (activo)                           -- indice para listar solo los activos
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- Lecturas históricas de los sensores (utilizada por sensor-service)
-- Guarda cada medicion que envia un sensor a lo largo del tiempo
CREATE TABLE IF NOT EXISTS lecturas_sensor (
    id                BIGINT   NOT NULL AUTO_INCREMENT,          -- identificador unico de la lectura
    sensor_id         BIGINT   NOT NULL,                         -- sensor al que pertenece la lectura
    valor             DOUBLE   NOT NULL,                         -- valor medido
    timestamp_lectura DATETIME NOT NULL,                         -- momento en que se tomo la medicion
    fecha_registro    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- momento en que se guardo en la BD
    PRIMARY KEY (id),                                            -- clave primaria
    -- Relacion con sensores: si se borra un sensor, se borran sus lecturas (CASCADE)
    CONSTRAINT fk_lecturas_sensor
        FOREIGN KEY (sensor_id) REFERENCES sensores(id)
        ON DELETE CASCADE,
    INDEX idx_lecturas_sensor (sensor_id),                       -- indice por sensor
    INDEX idx_lecturas_timestamp (timestamp_lectura DESC),       -- indice por fecha (mas recientes primero)
    INDEX idx_lecturas_sensor_timestamp (sensor_id, timestamp_lectura DESC)  -- indice combinado para consultas por sensor y fecha
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- Reglas / umbrales para generar alertas (utilizada por alert-service)
-- Define las condiciones que disparan una alerta segun el tipo de sensor
CREATE TABLE IF NOT EXISTS umbrales_alerta (
    id           BIGINT       NOT NULL AUTO_INCREMENT,           -- identificador unico de la regla
    tipo_sensor  VARCHAR(50)  NOT NULL,   -- temperatura, humedad, ph  -- a que tipo de sensor aplica
    zona         VARCHAR(50)  NULL,       -- NULL = aplica a todas las zonas
    valor_min    DOUBLE       NULL,       -- si lectura < este valor → alerta
    valor_max    DOUBLE       NULL,       -- si lectura > este valor → alerta
    severidad    VARCHAR(20)  NOT NULL,   -- BAJA, MEDIA, ALTA, CRITICA
    mensaje_tpl  VARCHAR(255) NULL,       -- plantilla del mensaje
    activo       BOOLEAN      NOT NULL DEFAULT TRUE,             -- si la regla esta vigente
    PRIMARY KEY (id),                                            -- clave primaria
    INDEX idx_umbrales_tipo (tipo_sensor),                       -- indice para buscar por tipo de sensor
    INDEX idx_umbrales_activo (activo)                           -- indice para usar solo reglas activas
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- Alertas generadas (utilizada por alert-service)
-- Registra cada alerta que se dispara cuando una lectura rompe un umbral
CREATE TABLE IF NOT EXISTS alertas (
    id                BIGINT       NOT NULL AUTO_INCREMENT,      -- identificador unico de la alerta
    sensor_id         BIGINT       NOT NULL,                     -- sensor que origino la alerta
    lectura_id        BIGINT       NULL,                         -- lectura concreta que la disparo (opcional)
    umbral_id         BIGINT       NULL,                         -- regla/umbral que se incumplio (opcional)
    tipo_alerta       VARCHAR(50)  NOT NULL,                     -- categoria de la alerta
    mensaje           VARCHAR(500) NOT NULL,                     -- texto descriptivo mostrado al usuario
    valor_detectado   DOUBLE       NOT NULL,                     -- valor de la lectura que causo la alerta
    severidad         VARCHAR(20)  NOT NULL,                     -- gravedad (BAJA / MEDIA / ALTA / CRITICA)
    fecha_generacion  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- cuando se genero la alerta
    resuelta          BOOLEAN      NOT NULL DEFAULT FALSE,       -- si ya fue atendida
    fecha_resolucion  DATETIME     NULL,                         -- cuando se marco como resuelta
    PRIMARY KEY (id),                                            -- clave primaria
    -- Relacion con el sensor que disparo la alerta
    CONSTRAINT fk_alertas_sensor
        FOREIGN KEY (sensor_id) REFERENCES sensores(id),
    -- Relacion con la lectura; si se borra la lectura, el campo queda en NULL
    CONSTRAINT fk_alertas_lectura
        FOREIGN KEY (lectura_id) REFERENCES lecturas_sensor(id)
        ON DELETE SET NULL,
    -- Relacion con el umbral; si se borra el umbral, el campo queda en NULL
    CONSTRAINT fk_alertas_umbral
        FOREIGN KEY (umbral_id) REFERENCES umbrales_alerta(id)
        ON DELETE SET NULL,
    INDEX idx_alertas_resuelta (resuelta),                       -- indice para filtrar resueltas/pendientes
    INDEX idx_alertas_fecha (fecha_generacion DESC),             -- indice por fecha (mas recientes primero)
    INDEX idx_alertas_severidad (severidad)                      -- indice para filtrar por gravedad
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 3) DATOS INICIALES
-- ============================================================

-- Usuario administrador inicial
-- Credenciales: admin / admin123  (cámbialas después del primer login)
-- Inserta la cuenta de administrador con todos los permisos
INSERT INTO usuarios (username, email, password_hash, nombre_completo, rol)
VALUES (
    'admin',
    'admin@agrosmart.local',
    '$2b$10$EipX46/PowsMfX7tsIOYduX9GKJQ4zablNmzO0BgDRAy9YnIOoXPm',
    'Administrador del Sistema',
    'ADMIN'
);

-- Usuario agricultor de prueba
-- Credenciales: agricultor / admin123
-- Inserta una cuenta de ejemplo con rol AGRICULTOR para pruebas
INSERT INTO usuarios (username, email, password_hash, nombre_completo, rol)
VALUES (
    'agricultor',
    'agricultor@agrosmart.local',
    '$2b$10$EipX46/PowsMfX7tsIOYduX9GKJQ4zablNmzO0BgDRAy9YnIOoXPm',
    'Juan Pérez',
    'AGRICULTOR'
);


-- Sensores virtuales del invernadero
-- Carga los 5 sensores simulados (temperatura, humedad y pH) con sus rangos normales
INSERT INTO sensores (nombre, tipo, zona, unidad, valor_min_esperado, valor_max_esperado) VALUES
    ('Sensor de Temperatura Norte', 'temperatura', 'zona1', 'C',  18.0, 30.0),
    ('Sensor de Humedad Norte',     'humedad',     'zona1', '%',  40.0, 80.0),
    ('Sensor de pH Norte',          'ph',          'zona1', 'pH',  5.5,  7.5),
    ('Sensor de Temperatura Sur',   'temperatura', 'zona2', 'C',  18.0, 30.0),
    ('Sensor de Humedad Sur',       'humedad',     'zona2', '%',  40.0, 80.0);


-- Umbrales para generación de alertas
-- Reglas que indican cuando una lectura es anormal y con que severidad alertar
INSERT INTO umbrales_alerta (tipo_sensor, zona, valor_min, valor_max, severidad, mensaje_tpl) VALUES
    ('temperatura', NULL, NULL, 35.0, 'ALTA',     'Temperatura demasiado alta en {zona}: {valor}°C'),
    ('temperatura', NULL, 10.0, NULL, 'ALTA',     'Temperatura demasiado baja en {zona}: {valor}°C'),
    ('temperatura', NULL, NULL, 40.0, 'CRITICA',  'TEMPERATURA CRÍTICA en {zona}: {valor}°C - Acción inmediata'),
    ('humedad',     NULL, 30.0, NULL, 'MEDIA',    'Humedad baja en {zona}: {valor}%'),
    ('humedad',     NULL, NULL, 90.0, 'MEDIA',    'Humedad excesiva en {zona}: {valor}%'),
    ('ph',          NULL, 5.0,  NULL, 'ALTA',     'pH demasiado ácido en {zona}: {valor}'),
    ('ph',          NULL, NULL, 8.0,  'ALTA',     'pH demasiado alcalino en {zona}: {valor}');


-- ============================================================
-- Verificación
-- ============================================================
-- SELECT COUNT(*) AS usuarios   FROM usuarios;
-- SELECT COUNT(*) AS sensores   FROM sensores;
-- SELECT COUNT(*) AS umbrales   FROM umbrales_alerta;
-- ============================================================
