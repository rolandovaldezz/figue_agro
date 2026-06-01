/* ============================================================
 * AgroSmart - Backend simulado (solo para DEMO_MODE = true)
 * ============================================================
 * Reproduce en memoria el comportamiento esperado del backend:
 * usuarios, sensores, lecturas que cambian cada 5s y alertas que
 * se generan al violar los umbrales. Espeja los datos de db/init.sql.
 *
 * Cuando DEMO_MODE = false, este archivo NO se usa: las peticiones
 * van directo al API Gateway real.
 * ============================================================ */

// Crea el objeto global MockBackend con una funcion autoejecutable (IIFE) que oculta lo interno
window.MockBackend = (function () {

  // ---- Usuarios (mismos que db/init.sql) ----
  // Diccionario de usuarios validos para el login simulado
  const usuarios = {
    admin:      { password: "admin123", rol: "ADMIN",      nombre_completo: "Administrador del Sistema" }, // Usuario administrador
    agricultor: { password: "admin123", rol: "AGRICULTOR", nombre_completo: "Juan Pérez" }                 // Usuario agricultor
  };

  // ---- Sensores (mismos que db/init.sql) ----
  // Lista de sensores simulados con su tipo, zona, unidad y rango esperado
  const sensores = [
    { id: 1, nombre: "Sensor de Temperatura Norte", tipo: "temperatura", zona: "zona1", unidad: "C",  valor_min_esperado: 18.0, valor_max_esperado: 30.0, activo: true }, // Temperatura zona1
    { id: 2, nombre: "Sensor de Humedad Norte",     tipo: "humedad",     zona: "zona1", unidad: "%",  valor_min_esperado: 40.0, valor_max_esperado: 80.0, activo: true }, // Humedad zona1
    { id: 3, nombre: "Sensor de pH Norte",          tipo: "ph",          zona: "zona1", unidad: "pH", valor_min_esperado: 5.5,  valor_max_esperado: 7.5,  activo: true }, // pH zona1
    { id: 4, nombre: "Sensor de Temperatura Sur",   tipo: "temperatura", zona: "zona2", unidad: "C",  valor_min_esperado: 18.0, valor_max_esperado: 30.0, activo: true }, // Temperatura zona2
    { id: 5, nombre: "Sensor de Humedad Sur",       tipo: "humedad",     zona: "zona2", unidad: "%",  valor_min_esperado: 40.0, valor_max_esperado: 80.0, activo: true }  // Humedad zona2
  ];

  // ---- Umbrales (mismos que db/init.sql) ----
  // Reglas que definen cuando una lectura genera una alerta (con su severidad y plantilla de mensaje)
  const umbrales = [
    { id: 1, tipo_sensor: "temperatura", valor_min: null, valor_max: 35.0, severidad: "ALTA",    tpl: "Temperatura demasiado alta en {zona}: {valor}°C" },         // Temp alta
    { id: 2, tipo_sensor: "temperatura", valor_min: 10.0, valor_max: null, severidad: "ALTA",    tpl: "Temperatura demasiado baja en {zona}: {valor}°C" },        // Temp baja
    { id: 3, tipo_sensor: "temperatura", valor_min: null, valor_max: 40.0, severidad: "CRITICA", tpl: "TEMPERATURA CRÍTICA en {zona}: {valor}°C - Acción inmediata" }, // Temp critica
    { id: 4, tipo_sensor: "humedad",     valor_min: 30.0, valor_max: null, severidad: "MEDIA",   tpl: "Humedad baja en {zona}: {valor}%" },                        // Humedad baja
    { id: 5, tipo_sensor: "humedad",     valor_min: null, valor_max: 90.0, severidad: "MEDIA",   tpl: "Humedad excesiva en {zona}: {valor}%" },                    // Humedad excesiva
    { id: 6, tipo_sensor: "ph",          valor_min: 5.0,  valor_max: null, severidad: "ALTA",    tpl: "pH demasiado ácido en {zona}: {valor}" },                   // pH acido
    { id: 7, tipo_sensor: "ph",          valor_min: null, valor_max: 8.0,  severidad: "ALTA",    tpl: "pH demasiado alcalino en {zona}: {valor}" }                 // pH alcalino
  ];

  // Estado vivo
  // Almacen del historial de lecturas por sensor: clave = sensorId, valor = arreglo de lecturas
  const historial = {};   // sensorId -> [lecturas]
  // Lista de alertas generadas
  const alertas = [];
  // Contador para asignar un id unico a cada lectura
  let lecturaSeq = 0;
  // Contador para asignar un id unico a cada alerta
  let alertaSeq = 0;

  // Devuelve el rango de simulacion (min, max) segun el tipo de sensor
  function rango(tipo) {
    // Rango de simulación: a veces fuera de lo esperado para generar alertas
    switch (tipo) {
      case "temperatura": return [12, 42];   // Rango simulado de temperatura
      case "humedad":     return [25, 95];   // Rango simulado de humedad
      case "ph":          return [4.5, 8.5]; // Rango simulado de pH
      default:            return [0, 100];   // Rango por defecto
    }
  }

  // Redondea un valor: 2 decimales para pH, 1 decimal para el resto
  function redondea(tipo, v) {
    return tipo === "ph" ? Math.round(v * 100) / 100 : Math.round(v * 10) / 10;
  }

  // Genera una lectura simulada para un sensor en un instante de tiempo t
  function nuevaLectura(sensor, t) {
    // Obtiene el rango de simulacion del tipo de sensor
    const [min, max] = rango(sensor.tipo);
    // 80% del tiempo dentro de lo esperado, 20% fuera (para ver alertas)
    // Variable donde se calculara el valor
    let valor;
    // El 80% de las veces genera un valor dentro del rango esperado
    if (Math.random() < 0.8) {
      valor = sensor.valor_min_esperado + Math.random() * (sensor.valor_max_esperado - sensor.valor_min_esperado);
    } else {
      // El 20% restante genera un valor en el rango amplio (puede salirse y disparar alertas)
      valor = min + Math.random() * (max - min);
    }
    // Redondea el valor segun el tipo
    valor = redondea(sensor.tipo, valor);
    // Devuelve la lectura con un id nuevo, el sensor, el valor y la fecha en formato ISO
    return {
      id: ++lecturaSeq,
      sensor_id: sensor.id,
      valor: valor,
      timestamp_lectura: new Date(t).toISOString()
    };
  }

  // Revisa una lectura contra los umbrales y genera alertas si las viola
  function evaluaAlertas(sensor, lectura) {
    // Toma solo los umbrales del tipo de sensor de esta lectura
    umbrales
      .filter(u => u.tipo_sensor === sensor.tipo)
      .forEach(u => {
        // Comprueba si la lectura esta por debajo del minimo del umbral
        const bajo = u.valor_min != null && lectura.valor < u.valor_min;
        // Comprueba si la lectura esta por encima del maximo del umbral
        const alto = u.valor_max != null && lectura.valor > u.valor_max;
        // Si se viola el umbral (por arriba o por abajo), genera una alerta
        if (bajo || alto) {
          // Arma el mensaje sustituyendo la zona y el valor en la plantilla
          const mensaje = u.tpl
            .replace("{zona}", sensor.zona)
            .replace("{valor}", lectura.valor);
          // Inserta la alerta al inicio de la lista (la mas reciente primero)
          alertas.unshift({
            id: ++alertaSeq,                            // Id unico de la alerta
            sensor_id: sensor.id,                       // Sensor que la origino
            lectura_id: lectura.id,                     // Lectura que la disparo
            umbral_id: u.id,                            // Umbral violado
            tipo_alerta: sensor.tipo,                   // Tipo de alerta
            mensaje: mensaje,                           // Mensaje generado
            valor_detectado: lectura.valor,             // Valor que disparo la alerta
            severidad: u.severidad,                     // Severidad del umbral
            fecha_generacion: lectura.timestamp_lectura, // Fecha de la lectura
            resuelta: false,                            // Empieza sin resolver
            fecha_resolucion: null                      // Sin fecha de resolucion aun
          });
        }
      });
    // Evita que la lista crezca infinitamente en la demo
    // Recorta la lista de alertas a un maximo de 200 para no consumir memoria sin limite
    if (alertas.length > 200) alertas.length = 200;
  }

  // Genera historial inicial (últimos N puntos, uno cada 5s hacia atrás)
  // Crea datos historicos iniciales para que la app tenga algo que mostrar al arrancar
  function sembrar() {
    // Momento actual en milisegundos
    const ahora = Date.now();
    // Cantidad de puntos historicos a generar
    const N = 30;
    // Inicializa el historial vacio de cada sensor
    sensores.forEach(s => { historial[s.id] = []; });
    // Recorre desde N-1 hacia atras para crear lecturas separadas por 5 segundos
    for (let i = N - 1; i >= 0; i--) {
      // Calcula el instante de esta lectura (hacia el pasado)
      const t = ahora - i * 5000;
      // Para cada sensor genera una lectura en ese instante
      sensores.forEach(s => {
        // Crea la lectura
        const l = nuevaLectura(s, t);
        // La agrega al historial del sensor
        historial[s.id].push(l);
        // Evalua si genera alguna alerta
        evaluaAlertas(s, l);
      });
    }
  }
  // Ejecuta la siembra inicial de datos
  sembrar();

  // Cada 5s agrega una lectura nueva por sensor (simula el sensor-service)
  // Temporizador que cada 5 segundos genera nuevas lecturas (imita al servicio real de sensores)
  setInterval(function () {
    // Instante actual
    const t = Date.now();
    // Para cada sensor crea y registra una nueva lectura
    sensores.forEach(s => {
      // Genera la lectura
      const l = nuevaLectura(s, t);
      // La agrega al historial
      historial[s.id].push(l);
      // Limita el historial a 500 lecturas eliminando la mas antigua si se pasa
      if (historial[s.id].length > 500) historial[s.id].shift();
      // Evalua si la lectura genera alguna alerta
      evaluaAlertas(s, l);
    });
  }, 5000);

  // ---- API pública (la consume api.js cuando DEMO_MODE = true) ----
  // Devuelve las funciones que api.js usa como si fueran el backend real
  return {
    // Valida usuario y contrasena; devuelve un token simulado o lanza error 401
    login: function (username, password) {
      // Busca el usuario en el diccionario
      const u = usuarios[username];
      // Si no existe o la contrasena no coincide, lanza error de credenciales
      if (!u || u.password !== password) {
        const err = new Error("Usuario o contraseña incorrectos");
        err.status = 401;
        throw err;
      }
      // Devuelve un token falso (firmado de mentira) y los datos del usuario
      return {
        token: "demo-token." + btoa(username) + ".firmado", // Token simulado con el usuario codificado en base64
        username: username,
        rol: u.rol,
        nombre_completo: u.nombre_completo
      };
    },

    // Devuelve una copia de la lista de sensores
    sensores: function () {
      // Object.assign crea una copia para no exponer los objetos internos
      return sensores.map(s => Object.assign({}, s));
    },

    // Devuelve la ultima lectura de cada sensor
    lecturasRecientes: function () {
      // Última lectura de cada sensor
      return sensores.map(s => {
        // Historial del sensor
        const h = historial[s.id];
        // Devuelve una copia de la ultima lectura del historial
        return Object.assign({}, h[h.length - 1]);
      });
    },

    // Devuelve las ultimas "limit" lecturas de un sensor concreto
    lecturasPorSensor: function (sensorId, limit) {
      // Historial del sensor (o arreglo vacio si no existe)
      const h = historial[sensorId] || [];
      // Numero de lecturas a devolver (30 por defecto)
      const n = limit || 30;
      // Toma las ultimas n lecturas y devuelve copias de cada una
      return h.slice(-n).map(l => Object.assign({}, l));
    },

    // Devuelve las alertas; si soloActivas es true, filtra las no resueltas
    alertas: function (soloActivas) {
      // Filtra solo las activas o toma todas
      const lista = soloActivas ? alertas.filter(a => !a.resuelta) : alertas;
      // Devuelve copias para no exponer los objetos internos
      return lista.map(a => Object.assign({}, a));
    },

    // Marca una alerta como resuelta por su id
    resolver: function (id) {
      // Busca la alerta por id (convierte a numero por si llega como texto)
      const a = alertas.find(x => x.id === Number(id));
      // Si no se encuentra, lanza error 404
      if (!a) { const e = new Error("Alerta no encontrada"); e.status = 404; throw e; }
      // La marca como resuelta
      a.resuelta = true;
      // Registra la fecha de resolucion (ahora)
      a.fecha_resolucion = new Date().toISOString();
      // Devuelve una copia de la alerta resuelta
      return Object.assign({}, a);
    }
  };
})();
