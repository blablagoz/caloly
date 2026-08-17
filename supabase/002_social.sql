-- Caloly v0.6.0 - Social graph, per-relationship sharing permissions, daily summary sync.
-- Run after 001_auth_profiles.sql.

create extension if not exists pgcrypto;

create table if not exists public.follow_requests (
    id uuid primary key default gen_random_uuid(),
    requester_id uuid not null references public.profiles(id) on delete cascade,
    target_id uuid not null references public.profiles(id) on delete cascade,
    relationship_type text not null default 'FRIEND' check (relationship_type in ('FRIEND','PARTNER','DIETITIAN','CLIENT')),
    status text not null default 'PENDING' check (status in ('PENDING','ACCEPTED','REJECTED')),
    created_at timestamptz not null default now(),
    responded_at timestamptz,
    constraint no_self_follow_request check (requester_id <> target_id)
);

create unique index if not exists one_pending_request_per_pair
on public.follow_requests (least(requester_id, target_id), greatest(requester_id, target_id))
where status = 'PENDING';

create table if not exists public.relationships (
    id uuid primary key default gen_random_uuid(),
    user_a uuid not null references public.profiles(id) on delete cascade,
    user_b uuid not null references public.profiles(id) on delete cascade,
    relationship_type text not null default 'FRIEND' check (relationship_type in ('FRIEND','PARTNER','DIETITIAN','CLIENT')),
    created_at timestamptz not null default now(),
    constraint no_self_relationship check (user_a <> user_b)
);

create unique index if not exists one_relationship_per_pair
on public.relationships (least(user_a, user_b), greatest(user_a, user_b));

create table if not exists public.sharing_permissions (
    relationship_id uuid not null references public.relationships(id) on delete cascade,
    owner_id uuid not null references public.profiles(id) on delete cascade,
    viewer_id uuid not null references public.profiles(id) on delete cascade,
    share_calories boolean not null default true,
    share_macros boolean not null default true,
    share_steps boolean not null default true,
    share_activity boolean not null default true,
    share_weight boolean not null default false,
    share_food_details boolean not null default false,
    share_history boolean not null default false,
    updated_at timestamptz not null default now(),
    primary key (relationship_id, owner_id),
    constraint sharing_owner_not_viewer check (owner_id <> viewer_id)
);

create table if not exists public.daily_summaries (
    user_id uuid not null references public.profiles(id) on delete cascade,
    date date not null,
    consumed_calories integer not null default 0,
    calorie_goal integer not null default 2100,
    protein_grams integer not null default 0,
    protein_goal integer not null default 140,
    carbs_grams integer not null default 0,
    carbs_goal integer not null default 220,
    fat_grams integer not null default 0,
    fat_goal integer not null default 70,
    steps integer not null default 0,
    active_calories integer not null default 0,
    total_calories_burned integer not null default 0,
    updated_at timestamptz not null default now(),
    primary key (user_id, date)
);

alter table public.follow_requests enable row level security;
alter table public.relationships enable row level security;
alter table public.sharing_permissions enable row level security;
alter table public.daily_summaries enable row level security;

-- Mobile client may only directly read/write its own daily summary. Shared reads go through a SECURITY DEFINER RPC
-- so unauthorized columns can be returned as NULL instead of exposing the whole row.
create policy "users manage own daily summary" on public.daily_summaries
for all to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

-- Social tables are intentionally hidden from direct Data API access. RPCs below expose only permitted data.

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
    from profiles p
    where p.id <> auth.uid()
      and length(trim(search_text)) >= 2
      and (
        coalesce(p.username,'') ilike '%' || trim(search_text) || '%'
        or coalesce(p.display_name,'') ilike '%' || trim(search_text) || '%'
      )
    order by
      case when lower(coalesce(p.username,'')) = lower(trim(search_text)) then 0 else 1 end,
      p.username nulls last
    limit 30;
$$;

create or replace function public.send_caloly_follow_request(target_user_id uuid, relation_type text default 'FRIEND')
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare new_id uuid;
begin
    if auth.uid() is null then raise exception 'Not authenticated'; end if;
    if target_user_id = auth.uid() then raise exception 'Kendine takip isteği gönderemezsin'; end if;
    if relation_type not in ('FRIEND','PARTNER','DIETITIAN','CLIENT') then raise exception 'Invalid relationship type'; end if;
    if exists (select 1 from relationships r where (r.user_a=auth.uid() and r.user_b=target_user_id) or (r.user_b=auth.uid() and r.user_a=target_user_id)) then
        raise exception 'Zaten bağlantıdasınız';
    end if;
    insert into follow_requests(requester_id,target_id,relationship_type)
    values(auth.uid(), target_user_id, relation_type)
    returning id into new_id;
    return new_id;
end;
$$;

create or replace function public.get_caloly_follow_requests()
returns table (
    request_id uuid,
    requester_id uuid,
    username text,
    display_name text,
    avatar_url text,
    relationship_type text,
    created_at timestamptz
)
language sql
security definer
set search_path = public
as $$
    select fr.id, p.id, p.username, p.display_name, p.avatar_url, fr.relationship_type, fr.created_at
    from follow_requests fr
    join profiles p on p.id = fr.requester_id
    where fr.target_id = auth.uid() and fr.status = 'PENDING'
    order by fr.created_at desc;
$$;

