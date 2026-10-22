-- ============================================================================
-- Camino a la Iluminación — Base de datos
-- Curso Integrador I
-- Motor: MySQL 8.x / MariaDB 10.x
--
-- Corresponde al modelo entidad-relación del Capítulo 8.3 del informe:
-- USUARIO, PARROQUIA, HORARIO, AVISO, RESERVA, DOCUMENTO
--
-- IMPORTANTE: si cargan este script por línea de comandos, indiquen utf8mb4
-- al cliente de mysql para que los acentos y la ñ no se corrompan:
--   mysql -u root --default-character-set=utf8mb4 < camino_iluminacion.sql
-- Si lo cargan desde MySQL Workbench, esto no hace falta: Workbench ya lo
-- maneja automáticamente.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS camino_iluminacion
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE camino_iluminacion;

-- ----------------------------------------------------------------------------
-- USUARIO
-- Representa tanto a feligreses como a personal parroquial (columna `rol`).
-- Coincide con la herencia Usuario -> Feligrés / Administrador del diagrama
-- de clases UML (Capítulo 8.4): en una base de datos relacional se modela
-- como una sola tabla con un campo que distingue el tipo de usuario.
-- ----------------------------------------------------------------------------
CREATE TABLE usuario (
  id_usuario        INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  dni               VARCHAR(120) NOT NULL COMMENT 'DNI CIFRADO (AES-256-GCM, formato v1:...): es la credencial de acceso; en claro solo existe en la aplicación',
  dni_indice        CHAR(64)     NULL     COMMENT 'Huella HMAC-SHA256 del DNI: búsqueda y unicidad sin guardar el DNI en claro',
  nombre            VARCHAR(80)  NOT NULL,
  apellido          VARCHAR(80)  NOT NULL,
  correo            VARCHAR(120) NOT NULL,
  telefono          VARCHAR(20)  NULL,
  contrasena_hash   VARCHAR(255) NOT NULL,
  rol               ENUM('feligres', 'administrador') NOT NULL DEFAULT 'feligres',
  ubicacion         VARCHAR(150) NULL COMMENT 'Distrito o referencia de ubicación del feligrés',
  id_parroquia      INT UNSIGNED NULL COMMENT 'Solo para personal (administrador): parroquia que gestiona',
  creado_en         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT uq_usuario_dni    UNIQUE (dni_indice),
  CONSTRAINT uq_usuario_correo UNIQUE (correo)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- PARROQUIA
-- ----------------------------------------------------------------------------
CREATE TABLE parroquia (
  id_parroquia            INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  nombre                  VARCHAR(120) NOT NULL,
  direccion               VARCHAR(200) NOT NULL,
  latitud                 DECIMAL(9,6) NULL,
  longitud                DECIMAL(9,6) NULL,
  foto_url                VARCHAR(255) NULL COMMENT 'Foto exterior (URL)',
  foto_interior_url       VARCHAR(255) NULL COMMENT 'Foto interior (URL)',
  horario_tramites        VARCHAR(255) NULL COMMENT 'Texto libre: cuándo y dónde se atienden los trámites',
  creado_en               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- El personal (administrador) pertenece a una parroquia; los feligreses no (NULL).
ALTER TABLE usuario
  ADD CONSTRAINT fk_usuario_parroquia FOREIGN KEY (id_parroquia)
    REFERENCES parroquia (id_parroquia)
    ON DELETE SET NULL ON UPDATE CASCADE;

-- ----------------------------------------------------------------------------
-- SACRAMENTO — catálogo de sacramentos. Agregar uno nuevo es insertar una fila.
-- ----------------------------------------------------------------------------
CREATE TABLE sacramento (
  id_sacramento TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  codigo        VARCHAR(30) NOT NULL COMMENT 'Identificador estable, ej: primera_comunion',
  nombre        VARCHAR(60) NOT NULL COMMENT 'Nombre que ve el usuario',

  CONSTRAINT uq_sacramento_codigo UNIQUE (codigo),
  CONSTRAINT uq_sacramento_nombre UNIQUE (nombre)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- PARROQUIA_SACRAMENTO — qué sacramentos ofrece cada parroquia (relación N:M)
-- ----------------------------------------------------------------------------
CREATE TABLE parroquia_sacramento (
  id_parroquia  INT UNSIGNED NOT NULL,
  id_sacramento TINYINT UNSIGNED NOT NULL,

  PRIMARY KEY (id_parroquia, id_sacramento),
  CONSTRAINT fk_ps_parroquia FOREIGN KEY (id_parroquia)
    REFERENCES parroquia (id_parroquia)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_ps_sacramento FOREIGN KEY (id_sacramento)
    REFERENCES sacramento (id_sacramento)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- HORARIO
-- Un registro por franja (día + hora + tipo) de una parroquia.
-- ----------------------------------------------------------------------------
CREATE TABLE horario (
  id_horario     INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  id_parroquia   INT UNSIGNED NOT NULL,
  dia_semana     ENUM('lunes','martes','miercoles','jueves','viernes','sabado','domingo') NOT NULL,
  hora           VARCHAR(120) NOT NULL COMMENT 'Ej: "7:00 a.m. y 7:00 p.m."',
  tipo           ENUM('misa','actividad') NOT NULL DEFAULT 'misa',

  CONSTRAINT fk_horario_parroquia FOREIGN KEY (id_parroquia)
    REFERENCES parroquia (id_parroquia)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- AVISO
-- ----------------------------------------------------------------------------
CREATE TABLE aviso (
  id_aviso       INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  id_parroquia   INT UNSIGNED NOT NULL,
  titulo         VARCHAR(150) NOT NULL,
  descripcion    TEXT NOT NULL,
  tipo           ENUM('santa_cena','ayuno','vigilia','celebracion','cambio_horario','otro') NOT NULL DEFAULT 'otro',
  urgente        BOOLEAN NOT NULL DEFAULT FALSE,
  fecha_evento   DATE NULL,
  id_autor       INT UNSIGNED NULL COMMENT 'Quién lo publicó (queda NULL si esa cuenta se elimina)',
  publicado_en   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_aviso_parroquia FOREIGN KEY (id_parroquia)
    REFERENCES parroquia (id_parroquia)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_aviso_autor FOREIGN KEY (id_autor)
    REFERENCES usuario (id_usuario)
    ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- RESERVA
-- ----------------------------------------------------------------------------
CREATE TABLE reserva (
  id_reserva         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  id_usuario         INT UNSIGNED NOT NULL,
  id_parroquia       INT UNSIGNED NOT NULL,
  id_sacramento      TINYINT UNSIGNED NOT NULL,
  nombre_solicitante VARCHAR(150) NOT NULL,
  fecha_solicitada   DATE NOT NULL,
  notas              TEXT NULL,
  estado             ENUM('pendiente','confirmada','rechazada') NOT NULL DEFAULT 'pendiente',
  motivo_rechazo     VARCHAR(255) NULL COMMENT 'Lo escribe el personal al rechazar; el feligrés lo ve en Mis solicitudes',
  creado_en          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_reserva_usuario FOREIGN KEY (id_usuario)
    REFERENCES usuario (id_usuario)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_reserva_parroquia FOREIGN KEY (id_parroquia)
    REFERENCES parroquia (id_parroquia)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_reserva_sacramento FOREIGN KEY (id_sacramento)
    REFERENCES sacramento (id_sacramento)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- DOCUMENTO
-- id_reserva es NULL-able: un documento puede subirse antes de que el
-- trámite quede formalmente asociado a una reserva (tal como ocurre hoy
-- en el prototipo, donde "subir documento" crea un trámite "Por confirmar").
-- ----------------------------------------------------------------------------
CREATE TABLE documento (
  id_documento        INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  id_reserva          INT UNSIGNED NULL,
  id_usuario          INT UNSIGNED NOT NULL,
  nombre_archivo       VARCHAR(200) NOT NULL,
  ruta_archivo         VARCHAR(255) NOT NULL,
  estado_validacion    ENUM('pendiente','validado','rechazado') NOT NULL DEFAULT 'pendiente',
  motivo_rechazo       VARCHAR(255) NULL,
  tipo_mime            VARCHAR(50)  NOT NULL DEFAULT 'application/pdf' COMMENT 'application/pdf, image/jpeg o image/png (detectado por contenido)',
  tamano_bytes         INT UNSIGNED NULL,
  fecha_envio          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_validacion     TIMESTAMP NULL,
  id_validador         INT UNSIGNED NULL COMMENT 'Quién lo validó o rechazó (queda NULL si esa cuenta se elimina)',

  CONSTRAINT fk_documento_reserva FOREIGN KEY (id_reserva)
    REFERENCES reserva (id_reserva)
    ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_documento_usuario FOREIGN KEY (id_usuario)
    REFERENCES usuario (id_usuario)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_documento_validador FOREIGN KEY (id_validador)
    REFERENCES usuario (id_usuario)
    ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- Índices adicionales para las consultas más frecuentes del sistema
-- ----------------------------------------------------------------------------
-- BITACORA — registro de acciones críticas (RNF09): ingresos, reservas, validaciones, publicaciones.
-- No guarda DNI, contraseñas ni contenido de documentos.
-- ----------------------------------------------------------------------------
CREATE TABLE bitacora (
  id_bitacora INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  id_usuario  INT UNSIGNED NULL COMMENT 'NULL si no se pudo identificar o la cuenta se eliminó',
  accion      VARCHAR(40)  NOT NULL COMMENT 'Ej: LOGIN_OK, LOGIN_FALLIDO, RESERVA_CREADA, DOCUMENTO_VALIDADO',
  detalle     VARCHAR(255) NULL,
  ip          VARCHAR(45)  NULL,
  creado_en   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  INDEX ix_bitacora_fecha (creado_en),
  INDEX ix_bitacora_usuario (id_usuario),
  CONSTRAINT fk_bitacora_usuario FOREIGN KEY (id_usuario)
    REFERENCES usuario (id_usuario)
    ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;

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

-- ----------------------------------------------------------------------------
CREATE INDEX idx_reserva_estado       ON reserva (estado);
CREATE INDEX idx_documento_estado     ON documento (estado_validacion);
CREATE INDEX idx_aviso_parroquia      ON aviso (id_parroquia, publicado_en);
CREATE INDEX idx_horario_parroquia    ON horario (id_parroquia, dia_semana);

-- ============================================================================
-- DATOS DE EJEMPLO
-- Mismos datos que ya usa el prototipo (js/main.js), para poder mostrar
-- el sistema con información real y consistente entre el prototipo y la BD.
-- ============================================================================

-- Parroquias de ejemplo (Chiclayo): son la ÚNICA fuente de datos del mapa, las fichas, el selector de reservas, los horarios y los avisos.
INSERT INTO sacramento (codigo, nombre) VALUES
  ('bautizo', 'Bautizo'),
  ('matrimonio', 'Matrimonio'),
  ('primera_comunion', 'Primera Comunión'),
  ('confirmacion', 'Confirmación');

INSERT INTO parroquia (nombre, direccion, latitud, longitud, foto_url, foto_interior_url, horario_tramites) VALUES
  ('Parroquia San José', 'Av. Los Álamos 245, Chiclayo', -6.7701, -79.8380, 'https://images.pexels.com/photos/3025593/pexels-photo-3025593.jpeg?auto=compress&cs=tinysrgb&w=900', 'https://images.pexels.com/photos/7520352/pexels-photo-7520352.jpeg?auto=compress&cs=tinysrgb&w=900', 'Lunes a viernes, 9:00 a.m. a 1:00 p.m., oficina parroquial.'),
  ('Parroquia Santa Fe', 'Calle Real 512, Chiclayo', -6.7620, -79.8450, 'https://images.pexels.com/photos/5139675/pexels-photo-5139675.jpeg?cs=tinysrgb&w=900', 'https://images.pexels.com/photos/208277/pexels-photo-208277.jpeg?auto=compress&cs=tinysrgb&w=900', 'Martes y jueves, 3:00 p.m. a 6:00 p.m., casa parroquial.'),
  ('Parroquia Nuestra Señora del Carmen', 'Jr. Las Palmeras 88, Chiclayo', -6.7800, -79.8300, 'https://images.pexels.com/photos/18750283/pexels-photo-18750283/free-photo-of-photo-of-a-small-church-and-palm-trees.jpeg?cs=tinysrgb&w=900', 'https://images.pexels.com/photos/9153530/pexels-photo-9153530.jpeg?auto=compress&cs=tinysrgb&w=900', 'Lunes a sábado, 10:00 a.m. a 1:00 p.m.'),
  ('Capilla Sagrado Corazón', 'Av. Grau 1420, Chiclayo', -6.7550, -79.8500, 'https://images.pexels.com/photos/19623130/pexels-photo-19623130/free-photo-of-exterior-of-a-church.jpeg?auto=compress&cs=tinysrgb&w=900', 'https://images.pexels.com/photos/27872483/pexels-photo-27872483/free-photo-of-a-church-with-a-cross-in-the-middle-of-it.jpeg?auto=compress&cs=tinysrgb&w=900', 'Solo domingos, 9:00 a.m. a 10:00 a.m., a la entrada de la capilla.');

INSERT INTO parroquia_sacramento (id_parroquia, id_sacramento) VALUES
  (1, 1), (1, 2), (1, 3),   -- San José: Bautizo, Matrimonio, Primera Comunión
  (2, 1), (2, 4),           -- Santa Fe: Bautizo, Confirmación
  (3, 2), (3, 3), (3, 4),   -- Nuestra Señora del Carmen: Matrimonio, Primera Comunión, Confirmación
  (4, 1);                   -- Sagrado Corazón: Bautizo

INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) VALUES
  (1, 'lunes', '7:00 a.m. y 7:00 p.m.', 'misa'),
  (1, 'martes', '7:00 a.m. y 7:00 p.m.', 'misa'),
  (1, 'miercoles', '7:00 a.m. y 7:00 p.m.', 'misa'),
  (1, 'jueves', '7:00 a.m. y 7:00 p.m.', 'misa'),
  (1, 'viernes', '7:00 a.m. y 7:00 p.m.', 'misa'),
  (1, 'sabado', '8:00 a.m. y 6:00 p.m.', 'misa'),
  (1, 'domingo', '7, 9, 11 a.m. y 7 p.m.', 'misa'),
  (1, 'martes', 'Catequesis de Primera Comunión — 5:00 p.m.', 'actividad'),
  (1, 'jueves', 'Grupo de oración — 7:30 p.m.', 'actividad'),
  (2, 'lunes', '6:30 a.m. y 6:30 p.m.', 'misa'),
  (2, 'martes', '6:30 a.m. y 6:30 p.m.', 'misa'),
  (2, 'miercoles', '6:30 a.m. y 6:30 p.m.', 'misa'),
  (2, 'jueves', '6:30 a.m. y 6:30 p.m.', 'misa'),
  (2, 'viernes', '6:30 a.m. y 6:30 p.m.', 'misa'),
  (2, 'sabado', '5:00 p.m. (vigilia)', 'misa'),
  (2, 'domingo', '8, 10 a.m. y 6 p.m.', 'misa'),
  (2, 'miercoles', 'Catequesis de Confirmación — 6:00 p.m.', 'actividad'),
  (3, 'lunes', '7:30 a.m.', 'misa'),
  (3, 'martes', '7:30 a.m.', 'misa'),
  (3, 'miercoles', '7:30 a.m.', 'misa'),
  (3, 'jueves', '7:30 a.m.', 'misa'),
  (3, 'viernes', '7:30 a.m.', 'misa'),
  (3, 'sabado', '6:00 p.m.', 'misa'),
  (3, 'domingo', '9 a.m., 12 p.m. y 7 p.m.', 'misa'),
  (3, 'viernes', 'Rosario y exposición del Santísimo — 5:30 p.m.', 'actividad'),
  (4, 'sabado', '4:00 p.m.', 'misa'),
  (4, 'domingo', '10:00 a.m.', 'misa'),
  (4, 'sabado', 'Charla de preparación para bautizos comunitarios — 3:00 p.m.', 'actividad');

-- Usuarios de DEMOSTRACIÓN. Contraseña de todos: Demo1234  (cámbiala antes de cualquier uso real).
-- DNI de ejemplo ficticios. El administrador gestiona la parroquia 1 (San José).
-- Se insertan en claro y la aplicación los cifra sola al arrancar (MigracionDni); por eso dni_indice queda NULL aquí.
INSERT INTO usuario (dni, nombre, apellido, correo, telefono, contrasena_hash, rol, id_parroquia) VALUES
  ('70000001', 'María',   'Fernández', 'maria.fernandez@example.com', '999111222', 'pbkdf2$120000$siMdRnNa8EYp3SuZXCNGSg==$k8pV0urFl7dy5jRq9Zm8mfyW05HCi9BOBpXGt6kDN4I=', 'feligres', NULL),
  ('70000002', 'Jorge',   'Ramírez',   'jorge.ramirez@example.com',   '999333444', 'pbkdf2$120000$dEaKG+4XMNm2PUfKungeog==$OJguo69V8mjkQ+BAYmc2PJOgiplAhp/NhQQs0G7ec80=', 'feligres', NULL),
  ('70000003', 'Lucía',   'Vega',      'lucia.vega@example.com',      '999444555', 'pbkdf2$120000$SXa6PWdCY4Zlg2WtAyoKeQ==$zXRsz1OKKig2WriBgPEtXB3veiYz1sRZH0oh62I6hpU=', 'feligres', NULL),
  ('70000004', 'Ana',     'Torres',    'ana.torres@example.com',      '999555666', 'pbkdf2$120000$byLs4hEE6LEycXPyeMUJnQ==$ixaXvIe8ZMZMENYSHHKjRtKC1MHt+Ws6+eA/ZX1atMs=', 'feligres', NULL),
  ('70000000', 'Personal','Parroquial','admin@caminoiluminacion.pe',  NULL,        'pbkdf2$120000$LbWUqcwLOoD2j3jVkDkxNg==$3xLosli1Uuanpb1KJVTjEbql6SoJWn+AkuPHI6+IEvU=', 'administrador', 1);

INSERT INTO reserva (id_usuario, id_parroquia, id_sacramento, nombre_solicitante, fecha_solicitada, estado) VALUES
  (1, 1, 1, 'María Fernández',    '2026-09-12', 'confirmada'),   -- Bautizo
  (2, 1, 1, 'Jorge Ramírez',      '2026-09-18', 'pendiente'),    -- Bautizo
  (3, 1, 2, 'Lucía y Pedro Vega', '2026-09-20', 'pendiente'),    -- Matrimonio
  (4, 1, 3, 'Ana Torres',         '2026-09-22', 'pendiente');    -- Primera Comunión

-- (Sin documentos de ejemplo: ahora los archivos se suben de verdad desde la página "Mis documentos".)

INSERT INTO aviso (id_parroquia, titulo, descripcion, tipo, urgente, fecha_evento) VALUES
  (1, 'Cambio de horario — misa de la tarde', 'Por mantenimiento del templo, la misa de las 7:00 p.m. del viernes se traslada a las 6:00 p.m.', 'cambio_horario', TRUE, '2026-10-09'),
  (1, 'Inicio de novena', 'Comienza la novena en honor a la Virgen, todos los días a las 7:00 p.m. en la capilla lateral.', 'celebracion', FALSE, '2026-10-12'),
  (1, 'Vigilia de oración', 'Vigilia nocturna desde las 9:00 p.m. hasta la misa de las 6:00 a.m. del sábado.', 'vigilia', FALSE, '2026-10-17'),
  (1, 'Ayuno y abstinencia', 'Jornada de ayuno y abstinencia. La parroquia atenderá confesiones de 4:00 p.m. a 6:00 p.m.', 'ayuno', TRUE, '2026-10-23'),
  (2, 'Inscripciones a Confirmación', 'Están abiertas las inscripciones para el grupo de Confirmación. Atención en la casa parroquial, martes y jueves.', 'otro', FALSE, '2026-10-20'),
  (3, 'Fiesta patronal', 'Misa solemne y procesión de la imagen de la Virgen del Carmen por las calles aledañas.', 'celebracion', FALSE, '2026-10-25');

-- Pagos (datos de EJEMPLO): el personal cambia los números y los montos desde el panel.
-- Los números 900000000 y 900000001 son ficticios: reemplázalos por los reales de la parroquia.
INSERT INTO cuenta_pago (id_parroquia, yape_numero, yape_titular, plin_numero, plin_titular)
SELECT id_parroquia, '900000000', 'Parroquia (ejemplo)', '900000001', 'Parroquia (ejemplo)' FROM parroquia;

INSERT INTO arancel (id_parroquia, id_sacramento, monto)
SELECT p.id_parroquia, s.id_sacramento,
       CASE s.codigo WHEN 'bautizo' THEN 50 WHEN 'primera_comunion' THEN 60 WHEN 'confirmacion' THEN 70 WHEN 'matrimonio' THEN 250 ELSE 0 END
FROM parroquia p CROSS JOIN sacramento s;
