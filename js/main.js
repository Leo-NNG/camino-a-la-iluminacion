// Prototipo estático — no hay servidor detrás.
// Estas funciones simulan la respuesta del sistema para la demo final.

/* ==================== AVISOS (para las ventanas flotantes) ==================== */
const AVISOS = [
  { fecha: "29 AGO", titulo: "Cambio de horario — misa de la tarde", texto: "La misa de las 7:00 p.m. del viernes se traslada a las 6:00 p.m.", urgente: true },
  { fecha: "01 SEP", titulo: "Inicio de novena", texto: "Novena en honor a la Virgen, todos los días a las 7:00 p.m.", urgente: false },
  { fecha: "05 SEP", titulo: "Vigilia de oración", texto: "Vigilia nocturna desde las 9:00 p.m. hasta la misa de las 6:00 a.m.", urgente: false },
  { fecha: "14 SEP", titulo: "Ayuno y abstinencia", texto: "Confesiones de 4:00 p.m. a 6:00 p.m. en la parroquia.", urgente: true }
];

/* ==================== SESIÓN / AUTENTICACIÓN ==================== */
function obtenerUsuarios() {
  return JSON.parse(localStorage.getItem('cai_usuarios') || '[]');
}
function guardarUsuarios(lista) {
  localStorage.setItem('cai_usuarios', JSON.stringify(lista));
}
function obtenerSesion() {
  return JSON.parse(localStorage.getItem('cai_sesion') || 'null');
}
function iniciarSesion(usuario) {
  localStorage.setItem('cai_sesion', JSON.stringify(usuario));
}
function cerrarSesion() {
  localStorage.removeItem('cai_sesion');
  window.location.reload();
}
function estaEnPaginas() {
  return window.location.pathname.includes('/paginas/');
}
function raiz() {
  return estaEnPaginas() ? '' : 'paginas/';
}

function pintarNavCuenta() {
  const navLinks = document.querySelector('.nav-links');
  if (!navLinks || document.getElementById('nav-cuenta')) return;
  if (document.body.dataset.paginaAuth === 'true') return;

  const sesion = obtenerSesion();
  const li = document.createElement('li');
  li.id = 'nav-cuenta';

  if (sesion) {
    li.innerHTML = `<span class="nav-saludo">Hola, ${sesion.nombre.split(' ')[0]} · <span class="nav-salir" id="btn-salir">Salir</span></span>`;
  } else {
    li.innerHTML = `<a href="${raiz()}login.html" class="nav-cuenta-link">Iniciar sesión</a>`;
  }
  navLinks.appendChild(li);

  const btnSalir = document.getElementById('btn-salir');
  if (btnSalir) btnSalir.addEventListener('click', cerrarSesion);
}

/* Protege una página: si no hay sesión, redirige a login guardando a dónde volver */
function protegerPagina(destinoLogin, nombrePaginaActual) {
  if (!obtenerSesion()) {
    window.location.href = `${destinoLogin}?next=${encodeURIComponent(nombrePaginaActual)}`;
  }
}

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

