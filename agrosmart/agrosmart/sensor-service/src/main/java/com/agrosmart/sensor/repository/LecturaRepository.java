package com.agrosmart.sensor.repository; // Paquete de los repositorios (capa de acceso a datos)

import com.agrosmart.sensor.model.Lectura; // Entidad Lectura que maneja este repositorio
import org.springframework.data.domain.Pageable; // Permite limitar y paginar los resultados de una consulta
import org.springframework.data.jpa.repository.JpaRepository; // Interfaz base que aporta CRUD y consultas automaticas
import java.util.List; // Tipo de coleccion para devolver varias lecturas
import java.util.Optional; // Contenedor que puede tener o no un resultado (evita devolver null)

/** Repositorio de lecturas. Aquí viven los SELECT/INSERT del historial. */
public interface LecturaRepository extends JpaRepository<Lectura, Long> { // Repositorio de Lectura con clave de tipo Long; hereda save/findAll/etc.

    /** La última lectura de un sensor (la más reciente). */
    Optional<Lectura> findTopBySensorIdOrderByTimestampLecturaDesc(Long sensorId); // Spring genera la consulta: trae la lectura mas reciente del sensor

    /** Las últimas N lecturas de un sensor (N lo controla el Pageable). */
    List<Lectura> findBySensorIdOrderByTimestampLecturaDesc(Long sensorId, Pageable pageable); // Trae las lecturas del sensor mas recientes primero, limitadas por Pageable
}
