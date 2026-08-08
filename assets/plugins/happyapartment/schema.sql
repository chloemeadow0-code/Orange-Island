-- ============================================================
-- 幸福公寓·AI 沙盒 - Supabase 建表 SQL
-- 在 Supabase Dashboard -> SQL Editor 里执行这段
-- 复用 AI小窝 同一 supabase 项目 (ogmlzwxwlbfmkdlafjrx)，只是独立一张表
-- ============================================================

-- 1. 建表
create table if not exists happy_apartment_data (
  home_id    text primary key,                          -- 用户名，存档隔离用
  data       jsonb not null default '{}'::jsonb,        -- 全部游戏数据塞一个JSON
  updated_at timestamptz not null default now()         -- 最后更新时间
);

-- 2. 自动更新 updated_at 的触发器 (AI小窝 已建过同名函数, create or replace 幂等)
create or replace function update_updated_at()
returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

drop trigger if exists trg_happy_apartment_updated on happy_apartment_data;
create trigger trg_happy_apartment_updated
  before update on happy_apartment_data
  for each row execute function update_updated_at();

-- 3. 开启 RLS（行级安全）
alter table happy_apartment_data enable row level security;

-- 4. RLS 策略：允许 anon 角色（publishable key）读写所有行
--    数据隔离靠 home_id(用户名)，不同实例用不同用户名，互不干扰
create policy "anon_select" on happy_apartment_data
  for select to anon, authenticated using (true);

create policy "anon_insert" on happy_apartment_data
  for insert to anon, authenticated with check (true);

create policy "anon_update" on happy_apartment_data
  for update to anon, authenticated using (true) with check (true);

create policy "anon_delete" on happy_apartment_data
  for delete to anon, authenticated using (true);

-- 5. 给 anon 角色授予表权限（RLS 开启后仍需显式授权）
grant select, insert, update, delete on happy_apartment_data to anon;
grant select, insert, update, delete on happy_apartment_data to authenticated;

-- ============================================================
-- 执行完成后，插件就可以正常初始化、存档、读档了
-- 验证：执行下面这句应该能查到空表（还没数据是正常的）
-- select * from happy_apartment_data;
-- ============================================================
