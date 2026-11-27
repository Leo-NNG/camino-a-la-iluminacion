# Backend — Camino a la Iluminación

Backend en Java 17 (Jakarta Servlets para Tomcat 10, JDBC + patrón DAO). Arquitectura en capas
según el Capítulo 8.1 del informe: Presentación → Lógica de negocio → DAO → BD. Se empaqueta como
`ROOT.war`, que trae también el frontend (`index.html`, `paginas/`, `css/`, `js/`, `vendor/`).

## Requisitos
- JDK 17 o superior
- Maven 3.9 (o el que trae IntelliJ)
- MySQL 8 o MariaDB 10.6+ con la base ya creada: correr primero `../database/camino_iluminacion.sql`
- Tomcat 10.1 para ejecutar la aplicación web

## Configurar (variables de entorno)
Toda la configuración sale de variables de entorno: en el código no hay usuarios, contraseñas ni tokens.

| Variable | ¿Obligatoria? | Por defecto | Para qué |
|---|---|---|---|
| `DB_USER` | **Sí** | — | Usuario de MySQL/MariaDB. En un despliegue, un usuario de aplicación sin privilegios de administrador. |
| `DB_PASSWORD` | **Sí** | — | Contraseña de ese usuario. |
| `DB_HOST` | No | `localhost` | Servidor de la base de datos. |
| `DB_PORT` | No | `3306` | Puerto. |
| `DB_NAME` | No | `camino_iluminacion` | Nombre de la base. |
| `CAI_DATA_KEY` | **Sí en despliegue** | — | Clave de cifrado del DNI: 32 bytes en base64 (ver el README principal, «Seguridad de los datos»). |
| `CAI_DATA_KEY_FILE` | No | `.data-key` en la carpeta de trabajo | Solo si no hay `CAI_DATA_KEY`: archivo donde se lee (o, en desarrollo, se crea) la clave. Respáldalo. |
| `DOCS_DIR` | No | `~/camino_iluminacion_docs` | Carpeta de documentos y comprobantes subidos (fuera de la carpeta pública). |
| `CAI_LOG_DIR` | No | `~/camino_iluminacion_logs` | Carpeta de los registros de Logback (un archivo por día, 30 días). |
| `CAI_URL_PUBLICA` | No | la dirección de la petición | Dirección que se imprime en los PDF verificables, p. ej. `https://miparroquia.pe`. |
| `FACTILIZA_TOKEN` | No | *(vacía)* | Token de la API de Factiliza para autocompletar nombres por DNI en el registro. Sin él, los nombres se escriben a mano. Nunca va en el código ni llega al navegador. |

Si falta `DB_USER` o `DB_PASSWORD`, cada consulta a la base falla con un mensaje que dice qué variable falta
(ya no se intenta entrar como `root` con una contraseña fija).

En Windows (PowerShell), para tu usuario; luego **cierra y vuelve a abrir** la terminal, IntelliJ o Tomcat:
```powershell
[Environment]::SetEnvironmentVariable('DB_USER', 'root', 'User')
[Environment]::SetEnvironmentVariable('DB_PASSWORD', (Read-Host 'Contraseña de MySQL'), 'User')
```
En SmartTomcat: *Run > Edit Configurations > Environment variables*. En un servicio de Tomcat, en `bin/setenv.bat`
(o `setenv.sh`), que no se sube al repositorio.

## Base de datos
- **Base nueva:** ejecutar `../database/camino_iluminacion.sql` (ya incluye DNI y parroquia del administrador).
- **Base creada con la versión anterior:** ejecutar `../database/migracion_01_dni_roles.sql`
  (lee primero la advertencia del encabezado: borra las cuentas registradas a mano, que no tenían DNI).

  Después ejecutar `../database/migracion_02_reservas.sql` (agrega `motivo_rechazo`; **sin ella fallan todas las consultas de reservas**).

