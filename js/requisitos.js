// Camino a la Iluminación — requisitos por sacramento.
//   * Admin (admin/requisitos.html): define qué documentos pide su parroquia.
//   * Feligrés (documentos.html): checklist de su solicitud y subida de cada documento al requisito que cumple.
//   * Reserva (reservas.html): qué documentos pide la parroquia antes de enviar la solicitud.
// Todo texto que viene del servidor se muestra con textContent (nunca innerHTML). El servidor valida todo de nuevo.
(function () {
  'use strict';

  const $ = (id) => document.getElementById(id);
  const MAX_BYTES = 5 * 1024 * 1024;
  const ESTADOS = { faltante: 'Falta', pendiente: 'En revisión', validado: 'Validado', rechazado: 'Rechazado' };

  function el(etiqueta, texto, clase) {
    const e = document.createElement(etiqueta);
    if (texto !== undefined && texto !== null) e.textContent = texto;
    if (clase) e.className = clase;
    return e;
  }

  function leer(r) {
    return r.json().catch(() => ({})).then((data) => ({ ok: r.ok, data }));
  }

  function insignia(estado) {
    return el('span', ESTADOS[estado] || estado, 'insignia ' + (estado === 'faltante' ? 'faltante' : estado));
  }

  /* ------------------------------------------------------------------ administración */
  function iniciarAdmin() {
    const form = $('form-requisito');
    if (!form) return;
    const campoId = $('r-id'), selSac = $('r-sacramento'), campoNombre = $('r-nombre'), campoDesc = $('r-descripcion');
    const selObl = $('r-obligatorio'), campoOrden = $('r-orden');
    const botonGuardar = $('r-guardar'), botonCancelar = $('r-cancelar');
    const tbody = $('tabla-requisitos-admin'), nota = $('nota-requisitos-admin'), mensaje = $('mensaje-requisitos');
    let requisitos = [];

    const salirDeEdicion = () => {
      form.reset();
      campoId.value = '';
      selSac.disabled = false;
      botonGuardar.textContent = 'Agregar requisito';
      botonCancelar.style.display = 'none';
    };

    const cargarSacramentos = () => fetch('/api/admin/sacramentos', { cache: 'no-store', credentials: 'same-origin' })
      .then(leer)
      .then(({ ok, data }) => {
        if (!ok) { nota.textContent = data.error || 'No se pudieron cargar los sacramentos.'; return; }
        selSac.textContent = '';
        const ofrecidos = data.filter((s) => s.ofrece);
        if (!ofrecidos.length) selSac.appendChild(new Option('Tu parroquia no ofrece ningún sacramento todavía', ''));
        ofrecidos.forEach((s) => selSac.appendChild(new Option(s.nombre, s.codigo)));
      })
      .catch(() => { nota.textContent = 'No se pudo conectar con el servidor.'; });

    const cargar = () => fetch('/api/admin/requisitos', { cache: 'no-store', credentials: 'same-origin' })
      .then(leer)
      .then(({ ok, data }) => {
        tbody.textContent = '';
        if (!ok) { nota.textContent = data.error || 'No se pudieron cargar los requisitos.'; return; }
        requisitos = data;
        nota.textContent = data.length ? '' : 'Todavía no definiste requisitos: los feligreses subirán documentos sin una lista guía.';
        data.forEach((q) => {
          const tr = document.createElement('tr');
          tr.dataset.id = String(q.id);
          const nombre = el('td', q.nombre);
          if (q.descripcion) { nombre.appendChild(document.createElement('br')); nombre.appendChild(el('small', q.descripcion)); }
          const tipo = el('td');
          tipo.appendChild(el('span', q.obligatorio ? 'Obligatorio' : 'Recomendado', 'insignia ' + (q.obligatorio ? 'pendiente' : 'faltante')));
          const acciones = el('td', null, 'fila-accion');
          const editar = el('button', 'Editar', 'boton-mini'); editar.dataset.accion = 'editar-requisito';
          const borrar = el('button', 'Eliminar', 'boton-mini rechazar'); borrar.dataset.accion = 'eliminar-requisito';
          acciones.append(editar, borrar);
          tr.append(el('td', q.nombreSacramento), el('td', String(q.orden)), nombre, tipo, acciones);
          tbody.appendChild(tr);
        });
      })
      .catch(() => { nota.textContent = 'No se pudo conectar con el servidor.'; });

    function avisar(texto, error) {
      mensaje.classList.add('visible');
      mensaje.style.color = error ? 'red' : '';
      mensaje.textContent = texto;
    }

    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const datos = new URLSearchParams({ nombre: campoNombre.value, descripcion: campoDesc.value, obligatorio: selObl.value, orden: campoOrden.value });
      if (campoId.value) datos.append('id', campoId.value); else datos.append('sacramento', selSac.value);
      avisar('Guardando...', false);
      fetch('/api/admin/requisitos', { method: 'POST', body: datos, credentials: 'same-origin' })
        .then(leer)
        .then(({ ok, data }) => {
          if (ok) { avisar('Requisito guardado. Los feligreses ya lo ven en su lista.', false); salirDeEdicion(); cargar(); }
          else avisar(data.error || 'No se pudo guardar el requisito.', true);
        })
        .catch(() => avisar('No se pudo conectar con el servidor.', true));
    });

    botonCancelar.addEventListener('click', salirDeEdicion);

    tbody.addEventListener('click', (e) => {
      const boton = e.target.closest('[data-accion]');
      if (!boton || boton.disabled) return;
      const id = boton.closest('tr').dataset.id;
      if (boton.dataset.accion === 'editar-requisito') {
        const q = requisitos.find((x) => String(x.id) === id);
        if (!q) return;
        campoId.value = String(q.id);
        selSac.textContent = '';
        selSac.appendChild(new Option(q.nombreSacramento, q.sacramento));
        selSac.disabled = true;
        campoNombre.value = q.nombre;
        campoDesc.value = q.descripcion || '';
        selObl.value = q.obligatorio ? 'true' : 'false';
        campoOrden.value = String(q.orden);
        botonGuardar.textContent = 'Guardar cambios';
        botonCancelar.style.display = '';
        campoNombre.focus();
      } else if (boton.dataset.accion === 'eliminar-requisito') {
        if (!window.confirm('¿Eliminar este requisito? Los feligreses dejarán de verlo en su lista.')) return;
        boton.disabled = true;
        fetch('/api/admin/requisitos?id=' + encodeURIComponent(id), { method: 'DELETE', credentials: 'same-origin' })
          .then(leer)
          .then(({ ok, data }) => { if (campoId.value === id) salirDeEdicion(); return cargar().then(() => { if (!ok) nota.textContent = data.error || 'No se pudo eliminar el requisito.'; }); })
          .catch(() => { nota.textContent = 'No se pudo conectar con el servidor.'; });
      }
    });

    cargarSacramentos().then(() => { salirDeEdicion(); return cargar(); });
  }

  /* ------------------------------------------------------------------ checklist del feligrés */
  function iniciarChecklist() {
    const panel = $('checklist-panel');
    const select = $('doc-reserva');
    if (!panel || !select) return;
    const lista = $('checklist-lista'), resumen = $('checklist-resumen'), mensaje = $('checklist-mensaje'), input = $('checklist-archivo');
    let requisitoElegido = null;
    let consulta = 0;

    function avisar(texto, error) {
      mensaje.className = 'mensaje-confirmacion visible';
      mensaje.style.color = error ? 'red' : '';
      mensaje.textContent = texto;
    }

    function pintar(c) {
      lista.textContent = '';
      resumen.textContent = c.obligatorios === 0
        ? 'Esta parroquia no pide documentos obligatorios para este trámite.'
        : `Requisitos obligatorios validados: ${c.validados} de ${c.obligatorios}.` + (c.completo ? ' ¡Tu expediente está completo!' : '');
      c.items.forEach((it) => {
        const li = el('li', null, 'item-requisito');
        const cabecera = el('div', null, 'item-requisito-cabecera');
        cabecera.append(el('strong', it.nombre), el('span', it.obligatorio ? 'Obligatorio' : 'Recomendado', 'etiqueta-requisito'), insignia(it.estado));
        li.appendChild(cabecera);
        if (it.descripcion) li.appendChild(el('small', it.descripcion));
        if (it.estado === 'rechazado' && it.motivo) li.appendChild(el('small', 'Motivo del rechazo: ' + it.motivo, 'motivo-requisito'));
        if (it.estado === 'faltante' || it.estado === 'rechazado') {
          const boton = el('button', it.estado === 'rechazado' ? 'Subir de nuevo' : 'Subir documento', 'boton-mini');
          boton.type = 'button';
          boton.addEventListener('click', () => { requisitoElegido = it.id; input.click(); });
          li.appendChild(boton);
        }
        lista.appendChild(li);
      });
      panel.hidden = false;
    }

    function cargar() {
      const reserva = select.value;
      const mia = ++consulta;
      mensaje.className = 'mensaje-confirmacion';
      if (!reserva) { panel.hidden = true; return Promise.resolve(); }
      return fetch('/api/reservas/checklist?reserva=' + encodeURIComponent(reserva), { cache: 'no-store', credentials: 'same-origin' })
        .then(leer)
        .then(({ ok, data }) => {
          if (mia !== consulta) return;   // ya se pidió el de otra solicitud
          if (ok) pintar(data); else panel.hidden = true;
        })
        .catch(() => { panel.hidden = true; });
    }

    input.addEventListener('change', () => {
      const archivo = input.files[0];
      input.value = '';
      if (!archivo || requisitoElegido === null) return;
      if (archivo.size === 0) return avisar('El archivo está vacío.', true);
      if (archivo.size > MAX_BYTES) return avisar('El archivo supera el máximo de 5 MB.', true);
      if (!/\.(pdf|jpe?g|png)$/i.test(archivo.name)) return avisar('Solo se aceptan PDF, JPG o PNG.', true);
      const datos = new FormData();
      datos.append('id_reserva', select.value);
      datos.append('id_requisito', String(requisitoElegido));
      datos.append('archivo', archivo);
      avisar('Subiendo...', false);
      fetch('/api/documentos', { method: 'POST', body: datos, credentials: 'same-origin' })
        .then(leer)
        .then(({ ok, data }) => {
          if (ok) { avisar('Documento recibido. La parroquia lo revisará.', false); document.dispatchEvent(new CustomEvent('documentos:actualizar')); }
          else avisar(data.error || 'No se pudo subir el archivo.', true);
          return cargar();
        })
        .catch(() => avisar('No se pudo conectar con el servidor.', true));
    });

    select.addEventListener('change', cargar);
    // main.js llena las solicitudes de forma asíncrona (y elige sola la única que haya): se vuelve a leer cuando cambian las opciones
    new MutationObserver(cargar).observe(select, { childList: true });
    cargar();
  }

  /* ------------------------------------------------------------------ requisitos al reservar */
  function iniciarInfoReserva() {
    const caja = $('requisitos-info');
    const parroquia = $('parroquia'), sacramento = $('sacramento'), tarjetas = $('tarjetas-sacramento');
    if (!caja || !parroquia || !sacramento) return;
    const lista = $('requisitos-lista');
    let consulta = 0;

    function cargar() {
      const mia = ++consulta;
      if (!parroquia.value || !sacramento.value) { caja.hidden = true; return; }
      fetch('/api/requisitos?parroquia=' + encodeURIComponent(parroquia.value) + '&sacramento=' + encodeURIComponent(sacramento.value),
        { cache: 'no-store', credentials: 'same-origin' })
        .then(leer)
        .then(({ ok, data }) => {
          if (mia !== consulta) return;
          lista.textContent = '';
          if (!ok || !data.length) { caja.hidden = true; return; }
          data.forEach((q) => {
            const li = el('li');
            li.appendChild(el('strong', q.nombre));
            li.appendChild(el('span', q.obligatorio ? ' (obligatorio)' : ' (recomendado)', 'etiqueta-requisito'));
            if (q.descripcion) { li.appendChild(document.createElement('br')); li.appendChild(el('small', q.descripcion)); }
            lista.appendChild(li);
          });
          caja.hidden = false;
        })
        .catch(() => { caja.hidden = true; });
    }

    parroquia.addEventListener('change', cargar);
    // el sacramento se elige tocando una tarjeta (main.js guarda el valor en un campo oculto, que no emite eventos)
    if (tarjetas) tarjetas.addEventListener('click', () => setTimeout(cargar, 0));
    cargar();
  }

  document.addEventListener('DOMContentLoaded', () => {
    iniciarAdmin();
    iniciarChecklist();
    iniciarInfoReserva();
  });
})();
