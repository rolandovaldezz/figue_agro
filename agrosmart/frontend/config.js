/* ============================================================
 * AgroSmart - Configuración del frontend
 * ============================================================
 * Este es el ÚNICO archivo que necesitas tocar para conectar
 * el frontend con tu backend en la nube.
 * ============================================================ */

// Crea un objeto global de configuracion accesible desde cualquier script (window lo hace global)
window.AGROSMART_CONFIG = {

  /* URL base del API Gateway (incluye el prefijo /api).
   *
   *  - En local con Docker:   "https://localhost:8443/api"
   *  - En tu nube privada:    "https://TU_IP_O_DOMINIO:8443/api"
   *
   * Importante: como el certificado es autofirmado, la primera vez
   * debes abrir esa URL en el navegador y aceptar la advertencia,
   * o el navegador bloqueará las peticiones del frontend.
   */
  // Direccion del backend. Por defecto usamos una ruta RELATIVA "/api": el propio
  // NGINX del frontend hace de proxy hacia el gateway, así el navegador habla con
  // el MISMO origen (http://localhost:8080) y NO necesita aceptar el certificado
  // autofirmado ni lidiar con CORS.
  //   - Vía proxy (recomendado): "/api"
  //   - Directo al gateway:      "https://localhost:8443/api"  (requiere aceptar el certificado)
  API_BASE_URL: "/api",

  /* MODO DEMO
   * ---------
   *  true  → el frontend NO llama al backend. Usa datos simulados en
   *          memoria para que puedas ver todo funcionando AHORA, sin
   *          Docker ni base de datos. Login: admin / admin123.
   *
   *  false → el frontend llama de verdad al API_BASE_URL de arriba.
   *          Úsalo cuando tu backend en la nube ya esté levantado.
   */
  // Interruptor que decide si se usan datos simulados (true) o el backend real (false)
  DEMO_MODE: false,

  /* Cada cuántos milisegundos se refresca el dashboard
   * (coincide con el intervalo del simulador del sensor-service). */
  // Tiempo en milisegundos entre cada actualizacion automatica del dashboard (5000 ms = 5 segundos)
  REFRESH_INTERVAL_MS: 5000,

  /* Cuántas lecturas históricas pedir/mostrar por sensor. */
  // Numero de puntos (lecturas pasadas) que se muestran en la grafica de historial de cada sensor
  HISTORY_POINTS: 30
};
