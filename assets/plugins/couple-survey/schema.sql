-- ============================================================
-- 情侣问卷·AI 插件 - Supabase 建表 SQL
-- 在 Supabase Dashboard -> SQL Editor 里执行这段（只需一次）
-- 使用 Supabase 项目 nvkcztwjlbszvwkvbetf 的实例 (URL/KEY 已在 main.js 与 ui.html 硬编码)
-- ============================================================

-- 1. 问卷主表
create table if not exists couple_surveys (
  id            text primary key,                 -- 问卷ID (插件端生成的随机串)
  owner_id      text not null,                    -- 发布者专属ID
  owner_name    text not null,                    -- 发布者用户名
  title         text not null,                    -- 问卷标题
  description   text default '',                  -- 问卷说明
  questions     jsonb not null default '[]'::jsonb, -- 问题数组 [{id,type,title,options?}]
  visibility    text not null default 'draft',    -- draft(草稿,仅自己)|public(公开)|private(私密)
  target_user   text default '',                  -- 私密时授权可见的对方ID/用户名
  created_at    timestamptz not null default now()
);

-- 2. 问卷回答表
create table if not exists couple_survey_responses (
  id              text primary key,               -- 回答ID
  survey_id       text not null,                  -- 所属问卷ID
  respondent_id   text not null,                  -- 填写者专属ID
  respondent_name text not null,                  -- 填写者用户名
  answers         jsonb not null default '{}'::jsonb, -- {questionId: answerValue}
  is_submitted    boolean not null default false, -- false=草稿, true=已提交
  created_at      timestamptz not null default now()
);

-- 3. 转发记录表
create table if not exists couple_survey_forwards (
  id          text primary key,
  survey_id   text not null,
  from_id     text not null,                      -- 转发人专属ID
  from_name   text not null,                      -- 转发人用户名
  to_target   text not null default '',           -- 转发给 (用户名/ID/会话标识)
  created_at  timestamptz not null default now()
);

-- 4. 自动更新时间触发器 (update_updated_at 在 AI小窝 已建过, create or replace 幂等)
create or replace function update_updated_at()
returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

-- 5. 开启 RLS
alter table couple_surveys enable row level security;
alter table couple_survey_responses enable row level security;
alter table couple_survey_forwards enable row level security;

-- 6. RLS 策略: 允许 anon 角色 (publishable key) 读写。数据隔离靠 owner_id / visibility 字段
create policy "anon_select_surveys" on couple_surveys
  for select to anon, authenticated using (true);
create policy "anon_insert_surveys" on couple_surveys
  for insert to anon, authenticated with check (true);
create policy "anon_update_surveys" on couple_surveys
  for update to anon, authenticated using (true) with check (true);
create policy "anon_delete_surveys" on couple_surveys
  for delete to anon, authenticated using (true);

create policy "anon_select_resp" on couple_survey_responses
  for select to anon, authenticated using (true);
create policy "anon_insert_resp" on couple_survey_responses
  for insert to anon, authenticated with check (true);
create policy "anon_update_resp" on couple_survey_responses
  for update to anon, authenticated using (true) with check (true);
create policy "anon_delete_resp" on couple_survey_responses
  for delete to anon, authenticated using (true);

create policy "anon_select_fwd" on couple_survey_forwards
  for select to anon, authenticated using (true);
create policy "anon_insert_fwd" on couple_survey_forwards
  for insert to anon, authenticated with check (true);
create policy "anon_delete_fwd" on couple_survey_forwards
  for delete to anon, authenticated using (true);

-- 7. 授权
grant select, insert, update, delete on couple_surveys to anon;
grant select, insert, update, delete on couple_surveys to authenticated;
grant select, insert, update, delete on couple_survey_responses to anon;
grant select, insert, update, delete on couple_survey_responses to authenticated;
grant select, insert, update, delete on couple_survey_forwards to anon;
grant select, insert, update, delete on couple_survey_forwards to authenticated;

-- ============================================================
-- 执行完成后插件即可使用。验证（应返回空表，正常）：
-- select * from couple_surveys;
-- ============================================================
