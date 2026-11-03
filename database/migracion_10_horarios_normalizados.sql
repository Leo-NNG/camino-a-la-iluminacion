-- ============================================================================
-- Migración 10 — Horarios normalizados: una fila por celebración, con hora real (TIME)
-- Para una base que ya tiene la migración 09. (Si creas la base desde cero, camino_iluminacion.sql ya lo incluye.)
--
-- Antes, `horario.hora` era texto libre ("7, 9, 11 a.m. y 7 p.m."): no se podía ordenar, validar ni calcular la próxima
-- misa. Ahora cada celebración es una fila con `hora_inicio` (TIME) y un `detalle` opcional.
--
-- Qué hace:
--   1. Agrega `hora_inicio` y `detalle`.
--   2. Convierte cada fila en una fila por hora usando una tabla de equivalencias con los textos conocidos
--      (los horarios de ejemplo del proyecto).
--   3. Las filas con un texto que NO está en la tabla (por ejemplo, horarios que escribiste a mano) NO se pierden:
--      se copian a `horario_sin_migrar` para que las vuelvas a crear desde el panel (Gestionar horarios).
--   4. Elimina la columna `hora` y deja `hora_inicio` como obligatoria.
--   Las intenciones de misa ya registradas conservan su fecha y la hora que se les copió, pero dejan de estar
--   enlazadas a una celebración concreta (id_horario queda NULL).
--
-- ANTES DE EJECUTAR haz una copia:
--     mysqldump -u root -p --result-file=respaldo_antes_de_10.sql camino_iluminacion
-- Si falla a la mitad NO la repitas: restaura la copia y avísame del error exacto.
--
-- Uso:  mysql -u root -p --default-character-set=utf8mb4 -e "source database/migracion_10_horarios_normalizados.sql"
-- ============================================================================
USE camino_iluminacion;

ALTER TABLE horario
  ADD COLUMN hora_inicio TIME NULL AFTER dia_semana,
  ADD COLUMN detalle VARCHAR(120) NULL AFTER hora_inicio;

CREATE TEMPORARY TABLE equivalencia (
  texto       VARCHAR(120) NOT NULL,
  hora_inicio TIME NOT NULL,
  detalle     VARCHAR(120) NULL
);
INSERT INTO equivalencia (texto, hora_inicio, detalle) VALUES
  ('7:00 a.m. y 7:00 p.m.', '07:00:00', NULL),
  ('7:00 a.m. y 7:00 p.m.', '19:00:00', NULL),
  ('8:00 a.m. y 6:00 p.m.', '08:00:00', NULL),
  ('8:00 a.m. y 6:00 p.m.', '18:00:00', NULL),
  ('7, 9, 11 a.m. y 7 p.m.', '07:00:00', NULL),
  ('7, 9, 11 a.m. y 7 p.m.', '09:00:00', NULL),
  ('7, 9, 11 a.m. y 7 p.m.', '11:00:00', NULL),
  ('7, 9, 11 a.m. y 7 p.m.', '19:00:00', NULL),
  ('Catequesis de Primera Comunión — 5:00 p.m.', '17:00:00', 'Catequesis de Primera Comunión'),
  ('Grupo de oración — 7:30 p.m.', '19:30:00', 'Grupo de oración'),
  ('6:30 a.m. y 6:30 p.m.', '06:30:00', NULL),
  ('6:30 a.m. y 6:30 p.m.', '18:30:00', NULL),
  ('5:00 p.m. (vigilia)', '17:00:00', 'vigilia'),
  ('8, 10 a.m. y 6 p.m.', '08:00:00', NULL),
  ('8, 10 a.m. y 6 p.m.', '10:00:00', NULL),
  ('8, 10 a.m. y 6 p.m.', '18:00:00', NULL),
  ('Catequesis de Confirmación — 6:00 p.m.', '18:00:00', 'Catequesis de Confirmación'),
  ('7:30 a.m.', '07:30:00', NULL),
  ('6:00 p.m.', '18:00:00', NULL),
  ('9 a.m., 12 p.m. y 7 p.m.', '09:00:00', NULL),
  ('9 a.m., 12 p.m. y 7 p.m.', '12:00:00', NULL),
  ('9 a.m., 12 p.m. y 7 p.m.', '19:00:00', NULL),
  ('Rosario y exposición del Santísimo — 5:30 p.m.', '17:30:00', 'Rosario y exposición del Santísimo'),
  ('4:00 p.m.', '16:00:00', NULL),
  ('10:00 a.m.', '10:00:00', NULL),
  ('Charla de preparación para bautizos comunitarios — 3:00 p.m.', '15:00:00', 'Charla de preparación para bautizos comunitarios');

-- Filas con un texto desconocido: se guardan aparte, sin perderlas.
CREATE TABLE IF NOT EXISTS horario_sin_migrar (
  id_horario   INT UNSIGNED NOT NULL,
  id_parroquia INT UNSIGNED NOT NULL,
  dia_semana   VARCHAR(12)  NOT NULL,
  tipo         VARCHAR(12)  NOT NULL,
  hora         VARCHAR(120) NOT NULL
) ENGINE=InnoDB;
INSERT INTO horario_sin_migrar (id_horario, id_parroquia, dia_semana, tipo, hora)
SELECT h.id_horario, h.id_parroquia, h.dia_semana, h.tipo, h.hora
FROM horario h
WHERE h.hora_inicio IS NULL
  AND NOT EXISTS (SELECT 1 FROM equivalencia e WHERE e.texto = h.hora);

-- Una fila nueva por cada hora de cada fila conocida.
INSERT INTO horario (id_parroquia, dia_semana, hora, hora_inicio, detalle, tipo)
SELECT h.id_parroquia, h.dia_semana, h.hora, e.hora_inicio, e.detalle, h.tipo
FROM horario h
JOIN equivalencia e ON e.texto = h.hora
WHERE h.hora_inicio IS NULL;

-- Se eliminan las filas antiguas (ya convertidas o guardadas en horario_sin_migrar).
DELETE FROM horario WHERE hora_inicio IS NULL;

ALTER TABLE horario
  DROP COLUMN hora,
  MODIFY COLUMN hora_inicio TIME NOT NULL;

DROP TEMPORARY TABLE equivalencia;

-- Resultado: cuántas filas quedaron y cuántas hay que rehacer a mano (idealmente 0).
SELECT COUNT(*) AS horarios_migrados FROM horario;
SELECT COUNT(*) AS filas_para_rehacer_a_mano FROM horario_sin_migrar;

-- Si no quedó nada por rehacer, se elimina la tabla auxiliar; si quedó algo, se conserva para que lo revises.
SET @limpiar = IF((SELECT COUNT(*) FROM horario_sin_migrar) = 0,
                  'DROP TABLE horario_sin_migrar',
                  'SELECT ''Hay horarios que rehacer: revisa la tabla horario_sin_migrar y créalos de nuevo en Gestionar horarios'' AS aviso');
PREPARE sentencia FROM @limpiar;
EXECUTE sentencia;
DEALLOCATE PREPARE sentencia;
