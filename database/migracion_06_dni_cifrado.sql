-- ============================================================================
-- Migración 06 — DNI cifrado en la base de datos
-- Para una base ya creada. (Si creas la base desde cero, camino_iluminacion.sql ya lo incluye.)
-- Requiere haber aplicado antes las migraciones 01 a 05.
--
-- Qué hace:
--   * `usuario.dni` pasa de CHAR(8) a VARCHAR(120): ahora guarda el DNI CIFRADO (AES-256-GCM, lo hace la
--     aplicación con la clase Cripto). Un volcado de la base ya no revela los DNI.
--   * Agrega `usuario.dni_indice` (CHAR(64)): huella HMAC-SHA256 del DNI. Sirve para buscar por DNI (login)
--     y para garantizar que no se repita (restricción uq_usuario_dni), sin guardar el DNI en claro.
--   * Quita el CHECK de 8 dígitos sobre `dni` (ya no aplica al texto cifrado; la aplicación valida el formato).
--
-- Los DNI existentes NO se cifran aquí: lo hace la aplicación sola la primera vez que arranca
-- (clase MigracionDni), porque la clave de cifrado vive fuera de la base de datos.
-- Antes de arrancar, define la clave (ver README): variable de entorno CAI_DATA_KEY.
--
-- ANTES DE EJECUTAR haz una copia:
--     mysqldump -u root -p camino_iluminacion > respaldo_antes_de_06.sql
-- Si falla a la mitad NO la repitas: restaura la copia y avísame del error exacto.
--
-- Uso:  mysql -u root -p --default-character-set=utf8mb4 < migracion_06_dni_cifrado.sql
-- Nota: si tu servidor es MySQL 8.0.16 a 8.0.18, cambia `DROP CONSTRAINT ck_usuario_dni` por
--       `DROP CHECK ck_usuario_dni` (MariaDB y MySQL 8.0.19 o superior aceptan ambas formas con DROP CONSTRAINT).
-- ============================================================================
USE camino_iluminacion;

ALTER TABLE usuario DROP CONSTRAINT ck_usuario_dni;
ALTER TABLE usuario DROP INDEX uq_usuario_dni;

ALTER TABLE usuario
  MODIFY COLUMN dni VARCHAR(120) NOT NULL
    COMMENT 'DNI CIFRADO (AES-256-GCM, formato v1:...). El valor en claro solo existe en la aplicación',
  ADD COLUMN dni_indice CHAR(64) NULL
    COMMENT 'Huella HMAC-SHA256 del DNI: búsqueda e unicidad sin guardar el DNI en claro' AFTER dni,
  ADD CONSTRAINT uq_usuario_dni UNIQUE (dni_indice);