## Reglas de reservas
- Solo reservan los **feligreses** (el personal no). La fecha debe ser futura y de hasta 2 años.
- **Disponibilidad:** no puede haber dos solicitudes activas (pendiente o confirmada) del mismo sacramento, en la misma parroquia y la misma fecha. La comprobación y el registro van en una transacción que bloquea la fila de la parroquia. Una solicitud rechazada libera la fecha.
- El personal confirma o rechaza **solo solicitudes pendientes** de su parroquia; **rechazar exige un motivo** (hasta 255 caracteres), que el feligrés ve en "Mis solicitudes" (`/api/mis-reservas`).

  Luego `../database/migracion_03_documentos.sql` (agrega `tipo_mime` y `tamano_bytes`; **borra los 4 documentos de ejemplo**, que apuntaban a archivos inexistentes).

  Luego `../database/migracion_04_parroquias_horarios_avisos.sql` (agrega fotos y horario de trámites a `parroquia`; **reemplaza todos los horarios y avisos por los de ejemplo de Chiclayo**; no toca usuarios, reservas ni documentos).

  Por último `../database/migracion_05_normalizacion_bitacora.sql` (**haz antes una copia con `mysqldump`**: cambia la estructura y no se puede deshacer sola). Crea `sacramento` y `parroquia_sacramento` (llenándola con lo que ya tenías en `parroquia.sacramentos_disponibles`), convierte `reserva.sacramento` en la clave foránea `id_sacramento` conservando todas las reservas, y agrega `documento.id_validador`, `aviso.id_autor` y la tabla `bitacora`.

## Arquitectura en capas (MVC + DAO + servicios)
`HTML/JS (vista)` → `servlets (controlador)` → `servicios (reglas de negocio)` → `DAO (JDBC)` → MySQL/MariaDB.
- **Servlets** (`controlador/`): leen la petición, llaman a un servicio y escriben el JSON. No contienen reglas.
- **Servicios** (`servicio/`): `UsuarioService`, `ReservaService`, `DocumentoService`, `ContenidoService` (avisos, horarios, sacramentos de la parroquia) y `ReporteService`. Validan, aplican permisos por parroquia, registran la bitácora y lanzan `ReglaNegocioException` (con su código HTTP) cuando una regla no se cumple. No conocen HTTP: se prueban con DAO falsos.
- **DAO** (`dao/`, `dao/impl/`): una interfaz por entidad y su implementación JDBC con consultas parametrizadas. `Servicios` es el único lugar donde se ensamblan con los DAO reales.
- Las lecturas públicas simples (parroquias, horarios y avisos publicados, catálogo de sacramentos) consultan el DAO directamente.

## Bitácora y trazabilidad (RNF09)
Tabla `bitacora`: quién, qué acción, un detalle breve y la IP. Se registran ingresos (correctos y fallidos), salidas, registros, reservas (creadas, confirmadas, rechazadas), documentos (subidos, validados, rechazados), avisos, horarios, sacramentos y exportaciones de reportes. **No guarda DNI, contraseñas, nombres de archivo ni contenido.** Si la bitácora falla, la acción del usuario no se cancela (queda el error en el log). Además `documento.id_validador` y `aviso.id_autor` guardan quién actuó. Consulta de ejemplo:
```sql
SELECT b.creado_en, b.accion, b.detalle, b.ip, u.nombre, u.apellido
FROM bitacora b LEFT JOIN usuario u ON u.id_usuario = b.id_usuario
ORDER BY b.creado_en DESC LIMIT 50;
```

## Reportes (panel del administrador)
`GET /api/admin/reportes?tipo=tramites|documentos|agenda|ingresos[&mes=AAAA-MM][&formato=xlsx]`, siempre de **la parroquia del administrador**. Sin `formato` devuelve JSON para las tablas de la pantalla; con `formato=xlsx` descarga un **Excel real** generado con Apache POI en `util/ExcelReporte` (los textos se guardan como celda de texto, así que un valor que empiece con `=` nunca se ejecuta como fórmula; los importes llevan formato `S/ #,##0.00`).
- R1 trámites por sacramento y mes, con totales y columna **Canceladas** («Solicitudes» incluye las canceladas por el feligrés).
- R2 documentos pendientes con días de espera. R3 agenda de celebraciones confirmadas.
- R4 **ingresos verificados** (pagos aprobados) por mes de pago: hoja «Resumen de ingresos» (concepto × Yape/Plin, con total) y hoja «Detalle de pagos» (fecha, feligrés, concepto, método, n.º de operación, monto y código de recibo).

`GET /api/admin/resumen` alimenta el **resumen (dashboard)** de `paginas/admin/resumen.html`, que es la página de inicio del panel: solicitudes por confirmar, documentos por validar, pagos por verificar, celebraciones e intenciones (con pago verificado) de los próximos 7 días, y solicitudes, cancelaciones e ingresos del mes en curso. Las fechas se calculan con la hora de Perú. No requiere migración.

## Seguridad adicional
- **CSRF:** `FiltroOrigen` exige que toda petición que cambia datos bajo `/api/` venga del propio sitio (`Sec-Fetch-Site` same-origin y `Origin` igual al `Host`). Si pones un proxy inverso delante de Tomcat, debe **conservar el encabezado `Host` original**; de lo contrario las peticiones legítimas se rechazarán con 403.
- **Cookie de sesión:** `ConfiguracionSesion` la marca `HttpOnly` y `SameSite=Lax`. Con HTTPS agrega también `Secure` (`setSecure(true)`).
- Los errores del servidor se registran con Logback/SLF4J (`util/Registro`, archivos en `CAI_LOG_DIR`); al navegador nunca se le envían detalles internos.

