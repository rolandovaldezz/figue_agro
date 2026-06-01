package com.agrosmart.auth.service; // Paquete de los servicios (la capa con la lógica de negocio)

import com.agrosmart.auth.dto.LoginResponse; // DTO de respuesta que se devuelve tras un login correcto
import com.agrosmart.auth.dto.RegisterRequest; // DTO con los datos de entrada para registrar un usuario
import com.agrosmart.auth.model.Usuario; // Entidad Usuario que se lee y guarda en la base de datos
import com.agrosmart.auth.repository.UsuarioRepository; // Repositorio para acceder a la tabla `usuarios`
import com.agrosmart.auth.security.JwtService; // Servicio que genera los tokens JWT
import org.springframework.http.HttpStatus; // Códigos de estado HTTP usados al lanzar errores controlados
import org.springframework.security.crypto.password.PasswordEncoder; // Codificador bcrypt para cifrar y comparar contraseñas
import org.springframework.stereotype.Service; // Marca la clase como servicio gestionado por Spring
import org.springframework.web.server.ResponseStatusException; // Excepción que devuelve al cliente un código HTTP concreto con un mensaje

import java.time.LocalDateTime; // Tipo de fecha y hora, para registrar el último acceso y la fecha de registro

/**
 * Lógica de autenticación: aquí ocurren los SELECT/INSERT/UPDATE reales
 * (a través del repositorio).
 */
@Service // Registra la clase como bean de servicio para que Spring la inyecte en el controlador
public class AuthService { // Servicio con la lógica de login y registro

    private final UsuarioRepository usuarioRepository; // Repositorio para consultar/guardar usuarios; final = se asigna una vez
    private final PasswordEncoder passwordEncoder; // Codificador bcrypt para cifrar y verificar contraseñas
    private final JwtService jwtService; // Servicio que genera el token JWT tras un login válido

    public AuthService(UsuarioRepository usuarioRepository, // Constructor: Spring inyecta las tres dependencias necesarias
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.usuarioRepository = usuarioRepository; // Guarda el repositorio en el campo de la clase
        this.passwordEncoder = passwordEncoder; // Guarda el codificador de contraseñas
        this.jwtService = jwtService; // Guarda el servicio de tokens JWT
    }

    /**
     * LOGIN:
     *  1. SELECT del usuario por username.
     *  2. Verifica que esté activo.
     *  3. Compara la contraseña contra el hash bcrypt.
     *  4. UPDATE de ultimo_acceso.
     *  5. Devuelve un JWT firmado.
     */
    public LoginResponse login(String username, String passwordPlano) { // Inicia sesión: recibe usuario y contraseña en texto plano y devuelve la respuesta con el token
        Usuario u = usuarioRepository.findByUsername(username)   // <-- SELECT
                .orElseThrow(() -> new ResponseStatusException( // Si el Optional viene vacío (usuario no existe), lanza un error
                        HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos")); // Responde 401 con mensaje genérico (no revela si falló el usuario o la contraseña)

        if (Boolean.FALSE.equals(u.getActivo())) { // Comprueba si la cuenta está inactiva (compara con FALSE de forma segura ante null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuario inactivo"); // Si está inactiva, responde 403 FORBIDDEN
        }

        if (!passwordEncoder.matches(passwordPlano, u.getPasswordHash())) { // Compara la contraseña recibida con el hash bcrypt guardado; si NO coincide...
            throw new ResponseStatusException( // ...lanza un error de credenciales inválidas
                    HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos"); // Responde 401 con el mismo mensaje genérico por seguridad
        }

        u.setUltimoAcceso(LocalDateTime.now()); // Actualiza la marca de último acceso al momento actual
        usuarioRepository.save(u);                                // <-- UPDATE

        String token = jwtService.generar(u); // Genera el token JWT firmado con los datos del usuario
        return new LoginResponse(token, u.getUsername(), u.getRol(), u.getNombreCompleto()); // Devuelve la respuesta con el token y los datos básicos del usuario
    }

    /**
     * REGISTRO: crea un usuario nuevo con la contraseña cifrada (INSERT).
     */
    public void register(RegisterRequest req) { // Registra un usuario nuevo a partir de los datos recibidos
        if (usuarioRepository.existsByUsername(req.username())) { // Comprueba si ya existe un usuario con ese username
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El username ya existe"); // Si existe, responde 409 CONFLICT (no se puede duplicar)
        }
        if (usuarioRepository.existsByEmail(req.email())) { // Comprueba si ya existe un usuario con ese email
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya existe"); // Si existe, responde 409 CONFLICT
        }

        Usuario u = new Usuario(); // Crea un nuevo objeto Usuario vacío para rellenarlo
        u.setUsername(req.username()); // Asigna el username recibido
        u.setEmail(req.email()); // Asigna el email recibido
        u.setPasswordHash(passwordEncoder.encode(req.password()));  // cifra con bcrypt
        u.setNombreCompleto(req.nombreCompleto()); // Asigna el nombre completo recibido
        u.setRol(req.rol() == null || req.rol().isBlank() ? "AGRICULTOR" : req.rol()); // Si no se envió rol (null o vacío) asigna "AGRICULTOR" por defecto; si no, usa el enviado
        u.setActivo(true); // Marca la cuenta nueva como activa
        u.setFechaRegistro(LocalDateTime.now()); // Registra la fecha y hora actual como fecha de creación

        usuarioRepository.save(u);                                  // <-- INSERT
    }
}
