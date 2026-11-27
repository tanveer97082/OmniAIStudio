-- ============================================================================
-- OmniAI-Studio v5.5 — Supabase PostgreSQL Compressed Text-Only Cloud DB
-- Google OAuth 2.0 PKCE + Server-Enforced Row Level Security (RLS) RBAC
-- ============================================================================
-- Ready for 1-paste execution inside Supabase Dashboard -> SQL Editor.
-- Zero binary files allowed: omni_sync_records stores GZIP+Base64 text capped
-- strictly at 256 KB (262,144 bytes) per row.
-- ============================================================================

-- 1. USER PROFILES & RBAC TABLE
create table if not exists public.user_profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    email text unique not null,
    display_name text,
    role text not null default 'PENDING' check (role in ('OWNER', 'EDITOR', 'VIEWER', 'PENDING')),
    is_approved boolean not null default false,
    last_device text,
    last_seen_at timestamptz default now(),
    created_at timestamptz default now()
);

create index if not exists idx_user_profiles_role on public.user_profiles(role);
create index if not exists idx_user_profiles_last_seen on public.user_profiles(last_seen_at desc);

-- 2. SECURITY DEFINER RBAC HELPER FUNCTIONS
-- Executed with search_path locked to public to prevent search_path hijacking.
create or replace function public.get_my_role()
returns text
language sql
stable
security definer
set search_path = public
as $$
    select coalesce(
        (select up.role from public.user_profiles up where up.id = auth.uid() limit 1),
        'PENDING'
    );
$$;

create or replace function public.is_approved_user()
returns booleanpython3 -c "with open('omniai-studio/supabase/schema_and_rls.sql') as f: text = f.read(); print('=== TABLES ==='); print('\n'.join(re.findall(r'create table[^\;]+\;', text, re.I))); print('=== FUNCTIONS ==='); print('\n---\n'.join(re.findall(r'create (?:or replace )?function[^\$]+\$\$.*?\$\$;', text, re.I | re.S))); print('=== TRIGGERS ==='); print('\n'.join(re.findall(r'create trigger[^\;]+\;', text, re.I))); print('=== POLICIES ==='); print('\n'.join(re.findall(r'create policy[^\;]+\;', text, re.I)))" > /tmp/parsed.txt
cat /tmp/parsed.txt

language sql
stablepython3 -c "with open('omniai-studio/supabase/schema_and_rls.sql') as f: text = f.read(); import re; print('=== TABLES ==='); [print(m.group(0)[:150]) for m in re.finditer(r'create table[^\(]+\([^\;]+\);', text, re.I)]; print('=== FUNCTIONS ==='); [print(fn.group(0)) for fn in re.finditer(r'create (?:or replace )?function [^\(]+\(', text, re.I)]; print('=== TRIGGERS ==='); [print(tr.group(0)) for tr in re.finditer(r'create trigger [^\n]+', text, re.I)]; print('=== POLICIES ==='); [print(p.group(0)) for p in re.finditer(r'create policy [^\n]+', text, re.I)]"
security definer
set search_path = public
as $$
    select coalesce(
        (select up.is_approved from public.user_profiles up where up.id = auth.uid() limit 1),
        false
    );
$$;

create or replace function public.ensure_user_profile()
returns setof public.user_profiles
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    caller_id uuid := auth.uid();
    has_owner boolean;
    auth_user auth.users%rowtype;
    resolved_role text;
    resolved_approved boolean;
