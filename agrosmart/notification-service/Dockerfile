# ============================================================
# notification-service - Dockerfile (multi-stage)
# ============================================================
# Stage 1: compila el JAR con Maven
# Stage 2: empaqueta el JAR sobre una imagen ligera con solo JRE
# ============================================================

# ---------- Stage 1: BUILD ----------
# Imagen con Maven + JDK 17 (Alpine) usada solo para compilar
FROM maven:3.9-eclipse-temurin-17-alpine AS build
# Carpeta de trabajo dentro del contenedor de build
WORKDIR /build

# Copiamos primero el pom para cachear la descarga de dependencias
COPY pom.xml ./
# Descarga por adelantado las dependencias declaradas
RUN mvn dependency:go-offline -B

# Copiamos el codigo fuente y compilamos el JAR (sin tests para un build mas rapido)
COPY src ./src
RUN mvn clean package -DskipTests -B


# ---------- Stage 2: RUNTIME ----------
# Imagen ligera solo con JRE 17 para ejecutar
FROM eclipse-temurin:17-jre-alpine

# Usuario sin privilegios (buena práctica de seguridad)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Carpeta de trabajo de la app
WORKDIR /app

# Copiamos el JAR ya compilado desde el stage de build
COPY --from=build /build/target/*.jar app.jar

# Puerto interno del servicio (8084 = notification-service)
EXPOSE 8084

# Healthcheck: Docker consulta el actuator para saber si el contenedor esta vivo
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8084/actuator/health || exit 1

# Comando que arranca la aplicacion
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
