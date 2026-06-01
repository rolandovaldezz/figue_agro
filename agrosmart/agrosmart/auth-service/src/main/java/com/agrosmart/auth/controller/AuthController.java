package com.agrosmart.auth.controller; // Paquete de los controladores REST (la capa que recibe las peticiones HTTP)

import com.agrosmart.auth.dto.LoginRequest; // DTO (objeto de datos) con el username y password que entran en el login
import com.agrosmart.auth.dto.LoginResponse; // DTO con la respuesta del login (token JWT y datos del usuario)
import com.agrosmart.auth.dto.RegisterRequest; // DTO con los datos para registrar un usuario nuevo
import com.agrosmart.auth.service.AuthService; // Servicio que contiene la lógica de negocio de autenticación
import jakarta.validation.Valid; // Anotación que dispara la validación automática del cuerpo de la petición (@NotBlank, @Email, etc.)
import org.springframework.http.HttpStatus; // Enum con los códigos de estado HTTP (200, 201, 401, etc.)
import org.springframework.http.ResponseEntity; // Permite construir la respuesta HTTP controlando código de estado y cuerpo
import org.springframework.web.bind.annotation.*; // Importa todas las anotaciones web de Spring (@RestController, @PostMapping, @RequestBody, etc.)

/**
 * Endpoints REST de autenticación.
 *
 * OJO con las rutas: el API Gateway aplica StripPrefix=1, así que la URL
 * pública /api/auth/login llega aquí como /auth/login.
 */
@RestController // Marca la clase como controlador REST: cada método devuelve directamente datos (JSON) en el cuerpo de la respuesta
@RequestMapping("/auth") // Prefijo de ruta común: todas las URLs de esta clase empiezan por /auth
public class AuthController { // Controlador que expone los endpoints de login y registro

    private final AuthService authService; // Referencia al servicio de autenticación (donde está la lógica real); final = se asigna una sola vez

    public AuthController(AuthService authService) { // Constructor: Spring inyecta automáticamente el AuthService aquí (inyección por constructor)
        this.authService = authService; // Guarda el servicio recibido en el campo de la clase para poder usarlo
    }

    /** Pública (el gateway NO valida JWT en /api/auth/**). */
    @PostMapping("/login") // Atiende peticiones HTTP POST a /auth/login
    public LoginResponse login(@Valid @RequestBody LoginRequest req) { // Recibe el cuerpo JSON como LoginRequest, lo valida (@Valid) y devuelve un LoginResponse
        return authService.login(req.username(), req.password()); // Delega en el servicio: valida credenciales y devuelve el token; el resultado se serializa a JSON
    }

    @PostMapping("/register") // Atiende peticiones HTTP POST a /auth/register
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest req) { // Recibe y valida el cuerpo JSON; devuelve una respuesta sin cuerpo (Void)
        authService.register(req); // Pide al servicio que cree el usuario nuevo en la base de datos
        return ResponseEntity.status(HttpStatus.CREATED).build(); // Responde con código 201 CREATED indicando que el recurso se creó con éxito
    }
}
