CREATE DATABASE  IF NOT EXISTS `agrosmart` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `agrosmart`;
-- MySQL dump 10.13  Distrib 8.0.43, for Win64 (x86_64)
--
-- Host: localhost    Database: agrosmart
-- ------------------------------------------------------
-- Server version	8.0.43

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `alertas`
--

DROP TABLE IF EXISTS `alertas`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alertas` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sensor_id` bigint NOT NULL,
  `lectura_id` bigint DEFAULT NULL,
  `umbral_id` bigint DEFAULT NULL,
  `tipo_alerta` varchar(50) NOT NULL,
  `mensaje` varchar(500) NOT NULL,
  `valor_detectado` double NOT NULL,
  `severidad` varchar(20) NOT NULL,
  `fecha_generacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resuelta` tinyint(1) NOT NULL DEFAULT '0',
  `fecha_resolucion` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_alertas_sensor` (`sensor_id`),
  KEY `fk_alertas_lectura` (`lectura_id`),
  KEY `fk_alertas_umbral` (`umbral_id`),
  KEY `idx_alertas_resuelta` (`resuelta`),
  KEY `idx_alertas_fecha` (`fecha_generacion` DESC),
  KEY `idx_alertas_severidad` (`severidad`),
  CONSTRAINT `fk_alertas_lectura` FOREIGN KEY (`lectura_id`) REFERENCES `lecturas_sensor` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_alertas_sensor` FOREIGN KEY (`sensor_id`) REFERENCES `sensores` (`id`),
  CONSTRAINT `fk_alertas_umbral` FOREIGN KEY (`umbral_id`) REFERENCES `umbrales_alerta` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `alertas`
--

LOCK TABLES `alertas` WRITE;
/*!40000 ALTER TABLE `alertas` DISABLE KEYS */;
/*!40000 ALTER TABLE `alertas` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `lecturas_sensor`
--

DROP TABLE IF EXISTS `lecturas_sensor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lecturas_sensor` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sensor_id` bigint NOT NULL,
  `valor` double NOT NULL,
  `timestamp_lectura` datetime NOT NULL,
  `fecha_registro` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_lecturas_sensor` (`sensor_id`),
  KEY `idx_lecturas_timestamp` (`timestamp_lectura` DESC),
  KEY `idx_lecturas_sensor_timestamp` (`sensor_id`,`timestamp_lectura` DESC),
  CONSTRAINT `fk_lecturas_sensor` FOREIGN KEY (`sensor_id`) REFERENCES `sensores` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lecturas_sensor`
--

LOCK TABLES `lecturas_sensor` WRITE;
/*!40000 ALTER TABLE `lecturas_sensor` DISABLE KEYS */;
/*!40000 ALTER TABLE `lecturas_sensor` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sensores`
--

DROP TABLE IF EXISTS `sensores`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sensores` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nombre` varchar(100) NOT NULL,
  `tipo` varchar(50) NOT NULL,
  `zona` varchar(50) NOT NULL,
  `unidad` varchar(20) NOT NULL,
  `valor_min_esperado` double DEFAULT NULL,
  `valor_max_esperado` double DEFAULT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT '1',
  `fecha_creacion` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sensores_tipo` (`tipo`),
  KEY `idx_sensores_zona` (`zona`),
  KEY `idx_sensores_activo` (`activo`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sensores`
--

LOCK TABLES `sensores` WRITE;
/*!40000 ALTER TABLE `sensores` DISABLE KEYS */;
INSERT INTO `sensores` VALUES (1,'Sensor de Temperatura Norte','temperatura','zona1','C',18,30,1,'2026-05-31 18:50:27'),(2,'Sensor de Humedad Norte','humedad','zona1','%',40,80,1,'2026-05-31 18:50:27'),(3,'Sensor de pH Norte','ph','zona1','pH',5.5,7.5,1,'2026-05-31 18:50:27'),(4,'Sensor de Temperatura Sur','temperatura','zona2','C',18,30,1,'2026-05-31 18:50:27'),(5,'Sensor de Humedad Sur','humedad','zona2','%',40,80,1,'2026-05-31 18:50:27');
/*!40000 ALTER TABLE `sensores` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `umbrales_alerta`
--

DROP TABLE IF EXISTS `umbrales_alerta`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `umbrales_alerta` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tipo_sensor` varchar(50) NOT NULL,
  `zona` varchar(50) DEFAULT NULL,
  `valor_min` double DEFAULT NULL,
  `valor_max` double DEFAULT NULL,
  `severidad` varchar(20) NOT NULL,
  `mensaje_tpl` varchar(255) DEFAULT NULL,
  `activo` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `idx_umbrales_tipo` (`tipo_sensor`),
  KEY `idx_umbrales_activo` (`activo`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `umbrales_alerta`
--

LOCK TABLES `umbrales_alerta` WRITE;
/*!40000 ALTER TABLE `umbrales_alerta` DISABLE KEYS */;
INSERT INTO `umbrales_alerta` VALUES (1,'temperatura',NULL,NULL,35,'ALTA','Temperatura demasiado alta en {zona}: {valor}°C',1),(2,'temperatura',NULL,10,NULL,'ALTA','Temperatura demasiado baja en {zona}: {valor}°C',1),(3,'temperatura',NULL,NULL,40,'CRITICA','TEMPERATURA CRÍTICA en {zona}: {valor}°C - Acción inmediata',1),(4,'humedad',NULL,30,NULL,'MEDIA','Humedad baja en {zona}: {valor}%',1),(5,'humedad',NULL,NULL,90,'MEDIA','Humedad excesiva en {zona}: {valor}%',1),(6,'ph',NULL,5,NULL,'ALTA','pH demasiado ácido en {zona}: {valor}',1),(7,'ph',NULL,NULL,8,'ALTA','pH demasiado alcalino en {zona}: {valor}',1);
/*!40000 ALTER TABLE `umbrales_alerta` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `usuarios`
--

DROP TABLE IF EXISTS `usuarios`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `usuarios` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `email` varchar(100) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `nombre_completo` varchar(150) DEFAULT NULL,
  `rol` varchar(20) NOT NULL DEFAULT 'AGRICULTOR',
  `activo` tinyint(1) NOT NULL DEFAULT '1',
  `fecha_registro` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `ultimo_acceso` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_usuarios_username` (`username`),
  UNIQUE KEY `uq_usuarios_email` (`email`),
  KEY `idx_usuarios_rol` (`rol`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `usuarios`
--

LOCK TABLES `usuarios` WRITE;
/*!40000 ALTER TABLE `usuarios` DISABLE KEYS */;
INSERT INTO `usuarios` VALUES (1,'admin','admin@agrosmart.local','$2b$10$EipX46/PowsMfX7tsIOYduX9GKJQ4zablNmzO0BgDRAy9YnIOoXPm','Administrador del Sistema','ADMIN',1,'2026-05-31 18:50:27',NULL),(2,'agricultor','agricultor@agrosmart.local','$2b$10$EipX46/PowsMfX7tsIOYduX9GKJQ4zablNmzO0BgDRAy9YnIOoXPm','Juan Pérez','AGRICULTOR',1,'2026-05-31 18:50:27',NULL);
/*!40000 ALTER TABLE `usuarios` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'agrosmart'
--

--
-- Dumping routines for database 'agrosmart'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-01 14:27:00
