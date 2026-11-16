-- ============================================================================
-- Migración 13 — Cancelar y reprogramar solicitudes de sacramento
-- Para una base que ya tiene la migración 12. (Si creas la base desde cero, camino_iluminacion.sql ya lo incluye.)
--
-- Qué hace (solo agrega; no modifica ni borra ningún dato existente):
--   * `reserva.estado` admite un valor nuevo: 'cancelada' (la cancela el propio feligrés).
--   * `reserva.fecha_original`: la primera fecha que se pidió, se llena la primera vez que se reprograma.
--   * `reserva.reprogramaciones`: cuántas veces se reprogramó (las solicitudes existentes quedan en 0).
--   * `reserva.cancelada_en` y `reserva.motivo_cancelacion` (opcional, lo escribe el feligrés).
--
-- ANTES DE EJECUTAR haz una copia (fuera del repositorio):
--     mysqldump -u root -p --result-file=respaldo_antes_de_13.sql camino_iluminacion
-- Si falla a la mitad NO la repitas: restaura la copia y avísame del error exacto.
--
-- Uso:  mysql -u root -p --default-character-set=utf8mb4 -e "source database/migracion_13_cancelar_reprogramar.sql"
-- ============================================================================
USE camino_iluminacion;

ALTER TABLE reserva
  MODIFY COLUMN estado ENUM('pendiente','confirmada','rechazada','cancelada') NOT NULL DEFAULT 'pendiente',
  ADD COLUMN fecha_original     DATE NULL COMMENT 'Primera fecha pedida; se llena al reprogramar por primera vez',
  ADD COLUMN reprogramaciones   TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Cuántas veces el feligrés reprogramó la solicitud',
  ADD COLUMN cancelada_en       TIMESTAMP NULL COMMENT 'Cuándo la canceló el feligrés',
  ADD COLUMN motivo_cancelacion VARCHAR(255) NULL COMMENT 'Motivo opcional que escribe el feligrés al cancelar';
