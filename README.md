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
Pendiente: reportes reales, capa de servicios, bitácora y pruebas con JUnit.
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
