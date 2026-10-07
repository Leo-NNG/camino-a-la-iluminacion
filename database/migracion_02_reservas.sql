-- ============================================================================
-- Migración 02 — Reservas: motivo de rechazo
-- Para una base ya creada. (Si creas la base desde cero, camino_iluminacion.sql ya lo incluye.)
-- Requiere haber aplicado antes la migración 01.
--
-- Uso:  mysql -u root -p --default-character-set=utf8mb4 < migracion_02_reservas.sql
-- No borra ni modifica datos existentes.
-- ============================================================================
USE camino_iluminacion;

ALTER TABLE reserva
  ADD COLUMN motivo_rechazo VARCHAR(255) NULL
    COMMENT 'Lo escribe el personal al rechazar; el feligrés lo ve en Mis solicitudes'
    AFTER estado;
