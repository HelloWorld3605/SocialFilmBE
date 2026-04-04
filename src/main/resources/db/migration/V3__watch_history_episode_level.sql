ALTER TABLE watch_history
    DROP INDEX uk_history_user_slug;

ALTER TABLE watch_history
    ADD COLUMN last_server_index INT NULL AFTER last_position_seconds,
    ADD COLUMN last_episode_index INT NULL AFTER last_server_index,
    ADD COLUMN duration_seconds INT NULL AFTER last_episode_index;

ALTER TABLE watch_history
    ADD CONSTRAINT uk_history_user_slug_episode UNIQUE (user_id, movie_slug, last_episode_index);
