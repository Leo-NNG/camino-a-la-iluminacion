// Prototipo estático — no hay servidor detrás.
// Estas funciones simulan la respuesta del sistema para la demo final.

/* ==================== AVISOS (para las ventanas flotantes) ==================== */
const AVISOS = [
  { fecha: "29 AGO", titulo: "Cambio de horario — misa de la tarde", texto: "La misa de las 7:00 p.m. del viernes se traslada a las 6:00 p.m.", urgente: true },
  { fecha: "01 SEP", titulo: "Inicio de novena", texto: "Novena en honor a la Virgen, todos los días a las 7:00 p.m.", urgente: false },
  { fecha: "05 SEP", titulo: "Vigilia de oración", texto: "Vigilia nocturna desde las 9:00 p.m. hasta la misa de las 6:00 a.m.", urgente: false },
  { fecha: "14 SEP", titulo: "Ayuno y abstinencia", texto: "Confesiones de 4:00 p.m. a 6:00 p.m. en la parroquia.", urgente: true }
];

/* ==================== ALMACÉN COMPARTIDO (panel admin ↔ pantallas del feligrés) ====================
   Usa localStorage para que las acciones del panel de administrador (aprobar un
   documento, publicar un aviso, cambiar un horario) se reflejen en las pantallas
   del feligrés dentro del MISMO navegador. Simulación para la demo: en la versión
   final esto lo haría la base de datos real. */
const Almacen = {
  get(clave, porDefecto) {
    try {
      const guardado = localStorage.getItem('cai_' + clave);
      return guardado ? JSON.parse(guardado) : JSON.parse(JSON.stringify(porDefecto));
    } catch (e) { return porDefecto; }
  },
  set(clave, valor) {
    try { localStorage.setItem('cai_' + clave, JSON.stringify(valor)); } catch (e) {}
  },
  reiniciar() {
    ['documentos', 'avisos_nuevos', 'horarios'].forEach((k) => {
      try { localStorage.removeItem('cai_' + k); } catch (e) {}
    });
  },
};

const DOCUMENTOS_SEMILLA = [
  { id: 'doc1', feligres: 'María Fernández', documento: 'Partida de nacimiento', tramite: 'Bautizo', fecha: '12/08/2026', estado: 'validado' },
  { id: 'doc2', feligres: 'Jorge Ramírez', documento: 'DNI del padrino', tramite: 'Bautizo', fecha: '18/08/2026', estado: 'pendiente' },
  { id: 'doc3', feligres: 'Lucía y Pedro Vega', documento: 'Certificado prematrimonial', tramite: 'Matrimonio', fecha: '20/08/2026', estado: 'pendiente' },
  { id: 'doc4', feligres: 'Ana Torres', documento: 'Constancia de catequesis', tramite: 'Primera Comunión', fecha: '22/08/2026', estado: 'pendiente' },
];

