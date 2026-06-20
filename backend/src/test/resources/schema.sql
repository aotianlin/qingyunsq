-- ============================================================
-- CampusForum Database Schema (Test)
-- Testcontainers 鍒濆鍖栬剼鏈?鈥?鍘婚櫎 CREATE DATABASE / USE 璇彞
-- ============================================================

-- ============================================================
-- 1. tenants 绉熸埛/瀛︽牎
-- ============================================================
CREATE TABLE IF NOT EXISTS tenants (
  id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  code         VARCHAR(32)  NOT NULL COMMENT '绉熸埛缂栫爜锛堢敤浜庡瓙鍩熷悕锛?,
  name         VARCHAR(128) NOT NULL COMMENT '瀛︽牎鍏ㄧО',
  logo_url     VARCHAR(255) DEFAULT NULL,
  domain       VARCHAR(128) DEFAULT NULL,
  status       TINYINT NOT NULL DEFAULT 1 COMMENT '1鍚敤 0鍋滅敤',
  ai_config    JSON DEFAULT NULL COMMENT 'AI 閰嶇疆',
  announcement VARCHAR(500) DEFAULT NULL COMMENT '绉熸埛鍏憡',
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB COMMENT='绉熸埛/瀛︽牎';

-- ============================================================
-- 2. users 鐢ㄦ埛
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
  id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  student_no      VARCHAR(32)  DEFAULT NULL COMMENT '瀛﹀彿',
  email           VARCHAR(128) NOT NULL,
  password_hash   VARCHAR(128) NOT NULL,
  nickname        VARCHAR(64)  NOT NULL,
  avatar_url      VARCHAR(255) DEFAULT NULL,
  bio             VARCHAR(255) DEFAULT NULL,
  college         VARCHAR(64)  DEFAULT NULL COMMENT '瀛﹂櫌',
  major           VARCHAR(64)  DEFAULT NULL COMMENT '涓撲笟',
  grade           VARCHAR(8)   DEFAULT NULL COMMENT '骞寸骇',
  role            VARCHAR(32)  NOT NULL DEFAULT 'USER' COMMENT 'USER/TENANT_ADMIN/SUPER_ADMIN',
  status          TINYINT NOT NULL DEFAULT 1 COMMENT '1姝ｅ父 0灏佺',
  last_login_at   DATETIME DEFAULT NULL,
  reset_token     VARCHAR(64)  DEFAULT NULL COMMENT '瀵嗙爜閲嶇疆浠ょ墝 SHA-256 鍝堝笇锛坔ex锛?,
  reset_token_expires DATETIME DEFAULT NULL COMMENT '瀵嗙爜閲嶇疆浠ょ墝杩囨湡鏃堕棿',
  mute_settings   JSON DEFAULT NULL COMMENT '娑堟伅鍏嶆墦鎵拌缃?,
  tag_subscriptions JSON DEFAULT NULL COMMENT '闂瓟鏍囩璁㈤槄',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_tenant_email (tenant_id, email),
  UNIQUE KEY uk_tenant_student (tenant_id, student_no),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB COMMENT='鐢ㄦ埛';

-- ============================================================
-- 3. spaces 瀛︿範绌洪棿
-- ============================================================
CREATE TABLE IF NOT EXISTS spaces (
  id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id     BIGINT UNSIGNED NOT NULL,
  owner_id      BIGINT UNSIGNED NOT NULL,
  name          VARCHAR(64)  NOT NULL,
  description   VARCHAR(255) DEFAULT NULL,
  category      VARCHAR(16)  NOT NULL COMMENT 'MAJOR/CLASS/CLUB/INTEREST',
  visibility    VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC/REVIEW/INVITE',
  cover_url     VARCHAR(255) DEFAULT NULL,
  sensitive_words TEXT DEFAULT NULL COMMENT '绌洪棿鑷畾涔夋晱鎰熻瘝',
  post_notice   VARCHAR(500) DEFAULT NULL COMMENT '鍙戝笘椤荤煡',
  member_count  INT NOT NULL DEFAULT 0,
  post_count    INT NOT NULL DEFAULT 0,
  status        TINYINT NOT NULL DEFAULT 1,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT NOT NULL DEFAULT 0,
  KEY idx_tenant_category (tenant_id, category),
  KEY idx_owner (owner_id)
) ENGINE=InnoDB COMMENT='瀛︿範绌洪棿';

-- ============================================================
-- 4. space_members 绌洪棿鎴愬憳
-- ============================================================
CREATE TABLE IF NOT EXISTS space_members (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id   BIGINT UNSIGNED NOT NULL,
  space_id    BIGINT UNSIGNED NOT NULL,
  user_id     BIGINT UNSIGNED NOT NULL,
  role        VARCHAR(16) NOT NULL DEFAULT 'MEMBER' COMMENT 'OWNER/ADMIN/MEMBER',
  status      TINYINT NOT NULL DEFAULT 0 COMMENT '0寰呭鏍?1宸插姞鍏?2宸查€€鍑?3宸叉嫆缁?,
  joined_at   DATETIME DEFAULT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_space_user (space_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='绌洪棿鎴愬憳';

-- ============================================================
-- 5. posts 甯栧瓙锛堢粺涓€琛級
-- ============================================================
CREATE TABLE IF NOT EXISTS posts (
  id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id     BIGINT UNSIGNED NOT NULL,
  author_id     BIGINT UNSIGNED NOT NULL,
  scope         VARCHAR(8)  NOT NULL COMMENT 'SQUARE/SPACE',
  space_id      BIGINT UNSIGNED DEFAULT NULL,
  type          VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/QA/CHECKIN/RESOURCE',
  title         VARCHAR(255) DEFAULT NULL,
  content       MEDIUMTEXT NOT NULL,
  attachments   JSON DEFAULT NULL,
  topics        JSON DEFAULT NULL COMMENT '璇濋',
  tags          JSON DEFAULT NULL,
  ai_summary    TEXT DEFAULT NULL COMMENT 'AI 鐢熸垚鎽樿',
  ai_risk_level TINYINT DEFAULT 0 COMMENT '0姝ｅ父 1涓闄?2楂橀闄?,
  view_count    INT NOT NULL DEFAULT 0,
  like_count    INT NOT NULL DEFAULT 0,
  comment_count INT NOT NULL DEFAULT 0,
  is_pinned     TINYINT NOT NULL DEFAULT 0,
  is_essence    TINYINT NOT NULL DEFAULT 0,
  status        TINYINT NOT NULL DEFAULT 1 COMMENT '0寰呭 1姝ｅ父 2闅愯棌',
  pinned_at     DATETIME DEFAULT NULL COMMENT '缃《鏃堕棿',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT NOT NULL DEFAULT 0,
  KEY idx_tenant_scope_time (tenant_id, scope, created_at),
  KEY idx_space_time (space_id, created_at),
  KEY idx_author (author_id),
  FULLTEXT KEY ft_title_content (title, content) /*!50700 WITH PARSER ngram */
) ENGINE=InnoDB COMMENT='甯栧瓙';

-- ============================================================
-- 6. comments 璇勮
-- ============================================================
CREATE TABLE IF NOT EXISTS comments (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id   BIGINT UNSIGNED NOT NULL,
  post_id     BIGINT UNSIGNED NOT NULL,
  parent_id   BIGINT UNSIGNED DEFAULT NULL,
  reply_to_id BIGINT UNSIGNED DEFAULT NULL,
  author_id   BIGINT UNSIGNED NOT NULL,
  content     TEXT NOT NULL,
  like_count  INT NOT NULL DEFAULT 0,
  status      TINYINT NOT NULL DEFAULT 1,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT NULL COMMENT '鏈€鍚庣紪杈戞椂闂?,
  deleted     TINYINT NOT NULL DEFAULT 0,
  KEY idx_post (post_id, created_at),
  KEY idx_author (author_id)
) ENGINE=InnoDB COMMENT='璇勮';

-- ============================================================
-- 7. reactions 鐐硅禐/鏀惰棌
-- ============================================================
CREATE TABLE IF NOT EXISTS reactions (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id   BIGINT UNSIGNED NOT NULL,
  user_id     BIGINT UNSIGNED NOT NULL,
  target_type VARCHAR(16) NOT NULL COMMENT 'POST/COMMENT/RESOURCE',
  target_id   BIGINT UNSIGNED NOT NULL,
  type        VARCHAR(16) NOT NULL COMMENT 'LIKE/COLLECT',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_target (user_id, target_type, target_id, type),
  KEY idx_target (target_type, target_id)
) ENGINE=InnoDB COMMENT='鐐硅禐鏀惰棌';

-- ============================================================
-- 8. qa_questions 闂瓟鎵╁睍
-- ============================================================
CREATE TABLE IF NOT EXISTS qa_questions (
  id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id       BIGINT UNSIGNED NOT NULL,
  post_id         BIGINT UNSIGNED NOT NULL,
  is_solved       TINYINT NOT NULL DEFAULT 0,
  accepted_comment_id BIGINT UNSIGNED DEFAULT NULL,
  solved_at       DATETIME DEFAULT NULL,
  UNIQUE KEY uk_post (post_id)
) ENGINE=InnoDB COMMENT='闂瓟鎵╁睍';

-- ============================================================
-- 9. checkin_challenges 鎵撳崱鎸戞垬
-- ============================================================
CREATE TABLE IF NOT EXISTS checkin_challenges (
  id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id     BIGINT UNSIGNED NOT NULL,
  space_id      BIGINT UNSIGNED DEFAULT NULL,
  creator_id    BIGINT UNSIGNED NOT NULL,
  name          VARCHAR(64) NOT NULL,
  description   VARCHAR(500) DEFAULT NULL,
  start_date    DATE NOT NULL,
  end_date      DATE NOT NULL,
  rule          JSON DEFAULT NULL,
  member_count  INT NOT NULL DEFAULT 0,
  status        TINYINT NOT NULL DEFAULT 1,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='鎵撳崱鎸戞垬';

-- ============================================================
-- 10. checkin_records 鎵撳崱璁板綍
-- ============================================================
CREATE TABLE IF NOT EXISTS checkin_records (
  id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id     BIGINT UNSIGNED NOT NULL,
  challenge_id  BIGINT UNSIGNED NOT NULL,
  user_id       BIGINT UNSIGNED NOT NULL,
  checkin_date  DATE NOT NULL,
  content       TEXT DEFAULT NULL,
  image_urls    JSON DEFAULT NULL,
  ai_check      TINYINT DEFAULT 0 COMMENT 'AI 鍐呭鍚堣鏍￠獙',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_date (challenge_id, user_id, checkin_date),
  KEY idx_user (user_id, checkin_date)
) ENGINE=InnoDB COMMENT='鎵撳崱璁板綍';

-- ============================================================
-- 11. resources 璧勬簮
-- ============================================================
CREATE TABLE IF NOT EXISTS resources (
  id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id     BIGINT UNSIGNED NOT NULL,
  uploader_id   BIGINT UNSIGNED NOT NULL,
  space_id      BIGINT UNSIGNED DEFAULT NULL,
  file_name     VARCHAR(255) NOT NULL,
  file_size     BIGINT UNSIGNED NOT NULL,
  file_type     VARCHAR(32)  NOT NULL,
  file_md5      VARCHAR(64)  DEFAULT NULL,
  file_sha256   VARCHAR(64)  DEFAULT NULL COMMENT 'SHA-256 hex 鎸囩汗',
  storage_key   VARCHAR(255) NOT NULL,
  visibility    VARCHAR(16)  NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC/SPACE/PRIVATE',
  college       VARCHAR(64)  DEFAULT NULL,
  major         VARCHAR(64)  DEFAULT NULL,
  course        VARCHAR(128) DEFAULT NULL,
  semester      VARCHAR(16)  DEFAULT NULL,
  tags          JSON DEFAULT NULL,
  download_count INT NOT NULL DEFAULT 0,
  collect_count  INT NOT NULL DEFAULT 0,
  version       VARCHAR(32)  DEFAULT NULL,
  description   TEXT DEFAULT NULL,
  status        TINYINT NOT NULL DEFAULT 1,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT NOT NULL DEFAULT 0,
  KEY idx_tenant (tenant_id),
  KEY idx_uploader (uploader_id),
  KEY idx_space (space_id),
  KEY idx_md5 (file_md5),
  KEY idx_resources_file_sha256 (file_sha256)
) ENGINE=InnoDB COMMENT='璧勬簮';

-- ============================================================
-- 12. notifications 閫氱煡
-- ============================================================
CREATE TABLE IF NOT EXISTS notifications (
  id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id    BIGINT UNSIGNED NOT NULL,
  receiver_id  BIGINT UNSIGNED NOT NULL,
  sender_id    BIGINT UNSIGNED DEFAULT NULL,
  type         VARCHAR(32) NOT NULL COMMENT 'COMMENT/LIKE/REPLY/MENTION/ACCEPT/JOIN/SYSTEM',
  title        VARCHAR(128) NOT NULL,
  content      TEXT DEFAULT NULL,
  redirect_url VARCHAR(255) DEFAULT NULL,
  is_read      TINYINT NOT NULL DEFAULT 0,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_receiver_read_time (receiver_id, is_read, created_at)
) ENGINE=InnoDB COMMENT='閫氱煡';

-- ============================================================
-- 13. messages 绉佷俊
-- ============================================================
CREATE TABLE IF NOT EXISTS messages (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id   BIGINT UNSIGNED NOT NULL,
  sender_id   BIGINT UNSIGNED NOT NULL,
  receiver_id BIGINT UNSIGNED NOT NULL,
  content     TEXT DEFAULT NULL,
  image_url   VARCHAR(255) DEFAULT NULL,
  is_read     TINYINT NOT NULL DEFAULT 0,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_conversation (sender_id, receiver_id, created_at),
  KEY idx_receiver_read (receiver_id, is_read, created_at)
) ENGINE=InnoDB COMMENT='绉佷俊';

-- ============================================================
-- 14. audit_logs 瀹¤鏃ュ織
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_logs (
  id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id    BIGINT UNSIGNED NOT NULL,
  operator_id  BIGINT UNSIGNED DEFAULT NULL,
  action       VARCHAR(64)  NOT NULL COMMENT '鎿嶄綔绫诲瀷',
  target_type  VARCHAR(32)  DEFAULT NULL,
  target_id    BIGINT UNSIGNED DEFAULT NULL,
  detail       JSON DEFAULT NULL COMMENT '鎿嶄綔璇︽儏',
  ip_address   VARCHAR(64)  DEFAULT NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tenant_time (tenant_id, created_at),
  KEY idx_operator (operator_id)
) ENGINE=InnoDB COMMENT='瀹¤鏃ュ織';

-- ============================================================
-- 15. reports 涓炬姤
-- ============================================================
CREATE TABLE IF NOT EXISTS reports (
  id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id    BIGINT UNSIGNED NOT NULL,
  reporter_id  BIGINT UNSIGNED NOT NULL,
  target_type  VARCHAR(16) NOT NULL COMMENT 'POST/COMMENT/RESOURCE/USER',
  target_id    BIGINT UNSIGNED NOT NULL,
  reason       VARCHAR(32)  NOT NULL,
  description  TEXT DEFAULT NULL,
  status       TINYINT NOT NULL DEFAULT 0 COMMENT '0寰呭鐞?1宸插鐞?2宸查┏鍥?,
  handler_id   BIGINT UNSIGNED DEFAULT NULL,
  handle_note  TEXT DEFAULT NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handled_at   DATETIME DEFAULT NULL,
  KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB COMMENT='涓炬姤';

-- ============================================================
-- 16. sensitive_words 鏁忔劅璇?
-- ============================================================
CREATE TABLE IF NOT EXISTS sensitive_words (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id   BIGINT UNSIGNED NOT NULL,
  word        VARCHAR(64) NOT NULL,
  level       TINYINT NOT NULL DEFAULT 1 COMMENT '1浣?2涓?3楂?,
  is_regex    TINYINT NOT NULL DEFAULT 0 COMMENT '0鏅€氳瘝鏉?1姝ｅ垯琛ㄨ揪寮?,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_word (tenant_id, word)
) ENGINE=InnoDB COMMENT='鏁忔劅璇?;

-- ============================================================
-- 17. achievements 鎴愬氨
-- ============================================================
CREATE TABLE IF NOT EXISTS achievements (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(32)  NOT NULL COMMENT '鎴愬氨缂栫爜',
  name        VARCHAR(64)  NOT NULL,
  description VARCHAR(255) DEFAULT NULL,
  icon_url    VARCHAR(255) DEFAULT NULL,
  rule        JSON DEFAULT NULL COMMENT '瑙﹀彂瑙勫垯',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB COMMENT='鎴愬氨瀹氫箟';

CREATE TABLE IF NOT EXISTS user_achievements (
  id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id      BIGINT UNSIGNED NOT NULL,
  user_id        BIGINT UNSIGNED NOT NULL,
  achievement_id BIGINT UNSIGNED NOT NULL,
  awarded_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_achieve (user_id, achievement_id)
) ENGINE=InnoDB COMMENT='鐢ㄦ埛鎴愬氨';

-- ============================================================
-- 19. follows 鐢ㄦ埛鍏虫敞
-- ============================================================
CREATE TABLE IF NOT EXISTS follows (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id   BIGINT UNSIGNED NOT NULL,
  follower_id BIGINT UNSIGNED NOT NULL COMMENT '鍏虫敞鑰?,
  followee_id BIGINT UNSIGNED NOT NULL COMMENT '琚叧娉ㄨ€?,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_follow (follower_id, followee_id),
  KEY idx_followee (followee_id)
) ENGINE=InnoDB COMMENT='鐢ㄦ埛鍏虫敞';

-- ============================================================
-- 20. post_ai_cards 甯栧瓙 AI 鏅鸿兘鍗＄墖缂撳瓨
-- ============================================================
CREATE TABLE IF NOT EXISTS post_ai_cards (
  id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  tenant_id               BIGINT UNSIGNED NOT NULL,
  post_id                 BIGINT UNSIGNED NOT NULL COMMENT '瀵瑰簲甯栧瓙ID',
  tldr                    TEXT DEFAULT NULL COMMENT 'TL;DR',
  audience                VARCHAR(255) DEFAULT NULL COMMENT '閫傚悎璋佽',
  value_type              VARCHAR(255) DEFAULT NULL COMMENT '浠峰€肩被鍨?,
  read_minutes            INT DEFAULT NULL COMMENT '棰勮闃呰鏃堕暱',
  comment_consensus       TEXT DEFAULT NULL COMMENT '璇勮鍏辫瘑',
  comment_disputes        TEXT DEFAULT NULL COMMENT '璇勮浜夎',
  hot_comment_id          BIGINT UNSIGNED DEFAULT NULL COMMENT '鏈€鐑瘎璁篒D',
  hot_comment_excerpt     VARCHAR(255) DEFAULT NULL COMMENT '鏈€鐑瘎璁烘埅鍙?,
  highlights              TEXT DEFAULT NULL COMMENT 'AI閲嶇偣楂樹寒 JSON 鏁扮粍',
  post_version            BIGINT UNSIGNED DEFAULT NULL COMMENT '甯栧瓙鏇存柊鏃堕棿鎴?,
  comment_count_snapshot  INT DEFAULT NULL COMMENT '鐢熸垚鏃惰瘎璁烘暟',
  created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted                 TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_post (post_id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB COMMENT='甯栧瓙 AI 鏅鸿兘鍗＄墖缂撳瓨';

-- ============================================================
-- 鍒濆鏁版嵁锛氶粯璁ょ鎴凤紙standalone 妯″紡蹇呴渶锛?
-- ============================================================
INSERT INTO tenants (id, code, name, status, created_at, updated_at)
VALUES (1, 'default', '榛樿绉熸埛', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE status = 1;
