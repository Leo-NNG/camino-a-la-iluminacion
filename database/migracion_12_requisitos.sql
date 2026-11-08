-- ============================================================================
-- Migración 12 — Requisitos por sacramento y checklist de documentos
-- Para una base que ya tiene la migración 11. (Si creas la base desde cero, camino_iluminacion.sql ya lo incluye.)
--
-- Qué hace:
--   * `requisito_sacramento`: los documentos que cada parroquia pide para cada sacramento (nombre, descripción,
--     obligatorio o recomendado, orden). Se siembran requisitos base para los sacramentos que ya ofrece cada parroquia;
--     el personal los edita en el panel "Requisitos".
--   * `documento.id_requisito` (opcional): qué requisito cumple cada documento. Los documentos que ya existen quedan
--     sin requisito (NULL) y siguen funcionando igual.
--   No modifica ni borra ningún dato existente.
--
-- ANTES DE EJECUTAR haz una copia (fuera del repositorio):
--     mysqldump -u root -p --result-file=respaldo_antes_de_12.sql camino_iluminacion
-- Si falla a la mitad NO la repitas: restaura la copia y avísame del error exacto.
--
-- Uso:  mysql -u root -p --default-character-set=utf8mb4 -e "source database/migracion_12_requisitos.sql"
-- ============================================================================
USE camino_iluminacion;

CREATE TABLE requisito_sacramento (
  id_requisito  INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  id_parroquia  INT UNSIGNED NOT NULL,
  id_sacramento TINYINT UNSIGNED NOT NULL,
  nombre        VARCHAR(80)  NOT NULL,
  descripcion   VARCHAR(255) NULL,
  obligatorio   TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1 = hace falta para completar el trámite; 0 = recomendado',
  orden         TINYINT UNSIGNED NOT NULL DEFAULT 1,
  creado_en     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT uq_requisito UNIQUE (id_parroquia, id_sacramento, nombre),
  INDEX ix_requisito_busqueda (id_parroquia, id_sacramento, orden),
  CONSTRAINT fk_requisito_parroquia FOREIGN KEY (id_parroquia)
    REFERENCES parroquia (id_parroquia)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_requisito_sacramento FOREIGN KEY (id_sacramento)
    REFERENCES sacramento (id_sacramento)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

ALTER TABLE documento
  ADD COLUMN id_requisito INT UNSIGNED NULL COMMENT 'Requisito de la parroquia que este documento cumple (opcional)',
  ADD CONSTRAINT fk_documento_requisito FOREIGN KEY (id_requisito)
    REFERENCES requisito_sacramento (id_requisito)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- Requisitos base (texto orientativo): cada parroquia los ajusta en el panel "Requisitos" según sus propias normas.
INSERT INTO requisito_sacramento (id_parroquia, id_sacramento, nombre, descripcion, obligatorio, orden)
SELECT ps.id_parroquia, s.id_sacramento, d.nombre, d.descripcion, d.obligatorio, d.orden
FROM parroquia_sacramento ps
JOIN sacramento s ON s.id_sacramento = ps.id_sacramento
JOIN (
  SELECT 'bautizo' AS codigo, 'Partida de nacimiento del niño' AS nombre, 'Copia de la partida o acta de nacimiento.' AS descripcion, 1 AS obligatorio, 1 AS orden
  UNION ALL SELECT 'bautizo', 'DNI de los padres', 'Copia por ambos lados.', 1, 2
  UNION ALL SELECT 'bautizo', 'Constancia de la charla prebautismal', 'De los padres y de los padrinos.', 1, 3
  UNION ALL SELECT 'bautizo', 'Constancia de confirmación del padrino', 'Si el padrino o la madrina ya recibió la confirmación.', 0, 4
  UNION ALL SELECT 'matrimonio', 'Partida de bautismo de ambos novios', 'Copia actualizada (emitida en los últimos 6 meses).', 1, 1
  UNION ALL SELECT 'matrimonio', 'DNI de ambos novios', 'Copia por ambos lados.', 1, 2
  UNION ALL SELECT 'matrimonio', 'Constancia de la charla prematrimonial', 'De ambos novios.', 1, 3
  UNION ALL SELECT 'matrimonio', 'Constancia de confirmación de ambos novios', 'Si ya recibieron la confirmación.', 0, 4
  UNION ALL SELECT 'primera_comunion', 'Partida de bautismo', 'Copia actualizada.', 1, 1
  UNION ALL SELECT 'primera_comunion', 'DNI o partida de nacimiento del niño', 'Copia legible.', 1, 2
  UNION ALL SELECT 'primera_comunion', 'Constancia de catequesis', 'Del año de preparación para la Primera Comunión.', 1, 3
  UNION ALL SELECT 'confirmacion', 'Partida de bautismo', 'Copia actualizada.', 1, 1
  UNION ALL SELECT 'confirmacion', 'Constancia de Primera Comunión', 'Copia de la constancia o del registro.', 1, 2
  UNION ALL SELECT 'confirmacion', 'Constancia de catequesis de confirmación', 'Del año de preparación para la confirmación.', 1, 3
  UNION ALL SELECT 'confirmacion', 'DNI del padrino o de la madrina', 'Copia por ambos lados.', 0, 4
) d ON d.codigo = s.codigo;
