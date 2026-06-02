package com.agrosmart.notification.service;                             // Paquete de la capa de servicio del notification-service

import com.agrosmart.notification.dto.AlertaMensaje;                     // DTO con los datos de la alerta a notificar
import org.slf4j.Logger;                                                // Interfaz de logging
import org.slf4j.LoggerFactory;                                         // Fabrica que crea el logger
import org.springframework.beans.factory.annotation.Value;             // Inyecta valores de configuracion (application.yml)
import org.springframework.mail.SimpleMailMessage;                     // Representa un correo simple de solo texto
import org.springframework.mail.javamail.JavaMailSender;               // Componente de Spring que envia correos por SMTP
import org.springframework.stereotype.Service;                         // Marca la clase como servicio gestionado por Spring

import java.time.Duration;                                              // Para calcular el tiempo transcurrido entre dos instantes
import java.time.Instant;                                              // Marca de tiempo del ultimo correo enviado por sensor
import java.util.Map;                                                  // Mapa que recuerda el ultimo envio por sensor
import java.util.concurrent.ConcurrentHashMap;                         // Mapa seguro para acceso concurrente (varios mensajes a la vez)

/**
 * Envía las alertas por correo electrónico usando SMTP (Gmail).
 *
 * JavaMailSender lo autoconfigura Spring Boot a partir de las propiedades
 * spring.mail.* (host, puerto, usuario y contraseña de aplicación).
 */
@Service                                                                // Registra la clase como bean de servicio
public class EmailService {                                             // Servicio encargado de construir y enviar el correo

    private static final Logger log = LoggerFactory.getLogger(EmailService.class); // Logger de la clase

    private final JavaMailSender mailSender;                            // Componente que realmente envia el correo
    private final String from;                                          // Direccion remitente (tu Gmail)
    private final String to;                                            // Direccion destinataria (a quien llega la alerta)
    private final long cooldownMinutos;                                // Minutos de espera entre correos del MISMO sensor (anti-spam)

    // Recuerda el instante del ultimo correo enviado por cada sensor (para aplicar el cooldown).
    private final Map<Long, Instant> ultimoEnvioPorSensor = new ConcurrentHashMap<>(); // sensorId -> ultimo envio

    public EmailService(JavaMailSender mailSender,                      // Constructor: Spring inyecta el sender y la configuracion
                        @Value("${notify.from}") String from,          // Remitente, desde la configuracion
                        @Value("${notify.to}") String to,              // Destinatario, desde la configuracion
                        @Value("${notify.cooldown-minutes:30}") long cooldownMinutos) { // Cooldown en minutos (por defecto 30)
        this.mailSender = mailSender;                                  // Guarda el sender
        this.from = from;                                              // Guarda el remitente
        this.to = to;                                                  // Guarda el destinatario
        this.cooldownMinutos = cooldownMinutos;                        // Guarda el cooldown configurado
    }

    /**
     * Envío AUTOMÁTICO al destinatario fijo (notify.to), respetando el cooldown por sensor.
     * Lo llama el AlertaSubscriber cuando llega una alerta de severidad configurada.
     */
    public void enviarAlerta(AlertaMensaje a) {                        // Recibe la alerta y manda el correo automatico
        // ---- Cooldown: evita ráfagas de correos del mismo sensor (no enojar a Gmail) ----
        Long sensor = a.sensorId() != null ? a.sensorId() : 0L;        // Clave del sensor (0 si viniera nulo)
        Instant ahora = Instant.now();                                 // Momento actual
        Instant ultimo = ultimoEnvioPorSensor.get(sensor);            // Ultimo envio registrado para ese sensor
        if (ultimo != null && Duration.between(ultimo, ahora).toMinutes() < cooldownMinutos) { // Si aun no pasa el cooldown...
            log.info("Sensor {} en cooldown ({} min): NO se reenvía correo (alerta {})", sensor, cooldownMinutos, a.severidad()); // ...lo registra
            return;                                                   // ...y no envia (evita la rafaga)
        }
        despachar(to, a);                                            // Envia al destinatario fijo configurado
        ultimoEnvioPorSensor.put(sensor, ahora);                     // Registra el envio para aplicar el cooldown a los siguientes
        log.info("Correo (automático) enviado a {} [{}] sensor={}", to, a.severidad(), a.sensorId()); // Log del envio automatico
    }

    /**
     * Envío MANUAL a un destinatario elegido por el usuario (desde el botón de la UI).
     * No aplica cooldown: si el usuario lo pide, se manda.
     */
    public void enviarA(String destinatario, AlertaMensaje a) {       // Recibe el correo destino y la alerta
        despachar(destinatario, a);                                  // Envia directamente al destinatario indicado
        log.info("Correo (manual) enviado a {} [{}] sensor={}", destinatario, a.severidad(), a.sensorId()); // Log del envio manual
    }

    /** Construye y envía físicamente el correo a un destinatario (lógica común a ambos envíos). */
    private void despachar(String destinatario, AlertaMensaje a) {    // Arma el SimpleMailMessage y lo manda por SMTP
        SimpleMailMessage correo = new SimpleMailMessage();            // Crea un correo simple (texto plano)
        correo.setFrom(from);                                         // Quien envia (tu Gmail)
        correo.setTo(destinatario);                                  // Quien recibe (fijo o elegido)
        correo.setSubject("[AgroSmart] Alerta " + a.severidad() + " - " + a.tipo() + " en " + a.zona()); // Asunto resumido
        correo.setText(construirCuerpo(a));                          // Cuerpo del mensaje con el detalle
        mailSender.send(correo);                                     // Envia el correo por SMTP
    }

    /** Arma el texto del correo con los datos de la alerta. */
    private String construirCuerpo(AlertaMensaje a) {                  // Genera el cuerpo legible del correo
        return "Se ha generado una alerta en tu invernadero AgroSmart.\n\n" + // Encabezado del mensaje
                "Severidad:  " + a.severidad() + "\n" +                // Linea con la severidad
                "Tipo:       " + a.tipo() + "\n" +                     // Linea con el tipo de sensor
                "Zona:       " + a.zona() + "\n" +                     // Linea con la zona
                "Sensor ID:  " + a.sensorId() + "\n" +                 // Linea con el id del sensor
                "Valor:      " + a.valor() + "\n" +                    // Linea con el valor detectado
                "Fecha:      " + a.fecha() + "\n\n" +                  // Linea con la fecha
                "Mensaje:    " + a.mensaje() + "\n\n" +                // Mensaje descriptivo de la alerta
                "—\nEste es un aviso automático de AgroSmart.";        // Pie del correo
    }
}
