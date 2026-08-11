-- Period closures (Etapa E)
-- OPEN by default (no row). CLOSED when a row exists in group_period_closures.
-- Only OWNER can close/reopen. Closed periods block expense and settlement mutations.

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

alter table public.group_period_closures enable row level security;

drop policy if exists group_period_closures_select on public.group_period_closures;
create policy group_period_closures_select on public.group_period_closures
  for select to authenticated
  using (public.is_group_member(group_id));

-- Mutations go through security definer RPCs only.
revoke insert, update, delete on public.group_period_closures from authenticated;
grant select on public.group_period_closures to authenticated;

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

grant execute on function public.is_group_period_closed(uuid, int, int) to authenticated;
grant execute on function public.close_group_period(uuid, int, int) to authenticated;
grant execute on function public.reopen_group_period(uuid, int, int) to authenticated;

-- Enforce on create_expense (preserves ≥2 members rule from 160000)
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

create or replace function public.enforce_settlement_period_open()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_group_id uuid;
  v_year int;
  v_month int;
begin
  if tg_op = 'DELETE' then
    v_group_id := old.group_id;
    v_year := old.period_year;
    v_month := old.period_month;
  else
    v_group_id := new.group_id;
    v_year := new.period_year;
    v_month := new.period_month;
  end if;

  if public.is_group_period_closed(v_group_id, v_year, v_month) then
    raise exception 'period is closed';
  end if;

  if tg_op = 'DELETE' then
    return old;
  end if;
  return new;
end;
$$;

drop trigger if exists trg_settlement_payments_period_closed on public.settlement_payments;
create trigger trg_settlement_payments_period_closed
  before insert or delete on public.settlement_payments
  for each row
  execute function public.enforce_settlement_period_open();