function insigniaHtml(estado) {
  const etiquetas = { validado: 'Validado', pendiente: 'Pendiente', rechazado: 'Rechazado' };
  return `<span class="insignia ${estado}">${etiquetas[estado]}</span>`;
}

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

  /* ---------- formulario de registro (Conectado a MySQL via Servlet) ---------- */
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

        alerta.textContent = 'Guardando usuario en la base de datos...';
        alerta.classList.add('visible', 'exito');

        // Preparamos los datos para enviarlos al Servlet RegistroServlet (/api/registro)
        const datos = new URLSearchParams();
        datos.append('nombre', campos.nombre.value.trim());
        datos.append('apellido', ''); // Si tu formulario no pide apellido por separado, lo mandamos vacío o ajustas el input
        datos.append('correo', campos.correo.value.trim().toLowerCase());
        datos.append('telefono', campos.telefono.value.trim());
        datos.append('contrasena', campos.clave.value);
        datos.append('ubicacion', 'Chiclayo'); // Ubicación por defecto para la demo

        fetch('/api/registro', {
            method: 'POST',
            body: datos
        })
        .then(response => {
            if (response.ok) {
                alerta.textContent = '¡Cuenta creada y guardada en MySQL con éxito!';
                setTimeout(() => window.location.href = 'login.html', 1200);
            } else {
                alerta.textContent = 'Error al registrar la cuenta en el servidor.';
                alerta.className = 'alerta-auth visible error';
            }
        })
        .catch(error => {
            console.error("Error en la petición:", error);
            alerta.textContent = 'Error de conexión con el servidor Java.';
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

        const datos = new URLSearchParams();
        datos.append('correo', correo.value.trim().toLowerCase());
        datos.append('contrasena', clave.value);

        fetch('/api/login', {
            method: 'POST',
            body: datos
        })
        .then(response => response.json())
        .then(data => {
            if (data.exito) {
                // Guardamos los datos básicos en el localStorage del navegador solo para pintar el saludo ("Hola, [Nombre]")
                iniciarSesion({ nombre: data.nombre, rol: data.rol, correo: correo.value.trim() });

                const params = new URLSearchParams(window.location.search);
                const siguiente = params.get('next') || 'reservas.html';
                window.location.href = siguiente;
            } else {
                alerta.textContent = data.error || 'Correo o contraseña incorrectos.';
                alerta.className = 'alerta-auth visible error';
            }
        })
        .catch(error => {
            console.error("Error en la petición:", error);
            alerta.textContent = 'Error de conexión con el servidor Java.';
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
          mensaje.textContent = 'Enviando a la base de datos...';
          mensaje.classList.add('visible');
          const botonSubmit = formReserva.querySelector('button[type="submit"]');
          botonSubmit.setAttribute('disabled', 'true');

          // 1. Capturar los datos del formulario HTML
          const datos = new URLSearchParams();
          // Asegúrate de que los IDs coincidan con los de tu HTML (reservas.html)
          datos.append('sacramento', document.getElementById('sacramento').value);
          datos.append('nombre_solicitante', document.getElementById('nombre').value);
          datos.append('fecha', document.getElementById('fecha').value);
          datos.append('notas', document.getElementById('notas').value);

          // 2. Enviar los datos al Servlet de Java
          fetch('/api/reservas', {
              method: 'POST',
              body: datos
          })
          .then(respuesta => {
              if (respuesta.ok) {
                  mensaje.textContent = '¡Éxito! Tu reserva ya está guardada en MySQL.';
                  mensaje.style.color = "green";
              } else {
                  mensaje.textContent = 'Error al guardar la reserva en el servidor.';
                  mensaje.style.color = "red";
              }
          })
          .catch(error => {
              console.error("Error en la petición:", error);
              mensaje.textContent = 'Error de conexión con el servidor Java.';
          })
          .finally(() => {
              botonSubmit.removeAttribute('disabled');
          });
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
    mensaje.textContent = 'Documento recibido (simulación). Ya aparece como pendiente en el panel de administración.';

    // Se agrega como trámite pendiente para que el administrador lo vea y lo valide.
    const sesion = obtenerSesion();
    const documentos = Almacen.get('documentos', DOCUMENTOS_SEMILLA);
    documentos.unshift({
      id: 'doc' + Date.now(),
      feligres: sesion ? sesion.nombre : 'Feligrés (invitado)',
      documento: nombre,
      tramite: 'Por confirmar',
      fecha: new Date().toLocaleDateString('es-PE'),
      estado: 'pendiente',
    });
    Almacen.set('documentos', documentos);
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

  /* ---------- tabla de documentos: feligrés (solo lectura) ---------- */
  const tablaDocFeligres = document.getElementById('tabla-documentos-feligres');
  if (tablaDocFeligres) {
    const documentos = Almacen.get('documentos', DOCUMENTOS_SEMILLA);
    tablaDocFeligres.innerHTML = documentos.map((d) => `
      <tr>
        <td>${d.documento}</td>
        <td>${d.tramite}</td>
        <td>${d.fecha}</td>
        <td>${insigniaHtml(d.estado)}</td>
      </tr>
    `).join('');
  }

  /* ---------- tabla de documentos: administrador (aprobar/rechazar) ---------- */
  const tablaDocAdmin = document.getElementById('tabla-documentos-admin');
  if (tablaDocAdmin) {
    const pintarTablaAdmin = () => {
      const documentos = Almacen.get('documentos', DOCUMENTOS_SEMILLA);
      tablaDocAdmin.innerHTML = documentos.map((d) => {
        const resuelto = d.estado !== 'pendiente';
        return `
          <tr>
            <td>${d.feligres}</td>
            <td>${d.documento}</td>
            <td>${d.tramite}</td>
            <td>${d.fecha}</td>
            <td>${insigniaHtml(d.estado)}</td>
            <td class="fila-accion">
              <button class="boton-mini aprobar" data-id="${d.id}" data-accion="aprobar" ${resuelto ? 'disabled' : ''}>Aprobar</button>
              <button class="boton-mini rechazar" data-id="${d.id}" data-accion="rechazar" ${resuelto ? 'disabled' : ''}>Rechazar</button>
            </td>
          </tr>
        `;
      }).join('');

      tablaDocAdmin.querySelectorAll('[data-accion]').forEach((boton) => {
        boton.addEventListener('click', () => {
          const documentos2 = Almacen.get('documentos', DOCUMENTOS_SEMILLA);
          const item = documentos2.find((d) => d.id === boton.dataset.id);
          if (!item) return;
          item.estado = boton.dataset.accion === 'aprobar' ? 'validado' : 'rechazado';
          Almacen.set('documentos', documentos2);
          pintarTablaAdmin();
        });
      });
    };
    pintarTablaAdmin();
  }

  /* ---------- avisos publicados por el administrador (visibles del lado feligrés) ---------- */
  const contenedorAvisosNuevos = document.getElementById('avisos-nuevos');
  if (contenedorAvisosNuevos) {
    const avisosNuevos = Almacen.get('avisos_nuevos', []);
    contenedorAvisosNuevos.innerHTML = avisosNuevos.map((a) => `
      <div class="aviso ${a.urgente ? 'urgente' : ''}">
        <span class="aviso-fecha">${a.fecha}</span>
        <div>
          <h4>${a.titulo}</h4>
          <p>${a.descripcion}</p>
        </div>
      </div>
    `).join('');
  }

  /* ---------- formulario del admin: publicar aviso ---------- */
  const formAviso = document.getElementById('form-aviso');
  if (formAviso) {
    formAviso.addEventListener('submit', (e) => {
      e.preventDefault();
      const fecha = document.getElementById('fecha-aviso').value;
      const fechaCorta = fecha
        ? new Date(fecha + 'T00:00:00').toLocaleDateString('es-PE', { day: '2-digit', month: 'short' }).toUpperCase().replace('.', '')
        : '';
      const avisosNuevos = Almacen.get('avisos_nuevos', []);
      avisosNuevos.unshift({
        fecha: fechaCorta,
        titulo: document.getElementById('titulo-aviso').value,
        descripcion: document.getElementById('descripcion-aviso').value,
        urgente: document.getElementById('urgente-aviso').checked,
      });
      Almacen.set('avisos_nuevos', avisosNuevos);

      const mensaje = document.getElementById('mensaje-aviso');
      mensaje.classList.add('visible');
      mensaje.textContent = 'Aviso publicado. Ya aparece en la pantalla de avisos del feligrés.';
      formAviso.reset();
    });
  }

  /* ---------- horarios: si el administrador guardó cambios, se reflejan aquí ---------- */
  const elSemana = document.getElementById('horario-semana');
  const elSabado = document.getElementById('horario-sabado');
  const elDomingo = document.getElementById('horario-domingo');
  if (elSemana && elSabado && elDomingo) {
    const horarios = Almacen.get('horarios', null);
    if (horarios) {
      elSemana.textContent = horarios.semana;
      elSabado.textContent = horarios.sabado;
      elDomingo.textContent = horarios.domingo;
    }
  }

  /* ---------- formulario del admin: guardar horarios ---------- */
  const formHorarios = document.getElementById('form-horarios');
  if (formHorarios) {
    formHorarios.addEventListener('submit', (e) => {
      e.preventDefault();
      Almacen.set('horarios', {
        semana: document.getElementById('h-semana').value,
        sabado: document.getElementById('h-sabado').value,
        domingo: document.getElementById('h-domingo').value,
      });
      const mensaje = document.getElementById('mensaje-horarios');
      mensaje.classList.add('visible');
      mensaje.textContent = 'Horarios actualizados. Ya se reflejan en la pantalla de horarios del feligrés.';
    });
  }

  /* ---------- botón de reiniciar datos de demostración ---------- */
  const btnReiniciar = document.getElementById('reiniciar-demo');
  if (btnReiniciar) {
    btnReiniciar.addEventListener('click', () => {
      Almacen.reiniciar();
      btnReiniciar.textContent = 'Datos reiniciados ✓';
      setTimeout(() => { btnReiniciar.textContent = 'Reiniciar datos de demostración'; }, 2000);
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

document.addEventListener("DOMContentLoaded", () => {
    // 1. Consultamos el endpoint del Servlet administrador que creamos
    fetch('/api/admin/reservas')
        .then(response => response.json())
        .then(data => {
            const tbody = document.getElementById('tabla-documentos-admin');

            if (!tbody) return;

            // Limpiamos la tabla por si tuviera datos estáticos
            tbody.innerHTML = '';

            // 2. Recorremos las reservas reales obtenidas de MySQL
            data.forEach(reserva => {
                const fila = document.createElement('tr');

                fila.innerHTML = `
                    <td>${reserva.nombreSolicitante}</td>
                    <td>Solicitud de Sacramento</td>
                    <td>${reserva.sacramento}</td>
                    <td>${reserva.fecha}</td>
                    <td><span class="badge-${reserva.estado}">${reserva.estado}</span></td>
                    <td>
                        <button class="boton-aprobar" onclick="alert('Acción de aprobar para ID: ${reserva.id}')">Aprobar</button>
                        <button class="boton-rechazar" onclick="alert('Acción de rechazar para ID: ${reserva.id}')">Rechazar</button>
                    </td>
                `;

                tbody.appendChild(fila);
            });
        })
        .catch(error => console.error("Error al cargar las reservas del administrador:", error));
});

