-- ============================================================================
-- Migración 09 — Fechas disponibles para reservar sacramentos
-- Para una base que ya tiene la migración 08. (Si creas la base desde cero, camino_iluminacion.sql ya lo incluye.)
--
-- Qué hace:
--   * `fecha_disponible`: fechas que cada parroquia publica para un sacramento, con su cupo y una hora informativa.
--   * Regla (opción B): si la parroquia publicó al menos una fecha futura para ese sacramento, el feligrés solo puede
--     reservar entre esas fechas y hasta agotar el cupo. Si no publicó ninguna, se mantiene la fecha libre que valida
--     el personal. No modifica ni borra ningún dato existente.
--
-- ANTES DE EJECUTAR haz una copia:
--     mysqldump -u root -p --result-file=respaldo_antes_de_09.sql camino_iluminacion
-- Si falla a la mitad NO la repitas: restaura la copia y avísame del error exacto.
--
-- Uso:  mysql -u root -p --default-character-set=utf8mb4 -e "source database/migracion_09_fechas_disponibles.sql"
-- ============================================================================
USE camino_iluminacion;

CREATE TABLE fecha_disponible (
  id_fecha      INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  id_parroquia  INT UNSIGNED NOT NULL,
  id_sacramento TINYINT UNSIGNED NOT NULL,
  fecha         DATE NOT NULL,
  hora_inicio   TIME NULL COMMENT 'Hora de la celebración; solo informativa para el feligrés',
  cupo          SMALLINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'Solicitudes activas (pendientes o confirmadas) que admite esa fecha',
  creada_en     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT uq_fecha_disponible UNIQUE (id_parroquia, id_sacramento, fecha),
  INDEX ix_fecha_disponible_busqueda (id_parroquia, id_sacramento, fecha),
  CONSTRAINT fk_fecha_parroquia FOREIGN KEY (id_parroquia)
    REFERENCES parroquia (id_parroquia)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_fecha_sacramento FOREIGN KEY (id_sacramento)
    REFERENCES sacramento (id_sacramento)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;
