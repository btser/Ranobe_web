-- Chỉ đổi tên cột; giữ nguyên toàn bộ nội dung, kiểu dữ liệu và trạng thái cho phép NULL.
-- Chấp nhận cả cơ sở dữ liệu tạo từ bản SQL mới đã dùng tên content.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema=current_schema() AND table_name='chapters' AND column_name='context') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema=current_schema() AND table_name='chapters' AND column_name='content') THEN
            RAISE EXCEPTION 'chapters has both context and content; manual review is required';
        END IF;
        ALTER TABLE chapters RENAME COLUMN context TO content;
    ELSIF NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_schema=current_schema() AND table_name='chapters' AND column_name='content') THEN
        RAISE EXCEPTION 'chapters has neither context nor content';
    END IF;
END $$;
