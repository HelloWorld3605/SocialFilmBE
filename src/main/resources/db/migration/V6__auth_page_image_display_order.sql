ALTER TABLE auth_page_images
    ADD COLUMN display_order INT NOT NULL DEFAULT 1 AFTER description;

SET @auth_image_display_order := 0;

UPDATE auth_page_images
SET display_order = (@auth_image_display_order := @auth_image_display_order + 1)
ORDER BY created_at DESC, id DESC;
