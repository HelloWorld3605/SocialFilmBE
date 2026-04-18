ALTER TABLE auth_page_images
    ADD COLUMN focal_point_x INT NOT NULL DEFAULT 50,
    ADD COLUMN focal_point_y INT NOT NULL DEFAULT 50;
