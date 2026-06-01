package com.agrosmart.sensor.repository; // Paquete de los repositorios (capa de acceso a datos)

import com.agrosmart.sensor.model.Sensor; // Entidad Sensor que maneja este repositorio
import org.springframework.data.jpa.repository.JpaRepository; // Interfaz base que aporta CRUD y consultas automaticas
import java.util.List; // Tipo de coleccion para devolver varios sensores

/** Repositorio del catálogo de sensores. */
public interface SensorRepository extends JpaRepository<Sensor, Long> { // Repositorio de Sensor con clave de tipo Long; hereda save/findAll/etc.

    /** SELECT * FROM sensores WHERE activo = true */
    List<Sensor> findByActivoTrue(); // Spring genera la consulta: devuelve solo los sensores cuyo campo activo es true
}
