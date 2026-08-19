-- Caloly v0.9.5 - Live social bootstrap follow-up and safer Turkish people search.
-- Run after 001-006. The production project received this migration on 2026-08-18.

-- Existing auth accounts may predate the public profile trigger. Backfill them safely.
insert into public.profiles (id, username, display_name, avatar_url, created_at, updated_at)
select
    u.id,
    nullif(normalize(trim(u.raw_user_meta_data ->> 'username'), NFC), ''),
    coalesce(u.raw_user_meta_data ->> 'display_name', u.raw_user_meta_data ->> 'full_name'),
    coalesce(u.raw_user_meta_data ->> 'avatar_url', u.raw_user_meta_data ->> 'picture'),
    coalesce(u.created_at, now()),
    now()
from auth.users u
on conflict (id) do update set
    username = coalesce(excluded.username, public.profiles.username),
    display_name = coalesce(excluded.display_name, public.profiles.display_name),
    avatar_url = coalesce(excluded.avatar_url, public.profiles.avatar_url),
    updated_at = now();

insert into public.login_identifiers (user_id, username, username_key, login_email, updated_at)
select
    p.id,
    p.username,
    public.caloly_username_key(p.username),
    lower(u.email),
    now()
from public.profiles p
join auth.users u on u.id = p.id
where p.username is not null and u.email is not null
on conflict (user_id) do update set
    username = excluded.username,
    username_key = excluded.username_key,
    login_email = excluded.login_email,
    updated_at = now();

create or replace function public.search_caloly_profiles(search_text text)
returns table (
    id uuid,
    username text,
    display_name text,
    avatar_url text,
    relationship_status text
)
language sql
security definer
set search_path = public
as $$
    with query as (
        select public.caloly_username_key(trim(search_text)) as key
    )
    select p.id, p.username, p.display_name, p.avatar_url,
        case
            when exists (
                select 1 from relationships r
                where (r.user_a = auth.uid() and r.user_b = p.id)
                   or (r.user_b = auth.uid() and r.user_a = p.id)
            ) then 'CONNECTED'
            when exists (
                select 1 from follow_requests fr
                where fr.status = 'PENDING'
                  and fr.requester_id = auth.uid() and fr.target_id = p.id
            ) then 'REQUESTED'
            when exists (
                select 1 from follow_requests fr
                where fr.status = 'PENDING'
                  and fr.target_id = auth.uid() and fr.requester_id = p.id
            ) then 'INCOMING'
            else null
        end
    from profiles p cross join query q
    where auth.uid() is not null
      and p.id <> auth.uid()
      and length(q.key) >= 2
      and (
        public.caloly_username_key(coalesce(p.username, '')) like '%' || q.key || '%'
        or public.caloly_username_key(coalesce(p.display_name, '')) like '%' || q.key || '%'
      )
    order by
      case when public.caloly_username_key(coalesce(p.username, '')) = q.key then 0 else 1 end,
      p.display_name nulls last,
      p.username nulls last
    limit 50;
$$;

alter function public.caloly_username_key(text) set search_path = pg_catalog;
revoke all on function public.caloly_username_key(text) from public, anon, authenticated;
revoke all on function public.set_caloly_username_key() from public, anon, authenticated;
revoke all on function public.sync_profile_from_auth() from public, anon, authenticated;
revoke all on function public.search_caloly_profiles(text) from public, anon;
grant execute on function public.search_caloly_profiles(text) to authenticated;

alter table public.daily_summaries alter column calorie_goal set default 0;
alter table public.daily_summaries alter column protein_goal set default 0;
alter table public.daily_summaries alter column carbs_goal set default 0;
alter table public.daily_summaries alter column fat_goal set default 0;

notify pgrst, 'reload schema';
