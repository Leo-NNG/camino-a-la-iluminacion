/* Service worker de Camino a la Iluminación (PWA instalable).
 *
 * Reglas:
 *  - /api/* NUNCA pasa por la caché: datos personales y siempre frescos.
 *  - Las páginas HTML no se guardan (pueden ser privadas, p. ej. el panel): se piden a la red y, si no hay
 *    conexión, se muestra paginas/offline.html.
 *  - CSS, JS, fuentes e íconos: primero la red (así un despliegue nuevo se ve al instante) y, sin conexión,
 *    la última copia guardada.
 * Al cambiar algo de esta lista o de la lógica, sube VERSION para que se renueve la caché.
 */
const VERSION = 'cai-v1';
const SIN_CONEXION = '/paginas/offline.html';
const PRECARGA = [
  SIN_CONEXION,
  '/css/estilos.css',
  '/css/ios.css',
  '/css/accesibilidad.css',
  '/js/preferencias.js',
  '/js/main.js',
  '/iconos/icono-192.png',
  '/iconos/favicon-32.png',
  '/vendor/inter/inter-latin-400-normal.woff2',
  '/vendor/inter/inter-latin-600-normal.woff2'
];
const ESTATICOS = /^\/(css|js|vendor|iconos)\//;

self.addEventListener('install', (evento) => {
  evento.waitUntil(caches.open(VERSION).then((cache) => cache.addAll(PRECARGA)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (evento) => {
  evento.waitUntil(
    caches.keys()
      .then((nombres) => Promise.all(nombres.filter((n) => n !== VERSION).map((n) => caches.delete(n))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (evento) => {
  const pedido = evento.request;
  if (pedido.method !== 'GET') return;
  const url = new URL(pedido.url);
  if (url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return;

  if (pedido.mode === 'navigate') {
    evento.respondWith(fetch(pedido).catch(() => caches.match(SIN_CONEXION)));
    return;
  }
  if (ESTATICOS.test(url.pathname)) {
    evento.respondWith(
      fetch(pedido)
        .then((respuesta) => {
          if (respuesta.ok && respuesta.type === 'basic') {
            const copia = respuesta.clone();
            caches.open(VERSION).then((cache) => cache.put(pedido, copia));
          }
          return respuesta;
        })
        .catch(() => caches.match(pedido, { ignoreSearch: true }).then((r) => r || Response.error()))
    );
  }
});
