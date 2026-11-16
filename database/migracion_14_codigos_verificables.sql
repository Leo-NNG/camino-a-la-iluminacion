-- ============================================================================
-- Migración 14 — Códigos de verificación para constancias de reserva y recibos de pago
-- Para una base que ya tiene la migración 13. (Si creas la base desde cero, camino_iluminacion.sql ya lo incluye.)
--
-- Qué hace (solo agrega; no modifica ni borra ningún dato existente):
--   * `reserva.codigo_verificacion` (CON-XXXXX-XXXXX) y `pago.codigo_verificacion` (REC-XXXXX-XXXXX): código único
--     que se imprime en la constancia o el recibo en PDF para que cualquiera pueda comprobar en la página pública
--     "Verificar documento" que es auténtico y sigue vigente. Se asigna la primera vez que alguien descarga el
--     documento; las reservas y pagos que ya existen quedan en NULL hasta entonces.
--
-- ANTES DE EJECUTAR haz una copia (fuera del repositorio):
--     mysqldump -u root -p --result-file=respaldo_antes_de_14.sql camino_iluminacion
-- Si falla a la mitad NO la repitas: restaura la copia y avísame del error exacto.
--
-- Uso:  mysql -u root -p --default-character-set=utf8mb4 -e "source database/migracion_14_codigos_verificables.sql"
-- ============================================================================
USE camino_iluminacion;

ALTER TABLE reserva
  ADD COLUMN codigo_verificacion VARCHAR(20) NULL COMMENT 'Código impreso en la constancia en PDF (CON-XXXXX-XXXXX); se asigna al descargarla',
  ADD CONSTRAINT uq_reserva_codigo UNIQUE (codigo_verificacion);

ALTER TABLE pago
  ADD COLUMN codigo_verificacion VARCHAR(20) NULL COMMENT 'Código impreso en el recibo en PDF (REC-XXXXX-XXXXX); se asigna al descargarlo',
  ADD CONSTRAINT uq_pago_codigo UNIQUE (codigo_verificacion);
