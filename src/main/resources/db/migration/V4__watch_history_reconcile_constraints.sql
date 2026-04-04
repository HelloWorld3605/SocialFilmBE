SET @add_last_server_index = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'watch_history'
              AND column_name = 'last_server_index'
        ),
        'SELECT 1',
        'ALTER TABLE watch_history ADD COLUMN last_server_index INT NULL AFTER last_position_seconds'
    )
);
PREPARE stmt FROM @add_last_server_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_last_episode_index = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'watch_history'
              AND column_name = 'last_episode_index'
        ),
        'SELECT 1',
        'ALTER TABLE watch_history ADD COLUMN last_episode_index INT NULL AFTER last_server_index'
    )
);
PREPARE stmt FROM @add_last_episode_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_duration_seconds = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'watch_history'
              AND column_name = 'duration_seconds'
        ),
        'SELECT 1',
        'ALTER TABLE watch_history ADD COLUMN duration_seconds INT NULL AFTER last_episode_index'
    )
);
PREPARE stmt FROM @add_duration_seconds;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_legacy_history_unique = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'watch_history'
              AND index_name = 'uk_history_user_slug'
        ),
        'ALTER TABLE watch_history DROP INDEX uk_history_user_slug',
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_legacy_history_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_episode_history_unique = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'watch_history'
              AND index_name = 'uk_history_user_slug_episode'
        ),
        'SELECT 1',
        'ALTER TABLE watch_history ADD CONSTRAINT uk_history_user_slug_episode UNIQUE (user_id, movie_slug, last_episode_index)'
    )
);
PREPARE stmt FROM @add_episode_history_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
