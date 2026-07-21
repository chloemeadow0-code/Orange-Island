-- ============================================================================
-- Orange Island (橘子岛) — Account & Invitation-Code schema for Supabase
-- ----------------------------------------------------------------------------
-- Run in Supabase Studio → SQL Editor → New query → Run. Idempotent.
--
-- TIP: If you copy from chat, the $$ tags may get mangled. Open this file
-- directly in VS Code and copy from there. The "$body$" tag is used because
-- some clipboards collapse the conventional "$$".
-- ============================================================================

-- ── 1. profiles ──────────────────────────────────────────────────────────────
create table if not exists public.profiles (
    id         uuid primary key references auth.users(id) on delete cascade,
    username   text unique not null,
    email      text not null,
    created_at timestamptz not null default now()
);

-- ── 2. invitation_codes ───────────────────────────────────────────────────────
create table if not exists public.invitation_codes (
    code       text primary key,
    max_uses   int not null default 1 check (max_uses >= 0),
    used_count int not null default 0 check (used_count >= 0),
    is_active  boolean not null default true,
    expires_at timestamptz,
    note       text,
    created_at timestamptz not null default now()
);

-- ── 3. RLS ───────────────────────────────────────────────────────────────────
alter table public.profiles        enable row level security;
alter table public.invitation_codes enable row level security;

drop policy if exists "profiles_public_read" on public.profiles;
create policy "profiles_public_read"
    on public.profiles for select
    using (true);

-- invitation_codes: no client policies → only service_role + security-definer RPCs.

-- ── 4. Bulk-generate codes (backoffice helper) ───────────────────────────────
--   select * from public.generate_invitation_codes(10, 1, now() + interval '30 days', 'beta');
create or replace function public.generate_invitation_codes(
    p_count      int,
    p_max_uses   int default 1,
    p_expires_at timestamptz default null,
    p_note       text default null
) returns setof text
language plpgsql
security definer
set search_path = public
as $body$
declare
    i int;
    c text;
begin
    for i in 1..greatest(p_count, 1) loop
        c := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
        insert into public.invitation_codes (code, max_uses, expires_at, note)
        values (c, greatest(p_max_uses, 1), p_expires_at, p_note);
        return next c;
    end loop;
end;
$body$;

-- ── 5. Pre-check: invite validity (non-consuming) ────────────────────────────
-- Pure SQL function — no PL/pgSQL variables, robust to dollar-quote mangling.
create or replace function public.check_invitation_code(p_code text)
returns boolean
language sql
security definer
set search_path = public
as $body$
    select exists(
        select 1 from public.invitation_codes
        where code = upper(trim(p_code))
          and is_active = true
          and (expires_at is null or expires_at >= now())
          and used_count < max_uses
    );
$body$;

-- ── 6. Atomic finalize: consume invite + write profile ───────────────────────
-- Uses UPDATE...WHERE for atomic consumption (no separate SELECT FOR UPDATE).
create or replace function public.complete_registration(
    p_username    text,
    p_invite_code text
) returns void
language plpgsql
security definer
set search_path = public
as $body$
declare
    v_uid   uuid := auth.uid();
    v_email text;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    -- Atomic consume: only matches a valid invite, and locks the row via UPDATE.
    update public.invitation_codes
        set used_count = used_count + 1
        where code = upper(trim(p_invite_code))
          and is_active = true
          and (expires_at is null or expires_at >= now())
          and used_count < max_uses;
    if not found then
        raise exception 'invalid_invitation_code';
    end if;

    select email into v_email from auth.users where id = v_uid;

    insert into public.profiles (id, username, email)
    values (v_uid, trim(p_username), v_email)
    on conflict (id) do update
        set username = excluded.username,
            email    = excluded.email;
end;
$body$;
