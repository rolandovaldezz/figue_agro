// Declaracion del paquete: ubica esta clase en el subpaquete de seguridad del gateway
package com.agrosmart.gateway.security;

// Importa Claims: contiene los datos (subject, rol, expiracion) que viajan dentro del token JWT
import io.jsonwebtoken.Claims;
// Importa Jwts: utilidad principal para construir y validar tokens JWT
import io.jsonwebtoken.Jwts;
// Importa Keys: ayuda a crear la clave secreta a partir de los bytes del JWT_SECRET
import io.jsonwebtoken.security.Keys;
// Importa Logger: interfaz para escribir mensajes de registro (logs)
import org.slf4j.Logger;
// Importa LoggerFactory: fabrica que crea la instancia del Logger
import org.slf4j.LoggerFactory;
// Importa Value: permite inyectar valores de configuracion (como jwt.secret) en atributos
import org.springframework.beans.factory.annotation.Value;
// Importa GatewayFilterChain: la cadena de filtros que continua procesando la peticion
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
// Importa GlobalFilter: interfaz para crear un filtro que aplica a TODAS las rutas del gateway
import org.springframework.cloud.gateway.filter.GlobalFilter;
// Importa Ordered: permite definir el orden de ejecucion de este filtro respecto a otros
import org.springframework.core.Ordered;
// Importa HttpHeaders: constantes con nombres estandar de cabeceras HTTP (como Authorization)
import org.springframework.http.HttpHeaders;
// Importa HttpMethod: enumeracion de metodos HTTP (GET, POST, OPTIONS, etc.)
import org.springframework.http.HttpMethod;
// Importa HttpStatus: enumeracion de codigos de estado HTTP (como 401 UNAUTHORIZED)
import org.springframework.http.HttpStatus;
// Importa ServerHttpRequest: representa la peticion HTTP entrante en el modelo reactivo
import org.springframework.http.server.reactive.ServerHttpRequest;
// Importa Component: marca esta clase para que Spring la detecte y administre como bean
import org.springframework.stereotype.Component;
// Importa ServerWebExchange: contiene la peticion y la respuesta de un intercambio web reactivo
import org.springframework.web.server.ServerWebExchange;
// Importa Mono: tipo reactivo que representa un resultado asincrono (cero o un elemento)
import reactor.core.publisher.Mono;

// Importa SecretKey: tipo de clave criptografica usada para verificar la firma del JWT
import javax.crypto.SecretKey;
// Importa StandardCharsets: provee el juego de caracteres UTF-8 para convertir el secreto a bytes
import java.nio.charset.StandardCharsets;

/**
 * Filtro GLOBAL del gateway: valida el JWT en CADA petición, salvo en las
 * rutas públicas (login/registro y preflight CORS).
 *
 * Es el corazón de la seguridad del sistema: los microservicios internos
 * confían en que, si una petición llega hasta ellos, el gateway ya validó el
 * token. Por eso ningún microservicio está expuesto al exterior (solo el
 * gateway publica el puerto 8443).
 *
 * Usa la MISMA clave (JWT_SECRET) con la que el auth-service firma los tokens.
 */
// Component: registra esta clase como filtro global que Spring aplica automaticamente
@Component
// Clase del filtro: implementa GlobalFilter (aplica a todas las rutas) y Ordered (define prioridad)
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    // Logger para registrar eventos de este filtro (por ejemplo, rechazos de peticiones)
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    // Clave secreta usada para verificar la firma de cada token JWT
    private final SecretKey key;

    // Constructor: recibe el secreto desde la configuracion (jwt.secret) y construye la clave
    public JwtAuthenticationFilter(@Value("${jwt.secret}") String secret) {
        // Genera la clave HMAC-SHA a partir de los bytes UTF-8 del secreto compartido
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Sobrescribe el metodo filter: se ejecuta en cada peticion que pasa por el gateway
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Obtiene la peticion HTTP entrante del intercambio
        ServerHttpRequest request = exchange.getRequest();
        // Extrae la ruta solicitada (por ejemplo /api/sensores/...)
        String path = request.getPath().value();

        // ---- Rutas que NO requieren token ----
        // Si la ruta es de autenticacion, es un preflight CORS o es el healthcheck, deja pasar sin validar
        if (path.startsWith("/api/auth/")                       // login / registro
                || request.getMethod() == HttpMethod.OPTIONS    // preflight CORS
                || path.startsWith("/actuator/health")) {       // healthcheck
            // Continua la cadena de filtros sin exigir token
            return chain.filter(exchange);
        }

        // ---- Resto de rutas: exige Bearer token válido ----
        // Lee el valor de la cabecera Authorization de la peticion
        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        // Si no hay cabecera o no empieza con "Bearer ", rechaza la peticion con 401
        if (auth == null || !auth.startsWith("Bearer ")) {
            return rechazar(exchange, "Falta el encabezado Authorization");
        }

        // Quita el prefijo "Bearer " (7 caracteres) para quedarse solo con el token
        String token = auth.substring(7);
        // Intenta validar el token; si algo falla, se captura la excepcion mas abajo
        try {
            // Verifica la firma del token con la clave y obtiene sus datos (claims)
            Claims claims = Jwts.parser()
                    .verifyWith(key)                  // usa la clave secreta para comprobar la firma
                    .build()                          // construye el parser configurado
                    .parseSignedClaims(token)         // valida firma y expiracion del token
                    .getPayload();                    // extrae el contenido (claims) del token

            // Propaga la identidad a los microservicios internos (por si la necesitan).
            // Crea una copia de la peticion agregando cabeceras con el usuario y su rol
            ServerWebExchange mutado = exchange.mutate()
                    .request(r -> r
                            .header("X-Auth-User", claims.getSubject())                 // usuario tomado del campo subject
                            .header("X-Auth-Rol", String.valueOf(claims.get("rol"))))   // rol tomado del claim "rol"
                    .build();
            // Continua la cadena de filtros con la peticion enriquecida con la identidad
            return chain.filter(mutado);

        } catch (Exception e) {
            // Si el token es invalido o expiro, rechaza la peticion con 401
            return rechazar(exchange, "Token inválido o expirado");
        }
    }

    // Metodo auxiliar que responde con 401 (no autorizado) y registra el motivo
    private Mono<Void> rechazar(ServerWebExchange exchange, String motivo) {
        // Registra en el log (nivel debug) por que se rechazo la peticion
        log.debug("Petición rechazada (401): {}", motivo);
        // Establece el codigo de estado 401 UNAUTHORIZED en la respuesta
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        // Cierra la respuesta sin cuerpo y termina el procesamiento
        return exchange.getResponse().setComplete();
    }

    /** Se ejecuta lo más temprano posible en la cadena de filtros. */
    // Sobrescribe getOrder para definir la prioridad de ejecucion del filtro
    @Override
    public int getOrder() {
        // Devuelve la maxima prioridad para que la validacion del token ocurra antes que otros filtros
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
