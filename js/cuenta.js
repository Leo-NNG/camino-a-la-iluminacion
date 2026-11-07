// Camino a la Iluminación — "Mi cuenta" (Ley n.° 29733). Todo lo decide el servidor: aquí solo se muestran los datos
// con textContent (nunca innerHTML) y se envían los formularios a /api/cuenta/...
(function () {
  'use strict';

  const $ = (id) => document.getElementById(id);

  function mostrar(alerta, texto, tipo) {
    alerta.textContent = texto;
    alerta.className = 'alerta-auth visible ' + tipo;
  }

  async function enviar(ruta, datos) {
    const r = await fetch('/api/cuenta' + ruta, { method: 'POST', body: new URLSearchParams(datos), credentials: 'same-origin' });
    const d = await r.json().catch(() => ({}));
    return { ok: r.ok, mensaje: d.mensaje || d.error || 'No se pudo completar la operación.' };
  }

  async function cargar() {
    const r = await fetch('/api/cuenta', { cache: 'no-store', credentials: 'same-origin' });
    if (!r.ok) return;
    const p = await r.json();
    $('d-nombre').textContent = p.nombre + ' ' + p.apellido;
    $('d-dni').textContent = p.dni;
    $('d-desde').textContent = p.creadoEn;
    $('c-correo').value = p.correo || '';
    $('c-telefono').value = p.telefono || '';
    const panel = $('panel-politica');
    panel.hidden = p.politicaAceptada;
    $('texto-politica').textContent = p.politicaVersionAceptada
      ? 'Aceptaste una versión anterior de la Política de privacidad. Para seguir usando tu cuenta con tranquilidad, acepta la versión vigente (' + p.politicaVigente + ').'
      : 'Todavía no has aceptado la Política de privacidad (versión ' + p.politicaVigente + ').';
    if (p.rol !== 'feligres') {
      $('panel-eliminar').hidden = true;
      $('nota-personal').hidden = false;
    }
  }

  function enganchar() {
    $('btn-politica').addEventListener('click', async () => {
      const res = await enviar('/politica', {});
      mostrar($('alerta-politica'), res.mensaje, res.ok ? 'exito' : 'error');
      if (res.ok) setTimeout(cargar, 800);
    });

    $('form-contacto').addEventListener('submit', async (e) => {
      e.preventDefault();
      const correo = $('c-correo').value.trim();
      const telefono = $('c-telefono').value.trim();
      const alerta = $('alerta-contacto');
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(correo)) return mostrar(alerta, 'Ingresa un correo válido.', 'error');
      if (telefono && !/^[0-9]{7,9}$/.test(telefono)) return mostrar(alerta, 'El teléfono debe tener de 7 a 9 dígitos.', 'error');
      const res = await enviar('/contacto', { correo: correo.toLowerCase(), telefono: telefono });
      mostrar(alerta, res.mensaje, res.ok ? 'exito' : 'error');
    });

    $('form-clave').addEventListener('submit', async (e) => {
      e.preventDefault();
      const alerta = $('alerta-clave');
      const actual = $('k-actual').value;
      const nueva = $('k-nueva').value;
      if (!actual) return mostrar(alerta, 'Escribe tu contraseña actual.', 'error');
      if (nueva.length < 6) return mostrar(alerta, 'La nueva contraseña debe tener al menos 6 caracteres.', 'error');
      if (nueva !== $('k-nueva2').value) return mostrar(alerta, 'Las contraseñas nuevas no coinciden.', 'error');
      const res = await enviar('/contrasena', { actual: actual, nueva: nueva });
      mostrar(alerta, res.mensaje, res.ok ? 'exito' : 'error');
      if (res.ok) ['k-actual', 'k-nueva', 'k-nueva2'].forEach((id) => { $(id).value = ''; });
    });

    $('form-eliminar').addEventListener('submit', async (e) => {
      e.preventDefault();
      const alerta = $('alerta-eliminar');
      const clave = $('e-clave').value;
      if (!clave) return mostrar(alerta, 'Escribe tu contraseña para confirmar.', 'error');
      if (!window.confirm('¿Eliminar tu cuenta de forma definitiva? Esta acción no se puede deshacer.')) return;
      const res = await enviar('/eliminar', { contrasena: clave });
      if (res.ok) {
        mostrar(alerta, res.mensaje + ' Te llevamos al inicio…', 'exito');
        setTimeout(() => { window.location.href = rutaIndex(); }, 1500);
      } else {
        mostrar(alerta, res.mensaje, 'error');
      }
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    // main.js ya cargó la sesión y aplicó la guarda; si no hay sesión, redirigió al login.
    enganchar();
    cargar();
  });
})();
