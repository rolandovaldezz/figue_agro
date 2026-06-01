# 🗄️ Base de Datos — AgroSmart

Esta carpeta contiene el script de inicialización de la base de datos
para tu nube privada (Rocky Linux con MariaDB ya configurada con replicación
y espejeo).

## 📄 Archivos

- `init.sql` — Script completo: crea base, usuario, tablas y datos iniciales.

## 🚀 Cómo subir el script a tu nube privada

### Opción A: Desde el servidor Rocky Linux (recomendado)

1. Copia el archivo al servidor:
   ```bash
   scp init.sql usuario@<IP_NUBE>:/tmp/
   ```

2. Conéctate por SSH al **nodo master**:
   ```bash
   ssh usuario@<IP_NUBE>
   ```

3. Carga el script:
   ```bash
   mysql -u root -p < /tmp/init.sql
   ```

4. La **replicación se encargará automáticamente** de propagar los cambios
   al nodo réplica (espejeo).

### Opción B: Desde tu máquina (si el puerto 3306 está abierto)

```bash
mysql -h <IP_NUBE> -P 3306 -u root -p < db/init.sql
```

## ✅ Verificar que todo se cargó correctamente

Conéctate a MariaDB y ejecuta:

```sql
USE agrosmart;

-- Debe mostrar 5 tablas
SHOW TABLES;

-- Debe mostrar 2 usuarios (admin y agricultor)
SELECT id, username, rol, activo FROM usuarios;

-- Debe mostrar 5 sensores virtuales
SELECT id, nombre, tipo, zona FROM sensores;

-- Debe mostrar 7 reglas de alerta
SELECT id, tipo_sensor, severidad FROM umbrales_alerta;
```

## 🔁 Verificar que la réplica está sincronizada

En el nodo **master**:
```sql
SHOW MASTER STATUS;
```

En el nodo **réplica**:
```sql
SHOW REPLICA STATUS\G
```

Asegúrate de que en la réplica aparezcan:
- `Replica_IO_Running: Yes`
- `Replica_SQL_Running: Yes`
- `Seconds_Behind_Source: 0` (o cerca de cero)

Si todo está OK, la réplica debe mostrar también las mismas tablas y datos
que el master:

```sql
USE agrosmart;
SELECT COUNT(*) FROM usuarios;   -- debe dar 2
SELECT COUNT(*) FROM sensores;   -- debe dar 5
```

## 🔐 Credenciales por defecto

| Usuario     | Contraseña  | Rol         |
|-------------|-------------|-------------|
| `admin`     | `admin123`  | ADMIN       |
| `agricultor`| `admin123`  | AGRICULTOR  |

> ⚠️ **Cámbialas inmediatamente después del primer login.**

## 🧹 Resetear la base (borrar todo y volver a crear)

```sql
DROP DATABASE IF EXISTS agrosmart;
```

Y vuelve a ejecutar `init.sql`.