begin
    if caller_id is null then
        raise exception 'Authentication required' using errcode = '28000';
    end if;

    if exists (select 1 from public.user_profiles where id = caller_id) then
        return query
        select up.*
        from public.user_profiles up
        where up.id = caller_id;
        return;
    end if;

    perform pg_advisory_xact_lock(78550192341);

    if not exists (select 1 from public.user_profiles where id = caller_id) then
        select u.* into auth_user
        from auth.users u
        where u.id = caller_id;

        if not found then
            raise exception 'Authenticated user not found' using errcode = '28000';
        end if;

        select exists (
            select 1 from public.user_profiles where role = 'OWNER'
        ) into has_owner;

        if has_owner then
            resolved_role := 'PENDING';
            resolved_approved := false;
        else
            resolved_role := 'OWNER';
            resolved_approved := true;
        end if;

        insert into public.user_profiles (
            id, email, display_name, role, is_approved, last_device, last_seen_at
        )
        values (
            auth_user.id,
            coalesce(auth_user.email, auth_user.id::text || '@unknown.local'),
            coalesce(
                nullif(auth_user.raw_user_meta_data ->> 'full_name', ''),
                nullif(auth_user.raw_user_meta_data ->> 'name', ''),
                split_part(coalesce(auth_user.email, 'user'), '@', 1)
            ),
            resolved_role,
            resolved_approved,
            null,
            now()
        )
        on conflict (id) do nothing;
    end if;

    return query
    select up.*
    from public.user_profiles up
    where up.id = caller_id;
end;
$$;

revoke all on function public.get_my_role() from public;
grant execute on function public.get_my_role() to authenticated;

revoke all on function public.is_approved_user() from public;
grant execute on function public.is_approved_user() to authenticated;

revoke all on function public.ensure_user_profile() from public;
grant execute on function public.ensure_user_profile() to authenticated;

-- 3. AUTOMATIC FIRST-USER OWNER BOOTSTRAP TRIGGER
-- First account ever registered becomes OWNER + is_approved = true.
-- Every subsequent signup defaults to PENDING + is_approved = false.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    owner_exists boolean;
    assigned_role text;
    assigned_approved boolean;
    resolved_name text;
begin
    -- Serialize first-owner election across concurrent signups
    perform pg_advisory_xact_lock(78550192341);

    select exists (
        select 1 from public.user_profiles where role = 'OWNER'
    ) into owner_exists;

    if not owner_exists then
        assigned_role := 'OWNER';
        assigned_approved := true;
    else
        assigned_role := 'PENDING';
        assigned_approved := false;
    end if;

    resolved_name := coalesce(
        nullif(new.raw_user_meta_data ->> 'full_name', ''),
        nullif(new.raw_user_meta_data ->> 'name', ''),
        split_part(coalesce(new.email, 'user'), '@', 1)
    );

    insert into public.user_profiles (
        id,
        email,
        display_name,
        role,
        is_approved,
        last_seen_at,
        created_at
    )
    values (
        new.id,
        coalesce(new.email, new.id::text || '@unknown.local'),
        resolved_name,
        assigned_role,
        assigned_approved,
        now(),
        now()
    )
    on conflict (id) do nothing;

    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row
    execute function public.handle_new_user();

-- 4. DEFENSE-IN-DEPTH TRIGGER PREVENTING NON-OWNER ROLE SELF-ESCALATION
create or replace function public.prevent_non_owner_role_escalation()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if new.role = 'OWNER' and old.role <> 'OWNER' then
        raise exception 'Permission denied: OWNER is assigned only by the first-owner bootstrap'
            using errcode = '42501';
    end if;

    if auth.uid() = old.id
       and (
           new.role is distinct from old.role
           or new.is_approved is distinct from old.is_approved
       ) then
        raise exception 'Permission denied: users cannot change their own role or approval status'
            using errcode = '42501';
    end if;

    if public.get_my_role() <> 'OWNER' or not public.is_approved_user() then
        if new.role is distinct from old.role
           or new.is_approved is distinct from old.is_approved
           or new.email is distinct from old.email
           or new.display_name is distinct from old.display_name
           or new.created_at is distinct from old.created_at
           or new.id is distinct from old.id then
            raise exception 'Permission denied: only an approved OWNER may modify profile identity, role, or approval'
                using errcode = '42501';
        end if;
    end if;

    return new;
end;
$$;

drop trigger if exists trg_prevent_role_escalation on public.user_profiles;
create trigger trg_prevent_role_escalation
    before update on public.user_profiles
    for each row
    execute function public.prevent_non_owner_role_escalation();

