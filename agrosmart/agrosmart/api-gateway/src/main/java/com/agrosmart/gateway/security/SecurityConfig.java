// Declaracion del paquete: ubica esta clase en el subpaquete de seguridad del gateway
package com.agrosmart.gateway.security;

// Importa Bean: marca un metodo cuyo retorno Spring administra como componente
import org.springframework.context.annotation.Bean;
// Importa Configuration: indica que esta clase define configuracion de Spring
import org.springframework.context.annotation.Configuration;
// Importa EnableWebFluxSecurity: activa la seguridad para aplicaciones reactivas (WebFlux)
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
// Importa ServerHttpSecurity: constructor para definir reglas de seguridad reactivas
import org.springframework.security.config.web.server.ServerHttpSecurity;
// Importa SecurityWebFilterChain: la cadena de filtros de seguridad que se aplica a las peticiones
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * El gateway usa WebFlux (reactivo), así que la seguridad también es reactiva.
 *
 * Dejamos pasar TODO a nivel de Spring Security (permitAll) porque la
 * verdadera validación del JWT la hace nuestro JwtAuthenticationFilter.
 * Sin esta clase, spring-boot-starter-security bloquearía todo con un login
 * por defecto.
 */
// Configuration: indica que esta clase aporta definiciones de beans de configuracion
@Configuration
// EnableWebFluxSecurity: habilita la seguridad reactiva de Spring en el gateway
@EnableWebFluxSecurity
// Clase de configuracion de seguridad del API Gateway
public class SecurityConfig {

    // Bean: Spring administra el objeto devuelto por este metodo
    @Bean
    // Define la cadena de filtros de seguridad usando el constructor ServerHttpSecurity
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        // Configura las reglas de seguridad del gateway
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)            // desactiva CSRF (no aplica en una API stateless con JWT)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)  // desactiva la autenticacion HTTP Basic
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)  // desactiva el formulario de login por defecto
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll()); // permite todas las peticiones aqui (el JWT lo valida nuestro filtro)
        // Construye y devuelve la cadena de filtros de seguridad configurada
        return http.build();
    }
}
