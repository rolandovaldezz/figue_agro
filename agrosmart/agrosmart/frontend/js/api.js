/* ============================================================
 * AgroSmart - Capa de acceso al API
 * ============================================================
 * Centraliza TODAS las llamadas al backend. Según config.js:
 *   - DEMO_MODE = true  → responde con MockBackend (datos simulados)
 *   - DEMO_MODE = false → hace fetch() real al API Gateway con el JWT
 *
 * Las funciones devuelven Promesas y normalizan los nombres de
 * campos (acepta camelCase y snake_case del backend).
 * ============================================================ */

// Crea el objeto global Api usando una funcion autoejecutable (IIFE) para encapsular lo privado
window.Api = (function () {
  // Atajo a la configuracion global definida en config.js
  const CFG = window.AGROSMART_CONFIG;

  // -------- Sesión / token --------
  // Nombre de la clave en localStorage donde se guarda el token JWT
  const TOKEN_KEY = "agrosmart_token";
  // Nombre de la clave en localStorage donde se guardan los datos del usuario
  const USER_KEY  = "agrosmart_user";

  // Guarda en localStorage el token y los datos del usuario tras un login exitoso
  function setSession(data) {
    // Almacena el token JWT en el navegador
    localStorage.setItem(TOKEN_KEY, data.token);
    // Almacena los datos del usuario como texto JSON, tomando el primer campo que exista
    localStorage.setItem(USER_KEY, JSON.stringify({
      username: data.username || pick(data, "username", "user") || "", // Nombre de usuario
      rol: data.rol || pick(data, "rol", "role") || "",                // Rol (ADMIN, AGRICULTOR...)
      nombre_completo: pick(data, "nombre_completo", "nombreCompleto") || "" // Nombre completo
    }));
  }
  // Devuelve el token guardado (o null si no hay sesion)
  function getToken() { return localStorage.getItem(TOKEN_KEY); }
  // Devuelve el objeto del usuario guardado; si falla el parseo devuelve un objeto vacio
  function getUser()  { try { return JSON.parse(localStorage.getItem(USER_KEY)) || {}; } catch (e) { return {}; } }
  // Indica si hay sesion iniciada (true si existe token)
  function isLoggedIn() { return !!getToken(); }
  // Cierra sesion borrando el token y los datos del usuario del navegador
  function logout() { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY); }

  // -------- Helper: tomar el primer campo que exista --------
  // Recorre varias posibles claves y devuelve el valor de la primera que tenga dato
  function pick(obj, ...keys) {
    // Si el objeto no existe, no hay nada que devolver
    if (!obj) return undefined;
    // Revisa cada clave candidata en orden
    for (const k of keys) {
      // Si la clave tiene un valor valido (ni undefined ni null), la devuelve
      if (obj[k] !== undefined && obj[k] !== null) return obj[k];
    }
    // Si ninguna clave tenia valor, devuelve undefined
    return undefined;
  }

  // -------- fetch() real con manejo de errores --------
  // Hace una peticion HTTP real al backend; async permite usar await dentro
  async function request(method, path, body, auth) {
    // Indica que el cuerpo de la peticion va en formato JSON
    const headers = { "Content-Type": "application/json" };
    // Si la peticion requiere autenticacion, adjunta el token JWT
    if (auth) {
      // Recupera el token guardado
      const t = getToken();
      // Si hay token, lo agrega en la cabecera Authorization con el prefijo "Bearer "
      if (t) headers["Authorization"] = "Bearer " + t;
    }
    // Variable donde se guardara la respuesta del servidor
    let res;
    // Intenta hacer la peticion; el try/catch atrapa errores de red
    try {
      // Llama al servidor con la URL base + la ruta, el metodo, las cabeceras y el cuerpo
      res = await fetch(CFG.API_BASE_URL + path, {
        method: method,
        headers: headers,
        body: body ? JSON.stringify(body) : undefined // Convierte el cuerpo a JSON solo si existe
      });
    } catch (netErr) {
      // Si falla la conexion (servidor caido o certificado no aceptado), crea un error explicativo
      const e = new Error(
        "No se pudo contactar al servidor (" + CFG.API_BASE_URL + "). " +
        "Si usas certificado autofirmado, abre esa URL en el navegador y acepta la advertencia."
      );
      // Marca el error con status 0 (sin respuesta del servidor)
      e.status = 0;
      // Lanza el error para que lo maneje quien llamo a esta funcion
      throw e;
    }

    // Si el servidor responde 401, la sesion no es valida
    if (res.status === 401) {
      // Borra la sesion local
      logout();
      // Crea un error indicando que la sesion expiro o las credenciales son invalidas
      const e = new Error("Sesión expirada o credenciales inválidas.");
      e.status = 401;
      throw e;
    }
    // Si la respuesta no fue exitosa (codigo distinto de 2xx)
    if (!res.ok) {
      // Mensaje de error por defecto con el codigo de estado
      let msg = "Error " + res.status;
      // Intenta leer un mensaje mas claro del cuerpo de la respuesta
      try { const j = await res.json(); msg = j.message || j.error || msg; } catch (e) {}
      // Crea el error con el mensaje obtenido
      const err = new Error(msg);
      // Guarda el codigo de estado en el error
      err.status = res.status;
      throw err;
    }
    // Si la respuesta es 204 (sin contenido), no hay nada que devolver
    if (res.status === 204) return null;
    // Lee el cuerpo de la respuesta como texto
    const text = await res.text();
    // Si hay texto lo convierte a objeto JSON; si no, devuelve null
    return text ? JSON.parse(text) : null;
  }

  // -------- Normalizadores (camelCase | snake_case) --------
  // Convierte un sensor del backend a un formato uniforme que usa el frontend
  function normSensor(s) {
    return {
      id: pick(s, "id"),                                               // Identificador del sensor
      nombre: pick(s, "nombre", "name"),                               // Nombre del sensor
      tipo: pick(s, "tipo", "type"),                                   // Tipo (temperatura, humedad, ph...)
      zona: pick(s, "zona", "zone"),                                   // Zona del invernadero
      unidad: pick(s, "unidad", "unit"),                               // Unidad de medida (C, %, pH...)
      minEsperado: pick(s, "valorMinEsperado", "valor_min_esperado"),  // Valor minimo esperado
      maxEsperado: pick(s, "valorMaxEsperado", "valor_max_esperado"),  // Valor maximo esperado
      activo: pick(s, "activo", "active")                              // Si el sensor esta activo
    };
  }
  // Convierte una lectura del backend a un formato uniforme
  function normLectura(l) {
    return {
      id: pick(l, "id"),                          // Identificador de la lectura
      sensorId: pick(l, "sensorId", "sensor_id"), // A que sensor pertenece
      valor: pick(l, "valor", "value"),           // Valor medido
      timestamp: pick(l, "timestampLectura", "timestamp_lectura", "timestamp", "fecha") // Fecha/hora de la lectura
    };
  }
  // Convierte una alerta del backend a un formato uniforme
  function normAlerta(a) {
    return {
      id: pick(a, "id"),                                              // Identificador de la alerta
      sensorId: pick(a, "sensorId", "sensor_id"),                     // Sensor que origino la alerta
      tipo: pick(a, "tipoAlerta", "tipo_alerta", "tipo"),             // Tipo de alerta
      mensaje: pick(a, "mensaje", "message"),                         // Texto descriptivo de la alerta
      valor: pick(a, "valorDetectado", "valor_detectado", "valor"),   // Valor que disparo la alerta
      severidad: pick(a, "severidad", "severity"),                    // Gravedad (BAJA, MEDIA, ALTA, CRITICA)
      fecha: pick(a, "fechaGeneracion", "fecha_generacion", "fecha"), // Fecha en que se genero
      resuelta: !!pick(a, "resuelta", "resolved"),                    // Si ya fue resuelta (true/false)
      fechaResolucion: pick(a, "fechaResolucion", "fecha_resolucion") // Fecha en que se resolvio
    };
  }
  // Asegura que el dato sea un arreglo: si es lista lo deja, si trae .content lo extrae, si es uno lo envuelve
  function arr(x) { return Array.isArray(x) ? x : (x && Array.isArray(x.content) ? x.content : (x ? [x] : [])); }

  // Funcion corta que indica si el modo DEMO esta activo
  const DEMO = () => CFG.DEMO_MODE === true;

  // ============================================================
  //  API pública usada por la app
  // ============================================================
  // Devuelve el objeto con las funciones publicas que usara app.js
  return {
    // Expone utilidades de sesion y un indicador del modo demo
    isLoggedIn, getUser, logout, isDemo: DEMO,

    // Inicia sesion con usuario y contrasena
    async login(username, password) {
      // En modo demo valida contra el backend simulado
      if (DEMO()) {
        // Pide al mock que valide las credenciales
        const data = window.MockBackend.login(username, password);
        // Guarda la sesion devuelta
        setSession(data);
        // Devuelve los datos del usuario
        return getUser();
      }
      // En modo real envia las credenciales al endpoint de login (sin token, auth=false)
      const data = await request("POST", "/auth/login", { username, password }, false);
      // Si no llega un token, lanza error
      if (!data || !data.token) throw new Error("El servidor no devolvió un token.");
      // Guarda la sesion con el token recibido
      setSession(data);
      // Devuelve los datos del usuario
      return getUser();
    },

    // Obtiene la lista de sensores (del mock o del backend real)
    async getSensores() {
      // Elige la fuente segun el modo demo
      const data = DEMO() ? window.MockBackend.sensores()
                          : await request("GET", "/sensores", null, true);
      // Normaliza cada sensor a formato uniforme y devuelve el arreglo
      return arr(data).map(normSensor);
    },

    // Obtiene la ultima lectura de cada sensor
    async getLecturasRecientes() {
      // Elige la fuente segun el modo demo
      const data = DEMO() ? window.MockBackend.lecturasRecientes()
                          : await request("GET", "/sensores/lecturas", null, true);
      // Normaliza las lecturas y las devuelve
      return arr(data).map(normLectura);
    },

    // Obtiene el historial de lecturas de un sensor concreto
    async getLecturasSensor(sensorId, limit) {
      // Usa el limite recibido o el valor por defecto de la configuracion
      const n = limit || CFG.HISTORY_POINTS;
      // Elige la fuente segun el modo demo, pidiendo n lecturas de ese sensor
      const data = DEMO() ? window.MockBackend.lecturasPorSensor(sensorId, n)
                          : await request("GET", "/sensores/" + sensorId + "/lecturas?limit=" + n, null, true);
      // Normaliza las lecturas y las devuelve
      return arr(data).map(normLectura);
    },

    // Obtiene las alertas; soloActivas filtra solo las no resueltas
    async getAlertas(soloActivas) {
      // Variable donde se guardaran los datos crudos
      let data;
      // En modo demo pide las alertas al mock
      if (DEMO()) {
        data = window.MockBackend.alertas(soloActivas);
      } else {
        // En modo real arma el parametro de filtro si se piden solo activas
        const q = soloActivas ? "?resuelta=false" : "";
        // Pide las alertas al backend
        data = await request("GET", "/alertas" + q, null, true);
      }
      // Normaliza las alertas y las devuelve
      return arr(data).map(normAlerta);
    },

    // Marca una alerta como resuelta por su id
    async resolverAlerta(id) {
      // En modo demo le pide al mock que la resuelva y normaliza el resultado
      if (DEMO()) return normAlerta(window.MockBackend.resolver(id));
      // En modo real envia la peticion de resolver al backend
      const data = await request("POST", "/alertas/" + id + "/resolver", null, true);
      // Devuelve la alerta normalizada o, si no hay cuerpo, una marca minima de resuelta
      return data ? normAlerta(data) : { id, resuelta: true };
    }
  };
})();
