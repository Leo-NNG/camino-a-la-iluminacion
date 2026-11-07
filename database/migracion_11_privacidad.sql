-- ============================================================================
-- Migración 11 — Privacidad (Ley n.° 29733): aceptación de la política y eliminación (anonimización) de la cuenta
-- Para una base que ya tiene la migración 10. (Si creas la base desde cero, camino_iluminacion.sql ya lo incluye.)
--
-- Qué hace (solo agrega columnas a `usuario`; no modifica ni borra ningún dato):
--   * politica_version / politica_aceptada_en: qué versión de la política aceptó la persona y cuándo. Las cuentas
--     existentes quedan en NULL: la aceptan desde "Mi cuenta".
--   * eliminado_en: se llena cuando la persona elimina su cuenta. La fila NO se borra (las reservas, los pagos y la
--     bitácora conservan su referencia), pero sus datos personales se vacían desde la aplicación.
--
-- ANTES DE EJECUTAR haz una copia (fuera del repositorio):
--     mysqldump -u root -p --result-file=respaldo_antes_de_11.sql camino_iluminacion
-- Si falla a la mitad NO la repitas: restaura la copia y avísame del error exacto.
--
-- Uso:  mysql -u root -p --default-character-set=utf8mb4 -e "source database/migracion_11_privacidad.sql"
-- ============================================================================
USE camino_iluminacion;

ALTER TABLE usuario
  ADD COLUMN politica_version      VARCHAR(20) NULL COMMENT 'Versión de la política de privacidad que aceptó (NULL = aún no la acepta)',
  ADD COLUMN politica_aceptada_en  TIMESTAMP   NULL COMMENT 'Cuándo aceptó la política',
  ADD COLUMN eliminado_en          TIMESTAMP   NULL COMMENT 'Cuándo eliminó su cuenta: la fila se conserva anonimizada (sin datos personales)';
