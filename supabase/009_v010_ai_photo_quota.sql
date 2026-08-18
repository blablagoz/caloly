create table if not exists public.ai_photo_usage (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

alter table public.ai_photo_usage enable row level security;
revoke all on table public.ai_photo_usage from public, anon, authenticated;
grant all on table public.ai_photo_usage to service_role;

create index if not exists ai_photo_usage_user_created_idx
  on public.ai_photo_usage (user_id, created_at desc);

create or replace function public.consume_caloly_photo_quota(
  p_user_id uuid,
  p_limit integer default 3
)
returns jsonb
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
declare
  v_count integer;
  v_oldest timestamptz;
  v_reset_at timestamptz;
begin
  if p_user_id is null or p_limit <> 3 then
    raise exception 'invalid quota request';
  end if;

  perform pg_advisory_xact_lock(hashtextextended(p_user_id::text, 90310));

  delete from public.ai_photo_usage
  where user_id = p_user_id
    and created_at < now() - interval '24 hours';

  select count(*)::integer, min(created_at)
  into v_count, v_oldest
  from public.ai_photo_usage
  where user_id = p_user_id
    and created_at > now() - interval '1 hour';

  if v_count >= p_limit then
    v_reset_at := v_oldest + interval '1 hour';
    return jsonb_build_object(
      'allowed', false,
      'remaining', 0,
      'resetAt', v_reset_at
    );
  end if;

  insert into public.ai_photo_usage (user_id) values (p_user_id);
  return jsonb_build_object(
    'allowed', true,
    'remaining', p_limit - v_count - 1,
    'resetAt', coalesce(v_oldest, now()) + interval '1 hour'
  );
end;
$$;

revoke all on function public.consume_caloly_photo_quota(uuid, integer) from public, anon, authenticated;
grant execute on function public.consume_caloly_photo_quota(uuid, integer) to service_role;
