// Prototipo estático — no hay servidor detrás.
// Estas funciones solo simulan la respuesta del sistema para la demo.

document.addEventListener('DOMContentLoaded', () => {

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
  if (buscador) {
    buscador.addEventListener('input', (e) => {
      const termino = e.target.value.toLowerCase();
      document.querySelectorAll('.tarjeta-iglesia').forEach((tarjeta) => {
        const texto = tarjeta.textContent.toLowerCase();
        tarjeta.style.display = texto.includes(termino) ? '' : 'none';
      });
    });
  }
});
