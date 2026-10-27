-- ============================================================================
-- Migración 07 — Pagos Yape/Plin por parroquia, aranceles y notificaciones
-- Para una base ya creada. (Si creas la base desde cero, camino_iluminacion.sql ya lo incluye.)
-- Requiere haber aplicado antes las migraciones 01 a 06.
--
-- Qué hace:
--   * `cuenta_pago`: número Yape y Plin de CADA parroquia (con su titular).
--   * `arancel`: monto de cada sacramento en cada parroquia (el monto nunca viene del navegador).
--   * `pago`: arancel de reservas, donaciones y diezmos con comprobante; el personal los verifica.
--     La operación y el comprobante no pueden repetirse en pagos vigentes (anti-fraude).
--   * `notificacion`: avisos dentro de la plataforma al feligrés.
--   * Datos de EJEMPLO: números ficticios y aranceles de 50/60/70/250 soles. Cámbialos desde el panel.
--
-- ANTES DE EJECUTAR haz una copia:
--     mysqldump -u root -p camino_iluminacion > respaldo_antes_de_07.sql
-- Si falla a la mitad NO la repitas: restaura la copia y avísame del error exacto.
--
-- Uso:  mysql -u root -p --default-character-set=utf8mb4 < migracion_07_pagos.sql
-- ============================================================================
USE camino_iluminacion;

-- ----------------------------------------------------------------------------
-- PAGOS (Yape / Plin con verificación manual) — sin pasarela ni datos de tarjeta
-- ----------------------------------------------------------------------------
-- CUENTA_PAGO: a qué número Yape/Plin paga el feligrés, POR PARROQUIA (lo edita el personal desde el panel).
CREATE TABLE cuenta_pago (
  id_parroquia   INT UNSIGNED PRIMARY KEY,
  yape_numero    CHAR(9)      NULL,
  yape_titular   VARCHAR(100) NULL,
  plin_numero    CHAR(9)      NULL,
  plin_titular   VARCHAR(100) NULL,
  actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_cuenta_parroquia FOREIGN KEY (id_parroquia)
    REFERENCES parroquia (id_parroquia)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ARANCEL: monto oficial de cada sacramento en cada parroquia. El monto SIEMPRE sale de aquí, nunca del navegador.
CREATE TABLE arancel (
  id_parroquia  INT UNSIGNED     NOT NULL,
  id_sacramento TINYINT UNSIGNED NOT NULL,
  monto         DECIMAL(8,2)     NOT NULL,
  PRIMARY KEY (id_parroquia, id_sacramento),
  CONSTRAINT ck_arancel_monto CHECK (monto >= 0),
  CONSTRAINT fk_arancel_parroquia FOREIGN KEY (id_parroquia)
    REFERENCES parroquia (id_parroquia)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_arancel_sacramento FOREIGN KEY (id_sacramento)
    REFERENCES sacramento (id_sacramento)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

-- PAGO: arancel de una reserva, donación o diezmo. Se paga por Yape/Plin, se sube el comprobante y el personal lo verifica.
CREATE TABLE pago (
  id_pago          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  id_usuario       INT UNSIGNED NOT NULL,
  id_parroquia     INT UNSIGNED NOT NULL,
  id_reserva       INT UNSIGNED NULL COMMENT 'Solo para el concepto arancel',
  concepto         ENUM('arancel','donacion','diezmo') NOT NULL DEFAULT 'arancel',
  monto            DECIMAL(8,2) NOT NULL,
  metodo           ENUM('yape','plin') NOT NULL,
  celular_origen   VARCHAR(120) NULL COMMENT 'Celular desde el que se pagó, CIFRADO (AES-256-GCM, formato v1:...)',
  numero_operacion VARCHAR(12)  NOT NULL,
  ruta_voucher     VARCHAR(255) NOT NULL COMMENT 'Nombre del archivo en el almacén privado (fuera de la carpeta pública)',
  voucher_sha256   VARCHAR(64)  NOT NULL COMMENT 'SHA-256 del comprobante (hex)',
  estado           ENUM('pendiente','aprobado','rechazado') NOT NULL DEFAULT 'pendiente',
  observacion      VARCHAR(255) NULL COMMENT 'Motivo del rechazo; lo ve el feligrés',
  revisado_por     INT UNSIGNED NULL,
  creado_en        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revisado_en      TIMESTAMP NULL,
  -- Anti-fraude: una operación y un comprobante no pueden usarse en dos pagos vigentes. Los pagos RECHAZADOS quedan
  -- fuera (valor NULL), así el feligrés puede reintentar tras corregir un error sin que choque con el rechazado.
  clave_operacion  VARCHAR(40) AS (IF(estado = 'rechazado', NULL, CONCAT(metodo, ':', numero_operacion))) VIRTUAL,
  clave_voucher    VARCHAR(64) AS (IF(estado = 'rechazado', NULL, voucher_sha256)) VIRTUAL,
  CONSTRAINT uq_pago_operacion UNIQUE (clave_operacion),
  CONSTRAINT uq_pago_voucher   UNIQUE (clave_voucher),
  CONSTRAINT ck_pago_monto CHECK (monto > 0),
  INDEX ix_pago_estado (id_parroquia, estado, creado_en),
  INDEX ix_pago_usuario (id_usuario),
  CONSTRAINT fk_pago_usuario FOREIGN KEY (id_usuario)
    REFERENCES usuario (id_usuario)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_pago_parroquia FOREIGN KEY (id_parroquia)
    REFERENCES parroquia (id_parroquia)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_pago_reserva FOREIGN KEY (id_reserva)
    REFERENCES reserva (id_reserva)
    ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_pago_revisor FOREIGN KEY (revisado_por)
    REFERENCES usuario (id_usuario)
    ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;

-- NOTIFICACION: avisos dentro de la plataforma al feligrés (RF13): pago verificado, pago observado, etc.
CREATE TABLE notificacion (
  id_notificacion INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  id_usuario      INT UNSIGNED NOT NULL,
  asunto          VARCHAR(120) NOT NULL,
  mensaje         VARCHAR(500) NOT NULL,
  leida           BOOLEAN NOT NULL DEFAULT FALSE,
  creada_en       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX ix_notificacion_usuario (id_usuario, leida, creada_en),
  CONSTRAINT fk_notificacion_usuario FOREIGN KEY (id_usuario)
    REFERENCES usuario (id_usuario)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;


-- Pagos (datos de EJEMPLO): el personal cambia los números y los montos desde el panel.
-- Los números 900000000 y 900000001 son ficticios: reemplázalos por los reales de la parroquia.
INSERT INTO cuenta_pago (id_parroquia, yape_numero, yape_titular, plin_numero, plin_titular)
SELECT id_parroquia, '900000000', 'Parroquia (ejemplo)', '900000001', 'Parroquia (ejemplo)' FROM parroquia;

INSERT INTO arancel (id_parroquia, id_sacramento, monto)
SELECT p.id_parroquia, s.id_sacramento,
       CASE s.codigo WHEN 'bautizo' THEN 50 WHEN 'primera_comunion' THEN 60 WHEN 'confirmacion' THEN 70 WHEN 'matrimonio' THEN 250 ELSE 0 END
FROM parroquia p CROSS JOIN sacramento s;
