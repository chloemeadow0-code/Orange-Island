-- 提问箱插件 Supabase 数据库表结构
-- 在 Supabase SQL Editor 中执行以下 SQL
--
-- 设计：3 张主表 + 1 张用户表
--   qbox_users  : 用户表（昵称派生 id：user_昵称 / ai_昵称）
--   qboxes      : 提问箱（广场上展示的名称/简介/主人）
--   questions   : 提问（投递到某个箱，公开可见）
--   answers     : 回答（针对某条提问的公开回答）
--
-- 所有提问与回答都是公开的，RLS 对 anon 全开（anon key 即可访问）。

-- 启用 UUID 扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 用户表
CREATE TABLE IF NOT EXISTS qbox_users (
  id TEXT PRIMARY KEY,                       -- user_昵称 / ai_昵称
  nickname TEXT NOT NULL DEFAULT '匿名',
  role TEXT NOT NULL DEFAULT 'user',         -- user | ai
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 提问箱表
CREATE TABLE IF NOT EXISTS qboxes (
  id TEXT PRIMARY KEY,                       -- 用户自填的提问箱 ID（如 my_box_001）
  name TEXT NOT NULL,                        -- 提问箱名称
  description TEXT DEFAULT '',               -- 简介
  owner_id TEXT NOT NULL REFERENCES qbox_users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 提问表
CREATE TABLE IF NOT EXISTS questions (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  box_id TEXT NOT NULL REFERENCES qboxes(id) ON DELETE CASCADE,
  asker_id TEXT REFERENCES qbox_users(id) ON DELETE SET NULL,  -- 提问者（匿名时可空）
  asker_nickname TEXT DEFAULT '匿名',         -- 冗余存昵称，避免删用户后丢上下文
  content TEXT NOT NULL,
  is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 回答表
CREATE TABLE IF NOT EXISTS answers (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  question_id UUID NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
  box_id TEXT NOT NULL REFERENCES qboxes(id) ON DELETE CASCADE,  -- 冗余，便于按箱查询
  answerer_id TEXT REFERENCES qbox_users(id) ON DELETE SET NULL, -- 回答者
  answerer_nickname TEXT DEFAULT '匿名',
  content TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 开启 RLS
ALTER TABLE qbox_users  ENABLE ROW LEVEL SECURITY;
ALTER TABLE qboxes      ENABLE ROW LEVEL SECURITY;
ALTER TABLE questions   ENABLE ROW LEVEL SECURITY;
ALTER TABLE answers     ENABLE ROW LEVEL SECURITY;

-- 允许 anon 读写（anon key 即可访问）
CREATE POLICY "Allow all operations for anon" ON qbox_users FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for anon" ON qboxes     FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for anon" ON questions  FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all operations for anon" ON answers    FOR ALL USING (true) WITH CHECK (true);
