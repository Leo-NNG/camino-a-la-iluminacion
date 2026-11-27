/* Registro: autocompletar nombres y apellidos a partir del DNI (consulta Factiliza hecha por el servidor).
   Es solo una ayuda: si la consulta no está configurada o falla, el feligrés escribe sus datos a mano.
   Los campos siempre quedan editables y el envío del formulario no depende de esta consulta. */
document.addEventListener('DOMContentLoaded', () => {
  const campoDni = document.getElementById('reg-dni');
  const estado = document.getElementById('estado-dni');
  if (!campoDni || !estado) return;
  const campoNombre = document.getElementById('reg-nombre');
  const campoApellido = document.getElementById('reg-apellido');

  let disponible = false;
  let ultimoDni = '';
  let autocompletado = { nombre: '', apellido: '' };

  // "MARIA JOSE" -> "Maria Jose"; respeta guiones y la ñ.
  const formatoNombre = (texto) => texto.toLocaleLowerCase('es-PE')
    .replace(/(^|[\s-])(\p{L})/gu, (_, sep, letra) => sep + letra.toLocaleUpperCase('es-PE'));

  // Solo se reemplaza lo que está vacío o lo que pusimos nosotros: nunca lo que escribió la persona.
  const puedeReemplazar = (campo, clave) => !campo.value.trim() || campo.value === autocompletado[clave];

  const consultar = () => {
    const dni = campoDni.value.trim();
    if (!disponible || !/^[0-9]{8}$/.test(dni) || dni === ultimoDni) return;
    ultimoDni = dni;
    estado.textContent = 'Buscando tus datos…';
    fetch('/api/consulta-dni', { method: 'POST', body: new URLSearchParams({ dni }), credentials: 'same-origin' })
      .then((r) => r.json().catch(() => ({})).then((data) => ({ ok: r.ok, estadoHttp: r.status, data })))
      .then(({ ok, estadoHttp, data }) => {
        if (campoDni.value.trim() !== dni) return; // cambió el DNI mientras se consultaba
        if (!ok || !data.exito) {
          if (estadoHttp === 502) ultimoDni = ''; // falla pasajera: se puede reintentar al salir del campo
          estado.textContent = data.error || 'No pudimos consultar el DNI. Escribe tus nombres y apellidos.';
          return;
        }
        const nombre = formatoNombre(data.nombres || '');
        const apellido = formatoNombre(data.apellidos || '');
        if (puedeReemplazar(campoNombre, 'nombre')) { campoNombre.value = nombre; autocompletado.nombre = nombre; }
        if (puedeReemplazar(campoApellido, 'apellido')) { campoApellido.value = apellido; autocompletado.apellido = apellido; }
        estado.textContent = 'Completamos tus nombres con los datos del DNI. Revísalos y corrígelos si hace falta.';
      })
      .catch(() => { estado.textContent = 'No pudimos consultar el DNI. Escribe tus nombres y apellidos.'; });
  };

  fetch('/api/consulta-dni', { cache: 'no-store', credentials: 'same-origin' })
    .then((r) => (r.ok ? r.json() : { disponible: false }))
    .then((d) => {
      disponible = !!(d && d.disponible);
      if (disponible) {
        estado.textContent = 'Al escribir tu DNI completaremos tus nombres y apellidos.';
        consultar(); // por si el navegador ya autocompletó el DNI
      }
    })
    .catch(() => { disponible = false; });

  campoDni.addEventListener('input', () => { if (campoDni.value.trim().length === 8) consultar(); });
  campoDni.addEventListener('blur', consultar);
});
