// Camino a la Iluminación — lógica del navegador.
// Todos los datos (parroquias, horarios, avisos, reservas, documentos) vienen del servidor Java /
// base de datos mediante /api/...; aquí no queda ninguna simulación en localStorage.

/* ==================== SESIÓN / AUTENTICACIÓN ====================
   La fuente de verdad es el SERVIDOR (/api/sesion). El navegador solo guarda en memoria lo que
   el servidor responde: nunca se confía en localStorage para saber quién es el usuario ni su rol.
   (Las páginas del panel también están protegidas en el servidor por FiltroAdmin y por cada servlet.) */
let SESION = null;

// Limpieza de restos de versiones anteriores del prototipo, que guardaban cuentas y contraseñas en el navegador.
try { localStorage.removeItem('cai_usuarios'); localStorage.removeItem('cai_sesion'); } catch (e) { /* sin acceso a localStorage */ }

function obtenerSesion() { return SESION; }

async function cargarSesion() {
  try {
    const r = await fetch('/api/sesion', { cache: 'no-store', credentials: 'same-origin' });
    const d = await r.json();
    SESION = d && d.autenticado ? { nombre: d.nombre, rol: d.rol, parroquia: d.parroquia || '' } : null;
  } catch (e) {
    SESION = null;
  }
  return SESION;
}

async function cerrarSesion() {
  try { await fetch('/api/logout', { method: 'POST', credentials: 'same-origin' }); } catch (e) { /* si falla, igual se sale de la vista */ }
  SESION = null;
  window.location.href = rutaIndex();
}

function estaEnAdmin() { return window.location.pathname.includes('/paginas/admin/'); }
function estaEnPaginas() { return window.location.pathname.includes('/paginas/'); }
function rutaPaginas() { return estaEnAdmin() ? '../' : (estaEnPaginas() ? '' : 'paginas/'); }
function rutaIndex() { return estaEnAdmin() ? '../../index.html' : (estaEnPaginas() ? '../index.html' : 'index.html'); }

const INICIO_POR_ROL = { administrador: 'admin/documentos.html', feligres: 'reservas.html' };
function inicioPorRol(rol) { return rutaPaginas() + (INICIO_POR_ROL[rol] || 'login.html'); }

/* Solo se acepta como destino una página interna (evita redirecciones a sitios externos). */
function destinoSeguro(next, rol) {
  if (!/^(admin\/)?[a-z0-9_-]+\.html$/i.test(next || '')) return inicioPorRol(rol);
  if (next.toLowerCase().startsWith('admin/') && rol !== 'administrador') return inicioPorRol(rol);
  return rutaPaginas() + next;
}

function pintarNavCuenta() {
  const navLinks = document.querySelector('.nav-links');
  if (!navLinks || document.getElementById('nav-cuenta')) return;
  if (document.body.dataset.paginaAuth === 'true') return;

  const sesion = obtenerSesion();

  // El personal no usa las pantallas de feligrés: en el sitio público se reemplazan por el acceso al panel.
  if (sesion && sesion.rol === 'administrador' && !estaEnAdmin()) {
    navLinks.querySelectorAll('a[href$="reservas.html"], a[href$="documentos.html"]').forEach((a) => {
      const item = a.closest('li');
      if (item) item.remove();
    });
    const liPanel = document.createElement('li');
    const enlace = document.createElement('a');
    enlace.href = rutaPaginas() + 'admin/documentos.html';
    enlace.textContent = 'Panel administrador';
    liPanel.appendChild(enlace);
    navLinks.appendChild(liPanel);
  }

  const li = document.createElement('li');
  li.id = 'nav-cuenta';
  if (sesion) {
    // textContent (no innerHTML): el nombre viene de la base de datos y no debe interpretarse como HTML.
    const saludo = document.createElement('span');
    saludo.className = 'nav-saludo';
    saludo.append('Hola, ' + String(sesion.nombre || '').split(' ')[0] + ' · ');
    const salir = document.createElement('span');
    salir.className = 'nav-salir';
    salir.id = 'btn-salir';
    salir.textContent = 'Salir';
    salir.addEventListener('click', cerrarSesion);
    saludo.appendChild(salir);
    li.appendChild(saludo);
  } else {
    const enlace = document.createElement('a');
    enlace.href = rutaPaginas() + 'login.html';
    enlace.className = 'nav-cuenta-link';
    enlace.textContent = 'Iniciar sesión';
    li.appendChild(enlace);
  }
  navLinks.appendChild(li);
}

/* Guarda de página: <body data-requiere-rol="feligres|administrador">.
   Sin sesión -> login (recordando a dónde volver); con otro rol -> a su propia zona. */
function aplicarGuarda() {
  const requerido = document.body.dataset.requiereRol;
  if (!requerido) return true;
  const sesion = obtenerSesion();
  if (!sesion) {
    const next = encodeURIComponent((estaEnAdmin() ? 'admin/' : '') + window.location.pathname.split('/').pop());
    window.location.replace(`${rutaPaginas()}login.html?next=${next}`);
    return false;
  }
  if (sesion.rol !== requerido) {
    window.location.replace(inicioPorRol(sesion.rol));
    return false;
  }
  document.body.classList.add('sesion-ok'); // el CSS oculta la página protegida hasta este punto
  return true;
}

/* La sesión se consulta al servidor sin bloquear el resto de la página. */
document.addEventListener('DOMContentLoaded', async () => {
  await cargarSesion();
  if (!aplicarGuarda()) return;
  pintarNavCuenta();
  const franja = document.getElementById('franja-parroquia');
  if (franja && SESION && SESION.parroquia) franja.textContent = SESION.parroquia;
});

