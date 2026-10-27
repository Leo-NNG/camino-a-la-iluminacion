// Camino a la Iluminación — pagos Yape/Plin, intenciones de misa, recibos y bandeja de avisos.
// Se carga DESPUÉS de main.js y reutiliza sus ayudas (escaparHtml, formatearFecha, cargarParroquias, NOMBRES_*).
// Toda la información viene del servidor (/api/...). El navegador solo ayuda a llenar los formularios:
// cada regla (monto, cupo, tipo de archivo, duplicados) la vuelve a comprobar el servidor.
// Los textos que vienen de la base de datos SIEMPRE se escapan o se insertan con textContent.

(function () {
  'use strict';

  const ESTADOS_PAGO = { pendiente: ['pendiente', 'En verificación'], aprobado: ['validado', 'Verificado'], rechazado: ['rechazado', 'Rechazado'] };
  const NOMBRES_CONCEPTO = { arancel: 'Arancel', donacion: 'Donación', diezmo: 'Diezmo', intencion: 'Intención de misa' };
  const NOMBRES_METODO = { yape: 'Yape', plin: 'Plin' };
  const MAX_BYTES = 5 * 1024 * 1024;
  const RE_OPERACION = /^\d{6,12}$/;
  const RE_CELULAR = /^9\d{8}$/;
  const RE_IMPORTE = /^\d{1,6}([.,]\d{1,2})?$/;
  const DIA_JS = { domingo: 0, lunes: 1, martes: 2, miercoles: 3, jueves: 4, viernes: 5, sabado: 6 };

  const q = (id) => document.getElementById(id);
  const leer = (r) => r.json().catch(() => ({})).then((data) => ({ ok: r.ok, estado: r.status, data }));
  const pedir = (url) => fetch(url, { cache: 'no-store', credentials: 'same-origin' }).then(leer);
  const enviar = (url, body) => fetch(url, { method: 'POST', body, credentials: 'same-origin' }).then(leer);
  const moneda = (n) => 'S/ ' + Number(n).toFixed(2);
  const esc = (t) => escaparHtml(t);
  const SIN_CONEXION = 'No se pudo conectar con el servidor.';

  function insignia(estado) {
    const [clase, texto] = ESTADOS_PAGO[estado] || ['pendiente', esc(estado)];
    return `<span class="insignia ${clase}">${texto}</span>`;
  }

  function dosDigitos(n) { return String(n).padStart(2, '0'); }
  function aIso(d) { return `${d.getFullYear()}-${dosDigitos(d.getMonth() + 1)}-${dosDigitos(d.getDate())}`; }
  function sumarDias(d, n) { const x = new Date(d.getFullYear(), d.getMonth(), d.getDate()); x.setDate(x.getDate() + n); return x; }
  function nombreDia(iso) {
    const d = new Date(iso + 'T00:00:00');
    return ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'][d.getDay()];
  }

  /* Pestañas con el control segmentado de iOS: <div class="ios-seg"><button data-tab="x"> y paneles [data-panel="x"]. */
  function montarPestanas(seg, alCambiar) {
    if (!seg) return;
    const botones = Array.from(seg.querySelectorAll('button[data-tab]'));
    const mostrar = (nombre) => {
      botones.forEach((b) => b.classList.toggle('activo', b.dataset.tab === nombre));
      document.querySelectorAll('[data-panel]').forEach((p) => { p.hidden = p.dataset.panel !== nombre; });
      if (alCambiar) alCambiar(nombre);
    };
    botones.forEach((b) => b.addEventListener('click', () => mostrar(b.dataset.tab)));
    const inicial = (window.location.hash || '').replace('#', '');
    mostrar(botones.some((b) => b.dataset.tab === inicial) ? inicial : botones[0].dataset.tab);
    return mostrar;
  }

  /* ==================== BLOQUE DE COBRO (compartido por aranceles, donaciones, diezmos e intenciones) ====================
     Muestra el monto, a qué número pagar (Yape o Plin, con su titular) y recoge la operación, el celular y la captura.
     cfg = { monto, cuenta: {yape, plin}, url, campos(): {nombre: valor}, etiqueta, alExito(data) } */
  function montarCobro(cont, cfg) {
    const metodos = ['yape', 'plin'].filter((m) => cfg.cuenta && cfg.cuenta[m]);
    if (!metodos.length) {
      cont.innerHTML = '<p class="nota-cobro">Esta parroquia aún no configuró Yape ni Plin. Consulta en la parroquia cómo pagar.</p>';
      return;
    }
    let metodo = metodos[0];
    let urlVista = null;

    cont.innerHTML = `
      <div class="caja-cobro">
        <p class="cobro-monto">Monto a pagar <strong>${moneda(cfg.monto)}</strong></p>
        <div class="ios-seg" data-metodos>
          ${['yape', 'plin'].map((m) => `<button type="button" data-metodo="${m}" ${metodos.includes(m) ? '' : 'disabled title="La parroquia no tiene este método"'}>${NOMBRES_METODO[m]}</button>`).join('')}
        </div>
        <div class="cobro-cuenta" data-cuenta></div>
        <ol class="cobro-pasos">
          <li>Paga el monto exacto desde tu app al número indicado.</li>
          <li>Toma una captura del comprobante donde se vea el <strong>número de operación</strong>.</li>
          <li>Completa los datos y sube la captura: la parroquia verificará tu pago.</li>
        </ol>
        <form class="cobro-form" novalidate>
          <div class="campo"><label>Número de operación</label>
            <input type="text" name="operacion" inputmode="numeric" maxlength="12" autocomplete="off" placeholder="Entre 6 y 12 dígitos"></div>
          <div class="campo"><label>Celular desde el que pagaste</label>
            <input type="text" name="celular" inputmode="numeric" maxlength="9" autocomplete="off" placeholder="9 dígitos, empieza con 9"></div>
          <div class="campo"><label>Captura del comprobante (JPG o PNG, máx. 5 MB)</label>
            <input type="file" name="comprobante" accept="image/png,image/jpeg">
            <img class="cobro-vista" alt="Vista previa de tu comprobante" hidden></div>
          <button type="submit" class="boton boton-primario">${esc(cfg.etiqueta || 'Enviar pago')}</button>
        </form>
        <div class="mensaje-confirmacion" data-mensaje role="status" aria-live="polite"></div>
      </div>`;

    const form = cont.querySelector('form');
    const mensaje = cont.querySelector('[data-mensaje]');
    const vista = cont.querySelector('.cobro-vista');
    const archivo = form.elements.comprobante;

    const pintarCuenta = () => {
      const c = cfg.cuenta[metodo];
      cont.querySelectorAll('[data-metodo]').forEach((b) => b.classList.toggle('activo', b.dataset.metodo === metodo));
      const caja = cont.querySelector('[data-cuenta]');
      caja.innerHTML = `<span class="cuenta-etiqueta">${NOMBRES_METODO[metodo]} a nombre de</span>
        <strong>${esc(c.titular)}</strong>
        <span class="cuenta-numero">${esc(c.numero)}</span>
        <button type="button" class="boton-mini" data-copiar>Copiar número</button>`;
    };
    pintarCuenta();

    cont.addEventListener('click', (e) => {
      const m = e.target.closest('[data-metodo]');
      if (m && !m.disabled) { metodo = m.dataset.metodo; pintarCuenta(); return; }
      const copiar = e.target.closest('[data-copiar]');
      if (copiar) {
        const numero = cfg.cuenta[metodo].numero;
        const listo = () => { copiar.textContent = '¡Copiado!'; setTimeout(() => { copiar.textContent = 'Copiar número'; }, 1600); };
        if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(numero).then(listo, () => { copiar.textContent = 'Cópialo a mano'; });
        else copiar.textContent = 'Cópialo a mano';
      }
    });

    archivo.addEventListener('change', () => {
      if (urlVista) { URL.revokeObjectURL(urlVista); urlVista = null; }
      const f = archivo.files[0];
      if (f && /^image\/(png|jpeg)$/.test(f.type) && f.size <= MAX_BYTES) { urlVista = URL.createObjectURL(f); vista.src = urlVista; vista.hidden = false; }
      else vista.hidden = true;
    });

    function error(texto) { mensaje.textContent = texto; mensaje.style.color = 'var(--ios-red, red)'; mensaje.classList.add('visible'); }

    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const operacion = form.elements.operacion.value.trim();
      const celular = form.elements.celular.value.replace(/\s/g, '');
      const f = archivo.files[0];
      if (!RE_OPERACION.test(operacion)) return error('El número de operación debe tener entre 6 y 12 dígitos.');
      if (!RE_CELULAR.test(celular)) return error('Ingresa el celular desde el que pagaste: 9 dígitos y empieza con 9.');
      if (!f) return error('Sube la captura del comprobante.');
      if (!/^image\/(png|jpeg)$/.test(f.type)) return error('El comprobante debe ser una imagen JPG o PNG.');
      if (f.size === 0 || f.size > MAX_BYTES) return error('La captura debe pesar menos de 5 MB.');

      const datos = new FormData();
      datos.append('metodo', metodo);
      datos.append('numero_operacion', operacion);
      datos.append('celular', celular);
      datos.append('comprobante', f);
      const extra = cfg.campos();
      Object.keys(extra).forEach((k) => datos.append(k, extra[k]));

      const boton = form.querySelector('button[type="submit"]');
      boton.disabled = true;
      mensaje.textContent = 'Enviando…';
      mensaje.style.color = '';
      mensaje.classList.add('visible');
      enviar(cfg.url, datos)
        .then(({ ok, data }) => {
          if (!ok) return error(data.error || 'No se pudo registrar el pago.');
          mensaje.style.color = '';
          mensaje.textContent = '¡Listo! Tu pago quedó en verificación. Te avisaremos en tu bandeja cuando la parroquia lo revise.';
          form.reset();
          vista.hidden = true;
          if (cfg.alExito) cfg.alExito(data);
        })
        .catch(() => error(SIN_CONEXION))
        .finally(() => { boton.disabled = false; });
    });
  }

  /* ==================== PÁGINA: pagos.html ==================== */
  function iniciarPaginaPagos() {
    const tabla = q('tabla-mis-pagos');
    if (!tabla) return;
    const notaPagos = q('nota-mis-pagos');

    function cargarMisPagos() {
      return pedir('/api/pagos').then(({ ok, data }) => {
        if (!ok) { tabla.innerHTML = ''; notaPagos.textContent = data.error || 'No se pudieron cargar tus pagos.'; return; }
        notaPagos.textContent = data.length ? '' : 'Todavía no has hecho ningún pago.';
        tabla.innerHTML = data.map((p) => {
          const obs = p.estado === 'rechazado' && p.observacion ? `<br><small>Motivo: ${esc(p.observacion)}</small>` : '';
          const recibo = p.estado === 'aprobado' ? ` · <a href="/api/pagos/recibo?id=${Number(p.id)}" target="_blank" rel="noopener">Recibo PDF</a>` : '';
          return `<tr>
            <td>${esc(p.detalle)}<br><small>${esc(p.parroquia)}</small></td>
            <td>${moneda(p.monto)}</td>
            <td>${esc(NOMBRES_METODO[p.metodo] || p.metodo)}<br><small>Op. ${esc(p.operacion)}</small></td>
            <td>${formatearFecha(p.fecha)}</td>
            <td>${insignia(p.estado)}${obs}</td>
            <td><a href="/api/pagos/voucher?id=${Number(p.id)}" target="_blank" rel="noopener">Comprobante</a>${recibo}</td>
          </tr>`;
        }).join('');
      }).catch(() => { notaPagos.textContent = SIN_CONEXION; });
    }

    /* ---- pestaña 1: arancel de una solicitud ---- */
    const selReserva = q('pago-reserva');
    const zonaArancel = q('cobro-arancel');
    function cargarSolicitudes() {
      return pedir('/api/mis-reservas').then(({ data }) => {
        const activas = Array.isArray(data) ? data.filter((r) => r.estado !== 'rechazada') : [];
        selReserva.length = 1;
        activas.forEach((r) => {
          const op = document.createElement('option');
          op.value = String(r.id);
          op.textContent = `${NOMBRES_SACRAMENTO[r.sacramento] || r.sacramento} — ${r.parroquia} — ${formatearFecha(r.fecha)} (${r.solicitante})`;
          selReserva.appendChild(op);
        });
        q('aviso-sin-solicitudes').hidden = activas.length > 0;
        const previa = new URLSearchParams(window.location.search).get('reserva');
        if (previa && activas.some((r) => String(r.id) === previa)) { selReserva.value = previa; mostrarArancel(); }
      }).catch(() => {});
    }
    function mostrarArancel(exito) {
      zonaArancel.innerHTML = '';
      if (!selReserva.value) return;
      zonaArancel.innerHTML = '<p class="nota-cobro">Consultando el arancel…</p>';
      pedir('/api/pagos/info?id_reserva=' + encodeURIComponent(selReserva.value)).then(({ ok, data }) => {
        if (!ok) { zonaArancel.innerHTML = `<p class="nota-cobro">${esc(data.error || 'No se pudo consultar el arancel.')}</p>`; return; }
        if (data.pagoVigente) {
          const [, texto] = ESTADOS_PAGO[data.pagoVigente.estado] || ['', data.pagoVigente.estado];
          // Tras pagar, el bloque de cobro se reemplaza por este aviso: el mensaje de éxito debe quedar visible.
          const exitoHtml = exito ? '<p class="exito-pago" role="status">¡Listo! Tu pago quedó en verificación. Te avisaremos en tu bandeja cuando la parroquia lo revise.</p>' : '';
          zonaArancel.innerHTML = exitoHtml + `<p class="nota-cobro">Esta solicitud ya tiene un pago registrado (${esc(texto)}). Míralo en la pestaña <a href="#mis-pagos" data-ir="mis-pagos">Mis pagos</a>.</p>`;
          return;
        }
        if (data.monto == null || Number(data.monto) <= 0) {
          zonaArancel.innerHTML = '<p class="nota-cobro">Este trámite no tiene un arancel que pagar en línea. Consulta en la parroquia.</p>';
          return;
        }
        zonaArancel.innerHTML = `<p class="cobro-titulo">${esc(data.detalle)} · ${esc(data.parroquia)}</p><div id="bloque-arancel"></div>`;
        montarCobro(q('bloque-arancel'), {
          monto: Number(data.monto), cuenta: data.cuenta, url: '/api/pagos', etiqueta: 'Enviar pago del arancel',
          campos: () => ({ tipo: 'arancel', id_reserva: selReserva.value }),
          alExito: () => { cargarMisPagos(); mostrarArancel(true); },
        });
      }).catch(() => { zonaArancel.innerHTML = `<p class="nota-cobro">${SIN_CONEXION}</p>`; });
    }
    selReserva.addEventListener('change', () => mostrarArancel(false));

    /* ---- pestaña 2: donación o diezmo ---- */
    const selParroquia = q('libre-parroquia');
    const selConcepto = q('libre-concepto');
    const inpMonto = q('libre-monto');
    const zonaLibre = q('cobro-libre');
    const avisoLibre = q('aviso-libre');
    cargarParroquias().then((lista) => {
      lista.forEach((p) => {
        const op = document.createElement('option');
        op.value = String(p.id);
        op.textContent = p.nombre;
        selParroquia.appendChild(op);
      });
    });
    const limpiarLibre = () => { zonaLibre.innerHTML = ''; avisoLibre.textContent = ''; };
    [selParroquia, selConcepto, inpMonto].forEach((c) => c.addEventListener('input', limpiarLibre));
    q('btn-libre-continuar').addEventListener('click', () => {
      limpiarLibre();
      const texto = inpMonto.value.trim();
      if (!selParroquia.value) { avisoLibre.textContent = 'Elige una parroquia.'; return; }
      if (!RE_IMPORTE.test(texto)) { avisoLibre.textContent = 'Escribe un monto válido (solo números, hasta 2 decimales).'; return; }
      const monto = Number(texto.replace(',', '.'));
      if (monto < 1 || monto > 10000) { avisoLibre.textContent = 'El monto debe estar entre S/ 1 y S/ 10000.'; return; }
      pedir('/api/pagos/info?id_parroquia=' + encodeURIComponent(selParroquia.value)).then(({ ok, data }) => {
        if (!ok) { avisoLibre.textContent = data.error || 'No se pudo consultar la parroquia.'; return; }
        zonaLibre.innerHTML = `<p class="cobro-titulo">${esc(NOMBRES_CONCEPTO[selConcepto.value])} · ${esc(data.parroquia)}</p><div id="bloque-libre"></div>`;
        montarCobro(q('bloque-libre'), {
          monto, cuenta: data.cuenta, url: '/api/pagos', etiqueta: 'Enviar ' + NOMBRES_CONCEPTO[selConcepto.value].toLowerCase(),
          campos: () => ({ tipo: selConcepto.value, id_parroquia: selParroquia.value, monto: monto.toFixed(2) }),
          alExito: () => { cargarMisPagos(); inpMonto.value = ''; },
        });
      }).catch(() => { avisoLibre.textContent = SIN_CONEXION; });
    });

    document.addEventListener('click', (e) => {
      const ir = e.target.closest('[data-ir]');
      if (ir) { e.preventDefault(); mostrar(ir.dataset.ir); }
    });
    const mostrar = montarPestanas(q('pestanas-pagos'), (nombre) => { if (nombre === 'mis-pagos') cargarMisPagos(); });
    cargarSolicitudes();
    cargarMisPagos();
  }

  /* ==================== PÁGINA: intenciones.html ==================== */
  function iniciarPaginaIntenciones() {
    const selParroquia = q('int-parroquia');
    if (!selParroquia) return;
    const zona = q('zona-intencion');
    const nota = q('nota-intencion');
    const tabla = q('tabla-mis-intenciones');
    const notaTabla = q('nota-mis-intenciones');
    let info = null;

    function cargarMisIntenciones() {
      return pedir('/api/intenciones').then(({ ok, data }) => {
        if (!ok) { tabla.innerHTML = ''; notaTabla.textContent = data.error || 'No se pudieron cargar tus intenciones.'; return; }
        notaTabla.textContent = data.length ? '' : 'Todavía no has pedido ninguna intención.';
        tabla.innerHTML = data.map((i) => {
          const obs = i.estadoPago === 'rechazado' && i.observacion ? `<br><small>Motivo: ${esc(i.observacion)}</small>` : '';
          const recibo = i.estadoPago === 'aprobado' ? `<a href="/api/pagos/recibo?id=${Number(i.idPago)}" target="_blank" rel="noopener">Recibo PDF</a>` : '';
          return `<tr>
            <td>${formatearFecha(i.fecha)}<br><small>${esc(nombreDia(i.fecha))} · ${esc(i.hora)}</small></td>
            <td>${esc(i.etiqueta)} <strong>${esc(i.texto)}</strong><br><small>${esc(i.parroquia)}</small></td>
            <td>${moneda(i.monto)}</td>
            <td>${insignia(i.estadoPago)}${obs}</td>
            <td>${recibo}</td>
          </tr>`;
        }).join('');
      }).catch(() => { notaTabla.textContent = SIN_CONEXION; });
    }

    cargarParroquias().then((lista) => {
      lista.forEach((p) => {
        const op = document.createElement('option');
        op.value = String(p.id);
        op.textContent = p.nombre;
        selParroquia.appendChild(op);
      });
    });

    selParroquia.addEventListener('change', () => {
      zona.innerHTML = '';
      nota.textContent = '';
      info = null;
      if (!selParroquia.value) return;
      pedir('/api/intenciones/info?id_parroquia=' + encodeURIComponent(selParroquia.value)).then(({ ok, data }) => {
        if (!ok) { nota.textContent = data.error || 'No se pudo consultar la parroquia.'; return; }
        info = data;
        if (data.ofrenda == null || Number(data.ofrenda) <= 0) { nota.textContent = 'Esta parroquia aún no recibe intenciones de misa en línea. Consulta en la parroquia.'; return; }
        if (!data.misas.length) { nota.textContent = 'Esta parroquia aún no publicó sus horarios de misa.'; return; }
        pintarFormulario();
      }).catch(() => { nota.textContent = SIN_CONEXION; });
    });

    function pintarFormulario() {
      const hoy = new Date();
      const minimo = aIso(sumarDias(hoy, 1));
      const maximo = aIso(sumarDias(hoy, 90));
      zona.innerHTML = `
        <div class="ios-panel">
          <p class="cobro-titulo">${esc(info.parroquia)} · ofrenda ${moneda(info.ofrenda)}</p>
          <div class="campo"><label for="int-misa">Misa</label>
            <select id="int-misa">${info.misas.map((m) => `<option value="${Number(m.id)}" data-dia="${esc(m.dia)}">${esc(NOMBRES_DIA[m.dia] || m.dia)} — ${esc(m.hora)}</option>`).join('')}</select></div>
          <div class="campo"><label for="int-fecha">Fecha de la misa</label>
            <input type="date" id="int-fecha" min="${minimo}" max="${maximo}">
            <div class="chips-fecha" id="chips-fecha"></div>
            <p class="ayuda" id="ayuda-fecha">Elige una fecha desde mañana y hasta 90 días. Debe caer en el día de la misa.</p></div>
          <div class="campo"><label for="int-tipo">¿Por quién o por qué?</label>
            <select id="int-tipo">
              <option value="difunto">Por el eterno descanso de…</option>
              <option value="accion_gracias">En acción de gracias por…</option>
              <option value="salud">Por la salud de…</option>
              <option value="otra">Otra intención</option></select></div>
          <div class="campo"><label for="int-texto">Escribe la intención</label>
            <textarea id="int-texto" rows="2" maxlength="200" placeholder="Ej. Juan Pérez Rojas"></textarea>
            <p class="ayuda"><span id="int-contador">0</span>/200 · solo letras, números y signos comunes.</p></div>
          <button type="button" class="boton boton-primario" id="btn-int-continuar">Continuar al pago</button>
          <p class="ayuda" id="aviso-int" role="alert"></p>
          <div id="cobro-intencion"></div>
        </div>`;

      const selMisa = q('int-misa'), inpFecha = q('int-fecha'), chips = q('chips-fecha'), texto = q('int-texto');
      const aviso = q('aviso-int'), zonaCobro = q('cobro-intencion');
      const diaDeMisa = () => DIA_JS[selMisa.selectedOptions[0].dataset.dia];

      function pintarChips() {
        chips.innerHTML = '';
        let d = sumarDias(hoy, 1), n = 0;
        while (n < 5 && d <= sumarDias(hoy, 90)) {
          if (d.getDay() === diaDeMisa()) {
            const b = document.createElement('button');
            b.type = 'button';
            b.className = 'chip';
            b.textContent = `${dosDigitos(d.getDate())}/${dosDigitos(d.getMonth() + 1)}`;
            b.dataset.fecha = aIso(d);
            chips.appendChild(b);
            n++;
          }
          d = sumarDias(d, 1);
        }
      }
      pintarChips();
      chips.addEventListener('click', (e) => {
        const c = e.target.closest('[data-fecha]');
        if (c) { inpFecha.value = c.dataset.fecha; zonaCobro.innerHTML = ''; }
      });
      selMisa.addEventListener('change', () => { pintarChips(); inpFecha.value = ''; zonaCobro.innerHTML = ''; });
      [inpFecha, texto, q('int-tipo')].forEach((c) => c.addEventListener('input', () => { zonaCobro.innerHTML = ''; aviso.textContent = ''; }));
      texto.addEventListener('input', () => { q('int-contador').textContent = String(texto.value.length); });

      q('btn-int-continuar').addEventListener('click', () => {
        zonaCobro.innerHTML = '';
        aviso.textContent = '';
        const f = inpFecha.value;
        if (!f) { aviso.textContent = 'Elige la fecha de la misa.'; return; }
        if (f < minimo || f > maximo) { aviso.textContent = 'La fecha debe ser desde mañana y dentro de los próximos 90 días.'; return; }
        if (new Date(f + 'T00:00:00').getDay() !== diaDeMisa()) { aviso.textContent = 'Esa fecha no cae en el día de la misa elegida. Usa una de las fechas sugeridas.'; return; }
        const t = texto.value.trim().replace(/\s+/g, ' ');
        if (t.length < 3) { aviso.textContent = 'Escribe la intención (mínimo 3 caracteres).'; return; }
        if (!/^[\p{L}\p{N} .,;:'’\-()¡!¿?/&]+$/u.test(t)) { aviso.textContent = 'La intención solo admite letras, números y signos comunes.'; return; }
        zonaCobro.innerHTML = '<div id="bloque-intencion"></div>';
        montarCobro(q('bloque-intencion'), {
          monto: Number(info.ofrenda), cuenta: info.cuenta, url: '/api/intenciones', etiqueta: 'Enviar intención y ofrenda',
          campos: () => ({ id_parroquia: selParroquia.value, id_horario: selMisa.value, fecha: f, tipo: q('int-tipo').value, texto: t }),
          alExito: () => { cargarMisIntenciones(); texto.value = ''; q('int-contador').textContent = '0'; },
        });
        zonaCobro.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      });
    }

    cargarMisIntenciones();
  }

  /* ==================== PÁGINA: bandeja.html (notificaciones del feligrés) ==================== */
  function iniciarBandeja() {
    const lista = q('lista-bandeja');
    if (!lista) return;
    const nota = q('nota-bandeja');
    const cargar = () => pedir('/api/notificaciones').then(({ ok, data }) => {
      if (!ok) { lista.innerHTML = ''; nota.textContent = data.error || 'No se pudo cargar tu bandeja.'; return; }
      nota.textContent = data.notificaciones.length ? '' : 'No tienes avisos todavía.';
      lista.innerHTML = data.notificaciones.map((n) => `
        <li class="bandeja-item ${n.leida ? '' : 'nueva'}">
          <div class="bandeja-cabecera"><strong>${esc(n.asunto)}</strong><small>${formatearFecha(n.fecha)}</small></div>
          <p>${esc(n.mensaje)}</p>
        </li>`).join('');
      q('btn-marcar-leidas').disabled = data.noLeidas === 0;
      actualizarCampana(data.noLeidas);
    }).catch(() => { nota.textContent = SIN_CONEXION; });
    q('btn-marcar-leidas').addEventListener('click', () => {
      enviar('/api/notificaciones', new URLSearchParams()).then(cargar).catch(() => { nota.textContent = SIN_CONEXION; });
    });
    cargar();
  }


  /* ==================== PANEL: admin/pagos.html ==================== */
  function iniciarPanelPagos() {
    const tbody = q('tabla-pagos-admin');
    if (!tbody) return;
    const nota = q('nota-pagos-admin');
    const seg = q('pestanas-estado');
    let estado = 'pendiente';

    const cargar = () => pedir('/api/admin/pagos?estado=' + estado).then(({ ok, data }) => {
      if (!ok) { tbody.innerHTML = ''; nota.textContent = data.error || 'No se pudieron cargar los pagos.'; return; }
      nota.textContent = data.length ? '' : (estado === 'pendiente' ? 'No hay pagos esperando verificación.' : 'No hay pagos en este estado.');
      tbody.innerHTML = data.map((p) => {
        const id = Number(p.id);
        const comprobante = `<a href="/api/pagos/voucher?id=${id}" target="_blank" rel="noopener" class="miniatura">
            <img src="/api/pagos/voucher?id=${id}" alt="Comprobante del pago ${id}" loading="lazy"></a>`;
        let accion;
        if (p.estado === 'pendiente') {
          accion = `<button class="boton-mini aprobar" data-accion="aprobar">Verificar</button>
                    <button class="boton-mini rechazar" data-accion="pedir-motivo">Rechazar</button>`;
        } else if (p.estado === 'aprobado') {
          accion = `<a href="/api/pagos/recibo?id=${id}" target="_blank" rel="noopener">Recibo PDF</a>`;
        } else {
          accion = p.observacion ? `<small>Motivo: ${esc(p.observacion)}</small>` : '';
        }
        return `<tr data-id="${id}">
          <td>${esc(p.feligres)}<br><small>${formatearFecha(p.fecha)}</small></td>
          <td>${esc(NOMBRES_CONCEPTO[p.concepto] || p.concepto)}<br><small>${esc(p.detalle)}</small></td>
          <td><strong>${moneda(p.monto)}</strong></td>
          <td>${esc(NOMBRES_METODO[p.metodo] || p.metodo)}<br><small>Op. ${esc(p.operacion)}</small><br><small>Cel. ${esc(p.celular)}</small></td>
          <td>${comprobante}</td>
          <td class="fila-accion">${accion}</td>
        </tr>`;
      }).join('');
    }).catch(() => { nota.textContent = SIN_CONEXION; });

    montarPestanas(seg, (nombre) => { estado = nombre; cargar(); });

    const resolver = (id, accion, observacion) => {
      const datos = new URLSearchParams({ id, accion });
      if (observacion) datos.append('observacion', observacion);
      return enviar('/api/admin/pagos', datos).then(({ ok, data }) => Promise.all([cargar(), cargarIngresos()]).then(() => { if (!ok) nota.textContent = data.error || 'No se pudo actualizar el pago.'; }))
        .catch(() => { nota.textContent = SIN_CONEXION; });
    };
    tbody.addEventListener('click', (e) => {
      const b = e.target.closest('[data-accion]');
      if (!b) return;
      const fila = b.closest('tr'), celda = fila.querySelector('.fila-accion'), id = fila.dataset.id;
      if (b.dataset.accion === 'aprobar') { b.disabled = true; resolver(id, 'aprobar'); }
      else if (b.dataset.accion === 'pedir-motivo') {
        celda.innerHTML = `<input type="text" class="campo-motivo" maxlength="255" placeholder="Motivo del rechazo" aria-label="Motivo del rechazo" style="min-width:200px;padding:6px 8px;">
          <button class="boton-mini rechazar" data-accion="rechazar">Confirmar rechazo</button>
          <button class="boton-mini" data-accion="cancelar">Cancelar</button>`;
        celda.querySelector('input').focus();
      } else if (b.dataset.accion === 'cancelar') cargar();
      else if (b.dataset.accion === 'rechazar') {
        const motivo = celda.querySelector('input').value.trim();
        if (!motivo) { nota.textContent = 'Escribe el motivo del rechazo: el feligrés lo verá.'; celda.querySelector('input').focus(); return; }
        b.disabled = true;
        resolver(id, 'rechazar', motivo);
      }
    });

    /* Ingresos aprobados por concepto, del mes elegido. */
    const inpMes = q('ingresos-mes'), cajas = q('ingresos-cajas');
    const hoy = new Date();
    inpMes.value = `${hoy.getFullYear()}-${dosDigitos(hoy.getMonth() + 1)}`;
    const cargarIngresos = () => pedir('/api/admin/pagos?resumen=ingresos&mes=' + encodeURIComponent(inpMes.value)).then(({ ok, data }) => {
      if (!ok) { cajas.innerHTML = `<p class="nota-cobro">${esc(data.error || 'No se pudieron cargar los ingresos.')}</p>`; return; }
      const total = data.reduce((s, f) => s + Number(f.total), 0);
      cajas.innerHTML = data.map((f) => `<div class="ios-kpi"><div class="ios-kpi-t">${esc(NOMBRES_CONCEPTO[f.concepto] || f.concepto)} (${Number(f.pagos)})</div><div class="ios-kpi-v">${moneda(f.total)}</div></div>`).join('')
        + `<div class="ios-kpi kpi-total"><div class="ios-kpi-t">Total del mes</div><div class="ios-kpi-v">${moneda(total)}</div></div>`;
      if (!data.length) cajas.innerHTML = '<p class="nota-cobro">No hay pagos verificados en este mes.</p>';
    }).catch(() => { cajas.innerHTML = `<p class="nota-cobro">${SIN_CONEXION}</p>`; });
    inpMes.addEventListener('change', cargarIngresos);
    cargarIngresos();
  }

  /* ==================== PANEL: admin/cobros.html ==================== */
  function iniciarPanelCobros() {
    const formCuenta = q('form-cuenta');
    if (!formCuenta) return;
    const formOfrenda = q('form-ofrenda');
    const tabla = q('tabla-aranceles');
    const aviso = (id, texto, bien) => { const e = q(id); e.textContent = texto; e.style.color = bien ? 'var(--color-sage, green)' : 'var(--ios-red, red)'; };

    const cargar = () => pedir('/api/admin/pagos/config').then(({ ok, data }) => {
      if (!ok) { aviso('aviso-cuenta', data.error || 'No se pudo cargar la configuración.', false); return; }
      const c = data.cuenta || {};
      formCuenta.elements.yape_numero.value = c.yape ? c.yape.numero : '';
      formCuenta.elements.yape_titular.value = c.yape ? c.yape.titular : '';
      formCuenta.elements.plin_numero.value = c.plin ? c.plin.numero : '';
      formCuenta.elements.plin_titular.value = c.plin ? c.plin.titular : '';
      formOfrenda.elements.monto.value = data.ofrendaIntencion == null ? '' : Number(data.ofrendaIntencion).toFixed(2);
      tabla.innerHTML = data.aranceles.map((a) => `<tr data-sacramento="${Number(a.idSacramento)}">
          <td>${esc(a.sacramento)}</td>
          <td><input type="text" inputmode="decimal" class="monto-arancel" value="${a.monto == null ? '' : Number(a.monto).toFixed(2)}" placeholder="Sin definir" aria-label="Arancel de ${esc(a.sacramento)}"></td>
          <td><button class="boton-mini aprobar" data-guardar>Guardar</button> <small class="estado-fila"></small></td>
        </tr>`).join('');
    }).catch(() => aviso('aviso-cuenta', SIN_CONEXION, false));

    const guardar = (campos, idAviso) => enviar('/api/admin/pagos/config', new URLSearchParams(campos))
      .then(({ ok, data }) => { aviso(idAviso, ok ? 'Guardado.' : (data.error || 'No se pudo guardar.'), ok); return ok; })
      .catch(() => { aviso(idAviso, SIN_CONEXION, false); return false; });

    formCuenta.addEventListener('submit', (e) => {
      e.preventDefault();
      const f = formCuenta.elements;
      guardar({ accion: 'cuenta', yape_numero: f.yape_numero.value, yape_titular: f.yape_titular.value, plin_numero: f.plin_numero.value, plin_titular: f.plin_titular.value }, 'aviso-cuenta').then(cargar);
    });
    formOfrenda.addEventListener('submit', (e) => {
      e.preventDefault();
      guardar({ accion: 'ofrenda', monto: formOfrenda.elements.monto.value }, 'aviso-ofrenda').then(cargar);
    });
    tabla.addEventListener('click', (e) => {
      const b = e.target.closest('[data-guardar]');
      if (!b) return;
      const fila = b.closest('tr'), estadoFila = fila.querySelector('.estado-fila');
      const monto = fila.querySelector('.monto-arancel').value.trim();
      if (!monto) { estadoFila.textContent = 'Escribe un monto (0 si es gratuito).'; return; }
      b.disabled = true;
      enviar('/api/admin/pagos/config', new URLSearchParams({ accion: 'arancel', id_sacramento: fila.dataset.sacramento, monto }))
        .then(({ ok, data }) => { estadoFila.textContent = ok ? 'Guardado.' : (data.error || 'No se pudo guardar.'); })
        .catch(() => { estadoFila.textContent = SIN_CONEXION; })
        .finally(() => { b.disabled = false; });
    });
    cargar();
  }

  /* ==================== PANEL: admin/intenciones.html (agenda del párroco) ==================== */
  function iniciarAgendaIntenciones() {
    const cont = q('agenda-intenciones');
    if (!cont) return;
    const nota = q('nota-agenda');
    const desde = q('agenda-desde'), hasta = q('agenda-hasta');
    const hoy = new Date();
    desde.value = aIso(hoy);
    hasta.value = aIso(sumarDias(hoy, 14));

    const cargar = () => {
      nota.textContent = '';
      return pedir(`/api/admin/intenciones?desde=${encodeURIComponent(desde.value)}&hasta=${encodeURIComponent(hasta.value)}`).then(({ ok, data }) => {
        if (!ok) { cont.innerHTML = ''; nota.textContent = data.error || 'No se pudo cargar la agenda.'; return; }
        if (!data.length) { cont.innerHTML = ''; nota.textContent = 'No hay intenciones con pago verificado en este rango.'; return; }
        const porMisa = new Map();
        data.forEach((i) => { const k = i.fecha + '|' + i.hora; if (!porMisa.has(k)) porMisa.set(k, []); porMisa.get(k).push(i); });
        cont.innerHTML = Array.from(porMisa.entries()).map(([k, lista]) => {
          const primera = lista[0];
          return `<section class="agenda-misa">
            <h3>${esc(nombreDia(primera.fecha))} ${formatearFecha(primera.fecha)} · ${esc(primera.hora)}</h3>
            <ul>${lista.map((i) => `<li>${esc(i.etiqueta)} <strong>${esc(i.texto)}</strong><small> — ofrece ${esc(i.solicitante)}</small></li>`).join('')}</ul>
          </section>`;
        }).join('');
      }).catch(() => { nota.textContent = SIN_CONEXION; });
    };
    q('btn-agenda').addEventListener('click', cargar);
    q('btn-imprimir').addEventListener('click', () => window.print());
    cargar();
  }

  document.addEventListener('DOMContentLoaded', () => {
    iniciarPaginaPagos();
    iniciarPaginaIntenciones();
    iniciarBandeja();
    iniciarPanelPagos();
    iniciarPanelCobros();
    iniciarAgendaIntenciones();
  });
})();
