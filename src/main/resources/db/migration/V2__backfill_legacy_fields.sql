-- Thêm cột trước, bổ sung giá trị cũ sau, cuối cùng mới đặt ràng buộc.
ALTER TABLE categories ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS cover_url TEXT;
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS role VARCHAR(255);
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN;
ALTER TABLE products ADD COLUMN IF NOT EXISTS cover_image TEXT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS summary TEXT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE products ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN;
ALTER TABLE volumes ADD COLUMN IF NOT EXISTS title VARCHAR(255);
ALTER TABLE volumes ADD COLUMN IF NOT EXISTS cover_image TEXT;
ALTER TABLE volumes ADD COLUMN IF NOT EXISTS summary TEXT;
ALTER TABLE volumes ADD COLUMN IF NOT EXISTS sort_order INTEGER;
ALTER TABLE volumes ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN;
ALTER TABLE chapters ADD COLUMN IF NOT EXISTS title VARCHAR(255);
ALTER TABLE chapters ADD COLUMN IF NOT EXISTS sort_order INTEGER;
ALTER TABLE chapters ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN;
ALTER TABLE chapters ADD COLUMN IF NOT EXISTS is_completed BOOLEAN;
ALTER TABLE chapter_images ADD COLUMN IF NOT EXISTS sort_order INTEGER;
ALTER TABLE image_cleanup_candidates ADD COLUMN IF NOT EXISTS retry_at TIMESTAMP WITH TIME ZONE;

-- Đồng bộ kiểu ENUM PostgreSQL cũ với cách Java đang lưu chuỗi.
ALTER TABLE products ALTER COLUMN status DROP DEFAULT;
ALTER TABLE products ALTER COLUMN status TYPE VARCHAR(255) USING status::TEXT;
ALTER TABLE ranking ALTER COLUMN sort_by DROP DEFAULT;
ALTER TABLE ranking ALTER COLUMN sort_by TYPE VARCHAR(255) USING sort_by::TEXT;

UPDATE categories SET is_deleted=FALSE WHERE is_deleted IS NULL;
UPDATE users SET is_deleted=FALSE WHERE is_deleted IS NULL;
UPDATE accounts SET is_deleted=FALSE WHERE is_deleted IS NULL;
UPDATE accounts SET role='USER' WHERE role IS NULL;
UPDATE products SET is_deleted=FALSE WHERE is_deleted IS NULL;
UPDATE products SET status='PRIVATE' WHERE status IS NULL;
UPDATE volumes SET is_deleted=FALSE WHERE is_deleted IS NULL;
UPDATE volumes SET sort_order=0 WHERE sort_order IS NULL;
UPDATE chapters SET is_deleted=FALSE WHERE is_deleted IS NULL;
UPDATE chapters SET sort_order=0 WHERE sort_order IS NULL;
UPDATE chapters SET is_completed=TRUE WHERE is_completed IS NULL;
UPDATE chapter_images SET sort_order=0 WHERE sort_order IS NULL;
UPDATE ranking SET sort_by='All' WHERE sort_by IS NULL;
UPDATE ranking SET total_points=0 WHERE total_points IS NULL;
UPDATE ranking SET total_cmts=0 WHERE total_cmts IS NULL;
UPDATE ranking SET total_views=0 WHERE total_views IS NULL;
UPDATE ranking SET avg_vote=0 WHERE avg_vote IS NULL;
UPDATE ranking SET total_words=0 WHERE total_words IS NULL;

ALTER TABLE categories ALTER COLUMN is_deleted SET DEFAULT FALSE, ALTER COLUMN is_deleted SET NOT NULL;
ALTER TABLE users ALTER COLUMN is_deleted SET DEFAULT FALSE, ALTER COLUMN is_deleted SET NOT NULL;
ALTER TABLE accounts ALTER COLUMN is_deleted SET DEFAULT FALSE, ALTER COLUMN is_deleted SET NOT NULL,
    ALTER COLUMN role SET DEFAULT 'USER', ALTER COLUMN role SET NOT NULL;
ALTER TABLE products ALTER COLUMN is_deleted SET DEFAULT FALSE, ALTER COLUMN is_deleted SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'PRIVATE', ALTER COLUMN status SET NOT NULL;
ALTER TABLE volumes ALTER COLUMN is_deleted SET DEFAULT FALSE, ALTER COLUMN is_deleted SET NOT NULL,
    ALTER COLUMN sort_order SET DEFAULT 0, ALTER COLUMN sort_order SET NOT NULL;
ALTER TABLE chapters ALTER COLUMN is_deleted SET DEFAULT FALSE, ALTER COLUMN is_deleted SET NOT NULL,
    ALTER COLUMN sort_order SET DEFAULT 0, ALTER COLUMN sort_order SET NOT NULL,
    ALTER COLUMN is_completed SET DEFAULT TRUE, ALTER COLUMN is_completed SET NOT NULL;
ALTER TABLE chapter_images ALTER COLUMN sort_order SET DEFAULT 0, ALTER COLUMN sort_order SET NOT NULL;
ALTER TABLE ranking ALTER COLUMN sort_by SET DEFAULT 'All', ALTER COLUMN sort_by SET NOT NULL,
    ALTER COLUMN total_points SET DEFAULT 0, ALTER COLUMN total_points SET NOT NULL,
    ALTER COLUMN total_cmts SET DEFAULT 0, ALTER COLUMN total_cmts SET NOT NULL,
    ALTER COLUMN total_views SET DEFAULT 0, ALTER COLUMN total_views SET NOT NULL,
    ALTER COLUMN avg_vote SET DEFAULT 0, ALTER COLUMN avg_vote SET NOT NULL,
    ALTER COLUMN total_words SET DEFAULT 0, ALTER COLUMN total_words SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_products_updated_at ON products(updated_at DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_chapters_volume_order ON chapters(volume_id,sort_order,chapter_id);
CREATE INDEX IF NOT EXISTS idx_volumes_product_order ON volumes(product_id,sort_order,volume_id);
-- Nếu dữ liệu cũ trùng/thiếu tên hoặc thư điện tử, dừng để xử lý rõ ràng; không tự xóa tài khoản.
ALTER TABLE accounts ALTER COLUMN username SET NOT NULL, ALTER COLUMN email SET NOT NULL,
    ALTER COLUMN password SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_accounts_username_lower ON accounts(LOWER(username));
CREATE UNIQUE INDEX IF NOT EXISTS uq_accounts_email_lower ON accounts(LOWER(email));