/* ==================== GEOLOCALIZACIÓN ==================== */
function distanciaKm(lat1, lng1, lat2, lng2) {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLng = (lng2 - lng1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function pedirUbicacion(alExito, alFallo) {
  if (!navigator.geolocation) { alFallo && alFallo(); return; }
  navigator.geolocation.getCurrentPosition(
    (pos) => alExito(pos.coords.latitude, pos.coords.longitude),
    () => alFallo && alFallo(),
    { enableHighAccuracy: true, timeout: 8000 }
  );
}

/* ==================== UTILIDADES DE RESERVAS ==================== */
const NOMBRES_SACRAMENTO = { bautizo: 'Bautizo', matrimonio: 'Matrimonio', primera_comunion: 'Primera Comunión', confirmacion: 'Confirmación' };
const ESTADOS_RESERVA = { pendiente: ['pendiente', 'Pendiente'], confirmada: ['validado', 'Confirmada'], rechazada: ['rechazado', 'Rechazada'] };

/* Los textos de la base de datos se insertan siempre escapados (nunca como HTML crudo). */
function escaparHtml(texto) {
  return String(texto ?? '').replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function formatearFecha(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso || '');
  return m ? `${m[3]}/${m[2]}/${m[1]}` : escaparHtml(iso);
}

/* "Mis solicitudes" del feligrés (tabla en reservas.html). Devuelve una promesa. */
function cargarMisReservas() {
  const tbody = document.getElementById('tabla-mis-reservas');
  if (!tbody) return Promise.resolve();
  const nota = document.getElementById('nota-mis-reservas');
  return fetch('/api/mis-reservas', { cache: 'no-store', credentials: 'same-origin' })
    .then((r) => r.json().catch(() => ({})).then((data) => ({ ok: r.ok, data })))
    .then(({ ok, data }) => {
      if (!ok) {
        tbody.innerHTML = '';
        nota.textContent = data.error || 'No se pudieron cargar tus solicitudes.';
        return;
      }
      nota.textContent = data.length ? '' : 'Todavía no has enviado ninguna solicitud.';
      tbody.innerHTML = data.map((r) => {
        const [clase, texto] = ESTADOS_RESERVA[r.estado] || ['pendiente', escaparHtml(r.estado)];
        const detalle = r.estado === 'rechazada' && r.motivo ? `Motivo: ${escaparHtml(r.motivo)}`
          : (r.estado === 'confirmada' ? 'La parroquia confirmó tu fecha.' : 'En revisión por la parroquia.');
        return `<tr>
          <td>${escaparHtml(NOMBRES_SACRAMENTO[r.sacramento] || r.sacramento)}</td>
          <td>${escaparHtml(r.parroquia)}</td>
          <td>${escaparHtml(r.solicitante)}</td>
          <td>${formatearFecha(r.fecha)}</td>
          <td><span class="insignia ${clase}">${texto}</span></td>
          <td>${detalle}</td>
        </tr>`;
      }).join('');
    })
    .catch(() => { nota.textContent = 'No se pudo conectar con el servidor.'; });
}

/* ==================== PARROQUIAS Y CONTENIDO PÚBLICO (fuente única: la base de datos) ==================== */
const DIAS_ORDEN = ['lunes', 'martes', 'miercoles', 'jueves', 'viernes', 'sabado', 'domingo'];
const NOMBRES_DIA = { lunes: 'Lunes', martes: 'Martes', miercoles: 'Miércoles', jueves: 'Jueves', viernes: 'Viernes', sabado: 'Sábado', domingo: 'Domingo' };
const MESES_CORTOS = ['ENE', 'FEB', 'MAR', 'ABR', 'MAY', 'JUN', 'JUL', 'AGO', 'SEP', 'OCT', 'NOV', 'DIC'];

let PARROQUIAS = null;
function cargarParroquias() {
  if (PARROQUIAS) return Promise.resolve(PARROQUIAS);
  return fetch('/api/parroquias', { cache: 'no-cache' })
    .then((r) => (r.ok ? r.json() : []))
    .catch(() => [])
    .then((lista) => { PARROQUIAS = Array.isArray(lista) ? lista : []; return PARROQUIAS; });
}

function normalizarTexto(t) {
  return String(t ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
}

/* "29 OCT" a partir de "2026-10-29" (sin pasar por Date: evita corrimientos por zona horaria). */
function etiquetaFecha(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso || '');
  return m ? `${m[3]} ${MESES_CORTOS[Number(m[2]) - 1] || ''}`.trim() : '';
}

/* Siglas para el círculo de la tarjeta: "Parroquia San José" -> SJ, "Capilla Sagrado Corazón" -> SC. */
function siglasParroquia(nombre) {
  const palabras = String(nombre || '').split(/\s+/).filter(Boolean)
    .filter((w) => !/^(parroquia|capilla|iglesia|santuario|de|del|la|las|los|el)$/i.test(w));
  return palabras.slice(0, 2).map((w) => w[0].toUpperCase()).join('') || '⛪';
}

/* Parroquia elegida: ?parroquia=ID de la URL, si no la última que se usó en esta sesión, si no la primera. */
function parroquiaElegida(lista) {
  const deUrl = new URLSearchParams(window.location.search).get('parroquia');
  let guardada = null;
  try { guardada = sessionStorage.getItem('cai_parroquia'); } catch (e) { /* sin acceso */ }
  return lista.find((p) => String(p.id) === deUrl)
    || lista.find((p) => String(p.id) === guardada)
    || lista[0] || null;
}

function prepararSelectorParroquia(select, lista, alCambiar) {
  select.innerHTML = '';
  lista.forEach((p) => {
    const op = document.createElement('option');
    op.value = String(p.id);
    op.textContent = p.nombre;
    select.appendChild(op);
  });
  const inicial = parroquiaElegida(lista);
  if (inicial) select.value = String(inicial.id);
  select.addEventListener('change', () => {
    const p = lista.find((x) => String(x.id) === select.value);
    if (!p) return;
    try { sessionStorage.setItem('cai_parroquia', String(p.id)); } catch (e) { /* sin acceso */ }
    const url = new URL(window.location.href);
    url.searchParams.set('parroquia', String(p.id));
    window.history.replaceState(null, '', url);
    alCambiar(p);
  });
  if (inicial) alCambiar(inicial);
}

/* Agrupa los horarios de un mismo tipo: si de lunes a viernes dicen lo mismo, se muestra "Lunes a viernes". */
function agruparHorarios(lista) {
  const porDia = {};
  lista.forEach((h) => { (porDia[h.dia] = porDia[h.dia] || []).push(h.hora); });
  const texto = (d) => (porDia[d] || []).join(' · ');
  const semana = DIAS_ORDEN.slice(0, 5);
  const filas = [];
  const iguales = semana.every((d) => porDia[d]) && new Set(semana.map(texto)).size === 1;
  if (iguales) filas.push({ grupo: 'semana', etiqueta: 'Lunes a viernes', detalle: texto('lunes') });
  else semana.forEach((d) => { if (porDia[d]) filas.push({ grupo: 'semana', etiqueta: NOMBRES_DIA[d], detalle: texto(d) }); });
  ['sabado', 'domingo'].forEach((d) => { if (porDia[d]) filas.push({ grupo: d, etiqueta: NOMBRES_DIA[d], detalle: texto(d) }); });
  return filas;
}

function aplicarFiltroDia(dia) {
  document.querySelectorAll('#lista-misas .fila-horario').forEach((fila) => {
    fila.style.display = (dia === 'todos' || fila.dataset.dia === dia) ? '' : 'none';
  });
}
function aplicarFiltroAvisos(filtro) {
  document.querySelectorAll('#lista-avisos .aviso').forEach((aviso) => {
    aviso.style.display = (filtro === 'todos' || aviso.classList.contains(filtro)) ? '' : 'none';
  });
}

/* ==================== INICIO ==================== */
document.addEventListener('DOMContentLoaded', () => {

  /* ---------- campos numéricos (DNI): solo dígitos ---------- */
  document.querySelectorAll('input[data-solo-digitos]').forEach((input) => {
    input.addEventListener('input', () => {
      input.value = input.value.replace(/\D/g, '').slice(0, input.maxLength > 0 ? input.maxLength : 20);
    });
  });

  /* ---------- menú móvil ---------- */
  const toggle = document.getElementById('menu-toggle');
  const navLinks = document.querySelector('.nav-links');
  if (toggle && navLinks) {
    toggle.addEventListener('click', () => navLinks.classList.toggle('abierto'));
  }

  /* ---------- avisos flotantes (toasts): los urgentes más recientes, desde la base de datos ---------- */
  if (!sessionStorage.getItem('cai_avisos_vistos')) {
    fetch('/api/avisos?urgentes=1', { cache: 'no-cache' })
      .then((r) => (r.ok ? r.json() : []))
      .catch(() => [])
      .then((avisos) => {
        if (!Array.isArray(avisos) || !avisos.length) return;
        sessionStorage.setItem('cai_avisos_vistos', '1');
        let contenedor = document.getElementById('toast-contenedor');
        if (!contenedor) {
          contenedor = document.createElement('div');
          contenedor.id = 'toast-contenedor';
          contenedor.className = 'toast-contenedor';
          document.body.appendChild(contenedor);
        }
        let i = 0;
        const mostrarSiguiente = () => {
          if (i >= avisos.length) return;
          const aviso = avisos[i++];
          const toast = document.createElement('div');
          toast.className = 'toast' + (aviso.urgente ? ' urgente' : '');
          // textContent: los textos vienen de la base de datos y no se interpretan como HTML.
          const fecha = document.createElement('span');
          fecha.className = 'toast-fecha';
          fecha.textContent = etiquetaFecha(aviso.fechaEvento) || etiquetaFecha(aviso.publicadoEn);
          const titulo = document.createElement('h5');
          titulo.textContent = aviso.titulo;
          const texto = document.createElement('p');
          const detalle = String(aviso.descripcion || '');
          texto.textContent = `${aviso.parroquia ? aviso.parroquia + ' — ' : ''}${detalle.length > 110 ? detalle.slice(0, 107) + '…' : detalle}`;
          toast.append(fecha, titulo, texto);
          contenedor.appendChild(toast);
          requestAnimationFrame(() => toast.classList.add('mostrar'));
          setTimeout(() => { toast.classList.remove('mostrar'); setTimeout(() => toast.remove(), 350); }, 2600);
          setTimeout(mostrarSiguiente, 3000);
        };
        setTimeout(mostrarSiguiente, 600);
      });
  }

  /* ---------- contador animado (hero de inicio) ---------- */
  document.querySelectorAll('.stat-num[data-hasta]').forEach((el) => {
    const meta = parseInt(el.dataset.hasta, 10);
    let actual = 0;
    const paso = Math.max(1, Math.round(meta / 40));
    const intervalo = setInterval(() => {
      actual += paso;
      if (actual >= meta) { actual = meta; clearInterval(intervalo); }
      el.textContent = actual.toLocaleString('es-PE') + (el.dataset.sufijo || '');
    }, 25);
  });

  /* ---------- formulario de registro (Conectado a MySQL via Servlet) ---------- */
    const formRegistro = document.getElementById('form-registro');
    if (formRegistro) {
      formRegistro.addEventListener('submit', (e) => {
        e.preventDefault();

        const campos = {
          nombre: document.getElementById('reg-nombre'),
          apellido: document.getElementById('reg-apellido'),
          dni: document.getElementById('reg-dni'),
          correo: document.getElementById('reg-correo'),
          telefono: document.getElementById('reg-telefono'),
          clave: document.getElementById('reg-clave'),
          clave2: document.getElementById('reg-clave2')
        };
        const terminos = document.getElementById('reg-terminos');
        const alerta = document.getElementById('alerta-registro');
        let valido = true;
        alerta.className = 'alerta-auth';

        Object.values(campos).forEach((c) => c.closest('.campo').classList.remove('con-error'));

        const patronNombre = /^[\p{L}][\p{L} .'-]{1,79}$/u;
        if (!patronNombre.test(campos.nombre.value.trim())) { marcarError(campos.nombre); valido = false; }
        if (!patronNombre.test(campos.apellido.value.trim())) { marcarError(campos.apellido); valido = false; }
        if (!/^[0-9]{8}$/.test(campos.dni.value.trim())) { marcarError(campos.dni); valido = false; }
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(campos.correo.value.trim())) { marcarError(campos.correo); valido = false; }
        if (!/^[0-9]{7,9}$/.test(campos.telefono.value.trim())) { marcarError(campos.telefono); valido = false; }
        if (campos.clave.value.length < 6) { marcarError(campos.clave); valido = false; }
        if (campos.clave2.value !== campos.clave.value || !campos.clave2.value) { marcarError(campos.clave2); valido = false; }
        if (!terminos.checked) valido = false;

        if (!valido) {
          alerta.textContent = 'Revisa los campos marcados: todos son obligatorios y deben ser válidos.';
          alerta.classList.add('visible', 'error');
          return;
        }

        alerta.textContent = 'Creando tu cuenta...';
        alerta.classList.add('visible', 'exito');

        // Preparamos los datos para enviarlos al Servlet RegistroServlet (/api/registro)
        const datos = new URLSearchParams();
        datos.append('dni', campos.dni.value.trim());
        datos.append('nombre', campos.nombre.value.trim());
        datos.append('apellido', campos.apellido.value.trim());
        datos.append('correo', campos.correo.value.trim().toLowerCase());
        datos.append('telefono', campos.telefono.value.trim());
        datos.append('contrasena', campos.clave.value);
        datos.append('ubicacion', 'Chiclayo'); // Ubicación por defecto para la demo

        fetch('/api/registro', { method: 'POST', body: datos })
          .then((response) => response.json().catch(() => ({})).then((data) => ({ ok: response.ok, data })))
          .then(({ ok, data }) => {
            if (ok) {
              alerta.textContent = '¡Cuenta creada! Ahora puedes iniciar sesión.';
              setTimeout(() => window.location.href = 'login.html', 1200);
            } else {
              alerta.textContent = data.error || 'Error al registrar la cuenta en el servidor.';
              alerta.className = 'alerta-auth visible error';
            }
          })
          .catch((error) => {
            console.error('Error en la petición:', error);
            alerta.textContent = 'No se pudo conectar con el servidor.';
            alerta.className = 'alerta-auth visible error';
          });
      });
    }

  function marcarError(campo) { campo.closest('.campo').classList.add('con-error'); }

  /* ---------- formulario de login (Conectado a MySQL via Servlet) ---------- */
    const formLogin = document.getElementById('form-login');
    if (formLogin) {
      formLogin.addEventListener('submit', (e) => {
        e.preventDefault();
        const dni = document.getElementById('log-dni');
        const clave = document.getElementById('log-clave');
        const alerta = document.getElementById('alerta-login');
        let valido = true;
        [dni, clave].forEach((c) => c.closest('.campo').classList.remove('con-error'));

        if (!/^[0-9]{8}$/.test(dni.value.trim())) { marcarError(dni); valido = false; }
        if (!clave.value.trim()) { marcarError(clave); valido = false; }

        if (!valido) {
          alerta.textContent = 'Ingresa tu DNI (8 dígitos) y tu contraseña para continuar.';
          alerta.className = 'alerta-auth visible error';
          return;
        }

        const datos = new URLSearchParams();
        datos.append('dni', dni.value.trim());
        datos.append('contrasena', clave.value);

        fetch('/api/login', {
            method: 'POST',
            body: datos
        })
        .then(response => response.json().catch(() => ({ exito: false, error: 'El servidor no respondió correctamente.' })))
        .then(data => {
            if (data.exito) {
                // La sesión real vive en el servidor; aquí solo se recuerda en memoria para esta vista.
                SESION = { nombre: data.nombre, rol: data.rol };
                const params = new URLSearchParams(window.location.search);
                window.location.href = destinoSeguro(params.get('next'), data.rol);
            } else {
                alerta.textContent = data.error || 'DNI o contraseña incorrectos.';
                alerta.className = 'alerta-auth visible error';
            }
        })
        .catch(error => {
            console.error("Error en la petición:", error);
            alerta.textContent = 'No se pudo conectar con el servidor.';
            alerta.className = 'alerta-auth visible error';
        });
      });
    }

  /* ---------- formulario de reserva (stepper) ---------- */
  const formReserva = document.getElementById('form-reserva');
  if (formReserva) {
    const pasos = document.querySelectorAll('.paso-item');
    const paneles = document.querySelectorAll('.panel-paso');
    let pasoActual = 0;

    /* Las tarjetas salen del catálogo de sacramentos de la base de datos (agregar uno nuevo no toca este código). */
    const contenedorTarjetas = document.getElementById('tarjetas-sacramento');
    const campoSacramento = document.getElementById('sacramento');
    fetch('/api/sacramentos', { cache: 'no-cache' })
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then((lista) => {
        contenedorTarjetas.innerHTML = lista.length ? lista.map((sc) =>
          `<div class="tarjeta-sacramento" data-valor="${escaparHtml(sc.nombre)}" data-codigo="${escaparHtml(sc.codigo)}">${escaparHtml(sc.nombre)}</div>`).join('')
          : '<p style="color:var(--color-ink-soft)">No hay sacramentos disponibles por ahora.</p>';
      })
      .catch(() => { contenedorTarjetas.innerHTML = '<p style="color:var(--color-ink-soft)">No se pudieron cargar los sacramentos. Recarga la página.</p>'; });
    contenedorTarjetas.addEventListener('click', (e) => {
      const t = e.target.closest('.tarjeta-sacramento');
      if (!t) return;
      contenedorTarjetas.querySelectorAll('.tarjeta-sacramento').forEach((x) => x.classList.remove('elegido'));
      t.classList.add('elegido');
      campoSacramento.value = t.dataset.valor;
      poblarParroquias();
    });

    /* El selector de parroquias sale de la base de datos y solo lista las que ofrecen el sacramento elegido. */
    const selectParroquia = document.getElementById('parroquia');
    function poblarParroquias() {
      return cargarParroquias().then((lista) => {
        const elegido = normalizarTexto(campoSacramento.value);
        const ofrecen = lista.filter((p) => !elegido || p.sacramentos.some((x) => normalizarTexto(x) === elegido));
        const previo = selectParroquia.value || new URLSearchParams(window.location.search).get('parroquia') || '';
        selectParroquia.length = 1; // deja solo el texto "Selecciona una parroquia"
        ofrecen.forEach((p) => {
          const op = document.createElement('option');
          op.value = String(p.id);
          op.textContent = p.nombre;
          selectParroquia.appendChild(op);
        });
        selectParroquia.options[0].textContent = (elegido && !ofrecen.length)
          ? 'Ninguna parroquia ofrece este sacramento por ahora' : 'Selecciona una parroquia';
        if (ofrecen.some((p) => String(p.id) === previo)) selectParroquia.value = previo;
      });
    }

    function mostrarPaso(i) {
      paneles.forEach((p, idx) => p.style.display = idx === i ? 'block' : 'none');
      pasos.forEach((p, idx) => {
        p.classList.toggle('activo', idx === i);
        p.classList.toggle('hecho', idx < i);
      });
    }

    document.querySelectorAll('[data-siguiente]').forEach((btn) => {
      btn.addEventListener('click', () => {
        if (pasoActual === 0 && !campoSacramento.value) {
          alert('Elige un sacramento para continuar.');
          return;
        }
        pasoActual = Math.min(pasoActual + 1, paneles.length - 1);
        mostrarPaso(pasoActual);
      });
    });
    document.querySelectorAll('[data-anterior]').forEach((btn) => {
      btn.addEventListener('click', () => {
        pasoActual = Math.max(pasoActual - 1, 0);
        mostrarPaso(pasoActual);
      });
    });

    mostrarPaso(0);
    poblarParroquias();
    cargarMisReservas();

    formReserva.addEventListener('submit', (e) => {
          e.preventDefault();

          const mensaje = document.getElementById('mensaje-reserva');
          mensaje.textContent = 'Enviando a la base de datos...';
          mensaje.classList.add('visible');
          const botonSubmit = formReserva.querySelector('button[type="submit"]');
          botonSubmit.setAttribute('disabled', 'true');

          // 1. Capturar los datos del formulario HTML
          const datos = new URLSearchParams();
          // Asegúrate de que los IDs coincidan con los de tu HTML (reservas.html)
          datos.append('sacramento', document.getElementById('sacramento').value);
          datos.append('id_parroquia', selectParroquia.value);
          datos.append('nombre_solicitante', document.getElementById('nombre').value);
          datos.append('fecha', document.getElementById('fecha').value);
          datos.append('notas', document.getElementById('notas').value);

          // 2. Enviar los datos al Servlet de Java
          fetch('/api/reservas', { method: 'POST', body: datos })
          .then((respuesta) => respuesta.json().catch(() => ({})).then((data) => ({ ok: respuesta.ok, data })))
          .then(({ ok, data }) => {
              if (ok) {
                  mensaje.textContent = '¡Solicitud registrada! Puedes seguir su estado en "Mis solicitudes", más abajo.';
                  mensaje.style.color = '';
                  // Se limpia el formulario para poder hacer otra solicitud; el servidor impide duplicar una fecha ocupada.
                  formReserva.reset();
                  contenedorTarjetas.querySelectorAll('.tarjeta-sacramento').forEach((x) => x.classList.remove('elegido'));
                  campoSacramento.value = '';
                  poblarParroquias();
                  pasoActual = 0;
                  mostrarPaso(0);
                  cargarMisReservas();
              } else {
                  mensaje.textContent = data.error || 'Error al guardar la reserva en el servidor.';
                  mensaje.style.color = 'red';
              }
          })
          .catch((error) => {
              console.error('Error en la petición:', error);
              mensaje.textContent = 'No se pudo conectar con el servidor.';
              mensaje.style.color = 'red';
          })
          .finally(() => {
              botonSubmit.removeAttribute('disabled');
          });
        });
  }

  /* ---------- documentos del feligrés: subida real al servidor ---------- */
  const zonaCarga = document.getElementById('zona-carga');
  const inputArchivo = document.getElementById('input-archivo');
  const listaArchivos = document.getElementById('lista-archivos');
  const selectReserva = document.getElementById('doc-reserva');
  const tablaDocFeligres = document.getElementById('tabla-documentos-feligres');
  const MAX_BYTES_DOC = 5 * 1024 * 1024;
  const ESTADOS_DOCUMENTO = { pendiente: ['pendiente', 'Pendiente'], validado: ['validado', 'Validado'], rechazado: ['rechazado', 'Rechazado'] };

  const cargarSolicitudesParaDocumentos = () => {
    if (!selectReserva) return Promise.resolve();
    return fetch('/api/mis-reservas', { cache: 'no-store', credentials: 'same-origin' })
      .then((r) => r.json().catch(() => []))
      .then((data) => {
        const activas = Array.isArray(data) ? data.filter((r) => r.estado !== 'rechazada') : [];
        selectReserva.length = 1; // deja solo la opción "Selecciona una solicitud"
        activas.forEach((r) => {
          const op = document.createElement('option');
          op.value = String(r.id);
          op.textContent = `${NOMBRES_SACRAMENTO[r.sacramento] || r.sacramento} — ${r.parroquia} — ${formatearFecha(r.fecha)} (${r.solicitante})`;
          selectReserva.appendChild(op);
        });
        document.getElementById('aviso-sin-reservas').style.display = activas.length ? 'none' : 'block';
        if (activas.length === 1) selectReserva.value = String(activas[0].id);
      })
      .catch(() => { /* sin conexión: la lista queda vacía */ });
  };

  const cargarMisDocumentos = () => {
    if (!tablaDocFeligres) return Promise.resolve();
    return fetch('/api/documentos', { cache: 'no-store', credentials: 'same-origin' })
      .then((r) => r.json().catch(() => ({})).then((data) => ({ ok: r.ok, data })))
      .then(({ ok, data }) => {
        if (!ok) { tablaDocFeligres.innerHTML = `<tr><td colspan="4">${escaparHtml(data.error || 'No se pudieron cargar tus documentos.')}</td></tr>`; return; }
        if (!data.length) { tablaDocFeligres.innerHTML = '<tr><td colspan="4">Todavía no has enviado documentos.</td></tr>'; return; }
        tablaDocFeligres.innerHTML = data.map((d) => {
          const [clase, texto] = ESTADOS_DOCUMENTO[d.estado] || ['pendiente', escaparHtml(d.estado)];
          const tramite = [NOMBRES_SACRAMENTO[d.sacramento] || d.sacramento, d.parroquia, d.fechaReserva ? formatearFecha(d.fechaReserva) : '']
            .filter(Boolean).map(escaparHtml).join(' · ');
          const motivo = d.estado === 'rechazado' && d.motivo ? `<br><small>Motivo: ${escaparHtml(d.motivo)}</small>` : '';
          return `<tr>
            <td><a href="/api/documentos/archivo?id=${Number(d.id)}" target="_blank" rel="noopener">${escaparHtml(d.nombre)}</a></td>
            <td>${tramite}</td>
            <td>${formatearFecha(d.fechaEnvio)}</td>
            <td><span class="insignia ${clase}">${texto}</span>${motivo}</td>
          </tr>`;
        }).join('');
      })
      .catch(() => { tablaDocFeligres.innerHTML = '<tr><td colspan="4">No se pudo conectar con el servidor.</td></tr>'; });
  };

  /* Sube UN archivo. La validación del navegador es solo comodidad: el servidor revisa de nuevo el contenido real. */
  const subirArchivo = (archivo, idReserva) => {
    if (archivo.size === 0) return Promise.resolve({ ok: false, mensaje: 'El archivo está vacío.' });
    if (archivo.size > MAX_BYTES_DOC) return Promise.resolve({ ok: false, mensaje: 'Supera el máximo de 5 MB.' });
    if (!/\.(pdf|jpe?g|png)$/i.test(archivo.name)) return Promise.resolve({ ok: false, mensaje: 'Solo se aceptan PDF, JPG o PNG.' });
    const datos = new FormData();
    datos.append('id_reserva', idReserva);
    datos.append('archivo', archivo);
    return fetch('/api/documentos', { method: 'POST', body: datos, credentials: 'same-origin' })
      .then((r) => r.json().catch(() => ({})).then((data) => ({ ok: r.ok, data })))
      .then(({ ok, data }) => ok ? { ok: true } : { ok: false, mensaje: data.error || 'No se pudo subir el archivo.' })
      .catch(() => ({ ok: false, mensaje: 'No se pudo conectar con el servidor.' }));
  };

  const procesarArchivos = async (archivos) => {
    const mensaje = document.getElementById('mensaje-documento');
    const campo = selectReserva.closest('.campo');
    campo.classList.remove('con-error');
    if (!selectReserva.value) {
      campo.classList.add('con-error');
      mensaje.classList.add('visible');
      mensaje.textContent = 'Primero elige la solicitud a la que pertenecen los documentos.';
      return;
    }
    let enviados = 0;
    for (const archivo of archivos) {
      const li = document.createElement('li');
      const nombre = document.createElement('span');
      nombre.textContent = '📄 ' + archivo.name;
      const estado = document.createElement('span');
      estado.className = 'insignia pendiente';
      estado.textContent = 'Subiendo…';
      li.append(nombre, estado);
      listaArchivos.appendChild(li);

      const res = await subirArchivo(archivo, selectReserva.value);
      if (res.ok) { enviados++; estado.textContent = 'Enviado'; }
      else { estado.className = 'insignia rechazado'; estado.textContent = res.mensaje; }
    }
    mensaje.classList.add('visible');
    mensaje.textContent = enviados
      ? `${enviados === 1 ? 'Documento recibido' : enviados + ' documentos recibidos'}. La parroquia los revisará y verás el resultado en la tabla de abajo.`
      : 'No se pudo enviar ningún documento. Revisa los avisos junto a cada archivo.';
    if (enviados) cargarMisDocumentos();
  };

  if (zonaCarga && selectReserva) {
    zonaCarga.addEventListener('click', () => inputArchivo && inputArchivo.click());
    zonaCarga.addEventListener('dragover', (e) => { e.preventDefault(); zonaCarga.classList.add('sobre'); });
    zonaCarga.addEventListener('dragleave', () => zonaCarga.classList.remove('sobre'));
    zonaCarga.addEventListener('drop', (e) => {
      e.preventDefault();
      zonaCarga.classList.remove('sobre');
      procesarArchivos([...e.dataTransfer.files]);
    });
    if (inputArchivo) {
      inputArchivo.addEventListener('change', (e) => {
        const archivos = [...e.target.files];
        e.target.value = ''; // permite volver a elegir el mismo archivo
        procesarArchivos(archivos);
      });
    }
    cargarSolicitudesParaDocumentos();
  }
  cargarMisDocumentos();

  /* ---------- avisos del feligrés (notificaciones.html): por parroquia, desde la base de datos ---------- */
  const listaAvisos = document.getElementById('lista-avisos');
  if (listaAvisos) {
    const selectorAv = document.getElementById('selector-parroquia');
    const mostrarAvisos = (p) => {
      listaAvisos.innerHTML = '<p style="color:var(--color-ink-soft)">Cargando avisos…</p>';
      fetch('/api/avisos?parroquia=' + encodeURIComponent(p.id), { cache: 'no-cache' })
        .then((r) => (r.ok ? r.json() : Promise.reject()))
        .then((avisos) => {
          listaAvisos.innerHTML = avisos.length ? avisos.map((a) => `
            <div class="aviso ${a.urgente ? 'urgente' : ''}">
              <span class="aviso-fecha">${escaparHtml(etiquetaFecha(a.fechaEvento) || etiquetaFecha(a.publicadoEn))}</span>
              <div>
                <h4>${escaparHtml(a.titulo)}</h4>
                <p>${escaparHtml(a.descripcion)}</p>
              </div>
            </div>`).join('') : '<p style="color:var(--color-ink-soft)">Esta parroquia aún no ha publicado avisos.</p>';
          const chipActivo = document.querySelector('.chip.activo[data-filtro]');
          if (chipActivo) aplicarFiltroAvisos(chipActivo.dataset.filtro);
        })
        .catch(() => { listaAvisos.innerHTML = '<p style="color:var(--color-ink-soft)">No se pudieron cargar los avisos. Intenta de nuevo.</p>'; });
    };
    cargarParroquias().then((lista) => {
      if (!lista.length) { listaAvisos.innerHTML = '<p>No hay parroquias registradas.</p>'; return; }
      prepararSelectorParroquia(selectorAv, lista, mostrarAvisos);
    });
  }

  /* ---------- horarios del feligrés (horarios.html): por parroquia, desde la base de datos ---------- */
  const listaMisas = document.getElementById('lista-misas');
  if (listaMisas) {
    const listaActividades = document.getElementById('lista-actividades');
    const selectorHor = document.getElementById('selector-parroquia');
    const mostrarHorarios = (p) => {
      document.getElementById('nombre-parroquia').textContent = p.nombre;
      listaMisas.innerHTML = '<li class="fila-horario"><span class="detalle">Cargando horarios…</span></li>';
      listaActividades.innerHTML = '';
      fetch('/api/horarios?parroquia=' + encodeURIComponent(p.id), { cache: 'no-cache' })
        .then((r) => (r.ok ? r.json() : Promise.reject()))
        .then((horarios) => {
          const misas = agruparHorarios(horarios.filter((h) => h.tipo === 'misa'));
          listaMisas.innerHTML = misas.length ? misas.map((f) => `
            <li class="fila-horario" data-dia="${escaparHtml(f.grupo)}"><span class="dia">${escaparHtml(f.etiqueta)}</span><span class="detalle">${escaparHtml(f.detalle)}</span></li>`).join('')
            : '<li class="fila-horario"><span class="detalle">Esta parroquia aún no ha publicado sus horarios de misa.</span></li>';
          const actividades = horarios.filter((h) => h.tipo === 'actividad');
          listaActividades.innerHTML = actividades.length ? actividades.map((h) => `
            <li class="fila-horario"><span class="dia">${escaparHtml(NOMBRES_DIA[h.dia] || h.dia)}</span><span class="detalle">${escaparHtml(h.hora)}</span></li>`).join('')
            : '<li class="fila-horario"><span class="detalle">Sin actividades registradas por ahora.</span></li>';
          const tabActiva = document.querySelector('.pestana-dia.activo');
          if (tabActiva) aplicarFiltroDia(tabActiva.dataset.dia);
        })
        .catch(() => { listaMisas.innerHTML = '<li class="fila-horario"><span class="detalle">No se pudieron cargar los horarios. Intenta de nuevo.</span></li>'; });
    };
    cargarParroquias().then((lista) => {
      if (!lista.length) { listaMisas.innerHTML = '<li class="fila-horario"><span class="detalle">No hay parroquias registradas.</span></li>'; return; }
      prepararSelectorParroquia(selectorHor, lista, mostrarHorarios);
    });
  }

  /* ---------- buscador de iglesias (mapa.html) ---------- */
  const buscador = document.getElementById('buscador-iglesias');
  const sinResultados = document.getElementById('sin-resultados');
  if (buscador) {
    buscador.addEventListener('input', (e) => {
      const termino = e.target.value.toLowerCase();
      let visibles = 0;
      document.querySelectorAll('.tarjeta-iglesia').forEach((tarjeta) => {
        const texto = tarjeta.textContent.toLowerCase();
        const coincide = texto.includes(termino);
        tarjeta.style.display = coincide ? '' : 'none';
        if (coincide) visibles++;
      });
      if (sinResultados) sinResultados.classList.toggle('visible', visibles === 0);
    });
  }

  /* ---------- chips de avisos ---------- */
  const chips = document.querySelectorAll('.chip[data-filtro]');
  chips.forEach((chip) => {
    chip.addEventListener('click', () => {
      chips.forEach((c) => c.classList.remove('activo'));
      chip.classList.add('activo');
      aplicarFiltroAvisos(chip.dataset.filtro);
    });
  });

  /* ---------- pestañas de día (horarios) ---------- */
  const pestanas = document.querySelectorAll('.pestana-dia');
  pestanas.forEach((p) => {
    p.addEventListener('click', () => {
      pestanas.forEach((x) => x.classList.remove('activo'));
      p.classList.add('activo');
      aplicarFiltroDia(p.dataset.dia);
    });
  });

  /* ---------- mapa real con Leaflet + tarjetas: iglesias cercanas (desde la base de datos) ---------- */
  const contenedorMapa = document.getElementById('mapa-iglesias');
  if (contenedorMapa) {
    const rejilla = document.getElementById('rejilla-iglesias');
    const permisoUbicacion = document.getElementById('permiso-ubicacion');
    cargarParroquias().then((lista) => {
      if (!lista.length) { rejilla.innerHTML = '<p>No hay parroquias registradas por ahora.</p>'; return; }
      rejilla.innerHTML = lista.map((p) => `
        <a href="iglesia.html?id=${Number(p.id)}" class="tarjeta tarjeta-iglesia" data-id="${Number(p.id)}">
          <div class="tarjeta-foto">${p.fotoUrl ? `<img src="${escaparHtml(p.fotoUrl)}" alt="${escaparHtml(p.nombre)}">` : ''}</div>
          <span class="tarjeta-icono">${escaparHtml(siglasParroquia(p.nombre))}</span>
          <h3>${escaparHtml(p.nombre)}</h3>
          <p><span class="distancia-real"></span>${escaparHtml(p.direccion)} — ${escaparHtml(p.sacramentos.join(', '))}</p>
        </a>`).join('');

      if (typeof L === 'undefined') return; // sin Leaflet (por ejemplo, sin acceso a internet) se muestran solo las tarjetas
      const conCoordenadas = lista.filter((p) => p.lat !== null && p.lng !== null);
      const centro = conCoordenadas.length
        ? [conCoordenadas.reduce((a, p) => a + p.lat, 0) / conCoordenadas.length, conCoordenadas.reduce((a, p) => a + p.lng, 0) / conCoordenadas.length]
        : [-6.7714, -79.8409];
      const mapa = L.map('mapa-iglesias').setView(centro, 14);
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
        maxZoom: 19
      }).addTo(mapa);

      const iconoUsuario = L.divIcon({ className: 'marcador-usuario', iconSize: [18, 18] });
      let marcadorUsuario = null;
      const marcadores = {};

      function pintarIglesias(latRef, lngRef) {
        conCoordenadas.forEach((p) => {
          const dist = latRef !== null ? distanciaKm(latRef, lngRef, p.lat, p.lng).toFixed(1) + ' km' : null;
          if (!marcadores[p.id]) marcadores[p.id] = L.marker([p.lat, p.lng]).addTo(mapa);
          marcadores[p.id].bindPopup(`
            <div class="popup-iglesia">
              <h4>${escaparHtml(p.nombre)}</h4>
              <p>${dist ? 'A ' + dist + ' — ' : ''}${escaparHtml(p.sacramentos.join(', '))}</p>
              <a href="iglesia.html?id=${Number(p.id)}">Ver ficha completa →</a>
            </div>`);
          const etiqueta = document.querySelector(`.tarjeta-iglesia[data-id="${Number(p.id)}"] .distancia-real`);
          if (etiqueta) etiqueta.textContent = dist ? `A ${dist} — ` : '';
        });
      }
      pintarIglesias(null, null);

      function activarUbicacionReal() {
        pedirUbicacion(
          (lat, lng) => {
            if (marcadorUsuario) marcadorUsuario.setLatLng([lat, lng]);
            else marcadorUsuario = L.marker([lat, lng], { icon: iconoUsuario }).addTo(mapa);
            marcadorUsuario.bindPopup('<div class="popup-iglesia"><h4>Tú estás aquí</h4></div>');
            mapa.setView([lat, lng], 14);
            pintarIglesias(lat, lng);
            if (permisoUbicacion) permisoUbicacion.style.display = 'none';
          },
          () => { if (permisoUbicacion) permisoUbicacion.querySelector('p').textContent = 'No pudimos acceder a tu ubicación. Mostramos las iglesias sin distancia.'; }
        );
      }
      const btn = document.getElementById('btn-permiso-ubicacion');
      if (btn) btn.addEventListener('click', activarUbicacionReal);
    });
  }

  /* ---------- ficha de iglesia (iglesia.html?id=N): todo desde la base de datos ---------- */
  const fichaIglesia = document.getElementById('ficha-iglesia');
  if (fichaIglesia) {
    const idPedido = new URLSearchParams(window.location.search).get('id');
    cargarParroquias().then((lista) => {
      const p = lista.find((x) => String(x.id) === idPedido) || lista[0];
      if (!p) { document.getElementById('nombre-iglesia').textContent = 'No encontramos esta parroquia'; return; }

      document.title = `Camino a la Iluminación — ${p.nombre}`;
      document.getElementById('nombre-iglesia').textContent = p.nombre;
      document.getElementById('direccion-iglesia').textContent = p.direccion;
      const foto = document.getElementById('foto-iglesia');
      const fotoInterior = document.getElementById('foto-interior');
      if (p.fotoUrl) { foto.src = p.fotoUrl; foto.alt = p.nombre; } else { foto.closest('figure').style.display = 'none'; }
      if (p.fotoInteriorUrl) fotoInterior.src = p.fotoInteriorUrl; else fotoInterior.closest('figure').style.display = 'none';
      document.getElementById('sacramentos-iglesia').textContent = p.sacramentos.length ? p.sacramentos.join(', ') + '.' : 'Consulta en la parroquia.';
      document.getElementById('tramites-iglesia').textContent = p.horarioTramites || 'Consulta en la parroquia.';
      document.getElementById('enlace-reservar').href = 'reservas.html?parroquia=' + Number(p.id);

      fetch('/api/horarios?parroquia=' + encodeURIComponent(p.id), { cache: 'no-cache' })
        .then((r) => (r.ok ? r.json() : Promise.reject()))
        .then((horarios) => {
          const misas = agruparHorarios(horarios.filter((h) => h.tipo === 'misa'));
          document.getElementById('horario-misas').innerHTML = misas.length
            ? misas.map((f) => `${escaparHtml(f.etiqueta)}: ${escaparHtml(f.detalle)}`).join('<br>')
            : 'Aún no publicado.';
        })
        .catch(() => { document.getElementById('horario-misas').textContent = 'No se pudo cargar el horario.'; });

      const mapaChico = document.getElementById('mapa-chico');
      if (mapaChico && typeof L !== 'undefined' && p.lat !== null && p.lng !== null) {
        const mapa = L.map('mapa-chico').setView([p.lat, p.lng], 15);
        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { attribution: '&copy; OpenStreetMap', maxZoom: 19 }).addTo(mapa);
        L.marker([p.lat, p.lng]).addTo(mapa)
          .bindPopup(`<div class="popup-iglesia"><h4>${escaparHtml(p.nombre)}</h4></div>`).openPopup();
      }
    });
  }
});

