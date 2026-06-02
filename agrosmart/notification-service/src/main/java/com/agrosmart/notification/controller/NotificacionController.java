package com.agrosmart.notification.controller;          // Paquete de los controladores REST del notification-service

import com.agrosmart.notification.dto.EnvioManual;       // DTO con el destinatario y la alerta a enviar
import com.agrosmart.notification.service.EmailService;   // Servicio que envia el correo
import org.springframework.http.HttpStatus;              // Codigos de estado HTTP (400, 500, etc.)
import org.springframework.http.ResponseEntity;          // Respuesta HTTP con codigo y cuerpo
import org.springframework.web.bind.annotation.*;        // Anotaciones REST (@RestController, @PostMapping, @RequestBody)

import java.util.Map;                                    // Mapa para devolver mensajes en JSON

/**
 * Endpoint para ENVIAR una alerta por correo MANUALMENTE a un destinatario elegido.
 *
 * El gateway enruta /api/notificaciones/** hasta aquí y SÍ valida el JWT antes,
 * así que solo un usuario autenticado puede disparar el envío.
 *
 *   POST /notificaciones/enviar   body: { "destinatario": "...", "alerta": {...} }
 */
@RestController                                          // Controlador REST (devuelve JSON)
@RequestMapping("/notificaciones")                       // Prefijo comun de las rutas
public class NotificacionController {                    // Controlador del envio manual de correos

    private final EmailService emailService;             // Servicio que realmente envia el correo

    public NotificacionController(EmailService emailService) { // Constructor: Spring inyecta el EmailService
        this.emailService = emailService;                // Guarda el servicio de correo
    }

    /** Envía la alerta recibida al correo indicado. */
    @PostMapping("/enviar")                              // Atiende POST /notificaciones/enviar
    public ResponseEntity<?> enviar(@RequestBody EnvioManual req) { // Recibe el destinatario y la alerta en el cuerpo
        // Validacion basica del correo destino
        if (req == null || req.destinatario() == null    // Si no llego cuerpo o destinatario...
                || !req.destinatario().contains("@") || !req.destinatario().contains(".")) { // ...o no parece un email valido...
            return ResponseEntity.status(HttpStatus.BAD_REQUEST) // ...responde 400 con un mensaje claro
                    .body(Map.of("message", "Correo destino inválido"));
        }
        if (req.alerta() == null) {                       // Si no llegaron datos de la alerta...
            return ResponseEntity.status(HttpStatus.BAD_REQUEST) // ...responde 400
                    .body(Map.of("message", "Faltan los datos de la alerta"));
        }
        try {                                             // Intenta enviar el correo
            emailService.enviarA(req.destinatario().trim(), req.alerta()); // Envia la alerta al destinatario elegido
            return ResponseEntity.ok(Map.of("message", "Correo enviado a " + req.destinatario().trim())); // Responde 200 con confirmacion
        } catch (Exception e) {                           // Si falla el envio (SMTP, etc.)...
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR) // ...responde 500 con el motivo
                    .body(Map.of("message", "No se pudo enviar el correo: " + e.getMessage()));
        }
    }
}
