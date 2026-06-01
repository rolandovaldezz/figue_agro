# ============================================================
# api-gateway - Dockerfile (multi-stage)
# ============================================================
# Punto de entrada único del sistema. Termina TLS y valida JWT.
# Los certificados se montan como volumen (/app/certs) desde
# el host por docker-compose.
# ============================================================
# Build multi-stage: compila con Maven y ejecuta con una imagen JRE liviana.

# ---------- Stage 1: BUILD ----------
# Imagen base con Maven + JDK 17 (Alpine) usada solo para compilar
FROM maven:3.9-eclipse-temurin-17-alpine AS build
# Directorio de trabajo dentro del contenedor donde ocurre la compilacion
WORKDIR /build

# Copiamos solo el pom.xml primero para aprovechar la cache de dependencias de Docker
COPY pom.xml ./
# Descarga por adelantado las dependencias declaradas en el pom (-B = modo no interactivo)
RUN mvn dependency:go-offline -B

# Copiamos el codigo fuente y empaquetamos el JAR (sin ejecutar tests)
COPY src ./src
RUN mvn clean package -DskipTests -B


# ---------- Stage 2: RUNTIME ----------
# Imagen base solo con JRE 17 (sin compilador): mas pequena y segura para ejecutar
FROM eclipse-temurin:17-jre-alpine

# Crea un usuario sin privilegios y corre el contenedor con el (buena practica de seguridad)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Directorio de trabajo de la aplicacion en ejecucion
WORKDIR /app

# Copiamos solo el JAR ya compilado desde el stage de build
COPY --from=build /build/target/*.jar app.jar

# Puerto HTTPS expuesto al exterior
EXPOSE 8443

# Healthcheck: consulta el endpoint de salud por HTTPS (--no-check-certificate por el certificado autofirmado)
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- --no-check-certificate https://localhost:8443/actuator/health || exit 1

# Comando que arranca el gateway al iniciar el contenedor
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
