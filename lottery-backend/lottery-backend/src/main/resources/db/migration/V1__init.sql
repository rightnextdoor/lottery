-- V1__init.sql
-- MySQL / MariaDB
-- Creates core schema used by Flyway seed migrations:
--   V1_1__seed_jurisdictions.sql
--   V1_2__seed_multistate_game_modes.sql
--
-- Also includes batch/ticket tables from your domain model.

-- ----------------------------
-- JURISDICTIONS
-- ----------------------------
CREATE TABLE `jurisdiction` (
  `code` VARCHAR(2) NOT NULL,
  `name` VARCHAR(80) NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`code`),
  UNIQUE KEY `uk_jurisdiction_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- RULES
-- ----------------------------
CREATE TABLE `rules` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `format_start_date` DATE NULL,
  `white_min` INT NOT NULL,
  `white_max` INT NOT NULL,
  `white_pick_count` INT NOT NULL,
  `red_min` INT NULL,
  `red_max` INT NULL,
  `red_pick_count` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  CHECK (`white_min` <= `white_max`),
  CHECK (`white_pick_count` > 0),
  CHECK (`red_pick_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- GAME MODE
-- ----------------------------
CREATE TABLE `game_mode` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `mode_key` VARCHAR(60) NOT NULL,
  `display_name` VARCHAR(80) NOT NULL,

  -- NOTE: we’re allowing rules to be nullable (per your latest decision).
  `rules_id` BIGINT NULL,

  `scope` VARCHAR(15) NOT NULL, -- MULTI_STATE | STATE_ONLY
  `jurisdiction_code` VARCHAR(2) NULL,

  `latest_draw_date` DATE NULL,
  `latest_white_winning_csv` VARCHAR(200) NULL,
  `latest_red_winning_csv` VARCHAR(80) NULL,
  `next_draw_date` DATE NULL,

  PRIMARY KEY (`id`),
  CONSTRAINT `uk_game_mode_key` UNIQUE (`mode_key`),

  KEY `ix_game_mode_scope` (`scope`),
  KEY `ix_game_mode_jurisdiction` (`jurisdiction_code`),
  KEY `ix_game_mode_rules` (`rules_id`),

  CONSTRAINT `fk_game_mode_rules`
    FOREIGN KEY (`rules_id`) REFERENCES `rules` (`id`)
    ON DELETE SET NULL
    ON UPDATE RESTRICT,

  CONSTRAINT `fk_game_mode_jurisdiction`
    FOREIGN KEY (`jurisdiction_code`) REFERENCES `jurisdiction` (`code`)
    ON DELETE SET NULL
    ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- GAME MODE DRAW DAYS (element collection)
-- ----------------------------
CREATE TABLE `game_mode_draw_day` (
  `game_mode_id` BIGINT NOT NULL,
  `draw_day` VARCHAR(10) NOT NULL, -- MONDAY..SUNDAY
  PRIMARY KEY (`game_mode_id`, `draw_day`),
  CONSTRAINT `fk_game_mode_draw_day_game_mode`
    FOREIGN KEY (`game_mode_id`) REFERENCES `game_mode` (`id`)
    ON DELETE CASCADE
    ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- SAVED BATCH
-- ----------------------------
CREATE TABLE `saved_batch` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `game_mode_id` BIGINT NOT NULL,
  `name` VARCHAR(120) NOT NULL,
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `ix_saved_batch_game_mode` (`game_mode_id`),
  CONSTRAINT `fk_saved_batch_game_mode`
    FOREIGN KEY (`game_mode_id`) REFERENCES `game_mode` (`id`)
    ON DELETE RESTRICT
    ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- TICKET
-- ----------------------------
CREATE TABLE `ticket` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `saved_batch_id` BIGINT NOT NULL,
  `ticket_index` INT NOT NULL,
  `strategy_group` VARCHAR(20) NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_ticket_batch_index` UNIQUE (`saved_batch_id`, `ticket_index`),
  KEY `ix_ticket_saved_batch` (`saved_batch_id`),
  CONSTRAINT `fk_ticket_saved_batch`
    FOREIGN KEY (`saved_batch_id`) REFERENCES `saved_batch` (`id`)
    ON DELETE CASCADE
    ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- TICKET PICK
-- ----------------------------
CREATE TABLE `ticket_pick` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `ticket_id` BIGINT NOT NULL,
  `pool_type` VARCHAR(10) NOT NULL, -- ex: WHITE | RED (based on your PoolType enum)
  `position` INT NOT NULL,
  `number_value` INT NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_pick_ticket_pool_position` UNIQUE (`ticket_id`, `pool_type`, `position`),
  KEY `ix_pick_ticket` (`ticket_id`),
  CONSTRAINT `fk_ticket_pick_ticket`
    FOREIGN KEY (`ticket_id`) REFERENCES `ticket` (`id`)
    ON DELETE CASCADE
    ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- OPTIONAL: NUMBER BALL (from planning notes)
-- Keep this if you want number-tiering stored in DB now.
-- ----------------------------
CREATE TABLE `number_ball` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `game_mode_id` BIGINT NOT NULL,
  `pool_type` VARCHAR(10) NOT NULL,      -- WHITE | RED
  `number_value` INT NOT NULL,

  `last_drawn_date` DATE NULL,

  `tier` VARCHAR(10) NOT NULL,           -- HOT | MID | COLD (or whatever your enum becomes)
  `status_change` VARCHAR(12) NULL,      -- PROMOTED | DEMOTED | NONE

  `total_count` INT NOT NULL DEFAULT 0,
  `tier_count` INT NOT NULL DEFAULT 0,

  PRIMARY KEY (`id`),
  CONSTRAINT `uk_number_ball_mode_pool_value` UNIQUE (`game_mode_id`, `pool_type`, `number_value`),
  KEY `ix_number_ball_game_mode` (`game_mode_id`),
  KEY `ix_number_ball_tier` (`tier`),
  CONSTRAINT `fk_number_ball_game_mode`
    FOREIGN KEY (`game_mode_id`) REFERENCES `game_mode` (`id`)
    ON DELETE CASCADE
    ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- DRAW RESULT
-- ----------------------------
CREATE TABLE `draw_result` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `game_mode_id` BIGINT NOT NULL,
  `draw_date` DATE NOT NULL,
  `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `source_name` VARCHAR(80) NULL,
  `source_ref` VARCHAR(300) NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_draw_game_date` UNIQUE (`game_mode_id`, `draw_date`),
  KEY `ix_draw_game_mode` (`game_mode_id`),
  KEY `ix_draw_date` (`draw_date`),
  CONSTRAINT `fk_draw_result_game_mode`
    FOREIGN KEY (`game_mode_id`) REFERENCES `game_mode` (`id`)
    ON DELETE CASCADE
    ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ----------------------------
-- DRAW PICK
-- ----------------------------
CREATE TABLE `draw_pick` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `draw_result_id` BIGINT NOT NULL,
  `pool_type` VARCHAR(10) NOT NULL,
  `position` INT NOT NULL,
  `number_value` INT NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_pick_draw_pool_position` UNIQUE (`draw_result_id`, `pool_type`, `position`),
  KEY `ix_draw_pick_draw` (`draw_result_id`),
  CONSTRAINT `fk_draw_pick_draw_result`
    FOREIGN KEY (`draw_result_id`) REFERENCES `draw_result` (`id`)
    ON DELETE CASCADE
    ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
