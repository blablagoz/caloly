-- Caloly v0.9.1 - Personal profile, username login and onboarding

alter table public.profiles
    add column if not exists birth_date date,
    add column if not exists height_cm integer,
    add column if not exists weight_kg numeric(6,2),
    add column if not exists target_weight_kg numeric(6,2),
    add column if not exists gender text,
    add column if not exists activity_level text,
    add column if not exists nutrition_goal text,
    add column if not exists onboarding_completed boolean not null default false;

alter table public.profiles drop constraint if exists profiles_gender_check;
alter table public.profiles add constraint profiles_gender_check
    check (gender is null or gender in ('FEMALE', 'MALE', 'UNDISCLOSED'));
alter table public.profiles drop constraint if exists profiles_activity_level_check;
alter table public.profiles add constraint profiles_activity_level_check
    check (activity_level is null or activity_level in ('SEDENTARY', 'LIGHT', 'MODERATE', 'ACTIVE'));
alter table public.profiles drop constraint if exists profiles_nutrition_goal_check;
alter table public.profiles add constraint profiles_nutrition_goal_check
    check (nutrition_goal is null or nutrition_goal in ('LOSE', 'MAINTAIN', 'GAIN'));

-- Login identifiers live outside the social profile surface. RLS has no client
-- policies, so only the service-role Edge Function and the trigger can read it.
create table if not exists public.login_identifiers (
    user_id uuid primary key references auth.users(id) on delete cascade,
    username text not null,
    login_email text not null,
    updated_at timestamptz not null default now()
);

create unique index if not exists login_identifiers_username_key
    on public.login_identifiers (lower(username));

alter table public.login_identifiers enable row level security;
revoke all on public.login_identifiers from anon, authenticated;
grant select on public.login_identifiers to service_role;

create or replace function public.sync_profile_from_auth()
returns trigger
language plpgsql
security definer set search_path = ''
as $$
begin
  insert into public.profiles (
    id, username, display_name, avatar_url, birth_date, height_cm,
    weight_kg, target_weight_kg, gender, activity_level, nutrition_goal,
    onboarding_completed, updated_at
  )
  values (
    new.id,
    nullif(lower(new.raw_user_meta_data ->> 'username'), ''),
    coalesce(new.raw_user_meta_data ->> 'display_name', new.raw_user_meta_data ->> 'full_name'),
    coalesce(new.raw_user_meta_data ->> 'avatar_url', new.raw_user_meta_data ->> 'picture'),
    nullif(new.raw_user_meta_data ->> 'birth_date', '')::date,
    nullif(new.raw_user_meta_data ->> 'height_cm', '')::integer,
    nullif(new.raw_user_meta_data ->> 'weight_kg', '')::numeric,
    nullif(new.raw_user_meta_data ->> 'target_weight_kg', '')::numeric,
    nullif(new.raw_user_meta_data ->> 'gender', ''),
    nullif(new.raw_user_meta_data ->> 'activity_level', ''),
    nullif(new.raw_user_meta_data ->> 'nutrition_goal', ''),
    coalesce((new.raw_user_meta_data ->> 'onboarding_completed')::boolean, false),
    now()
  )
  on conflict (id) do update set
    username = coalesce(excluded.username, public.profiles.username),
    display_name = coalesce(excluded.display_name, public.profiles.display_name),
    avatar_url = coalesce(excluded.avatar_url, public.profiles.avatar_url),
    birth_date = coalesce(excluded.birth_date, public.profiles.birth_date),
    height_cm = coalesce(excluded.height_cm, public.profiles.height_cm),
    weight_kg = coalesce(excluded.weight_kg, public.profiles.weight_kg),
    target_weight_kg = coalesce(excluded.target_weight_kg, public.profiles.target_weight_kg),
    gender = coalesce(excluded.gender, public.profiles.gender),
    activity_level = coalesce(excluded.activity_level, public.profiles.activity_level),
    nutrition_goal = coalesce(excluded.nutrition_goal, public.profiles.nutrition_goal),
    onboarding_completed = excluded.onboarding_completed,
    updated_at = now();

  if new.email is not null and nullif(new.raw_user_meta_data ->> 'username', '') is not null then
    insert into public.login_identifiers (user_id, username, login_email, updated_at)
    values (
      new.id,
      lower(new.raw_user_meta_data ->> 'username'),
      lower(new.email),
      now()
    )
    on conflict (user_id) do update set
      username = excluded.username,
      login_email = excluded.login_email,
      updated_at = now();
  end if;
  return new;
end;
$$;

-- Backfill existing accounts into the locked lookup table.
insert into public.login_identifiers (user_id, username, login_email)
select p.id, lower(p.username), lower(u.email)
from public.profiles p
join auth.users u on u.id = p.id
where p.username is not null and u.email is not null
on conflict (user_id) do update set
  username = excluded.username,
  login_email = excluded.login_email,
  updated_at = now();