/* ---------- panel admin: tablas "confirmar / rechazar con motivo" (reservas y documentos) ---------- */
function montarPanelResolucion(cfg) {
  const tbody = document.getElementById(cfg.tbodyId);
  if (!tbody) return; // la tabla no existe en esta página
  const nota = document.getElementById(cfg.notaId);

  const cargar = () => fetch(cfg.url, { cache: 'no-store', credentials: 'same-origin' })
    .then((r) => r.json().catch(() => ({})).then((data) => ({ ok: r.ok, data })))
    .then(({ ok, data }) => {
      if (!ok) { nota.textContent = data.error || cfg.errorCarga; tbody.innerHTML = ''; return; }
      nota.textContent = data.length ? '' : cfg.vacio;
      tbody.innerHTML = data.map((r) => `
        <tr data-id="${Number(r.id)}">
          ${cfg.celdas(r)}
          <td class="fila-accion">
            <button class="boton-mini aprobar" data-accion="confirmar">${cfg.etiquetaAprobar}</button>
            <button class="boton-mini rechazar" data-accion="pedir-motivo">Rechazar</button>
          </td>
        </tr>`).join('');
    })
    .catch(() => { nota.textContent = 'No se pudo conectar con el servidor.'; });

  const enviar = (id, estado, motivo) => {
    const datos = new URLSearchParams({ id, estado });
    if (motivo) datos.append('motivo', motivo);
    return fetch(cfg.url, { method: 'POST', body: datos, credentials: 'same-origin' })
      .then((r) => r.json().catch(() => ({})).then((data) => ({ ok: r.ok, data })))
      .then(({ ok, data }) => cargar().then(() => { if (!ok) nota.textContent = data.error || cfg.errorAccion; }))
      .catch(() => { nota.textContent = 'No se pudo conectar con el servidor.'; });
  };

  tbody.addEventListener('click', (e) => {
    const boton = e.target.closest('[data-accion]');
    if (!boton) return;
    const fila = boton.closest('tr');
    const id = fila.dataset.id;
    const celda = fila.querySelector('.fila-accion');

    if (boton.dataset.accion === 'confirmar') {
      boton.disabled = true;
      enviar(id, cfg.estadoAprobado);
    } else if (boton.dataset.accion === 'pedir-motivo') {
      // El rechazo exige un motivo, que el feligrés verá.
      celda.innerHTML = `
        <input type="text" class="campo-motivo" maxlength="255" placeholder="Motivo del rechazo" aria-label="Motivo del rechazo" style="min-width:200px;padding:6px 8px;">
        <button class="boton-mini rechazar" data-accion="rechazar">Confirmar rechazo</button>
        <button class="boton-mini" data-accion="cancelar">Cancelar</button>`;
      celda.querySelector('input').focus();
    } else if (boton.dataset.accion === 'cancelar') {
      cargar();
    } else if (boton.dataset.accion === 'rechazar') {
      const motivo = celda.querySelector('input').value.trim();
      if (!motivo) { nota.textContent = 'Escribe el motivo del rechazo para continuar.'; celda.querySelector('input').focus(); return; }
      boton.disabled = true;
      enviar(id, cfg.estadoRechazado, motivo);
    }
  });

  cargar();
}