-- 5. STRICT COMPRESSED TEXT-ONLY DELTA SYNC TABLE (NO BINARY BLOBS)
create table if not exists public.omni_sync_records (
    record_type text not null check (
        record_type in (
            'CHAT_THREAD',
            'CHAT_MESSAGE',
            'VIDEO_SCRIPT',
            'SOCIAL_POST',
            'NICHE',
            'PROJECT',
            'PROJECT_TASK',
            'AI_MEMORY',
            'PROMPT_CHAIN',
            'HASHTAG_PACK',
            'VAULT_META'
        )
    ),
    record_id text not null,
    niche_id bigint default 1,
    title_preview text,
    compressed_payload text not null check (
        octet_length(compressed_payload) <= 262144
        and left(compressed_payload, 3) = 'gz:'
    ),
    is_deleted boolean not null default false,
    updated_by uuid references auth.users(id),
    updated_at bigint not null,
    primary key (record_type, record_id)
);

create index if not exists idx_omni_sync_updated_at on public.omni_sync_records(updated_at asc);
create index if not exists idx_omni_sync_niche_updated on public.omni_sync_records(niche_id, updated_at desc);
create index if not exists idx_omni_sync_type_updated on public.omni_sync_records(record_type, updated_at desc);

-- ============================================================================
-- 6. STRICT SERVER-SIDE ROW LEVEL SECURITY (RLS) POLICIES
-- ============================================================================

alter table public.user_profiles enable row level security;
alter table public.user_profiles force row level security;

alter table public.omni_sync_records enable row level security;
alter table public.omni_sync_records force row level security;

revoke all on public.user_profiles from anon, public, authenticated;
grant select, update, delete on public.user_profiles to authenticated;

revoke all on public.omni_sync_records from anon, public, authenticated;
grant select, insert, update, delete on public.omni_sync_records to authenticated;

-- Drop existing policies for idempotent re-execution
drop policy if exists "user_profiles_select_self_or_owner" on public.user_profiles;
drop policy if exists "user_profiles_update_owner_admin" on public.user_profiles;
drop policy if exists "user_profiles_update_self_device_only" on public.user_profiles;
drop policy if exists "user_profiles_delete_owner_only" on public.user_profiles;

drop policy if exists "omni_sync_vault_meta_owner_only" on public.omni_sync_records;
drop policy if exists "omni_sync_content_select_approved" on public.omni_sync_records;
drop policy if exists "omni_sync_content_insert_editor_or_owner" on public.omni_sync_records;
drop policy if exists "omni_sync_content_update_editor_or_owner" on public.omni_sync_records;
drop policy if exists "omni_sync_content_delete_owner_only" on public.omni_sync_records;

-- user_profiles policies
-- Users can read only their own profile; OWNER can read all profiles.
create policy "user_profiles_select_self_or_owner"
    on public.user_profiles
    for select
    to authenticated
    using (
        auth.uid() = id
        or (public.get_my_role() = 'OWNER' and public.is_approved_user())
    );

-- ONLY OWNER can update roles, approval flags, or revoke users.
create policy "user_profiles_update_owner_admin"
    on public.user_profiles
    for update
    to authenticated
    using (
        public.get_my_role() = 'OWNER'
        and public.is_approved_user()
    )
    with check (
        public.get_my_role() = 'OWNER'
        and public.is_approved_user()
    );

-- Authenticated user may update ONLY their own device telemetry (role & is_approved immutable via check + trigger).
create policy "user_profiles_update_self_device_only"
    on public.user_profiles
    for update
    to authenticated
    using (auth.uid() = id)
    with check (
        auth.uid() = id
        and public.is_approved_user()
        and role = public.get_my_role()
        and is_approved = public.is_approved_user()
    );

-- ONLY OWNER can delete another profile row (cannot self-delete owner lock).
create policy "user_profiles_delete_owner_only"
    on public.user_profiles
    for delete
    to authenticated
    using (
        public.get_my_role() = 'OWNER'
        and public.is_approved_user()
        and id <> auth.uid()
    );

