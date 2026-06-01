package com.agrosmart.alert.repository;                                 // Paquete de los repositorios (acceso a la base de datos)

import com.agrosmart.alert.model.Umbral;                                // Importa la entidad Umbral, que mapea la tabla `umbrales_alerta`
import org.springframework.data.jpa.repository.JpaRepository;           // Interfaz base de Spring Data JPA con CRUD ya implementado
import java.util.List;                                                  // Importa List para devolver colecciones de resultados

/** Repositorio de reglas de alerta. */
public interface UmbralRepository extends JpaRepository<Umbral, Long> { // Hereda CRUD para la entidad Umbral cuya clave primaria es Long; Spring crea la implementacion

    /** Reglas activas para un tipo de sensor (temperatura, humedad, ph). */
    List<Umbral> findByTipoSensorAndActivoTrue(String tipoSensor);      // Consulta derivada: trae los umbrales de ese tipo que ademas tengan activo = true
}
