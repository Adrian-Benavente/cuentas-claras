-- Cuentas Claras MVP schema + RLS
-- Apply with: supabase db push / SQL editor

create extension if not exists pgcrypto;

-- Profiles (mirrors auth.users)
create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text not null,
  email text,
  avatar_url text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.groups (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(trim(name)) > 0),
  currency text not null default 'ARS' check (char_length(currency) = 3),
  invite_code text not null unique,
  avatar_url text,
  theme_id text not null default 'forest'
    check (theme_id in ('forest', 'ocean', 'sunset', 'slate', 'orchid')),
  created_by uuid not null references public.profiles (id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.group_members (
  group_id uuid not null references public.groups (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  role text not null check (role in ('OWNER', 'MEMBER')),
  joined_at timestamptz not null default now(),
  primary key (group_id, user_id)
);

create table if not exists public.expenses (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.groups (id) on delete cascade,
  description text not null check (char_length(trim(description)) > 0),
  amount_minor bigint not null check (amount_minor > 0),
  currency text not null check (char_length(currency) = 3),
  paid_by uuid not null references public.profiles (id),
  expense_date date not null,
  created_by uuid not null references public.profiles (id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  installment_series_id uuid null,
  installment_index int null,
  installment_count int null,
  check (
    (
      installment_series_id is null
      and installment_index is null
      and installment_count is null
    )
    or (
      installment_series_id is not null
      and installment_index is not null
      and installment_count is not null
      and installment_index >= 1
      and installment_count >= 2
      and installment_index <= installment_count
    )
  )
);

create table if not exists public.expense_splits (
  id uuid primary key default gen_random_uuid(),
  expense_id uuid not null references public.expenses (id) on delete cascade,
  user_id uuid not null references public.profiles (id),
  split_type text not null default 'EQUAL'
    check (split_type in ('EQUAL', 'PERCENTAGE', 'FIXED_AMOUNT')),
  share_amount_minor bigint not null check (share_amount_minor >= 0),
  unique (expense_id, user_id)
);

create index if not exists idx_expenses_group_date on public.expenses (group_id, expense_date desc);
create index if not exists idx_expenses_group_installment_series
  on public.expenses (group_id, installment_series_id);
create index if not exists idx_group_members_user on public.group_members (user_id);
create index if not exists idx_expense_splits_expense on public.expense_splits (expense_id);

-- Period closures: OPEN by default (no row); CLOSED when a row exists.
create table if not exists public.group_period_closures (
  group_id uuid not null references public.groups (id) on delete cascade,
  period_year int not null,
  period_month int not null check (period_month between 1 and 12),
  closed_by uuid not null references public.profiles (id),
  closed_at timestamptz not null default now(),
  primary key (group_id, period_year, period_month)
);

create index if not exists idx_group_period_closures_group
  on public.group_period_closures (group_id);

-- Helpers
create or replace function public.is_group_member(p_group_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.group_members gm
    where gm.group_id = p_group_id and gm.user_id = auth.uid()
  );
$$;

create or replace function public.is_group_owner(p_group_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.group_members gm
    where gm.group_id = p_group_id
      and gm.user_id = auth.uid()
      and gm.role = 'OWNER'
  );
$$;

create or replace function public.is_group_period_closed(
  p_group_id uuid,
  p_year int,
  p_month int
)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.group_period_closures c
    where c.group_id = p_group_id
      and c.period_year = p_year
      and c.period_month = p_month
  );
$$;

create or replace function public.close_group_period(
  p_group_id uuid,
  p_year int,
  p_month int
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;
  if p_month < 1 or p_month > 12 then
    raise exception 'invalid period month';
  end if;
  if not public.is_group_owner(p_group_id) then
    raise exception 'only owner can close period';
  end if;
  if public.is_group_period_closed(p_group_id, p_year, p_month) then
    return;
  end if;

  insert into public.group_period_closures (
    group_id, period_year, period_month, closed_by
  ) values (
    p_group_id, p_year, p_month, auth.uid()
  );
end;
$$;

create or replace function public.reopen_group_period(
  p_group_id uuid,
  p_year int,
  p_month int
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;
  if p_month < 1 or p_month > 12 then
    raise exception 'invalid period month';
  end if;
  if not public.is_group_owner(p_group_id) then
    raise exception 'only owner can reopen period';
  end if;

  delete from public.group_period_closures
  where group_id = p_group_id
    and period_year = p_year
    and period_month = p_month;
end;
$$;

create or replace function public.generate_invite_code()
returns text
language plpgsql
as $$
declare
  chars text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  result text := '';
  i int;
begin
  for i in 1..6 loop
    result := result || substr(chars, 1 + floor(random() * length(chars))::int, 1);
  end loop;
  return result;
end;
$$;

create or replace function public.create_group(p_name text)
returns public.groups
language plpgsql
security definer
set search_path = public
as $$
declare
  v_group public.groups;
  v_code text;
  v_attempts int := 0;
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;
  if p_name is null or length(trim(p_name)) = 0 then
    raise exception 'invalid name';
  end if;

  loop
    v_code := public.generate_invite_code();
    begin
      insert into public.groups (name, currency, invite_code, created_by)
      values (trim(p_name), 'ARS', v_code, auth.uid())
      returning * into v_group;
      exit;
    exception when unique_violation then
      v_attempts := v_attempts + 1;
      if v_attempts > 10 then
        raise exception 'could not generate invite code';
      end if;
    end;
  end loop;

  insert into public.group_members (group_id, user_id, role)
  values (v_group.id, auth.uid(), 'OWNER');

  return v_group;
end;
$$;

create or replace function public.join_group_by_code(p_invite_code text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_group public.groups;
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;

  if p_invite_code is null or length(trim(p_invite_code)) = 0 then
    raise exception 'invalid invite code';
  end if;

  select * into v_group
  from public.groups g
  where g.invite_code = upper(trim(p_invite_code));

  if not found then
    raise exception 'invalid invite code';
  end if;

  insert into public.group_members (group_id, user_id, role)
  values (v_group.id, auth.uid(), 'MEMBER')
  on conflict do nothing;

  return jsonb_build_object('group_id', v_group.id);
end;
$$;

create or replace function public.rotate_invite_code(p_group_id uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_code text;
  v_attempts int := 0;
begin
  if not public.is_group_owner(p_group_id) then
    raise exception 'only owner can rotate invite code';
  end if;

  loop
    v_code := public.generate_invite_code();
    begin
      update public.groups
      set invite_code = v_code, updated_at = now()
      where id = p_group_id;
      return v_code;
    exception when unique_violation then
      v_attempts := v_attempts + 1;
      if v_attempts > 10 then
        raise exception 'could not rotate invite code';
      end if;
    end;
  end loop;
end;
$$;

create or replace function public.remove_group_member(p_group_id uuid, p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_role text;
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;

  if not public.is_group_owner(p_group_id) then
    raise exception 'only owner can remove members';
  end if;

  if p_user_id = auth.uid() then
    raise exception 'cannot remove yourself';
  end if;

  select role into v_role
  from public.group_members
  where group_id = p_group_id and user_id = p_user_id;

  if v_role is null then
    raise exception 'member not found';
  end if;

  if v_role = 'OWNER' then
    raise exception 'cannot remove owner';
  end if;

  delete from public.group_members
  where group_id = p_group_id and user_id = p_user_id;
end;
$$;

create or replace function public.create_expense(
  p_group_id uuid,
  p_description text,
  p_amount_minor bigint,
  p_currency text,
  p_paid_by uuid,
  p_expense_date date,
  p_splits jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_expense public.expenses;
  v_split jsonb;
  v_sum bigint := 0;
begin
  if not public.is_group_member(p_group_id) then
    raise exception 'not a group member';
  end if;
  if (
    select count(*)::int from public.group_members where group_id = p_group_id
  ) < 2 then
    raise exception 'group needs at least two members';
  end if;
  if public.is_group_period_closed(
    p_group_id,
    extract(year from p_expense_date)::int,
    extract(month from p_expense_date)::int
  ) then
    raise exception 'period is closed';
  end if;
  if p_amount_minor is null or p_amount_minor <= 0 then
    raise exception 'amount must be > 0';
  end if;
  if not exists (
    select 1 from public.group_members
    where group_id = p_group_id and user_id = p_paid_by
  ) then
    raise exception 'payer must be a group member';
  end if;

  insert into public.expenses (
    group_id, description, amount_minor, currency, paid_by, expense_date, created_by
  ) values (
    p_group_id, trim(p_description), p_amount_minor, p_currency, p_paid_by, p_expense_date, auth.uid()
  ) returning * into v_expense;

  for v_split in select * from jsonb_array_elements(p_splits)
  loop
    v_sum := v_sum + (v_split->>'share_amount_minor')::bigint;
    insert into public.expense_splits (expense_id, user_id, split_type, share_amount_minor)
    values (
      v_expense.id,
      (v_split->>'user_id')::uuid,
      coalesce(v_split->>'split_type', 'EQUAL'),
      (v_split->>'share_amount_minor')::bigint
    );
  end loop;

  if v_sum <> p_amount_minor then
    raise exception 'splits must sum to expense amount';
  end if;

  return (
    select jsonb_build_object(
      'id', e.id,
      'group_id', e.group_id,
      'description', e.description,
      'amount_minor', e.amount_minor,
      'currency', e.currency,
      'paid_by', e.paid_by,
      'expense_date', e.expense_date,
      'created_by', e.created_by,
      'created_at', e.created_at,
      'updated_at', e.updated_at,
      'expense_splits', coalesce(
        (
          select jsonb_agg(jsonb_build_object(
            'id', s.id,
            'expense_id', s.expense_id,
            'user_id', s.user_id,
            'split_type', s.split_type,
            'share_amount_minor', s.share_amount_minor
          ))
          from public.expense_splits s where s.expense_id = e.id
        ),
        '[]'::jsonb
      )
    )
    from public.expenses e where e.id = v_expense.id
  );
end;
$$;

create or replace function public.update_expense(
  p_expense_id uuid,
  p_description text,
  p_amount_minor bigint,
  p_currency text,
  p_paid_by uuid,
  p_expense_date date,
  p_splits jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_expense public.expenses;
  v_split jsonb;
  v_sum bigint := 0;
begin
  select * into v_expense from public.expenses where id = p_expense_id;
  if v_expense.id is null then
    raise exception 'expense not found';
  end if;
  if not (
    v_expense.created_by = auth.uid()
    or public.is_group_owner(v_expense.group_id)
  ) then
    raise exception 'not allowed to edit expense';
  end if;
  if public.is_group_period_closed(
    v_expense.group_id,
    extract(year from v_expense.expense_date)::int,
    extract(month from v_expense.expense_date)::int
  ) or public.is_group_period_closed(
    v_expense.group_id,
    extract(year from p_expense_date)::int,
    extract(month from p_expense_date)::int
  ) then
    raise exception 'period is closed';
  end if;
  if p_amount_minor is null or p_amount_minor <= 0 then
    raise exception 'amount must be > 0';
  end if;

  update public.expenses
  set description = trim(p_description),
      amount_minor = p_amount_minor,
      currency = p_currency,
      paid_by = p_paid_by,
      expense_date = p_expense_date,
      updated_at = now()
  where id = p_expense_id
  returning * into v_expense;

  delete from public.expense_splits where expense_id = p_expense_id;

  for v_split in select * from jsonb_array_elements(p_splits)
  loop
    v_sum := v_sum + (v_split->>'share_amount_minor')::bigint;
    insert into public.expense_splits (expense_id, user_id, split_type, share_amount_minor)
    values (
      v_expense.id,
      (v_split->>'user_id')::uuid,
      coalesce(v_split->>'split_type', 'EQUAL'),
      (v_split->>'share_amount_minor')::bigint
    );
  end loop;

  if v_sum <> p_amount_minor then
    raise exception 'splits must sum to expense amount';
  end if;

  return (
    select jsonb_build_object(
      'id', e.id,
      'group_id', e.group_id,
      'description', e.description,
      'amount_minor', e.amount_minor,
      'currency', e.currency,
      'paid_by', e.paid_by,
      'expense_date', e.expense_date,
      'created_by', e.created_by,
      'created_at', e.created_at,
      'updated_at', e.updated_at,
      'expense_splits', coalesce(
        (
          select jsonb_agg(jsonb_build_object(
            'id', s.id,
            'expense_id', s.expense_id,
            'user_id', s.user_id,
            'split_type', s.split_type,
            'share_amount_minor', s.share_amount_minor
          ))
          from public.expense_splits s where s.expense_id = e.id
        ),
        '[]'::jsonb
      )
    )
    from public.expenses e where e.id = v_expense.id
  );
end;
$$;

-- Auto profile on signup
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, display_name, email, avatar_url)
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'full_name', split_part(new.email, '@', 1), 'Usuario'),
    new.email,
    new.raw_user_meta_data->>'avatar_url'
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- RLS
alter table public.profiles enable row level security;
alter table public.groups enable row level security;
alter table public.group_members enable row level security;
alter table public.expenses enable row level security;
alter table public.expense_splits enable row level security;

-- Profiles
drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles
  for select to authenticated
  using (
    id = auth.uid()
    or exists (
      select 1
      from public.group_members me
      join public.group_members other on me.group_id = other.group_id
      where me.user_id = auth.uid() and other.user_id = profiles.id
    )
    or exists (
      select 1
      from public.group_members me
      join public.expenses e on e.group_id = me.group_id
      where me.user_id = auth.uid()
        and e.paid_by = profiles.id
    )
    or exists (
      select 1
      from public.group_members me
      join public.expenses e on e.group_id = me.group_id
      join public.expense_splits s on s.expense_id = e.id
      where me.user_id = auth.uid()
        and s.user_id = profiles.id
    )
  );

drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self on public.profiles
  for update to authenticated
  using (id = auth.uid())
  with check (id = auth.uid());

drop policy if exists profiles_insert_self on public.profiles;
create policy profiles_insert_self on public.profiles
  for insert to authenticated
  with check (id = auth.uid());

-- Groups
drop policy if exists groups_select_member on public.groups;
create policy groups_select_member on public.groups
  for select to authenticated
  using (public.is_group_member(id));

drop policy if exists groups_update_owner on public.groups;
create policy groups_update_owner on public.groups
  for update to authenticated
  using (public.is_group_owner(id))
  with check (public.is_group_owner(id));

-- Members
drop policy if exists members_select on public.group_members;
create policy members_select on public.group_members
  for select to authenticated
  using (public.is_group_member(group_id));

-- Expenses
drop policy if exists expenses_select on public.expenses;
create policy expenses_select on public.expenses
  for select to authenticated
  using (public.is_group_member(group_id));

drop policy if exists expenses_delete on public.expenses;
create policy expenses_delete on public.expenses
  for delete to authenticated
  using (
    created_by = auth.uid()
    or public.is_group_owner(group_id)
  );

create or replace function public.enforce_expense_period_open()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if public.is_group_period_closed(
    old.group_id,
    extract(year from old.expense_date)::int,
    extract(month from old.expense_date)::int
  ) then
    raise exception 'period is closed';
  end if;
  return old;
end;
$$;

drop trigger if exists trg_expenses_period_closed_delete on public.expenses;
create trigger trg_expenses_period_closed_delete
  before delete on public.expenses
  for each row
  execute function public.enforce_expense_period_open();

-- Period closures
alter table public.group_period_closures enable row level security;

drop policy if exists group_period_closures_select on public.group_period_closures;
create policy group_period_closures_select on public.group_period_closures
  for select to authenticated
  using (public.is_group_member(group_id));

-- Splits
drop policy if exists splits_select on public.expense_splits;
create policy splits_select on public.expense_splits
  for select to authenticated
  using (
    exists (
      select 1 from public.expenses e
      where e.id = expense_id and public.is_group_member(e.group_id)
    )
  );

grant usage on schema public to authenticated;
grant select, insert, update on public.profiles to authenticated;
grant select, update on public.groups to authenticated;
grant select on public.group_members to authenticated;
grant select, delete on public.expenses to authenticated;
grant select on public.expense_splits to authenticated;
grant select on public.group_period_closures to authenticated;
revoke insert, update, delete on public.group_period_closures from authenticated;
grant execute on function public.create_group(text) to authenticated;
grant execute on function public.join_group_by_code(text) to authenticated;
grant execute on function public.rotate_invite_code(uuid) to authenticated;
grant execute on function public.remove_group_member(uuid, uuid) to authenticated;
grant execute on function public.create_expense(uuid, text, bigint, text, uuid, date, jsonb) to authenticated;
grant execute on function public.update_expense(uuid, text, bigint, text, uuid, date, jsonb) to authenticated;
grant execute on function public.is_group_period_closed(uuid, int, int) to authenticated;
grant execute on function public.close_group_period(uuid, int, int) to authenticated;
grant execute on function public.reopen_group_period(uuid, int, int) to authenticated;

-- Group avatars (Storage bucket + RPCs). Same contract as 20260811190000_group_avatars.sql
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'group-avatars',
  'group-avatars',
  true,
  2097152,
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists group_avatars_select on storage.objects;
create policy group_avatars_select on storage.objects
  for select to authenticated
  using (
    bucket_id = 'group-avatars'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  );

drop policy if exists group_avatars_insert on storage.objects;
create policy group_avatars_insert on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'group-avatars'
    and public.is_group_owner((storage.foldername(name))[1]::uuid)
  );

drop policy if exists group_avatars_update on storage.objects;
create policy group_avatars_update on storage.objects
  for update to authenticated
  using (
    bucket_id = 'group-avatars'
    and public.is_group_owner((storage.foldername(name))[1]::uuid)
  )
  with check (
    bucket_id = 'group-avatars'
    and public.is_group_owner((storage.foldername(name))[1]::uuid)
  );

drop policy if exists group_avatars_delete on storage.objects;
create policy group_avatars_delete on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'group-avatars'
    and public.is_group_owner((storage.foldername(name))[1]::uuid)
  );

create or replace function public.set_group_avatar(
  p_group_id uuid,
  p_avatar_url text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;
  if not public.is_group_owner(p_group_id) then
    raise exception 'only owner can set group avatar';
  end if;
  if p_avatar_url is null or length(trim(p_avatar_url)) = 0 then
    raise exception 'avatar url required';
  end if;

  update public.groups
  set avatar_url = trim(p_avatar_url),
      updated_at = now()
  where id = p_group_id;
end;
$$;

create or replace function public.clear_group_avatar(p_group_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;
  if not public.is_group_owner(p_group_id) then
    raise exception 'only owner can clear group avatar';
  end if;

  update public.groups
  set avatar_url = null,
      updated_at = now()
  where id = p_group_id;
end;
$$;

grant execute on function public.set_group_avatar(uuid, text) to authenticated;
grant execute on function public.clear_group_avatar(uuid) to authenticated;

create or replace function public.set_group_theme(
  p_group_id uuid,
  p_theme_id text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;
  if not public.is_group_owner(p_group_id) then
    raise exception 'only owner can set group theme';
  end if;
  if p_theme_id is null or p_theme_id not in ('forest', 'ocean', 'sunset', 'slate', 'orchid') then
    raise exception 'invalid theme';
  end if;

  update public.groups
  set theme_id = p_theme_id,
      updated_at = now()
  where id = p_group_id;
end;
$$;

grant execute on function public.set_group_theme(uuid, text) to authenticated;
create or replace function public.expense_to_json(p_expense_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'id', e.id,
    'group_id', e.group_id,
    'description', e.description,
    'amount_minor', e.amount_minor,
    'currency', e.currency,
    'paid_by', e.paid_by,
    'expense_date', e.expense_date,
    'created_by', e.created_by,
    'created_at', e.created_at,
    'updated_at', e.updated_at,
    'installment_series_id', e.installment_series_id,
    'installment_index', e.installment_index,
    'installment_count', e.installment_count,
    'expense_splits', coalesce(
      (
        select jsonb_agg(jsonb_build_object(
          'id', s.id,
          'expense_id', s.expense_id,
          'user_id', s.user_id,
          'split_type', s.split_type,
          'share_amount_minor', s.share_amount_minor
        ))
        from public.expense_splits s where s.expense_id = e.id
      ),
      '[]'::jsonb
    )
  )
  from public.expenses e
  where e.id = p_expense_id;
$$;

create or replace function public.create_installment_expenses(
  p_group_id uuid,
  p_description text,
  p_amount_minor bigint,
  p_currency text,
  p_paid_by uuid,
  p_start_date date,
  p_installment_count int,
  p_start_index int,
  p_participant_ids uuid[]
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_series_id uuid := gen_random_uuid();
  v_base_desc text := trim(p_description);
  v_count int := p_installment_count;
  v_start_index int := p_start_index;
  v_base bigint;
  v_remainder bigint;
  v_index int;
  v_amount_offset int;
  v_date_offset int;
  v_amount bigint;
  v_period date;
  v_last_day date;
  v_day int;
  v_date date;
  v_expense_id uuid;
  v_participants uuid[];
  v_n int;
  v_share_base bigint;
  v_share_rem bigint;
  v_i int;
  v_share bigint;
  v_ids uuid[] := '{}';
  v_start_day int;
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;
  if not public.is_group_member(p_group_id) then
    raise exception 'not a group member';
  end if;
  if (
    select count(*)::int from public.group_members where group_id = p_group_id
  ) < 2 then
    raise exception 'group needs at least two members';
  end if;
  if p_amount_minor is null or p_amount_minor <= 0 then
    raise exception 'amount must be > 0';
  end if;
  if v_count is null or v_count < 2 or v_count > 48 then
    raise exception 'invalid installment count';
  end if;
  if v_start_index is null or v_start_index < 1 or v_start_index > v_count then
    raise exception 'invalid installment start index';
  end if;
  if v_base_desc is null or char_length(v_base_desc) = 0 then
    raise exception 'description must not be blank';
  end if;
  if not exists (
    select 1 from public.group_members
    where group_id = p_group_id and user_id = p_paid_by
  ) then
    raise exception 'payer must be a group member';
  end if;
  if p_participant_ids is null or coalesce(array_length(p_participant_ids, 1), 0) < 1 then
    raise exception 'at least one participant is required';
  end if;

  select array_agg(uid order by uid::text)
  into v_participants
  from (
    select distinct unnest(p_participant_ids) as uid
  ) d;

  if coalesce(array_length(v_participants, 1), 0) <> coalesce(array_length(p_participant_ids, 1), 0) then
    raise exception 'participant IDs must be unique';
  end if;

  v_n := array_length(v_participants, 1);
  v_base := p_amount_minor / v_count;
  v_remainder := p_amount_minor % v_count;
  v_start_day := extract(day from p_start_date)::int;

  for v_index in v_start_index .. v_count loop
    v_amount_offset := v_index - 1;
    v_date_offset := v_index - v_start_index;
    v_amount := v_base + case when v_amount_offset < v_remainder then 1 else 0 end;
    v_period := (date_trunc('month', p_start_date) + make_interval(months => v_date_offset))::date;
    v_last_day := (date_trunc('month', v_period) + interval '1 month - 1 day')::date;
    v_day := least(v_start_day, extract(day from v_last_day)::int);
    v_date := make_date(
      extract(year from v_period)::int,
      extract(month from v_period)::int,
      v_day
    );

    if public.is_group_period_closed(
      p_group_id,
      extract(year from v_date)::int,
      extract(month from v_date)::int
    ) then
      raise exception 'period is closed';
    end if;

    insert into public.expenses (
      group_id,
      description,
      amount_minor,
      currency,
      paid_by,
      expense_date,
      created_by,
      installment_series_id,
      installment_index,
      installment_count
    ) values (
      p_group_id,
      v_base_desc || ' (' || v_index::text || '/' || v_count::text || ')',
      v_amount,
      p_currency,
      p_paid_by,
      v_date,
      auth.uid(),
      v_series_id,
      v_index,
      v_count
    ) returning id into v_expense_id;

    v_share_base := v_amount / v_n;
    v_share_rem := v_amount % v_n;
    for v_i in 1 .. v_n loop
      v_share := v_share_base + case when v_i <= v_share_rem then 1 else 0 end;
      insert into public.expense_splits (
        expense_id, user_id, split_type, share_amount_minor
      ) values (
        v_expense_id, v_participants[v_i], 'EQUAL', v_share
      );
    end loop;

    v_ids := array_append(v_ids, v_expense_id);
  end loop;

  return coalesce(
    (
      select jsonb_agg(public.expense_to_json(eid) order by ordinality)
      from unnest(v_ids) with ordinality as t(eid, ordinality)
    ),
    '[]'::jsonb
  );
end;
$$;

create or replace function public.delete_installment_series(p_series_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_group_id uuid;
  v_row public.expenses;
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;
  if p_series_id is null then
    raise exception 'invalid installment series';
  end if;

  select group_id into v_group_id
  from public.expenses
  where installment_series_id = p_series_id
  limit 1;

  if v_group_id is null then
    raise exception 'installment series not found';
  end if;
  if not public.is_group_member(v_group_id) then
    raise exception 'not a group member';
  end if;
  if not public.is_group_owner(v_group_id) then
    if exists (
      select 1
      from public.expenses e
      where e.installment_series_id = p_series_id
        and e.created_by <> auth.uid()
    ) then
      raise exception 'only creator or owner can delete installment series';
    end if;
  end if;

  for v_row in
    select * from public.expenses where installment_series_id = p_series_id
  loop
    if public.is_group_period_closed(
      v_row.group_id,
      extract(year from v_row.expense_date)::int,
      extract(month from v_row.expense_date)::int
    ) then
      raise exception 'cannot delete installment in closed period';
    end if;
  end loop;

  delete from public.expenses where installment_series_id = p_series_id;
end;
$$;

grant execute on function public.expense_to_json(uuid) to authenticated;
grant execute on function public.create_installment_expenses(
  uuid, text, bigint, text, uuid, date, int, int, uuid[]
) to authenticated;
grant execute on function public.delete_installment_series(uuid) to authenticated;

