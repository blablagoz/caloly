create table if not exists public.caloly_food_logs (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  date_key date not null,
  meal_type text not null,
  food_name text not null,
  brand text,
  amount double precision not null check (amount > 0),
  unit text not null,
  grams double precision not null check (grams >= 0),
  calories integer not null check (calories >= 0),
  protein_grams double precision not null default 0 check (protein_grams >= 0),
  carbs_grams double precision not null default 0 check (carbs_grams >= 0),
  fat_grams double precision not null default 0 check (fat_grams >= 0),
  created_at bigint not null,
  updated_at timestamptz not null default now()
);

create index if not exists caloly_food_logs_user_date_idx
  on public.caloly_food_logs (user_id, date_key, created_at desc);

alter table public.caloly_food_logs enable row level security;

revoke all on table public.caloly_food_logs from anon;
grant select, insert, update, delete on table public.caloly_food_logs to authenticated;

drop policy if exists "Users read own Caloly food logs" on public.caloly_food_logs;
create policy "Users read own Caloly food logs"
  on public.caloly_food_logs for select to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists "Users insert own Caloly food logs" on public.caloly_food_logs;
create policy "Users insert own Caloly food logs"
  on public.caloly_food_logs for insert to authenticated
  with check ((select auth.uid()) = user_id);

drop policy if exists "Users update own Caloly food logs" on public.caloly_food_logs;
create policy "Users update own Caloly food logs"
  on public.caloly_food_logs for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

drop policy if exists "Users delete own Caloly food logs" on public.caloly_food_logs;
create policy "Users delete own Caloly food logs"
  on public.caloly_food_logs for delete to authenticated
  using ((select auth.uid()) = user_id);
