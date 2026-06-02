/* ============================================================
 * AgroSmart - Lógica de la interfaz (SPA)
 * ============================================================
 * Orquesta login, navegación entre secciones y el render de
 * Dashboard, Sensores y Alertas usando la capa Api.
 * ============================================================ */

// Funcion autoejecutable (IIFE) que encapsula toda la logica para no ensuciar el espacio global
(function () {
  // Atajo a la configuracion global definida en config.js
  const CFG = window.AGROSMART_CONFIG;
  // Atajo para seleccionar UN elemento del HTML por selector CSS
  const $ = (sel) => document.querySelector(sel);
  // Atajo para seleccionar VARIOS elementos y obtenerlos como arreglo
  const $$ = (sel) => Array.from(document.querySelectorAll(sel));

  // Cache de sensores (para cruzar lecturas/alertas con su nombre)
  // Arreglo con todos los sensores ya cargados (evita pedirlos repetidamente)
  let sensoresCache = [];
  // Diccionario sensorId -> sensor para buscar un sensor por su id rapidamente
  let sensoresById = {};
  // Referencia al temporizador del auto-refresh (para poder detenerlo)
  let refreshTimer = null;
  // Referencia a la grafica actual de Chart.js (para destruirla antes de crear otra)
  let historyChart = null;
  // Seccion que se esta mostrando actualmente (dashboard, sensores o alertas)
  let currentPage = "dashboard";
  // Cache de las alertas mostradas actualmente (para buscar una por id al mandarla por correo)
  let alertasActuales = [];

  // ---------- Utilidades ----------
  // Muestra un aviso emergente (toast) con un mensaje y un color segun el tipo
  function toast(msg, tipo) {
    // Obtiene el elemento del toast
    const el = $("#toast");
    // Ajusta las clases del toast incluyendo el color de fondo (success, danger, dark...)
    el.className = "toast align-items-center text-white border-0 bg-" + (tipo || "dark");
    // Escribe el mensaje en el cuerpo del toast
    $("#toastBody").textContent = msg;
    // Crea (o reutiliza) la instancia de Bootstrap del toast y lo muestra durante 3.5 segundos
    bootstrap.Toast.getOrCreateInstance(el, { delay: 3500 }).show();
  }

  // Formatea una fecha ISO a un texto legible con dia, mes y hora completa
  function fmtFecha(iso) {
    // Si no hay fecha, muestra un guion
    if (!iso) return "—";
    // Convierte el texto a un objeto Date
    const d = new Date(iso);
    // Si la fecha no es valida, devuelve el texto original
    if (isNaN(d)) return iso;
    // Devuelve la fecha formateada al estilo de Mexico (es-MX)
    return d.toLocaleString("es-MX", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit" });
  }

  // Formatea una fecha ISO mostrando solo la hora (para las etiquetas de la grafica y "ultima actualizacion")
  function fmtHora(iso) {
    // Convierte el texto a un objeto Date
    const d = new Date(iso);
    // Si la fecha no es valida, devuelve cadena vacia
    if (isNaN(d)) return "";
    // Devuelve solo la hora con horas, minutos y segundos
    return d.toLocaleTimeString("es-MX", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  }

  // Devuelve la clase de icono adecuada segun el tipo de sensor
  function iconoTipo(tipo) {
    // Termometro para temperatura
    if (tipo === "temperatura") return "bi-thermometer-half";
    // Gota para humedad
    if (tipo === "humedad") return "bi-droplet-half";
    // Gotero para pH
    if (tipo === "ph") return "bi-eyedropper";
    // Icono generico para cualquier otro tipo
    return "bi-activity";
  }

  // Determina el estado de un sensor (ok, alerta o critico) comparando su valor con el rango esperado
  function estadoSensor(sensor, valor) {
    // Sin valor no se puede evaluar; se considera ok
    if (valor == null) return "ok";
    // Toma los limites minimo y maximo esperados del sensor
    const min = sensor.minEsperado, max = sensor.maxEsperado;
    // Si el valor esta por debajo del minimo o por encima del maximo, esta fuera de rango
    if ((min != null && valor < min) || (max != null && valor > max)) {
      // ¿qué tan fuera? margen del 15% => crítico
      // Toma como referencia el limite superior (o el inferior si no hay superior)
      const ref = max != null ? max : min;
      // Calcula un margen del 15% sobre esa referencia
      const margen = Math.abs(ref) * 0.15;
      // Si supera el limite por mas del margen, se considera critico
      if ((max != null && valor > max + margen) || (min != null && valor < min - margen)) return "critico";
      // Si esta fuera pero dentro del margen, es solo alerta
      return "alerta";
    }
    // Si esta dentro del rango, esta ok
    return "ok";
  }

  // ============================================================
  //  LOGIN
  // ============================================================
  // Muestra la pantalla de login y oculta la app
  function showLogin() {
    // Activa la vista de login
    $("#view-login").classList.add("active");
    // Desactiva la vista de la app
    $("#view-app").classList.remove("active");
    // Detiene el auto-refresh porque ya no estamos en el dashboard
    stopRefresh();
  }

  // Muestra la app principal y oculta el login (tras iniciar sesion)
  function showApp() {
    // Desactiva la vista de login
    $("#view-login").classList.remove("active");
    // Activa la vista de la app
    $("#view-app").classList.add("active");
    // Obtiene los datos del usuario logueado
    const u = Api.getUser();
    // Muestra el nombre de usuario en la barra de navegacion
    $("#navUser").textContent = u.username || "usuario";
    // Muestra el rol del usuario en el menu
    $("#navRol").textContent = u.rol || "—";
    // Navega a la seccion indicada en la URL (#...) o al dashboard por defecto
    navegar(location.hash.replace("#", "") || "dashboard");
  }

  // Escucha el envio del formulario de login
  $("#loginForm").addEventListener("submit", async (e) => {
    // Evita que el formulario recargue la pagina
    e.preventDefault();
    // Referencias al boton, la rueda de carga y la caja de error
    const btn = $("#loginBtn"), sp = $("#loginSpinner"), err = $("#loginError");
    // Oculta cualquier error previo
    err.classList.add("d-none");
    // Deshabilita el boton y muestra la rueda de carga mientras se procesa
    btn.disabled = true; sp.classList.remove("d-none");
    // Intenta iniciar sesion
    try {
      // Llama al login con el usuario y la contrasena escritos (recorta espacios del usuario)
      await Api.login($("#username").value.trim(), $("#password").value);
      // Si funciona, muestra la app
      showApp();
    } catch (ex) {
      // Si falla, muestra el mensaje de error
      err.textContent = ex.message || "No se pudo iniciar sesión.";
      err.classList.remove("d-none");
    } finally {
      // Pase lo que pase, reactiva el boton y oculta la rueda de carga
      btn.disabled = false; sp.classList.add("d-none");
    }
  });

  // Escucha el clic en "Cerrar sesion"
  $("#logoutBtn").addEventListener("click", (e) => {
    // Evita que el enlace navegue
    e.preventDefault();
    // Borra la sesion guardada
    Api.logout();
    // Vuelve a la pantalla de login
    showLogin();
    // Avisa al usuario que se cerro la sesion
    toast("Sesión cerrada");
  });

  // ============================================================
  //  NAVEGACIÓN (hash router)
  // ============================================================
  // Cambia entre secciones segun el nombre de pagina recibido
  function navegar(page) {
    // Si la pagina no es valida, vuelve al dashboard
    if (!["dashboard", "sensores", "alertas", "usuarios"].includes(page)) page = "dashboard";
    // Recuerda la pagina actual
    currentPage = page;
    // Oculta todas las secciones
    $$(".page").forEach(s => s.style.display = "none");
    // Muestra solo la seccion seleccionada
    $("#page-" + page).style.display = "block";
    // Marca como activo el enlace del menu correspondiente
    $$("[data-nav]").forEach(a => a.classList.toggle("active", a.dataset.nav === page));

    // Carga los datos de la seccion correspondiente
    if (page === "dashboard") cargarDashboard();
    if (page === "sensores")  cargarSensores();
    if (page === "alertas")   cargarAlertas();
    if (page === "usuarios")  cargarUsuarios();
  }

  // Cuando cambia la parte # de la URL, vuelve a navegar a la seccion indicada
  window.addEventListener("hashchange", () => navegar(location.hash.replace("#", "")));

  // ============================================================
  //  AUTO-REFRESH (solo dashboard)
  // ============================================================
  // Inicia el temporizador que refresca el dashboard cada cierto tiempo
  function startRefresh() {
    // Primero detiene cualquier temporizador anterior para no duplicarlos
    stopRefresh();
    // Crea un intervalo que se repite cada REFRESH_INTERVAL_MS milisegundos
    refreshTimer = setInterval(() => {
      // Solo recarga el dashboard si es la seccion visible (true = recarga silenciosa)
      if (currentPage === "dashboard") cargarDashboard(true);
    }, CFG.REFRESH_INTERVAL_MS);
  }
  // Detiene el temporizador de auto-refresh si existe
  function stopRefresh() {
    // Si hay un temporizador activo lo cancela y limpia la referencia
    if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null; }
  }

  // ============================================================
  //  Asegura el catálogo de sensores en cache
  // ============================================================
  // Carga los sensores solo una vez y los guarda en cache para reutilizarlos
  async function ensureSensores() {
    // Si ya estan en cache, los devuelve sin volver a pedirlos
    if (sensoresCache.length) return sensoresCache;
    // Pide los sensores al API y los guarda en cache
    sensoresCache = await Api.getSensores();
    // Reinicia el diccionario por id
    sensoresById = {};
    // Llena el diccionario para buscar cada sensor por su id
    sensoresCache.forEach(s => sensoresById[s.id] = s);
    // Devuelve la lista de sensores
    return sensoresCache;
  }

  // ============================================================
  //  DASHBOARD
  // ============================================================
  // Carga y dibuja el dashboard; silencioso indica si es una recarga automatica
  async function cargarDashboard(silencioso) {
    try {
      // Asegura que el catalogo de sensores este cargado
      await ensureSensores();
      // Pide en paralelo las lecturas recientes y las alertas activas
      const [lecturas, alertasActivas] = await Promise.all([
        Api.getLecturasRecientes(),
        Api.getAlertas(true)
      ]);

      // Diccionario sensorId -> ultima lectura para acceso rapido
      const lecturaPorSensor = {};
      // Llena el diccionario con la lectura de cada sensor
      lecturas.forEach(l => { lecturaPorSensor[l.sensorId] = l; });

      // Stats
      // Filtra los sensores que estan activos
      const activos = sensoresCache.filter(s => s.activo !== false);
      // Contador de sensores fuera de rango
      let fueraRango = 0;
      // Recorre los sensores activos para contar cuantos estan fuera de rango
      activos.forEach(s => {
        // Toma la lectura del sensor
        const l = lecturaPorSensor[s.id];
        // Si hay lectura y su estado no es "ok", suma uno al contador
        if (l && estadoSensor(s, l.valor) !== "ok") fueraRango++;
      });
      // Conjunto de zonas distintas (Set elimina duplicados)
      const zonas = new Set(sensoresCache.map(s => s.zona));

      // Actualiza la tarjeta de sensores activos
      $("#statSensores").textContent = activos.length;
      // Actualiza la tarjeta de alertas activas
      $("#statAlertas").textContent = alertasActivas.length;
      // Actualiza la tarjeta de fuera de rango
      $("#statFueraRango").textContent = fueraRango;
      // Actualiza la tarjeta de zonas monitoreadas
      $("#statZonas").textContent = zonas.size;
      // Muestra la hora de esta actualizacion
      $("#lastUpdate").textContent = fmtHora(new Date().toISOString());
      // Actualiza el numero en el badge de alertas del menu
      actualizarBadgeAlertas(alertasActivas.length);

      // Tarjetas por sensor
      // Contenedor donde van las tarjetas de sensores
      const cont = $("#sensorCards");
      // Limpia las tarjetas anteriores
      cont.innerHTML = "";
      // Crea una tarjeta por cada sensor activo
      activos.forEach(s => {
        // Lectura actual del sensor
        const l = lecturaPorSensor[s.id];
        // Valor medido (o null si no hay lectura)
        const valor = l ? l.valor : null;
        // Estado del sensor segun su valor
        const est = estadoSensor(s, valor);
        // Clase CSS de color del borde segun el estado
        const clase = est === "ok" ? "estado-ok" : (est === "critico" ? "estado-critico" : "estado-alerta");
        // Etiqueta (badge) de texto y color segun el estado
        const badge = est === "ok"
          ? '<span class="badge bg-success">Normal</span>'
          : (est === "critico" ? '<span class="badge bg-danger">Crítico</span>' : '<span class="badge bg-warning text-dark">Atención</span>');

        // Crea la columna que contendra la tarjeta
        const col = document.createElement("div");
        // Define el ancho responsive de la columna
        col.className = "col-12 col-sm-6 col-lg-4 col-xl-3";
        // Construye el HTML interno de la tarjeta del sensor
        col.innerHTML =
          '<div class="card sensor-card ' + clase + ' h-100">' +
            '<div class="card-body">' +
              '<div class="d-flex justify-content-between align-items-start mb-2">' +
                '<div class="text-muted small"><i class="bi ' + iconoTipo(s.tipo) + '"></i> ' + s.zona + '</div>' +
                badge +
              '</div>' +
              '<div class="fw-semibold mb-1">' + (s.nombre || s.tipo) + '</div>' +
              '<div><span class="sensor-value">' + (valor != null ? valor : "—") + '</span> <span class="sensor-unit">' + (s.unidad || "") + '</span></div>' +
              '<div class="text-muted small mt-2">Esperado: ' + fmtRango(s) + '</div>' +
              '<div class="text-muted small">' + (l ? fmtHora(l.timestamp) : "") + '</div>' +
            '</div>' +
          '</div>';
        // Agrega la tarjeta al contenedor
        cont.appendChild(col);
      });

      // Si no es una recarga silenciosa, arranca el auto-refresh
      if (!silencioso) startRefresh();
    } catch (ex) {
      // Si algo falla, maneja el error de forma comun
      manejarError(ex);
    }
  }

  // Da formato al texto del rango esperado de un sensor (minimo - maximo unidad)
  function fmtRango(s) {
    // Muestra el minimo o el simbolo de menos infinito si no hay
    const min = s.minEsperado != null ? s.minEsperado : "−∞";
    // Muestra el maximo o el simbolo de infinito si no hay
    const max = s.maxEsperado != null ? s.maxEsperado : "∞";
    // Une minimo, maximo y unidad en una sola cadena
    return min + " – " + max + " " + (s.unidad || "");
  }

  // Actualiza el badge rojo de alertas del menu con el numero n
  function actualizarBadgeAlertas(n) {
    // Referencia al badge
    const b = $("#navAlertCount");
    // Escribe el numero de alertas
    b.textContent = n;
    // Oculta el badge si no hay alertas (n = 0), lo muestra si hay
    b.classList.toggle("d-none", !n);
  }

  // ============================================================
  //  SENSORES
  // ============================================================
  // Carga y dibuja la tabla de sensores
  async function cargarSensores() {
    try {
      // Asegura que el catalogo de sensores este cargado
      await ensureSensores();
      // Pide las lecturas recientes de todos los sensores
      const lecturas = await Api.getLecturasRecientes();
      // Diccionario sensorId -> ultima lectura
      const lpor = {};
      // Llena el diccionario con cada lectura
      lecturas.forEach(l => lpor[l.sensorId] = l);

      // Referencia al cuerpo de la tabla de sensores
      const tb = $("#sensoresTable");
      // Limpia las filas anteriores
      tb.innerHTML = "";
      // Crea una fila por cada sensor
      sensoresCache.forEach(s => {
        // Lectura actual del sensor
        const l = lpor[s.id];
        // Valor medido (o null)
        const valor = l ? l.valor : null;
        // Estado del sensor segun su valor
        const est = estadoSensor(s, valor);
        // Etiqueta (badge) de estado con su color
        const estTxt = est === "ok"
          ? '<span class="badge bg-success">Normal</span>'
          : (est === "critico" ? '<span class="badge bg-danger">Crítico</span>' : '<span class="badge bg-warning text-dark">Atención</span>');
        // Crea la fila de la tabla
        const tr = document.createElement("tr");
        // Construye las celdas de la fila con los datos del sensor y un boton de historial
        tr.innerHTML =
          "<td>" + s.id + "</td>" +
          "<td>" + (s.nombre || "") + "</td>" +
          '<td><i class="bi ' + iconoTipo(s.tipo) + '"></i> ' + (s.tipo || "") + "</td>" +
          "<td>" + (s.zona || "") + "</td>" +
          "<td>" + (s.unidad || "") + "</td>" +
          "<td>" + fmtRango(s) + "</td>" +
          "<td><strong>" + (valor != null ? valor : "—") + "</strong></td>" +
          "<td>" + estTxt + "</td>" +
          '<td class="text-nowrap">' +
            '<button class="btn btn-sm btn-outline-success" data-sensor="' + s.id + '" title="Historial"><i class="bi bi-graph-up"></i></button> ' +
            '<button class="btn btn-sm btn-outline-primary" data-editar="' + s.id + '" title="Editar"><i class="bi bi-pencil"></i></button> ' +
            '<button class="btn btn-sm btn-outline-danger" data-eliminar="' + s.id + '" title="Eliminar"><i class="bi bi-trash"></i></button>' +
          '</td>';
        // Agrega la fila a la tabla
        tb.appendChild(tr);
      });

      // Asigna a cada boton "Historial" el evento que abre la grafica del sensor
      $$("#sensoresTable [data-sensor]").forEach(btn => {
        // Al hacer clic, muestra el historial del sensor (convierte el id de texto a numero)
        btn.addEventListener("click", () => mostrarHistorial(Number(btn.dataset.sensor)));
      });
      // Asigna a cada boton "Editar" el evento que abre el modal con los datos del sensor
      $$("#sensoresTable [data-editar]").forEach(btn => {
        // Busca el sensor por su id y abre el modal en modo edicion
        btn.addEventListener("click", () => abrirModalSensor(sensoresById[Number(btn.dataset.editar)]));
      });
      // Asigna a cada boton "Eliminar" el evento que pide confirmacion y borra el sensor
      $$("#sensoresTable [data-eliminar]").forEach(btn => {
        // Al hacer clic, ejecuta la baja del sensor
        btn.addEventListener("click", () => eliminarSensorUI(Number(btn.dataset.eliminar), btn));
      });
    } catch (ex) {
      // Si algo falla, maneja el error de forma comun
      manejarError(ex);
    }
  }

  // Muestra el panel con la grafica del historial de un sensor
  async function mostrarHistorial(sensorId) {
    // Busca el sensor en el diccionario por su id
    const s = sensoresById[sensorId];
    try {
      // Pide al API el historial de lecturas del sensor
      const lecturas = await Api.getLecturasSensor(sensorId, CFG.HISTORY_POINTS);
      // Muestra el panel de detalle (le quita la clase que lo oculta)
      $("#sensorDetail").classList.remove("d-none");
      // Pone el titulo del panel con el icono, nombre y unidad del sensor
      $("#detailTitle").innerHTML = '<i class="bi ' + iconoTipo(s.tipo) + '"></i> ' + s.nombre + " (" + s.unidad + ")";
      // Desplaza la vista suavemente hasta el panel
      $("#sensorDetail").scrollIntoView({ behavior: "smooth" });

      // Etiquetas del eje X: la hora de cada lectura
      const labels = lecturas.map(l => fmtHora(l.timestamp));
      // Datos del eje Y: el valor de cada lectura
      const datos = lecturas.map(l => l.valor);

      // Si ya habia una grafica, la destruye para crear una nueva sin solaparlas
      if (historyChart) historyChart.destroy();
      // Obtiene el contexto de dibujo 2D del canvas
      const ctx = $("#historyChart").getContext("2d");
      // Define el conjunto de datos principal (la linea del sensor)
      const ds = [{
        label: s.nombre + " (" + s.unidad + ")", // Texto de la leyenda
        data: datos,                              // Valores a graficar
        borderColor: "#2e7d32",                   // Color de la linea (verde)
        backgroundColor: "rgba(46,125,50,.12)",   // Color del relleno bajo la linea
        fill: true, tension: .3, pointRadius: 2   // Rellena, suaviza la curva y dibuja puntos pequenos
      }];
      // Líneas de referencia (rango esperado)
      // Si hay maximo esperado, agrega una linea de referencia roja
      if (s.maxEsperado != null) ds.push(lineaRef("Máx esperado", s.maxEsperado, "#dc3545", labels.length));
      // Si hay minimo esperado, agrega una linea de referencia naranja
      if (s.minEsperado != null) ds.push(lineaRef("Mín esperado", s.minEsperado, "#fd7e14", labels.length));

      // Crea la grafica de lineas con Chart.js
      historyChart = new Chart(ctx, {
        type: "line",                          // Tipo de grafica: lineas
        data: { labels, datasets: ds },        // Etiquetas y conjuntos de datos
        options: {
          responsive: true,                    // Se adapta al tamano del contenedor
          interaction: { intersect: false, mode: "index" }, // Tooltip al pasar el mouse por el eje X
          plugins: { legend: { position: "bottom" } },      // Leyenda abajo
          scales: { y: { beginAtZero: false } }             // El eje Y no empieza forzosamente en cero
        }
      });
    } catch (ex) {
      // Si algo falla, maneja el error de forma comun
      manejarError(ex);
    }
  }

  // Construye una linea horizontal de referencia (punteada) para la grafica
  function lineaRef(label, valor, color, n) {
    return {
      label: label, data: Array(n).fill(valor),       // Mismo valor repetido n veces (linea recta)
      borderColor: color, borderDash: [6, 4], borderWidth: 1, // Color, patron punteado y grosor fino
      pointRadius: 0, fill: false                      // Sin puntos y sin relleno
    };
  }

  // Al hacer clic en cerrar, oculta el panel de detalle del sensor
  $("#detailClose").addEventListener("click", () => $("#sensorDetail").classList.add("d-none"));

  // ============================================================
  //  ALERTAS
  // ============================================================
  // Carga y dibuja la tabla de alertas
  async function cargarAlertas() {
    try {
      // Asegura que el catalogo de sensores este cargado (para mostrar el nombre del sensor)
      await ensureSensores();
      // Lee si el filtro "Activas" esta marcado
      const soloActivas = $("#filterActivas").checked;
      // Pide las alertas segun el filtro
      const alertas = await Api.getAlertas(soloActivas);
      // Guarda las alertas para poder buscarlas por id al mandarlas por correo
      alertasActuales = alertas;

      // Referencia al cuerpo de la tabla de alertas
      const tb = $("#alertasTable");
      // Limpia las filas anteriores
      tb.innerHTML = "";
      // Si no hay alertas, muestra un mensaje y termina
      if (!alertas.length) {
        tb.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4"><i class="bi bi-check-circle"></i> Sin alertas ' + (soloActivas ? "activas" : "") + '.</td></tr>';
        return;
      }
      // Crea una fila por cada alerta
      alertas.forEach(a => {
        // Severidad en mayusculas (con MEDIA por defecto)
        const sev = (a.severidad || "MEDIA").toUpperCase();
        // Busca el sensor relacionado con la alerta
        const sensor = sensoresById[a.sensorId];
        // Etiqueta del estado: resuelta (gris) o activa (rojo)
        const estado = a.resuelta
          ? '<span class="badge bg-secondary">Resuelta</span>'
          : '<span class="badge bg-danger">Activa</span>';
        // Boton para mandar esta alerta por correo (disponible para todas las alertas)
        const correoBtn = '<button class="btn btn-sm btn-outline-primary" data-correo="' + a.id + '" title="Enviar por correo"><i class="bi bi-envelope"></i></button>';
        // Accion: si esta resuelta muestra la fecha; si no, un boton para resolverla
        const accion = a.resuelta
          ? '<span class="text-muted small">' + fmtFecha(a.fechaResolucion) + "</span>"
          : '<button class="btn btn-sm btn-outline-success" data-resolver="' + a.id + '"><i class="bi bi-check2"></i> Resolver</button>';
        // Crea la fila de la tabla
        const tr = document.createElement("tr");
        // Construye las celdas de la fila con los datos de la alerta
        tr.innerHTML =
          "<td>" + a.id + "</td>" +
          '<td><span class="badge sev-' + sev + '">' + sev + "</span></td>" +
          "<td>" + (a.mensaje || "") + (sensor ? ' <span class="text-muted small">(' + sensor.nombre + ")</span>" : "") + "</td>" +
          "<td>" + (a.valor != null ? a.valor : "—") + "</td>" +
          "<td>" + fmtFecha(a.fecha) + "</td>" +
          "<td>" + estado + "</td>" +
          '<td class="text-nowrap">' + correoBtn + " " + accion + "</td>";
        // Agrega la fila a la tabla
        tb.appendChild(tr);
      });

      // Asigna a cada boton "Resolver" el evento que resuelve la alerta
      $$("#alertasTable [data-resolver]").forEach(btn => {
        // Al hacer clic, resuelve la alerta (funcion async porque espera al API)
        btn.addEventListener("click", async () => {
          // Deshabilita el boton para evitar doble clic
          btn.disabled = true;
          try {
            // Pide al API resolver la alerta (convierte el id de texto a numero)
            await Api.resolverAlerta(Number(btn.dataset.resolver));
            // Avisa que se resolvio
            toast("Alerta resuelta", "success");
            // Recarga la tabla de alertas
            cargarAlertas();
          } catch (ex) {
            // Si falla, maneja el error y reactiva el boton
            manejarError(ex);
            btn.disabled = false;
          }
        });
      });

      // Asigna a cada boton "Correo" el evento que abre el modal para enviarla por correo
      $$("#alertasTable [data-correo]").forEach(btn => {
        // Al hacer clic, busca la alerta por id y abre el modal de envio
        btn.addEventListener("click", () => {
          const a = alertasActuales.find(x => x.id === Number(btn.dataset.correo)); // Busca la alerta en el cache
          if (a) abrirModalCorreo(a);                                               // Si la encuentra, abre el modal
        });
      });
    } catch (ex) {
      // Si algo falla, maneja el error de forma comun
      manejarError(ex);
    }
  }

  // ============================================================
  //  ENVIAR ALERTA POR CORREO (manual, a un destinatario elegido)
  // ============================================================
  // Instancia del modal de Bootstrap (se crea una vez y se reutiliza)
  let correoModal = null;
  // Alerta que se está por enviar (la elegida con el botón "Correo")
  let alertaParaCorreo = null;
  // Devuelve la instancia del modal de correo, creandola la primera vez
  function getCorreoModal() {
    if (!correoModal) correoModal = bootstrap.Modal.getOrCreateInstance($("#correoModal"));
    return correoModal;
  }

  // Abre el modal para enviar una alerta por correo, mostrando una vista previa
  function abrirModalCorreo(a) {
    // Recuerda la alerta elegida
    alertaParaCorreo = a;
    // Oculta errores previos y limpia el campo del correo
    $("#correoError").classList.add("d-none");
    $("#correoDestino").value = "";
    // Arma una vista previa de la alerta (severidad + mensaje + valor + fecha)
    const sev = (a.severidad || "MEDIA").toUpperCase();
    $("#correoPreview").innerHTML =
      '<span class="badge sev-' + sev + '">' + sev + "</span> " + escapeHtml(a.mensaje || "") +
      '<div class="text-muted mt-1">Valor: ' + (a.valor != null ? a.valor : "—") + " · " + fmtFecha(a.fecha) + "</div>";
    // Muestra el modal
    getCorreoModal().show();
  }

  // Maneja el envio del formulario del modal de correo
  $("#correoForm").addEventListener("submit", async (e) => {
    // Evita que el formulario recargue la pagina
    e.preventDefault();
    // Referencias al boton, su spinner y la caja de error
    const btn = $("#correoEnviarBtn"), sp = $("#correoSpinner"), err = $("#correoError");
    // Oculta errores anteriores
    err.classList.add("d-none");
    // Lee el correo destino
    const destinatario = $("#correoDestino").value.trim();
    // La alerta elegida
    const a = alertaParaCorreo;
    if (!a) return;
    // Arma el objeto de la alerta con los nombres que espera el backend (AlertaMensaje)
    const sensor = sensoresById[a.sensorId];
    const alerta = {
      sensorId: a.sensorId,                         // Sensor que origino la alerta
      zona: sensor ? sensor.zona : "",              // Zona (se toma del sensor si esta en cache)
      tipo: a.tipo,                                 // Tipo de medicion
      severidad: a.severidad,                       // Severidad
      mensaje: a.mensaje,                           // Texto de la alerta
      valor: a.valor,                               // Valor detectado
      fecha: a.fecha                                // Fecha de generacion
    };
    // Deshabilita el boton y muestra el spinner mientras envia
    btn.disabled = true; sp.classList.remove("d-none");
    try {
      // Pide al backend enviar el correo al destinatario elegido
      await Api.enviarAlertaCorreo(destinatario, alerta);
      // Cierra el modal y avisa del exito
      getCorreoModal().hide();
      toast("Correo enviado a " + destinatario, "success");
    } catch (ex) {
      // Muestra el error dentro del modal (ej. correo invalido o fallo SMTP)
      err.textContent = ex.message || "No se pudo enviar el correo.";
      err.classList.remove("d-none");
    } finally {
      // Reactiva el boton y oculta el spinner
      btn.disabled = false; sp.classList.add("d-none");
    }
  });

  // Al cambiar el filtro "Activas", recarga las alertas
  $("#filterActivas").addEventListener("change", cargarAlertas);
  // Al cambiar el filtro "Todas", recarga las alertas
  $("#filterTodas").addEventListener("change", cargarAlertas);

  // ============================================================
  //  CRUD DE SENSORES (crear / editar / eliminar)
  // ============================================================
  // Instancia del modal de Bootstrap (se crea una sola vez y se reutiliza)
  let sensorModal = null;
  // Devuelve la instancia del modal del sensor, creandola la primera vez
  function getSensorModal() {
    // Si aun no existe, la crea a partir del elemento del modal
    if (!sensorModal) sensorModal = bootstrap.Modal.getOrCreateInstance($("#sensorModal"));
    // Devuelve la instancia lista para usar
    return sensorModal;
  }

  // Abre el modal del sensor. Con un sensor -> modo edicion; sin el -> modo "nuevo".
  function abrirModalSensor(sensor) {
    // Oculta cualquier error previo del formulario
    $("#sensorFormError").classList.add("d-none");
    // Hay edicion solo si llega un sensor con id
    const editando = !!(sensor && sensor.id);
    // Cambia el titulo del modal segun el caso
    $("#sensorModalTitle").textContent = editando ? "Editar sensor" : "Nuevo sensor";
    // Rellena los campos (vacios si es nuevo, con los datos del sensor si se edita)
    $("#sId").value       = editando ? sensor.id : "";
    $("#sNombre").value   = editando ? (sensor.nombre || "") : "";
    $("#sTipo").value     = editando ? (sensor.tipo || "temperatura") : "temperatura";
    $("#sUnidad").value   = editando ? (sensor.unidad || "") : "";
    $("#sZona").value     = editando ? (sensor.zona || "") : "";
    $("#sMin").value      = editando && sensor.minEsperado != null ? sensor.minEsperado : "";
    $("#sMax").value      = editando && sensor.maxEsperado != null ? sensor.maxEsperado : "";
    $("#sActivo").checked = editando ? sensor.activo !== false : true;
    // Muestra el modal en pantalla
    getSensorModal().show();
  }

  // El boton "Nuevo sensor" abre el modal vacio (modo creacion)
  $("#btnNuevoSensor").addEventListener("click", () => abrirModalSensor(null));

  // Convierte el texto de un campo numerico a numero, o null si viene vacio
  function numOrNull(valor) {
    // Vacio o nulo -> null (para no enviar 0 por accidente)
    if (valor === "" || valor == null) return null;
    // Convierte a numero
    const n = Number(valor);
    // Si no es un numero valido devuelve null; si lo es, el numero
    return isNaN(n) ? null : n;
  }

  // Maneja el envio del formulario del sensor (crea o actualiza segun haya id)
  $("#sensorForm").addEventListener("submit", async (e) => {
    // Evita que el formulario recargue la pagina
    e.preventDefault();
    // Referencias al boton de guardar, su spinner y la caja de error del modal
    const btn = $("#sensorGuardarBtn"), sp = $("#sensorGuardarSpinner"), err = $("#sensorFormError");
    // Oculta errores anteriores
    err.classList.add("d-none");
    // Lee el id oculto (vacio = creando, con valor = editando)
    const id = $("#sId").value;
    // Arma el objeto del sensor con los nombres de campo que espera el backend (camelCase)
    const datos = {
      nombre: $("#sNombre").value.trim(),                 // Nombre del sensor
      tipo: $("#sTipo").value,                            // Tipo (temperatura, humedad, ph)
      zona: $("#sZona").value.trim(),                    // Zona del invernadero
      unidad: $("#sUnidad").value.trim(),                // Unidad de medida
      valorMinEsperado: numOrNull($("#sMin").value),     // Minimo esperado (o null)
      valorMaxEsperado: numOrNull($("#sMax").value),     // Maximo esperado (o null)
      activo: $("#sActivo").checked                       // Si el sensor esta activo
    };
    // Deshabilita el boton y muestra el spinner mientras guarda
    btn.disabled = true; sp.classList.remove("d-none");
    try {
      // Si hay id actualiza el sensor; si no, crea uno nuevo
      if (id) await Api.actualizarSensor(Number(id), datos);
      else    await Api.crearSensor(datos);
      // Cierra el modal
      getSensorModal().hide();
      // Avisa del resultado
      toast(id ? "Sensor actualizado" : "Sensor creado", "success");
      // Refresca la tabla de sensores (invalida la cache)
      await recargarSensores();
    } catch (ex) {
      // Muestra el error dentro del modal (ej. validacion o sesion expirada)
      err.textContent = ex.message || "No se pudo guardar el sensor.";
      err.classList.remove("d-none");
    } finally {
      // Pase lo que pase, reactiva el boton y oculta el spinner
      btn.disabled = false; sp.classList.add("d-none");
    }
  });

  // Pide confirmacion y elimina un sensor
  async function eliminarSensorUI(id, btn) {
    // Busca el sensor para mostrar su nombre en el aviso de confirmacion
    const s = sensoresById[id];
    // Si el usuario cancela la confirmacion, no hace nada
    if (!confirm('¿Eliminar el sensor "' + (s ? s.nombre : id) + '"? Esta acción no se puede deshacer.')) return;
    // Deshabilita el boton para evitar doble clic
    if (btn) btn.disabled = true;
    try {
      // Pide al backend eliminar el sensor
      await Api.eliminarSensor(id);
      // Avisa del exito
      toast("Sensor eliminado", "success");
      // Refresca la tabla
      await recargarSensores();
    } catch (ex) {
      // Si falla (ej. el sensor ya tiene alertas asociadas), lo muestra y reactiva el boton
      manejarError(ex);
      if (btn) btn.disabled = false;
    }
  }

  // Vacia la cache de sensores y vuelve a dibujar la tabla (refleja altas, bajas y cambios)
  async function recargarSensores() {
    // Limpia la cache para forzar una nueva consulta al backend
    sensoresCache = [];
    sensoresById = {};
    // Redibuja la tabla de sensores con los datos frescos
    await cargarSensores();
  }

  // ============================================================
  //  USUARIOS (alta de cuentas del sistema)
  // ============================================================
  // Maneja el envio del formulario de alta de usuario
  $("#usuarioForm").addEventListener("submit", async (e) => {
    // Evita que el formulario recargue la pagina
    e.preventDefault();
    // Referencias al boton, su spinner y las cajas de exito/error
    const btn = $("#usuarioBtn"), sp = $("#usuarioSpinner");
    const ok = $("#usuarioOk"), err = $("#usuarioError");
    // Oculta avisos previos
    ok.classList.add("d-none"); err.classList.add("d-none");
    // Arma el objeto con los nombres que espera el backend (RegisterRequest)
    const datos = {
      username: $("#uUsername").value.trim(),       // Usuario para iniciar sesion
      email: $("#uEmail").value.trim(),             // Correo del usuario
      password: $("#uPassword").value,              // Contrasena (minimo 6)
      nombreCompleto: $("#uNombre").value.trim(),   // Nombre completo (opcional)
      rol: $("#uRol").value                          // Rol elegido (AGRICULTOR o ADMIN)
    };
    // Deshabilita el boton y muestra el spinner
    btn.disabled = true; sp.classList.remove("d-none");
    try {
      // Pide al backend crear el usuario
      await Api.registrarUsuario(datos);
      // Muestra mensaje de exito
      ok.textContent = 'Usuario "' + datos.username + '" creado. Ya puede iniciar sesión.';
      ok.classList.remove("d-none");
      // Vuelve a cargar la lista desde la base de datos (asi el usuario nuevo aparece y persiste)
      cargarUsuarios();
      // Limpia el formulario para crear otro
      $("#usuarioForm").reset();
    } catch (ex) {
      // Muestra el error (por ejemplo, el username o email ya existe)
      err.textContent = ex.message || "No se pudo crear el usuario.";
      err.classList.remove("d-none");
    } finally {
      // Reactiva el boton y oculta el spinner
      btn.disabled = false; sp.classList.add("d-none");
    }
  });

  // Carga y dibuja la tabla de usuarios leyendolos de la base de datos (GET /usuarios, con JWT)
  async function cargarUsuarios() {
    // Referencia al cuerpo de la tabla
    const tb = $("#usuariosTable");
    // Muestra una rueda de carga mientras llegan los datos
    tb.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-4"><div class="spinner-border"></div></td></tr>';
    try {
      // Pide la lista de usuarios al backend
      const usuarios = await Api.listarUsuarios();
      // Limpia la tabla
      tb.innerHTML = "";
      // Si no hay usuarios, muestra un mensaje
      if (!usuarios.length) {
        tb.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-4">No hay usuarios registrados.</td></tr>';
        return;
      }
      // Crea una fila por cada usuario (escapando el texto por seguridad)
      usuarios.forEach(u => {
        // El usuario esta activo salvo que venga explicitamente en false
        const activo = u.activo !== false;
        // Etiqueta de estado segun si esta activo
        const estado = activo
          ? '<span class="badge bg-success">Activo</span>'
          : '<span class="badge bg-secondary">Inactivo</span>';
        // Crea la fila con los datos del usuario
        const tr = document.createElement("tr");
        tr.innerHTML =
          "<td>" + escapeHtml(u.username) + "</td>" +
          "<td>" + escapeHtml(u.email) + "</td>" +
          "<td>" + escapeHtml(u.nombreCompleto || "—") + "</td>" +
          '<td><span class="badge bg-secondary">' + escapeHtml(u.rol) + "</span></td>" +
          "<td>" + estado + "</td>";
        // Agrega la fila a la tabla
        tb.appendChild(tr);
      });
    } catch (ex) {
      // Si falla (por ejemplo, sesion expirada), limpia la tabla y maneja el error
      tb.innerHTML = "";
      manejarError(ex);
    }
  }

  // El boton de refrescar vuelve a cargar la lista de usuarios desde la BD
  $("#btnRefrescarUsuarios").addEventListener("click", cargarUsuarios);

  // Escapa caracteres especiales para que el texto del usuario no se interprete como HTML
  function escapeHtml(str) {
    // Sin texto -> cadena vacia
    if (str == null) return "";
    // Reemplaza los caracteres que el navegador trataria como HTML
    return String(str)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#039;");
  }

  // ============================================================
  //  Manejo de errores común
  // ============================================================
  // Funcion central que decide que hacer ante un error
  function manejarError(ex) {
    // Si el error es 401 (sesion invalida), regresa al login y avisa
    if (ex.status === 401) {
      showLogin();
      toast("Tu sesión expiró. Inicia de nuevo.", "danger");
      return;
    }
    // Para cualquier otro error, muestra un toast rojo con el mensaje
    toast(ex.message || "Ocurrió un error", "danger");
    // Y lo registra en la consola para depuracion
    console.error(ex);
  }

  // ============================================================
  //  Arranque
  // ============================================================
  // Funcion que se ejecuta al cargar la pagina para decidir que mostrar
  function init() {
    // Si el modo DEMO esta activo, muestra la cinta de aviso
    if (CFG.DEMO_MODE) $("#demoRibbon").classList.remove("d-none");
    // Si ya hay sesion guardada, entra directo a la app
    if (Api.isLoggedIn()) showApp();
    // Si no, muestra el login
    else showLogin();
  }
  // Ejecuta el arranque
  init();
})();
