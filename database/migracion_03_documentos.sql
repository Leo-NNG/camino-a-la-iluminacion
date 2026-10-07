-- ============================================================================
-- Migración 03 — Documentos reales (subida de archivos)
-- Para una base ya creada. (Si creas la base desde cero, camino_iluminacion.sql ya lo incluye.)
-- Requiere haber aplicado antes las migraciones 01 y 02.
--
-- Uso:  mysql -u root -p --default-character-set=utf8mb4 < migracion_03_documentos.sql
--
-- ATENCIÓN: borra los 4 documentos de ejemplo del script original: apuntaban a archivos
-- inexistentes (/uploads/docN.pdf) y aparecerían en el panel sin poder abrirse.
-- ============================================================================
USE camino_iluminacion;

ALTER TABLE documento
  ADD COLUMN tipo_mime    VARCHAR(50) NOT NULL DEFAULT 'application/pdf'
    COMMENT 'application/pdf, image/jpeg o image/png (detectado por contenido)' AFTER motivo_rechazo,
  ADD COLUMN tamano_bytes INT UNSIGNED NULL AFTER tipo_mime;

DELETE FROM documento WHERE ruta_archivo LIKE '/uploads/doc%';
