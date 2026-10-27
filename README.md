# Camino a la Iluminación

Software para parroquias católicas — Curso Integrador I.

## Integrantes
- Leo
- Edgardo
- Fernando

## Estado actual
Aplicación web con backend Java (servlets, patrón DAO, JDBC) y base de datos MySQL/MariaDB.
Ya funcionan de verdad (sin simulaciones): registro e inicio de sesión con **DNI**, roles
(feligrés / personal de una parroquia), reservas de sacramentos con control de fechas,
subida y validación de documentos, y parroquias, horarios y avisos desde la base de datos.
También funcionan: sacramentos normalizados (catálogo y relación con las parroquias), reportes reales con
descarga a Excel, bitácora de acciones, capa de servicios y pruebas JUnit (`mvn test` en `backend/`).
Pagos por **Yape y Plin** con verificación manual (arancel de reservas, donaciones y diezmos), con el número de cobro de
cada parroquia, comprobante en el almacén privado y avisos dentro de la plataforma. No hay pasarela ni se reciben datos de tarjeta.
Incluye **intenciones de misa con ofrenda** (cupo por misa, agenda para el personal) y **recibo en PDF** de cada pago aprobado.
Pendiente: despliegue como WAR y un antivirus para los archivos subidos.
Para ponerlo en marcha: `backend/README.md` (variables de entorno, scripts SQL y migraciones).

## Estructura del proyecto
```
camino-a-la-iluminacion/
├── backend/              → backend en Java (Maven, JDBC, patrón DAO) — ver backend/README.md
├── database/             → script SQL de la base de datos
├── index.html            → Inicio (feligrés)
├── css/estilos.css       → estilos compartidos
├── js/main.js            → lógica del navegador (llama a la API /api/...)
├── paginas/
│   ├── login.html / registro.html → acceso con DNI
│   ├── horarios.html
│   ├── notificaciones.html
│   ├── mapa.html
│   ├── iglesia.html
│   ├── reservas.html
│   ├── documentos.html
│   └── admin/            → panel de personal parroquial
│       ├── documentos.html   → validar documentos
│       ├── avisos.html       → publicar aviso
│       ├── horarios.html     → gestionar horarios
│       └── reportes.html     → trámites por sacramento
```

## Backend y base de datos
- `database/migracion_07_pagos.sql` — pagos Yape/Plin por parroquia, aranceles y notificaciones (hacer copia antes).
- `database/migracion_08_intenciones_misa.sql` — intenciones de misa y ofrenda por parroquia (hacer copia antes; requiere la 07).
- `database/migracion_05_normalizacion_bitacora.sql` — normaliza sacramentos y agrega la bitácora (hacer copia antes).
- `database/migracion_01_dni_roles.sql` — migración para una base creada antes del acceso por DNI.
- `database/camino_iluminacion.sql` — script con las 6 tablas del modelo entidad-relación (Capítulo 8.3) y datos de ejemplo.
- `backend/` — proyecto Maven en Java con modelos y DAO para las 6 tablas. Ver `backend/README.md` para instrucciones de configuración y ejecución.

## Cómo ver el prototipo
No necesita servidor: clona el repositorio y abre `index.html` en el navegador.

## Cómo contribuir
1. Trae los últimos cambios: `git pull`
2. Crea una rama para tu tarea: `git checkout -b nombre-de-tu-cambio`
3. Haz tus cambios y confírmalos: `git add . && git commit -m "Descripción breve"`
4. Sube tu rama: `git push origin nombre-de-tu-cambio`
5. Abre un Pull Request en GitHub para que el equipo revise antes de fusionar a `main`.

## Seguridad de los datos (clave de cifrado del DNI)

El DNI se guarda **cifrado** (AES-256-GCM) en `usuario.dni` y con una huella HMAC en `usuario.dni_indice` para poder
buscar y evitar repetidos. La clave NO está en el código ni en la base de datos:

- **Recomendado (y obligatorio en el despliegue):** variable de entorno `CAI_DATA_KEY` con 32 bytes en base64.
  Para generar una: `[Convert]::ToBase64String((1..32 | % { Get-Random -Maximum 256 }))` (PowerShell) u
  `openssl rand -base64 32`. En SmartTomcat: *Run > Edit Configurations > Environment variables*.
- **Solo desarrollo:** si no defines nada, la aplicación crea el archivo `.data-key` (ignorado por git) en su carpeta de
  trabajo. **Respáldalo**: sin la clave no se pueden leer los DNI ya cifrados. Todos los integrantes que compartan una
  base de datos deben usar LA MISMA clave.
- Base existente: aplica `database/migracion_06_dni_cifrado.sql`; al arrancar, la aplicación cifra sola los DNI pendientes.
- Además: cabeceras de seguridad en todas las respuestas (`CabecerasSeguridad`: CSP, nosniff, anti-frames, HSTS con HTTPS),
  PDF con scripts o archivos incrustados rechazados al subir, y fuentes y mapa (Leaflet) servidos desde `vendor/`.

## Librerías de apoyo (APF3)
| Librería | Para qué se usa | Dónde |
| --- | --- | --- |
| Apache POI | Reportes en Excel (.xlsx); el texto va siempre como celda de texto (sin inyección de fórmulas) | `util/ExcelReporte` |
| Logback (SLF4J) | Registro de eventos con archivo rotativo diario (`CAI_LOG_DIR` o `~/camino_iluminacion_logs`) | `logback.xml`, `util/Registro` |
| Google Guava | Caché con expiración para el límite de intentos de login; `ImmutableSet`, `Strings` | `util/LimiteIntentos`, `servicio/PagoService` |
| Apache Commons Codec | SHA-256 del comprobante para detectar un voucher reutilizado | `servicio/PagoService` |
