-- 朋友圈插件 Supabase 数据库表结构
-- 在 Supabase SQL Editor 中执行以下 SQL

-- 启用 UUID 扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 用户表
CREATE TABLE IF NOT EXISTS moment_users (
  id TEXT PRIMARY KEY,
  nickname TEXT NOT NULL DEFAULT '匿名用户',
  avatar_url TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 动态表
CREATE TABLE IF NOT EXISTS moments (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  user_id TEXT NOT NULL REFERENCES moment_users(id) ON DELETE CASCADE,
  content TEXT NOT NULL DEFAULT '',
  location TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 动态图片表
CREATE TABLE IF NOT EXISTS moment_images (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  moment_id UUID NOT NULL REFERENCES moments(id) ON DELETE CASCADE,
  image_url TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0
);

-- 点赞表
CREATE TABLE IF NOT EXISTS moment_likes (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  moment_id UUID NOT NULL REFERENCES moments(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES moment_users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(moment_id, user_id)
);

-- 评论表
CREATE TABLE IF NOT EXISTS moment_comments (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  moment_id UUID NOT NULL REFERENCES moments(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES moment_users(id) ON DELETE CASCADE,
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 开启 RLS
ALTER TABLE moment_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE moments ENABLE ROW LEVEL SECURITY;
ALTER TABLE moment_images ENABLE ROW LEVEL SECURITY;
ALTER TABLE moment_likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE moment_comments ENABLE ROW LEVEL SECURITY;

-- 允许 anon 用户读写（anon key 即可访问）
CREATE POLICY "Allow all operations for anon" ON moment_users FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for anon" ON moments FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for anon" ON moment_images FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for anon" ON moment_likes FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for anon" ON moment_comments FOR ALL USING (true) WITH CHECK (true);