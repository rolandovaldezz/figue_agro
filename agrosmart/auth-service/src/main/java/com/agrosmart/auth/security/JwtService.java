package com.agrosmart.auth.security; // Paquete con las clases relacionadas con seguridad (tokens, configuración de Spring Security)

import com.agrosmart.auth.model.Usuario; // Entidad Usuario, de la que se sacan los datos que irán dentro del token
import io.jsonwebtoken.Jwts; // Clase principal de la librería jjwt para construir y firmar tokens JWT
import io.jsonwebtoken.security.Keys; // Utilidad de jjwt para crear claves criptográficas seguras a partir de bytes
import org.springframework.beans.factory.annotation.Value; // Permite inyectar valores de configuración (application.properties o variables de entorno)
import org.springframework.stereotype.Service; // Marca la clase como servicio gestionado por Spring (componente de lógica)

import javax.crypto.SecretKey; // Tipo de la clave secreta usada para firmar el token con HMAC
import java.nio.charset.StandardCharsets; // Constantes de codificación de caracteres (aquí UTF-8) para convertir el texto del secreto a bytes
import java.util.Date; // Tipo de fecha que jjwt usa para las marcas de emisión y expiración

/**
 * Genera los tokens JWT que firman la identidad del usuario.
 *
 * El token lleva:
 *   - subject  = username
 *   - claim "rol"    = ADMIN / AGRICULTOR
 *   - claim "nombre" = nombre completo
 *   - fecha de emisión y de expiración
 *
 * Se firma con HMAC-SHA usando JWT_SECRET (la misma clave que usa el
 * api-gateway para VALIDARLO). Por eso el secreto debe ser idéntico en
 * ambos servicios (viene de la variable de entorno JWT_SECRET).
 */
@Service // Registra la clase como bean de servicio para que Spring la inyecte donde se necesite (p.ej. en AuthService)
public class JwtService { // Servicio encargado de crear los tokens JWT

    private final SecretKey key; // Clave secreta con la que se firma el token; final = se fija una vez en el constructor
    private final long expirationMs; // Tiempo de vida del token en milisegundos; final = se fija una vez

    public JwtService( // Constructor: Spring inyecta aquí los valores de configuración
            @Value("${jwt.secret}") String secret, // Inyecta el secreto desde la propiedad jwt.secret (variable de entorno JWT_SECRET)
            @Value("${jwt.expiration-ms:3600000}") long expirationMs) { // Inyecta la expiración desde jwt.expiration-ms; si no existe usa 3600000 ms (1 hora)
        // La clave debe tener al menos 32 bytes (256 bits) para HS256.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); // Convierte el secreto en bytes UTF-8 y crea la clave HMAC-SHA para firmar
        this.expirationMs = expirationMs; // Guarda la duración del token en el campo de la clase
    }

    public String generar(Usuario usuario) { // Genera y devuelve un token JWT (texto) para el usuario indicado
        Date ahora = new Date(); // Momento actual: será la fecha de emisión del token
        Date expira = new Date(ahora.getTime() + expirationMs); // Fecha de expiración = ahora + duración configurada

        return Jwts.builder() // Inicia la construcción del token con jjwt
                .subject(usuario.getUsername()) // Subject (sujeto) del token = username; identifica de quién es el token
                .claim("rol", usuario.getRol()) // Claim personalizado "rol" con el rol del usuario (ADMIN/AGRICULTOR)
                .claim("nombre", usuario.getNombreCompleto()) // Claim personalizado "nombre" con el nombre completo
                .issuedAt(ahora) // Marca la fecha/hora de emisión del token
                .expiration(expira) // Marca la fecha/hora en que el token deja de ser válido
                .signWith(key) // Firma el token con la clave secreta para que no se pueda falsificar ni alterar
                .compact(); // Serializa todo a la cadena compacta final del JWT (header.payload.firma) y la devuelve
    }
}
