# Backend — Camino a la Iluminación

Backend en Java (JDBC + patrón DAO) para el proyecto. Arquitectura en capas
según el Capítulo 8.1 del informe: Presentación → Lógica de negocio → DAO → BD.
Esta etapa cubre Modelo + DAO; la capa de Servicios (lógica de negocio) y los
controladores web se agregan en una etapa posterior del curso.

## Requisitos
- JDK 17 o superior
- Maven
- Base de datos ya creada: correr primero `../database/camino_iluminacion.sql`

## Configurar
Abre `src/main/java/pe/caminoiluminacion/conexion/Conexion.java` y cambia:
```java
private static final String CONTRASENA = "CAMBIAR_POR_TU_CONTRASENA";
```
por la contraseña real de tu MySQL/MariaDB.

## Compilar y correr
```bash
mvn compile
mvn exec:java
```
o desde tu IDE: clic derecho sobre `PruebaConexion.java` → Run.

`PruebaConexion.java` conecta a la base de datos, lista parroquias, horarios
y avisos, crea una reserva y un documento de prueba, y simula la aprobación
del documento por el panel de administración — para confirmar que las 6
tablas y sus relaciones funcionan de punta a punta.

## Estructura
```
src/main/java/pe/caminoiluminacion/
├── conexion/     Conexion.java (punto único de conexión JDBC)
├── modelo/       Usuario, Parroquia, Horario, Aviso, Reserva, Documento
├── dao/          Interfaces (contrato de cada DAO)
├── dao/impl/     Implementaciones con JDBC puro
└── PruebaConexion.java   Clase de demostración end-to-end
```
