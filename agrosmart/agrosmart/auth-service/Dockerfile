# ============================================================
# auth-service - Dockerfile (multi-stage)
# ============================================================
# Stage 1: compila el JAR con Maven
# Stage 2: empaqueta el JAR sobre una imagen ligera con solo JRE
# Imagen final: ~180 MB (vs ~500 MB si incluyéramos Maven)
# ============================================================
# Un build multi-stage usa una imagen pesada solo para compilar
# y luego copia el resultado a una imagen liviana para ejecutar.

# ---------- Stage 1: BUILD ----------
# Imagen base con Maven + JDK 17 (Alpine = muy liviana) usada solo para compilar
FROM maven:3.9-eclipse-temurin-17-alpine AS build
# Directorio de trabajo dentro del contenedor donde ocurre la compilacion
WORKDIR /build

# Primero copiamos solo el pom.xml para que Docker cachee las
# dependencias (no se vuelven a bajar si solo cambió el código)
COPY pom.xml ./
# Descarga por adelantado todas las dependencias declaradas en el pom (-B = modo no interactivo)
RUN mvn dependency:go-offline -B

# Ahora sí copiamos el código fuente y compilamos
COPY src ./src
# Compila y empaqueta el JAR saltando los tests (-DskipTests) para un build mas rapido
RUN mvn clean package -DskipTests -B


# ---------- Stage 2: RUNTIME ----------
# Imagen base solo con JRE 17 (sin Maven ni compilador): mas pequena y segura para ejecutar
FROM eclipse-temurin:17-jre-alpine

# Crear usuario sin privilegios (buena práctica de seguridad)
# Evita ejecutar la aplicacion como root dentro del contenedor
RUN addgroup -S spring && adduser -S spring -G spring
# A partir de aqui el contenedor corre con el usuario 'spring'
USER spring:spring

# Directorio de trabajo de la aplicacion en ejecucion
WORKDIR /app

# Copiamos solo el JAR ya compilado desde el stage de build
COPY --from=build /build/target/*.jar app.jar

# Documenta el puerto interno en el que escucha el servicio (8081 = auth-service)
EXPOSE 8081

# Healthcheck opcional: pide al actuator si está vivo
# Docker consulta este endpoint periodicamente para marcar el contenedor como sano o no
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health || exit 1

# Comando que arranca la aplicacion al iniciar el contenedor
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