## Pruebas automáticas
`mvn test` (desde `backend/`). JUnit 5, sin base de datos ni Tomcat: servlets y servicios reales con DAO falsos en memoria.
- **Pruebas unitarias:** `util/` (claves con hash, límite de intentos, JSON, almacén de archivos, Excel), `servicio/` (reservas con reloj fijo, usuarios, reportes, consulta de DNI con cliente HTTP falso), `filtro/` (CSRF, cookie de sesión) y `conexion/` (variables de entorno obligatorias).
- **Escenarios** (`controlador/EscenariosTest`): recorridos completos de acceso por DNI y roles, reservas, documentos, parroquias/horarios/avisos y sacramentos, incluyendo ataques típicos (otra parroquia, archivos disfrazados, entradas maliciosas).
- Lo que NO cubren: el SQL real de los DAO (hay que probarlo contra MySQL/MariaDB), Tomcat y el navegador.

## Parroquias, horarios y avisos (fuente única: la base de datos)
- **Públicos, sin iniciar sesión:** `GET /api/parroquias[?id=N]` (mapa, fichas y selector de reservas), `GET /api/horarios?parroquia=N` y `GET /api/avisos?parroquia=N` (o `?urgentes=1`: los 3 urgentes más recientes de todas las parroquias, que el sitio muestra como alertas flotantes). Solo se publican URLs de foto `http(s)`.
- **Panel del administrador, solo de SU parroquia** (la parroquia sale de la sesión, nunca de un parámetro): `/api/admin/avisos` (listar, publicar, eliminar) y `/api/admin/horarios` (listar, agregar, modificar, eliminar). Un registro de otra parroquia se trata como inexistente (404).
- **Reservas:** la parroquia se elige por **id** y debe **ofrecer el sacramento** elegido (campo `sacramentos_disponibles`); el formulario solo lista las parroquias que lo ofrecen.
- Las distancias del mapa se calculan con la ubicación real del navegador (si el usuario la permite); ya no hay distancias fijas.
- Los sacramentos están normalizados (ver la migración 05): catálogo `sacramento` y relación `parroquia_sacramento`. El administrador marca lo que ofrece su parroquia en *Gestionar horarios*.

## Documentos (subida real)
- Variable de entorno **`DOCS_DIR`**: carpeta donde se guardan los archivos (por defecto `~/camino_iluminacion_docs`). Debe estar **fuera** de la carpeta pública de la aplicación y con permisos de escritura para el usuario que ejecuta Tomcat. Respáldala junto con la base de datos.
- Los comprobantes de pago (Yape/Plin) se guardan en la **misma** carpeta `DOCS_DIR` (con nombre aleatorio); la base de datos solo guarda el nombre. Aplica `database/migracion_07_pagos.sql` y luego `database/migracion_08_intenciones_misa.sql` si tu base es anterior.
- Variable de entorno **`CAI_LOG_DIR`**: carpeta de los registros de Logback (por defecto `~/camino_iluminacion_logs`; un archivo por día, 30 días).
- Solo **PDF, JPG o PNG de hasta 5 MB**. El tipo se decide por el **contenido** del archivo (no por la extensión ni por lo que declare el navegador). Cada archivo se guarda con un nombre aleatorio; el nombre original solo se muestra.
- Todo documento pertenece a una **solicitud de reserva** del propio feligrés (así se sabe a qué parroquia corresponde). Máximo 50 documentos por usuario.
- Solo abre un archivo su **dueño** o el **administrador de la parroquia** de esa solicitud (`/api/documentos/archivo?id=N`; cualquier otro recibe 404). Se sirve con `nosniff`, sin caché y con `Content-Security-Policy` restrictiva.
- El administrador valida o rechaza **solo documentos pendientes** de su parroquia; rechazar exige un motivo que el feligrés ve.
- No hay antivirus: los archivos nunca se ejecutan ni se interpretan en el servidor, pero conviene sumar un análisis si se despliega para uso real.

## Acceso y roles
- Se inicia sesión con **DNI (8 dígitos) y contraseña**. El correo es solo dato de contacto.
- Contraseñas con PBKDF2 + sal. Tras 5 intentos fallidos desde una IP para un mismo DNI hay un bloqueo de 5 minutos.
- El registro web solo crea **feligreses**. El personal (administrador) se crea por script y queda asignado a **una parroquia**; solo ve y gestiona las reservas de esa parroquia.
- El panel (`/paginas/admin/*` y `/api/admin/*`) lo protege el servidor (`FiltroAdmin` + cada servlet); el navegador solo consulta `/api/sesion`.
- Usuarios de demostración (contraseña **Demo1234**; cámbiala antes de cualquier uso real):

