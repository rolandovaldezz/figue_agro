package com.agrosmart.sensor.controller; // Paquete de los controladores REST (capa que recibe las peticiones HTTP)

import com.agrosmart.sensor.model.Lectura; // Entidad Lectura: representa una medicion guardada en la tabla lecturas_sensor
import com.agrosmart.sensor.model.Sensor; // Entidad Sensor: representa un sensor del catalogo (tabla sensores)
import com.agrosmart.sensor.repository.LecturaRepository; // Repositorio para consultar las lecturas en la base de datos
import com.agrosmart.sensor.repository.SensorRepository; // Repositorio para consultar los sensores en la base de datos
import org.springframework.data.domain.PageRequest; // Permite pedir solo una pagina/cantidad limitada de resultados (paginacion)
import org.springframework.web.bind.annotation.*; // Anotaciones web de Spring (@RestController, @GetMapping, @PathVariable, etc.)

import java.util.ArrayList; // Lista dinamica usada para acumular resultados
import java.util.Collections; // Utilidades de colecciones (aqui se usa para invertir una lista)
import java.util.List; // Interfaz List: tipo de coleccion ordenada que devuelven los endpoints

/**
 * API REST de sensores y lecturas.
 *
 * El gateway aplica StripPrefix=1, así:
 *   /api/sensores              -> /sensores
 *   /api/sensores/lecturas     -> /sensores/lecturas
 *   /api/sensores/{id}/lecturas-> /sensores/{id}/lecturas
 */
@RestController // Marca la clase como controlador REST: cada metodo devuelve datos (JSON) directamente al cliente
@RequestMapping("/sensores") // Ruta base comun a todos los endpoints de esta clase
public class SensorController { // Controlador que expone las operaciones HTTP sobre sensores y lecturas

    private final SensorRepository sensorRepository; // Dependencia para acceder a los sensores en la BD
    private final LecturaRepository lecturaRepository; // Dependencia para acceder a las lecturas en la BD

    public SensorController(SensorRepository sensorRepository, LecturaRepository lecturaRepository) { // Constructor: Spring inyecta aqui los repositorios
        this.sensorRepository = sensorRepository; // Guarda el repositorio de sensores recibido por inyeccion
        this.lecturaRepository = lecturaRepository; // Guarda el repositorio de lecturas recibido por inyeccion
    }

    /** Catálogo completo de sensores. */
    @GetMapping // Atiende GET /sensores (sin sufijo): lista todos los sensores
    public List<Sensor> listar() { // Devuelve la lista completa de sensores
        return sensorRepository.findAll();                 // <-- SELECT * FROM sensores
    }

    /** Última lectura de cada sensor activo (lo que pinta el dashboard). */
    @GetMapping("/lecturas") // Atiende GET /sensores/lecturas: ultima lectura de cada sensor activo
    public List<Lectura> ultimasLecturas() { // Devuelve una lista con la lectura mas reciente por sensor activo
        List<Lectura> resultado = new ArrayList<>(); // Lista vacia donde se acumularan las ultimas lecturas
        for (Sensor s : sensorRepository.findByActivoTrue()) { // Recorre solo los sensores marcados como activos
            lecturaRepository.findTopBySensorIdOrderByTimestampLecturaDesc(s.getId()) // Busca la lectura mas reciente de ese sensor
                    .ifPresent(resultado::add); // Si existe esa lectura, la agrega a la lista de resultados
        }
        return resultado; // Devuelve la coleccion con la ultima lectura de cada sensor activo
    }

    /** Historial reciente de un sensor (para la gráfica), en orden cronológico. */
    @GetMapping("/{id}/lecturas") // Atiende GET /sensores/{id}/lecturas: historial de un sensor concreto
    public List<Lectura> historial(@PathVariable Long id, // @PathVariable toma el {id} de la URL como parametro
                                   @RequestParam(defaultValue = "30") int limit) { // @RequestParam lee ?limit= de la URL; por defecto 30
        List<Lectura> lista = lecturaRepository // Consulta las lecturas del sensor en el repositorio
                .findBySensorIdOrderByTimestampLecturaDesc(id, PageRequest.of(0, limit)); // Trae las 'limit' mas recientes (pagina 0)
        Collections.reverse(lista);   // de más antiguo a más reciente
        return lista; // Devuelve el historial ya ordenado cronologicamente para graficarlo
    }
}
