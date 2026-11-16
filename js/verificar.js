// Camino a la Iluminación — verificación pública de constancias y recibos (no pide sesión).
// El servidor decide todo (formato, límite de intentos, vigencia); aquí solo se muestra con textContent, nunca innerHTML.
(function () {
  'use strict';

  const $ = (id) => document.getElementById(id);
  const form = $('form-verificar');
  if (!form) return;
  const campo = $('codigo'), boton = $('boton-verificar'), resultado = $('resultado'), estado = $('resultado-estado'), datos = $('resultado-datos'), alerta = $('alerta-verificar');

  function fila(etiqueta, valor) {
    if (!valor) return;
    const dt = document.createElement('dt'); dt.textContent = etiqueta;
    const dd = document.createElement('dd'); dd.textContent = valor;
    datos.append(dt, dd);
  }

  function fechaLegible(iso) {
    const p = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso || '');
    return p ? p[3] + '/' + p[2] + '/' + p[1] : iso;
  }

  function mostrar(r) {
    alerta.className = 'alerta-auth';
    resultado.hidden = false;
    resultado.className = 'resultado-verificacion ' + (r.vigente ? 'vigente' : 'no-vigente');
    estado.textContent = (r.vigente ? '✔ ' : '✖ ') + r.estado;
    datos.textContent = '';
    fila('Documento', r.tipo === 'constancia' ? 'Constancia de reserva' : 'Recibo de pago');
    fila('Parroquia', r.parroquia);
    fila(r.tipo === 'constancia' ? 'Sacramento' : 'Concepto', r.detalle);
    fila(r.tipo === 'constancia' ? 'Fecha de la solicitud' : 'Fecha de verificación del pago', fechaLegible(r.fecha));
    fila('Monto', r.monto);
    fila('Solicitante (iniciales)', r.solicitante);
  }

  function error(texto) {
    resultado.hidden = true;
    alerta.textContent = texto;
    alerta.className = 'alerta-auth visible error';
  }

  function verificar() {
    const codigo = campo.value.trim();
    if (!codigo) return error('Escribe el código del documento.');
    boton.disabled = true;
    alerta.className = 'alerta-auth';
    fetch('/api/verificar?codigo=' + encodeURIComponent(codigo), { cache: 'no-store', credentials: 'same-origin' })
      .then((r) => r.json().catch(() => ({})).then((d) => ({ ok: r.ok, d })))
      .then(({ ok, d }) => { if (ok) mostrar(d); else error(d.error || 'No se pudo verificar el código.'); })
      .catch(() => error('No se pudo conectar con el servidor.'))
      .finally(() => { boton.disabled = false; });
  }

  form.addEventListener('submit', (e) => { e.preventDefault(); verificar(); });

  // El código impreso en el PDF trae un enlace con ?codigo=... y se verifica solo.
  document.addEventListener('DOMContentLoaded', () => {
    const previo = new URLSearchParams(window.location.search).get('codigo');
    if (previo) { campo.value = previo.slice(0, 20); verificar(); }
  });
})();
