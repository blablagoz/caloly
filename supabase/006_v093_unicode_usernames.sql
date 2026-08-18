-- Caloly v0.9.3 - Turkish/Unicode usernames and accent tolerant people search

alter table public.profiles drop constraint if exists username_format;
alter table public.profiles add constraint username_format
    check (username is null or username ~ '^[[:alnum:]_.]{3,24}$');

create or replace function public.caloly_username_key(value text)
returns text
language sql
immutable
parallel safe
as $$
    select lower(translate(normalize(coalesce(value, ''), NFC), 'ÇĞİÖŞÜI', 'çğiöşüı'))
$$;

alter table public.login_identifiers add column if not exists username_key text;
update public.login_identifiers
set username_key = public.caloly_username_key(username)
where username_key is null or username_key <> public.caloly_username_key(username);

create or replace function public.set_caloly_username_key()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    new.username := normalize(trim(new.username), NFC);
    new.username_key := public.caloly_username_key(new.username);
    return new;
end;
$$;

drop trigger if exists set_caloly_username_key on public.login_identifiers;
create trigger set_caloly_username_key
before insert or update of username on public.login_identifiers
for each row execute function public.set_caloly_username_key();

alter table public.login_identifiers alter column username_key set not null;
drop index if exists public.login_identifiers_username_key;
create unique index login_identifiers_username_key
    on public.login_identifiers (username_key);

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
    where p.id <> auth.uid()
      and length(q.key) >= 2
      and (
        public.caloly_username_key(coalesce(p.username, '')) like '%' || q.key || '%'
        or public.caloly_username_key(coalesce(p.display_name, '')) like '%' || q.key || '%'
      )
    order by
      case when public.caloly_username_key(coalesce(p.username, '')) = q.key then 0 else 1 end,
      p.username nulls last
    limit 30;
$$;

grant execute on function public.search_caloly_profiles(text) to authenticated;
