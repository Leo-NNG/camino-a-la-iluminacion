-- ============================================================================
-- Migración 04 — Parroquias, horarios y avisos desde la base de datos
-- Para una base ya creada. (Si creas la base desde cero, camino_iluminacion.sql ya lo incluye.)
-- Requiere haber aplicado antes las migraciones 01, 02 y 03.
--
-- Uso:  mysql -u root -p --default-character-set=utf8mb4 < migracion_04_parroquias_horarios_avisos.sql
--
-- ATENCIÓN: REEMPLAZA todos los horarios y avisos por los de ejemplo de Chiclayo. Es seguro si solo
-- tenías los datos de ejemplo del script original (la pantalla de administración todavía no los
-- escribía en la base). Si ya cargaste datos propios en esas dos tablas, haz una copia antes.
-- No toca usuarios, reservas ni documentos.
-- ============================================================================
USE camino_iluminacion;

ALTER TABLE parroquia
  ADD COLUMN foto_url          VARCHAR(255) NULL COMMENT 'Foto exterior (URL)' AFTER sacramentos_disponibles,
  ADD COLUMN foto_interior_url VARCHAR(255) NULL COMMENT 'Foto interior (URL)' AFTER foto_url,
  ADD COLUMN horario_tramites  VARCHAR(255) NULL COMMENT 'Texto libre: cuándo y dónde se atienden los trámites' AFTER foto_interior_url;

UPDATE parroquia SET direccion='Av. Los Álamos 245, Chiclayo', latitud=-6.7701, longitud=-79.8380, sacramentos_disponibles='Bautizo,Matrimonio,Primera Comunión',
  foto_url='https://images.pexels.com/photos/3025593/pexels-photo-3025593.jpeg?auto=compress&cs=tinysrgb&w=900', foto_interior_url='https://images.pexels.com/photos/7520352/pexels-photo-7520352.jpeg?auto=compress&cs=tinysrgb&w=900', horario_tramites='Lunes a viernes, 9:00 a.m. a 1:00 p.m., oficina parroquial.'
  WHERE nombre='Parroquia San José';
UPDATE parroquia SET direccion='Calle Real 512, Chiclayo', latitud=-6.7620, longitud=-79.8450, sacramentos_disponibles='Bautizo,Confirmación',
  foto_url='https://images.pexels.com/photos/5139675/pexels-photo-5139675.jpeg?cs=tinysrgb&w=900', foto_interior_url='https://images.pexels.com/photos/208277/pexels-photo-208277.jpeg?auto=compress&cs=tinysrgb&w=900', horario_tramites='Martes y jueves, 3:00 p.m. a 6:00 p.m., casa parroquial.'
  WHERE nombre='Parroquia Santa Fe';
UPDATE parroquia SET direccion='Jr. Las Palmeras 88, Chiclayo', latitud=-6.7800, longitud=-79.8300, sacramentos_disponibles='Matrimonio,Primera Comunión,Confirmación',
  foto_url='https://images.pexels.com/photos/18750283/pexels-photo-18750283/free-photo-of-photo-of-a-small-church-and-palm-trees.jpeg?cs=tinysrgb&w=900', foto_interior_url='https://images.pexels.com/photos/9153530/pexels-photo-9153530.jpeg?auto=compress&cs=tinysrgb&w=900', horario_tramites='Lunes a sábado, 10:00 a.m. a 1:00 p.m.'
  WHERE nombre='Parroquia Nuestra Señora del Carmen';
UPDATE parroquia SET direccion='Av. Grau 1420, Chiclayo', latitud=-6.7550, longitud=-79.8500, sacramentos_disponibles='Bautizo',
  foto_url='https://images.pexels.com/photos/19623130/pexels-photo-19623130/free-photo-of-exterior-of-a-church.jpeg?auto=compress&cs=tinysrgb&w=900', foto_interior_url='https://images.pexels.com/photos/27872483/pexels-photo-27872483/free-photo-of-a-church-with-a-cross-in-the-middle-of-it.jpeg?auto=compress&cs=tinysrgb&w=900', horario_tramites='Solo domingos, 9:00 a.m. a 10:00 a.m., a la entrada de la capilla.'
  WHERE nombre='Capilla Sagrado Corazón';

DELETE FROM horario;
DELETE FROM aviso;

INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'lunes', '7:00 a.m. y 7:00 p.m.', 'misa' FROM parroquia WHERE nombre='Parroquia San José';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'martes', '7:00 a.m. y 7:00 p.m.', 'misa' FROM parroquia WHERE nombre='Parroquia San José';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'miercoles', '7:00 a.m. y 7:00 p.m.', 'misa' FROM parroquia WHERE nombre='Parroquia San José';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'jueves', '7:00 a.m. y 7:00 p.m.', 'misa' FROM parroquia WHERE nombre='Parroquia San José';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'viernes', '7:00 a.m. y 7:00 p.m.', 'misa' FROM parroquia WHERE nombre='Parroquia San José';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'sabado', '8:00 a.m. y 6:00 p.m.', 'misa' FROM parroquia WHERE nombre='Parroquia San José';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'domingo', '7, 9, 11 a.m. y 7 p.m.', 'misa' FROM parroquia WHERE nombre='Parroquia San José';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'martes', 'Catequesis de Primera Comunión — 5:00 p.m.', 'actividad' FROM parroquia WHERE nombre='Parroquia San José';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'jueves', 'Grupo de oración — 7:30 p.m.', 'actividad' FROM parroquia WHERE nombre='Parroquia San José';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'lunes', '6:30 a.m. y 6:30 p.m.', 'misa' FROM parroquia WHERE nombre='Parroquia Santa Fe';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'martes', '6:30 a.m. y 6:30 p.m.', 'misa' FROM parroquia WHERE nombre='Parroquia Santa Fe';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'miercoles', '6:30 a.m. y 6:30 p.m.', 'misa' FROM parroquia WHERE nombre='Parroquia Santa Fe';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'jueves', '6:30 a.m. y 6:30 p.m.', 'misa' FROM parroquia WHERE nombre='Parroquia Santa Fe';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'viernes', '6:30 a.m. y 6:30 p.m.', 'misa' FROM parroquia WHERE nombre='Parroquia Santa Fe';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'sabado', '5:00 p.m. (vigilia)', 'misa' FROM parroquia WHERE nombre='Parroquia Santa Fe';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'domingo', '8, 10 a.m. y 6 p.m.', 'misa' FROM parroquia WHERE nombre='Parroquia Santa Fe';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'miercoles', 'Catequesis de Confirmación — 6:00 p.m.', 'actividad' FROM parroquia WHERE nombre='Parroquia Santa Fe';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'lunes', '7:30 a.m.', 'misa' FROM parroquia WHERE nombre='Parroquia Nuestra Señora del Carmen';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'martes', '7:30 a.m.', 'misa' FROM parroquia WHERE nombre='Parroquia Nuestra Señora del Carmen';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'miercoles', '7:30 a.m.', 'misa' FROM parroquia WHERE nombre='Parroquia Nuestra Señora del Carmen';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'jueves', '7:30 a.m.', 'misa' FROM parroquia WHERE nombre='Parroquia Nuestra Señora del Carmen';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'viernes', '7:30 a.m.', 'misa' FROM parroquia WHERE nombre='Parroquia Nuestra Señora del Carmen';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'sabado', '6:00 p.m.', 'misa' FROM parroquia WHERE nombre='Parroquia Nuestra Señora del Carmen';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'domingo', '9 a.m., 12 p.m. y 7 p.m.', 'misa' FROM parroquia WHERE nombre='Parroquia Nuestra Señora del Carmen';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'viernes', 'Rosario y exposición del Santísimo — 5:30 p.m.', 'actividad' FROM parroquia WHERE nombre='Parroquia Nuestra Señora del Carmen';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'sabado', '4:00 p.m.', 'misa' FROM parroquia WHERE nombre='Capilla Sagrado Corazón';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'domingo', '10:00 a.m.', 'misa' FROM parroquia WHERE nombre='Capilla Sagrado Corazón';
INSERT INTO horario (id_parroquia, dia_semana, hora, tipo) SELECT id_parroquia, 'sabado', 'Charla de preparación para bautizos comunitarios — 3:00 p.m.', 'actividad' FROM parroquia WHERE nombre='Capilla Sagrado Corazón';
INSERT INTO aviso (id_parroquia, titulo, descripcion, tipo, urgente, fecha_evento) SELECT id_parroquia, 'Cambio de horario — misa de la tarde', 'Por mantenimiento del templo, la misa de las 7:00 p.m. del viernes se traslada a las 6:00 p.m.', 'cambio_horario', TRUE, '2026-10-09' FROM parroquia WHERE nombre='Parroquia San José';
INSERT INTO aviso (id_parroquia, titulo, descripcion, tipo, urgente, fecha_evento) SELECT id_parroquia, 'Inicio de novena', 'Comienza la novena en honor a la Virgen, todos los días a las 7:00 p.m. en la capilla lateral.', 'celebracion', FALSE, '2026-10-12' FROM parroquia WHERE nombre='Parroquia San José';
INSERT INTO aviso (id_parroquia, titulo, descripcion, tipo, urgente, fecha_evento) SELECT id_parroquia, 'Vigilia de oración', 'Vigilia nocturna desde las 9:00 p.m. hasta la misa de las 6:00 a.m. del sábado.', 'vigilia', FALSE, '2026-10-17' FROM parroquia WHERE nombre='Parroquia San José';
INSERT INTO aviso (id_parroquia, titulo, descripcion, tipo, urgente, fecha_evento) SELECT id_parroquia, 'Ayuno y abstinencia', 'Jornada de ayuno y abstinencia. La parroquia atenderá confesiones de 4:00 p.m. a 6:00 p.m.', 'ayuno', TRUE, '2026-10-23' FROM parroquia WHERE nombre='Parroquia San José';
INSERT INTO aviso (id_parroquia, titulo, descripcion, tipo, urgente, fecha_evento) SELECT id_parroquia, 'Inscripciones a Confirmación', 'Están abiertas las inscripciones para el grupo de Confirmación. Atención en la casa parroquial, martes y jueves.', 'otro', FALSE, '2026-10-20' FROM parroquia WHERE nombre='Parroquia Santa Fe';
INSERT INTO aviso (id_parroquia, titulo, descripcion, tipo, urgente, fecha_evento) SELECT id_parroquia, 'Fiesta patronal', 'Misa solemne y procesión de la imagen de la Virgen del Carmen por las calles aledañas.', 'celebracion', FALSE, '2026-10-25' FROM parroquia WHERE nombre='Parroquia Nuestra Señora del Carmen';
