-- Caloly v0.9.2 - Shareable meal and day templates.
-- Run after 001, 002, 003 and 004.

create extension if not exists pgcrypto;

create table if not exists public.nutrition_templates (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references public.profiles(id) on delete cascade,
    title text not null check (char_length(trim(title)) between 1 and 80),
    kind text not null check (kind in ('MEAL', 'DAY')),
    items jsonb not null check (jsonb_typeof(items) = 'array'),
    shared boolean not null default true,
    created_at timestamptz not null default now()
);

create index if not exists nutrition_templates_owner_created_idx
    on public.nutrition_templates (owner_id, created_at desc);

alter table public.nutrition_templates enable row level security;
revoke all on public.nutrition_templates from anon, authenticated;

create or replace function public.publish_caloly_nutrition_template(
    p_title text,
    p_kind text,
    p_items jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    new_id uuid;
begin
    if auth.uid() is null then
        raise exception 'Not authenticated';
    end if;
    if p_kind not in ('MEAL', 'DAY') then
        raise exception 'Invalid template kind';
    end if;
    if jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
        raise exception 'Template must contain at least one food';
    end if;

    insert into public.nutrition_templates (owner_id, title, kind, items, shared)
    values (auth.uid(), trim(p_title), p_kind, p_items, true)
    returning id into new_id;

    return new_id;
end;
$$;

create or replace function public.get_caloly_shared_nutrition_templates()
returns table (
    template_id uuid,
    owner_name text,
    title text,
    kind text,
    items jsonb,
    created_at timestamptz
)
language sql
security definer
set search_path = public
as $$
    select distinct on (t.id)
        t.id,
        coalesce(p.display_name, p.username, 'Caloly kullanıcısı') as owner_name,
        t.title,
        t.kind,
        t.items,
        t.created_at
    from public.nutrition_templates t
    join public.profiles p on p.id = t.owner_id
    join public.relationships r
      on (r.user_a = auth.uid() and r.user_b = t.owner_id)
      or (r.user_b = auth.uid() and r.user_a = t.owner_id)
    join public.sharing_permissions sp
      on sp.relationship_id = r.id
     and sp.owner_id = t.owner_id
     and sp.viewer_id = auth.uid()
    where auth.uid() is not null
      and t.shared = true
      and sp.share_food_details = true
    order by t.id, t.created_at desc;
$$;

grant execute on function public.publish_caloly_nutrition_template(text, text, jsonb) to authenticated;
grant execute on function public.get_caloly_shared_nutrition_templates() to authenticated;

notify pgrst, 'reload schema';
