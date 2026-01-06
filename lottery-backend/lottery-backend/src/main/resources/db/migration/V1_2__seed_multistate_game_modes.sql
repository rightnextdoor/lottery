
INSERT INTO rules (
  format_start_date,
  white_min, white_max, white_pick_count,
  red_min, red_max, red_pick_count
) VALUES (
  '2015-10-04',
  1, 69, 5,
  1, 26, 1
);
SET @pb_rules_id = LAST_INSERT_ID();

INSERT INTO game_mode (
  mode_key,
  display_name,
  rules_id,
  scope,
  jurisdiction_code,
  latest_draw_date,
  latest_white_winning_csv,
  latest_red_winning_csv,
  next_draw_date
) VALUES (
  'POWERBALL',
  'Powerball',
  @pb_rules_id,
  'MULTI_STATE',
  NULL,
  NULL,
  NULL,
  NULL,
  NULL
);
SET @pb_game_id = LAST_INSERT_ID();

INSERT INTO game_mode_draw_day (game_mode_id, draw_day) VALUES
  (@pb_game_id, 'MONDAY'),
  (@pb_game_id, 'WEDNESDAY'),
  (@pb_game_id, 'SATURDAY');

-- MEGA MILLIONS (current matrix started Apr 8, 2025)
INSERT INTO rules (
  format_start_date,
  white_min, white_max, white_pick_count,
  red_min, red_max, red_pick_count
) VALUES (
  '2025-04-08',
  1, 70, 5,
  1, 24, 1
);
SET @mm_rules_id = LAST_INSERT_ID();

INSERT INTO game_mode (
  mode_key,
  display_name,
  rules_id,
  scope,
  jurisdiction_code,
  latest_draw_date,
  latest_white_winning_csv,
  latest_red_winning_csv,
  next_draw_date
) VALUES (
  'MEGA_MILLIONS',
  'Mega Millions',
  @mm_rules_id,
  'MULTI_STATE',
  NULL,
  NULL,
  NULL,
  NULL,
  NULL
);
SET @mm_game_id = LAST_INSERT_ID();

INSERT INTO game_mode_draw_day (game_mode_id, draw_day) VALUES
  (@mm_game_id, 'TUESDAY'),
  (@mm_game_id, 'FRIDAY');

