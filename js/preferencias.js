/* Preferencias de accesibilidad: se aplican en el <head>, antes de pintar la página, para que no "salte".
 * Solo guarda en este navegador el tamaño de letra (0 a 3) y si el alto contraste está activo; nada personal. */
(function () {
  var raiz = document.documentElement;
  var letra = 0;
  var contraste = null;
  try {
    letra = parseInt(localStorage.getItem('cai-letra') || '0', 10) || 0;
    contraste = localStorage.getItem('cai-contraste');
  } catch (e) { /* sin acceso a localStorage: valores por defecto */ }
  if (letra >= 1 && letra <= 3) raiz.classList.add('letra-' + letra);
  // Sin elección guardada, se respeta la preferencia del sistema operativo ("más contraste").
  if (contraste === '1' || (contraste === null && window.matchMedia && window.matchMedia('(prefers-contrast: more)').matches)) {
    raiz.classList.add('alto-contraste');
  }
})();
