-- Optional init (compose also creates DB/user via env).
-- Ensures utf8mb4 and grants from any host (LAN multi-backend).

CREATE DATABASE IF NOT EXISTS yap_playerdata
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- User is created by MARIADB_USER; widen host for remote backends.
GRANT ALL PRIVILEGES ON yap_playerdata.* TO 'yap'@'%';
FLUSH PRIVILEGES;