| Rol | DNI |
|---|---|
| Administrador (parroquia San José) | 70000000 |
| Feligreses | 70000001, 70000002, 70000003, 70000004 |

## Compilar, probar y correr
```bash
mvn clean test             # pruebas (no necesitan base de datos)
mvn clean package          # pruebas + target/ROOT.war
```
En IntelliJ: panel *Maven* → `clean` y luego `test`. Para usar la aplicación, despliega el WAR (siguiente sección)
o ejecútala con SmartTomcat, con las variables de entorno de «Configurar».

`PruebaConexion.java` es una demostración antigua de la capa DAO: **escribe datos de prueba** (una reserva y un
documento) en la base configurada, así que úsala solo contra una base de desarrollo (clic derecho → Run).

## Desplegar como WAR (Tomcat 10)
```bash
mvn clean package          # corre las pruebas y genera target/ROOT.war
```
`ROOT.war` trae el backend y el frontend (`index.html`, `paginas/`, `css/`, `js/`, `vendor/`). Cópialo a
`webapps/` de Tomcat 10 con ese nombre para que la app quede en la raíz (`http://servidor:8080/`), que es donde
el frontend llama a `/api/...`. No necesita `web.xml`: servlets y filtros usan anotaciones. Define las variables de
entorno de la sección «Configurar» en el servicio de Tomcat. En IntelliJ, SmartTomcat se sigue usando igual.

## Consulta de DNI en el registro (Factiliza)
`GET /api/consulta-dni` dice si está disponible (`{"disponible":true|false}`); `POST /api/consulta-dni` con `dni`
devuelve nombres y apellidos. La consulta la hace el servidor (`ConsultaDniService`, cliente HTTP inyectable) con
5 s de tiempo de espera y un límite de 10 consultas por IP cada 10 minutos. Si no hay token, Factiliza falla o
se supera el límite, responde un error con `"manual":true` y el feligrés escribe sus datos; el registro y la
casilla obligatoria de la política de privacidad no cambian. El DNI no se escribe en el registro de eventos.

## Estructura
```
src/main/java/pe/caminoiluminacion/
├── conexion/     Conexion.java (punto único de conexión JDBC, lee las variables DB_*)
├── controlador/  Servlets (/api/...): leen la petición, llaman al servicio y responden JSON
├── servicio/     Reglas de negocio; Servicios.java ensambla todo
├── dao/          Interfaces (contrato de cada DAO)
├── dao/impl/     Implementaciones con JDBC puro
├── modelo/       Entidades (Usuario, Parroquia, Reserva, Documento, Pago, ...)
├── filtro/       CSRF por origen, cabeceras de seguridad, acceso al panel
├── seguridad/    Cifrado del DNI (AES-GCM) y hash de contraseñas (PBKDF2)
├── util/         Excel (POI), PDF, límite de intentos, registro de eventos, archivos
└── PruebaConexion.java   Demostración antigua de la capa DAO (escribe datos de prueba)
```

## Intenciones de misa y recibos (resumen técnico)
- `IntencionService`: el feligrés elige una misa del horario, una fecha (desde mañana, hasta 90 días, en el día de la semana de esa misa), escribe la intención y paga la ofrenda por Yape/Plin. Máximo de 8 intenciones vigentes por misa y fecha; un pago rechazado libera el cupo. El cupo se controla en `IntencionDAOImpl` con una transacción que bloquea la fila del horario (`SELECT ... FOR UPDATE`), así que dos envíos simultáneos no pueden pasarse del límite.
- Los comprobantes de las intenciones pasan por el mismo `ComprobanteService` que los demás pagos (imagen real por su contenido, tope de 5 MB, operación y comprobante no reutilizables).
- `ReciboPdf`: genera el recibo (PDF 1.4, una página A4, fuente Helvetica estándar) sin librerías externas. Todo texto del usuario se sanea y se escapa antes de escribirse. Solo existe para pagos APROBADOS y lo descarga su dueño o el personal de la parroquia del pago (`GET /api/pagos/recibo?id=N`).
- Endpoints: `GET /api/intenciones/info`, `GET|POST /api/intenciones`, `GET /api/admin/intenciones`, `GET /api/pagos/recibo` y, en la configuración de pagos del panel, `accion=ofrenda`.
