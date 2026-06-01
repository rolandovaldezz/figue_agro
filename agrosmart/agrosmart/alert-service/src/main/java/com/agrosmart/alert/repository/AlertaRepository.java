package com.agrosmart.alert.repository;                                 // Paquete de los repositorios (acceso a la base de datos)

import com.agrosmart.alert.model.Alerta;                                // Importa la entidad Alerta, que mapea la tabla `alertas`
import org.springframework.data.jpa.repository.JpaRepository;           // Interfaz base de Spring Data JPA con CRUD ya implementado
import java.util.List;                                                  // Importa List para devolver colecciones de resultados

/** Repositorio de alertas generadas. */
public interface AlertaRepository extends JpaRepository<Alerta, Long> { // Hereda CRUD para la entidad Alerta cuya clave primaria es Long; Spring crea la implementacion

    /** Todas las alertas, más recientes primero. */
    List<Alerta> findAllByOrderByFechaGeneracionDesc();                 // Consulta derivada del nombre: trae todas las alertas ordenadas por fecha descendente

    /** Solo activas o solo resueltas, según el booleano. */
    List<Alerta> findByResueltaOrderByFechaGeneracionDesc(Boolean resuelta); // Filtra por el campo 'resuelta' (true/false) y ordena por fecha descendente

    /** ¿Ya hay una alerta ACTIVA para este sensor y esta regla?
     *  Sirve para no duplicar la misma alerta cada 5 segundos. */
    boolean existsBySensorIdAndUmbralIdAndResueltaFalse(Long sensorId, Long umbralId); // Devuelve true si existe una alerta no resuelta para ese sensor y umbral (evita duplicados)
}
