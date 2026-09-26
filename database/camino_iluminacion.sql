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
  nombre            VARCHAR(80)  NOT NULL,
  apellido          VARCHAR(80)  NOT NULL,
  correo            VARCHAR(120) NOT NULL,
  telefono          VARCHAR(20)  NULL,
  contrasena_hash   VARCHAR(255) NOT NULL,
  rol               ENUM('feligres', 'administrador') NOT NULL DEFAULT 'feligres',
  ubicacion         VARCHAR(150) NULL COMMENT 'Distrito o referencia de ubicación del feligrés',
  creado_en         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

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
  sacramentos_disponibles VARCHAR(255) NULL COMMENT 'Lista separada por comas, ej: Bautizo,Matrimonio',
  creado_en               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
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
  publicado_en   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_aviso_parroquia FOREIGN KEY (id_parroquia)
    REFERENCES parroquia (id_parroquia)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- RESERVA
-- ----------------------------------------------------------------------------
CREATE TABLE reserva (
  id_reserva         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  id_usuario         INT UNSIGNED NOT NULL,
  id_parroquia       INT UNSIGNED NOT NULL,
  sacramento         ENUM('bautizo','matrimonio','primera_comunion','confirmacion') NOT NULL,
  nombre_solicitante VARCHAR(150) NOT NULL,
  fecha_solicitada   DATE NOT NULL,
  notas              TEXT NULL,
  estado             ENUM('pendiente','confirmada','rechazada') NOT NULL DEFAULT 'pendiente',
  creado_en          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_reserva_usuario FOREIGN KEY (id_usuario)
    REFERENCES usuario (id_usuario)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_reserva_parroquia FOREIGN KEY (id_parroquia)
    REFERENCES parroquia (id_parroquia)
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
  fecha_envio          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_validacion     TIMESTAMP NULL,

  CONSTRAINT fk_documento_reserva FOREIGN KEY (id_reserva)
    REFERENCES reserva (id_reserva)
    ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT fk_documento_usuario FOREIGN KEY (id_usuario)
    REFERENCES usuario (id_usuario)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- Índices adicionales para las consultas más frecuentes del sistema
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

INSERT INTO parroquia (nombre, direccion, latitud, longitud, sacramentos_disponibles) VALUES
  ('Parroquia San José', 'Av. Los Álamos 245, distrito de ejemplo', -12.046374, -77.042793, 'Bautizo,Matrimonio,Primera Comunión'),
  ('Parroquia Santa Fe', 'Jr. Las Magnolias 118, distrito de ejemplo', -12.052000, -77.036000, 'Bautizo,Confirmación'),
  ('Parroquia Nuestra Señora del Carmen', 'Calle Los Cipreses 340, distrito de ejemplo', -12.041000, -77.050000, 'Matrimonio,Primera Comunión,Confirmación'),
  ('Capilla Sagrado Corazón', 'Av. Central 89, distrito de ejemplo', -12.058000, -77.045000, 'Bautizo');

INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) VALUES
  (1, 'lunes',    '7:00 a.m. y 7:00 p.m.', 'misa'),
  (1, 'sabado',   '8:00 a.m. y 6:00 p.m. (vigilia)', 'misa'),
  (1, 'domingo',  '7:00 a.m., 9:00 a.m., 11:00 a.m. y 7:00 p.m.', 'misa'),
  (1, 'martes',   'Catequesis de Primera Comunión — 5:00 p.m.', 'actividad'),
  (1, 'jueves',   'Grupo de oración — 7:30 p.m.', 'actividad');

INSERT INTO usuario (nombre, apellido, correo, telefono, contrasena_hash, rol) VALUES
  ('María',   'Fernández', 'maria.fernandez@example.com', '999111222', '$2y$10$reemplazar_por_hash_real', 'feligres'),
  ('Jorge',   'Ramírez',   'jorge.ramirez@example.com',   '999333444', '$2y$10$reemplazar_por_hash_real', 'feligres'),
  ('Lucía',   'Vega',      'lucia.vega@example.com',      '999444555', '$2y$10$reemplazar_por_hash_real', 'feligres'),
  ('Ana',     'Torres',    'ana.torres@example.com',      '999555666', '$2y$10$reemplazar_por_hash_real', 'feligres'),
  ('Personal','Parroquial','admin@caminoiluminacion.pe',  NULL,        '$2y$10$reemplazar_por_hash_real', 'administrador');

INSERT INTO reserva (id_usuario, id_parroquia, sacramento, nombre_solicitante, fecha_solicitada, estado) VALUES
  (1, 1, 'bautizo',           'María Fernández',    '2026-09-12', 'confirmada'),
  (2, 1, 'bautizo',           'Jorge Ramírez',      '2026-09-18', 'pendiente'),
  (3, 1, 'matrimonio',        'Lucía y Pedro Vega', '2026-09-20', 'pendiente'),
  (4, 1, 'primera_comunion',  'Ana Torres',         '2026-09-22', 'pendiente');

INSERT INTO documento (id_reserva, id_usuario, nombre_archivo, ruta_archivo, estado_validacion) VALUES
  (1, 1, 'partida_nacimiento_maria.pdf',   '/uploads/doc1.pdf', 'validado'),
  (2, 2, 'dni_padrino_jorge.pdf',          '/uploads/doc2.pdf', 'pendiente'),
  (3, 3, 'certificado_prematrimonial.pdf', '/uploads/doc3.pdf', 'pendiente'),
  (4, 4, 'constancia_catequesis_ana.pdf',  '/uploads/doc4.pdf', 'pendiente');

INSERT INTO aviso (id_parroquia, titulo, descripcion, tipo, urgente, fecha_evento) VALUES
  (1, 'Cambio de horario — misa de la tarde', 'Por mantenimiento del templo, la misa de las 7:00 p.m. del viernes se traslada a las 6:00 p.m.', 'cambio_horario', TRUE, '2026-08-29'),
  (1, 'Inicio de novena', 'Comienza la novena en honor a la Virgen, todos los días a las 7:00 p.m. en la capilla lateral.', 'celebracion', FALSE, '2026-09-01'),
  (1, 'Vigilia de oración', 'Vigilia nocturna desde las 9:00 p.m. hasta la misa de las 6:00 a.m. del sábado.', 'vigilia', FALSE, '2026-09-05'),
  (1, 'Ayuno y abstinencia', 'Jornada de ayuno y abstinencia. La parroquia atenderá confesiones de 4:00 p.m. a 6:00 p.m.', 'ayuno', TRUE, '2026-09-14');