document.addEventListener('DOMContentLoaded', () => {
  montarPanelResolucion({
    tbodyId: 'tabla-reservas-admin', notaId: 'nota-reservas-admin', url: '/api/admin/reservas',
    vacio: 'No hay solicitudes de reserva pendientes.', errorCarga: 'No se pudieron cargar las reservas.', errorAccion: 'No se pudo actualizar la reserva.',
    etiquetaAprobar: 'Confirmar', estadoAprobado: 'confirmada', estadoRechazado: 'rechazada',
    celdas: (r) => {
      const [clase, texto] = ESTADOS_RESERVA[r.estado] || ['pendiente', escaparHtml(r.estado)];
      const extra = [r.contacto ? `Contacto: ${escaparHtml(r.contacto)}` : '', r.notas ? `Notas: ${escaparHtml(r.notas)}` : '']
        .filter(Boolean).map((t) => `<br><small>${t}</small>`).join('');
      return `<td>${escaparHtml(r.nombreSolicitante)}${extra}</td>
          <td>${escaparHtml(NOMBRES_SACRAMENTO[r.sacramento] || r.sacramento)}</td>
          <td>${formatearFecha(r.fecha)}</td>
          <td><span class="insignia ${clase}">${texto}</span></td>`;
    },
  });

  montarPanelResolucion({
    tbodyId: 'tabla-documentos-admin', notaId: 'nota-documentos-admin', url: '/api/admin/documentos',
    vacio: 'No hay documentos pendientes de revisión.', errorCarga: 'No se pudieron cargar los documentos.', errorAccion: 'No se pudo actualizar el documento.',
    etiquetaAprobar: 'Validar', estadoAprobado: 'validado', estadoRechazado: 'rechazado',
    celdas: (d) => {
      const tramite = [NOMBRES_SACRAMENTO[d.sacramento] || d.sacramento, d.solicitante, d.fechaReserva ? formatearFecha(d.fechaReserva) : '']
        .filter(Boolean).map(escaparHtml).join(' · ');
      return `<td>${escaparHtml(d.feligres)}</td>
          <td><a href="/api/documentos/archivo?id=${Number(d.id)}" target="_blank" rel="noopener">${escaparHtml(d.nombre)}</a></td>
          <td>${tramite}</td>
          <td>${formatearFecha(d.fechaEnvio)}</td>
          <td><span class="insignia pendiente">Pendiente</span></td>`;
    },
  });
});

