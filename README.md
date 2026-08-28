# Camino a la Iluminación

Software para parroquias católicas — Curso Integrador I.

## Integrantes
- Leo
- Edgardo
- Fernando

## Estado actual
Prototipo estático (HTML/CSS/JS) correspondiente al punto 8.5 (Diseño UX/UI y prototipo)
del avance. No tiene backend ni base de datos: los formularios y la subida de
documentos son simulaciones para la demo.

## Estructura del proyecto
```
camino-a-la-iluminacion/
├── index.html            → Inicio
├── css/estilos.css       → estilos compartidos
├── js/main.js            → interacciones simuladas
└── paginas/
    ├── horarios.html
    ├── notificaciones.html
    ├── mapa.html
    ├── iglesia.html
    ├── reservas.html
    └── documentos.html
```

## Cómo ver el prototipo
No necesita servidor: clona el repositorio y abre `index.html` en el navegador.

## Cómo contribuir
1. Trae los últimos cambios: `git pull`
2. Crea una rama para tu tarea: `git checkout -b nombre-de-tu-cambio`
3. Haz tus cambios y confírmalos: `git add . && git commit -m "Descripción breve"`
4. Sube tu rama: `git push origin nombre-de-tu-cambio`
5. Abre un Pull Request en GitHub para que el equipo revise antes de fusionar a `main`.
