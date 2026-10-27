-- ============================================================================
-- Migración 05 — Normalización de sacramentos, bitácora y trazabilidad
-- Para una base ya creada. (Si creas la base desde cero, camino_iluminacion.sql ya lo incluye.)
-- Requiere haber aplicado antes las migraciones 01 a 04.
--
-- Qué hace:
--   * Crea el catálogo `sacramento` y la tabla N:M `parroquia_sacramento` (1FN: adiós a la lista
--     separada por comas) y los llena con lo que ya tenías en `parroquia.sacramentos_disponibles`.
--   * `reserva.sacramento` (ENUM) pasa a `reserva.id_sacramento` con clave foránea. Se conservan
--     todas las reservas existentes.
--   * Agrega `documento.id_validador`, `aviso.id_autor` y la tabla `bitacora`.
--
-- ANTES DE EJECUTAR haz una copia (los cambios de estructura no se deshacen solos):
--     mysqldump -u root -p camino_iluminacion > respaldo_antes_de_05.sql
-- Si falla a la mitad NO la repitas: restaura la copia y avísame del error exacto.
--
-- Uso:  mysql -u root -p --default-character-set=utf8mb4 < migracion_05_normalizacion_bitacora.sql
-- ============================================================================
USE camino_iluminacion;

-- 1) Catálogo de sacramentos
CREATE TABLE sacramento (
  id_sacramento TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  codigo        VARCHAR(30) NOT NULL COMMENT 'Identificador estable, ej: primera_comunion',
  nombre        VARCHAR(60) NOT NULL COMMENT 'Nombre que ve el usuario',
  CONSTRAINT uq_sacramento_codigo UNIQUE (codigo),
  CONSTRAINT uq_sacramento_nombre UNIQUE (nombre)
) ENGINE=InnoDB;

INSERT INTO sacramento (codigo, nombre) VALUES
  ('bautizo', 'Bautizo'),
  ('matrimonio', 'Matrimonio'),
  ('primera_comunion', 'Primera Comunión'),
  ('confirmacion', 'Confirmación');

-- 2) Qué sacramentos ofrece cada parroquia (N:M), a partir de la lista de texto actual
CREATE TABLE parroquia_sacramento (
  id_parroquia  INT UNSIGNED NOT NULL,
  id_sacramento TINYINT UNSIGNED NOT NULL,
  PRIMARY KEY (id_parroquia, id_sacramento),
  CONSTRAINT fk_ps_parroquia FOREIGN KEY (id_parroquia)
    REFERENCES parroquia (id_parroquia) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_ps_sacramento FOREIGN KEY (id_sacramento)
    REFERENCES sacramento (id_sacramento) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

INSERT INTO parroquia_sacramento (id_parroquia, id_sacramento)
SELECT p.id_parroquia, s.id_sacramento
FROM parroquia p
JOIN sacramento s ON FIND_IN_SET(s.nombre, p.sacramentos_disponibles) > 0;

-- 3) reserva: del ENUM a la clave foránea
ALTER TABLE reserva ADD COLUMN id_sacramento TINYINT UNSIGNED NULL AFTER id_parroquia;

UPDATE reserva r JOIN sacramento s ON s.codigo = r.sacramento SET r.id_sacramento = s.id_sacramento;

ALTER TABLE reserva
  MODIFY COLUMN id_sacramento TINYINT UNSIGNED NOT NULL,
  ADD CONSTRAINT fk_reserva_sacramento FOREIGN KEY (id_sacramento)
    REFERENCES sacramento (id_sacramento) ON DELETE RESTRICT ON UPDATE CASCADE,
  DROP COLUMN sacramento;

-- 4) parroquia: ya no necesita la lista de texto
ALTER TABLE parroquia DROP COLUMN sacramentos_disponibles;

-- 5) Trazabilidad: quién validó un documento y quién publicó un aviso
ALTER TABLE documento
  ADD COLUMN id_validador INT UNSIGNED NULL
    COMMENT 'Quién lo validó o rechazó (queda NULL si esa cuenta se elimina)' AFTER fecha_validacion,
  ADD CONSTRAINT fk_documento_validador FOREIGN KEY (id_validador)
    REFERENCES usuario (id_usuario) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE aviso
  ADD COLUMN id_autor INT UNSIGNED NULL
    COMMENT 'Quién lo publicó (queda NULL si esa cuenta se elimina)' AFTER fecha_evento,
  ADD CONSTRAINT fk_aviso_autor FOREIGN KEY (id_autor)
    REFERENCES usuario (id_usuario) ON DELETE SET NULL ON UPDATE CASCADE;

-- 6) Bitácora de acciones críticas (RNF09). No guarda DNI, contraseñas ni contenido de documentos.
CREATE TABLE bitacora (
  id_bitacora INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  id_usuario  INT UNSIGNED NULL COMMENT 'NULL si no se pudo identificar o la cuenta se eliminó',
  accion      VARCHAR(40)  NOT NULL COMMENT 'Ej: LOGIN_OK, LOGIN_FALLIDO, RESERVA_CREADA, DOCUMENTO_VALIDADO',
  detalle     VARCHAR(255) NULL,
  ip          VARCHAR(45)  NULL,
  creado_en   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX ix_bitacora_fecha (creado_en),
  INDEX ix_bitacora_usuario (id_usuario),
  CONSTRAINT fk_bitacora_usuario FOREIGN KEY (id_usuario)
    REFERENCES usuario (id_usuario) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;