/* ---------- panel admin: avisos y horarios de SU parroquia (base de datos) ---------- */
document.addEventListener('DOMContentLoaded', () => {
  const TIPOS_AVISO = { cambio_horario: 'Cambio de horario', santa_cena: 'Santa Cena', ayuno: 'Ayuno y abstinencia', vigilia: 'Vigilia', celebracion: 'Celebración especial', otro: 'Otro' };
  const leer = (r) => r.json().catch(() => ({})).then((data) => ({ ok: r.ok, data }));

  /* ----- avisos ----- */
  const formAviso = document.getElementById('form-aviso');
  if (formAviso) {
    const tbody = document.getElementById('tabla-avisos-admin');
    const nota = document.getElementById('nota-avisos-admin');
    const mensaje = document.getElementById('mensaje-aviso');

    const cargar = () => fetch('/api/admin/avisos', { cache: 'no-store', credentials: 'same-origin' })
      .then(leer)
      .then(({ ok, data }) => {
        if (!ok) { tbody.innerHTML = ''; nota.textContent = data.error || 'No se pudieron cargar los avisos.'; return; }
        nota.textContent = data.length ? '' : 'Todavía no has publicado avisos.';
        tbody.innerHTML = data.map((a) => `
          <tr data-id="${Number(a.id)}">
            <td>${escaparHtml(etiquetaFecha(a.fechaEvento) || '—')}</td>
            <td>${escaparHtml(a.titulo)}${a.urgente ? ' <span class="insignia rechazado">Urgente</span>' : ''}</td>
            <td>${escaparHtml(TIPOS_AVISO[a.tipo] || a.tipo)}</td>
            <td class="fila-accion"><button class="boton-mini rechazar" data-accion="eliminar-aviso">Eliminar</button></td>
          </tr>`).join('');
      })
      .catch(() => { nota.textContent = 'No se pudo conectar con el servidor.'; });

    formAviso.addEventListener('submit', (e) => {
      e.preventDefault();
      const datos = new URLSearchParams({
        tipo: document.getElementById('tipo-aviso').value,
        titulo: document.getElementById('titulo-aviso').value.trim(),
        fecha: document.getElementById('fecha-aviso').value,
        descripcion: document.getElementById('descripcion-aviso').value.trim(),
        urgente: document.getElementById('urgente-aviso').checked ? 'true' : 'false',
      });
      mensaje.classList.add('visible');
      mensaje.style.color = '';
      mensaje.textContent = 'Publicando…';
      fetch('/api/admin/avisos', { method: 'POST', body: datos, credentials: 'same-origin' })
        .then(leer)
        .then(({ ok, data }) => {
          if (ok) {
            mensaje.textContent = 'Aviso publicado. Ya lo ven los feligreses de tu parroquia.';
            formAviso.reset();
            cargar();
          } else {
            mensaje.style.color = 'red';
            mensaje.textContent = data.error || 'No se pudo publicar el aviso.';
          }
        })
        .catch(() => { mensaje.style.color = 'red'; mensaje.textContent = 'No se pudo conectar con el servidor.'; });
    });

    tbody.addEventListener('click', (e) => {
      const boton = e.target.closest('[data-accion="eliminar-aviso"]');
      if (!boton) return;
      if (!window.confirm('¿Eliminar este aviso? Dejará de verse para los feligreses.')) return;
      boton.disabled = true;
      fetch('/api/admin/avisos?id=' + encodeURIComponent(boton.closest('tr').dataset.id), { method: 'DELETE', credentials: 'same-origin' })
        .then(leer)
        .then(({ ok, data }) => cargar().then(() => { if (!ok) nota.textContent = data.error || 'No se pudo eliminar el aviso.'; }))
        .catch(() => { nota.textContent = 'No se pudo conectar con el servidor.'; });
    });

    cargar();
  }

  /* ----- horarios ----- */
  const formHorario = document.getElementById('form-horario');
  if (formHorario) {
    const tbody = document.getElementById('tabla-horarios-admin');
    const nota = document.getElementById('nota-horarios-admin');
    const mensaje = document.getElementById('mensaje-horarios');
    const campoId = document.getElementById('h-id');
    const botonGuardar = document.getElementById('h-guardar');
    const botonCancelar = document.getElementById('h-cancelar');
    let horarios = [];

    const salirDeEdicion = () => {
      formHorario.reset();
      campoId.value = '';
      botonGuardar.textContent = 'Agregar horario';
      botonCancelar.style.display = 'none';
    };

    const cargar = () => fetch('/api/admin/horarios', { cache: 'no-store', credentials: 'same-origin' })
      .then(leer)
      .then(({ ok, data }) => {
        if (!ok) { tbody.innerHTML = ''; nota.textContent = data.error || 'No se pudieron cargar los horarios.'; return; }
        horarios = data;
        nota.textContent = data.length ? '' : 'Todavía no has registrado horarios.';
        tbody.innerHTML = data.map((h) => `
          <tr data-id="${Number(h.id)}">
            <td>${h.tipo === 'misa' ? 'Misa' : 'Actividad'}</td>
            <td>${escaparHtml(NOMBRES_DIA[h.dia] || h.dia)}</td>
            <td>${escaparHtml(h.hora)}</td>
            <td class="fila-accion">
              <button class="boton-mini" data-accion="editar-horario">Editar</button>
              <button class="boton-mini rechazar" data-accion="eliminar-horario">Eliminar</button>
            </td>
          </tr>`).join('');
      })
      .catch(() => { nota.textContent = 'No se pudo conectar con el servidor.'; });

    formHorario.addEventListener('submit', (e) => {
      e.preventDefault();
      const datos = new URLSearchParams({
        dia: document.getElementById('h-dia').value,
        tipo: document.getElementById('h-tipo').value,
        hora: document.getElementById('h-hora').value.trim(),
      });
      if (campoId.value) datos.append('id', campoId.value);
      mensaje.classList.add('visible');
      mensaje.style.color = '';
      mensaje.textContent = 'Guardando…';
      fetch('/api/admin/horarios', { method: 'POST', body: datos, credentials: 'same-origin' })
        .then(leer)
        .then(({ ok, data }) => {
          if (ok) {
            mensaje.textContent = 'Horario guardado. Ya se ve en la pantalla de horarios de tu parroquia.';
            salirDeEdicion();
            cargar();
          } else {
            mensaje.style.color = 'red';
            mensaje.textContent = data.error || 'No se pudo guardar el horario.';
          }
        })
        .catch(() => { mensaje.style.color = 'red'; mensaje.textContent = 'No se pudo conectar con el servidor.'; });
    });

    botonCancelar.addEventListener('click', salirDeEdicion);

    tbody.addEventListener('click', (e) => {
      const boton = e.target.closest('[data-accion]');
      if (!boton) return;
      const id = boton.closest('tr').dataset.id;
      if (boton.dataset.accion === 'editar-horario') {
        const h = horarios.find((x) => String(x.id) === id);
        if (!h) return;
        campoId.value = String(h.id);
        document.getElementById('h-dia').value = h.dia;
        document.getElementById('h-tipo').value = h.tipo;
        document.getElementById('h-hora').value = h.hora;
        botonGuardar.textContent = 'Guardar cambios';
        botonCancelar.style.display = '';
        document.getElementById('h-hora').focus();
      } else if (boton.dataset.accion === 'eliminar-horario') {
        if (!window.confirm('¿Eliminar este horario?')) return;
        boton.disabled = true;
        fetch('/api/admin/horarios?id=' + encodeURIComponent(id), { method: 'DELETE', credentials: 'same-origin' })
          .then(leer)
          .then(({ ok, data }) => { if (campoId.value === id) salirDeEdicion(); return cargar().then(() => { if (!ok) nota.textContent = data.error || 'No se pudo eliminar el horario.'; }); })
          .catch(() => { nota.textContent = 'No se pudo conectar con el servidor.'; });
      }
    });

    salirDeEdicion();
    cargar();
  }
});