-- omni_sync_records policies
-- VAULT_META rows: STRICTLY OWNER for SELECT / INSERT / UPDATE / DELETE.
create policy "omni_sync_vault_meta_owner_only"
    on public.omni_sync_records
    for all
    to authenticated
    using (
        record_type = 'VAULT_META'
        and public.get_my_role() = 'OWNER'
        and public.is_approved_user()
    )
    with check (
        record_type = 'VAULT_META'
        and public.get_my_role() = 'OWNER'
        and public.is_approved_user()
    );

-- All non-VAULT_META content types:
-- SELECT allowed for approved OWNER, EDITOR, and VIEWER.
create policy "omni_sync_content_select_approved"
    on public.omni_sync_records
    for select
    to authenticated
    using (
        record_type <> 'VAULT_META'
        and public.is_approved_user()
        and public.get_my_role() in ('OWNER', 'EDITOR', 'VIEWER')
    );

-- INSERT allowed ONLY for approved OWNER and EDITOR.
create policy "omni_sync_content_insert_editor_or_owner"
    on public.omni_sync_records
    for insert
    to authenticated
    with check (
        record_type <> 'VAULT_META'
        and public.is_approved_user()
        and public.get_my_role() in ('OWNER', 'EDITOR')
        and (updated_by is null or updated_by = auth.uid())
    );

-- UPDATE allowed ONLY for approved OWNER and EDITOR.
create policy "omni_sync_content_update_editor_or_owner"
    on public.omni_sync_records
    for update
    to authenticated
    using (
        record_type <> 'VAULT_META'
        and public.is_approved_user()
        and public.get_my_role() in ('OWNER', 'EDITOR')
    )
    with check (
        record_type <> 'VAULT_META'
        and public.is_approved_user()
        and public.get_my_role() in ('OWNER', 'EDITOR')
        and (updated_by is null or updated_by = auth.uid())
    );

-- Hard DELETE allowed ONLY for approved OWNER.
create policy "omni_sync_content_delete_owner_only"
    on public.omni_sync_records
    for delete
    to authenticated
    using (
        record_type <> 'VAULT_META'
        and public.is_approved_user()
        and public.get_my_role() = 'OWNER'
    );

-- ============================================================================
-- 7. PRIVATE PER-USER API CREDENTIALS (NEVER STORED IN omni_sync_records)
-- ============================================================================
create table if not exists public.user_api_credentials (
    user_id uuid primary key references auth.users(id) on delete cascade,
    credentials jsonb not null default '{}'::jsonb
        check (jsonb_typeof(credentials) = 'object'),
    updated_at bigint not null default ((extract(epoch from now()) * 1000)::bigint)
);

alter table public.user_api_credentials enable row level security;
alter table public.user_api_credentials force row level security;

revoke all on public.user_api_credentials from anon, public, authenticated;
grant select, insert, update, delete on public.user_api_credentials to authenticated;

drop policy if exists "user_api_credentials_select_self" on public.user_api_credentials;
drop policy if exists "user_api_credentials_insert_self" on public.user_api_credentials;
drop policy if exists "user_api_credentials_update_self" on public.user_api_credentials;
drop policy if exists "user_api_credentials_delete_self" on public.user_api_credentials;

create policy "user_api_credentials_select_self"
    on public.user_api_credentials for select to authenticated
    using (
        auth.uid() = user_id
        and public.is_approved_user()
        and public.get_my_role() = 'OWNER'
    );
create policy "user_api_credentials_insert_self"
    on public.user_api_credentials for insert to authenticated
    with check (
        auth.uid() = user_id
        and public.is_approved_user()
        and public.get_my_role() = 'OWNER'
    );
create policy "user_api_credentials_update_self"
    on public.user_api_credentials for update to authenticated
    using (
        auth.uid() = user_id
        and public.is_approved_user()
        and public.get_my_role() = 'OWNER'
    )
    with check (
        auth.uid() = user_id
        and public.is_approved_user()
        and public.get_my_role() = 'OWNER'
    );
create policy "user_api_credentials_delete_self"
    on public.user_api_credentials for delete to authenticated
    using (
        auth.uid() = user_id
        and public.is_approved_user()
        and public.get_my_role() = 'OWNER'
    );
