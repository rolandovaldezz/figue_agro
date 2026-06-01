package com.agrosmart.alert.controller;                                 // Paquete de los controladores REST (capa que recibe las peticiones HTTP)

import com.agrosmart.alert.model.Alerta;                                // Importa la entidad Alerta que se devuelve en las respuestas
import com.agrosmart.alert.repository.AlertaRepository;                 // Importa el repositorio para consultar alertas en la base de datos
import com.agrosmart.alert.service.AlertaService;                       // Importa el servicio que contiene la logica de negocio (resolver alertas)
import org.springframework.web.bind.annotation.*;                       // Importa las anotaciones web de Spring (@RestController, @GetMapping, etc.)

import java.util.List;                                                  // Importa List para devolver listados de alertas

/**
 * API REST de alertas.
 *
 * El gateway aplica StripPrefix=1:
 *   /api/alertas               -> /alertas
 *   /api/alertas?resuelta=false-> /alertas?resuelta=false
 *   /api/alertas/{id}/resolver -> /alertas/{id}/resolver
 */
@RestController                                                         // Marca la clase como controlador REST: cada metodo devuelve datos (JSON) directamente al cliente
@RequestMapping("/alertas")                                            // Ruta base comun: todos los endpoints de esta clase cuelgan de /alertas
public class AlertaController {                                         // Controlador que expone los endpoints HTTP relacionados con alertas

    private final AlertaRepository alertaRepository;                   // Dependencia para leer alertas de la base de datos
    private final AlertaService alertaService;                         // Dependencia con la logica de negocio (por ejemplo, resolver una alerta)

    public AlertaController(AlertaRepository alertaRepository, AlertaService alertaService) { // Constructor: Spring inyecta automaticamente las dependencias (inyeccion por constructor)
        this.alertaRepository = alertaRepository;                      // Guarda el repositorio recibido en el campo de la clase
        this.alertaService = alertaService;                            // Guarda el servicio recibido en el campo de la clase
    }

    /** Lista alertas. Sin parámetro = todas; ?resuelta=false = solo activas. */
    @GetMapping                                                        // Atiende peticiones HTTP GET a /alertas (listar)
    public List<Alerta> listar(@RequestParam(required = false) Boolean resuelta) { // Parametro de consulta opcional 'resuelta' (puede venir o no en la URL)
        if (resuelta == null) {                                        // Si no se envio el parametro...
            return alertaRepository.findAllByOrderByFechaGeneracionDesc(); // ...devuelve todas las alertas ordenadas por fecha descendente
        }
        return alertaRepository.findByResueltaOrderByFechaGeneracionDesc(resuelta); // Si se envio, filtra por activas o resueltas segun el valor
    }

    /** Marca una alerta como resuelta. */
    @PostMapping("/{id}/resolver")                                     // Atiende POST a /alertas/{id}/resolver; {id} es una variable de la ruta
    public Alerta resolver(@PathVariable Long id) {                    // Toma el id de la URL y lo recibe como parametro del metodo
        return alertaService.resolver(id);                            // Delega en el servicio para marcar la alerta como resuelta y devuelve la alerta actualizada
    }
}