/* ---------- panel admin: sacramentos que ofrece la parroquia ---------- */
document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('form-sacramentos');
  if (!form) return;
  const lista = document.getElementById('lista-sacramentos-admin');
  const mensaje = document.getElementById('mensaje-sacramentos');
  const leer = (r) => r.json().catch(() => ({})).then((data) => ({ ok: r.ok, data }));

  fetch('/api/admin/sacramentos', { cache: 'no-store', credentials: 'same-origin' })
    .then(leer)
    .then(({ ok, data }) => {
      if (!ok) { lista.textContent = data.error || 'No se pudieron cargar los sacramentos.'; return; }
      lista.innerHTML = data.map((sc) => `
        <label style="display:flex;gap:10px;align-items:center;">
          <input type="checkbox" value="${escaparHtml(sc.codigo)}" ${sc.ofrece ? 'checked' : ''}>
          <span>${escaparHtml(sc.nombre)}</span>
        </label>`).join('');
    })
    .catch(() => { lista.textContent = 'No se pudo conectar con el servidor.'; });

  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const codigos = [...lista.querySelectorAll('input[type="checkbox"]:checked')].map((c) => c.value);
    mensaje.classList.add('visible');
    mensaje.style.color = '';
    mensaje.textContent = 'Guardando…';
    fetch('/api/admin/sacramentos', { method: 'POST', body: new URLSearchParams({ codigos: codigos.join(',') }), credentials: 'same-origin' })
      .then(leer)
      .then(({ ok, data }) => {
        if (ok) mensaje.textContent = codigos.length ? 'Guardado. Los feligreses ya ven tu parroquia para esos sacramentos.' : 'Guardado. Tu parroquia no aparecerá al reservar ningún sacramento.';
        else { mensaje.style.color = 'red'; mensaje.textContent = data.error || 'No se pudo guardar.'; }
      })
      .catch(() => { mensaje.style.color = 'red'; mensaje.textContent = 'No se pudo conectar con el servidor.'; });
  });
});