/* ==================== INICIO ==================== */
document.addEventListener('DOMContentLoaded', () => {

  pintarNavCuenta();

  /* ---------- menú móvil ---------- */
  const toggle = document.getElementById('menu-toggle');
  const navLinks = document.querySelector('.nav-links');
  if (toggle && navLinks) {
    toggle.addEventListener('click', () => navLinks.classList.toggle('abierto'));
  }

  /* ---------- avisos flotantes (toasts) al entrar a la página ---------- */
  if (!sessionStorage.getItem('cai_avisos_vistos')) {
    sessionStorage.setItem('cai_avisos_vistos', '1');
    let contenedor = document.getElementById('toast-contenedor');
    if (!contenedor) {
      contenedor = document.createElement('div');
      contenedor.id = 'toast-contenedor';
      contenedor.className = 'toast-contenedor';
      document.body.appendChild(contenedor);
    }
    let i = 0;
    function mostrarSiguiente() {
      if (i >= AVISOS.length) return;
      const aviso = AVISOS[i];
      const toast = document.createElement('div');
      toast.className = 'toast' + (aviso.urgente ? ' urgente' : '');
      toast.innerHTML = `<span class="toast-fecha">${aviso.fecha}</span><h5>${aviso.titulo}</h5><p>${aviso.texto}</p>`;
      contenedor.appendChild(toast);
      requestAnimationFrame(() => toast.classList.add('mostrar'));
      setTimeout(() => {
        toast.classList.remove('mostrar');
        setTimeout(() => toast.remove(), 350);
      }, 2600);
      i++;
      setTimeout(mostrarSiguiente, 3000);
    }
    setTimeout(mostrarSiguiente, 600);
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

  /* ---------- formulario de registro ---------- */
  const formRegistro = document.getElementById('form-registro');
  if (formRegistro) {
    formRegistro.addEventListener('submit', (e) => {
      e.preventDefault();
      const campos = {
        nombre: document.getElementById('reg-nombre'),
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

      if (!campos.nombre.value.trim()) { marcarError(campos.nombre); valido = false; }
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

      const usuarios = obtenerUsuarios();
      if (usuarios.some((u) => u.correo === campos.correo.value.trim().toLowerCase())) {
        alerta.textContent = 'Ya existe una cuenta registrada con ese correo.';
        alerta.classList.add('visible', 'error');
        return;
      }

      const nuevoUsuario = {
        nombre: campos.nombre.value.trim(),
        correo: campos.correo.value.trim().toLowerCase(),
        telefono: campos.telefono.value.trim(),
        clave: campos.clave.value,
        ubicacion: null
      };
      usuarios.push(nuevoUsuario);
      guardarUsuarios(usuarios);
      iniciarSesion(nuevoUsuario);

      alerta.textContent = 'Cuenta creada. Solicitando tu ubicación para mostrarte las iglesias más cercanas…';
      alerta.classList.add('visible', 'exito');

      pedirUbicacion(
        (lat, lng) => {
          nuevoUsuario.ubicacion = { lat, lng };
          iniciarSesion(nuevoUsuario);
          const lista = obtenerUsuarios().map((u) => u.correo === nuevoUsuario.correo ? nuevoUsuario : u);
          guardarUsuarios(lista);
          setTimeout(() => window.location.href = 'mapa.html', 900);
        },
        () => setTimeout(() => window.location.href = 'mapa.html', 900)
      );
    });
  }

  function marcarError(campo) { campo.closest('.campo').classList.add('con-error'); }

  /* ---------- formulario de login ---------- */
  const formLogin = document.getElementById('form-login');
  if (formLogin) {
    formLogin.addEventListener('submit', (e) => {
      e.preventDefault();
      const correo = document.getElementById('log-correo');
      const clave = document.getElementById('log-clave');
      const alerta = document.getElementById('alerta-login');
      let valido = true;
      [correo, clave].forEach((c) => c.closest('.campo').classList.remove('con-error'));

      if (!correo.value.trim()) { marcarError(correo); valido = false; }
      if (!clave.value.trim()) { marcarError(clave); valido = false; }

      if (!valido) {
        alerta.textContent = 'Completa correo y contraseña para continuar.';
        alerta.className = 'alerta-auth visible error';
        return;
      }

      const usuario = obtenerUsuarios().find(
        (u) => u.correo === correo.value.trim().toLowerCase() && u.clave === clave.value
      );

      if (!usuario) {
        alerta.textContent = 'Correo o contraseña incorrectos.';
        alerta.className = 'alerta-auth visible error';
        return;
      }

      iniciarSesion(usuario);
      const params = new URLSearchParams(window.location.search);
      const siguiente = params.get('next') || 'reservas.html';
      window.location.href = siguiente;
    });
  }

  /* ---------- formulario de reserva (stepper) ---------- */
  const formReserva = document.getElementById('form-reserva');
  if (formReserva) {
    const pasos = document.querySelectorAll('.paso-item');
    const paneles = document.querySelectorAll('.panel-paso');
    let pasoActual = 0;

    const tarjetasSacramento = document.querySelectorAll('.tarjeta-sacramento');
    const campoSacramento = document.getElementById('sacramento');
    tarjetasSacramento.forEach((t) => {
      t.addEventListener('click', () => {
        tarjetasSacramento.forEach((x) => x.classList.remove('elegido'));
        t.classList.add('elegido');
        campoSacramento.value = t.dataset.valor;
      });
    });

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

    formReserva.addEventListener('submit', (e) => {
      e.preventDefault();
      const mensaje = document.getElementById('mensaje-reserva');
      mensaje.classList.add('visible');
      mensaje.textContent = 'Solicitud registrada (simulación). En la versión final esto quedará guardado en la base de datos de la parroquia.';
      formReserva.querySelector('button[type="submit"]').setAttribute('disabled', 'true');
    });
  }

  /* ---------- zona de carga de documentos ---------- */
  const zonaCarga = document.getElementById('zona-carga');
  const inputArchivo = document.getElementById('input-archivo');
  const listaArchivos = document.getElementById('lista-archivos');
  function agregarArchivo(nombre) {
    if (!listaArchivos) return;
    const li = document.createElement('li');
    li.innerHTML = `<span>📄 ${nombre}</span><span class="insignia pendiente">Pendiente</span>`;
    listaArchivos.appendChild(li);
    const mensaje = document.getElementById('mensaje-documento');
    mensaje.classList.add('visible');
    mensaje.textContent = 'Documento recibido (simulación). Aquí se conectará la subida real de archivos.';
  }
  if (zonaCarga) {
    zonaCarga.addEventListener('click', () => inputArchivo && inputArchivo.click());
    zonaCarga.addEventListener('dragover', (e) => { e.preventDefault(); zonaCarga.classList.add('sobre'); });
    zonaCarga.addEventListener('dragleave', () => zonaCarga.classList.remove('sobre'));
    zonaCarga.addEventListener('drop', (e) => {
      e.preventDefault();
      zonaCarga.classList.remove('sobre');
      [...e.dataTransfer.files].forEach((f) => agregarArchivo(f.name));
    });
  }
  if (inputArchivo) {
    inputArchivo.addEventListener('change', (e) => {
      [...e.target.files].forEach((f) => agregarArchivo(f.name));
    });
  }

  /* ---------- buscador de iglesias (mapa.html) ---------- */
  const buscador = document.getElementById('buscador-iglesias');
  if (buscador) {
    buscador.addEventListener('input', (e) => {
      const termino = e.target.value.toLowerCase();
      document.querySelectorAll('.tarjeta-iglesia').forEach((tarjeta) => {
        const texto = tarjeta.textContent.toLowerCase();
        tarjeta.style.display = texto.includes(termino) ? '' : 'none';
      });
    });
  }

  /* ---------- chips de avisos ---------- */
  const chips = document.querySelectorAll('.chip[data-filtro]');
  if (chips.length) {
    chips.forEach((chip) => {
      chip.addEventListener('click', () => {
        chips.forEach((c) => c.classList.remove('activo'));
        chip.classList.add('activo');
        const filtro = chip.dataset.filtro;
        document.querySelectorAll('.aviso').forEach((aviso) => {
          aviso.style.display = (filtro === 'todos' || aviso.classList.contains(filtro)) ? '' : 'none';
        });
      });
    });
  }

  /* ---------- pestañas de día (horarios) ---------- */
  const pestanas = document.querySelectorAll('.pestana-dia');
  if (pestanas.length) {
    pestanas.forEach((p) => {
      p.addEventListener('click', () => {
        pestanas.forEach((x) => x.classList.remove('activo'));
        p.classList.add('activo');
        const dia = p.dataset.dia;
        document.querySelectorAll('.fila-horario').forEach((fila) => {
          fila.style.display = (dia === 'todos' || fila.dataset.dia === dia) ? '' : 'none';
        });
      });
    });
  }

  /* ---------- mapa real con Leaflet: iglesias cercanas + geolocalización ---------- */
  const contenedorMapa = document.getElementById('mapa-iglesias');
  if (contenedorMapa && typeof L !== 'undefined' && typeof IGLESIAS !== 'undefined') {
    const centroChiclayo = [-6.7714, -79.8409];
    const mapa = L.map('mapa-iglesias').setView(centroChiclayo, 14);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      maxZoom: 19
    }).addTo(mapa);

    const iconoUsuario = L.divIcon({ className: 'marcador-usuario', iconSize: [18, 18] });
    let marcadorUsuario = L.marker(centroChiclayo, { icon: iconoUsuario }).addTo(mapa)
      .bindPopup('<div class="popup-iglesia"><h4>Ubicación aproximada</h4></div>');

    const marcadoresIglesia = {};
    function pintarIglesias(latRef, lngRef) {
      IGLESIAS.forEach((ig) => {
        const dist = latRef ? distanciaKm(latRef, lngRef, ig.lat, ig.lng).toFixed(1) + ' km' : ig.distancia;
        if (!marcadoresIglesia[ig.id]) {
          marcadoresIglesia[ig.id] = L.marker([ig.lat, ig.lng]).addTo(mapa);
        }
        marcadoresIglesia[ig.id].bindPopup(`
          <div class="popup-iglesia">
            <h4>${ig.nombre}</h4>
            <p>A ${dist} — ${ig.sacramentos.join(', ')}</p>
            <a href="iglesia.html?id=${ig.id}">Ver ficha completa →</a>
          </div>
        `);
        const tarjeta = document.querySelector(`.tarjeta-iglesia[href*="${ig.id}"] .distancia-real`);
        if (tarjeta) tarjeta.textContent = 'A ' + dist;
      });
    }
    pintarIglesias(null, null);

    const permisoUbicacion = document.getElementById('permiso-ubicacion');
    function activarUbicacionReal() {
      pedirUbicacion(
        (lat, lng) => {
          marcadorUsuario.setLatLng([lat, lng]).setPopupContent('<div class="popup-iglesia"><h4>Tú estás aquí</h4></div>');
          mapa.setView([lat, lng], 14);
          pintarIglesias(lat, lng);
          if (permisoUbicacion) permisoUbicacion.style.display = 'none';
        },
        () => { if (permisoUbicacion) permisoUbicacion.querySelector('p').textContent = 'No pudimos acceder a tu ubicación. Mostrando iglesias con distancia referencial.'; }
      );
    }
    const sesion = obtenerSesion();
    if (sesion && sesion.ubicacion) {
      marcadorUsuario.setLatLng([sesion.ubicacion.lat, sesion.ubicacion.lng]).setPopupContent('<div class="popup-iglesia"><h4>Tú estás aquí</h4></div>');
      mapa.setView([sesion.ubicacion.lat, sesion.ubicacion.lng], 14);
      pintarIglesias(sesion.ubicacion.lat, sesion.ubicacion.lng);
      if (permisoUbicacion) permisoUbicacion.style.display = 'none';
    } else if (permisoUbicacion) {
      const btn = document.getElementById('btn-permiso-ubicacion');
      if (btn) btn.addEventListener('click', activarUbicacionReal);
    }
  }

  /* ---------- mapa chico en ficha de iglesia ---------- */
  const mapaChico = document.getElementById('mapa-chico');
  if (mapaChico && typeof L !== 'undefined' && typeof IGLESIAS !== 'undefined') {
    const params = new URLSearchParams(window.location.search);
    const iglesia = IGLESIAS.find((i) => i.id === params.get('id')) || IGLESIAS[0];
    const mapa = L.map('mapa-chico').setView([iglesia.lat, iglesia.lng], 15);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap',
      maxZoom: 19
    }).addTo(mapa);
    L.marker([iglesia.lat, iglesia.lng]).addTo(mapa)
      .bindPopup(`<div class="popup-iglesia"><h4>${iglesia.nombre}</h4></div>`).openPopup();
  }

  /* ---------- ficha de iglesia dinámica ---------- */
  const fichaIglesia = document.getElementById('ficha-iglesia');
  if (fichaIglesia && typeof IGLESIAS !== 'undefined') {
    const params = new URLSearchParams(window.location.search);
    const iglesia = IGLESIAS.find((i) => i.id === params.get('id')) || IGLESIAS[0];

    document.title = `Camino a la Iluminación — ${iglesia.nombre}`;
    document.getElementById('nombre-iglesia').textContent = iglesia.nombre;
    document.getElementById('direccion-iglesia').textContent = `${iglesia.direccion} — A ${iglesia.distancia} de tu ubicación actual.`;
    document.getElementById('foto-iglesia').src = iglesia.foto;
    document.getElementById('foto-iglesia').alt = iglesia.nombre;
    document.getElementById('foto-interior').src = iglesia.fotoInterior;

    document.getElementById('horario-misas').innerHTML = iglesia.horarioMisas
      .map((h) => `${h.dia}: ${h.detalle}`).join('<br>');
    document.getElementById('sacramentos-iglesia').textContent = iglesia.sacramentos.join(', ') + '.';
    document.getElementById('tramites-iglesia').textContent = iglesia.tramites;
  }
});
