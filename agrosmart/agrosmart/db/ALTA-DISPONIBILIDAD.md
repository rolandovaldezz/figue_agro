# 🔁 Alta disponibilidad — Master-Master (tolerar apagar una PC)

> Objetivo de la prueba: tienes **dos PCs** con MariaDB. Apagas **cualquiera**
> de las dos y el programa AgroSmart **sigue funcionando completo** (leyendo y
> guardando lecturas y alertas).

---

## 1. ¿Por qué master-master y no master-replica?

| Esquema | Apagas la réplica/nodo 2 | Apagas el master/nodo 1 |
|---|---|---|
| **Master → Replica** | ✅ sigue | ❌ las **escrituras** (`INSERT`) fallan: la réplica es solo lectura |
| **Master ↔ Master** | ✅ sigue | ✅ sigue: **los dos** aceptan lecturas y escrituras |

Para que el sistema sobreviva al apagado de **cualquiera** de las dos PCs,
necesitas **master-master**: cada nodo es master y, a la vez, réplica del otro.

> ❗ La replicación es **automática del motor MariaDB**. La app escribe en UN
> solo nodo; MariaDB copia el cambio al otro por el binlog. La app **nunca**
> escribe en los dos a la vez (eso causaría conflictos).

---

## 2. Configuración (se hace UNA vez en cada PC)

Supongamos:
- **Nodo 1** = `192.168.1.100`  (server-id 1)
- **Nodo 2** = `192.168.1.101`  (server-id 2)

### 2.1 Editar `/etc/my.cnf.d/server.cnf` en **NODO 1**

```ini
[mariadb]
server-id              = 1
log-bin                = mysql-bin
binlog_format          = ROW
# Evita choques de IDs autoincrementales entre los dos masters:
auto_increment_increment = 2
auto_increment_offset    = 1
# Permite conexiones remotas:
bind-address           = 0.0.0.0
```

### 2.2 Editar `/etc/my.cnf.d/server.cnf` en **NODO 2**

```ini
[mariadb]
server-id              = 2
log-bin                = mysql-bin
binlog_format          = ROW
auto_increment_increment = 2
auto_increment_offset    = 2     # <-- offset distinto
bind-address           = 0.0.0.0
```

Reinicia MariaDB en ambos: `sudo systemctl restart mariadb`

> El truco de `auto_increment_increment=2` con `offset` 1 y 2 hace que un nodo
> genere IDs impares (1,3,5...) y el otro pares (2,4,6...), así nunca chocan los
> `id` aunque ambos reciban `INSERT`.

---

## 3. Crear el usuario de replicación (en AMBOS nodos)

```sql
CREATE USER IF NOT EXISTS 'repl'@'%' IDENTIFIED BY 'ReplicaPass_2026';
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
FLUSH PRIVILEGES;
```

(Y recuerda que el usuario de la app `agrosmart_app` ya existe por `init.sql`.)

---

## 4. Enlazar los dos nodos

### 4.1 En NODO 1: ver su posición de binlog
```sql
SHOW MASTER STATUS;
-- Anota: File (p.ej. mysql-bin.000001) y Position (p.ej. 1234)
```

### 4.2 En NODO 2: apuntar al NODO 1 como su master
```sql
STOP SLAVE;
CHANGE MASTER TO
  MASTER_HOST='192.168.1.100',
  MASTER_USER='repl',
  MASTER_PASSWORD='ReplicaPass_2026',
  MASTER_LOG_FILE='mysql-bin.000001',   -- el File del paso 4.1
  MASTER_LOG_POS=1234;                   -- la Position del paso 4.1
START SLAVE;
```

### 4.3 En NODO 2: ver su posición de binlog
```sql
SHOW MASTER STATUS;
-- Anota File y Position del NODO 2
```

### 4.4 En NODO 1: apuntar al NODO 2 como su master (el enlace inverso)
```sql
STOP SLAVE;
CHANGE MASTER TO
  MASTER_HOST='192.168.1.101',
  MASTER_USER='repl',
  MASTER_PASSWORD='ReplicaPass_2026',
  MASTER_LOG_FILE='mysql-bin.000001',   -- el File del paso 4.3
  MASTER_LOG_POS=...;                    -- la Position del paso 4.3
START SLAVE;
```

---

## 5. Verificar que la replicación bidireccional funciona

En **cada** nodo:
```sql
SHOW SLAVE STATUS\G
```
Busca en AMBOS:
- `Slave_IO_Running: Yes`
- `Slave_SQL_Running: Yes`
- `Seconds_Behind_Master: 0`

Prueba rápida de espejeo:
```sql
-- En NODO 1:
USE agrosmart;
INSERT INTO sensores (nombre, tipo, zona, unidad) VALUES ('PRUEBA', 'temperatura', 'test', 'C');

-- En NODO 2 (debe aparecer solo):
SELECT * FROM sensores WHERE zona='test';
```
Borra la fila de prueba al terminar.

---

## 6. Cómo se conecta la app (ya quedó configurado)

En tu `.env` pones las dos IPs:
```bash
DB_HOST_1=192.168.1.100
DB_HOST_2=192.168.1.101
DB_PORT=3306
```

Los microservicios usan esta URL JDBC con **failover automático** (ya editada en
los `application.yml`):
```
jdbc:mariadb:sequential://${DB_HOST_1}:${DB_PORT},${DB_HOST_2}:${DB_PORT}/agrosmart
```
- `sequential` = se conecta al **nodo 1**; si no responde, salta al **nodo 2**.
- Como ambos son master, el nodo 2 también acepta `INSERT` → el sistema sigue
  guardando lecturas y alertas aunque el nodo 1 esté apagado.

---

## 7. Procedimiento de la prueba (apagar una PC)

1. Levanta todo: `docker compose up -d --build` y entra al frontend.
2. Verifica que el dashboard recibe lecturas y se generan alertas.
3. **Apaga el NODO 1** (o desconéctale la red).
4. Espera unos segundos. Las **conexiones nuevas** saltarán al NODO 2.
   - El dashboard sigue mostrando datos y guardando lecturas/alertas.
   - (Puede haber un pequeño parpadeo de error mientras Hikari renueva el pool.)
5. Vuelve a encender el NODO 1: MariaDB lo re-sincroniza solo (recupera del
   binlog del NODO 2 lo que se escribió mientras estuvo apagado).
6. Repite apagando el NODO 2 para demostrar que también tolera ese caso.

> 💡 Para que el salto sea más rápido durante la prueba, puedes bajar el
> `connection-timeout` de Hikari en los `application.yml` (está en 10000 ms).

---

## 8. (Opcional) Reforzar la reconexión más agresiva

Si en la prueba quieres que un microservicio ya conectado al nodo caído suelte
las conexiones muertas más rápido, añade a la URL JDBC:
```
...&connectTimeout=3000&socketTimeout=5000
```
Esto hace que detecte el corte en ~5 s en vez de esperar el timeout del SO.
