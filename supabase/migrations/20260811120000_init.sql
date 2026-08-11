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
  updated_at timestamptz not null default now()
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
