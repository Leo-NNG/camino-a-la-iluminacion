-- ============================================================================
-- Migración 01 — DNI como credencial y parroquia del administrador
-- Para una base de datos YA creada con la versión anterior de camino_iluminacion.sql.
-- (Si vas a crear la base desde cero, usa camino_iluminacion.sql: ya incluye esto.)
--
-- Uso:  mysql -u root -p --default-character-set=utf8mb4 < migracion_01_dni_roles.sql
--
-- ATENCIÓN: se BORRAN las cuentas que no son las 5 de demostración (las registradas
-- a mano con la versión anterior): no tienen DNI y su contraseña se guardó sin hash,
-- así que no podrían iniciar sesión de todos modos. Sus reservas y documentos se
-- borran con ellas (ON DELETE CASCADE). Haz una copia si necesitas conservarlos.
-- ============================================================================
USE camino_iluminacion;

-- 1) Columnas nuevas (dni queda opcional solo mientras se migra)
ALTER TABLE usuario
  ADD COLUMN dni          CHAR(8)      NULL AFTER id_usuario,
  ADD COLUMN id_parroquia INT UNSIGNED NULL AFTER ubicacion;

-- 2) DNI, contraseña con hash y parroquia de las cuentas de demostración
--    (contraseña: Demo1234)
UPDATE usuario SET dni='70000001', contrasena_hash='pbkdf2$120000$siMdRnNa8EYp3SuZXCNGSg==$k8pV0urFl7dy5jRq9Zm8mfyW05HCi9BOBpXGt6kDN4I=' WHERE correo='maria.fernandez@example.com';
UPDATE usuario SET dni='70000002', contrasena_hash='pbkdf2$120000$dEaKG+4XMNm2PUfKungeog==$OJguo69V8mjkQ+BAYmc2PJOgiplAhp/NhQQs0G7ec80=' WHERE correo='jorge.ramirez@example.com';
UPDATE usuario SET dni='70000003', contrasena_hash='pbkdf2$120000$SXa6PWdCY4Zlg2WtAyoKeQ==$zXRsz1OKKig2WriBgPEtXB3veiYz1sRZH0oh62I6hpU=' WHERE correo='lucia.vega@example.com';
UPDATE usuario SET dni='70000004', contrasena_hash='pbkdf2$120000$byLs4hEE6LEycXPyeMUJnQ==$ixaXvIe8ZMZMENYSHHKjRtKC1MHt+Ws6+eA/ZX1atMs=' WHERE correo='ana.torres@example.com';
UPDATE usuario SET dni='70000000', contrasena_hash='pbkdf2$120000$LbWUqcwLOoD2j3jVkDkxNg==$3xLosli1Uuanpb1KJVTjEbql6SoJWn+AkuPHI6+IEvU=', id_parroquia=1 WHERE correo='admin@caminoiluminacion.pe';

-- 3) Cuentas antiguas sin DNI
DELETE FROM usuario WHERE dni IS NULL;

-- 4) Restricciones definitivas
ALTER TABLE usuario
  MODIFY COLUMN dni CHAR(8) NOT NULL COMMENT 'Documento de identidad (8 dígitos): es la credencial de acceso',
  ADD CONSTRAINT uq_usuario_dni UNIQUE (dni),
  ADD CONSTRAINT ck_usuario_dni CHECK (dni REGEXP '^[0-9]{8}$'),
  ADD CONSTRAINT fk_usuario_parroquia FOREIGN KEY (id_parroquia)
    REFERENCES parroquia (id_parroquia) ON DELETE SET NULL ON UPDATE CASCADE;