/* ---------- panel admin: reportes reales y descarga a Excel ---------- */
document.addEventListener('DOMContentLoaded', () => {
  const tbodyTramites = document.getElementById('tabla-reporte-tramites');
  if (!tbodyTramites) return; // solo existe en paginas/admin/reportes.html
  const selectMes = document.getElementById('reporte-mes');
  const enlaceTramites = document.getElementById('descargar-tramites');
  const leer = (r) => r.json().catch(() => ({})).then((data) => ({ ok: r.ok, data }));
  const pedir = (query) => fetch('/api/admin/reportes?' + query, { cache: 'no-store', credentials: 'same-origin' }).then(leer);

  // Meses: "todos" y los últimos 12 (value AAAA-MM; <input type="month"> no existe en todos los navegadores).
  const hoy = new Date();
  const opciones = ['<option value="">Todos los meses</option>'];
  for (let i = 0; i < 12; i++) {
    const d = new Date(hoy.getFullYear(), hoy.getMonth() - i, 1);
    const valor = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    const etiqueta = d.toLocaleDateString('es-PE', { month: 'long', year: 'numeric' });
    opciones.push(`<option value="${valor}">${escaparHtml(etiqueta.charAt(0).toUpperCase() + etiqueta.slice(1))}</option>`);
  }
  selectMes.innerHTML = opciones.join('');

  const filaVacia = (columnas, texto) => `<tr><td colspan="${columnas}">${escaparHtml(texto)}</td></tr>`;

  const cargarTramites = () => {
    const mes = selectMes.value;
    enlaceTramites.href = '/api/admin/reportes?tipo=tramites&formato=xlsx' + (mes ? '&mes=' + encodeURIComponent(mes) : '');
    return pedir('tipo=tramites' + (mes ? '&mes=' + encodeURIComponent(mes) : '')).then(({ ok, data }) => {
      const nota = document.getElementById('nota-reporte-tramites');
      if (!ok) { tbodyTramites.innerHTML = ''; nota.textContent = data.error || 'No se pudo generar el reporte.'; return; }
      nota.textContent = '';
      const total = data.reduce((t, f) => ({ s: t.s + f.solicitudes, c: t.c + f.confirmadas, p: t.p + f.pendientes, r: t.r + f.rechazadas }), { s: 0, c: 0, p: 0, r: 0 });
      tbodyTramites.innerHTML = data.map((f) => `<tr><td>${escaparHtml(f.sacramento)}</td><td>${f.solicitudes}</td><td>${f.confirmadas}</td><td>${f.pendientes}</td><td>${f.rechazadas}</td></tr>`).join('')
        + `<tr class="fila-total"><td><strong>Total</strong></td><td><strong>${total.s}</strong></td><td><strong>${total.c}</strong></td><td><strong>${total.p}</strong></td><td><strong>${total.r}</strong></td></tr>`;
    }).catch(() => { document.getElementById('nota-reporte-tramites').textContent = 'No se pudo conectar con el servidor.'; });
  };

  const cargarDocumentos = () => pedir('tipo=documentos').then(({ ok, data }) => {
    const tbody = document.getElementById('tabla-reporte-documentos');
    const nota = document.getElementById('nota-reporte-documentos');
    if (!ok) { tbody.innerHTML = ''; nota.textContent = data.error || 'No se pudo generar el reporte.'; return; }
    nota.textContent = data.length ? '' : 'No hay documentos pendientes de revisión.';
    tbody.innerHTML = data.map((f) => `<tr><td>${escaparHtml(f.feligres)}</td><td>${escaparHtml(f.documento)}</td>
      <td>${escaparHtml([NOMBRES_SACRAMENTO[f.sacramento] || f.sacramento, f.solicitante].filter(Boolean).join(' · '))}</td>
      <td>${formatearFecha(f.fechaEnvio)}</td><td>${Number(f.diasEspera)}</td></tr>`).join('');
  }).catch(() => { document.getElementById('nota-reporte-documentos').textContent = 'No se pudo conectar con el servidor.'; });

  const cargarAgenda = () => pedir('tipo=agenda').then(({ ok, data }) => {
    const tbody = document.getElementById('tabla-reporte-agenda');
    const nota = document.getElementById('nota-reporte-agenda');
    if (!ok) { tbody.innerHTML = ''; nota.textContent = data.error || 'No se pudo generar el reporte.'; return; }
    nota.textContent = data.length ? '' : 'No hay celebraciones confirmadas próximas.';
    tbody.innerHTML = data.map((f) => `<tr><td>${formatearFecha(f.fecha)}</td><td>${escaparHtml(f.sacramento)}</td><td>${escaparHtml(f.solicitante)}</td><td>${escaparHtml(f.contacto)}</td></tr>`).join('');
  }).catch(() => { document.getElementById('nota-reporte-agenda').textContent = 'No se pudo conectar con el servidor.'; });

  selectMes.addEventListener('change', cargarTramites);
  cargarTramites();
  cargarDocumentos();
  cargarAgenda();
});
