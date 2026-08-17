-- Caloly v0.7.0 - relationship goals / partner dashboard.
-- Run after 002_social.sql.

create table if not exists public.relationship_goals (
    id uuid primary key default gen_random_uuid(),
    relationship_id uuid not null references public.relationships(id) on delete cascade,
    created_by uuid not null references public.profiles(id) on delete cascade,
    metric text not null check (metric in ('STEPS_DAILY','CALORIE_TARGET')),
    target_value integer not null check (target_value > 0),
    active boolean not null default true,
    created_at timestamptz not null default now()
);

alter table public.relationship_goals enable row level security;
-- Direct Data API access is intentionally omitted. Access goes through RPCs that verify membership.

create or replace function public.create_caloly_relationship_goal(
    p_relationship_id uuid,
    p_goal_metric text,
    p_target_value integer
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    new_id uuid;
begin
    if auth.uid() is null then raise exception 'Not authenticated'; end if;
    if p_goal_metric not in ('STEPS_DAILY','CALORIE_TARGET') then raise exception 'Geçersiz hedef türü'; end if;
    if p_target_value <= 0 then raise exception 'Hedef sıfırdan büyük olmalı'; end if;
    if not exists (
        select 1 from relationships r
        where r.id = p_relationship_id and (r.user_a = auth.uid() or r.user_b = auth.uid())
    ) then raise exception 'Bu bağlantı için yetkin yok'; end if;

    insert into relationship_goals(relationship_id, created_by, metric, target_value)
    values(p_relationship_id, auth.uid(), p_goal_metric, p_target_value)
    returning id into new_id;
    return new_id;
end;
$$;

create or replace function public.get_caloly_relationship_goals(
    p_relationship_id uuid,
    p_goal_date date
)
returns table (
    id uuid,
    relationship_id uuid,
    title text,
    metric text,
    target_value integer,
    my_value integer,
    partner_value integer,
    my_completed boolean,
    partner_completed boolean,
    active boolean
)
language sql
security definer
set search_path = public
as $$
    with rel as (
        select r.*,
               case when r.user_a = auth.uid() then r.user_b else r.user_a end as partner_id
        from relationships r
        where r.id = p_relationship_id and (r.user_a = auth.uid() or r.user_b = auth.uid())
    ), values_for_day as (
        select rel.id as rel_id,
               me.steps as my_steps,
               me.consumed_calories as my_calories,
               me.calorie_goal as my_calorie_goal,
               case when sp.share_steps then partner.steps else null end as partner_steps,
               case when sp.share_calories then partner.consumed_calories else null end as partner_calories,
               case when sp.share_calories then partner.calorie_goal else null end as partner_calorie_goal
        from rel
        left join daily_summaries me on me.user_id = auth.uid() and me.date = p_goal_date
        left join daily_summaries partner on partner.user_id = rel.partner_id and partner.date = p_goal_date
        left join sharing_permissions sp on sp.relationship_id = rel.id and sp.owner_id = rel.partner_id and sp.viewer_id = auth.uid()
    )
    select g.id,
           g.relationship_id,
           case when g.metric = 'STEPS_DAILY' then (g.target_value::text || ' adım birlikte') else 'İkiniz de kalori hedefinde' end as title,
           g.metric,
           g.target_value,
           case when g.metric = 'STEPS_DAILY' then v.my_steps else v.my_calories end as my_value,
           case when g.metric = 'STEPS_DAILY' then v.partner_steps else v.partner_calories end as partner_value,
           case when g.metric = 'STEPS_DAILY' then coalesce(v.my_steps,0) >= g.target_value
                else v.my_calories is not null and v.my_calorie_goal is not null and v.my_calories <= v.my_calorie_goal end as my_completed,
           case when g.metric = 'STEPS_DAILY' then coalesce(v.partner_steps,0) >= g.target_value
                else v.partner_calories is not null and v.partner_calorie_goal is not null and v.partner_calories <= v.partner_calorie_goal end as partner_completed,
           g.active
    from relationship_goals g
    join values_for_day v on v.rel_id = g.relationship_id
    where g.relationship_id = p_relationship_id and g.active
    order by g.created_at desc;
$$;

grant execute on function public.create_caloly_relationship_goal(uuid,text,integer) to authenticated;
grant execute on function public.get_caloly_relationship_goals(uuid,date) to authenticated;

-- Public avatars bucket. Only authenticated users can write inside their own folder.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 5242880, array['image/jpeg','image/png','image/webp'])
on conflict (id) do update set public = true, file_size_limit = 5242880;

create policy "avatar owner insert" on storage.objects
for insert to authenticated
with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid()::text));

create policy "avatar owner update" on storage.objects
for update to authenticated
using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid()::text))
with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid()::text));

create policy "public avatars read" on storage.objects
for select to public
using (bucket_id = 'avatars');
