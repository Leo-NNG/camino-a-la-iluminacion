// Camino a la Iluminación — cancelar y reprogramar una solicitud desde "Mis solicitudes" (reservas.html).
// main.js dibuja la tabla y los botones (data-accion); aquí se abre un panel de confirmación y se llama al servidor,
// que vuelve a validar todo (dueño, estado, anticipación, cupo). Todo texto del servidor se muestra con textContent.
(function () {
  'use strict';

  const $ = (id) => document.getElementById(id);
  const tbody = $('tabla-mis-reservas');
  const panel = $('panel-gestion');
  if (!tbody || !panel) return;

  const titulo = $('gestion-titulo'), texto = $('gestion-texto'), mensaje = $('gestion-mensaje');
  const campoMotivo = $('gestion-campo-motivo'), motivo = $('gestion-motivo');
  const campoFecha = $('gestion-campo-fecha'), fecha = $('gestion-fecha'), lista = $('gestion-fecha-lista'), ayudaFecha = $('gestion-fecha-ayuda');
  const confirmar = $('gestion-confirmar'), volver = $('gestion-volver');
  let actual = null; // { accion, id, parroquia, sacramento, fechaActual }
  let consulta = 0;

  function avisar(t, error) {
    mensaje.className = 'mensaje-confirmacion visible';
    mensaje.style.color = error ? 'red' : '';
    mensaje.textContent = t;
  }

  function iso(d) {
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }

  function cerrar() {
    panel.hidden = true;
    actual = null;
    consulta++;
    mensaje.className = 'mensaje-confirmacion';
    motivo.value = '';
    fecha.value = '';
    confirmar.disabled = false;
  }

  function abrir(datos) {
    actual = datos;
    consulta++;
    mensaje.className = 'mensaje-confirmacion';
    confirmar.disabled = false;
    campoMotivo.hidden = datos.accion !== 'cancelar-reserva';
    campoFecha.hidden = datos.accion !== 'reprogramar-reserva';
    if (datos.accion === 'cancelar-reserva') {
      titulo.textContent = 'Cancelar solicitud';
      texto.textContent = 'La fecha quedará libre para otras personas y no podrás recuperarla. Los documentos que subiste se conservan.';
      confirmar.textContent = 'Sí, cancelar la solicitud';
    } else {
      titulo.textContent = 'Reprogramar solicitud';
      texto.textContent = 'Elige la nueva fecha. La parroquia volverá a validarla; tus documentos y tu pago se conservan.';
      confirmar.textContent = 'Cambiar la fecha';
      cargarFechas(datos);
    }
    panel.hidden = false;
    panel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  function cargarFechas(datos) {
    const mia = ++consulta;
    fecha.hidden = true; lista.hidden = true; lista.textContent = '';
    ayudaFecha.textContent = 'Cargando fechas...';
    fetch('/api/fechas-disponibles?parroquia=' + encodeURIComponent(datos.parroquia) + '&sacramento=' + encodeURIComponent(datos.sacramento),
      { cache: 'no-store', credentials: 'same-origin' })
      .then((r) => r.json().catch(() => ({})).then((d) => ({ ok: r.ok, d })))
      .then(({ ok, d }) => {
        if (mia !== consulta) return;
        if (!ok) { ayudaFecha.textContent = d.error || 'No se pudieron cargar las fechas.'; return; }
        if (d.publicadas) {
          // La parroquia publicó fechas: solo vale una de ellas (y que no sea la actual).
          lista.appendChild(new Option('Elige una fecha', ''));
          d.fechas.filter((f) => f.fecha !== datos.fechaActual).forEach((f) => {
            const hora = f.hora ? ' ' + f.hora : '';
            lista.appendChild(new Option(f.fecha.split('-').reverse().join('/') + hora + ' (' + f.libres + ' ' + (f.libres === 1 ? 'lugar' : 'lugares') + ')', f.fecha));
          });
          lista.hidden = false;
          ayudaFecha.textContent = lista.options.length > 1 ? 'Estas son las fechas que la parroquia tiene disponibles.' : 'La parroquia no tiene otras fechas con cupo por ahora.';
        } else {
          const manana = new Date(); manana.setDate(manana.getDate() + 1);
          const limite = new Date(); limite.setFullYear(limite.getFullYear() + 2);
          fecha.min = iso(manana); fecha.max = iso(limite);
          fecha.hidden = false;
          ayudaFecha.textContent = 'Esta parroquia no publicó fechas para este sacramento: propón la que prefieras y la parroquia la confirmará.';
        }
      })
      .catch(() => { ayudaFecha.textContent = 'No se pudo conectar con el servidor.'; });
  }

  tbody.addEventListener('click', (e) => {
    const b = e.target.closest('[data-accion]');
    if (!b || !['cancelar-reserva', 'reprogramar-reserva'].includes(b.dataset.accion)) return;
    abrir({ accion: b.dataset.accion, id: b.dataset.id, parroquia: b.dataset.parroquia, sacramento: b.dataset.sacramento, fechaActual: b.dataset.fecha });
  });

  volver.addEventListener('click', cerrar);

  confirmar.addEventListener('click', () => {
    if (!actual) return;
    const datos = new URLSearchParams({ id_reserva: actual.id });
    let ruta;
    if (actual.accion === 'cancelar-reserva') {
      ruta = '/api/reservas/cancelar';
      datos.append('motivo', motivo.value);
    } else {
      ruta = '/api/reservas/reprogramar';
      const elegida = lista.hidden ? fecha.value : lista.value;
      if (!elegida) return avisar('Elige la nueva fecha.', true);
      datos.append('fecha', elegida);
    }
    confirmar.disabled = true;
    avisar('Guardando...', false);
    fetch(ruta, { method: 'POST', body: datos, credentials: 'same-origin' })
      .then((r) => r.json().catch(() => ({})).then((d) => ({ ok: r.ok, d })))
      .then(({ ok, d }) => {
        if (!ok) { avisar(d.error || 'No se pudo completar la operación.', true); confirmar.disabled = false; return; }
        avisar(d.mensaje || 'Listo.', false);
        if (typeof cargarMisReservas === 'function') cargarMisReservas();
        setTimeout(cerrar, 2500);
      })
      .catch(() => { avisar('No se pudo conectar con el servidor.', true); confirmar.disabled = false; });
  });
})();