create or replace function public.respond_caloly_follow_request(request_id uuid, accept_request boolean)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare req follow_requests%rowtype;
declare rel_id uuid;
begin
    select * into req from follow_requests where id=request_id and target_id=auth.uid() and status='PENDING' for update;
    if not found then raise exception 'Takip isteği bulunamadı'; end if;

    update follow_requests set status = case when accept_request then 'ACCEPTED' else 'REJECTED' end, responded_at=now() where id=request_id;
    if not accept_request then return; end if;

    select r.id into rel_id from relationships r
    where (r.user_a=req.requester_id and r.user_b=req.target_id)
       or (r.user_b=req.requester_id and r.user_a=req.target_id)
    limit 1;

    if rel_id is null then
        insert into relationships(user_a,user_b,relationship_type)
        values(req.requester_id, req.target_id, req.relationship_type)
        returning id into rel_id;
    end if;

    insert into sharing_permissions(relationship_id,owner_id,viewer_id)
    values(rel_id, req.requester_id, req.target_id), (rel_id, req.target_id, req.requester_id)
    on conflict (relationship_id,owner_id) do nothing;
end;
$$;

create or replace function public.get_caloly_connections()
returns table (
    relationship_id uuid,
    other_user_id uuid,
    username text,
    display_name text,
    avatar_url text,
    relationship_type text,
    share_calories boolean,
    share_macros boolean,
    share_steps boolean,
    share_activity boolean,
    share_weight boolean,
    share_food_details boolean,
    share_history boolean
)
language sql
security definer
set search_path = public
as $$
    select r.id,
           case when r.user_a=auth.uid() then r.user_b else r.user_a end as other_id,
           p.username,p.display_name,p.avatar_url,r.relationship_type,
           sp.share_calories,sp.share_macros,sp.share_steps,sp.share_activity,sp.share_weight,sp.share_food_details,sp.share_history
    from relationships r
    join profiles p on p.id = case when r.user_a=auth.uid() then r.user_b else r.user_a end
    join sharing_permissions sp on sp.relationship_id=r.id and sp.owner_id=auth.uid()
    where r.user_a=auth.uid() or r.user_b=auth.uid()
    order by p.display_name nulls last, p.username;
$$;

create or replace function public.update_caloly_sharing(
    relationship_id uuid,
    share_calories boolean,
    share_macros boolean,
    share_steps boolean,
    share_activity boolean,
    share_weight boolean,
    share_food_details boolean,
    share_history boolean
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    update sharing_permissions sp set
        share_calories=update_caloly_sharing.share_calories,
        share_macros=update_caloly_sharing.share_macros,
        share_steps=update_caloly_sharing.share_steps,
        share_activity=update_caloly_sharing.share_activity,
        share_weight=update_caloly_sharing.share_weight,
        share_food_details=update_caloly_sharing.share_food_details,
        share_history=update_caloly_sharing.share_history,
        updated_at=now()
    where sp.relationship_id=update_caloly_sharing.relationship_id and sp.owner_id=auth.uid();
    if not found then raise exception 'Paylaşım ilişkisi bulunamadı'; end if;
end;
$$;

create or replace function public.get_caloly_shared_daily_summary(target_user_id uuid, summary_date date)
returns table (
    date date,
    consumed_calories integer,
    calorie_goal integer,
    protein_grams integer,
    protein_goal integer,
    carbs_grams integer,
    carbs_goal integer,
    fat_grams integer,
    fat_goal integer,
    steps integer,
    active_calories integer,
    total_calories_burned integer
)
language sql
security definer
set search_path = public
as $$
    select d.date,
        case when sp.share_calories then d.consumed_calories else null end,
        case when sp.share_calories then d.calorie_goal else null end,
        case when sp.share_macros then d.protein_grams else null end,
        case when sp.share_macros then d.protein_goal else null end,
        case when sp.share_macros then d.carbs_grams else null end,
        case when sp.share_macros then d.carbs_goal else null end,
        case when sp.share_macros then d.fat_grams else null end,
        case when sp.share_macros then d.fat_goal else null end,
        case when sp.share_steps then d.steps else null end,
        case when sp.share_activity then d.active_calories else null end,
        case when sp.share_activity then d.total_calories_burned else null end
    from daily_summaries d
    join relationships r on ((r.user_a=target_user_id and r.user_b=auth.uid()) or (r.user_b=target_user_id and r.user_a=auth.uid()))
    join sharing_permissions sp on sp.relationship_id=r.id and sp.owner_id=target_user_id and sp.viewer_id=auth.uid()
    where d.user_id=target_user_id and d.date=summary_date
      and (summary_date=current_date or sp.share_history);
$$;

grant execute on function public.search_caloly_profiles(text) to authenticated;
grant execute on function public.send_caloly_follow_request(uuid,text) to authenticated;
grant execute on function public.get_caloly_follow_requests() to authenticated;
grant execute on function public.respond_caloly_follow_request(uuid,boolean) to authenticated;
grant execute on function public.get_caloly_connections() to authenticated;
grant execute on function public.update_caloly_sharing(uuid,boolean,boolean,boolean,boolean,boolean,boolean,boolean) to authenticated;
grant execute on function public.get_caloly_shared_daily_summary(uuid,date) to authenticated;

grant select, insert, update on public.daily_summaries to authenticated;
