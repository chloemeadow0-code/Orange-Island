-- ============================================================================
-- Orange Island (橘子岛) — Quiz / Review Gate schema for Supabase
-- ----------------------------------------------------------------------------
-- Run in Supabase Studio → SQL Editor → New query → Run. Idempotent.
--
-- Adds:
--   - public.quiz_questions      (question bank)
--   - public.quiz_submissions    (submitted answers, unique by qq_number)
--   - RPCs:
--       * get_active_questions(p_limit int)
--       * check_qq_submitted(p_qq_number text)
--       * submit_quiz(p_qq_number text, p_answers jsonb)
-- ============================================================================

-- ── 1. quiz_questions ───────────────────────────────────────────────────────
create table if not exists public.quiz_questions (
    id             uuid primary key default gen_random_uuid(),
    question       text not null,
    options        jsonb not null,
    correct_answer text not null,
    explanation    text,
    order_index    int not null default 0,
    is_active      boolean not null default true,
    created_at     timestamptz not null default now()
);

comment on table public.quiz_questions is '审核答题题库';
comment on column public.quiz_questions.options is '选项数组，格式 [{"label":"A","text":"..."}, ...]';

-- ── 2. quiz_submissions ─────────────────────────────────────────────────────
create table if not exists public.quiz_submissions (
    id            uuid primary key default gen_random_uuid(),
    qq_number     text not null,
    answers       jsonb not null,
    score         int not null,
    passed        boolean not null,
    submitted_at  timestamptz not null default now(),
    unique (qq_number)
);

comment on table public.quiz_submissions is '审核答题提交记录，一个 QQ 号只能提交一次';
comment on column public.quiz_submissions.answers is '格式 {"question_id": "A", ...}';

-- ── 3. RLS ───────────────────────────────────────────────────────────────────
alter table public.quiz_questions    enable row level security;
alter table public.quiz_submissions  enable row level security;

-- Anonymous users can only read active questions (correct_answer hidden via RPC/view below).
drop policy if exists "quiz_questions_anon_select_active" on public.quiz_questions;
create policy "quiz_questions_anon_select_active"
    on public.quiz_questions for select
    to anon
    using (is_active = true);

-- Anonymous users can insert submissions (the RPC handles validation and scoring).
drop policy if exists "quiz_submissions_anon_insert" on public.quiz_submissions;
create policy "quiz_submissions_anon_insert"
    on public.quiz_submissions for insert
    to anon
    with check (true);

-- Submissions are never readable by anonymous users.
drop policy if exists "quiz_submissions_no_select" on public.quiz_submissions;
create policy "quiz_submissions_no_select"
    on public.quiz_submissions for select
    to anon
    using (false);

-- ── 4. Type used to expose questions without leaking correct_answer ──────────
drop type if exists public.question_view cascade;
create type public.question_view as (
    id        uuid,
    question  text,
    options   jsonb
);

-- ── 5. RPC: get random active questions ──────────────────────────────────────
-- Returns a random subset of active questions. Does NOT include correct_answer.
create or replace function public.get_active_questions(p_limit int default 25)
returns setof public.question_view
language sql
security definer
set search_path = public
as $body$
    select id, question, options
    from public.quiz_questions
    where is_active = true
    order by random(), order_index
    limit greatest(p_limit, 1);
$body$;

-- ── 6. RPC: check whether a QQ number has already submitted ─────────────────
create or replace function public.check_qq_submitted(p_qq_number text)
returns boolean
language sql
security definer
set search_path = public
as $body$
    select exists(
        select 1 from public.quiz_submissions
        where qq_number = trim(p_qq_number)
    );
$body$;

-- ── 7. RPC: submit quiz answers and score on the server ─────────────────────
-- p_answers format: {"question_id": "A", ...}
-- Returns a JSON object: {"success": true, "score": int, "total": int, "passed": bool}
create or replace function public.submit_quiz(
    p_qq_number      text,
    p_answers        jsonb,
    p_pass_threshold numeric default 1.0
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $body$
declare
    v_qq       text;
    v_score    int := 0;
    v_total    int := 0;
    v_passed   boolean;
    q_id       text;
    q_answer   text;
    v_correct  text;
    v_threshold numeric;
begin
    v_qq := trim(p_qq_number);
    v_threshold := greatest(least(coalesce(p_pass_threshold, 1.0), 1.0), 0.0);

    if v_qq is null or v_qq = '' then
        return jsonb_build_object('success', false, 'error', 'missing_qq_number');
    end if;

    -- Prevent duplicate submissions.
    if exists(select 1 from public.quiz_submissions where qq_number = v_qq) then
        return jsonb_build_object('success', false, 'error', 'already_submitted');
    end if;

    -- Score only the active questions present in the submission.
    for q_id, q_answer in select * from jsonb_each_text(p_answers)
    loop
        select correct_answer into v_correct
        from public.quiz_questions
        where id = q_id::uuid
          and is_active = true;

        if found then
            v_total := v_total + 1;
            if upper(trim(q_answer)) = upper(trim(v_correct)) then
                v_score := v_score + 1;
            end if;
        end if;
    end loop;

    if v_total = 0 then
        return jsonb_build_object('success', false, 'error', 'no_valid_questions');
    end if;

    v_passed := (v_score::numeric / v_total::numeric) >= v_threshold;

    insert into public.quiz_submissions (qq_number, answers, score, passed)
    values (v_qq, p_answers, v_score, v_passed);

    return jsonb_build_object(
        'success', true,
        'score', v_score,
        'total', v_total,
        'passed', v_passed
    );

exception
    when unique_violation then
        return jsonb_build_object('success', false, 'error', 'already_submitted');
    when others then
        return jsonb_build_object('success', false, 'error', sqlerrm);
end;
$body$;

-- ── 8. Admin helper: store a single admin token ──────────────────────────────
create table if not exists public.admin_settings (
    key   text primary key,
    value text not null
);

-- Seed a random admin token on first run. View it in Supabase Studio → admin_settings.
insert into public.admin_settings (key, value)
values ('admin_token', encode(gen_random_bytes(32), 'hex'))
on conflict (key) do nothing;

-- ── 9. RPC: read submissions (requires admin token) ─────────────────────────
create or replace function public.get_submissions(p_token text)
returns setof public.quiz_submissions
language sql
security definer
set search_path = public
as $body$
    select s.*
    from public.quiz_submissions s
    where exists (
        select 1 from public.admin_settings
        where key = 'admin_token' and value = p_token
    )
    order by s.submitted_at desc;
$body$;

-- ── 10. RPC: simple submission stats (requires admin token) ─────────────────
create or replace function public.get_submission_stats(p_token text)
returns jsonb
language sql
security definer
set search_path = public
as $body$
    select jsonb_build_object(
        'total', count(*),
        'passed', count(*) filter (where passed = true),
        'failed', count(*) filter (where passed = false)
    )
    from public.quiz_submissions
    where exists (
        select 1 from public.admin_settings
        where key = 'admin_token' and value = p_token
    );
$body$;
