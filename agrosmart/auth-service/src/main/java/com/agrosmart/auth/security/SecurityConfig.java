package com.agrosmart.auth.security; // Paquete con las clases relacionadas con seguridad

import org.springframework.context.annotation.Bean; // Anotación que marca un método como productor de un bean gestionado por Spring
import org.springframework.context.annotation.Configuration; // Marca la clase como fuente de configuración de beans de Spring
import org.springframework.security.config.annotation.web.builders.HttpSecurity; // Objeto para configurar la seguridad de las peticiones HTTP
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity; // Activa la seguridad web de Spring Security
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // Implementación del cifrado de contraseñas con el algoritmo bcrypt
import org.springframework.security.crypto.password.PasswordEncoder; // Interfaz para codificar y comparar contraseñas
import org.springframework.security.web.SecurityFilterChain; // Cadena de filtros de seguridad que se aplica a cada petición

/**
 * Configuración de seguridad del auth-service.
 *
 * Importante: la validación del JWT la hace el API GATEWAY (no aquí). Este
 * servicio solo necesita:
 *   1. Dejar SUS endpoints abiertos (permitAll), porque el gateway es la
 *      verdadera puerta de seguridad.
 *   2. Un PasswordEncoder con bcrypt para comparar contraseñas.
 *
 * Sin esta clase, spring-boot-starter-security bloquearía todo por defecto.
 */
@Configuration // Indica que esta clase define beans de configuración de Spring
@EnableWebSecurity // Habilita y personaliza la seguridad web de Spring Security en este servicio
public class SecurityConfig { // Clase de configuración de la seguridad

    @Bean // El objeto devuelto se registra como bean: Spring lo usará como su cadena de filtros de seguridad
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception { // Define las reglas de seguridad HTTP; recibe el objeto de configuración http
        http // Configura la seguridad encadenando opciones
            .csrf(csrf -> csrf.disable()) // Desactiva la protección CSRF (innecesaria en una API REST sin sesiones/cookies)
            .httpBasic(basic -> basic.disable()) // Desactiva la autenticación HTTP Basic (no se usa aquí)
            .formLogin(form -> form.disable()) // Desactiva el formulario de login por defecto de Spring (es una API, no una web con formularios)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()); // Permite el acceso a TODAS las rutas sin autenticación (la seguridad real la aplica el API Gateway)
        return http.build(); // Construye y devuelve la cadena de filtros configurada
    }

    /** bcrypt: el mismo algoritmo con el que se guardaron los hashes en init.sql. */
    @Bean // Registra el codificador de contraseñas como bean para inyectarlo en otros componentes (p.ej. AuthService)
    public PasswordEncoder passwordEncoder() { // Provee el codificador de contraseñas de la aplicación
        return new BCryptPasswordEncoder(); // Devuelve un codificador bcrypt para cifrar y comparar contraseñas de forma segura
    }
}
