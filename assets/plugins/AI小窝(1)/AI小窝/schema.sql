-- ============================================================
-- AI小窝 - Supabase 建表 SQL
-- 在 Supabase Dashboard → SQL Editor 里执行这段
-- ============================================================

-- 1. 建表
create table if not exists ai_home_data (
  home_id    text primary key,                          -- 门牌号，数据隔离用
  data       jsonb not null default '{}'::jsonb,        -- 所有游戏数据塞一个JSON
  updated_at timestamptz not null default now()         -- 最后更新时间
);

-- 2. 自动更新 updated_at 的触发器
create or replace function update_updated_at()
returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

drop trigger if exists trg_ai_home_updated on ai_home_data;
create trigger trg_ai_home_updated
  before update on ai_home_data
  for each row execute function update_updated_at();

-- 3. 开启 RLS（行级安全）
alter table ai_home_data enable row level security;

-- 4. RLS 策略：允许 anon 角色（publishable key）读写所有行
--    数据隔离靠 home_id，不同插件实例用不同门牌号，互不干扰
create policy "anon_select" on ai_home_data
  for select to anon, authenticated using (true);

create policy "anon_insert" on ai_home_data
  for insert to anon, authenticated with check (true);

create policy "anon_update" on ai_home_data
  for update to anon, authenticated using (true) with check (true);

create policy "anon_delete" on ai_home_data
  for delete to anon, authenticated using (true);

-- 5. 给 anon 角色授予表权限（RLS 开启后仍需显式授权）
grant select, insert, update, delete on ai_home_data to anon;
grant select, insert, update, delete on ai_home_data to authenticated;

-- ============================================================
-- 执行完成后，插件就可以正常初始化、读写数据了
-- 验证：执行下面这句应该能查到空表（还没数据是正常的）
-- select * from ai_home_data;
-- ============================================================