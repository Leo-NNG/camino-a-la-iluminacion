-- ============================================================================
-- Migración 08 — Intenciones de misa con ofrenda
-- Para una base que ya tiene la migración 07. (Si creas la base desde cero, camino_iluminacion.sql ya lo incluye.)
--
-- Qué hace:
--   * `cuenta_pago.ofrenda_intencion`: monto de la ofrenda por una intención de misa en cada parroquia
--     (NULL = no se piden en línea). Los datos de EJEMPLO quedan en S/ 10: cámbialo desde el panel.
--   * `pago.concepto` admite el valor 'intencion'.
--   * `intencion_misa`: la intención (por quién se ofrece la misa), la misa elegida y su pago.
--
-- ANTES DE EJECUTAR haz una copia:
--     mysqldump -u root -p --result-file=respaldo_antes_de_08.sql camino_iluminacion
-- Si falla a la mitad NO la repitas: restaura la copia y avísame del error exacto.
--
-- Uso:  mysql -u root -p --default-character-set=utf8mb4 -e "source database/migracion_08_intenciones_misa.sql"
-- ============================================================================
USE camino_iluminacion;

ALTER TABLE cuenta_pago
  ADD COLUMN ofrenda_intencion DECIMAL(8,2) NULL
  COMMENT 'Ofrenda de una intención de misa en esta parroquia; NULL = no se piden en línea'
  AFTER plin_titular;

ALTER TABLE pago
  MODIFY COLUMN concepto ENUM('arancel','donacion','diezmo','intencion') NOT NULL DEFAULT 'arancel';

-- INTENCION_MISA: el feligrés pide que una misa se ofrezca por alguien o algo y deja una ofrenda (se paga como cualquier
-- pago Yape/Plin). El personal la ve en su agenda solo cuando el pago está APROBADO.
CREATE TABLE intencion_misa (
  id_intencion INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  id_usuario   INT UNSIGNED NOT NULL,
  id_parroquia INT UNSIGNED NOT NULL,
  id_horario   INT UNSIGNED NULL COMMENT 'Misa del horario; queda NULL si el horario se elimina (se conserva hora_misa)',
  hora_misa    VARCHAR(120) NOT NULL COMMENT 'Copia del texto de la hora, para que la intención no dependa del horario',
  fecha_misa   DATE NOT NULL,
  tipo         ENUM('difunto','accion_gracias','salud','otra') NOT NULL,
  texto        VARCHAR(200) NOT NULL,
  id_pago      INT UNSIGNED NOT NULL,
  creada_en    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_intencion_pago UNIQUE (id_pago),
  INDEX ix_intencion_agenda (id_parroquia, fecha_misa),
  INDEX ix_intencion_usuario (id_usuario),
  CONSTRAINT fk_intencion_usuario FOREIGN KEY (id_usuario)
    REFERENCES usuario (id_usuario)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_intencion_parroquia FOREIGN KEY (id_parroquia)
    REFERENCES parroquia (id_parroquia)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_intencion_horario FOREIGN KEY (id_horario)
    REFERENCES horario (id_horario)
    ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_intencion_pago FOREIGN KEY (id_pago)
    REFERENCES pago (id_pago)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

UPDATE cuenta_pago SET ofrenda_intencion = 10.00 WHERE ofrenda_intencion IS NULL;
