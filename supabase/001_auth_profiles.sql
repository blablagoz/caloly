-- Caloly v0.5.0 - Auth/Profile base schema
-- Run in Supabase SQL Editor after creating the project.

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    username text unique,
    display_name text,
    avatar_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint username_format check (username is null or username ~ '^[a-z0-9._]{3,24}$')
);

alter table public.profiles enable row level security;

create policy "profiles are discoverable by signed in users"
on public.profiles for select to authenticated using (true);

create policy "users update own profile"
on public.profiles for update to authenticated
using ((select auth.uid()) = id)
with check ((select auth.uid()) = id);

create or replace function public.sync_profile_from_auth()
returns trigger
language plpgsql
security definer set search_path = ''
as $$
begin
  insert into public.profiles (id, username, display_name, avatar_url, updated_at)
  values (
    new.id,
    nullif(lower(new.raw_user_meta_data ->> 'username'), ''),
    coalesce(new.raw_user_meta_data ->> 'display_name', new.raw_user_meta_data ->> 'full_name'),
    coalesce(new.raw_user_meta_data ->> 'avatar_url', new.raw_user_meta_data ->> 'picture'),
    now()
  )
  on conflict (id) do update set
    username = coalesce(excluded.username, public.profiles.username),
    display_name = coalesce(excluded.display_name, public.profiles.display_name),
    avatar_url = coalesce(excluded.avatar_url, public.profiles.avatar_url),
    updated_at = now();
  return new;
end;
$$;

drop trigger if exists on_auth_user_profile_sync on auth.users;
create trigger on_auth_user_profile_sync
after insert or update of raw_user_meta_data on auth.users
for each row execute procedure public.sync_profile_from_auth();
