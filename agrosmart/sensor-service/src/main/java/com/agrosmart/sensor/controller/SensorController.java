package com.agrosmart.sensor.controller; // Paquete de los controladores REST (capa que recibe las peticiones HTTP)

import com.agrosmart.sensor.model.Lectura; // Entidad Lectura: representa una medicion guardada en la tabla lecturas_sensor
import com.agrosmart.sensor.model.Sensor; // Entidad Sensor: representa un sensor del catalogo (tabla sensores)
import com.agrosmart.sensor.repository.LecturaRepository; // Repositorio para consultar las lecturas en la base de datos
import com.agrosmart.sensor.repository.SensorRepository; // Repositorio para consultar los sensores en la base de datos
import org.springframework.dao.DataIntegrityViolationException; // Excepcion que salta si se viola una restriccion de la BD (ej. clave foranea)
import org.springframework.data.domain.PageRequest; // Permite pedir solo una pagina/cantidad limitada de resultados (paginacion)
import org.springframework.http.HttpStatus; // Enum con los codigos de estado HTTP (201, 404, 409, etc.)
import org.springframework.http.ResponseEntity; // Permite construir la respuesta HTTP controlando codigo de estado y cuerpo
import org.springframework.web.bind.annotation.*; // Anotaciones web de Spring (@RestController, @GetMapping, @PathVariable, etc.)
import org.springframework.web.server.ResponseStatusException; // Excepcion que devuelve al cliente un codigo HTTP concreto con un mensaje

import java.util.ArrayList; // Lista dinamica usada para acumular resultados
import java.util.Collections; // Utilidades de colecciones (aqui se usa para invertir una lista)
import java.util.List; // Interfaz List: tipo de coleccion ordenada que devuelven los endpoints
import java.util.Map; // Mapa clave-valor usado para devolver mensajes de error en formato JSON

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

    /**
     * Crea un sensor nuevo. POST /sensores
     * El gateway valida el JWT antes de llegar aqui, asi que solo entran peticiones autenticadas.
     * Una vez creado y con activo=true, el SimuladorService empezara a generarle lecturas solo.
     */
    @PostMapping // Atiende POST /sensores: da de alta un sensor en el catalogo
    public ResponseEntity<Sensor> crear(@RequestBody Sensor sensor) { // Recibe el sensor en formato JSON en el cuerpo
        validar(sensor); // Comprueba que los campos obligatorios vengan completos
        sensor.setId(null); // Fuerza id nulo: lo asigna la BD (el cliente no puede elegir el id)
        if (sensor.getActivo() == null) sensor.setActivo(true); // Si no se indico, el sensor nace activo
        Sensor creado = sensorRepository.save(sensor);             // <-- INSERT
        return ResponseEntity.status(HttpStatus.CREATED).body(creado); // Responde 201 CREATED con el sensor ya guardado (incluye su id)
    }

    /** Actualiza un sensor existente. PUT /sensores/{id} */
    @PutMapping("/{id}") // Atiende PUT /sensores/{id}: modifica los datos de un sensor
    public Sensor actualizar(@PathVariable Long id, @RequestBody Sensor datos) { // Toma el id de la URL y los nuevos datos del cuerpo
        Sensor s = sensorRepository.findById(id) // Busca el sensor a modificar
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sensor no encontrado")); // Si no existe, responde 404
        validar(datos); // Valida los nuevos datos recibidos
        s.setNombre(datos.getNombre()); // Actualiza el nombre
        s.setTipo(datos.getTipo()); // Actualiza el tipo (temperatura, humedad, ph...)
        s.setZona(datos.getZona()); // Actualiza la zona
        s.setUnidad(datos.getUnidad()); // Actualiza la unidad de medida
        s.setValorMinEsperado(datos.getValorMinEsperado()); // Actualiza el minimo esperado
        s.setValorMaxEsperado(datos.getValorMaxEsperado()); // Actualiza el maximo esperado
        if (datos.getActivo() != null) s.setActivo(datos.getActivo()); // Actualiza el estado activo solo si vino en la peticion
        return sensorRepository.save(s);                            // <-- UPDATE
    }

    /** Elimina un sensor. DELETE /sensores/{id} */
    @DeleteMapping("/{id}") // Atiende DELETE /sensores/{id}: borra un sensor del catalogo
    public ResponseEntity<?> eliminar(@PathVariable Long id) { // Toma el id del sensor a eliminar de la URL
        if (!sensorRepository.existsById(id)) { // Si el sensor no existe...
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sensor no encontrado"); // ...responde 404
        }
        try { // Intenta borrarlo (la BD elimina en cascada sus lecturas)
            sensorRepository.deleteById(id);                       // <-- DELETE
            return ResponseEntity.noContent().build(); // Responde 204 SIN contenido: borrado con exito
        } catch (DataIntegrityViolationException e) { // Si el sensor tiene alertas asociadas, la clave foranea impide borrarlo
            return ResponseEntity.status(HttpStatus.CONFLICT) // Responde 409 CONFLICT con un mensaje claro
                    .body(Map.of("message",
                        "No se puede eliminar: el sensor ya tiene alertas asociadas. Desactívalo en su lugar."));
        }
    }

    /** Valida que los campos obligatorios del sensor vengan completos. */
    private void validar(Sensor s) { // Comprueba los datos minimos antes de guardar
        if (s == null // Si no llego cuerpo...
                || vacio(s.getNombre()) || vacio(s.getTipo()) // ...o falta nombre o tipo...
                || vacio(s.getZona()) || vacio(s.getUnidad())) { // ...o falta zona o unidad...
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, // ...responde 400 BAD REQUEST con el motivo
                    "Nombre, tipo, zona y unidad son obligatorios");
        }
    }

    /** Indica si un texto es nulo o esta vacio (solo espacios). */
    private boolean vacio(String v) { return v == null || v.isBlank(); } // Devuelve true si el texto no tiene contenido util
}
