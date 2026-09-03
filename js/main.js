// Prototipo estático — no hay servidor detrás.
// Estas funciones solo simulan la respuesta del sistema para la demo.

document.addEventListener('DOMContentLoaded', () => {

  // Menú hamburguesa en móvil: se agrega automáticamente a cualquier página con .barra-nav
  document.querySelectorAll('.barra-nav').forEach((nav) => {
    const objetivo = nav.querySelector('.nav-derecha') || nav.querySelector('.nav-links');
    if (!objetivo) return;
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'nav-toggle';
    boton.setAttribute('aria-label', 'Abrir menú de navegación');
    boton.textContent = '☰';
    boton.addEventListener('click', () => {
      const abierto = objetivo.classList.toggle('abierta');
      boton.textContent = abierto ? '✕' : '☰';
      boton.setAttribute('aria-label', abierto ? 'Cerrar menú de navegación' : 'Abrir menú de navegación');
    });
    nav.insertBefore(boton, objetivo);
  });

  // Formulario de reserva de sacramentos
  const formReserva = document.getElementById('form-reserva');
  if (formReserva) {
    formReserva.addEventListener('submit', (e) => {
      e.preventDefault();
      const mensaje = document.getElementById('mensaje-reserva');
      mensaje.classList.add('visible');
      mensaje.textContent = 'Solicitud registrada (simulación). En la versión final esto quedará guardado en la base de datos de la parroquia.';
      formReserva.querySelector('.boton-primario').setAttribute('disabled', 'true');
    });
  }

  // Zona de carga de documentos
  const zonaCarga = document.getElementById('zona-carga');
  if (zonaCarga) {
    zonaCarga.addEventListener('click', () => {
      const mensaje = document.getElementById('mensaje-documento');
      mensaje.classList.add('visible');
      mensaje.textContent = 'Documento recibido (simulación). Aquí se conectará la subida real de archivos.';
    });
  }

  // Buscador de iglesias (filtra las tarjetas visibles, solo del lado cliente)
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
      if (sinResultados) {
        sinResultados.classList.toggle('visible', visibles === 0);
      }
    });
  }

  // Panel de administrador: aprobar / rechazar documentos (solo cambia el estado en pantalla)
  document.querySelectorAll('[data-accion="aprobar"], [data-accion="rechazar"]').forEach((boton) => {
    boton.addEventListener('click', () => {
      const fila = boton.closest('tr');
      const insignia = fila.querySelector('.insignia');
      if (boton.dataset.accion === 'aprobar') {
        insignia.textContent = 'Validado';
        insignia.className = 'insignia validado';
      } else {
        insignia.textContent = 'Rechazado';
        insignia.className = 'insignia rechazado';
      }
      fila.querySelectorAll('.boton-mini').forEach((b) => b.setAttribute('disabled', 'true'));
    });
  });

  // Formulario de publicar aviso (admin)
  const formAviso = document.getElementById('form-aviso');
  if (formAviso) {
    formAviso.addEventListener('submit', (e) => {
      e.preventDefault();
      const mensaje = document.getElementById('mensaje-aviso');
      mensaje.classList.add('visible');
      mensaje.textContent = 'Aviso publicado (simulación). En la versión final aparecerá de inmediato en la app de los feligreses.';
      formAviso.querySelector('.boton-primario').setAttribute('disabled', 'true');
    });
  }

  // Formulario de horarios (admin)
  const formHorarios = document.getElementById('form-horarios');
  if (formHorarios) {
    formHorarios.addEventListener('submit', (e) => {
      e.preventDefault();
      const mensaje = document.getElementById('mensaje-horarios');
      mensaje.classList.add('visible');
      mensaje.textContent = 'Cambios guardados (simulación). Los feligreses verán el horario actualizado.';
    });
  }
});
