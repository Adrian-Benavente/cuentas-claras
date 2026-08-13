-- FCM device tokens + one push job per create/edit action (not per installment row).

create table if not exists public.device_push_tokens (
  token text primary key,
  user_id uuid not null references public.profiles (id) on delete cascade,
  updated_at timestamptz not null default now()
);

create index if not exists idx_device_push_tokens_user
  on public.device_push_tokens (user_id);

alter table public.device_push_tokens enable row level security;

drop policy if exists device_push_tokens_select on public.device_push_tokens;
create policy device_push_tokens_select on public.device_push_tokens
  for select to authenticated
  using (user_id = auth.uid());

drop policy if exists device_push_tokens_insert on public.device_push_tokens;
create policy device_push_tokens_insert on public.device_push_tokens
  for insert to authenticated
  with check (user_id = auth.uid());

drop policy if exists device_push_tokens_update on public.device_push_tokens;
create policy device_push_tokens_update on public.device_push_tokens
  for update to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

drop policy if exists device_push_tokens_delete on public.device_push_tokens;
create policy device_push_tokens_delete on public.device_push_tokens
  for delete to authenticated
  using (user_id = auth.uid());

grant select, insert, update, delete on public.device_push_tokens to authenticated;

create table if not exists public.push_jobs (
  id uuid primary key default gen_random_uuid(),
  event text not null check (event in ('expense_created', 'expense_updated')),
  group_id uuid not null references public.groups (id) on delete cascade,
  expense_id uuid not null references public.expenses (id) on delete cascade,
  actor_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now()
);

create index if not exists idx_push_jobs_created_at
  on public.push_jobs (created_at desc);

alter table public.push_jobs enable row level security;

revoke all on public.push_jobs from anon, authenticated;
grant all on public.push_jobs to service_role;

create or replace function public.enqueue_expense_push(
  p_event text,
  p_group_id uuid,
  p_expense_id uuid,
  p_actor_id uuid
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if p_event is null or p_group_id is null or p_expense_id is null or p_actor_id is null then
    return;
  end if;
  insert into public.push_jobs (event, group_id, expense_id, actor_id)
  values (p_event, p_group_id, p_expense_id, p_actor_id);
end;
$$;

revoke all on function public.enqueue_expense_push(text, uuid, uuid, uuid) from public, anon, authenticated;

create or replace function public.create_expense(
  p_group_id uuid,
  p_description text,
  p_amount_minor bigint,
  p_currency text,
  p_paid_by uuid,
  p_expense_date date,
  p_splits jsonb,
  p_category_id uuid
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
  v_note text := coalesce(trim(p_description), '');
  v_category public.expense_categories;
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
  if p_category_id is null then
    raise exception 'category is required';
  end if;
  select * into v_category
  from public.expense_categories
  where id = p_category_id and group_id = p_group_id;
  if v_category.id is null then
    raise exception 'category not found';
  end if;

  insert into public.expenses (
    group_id, description, amount_minor, currency, paid_by, expense_date, created_by, category_id
  ) values (
    p_group_id, v_note, p_amount_minor, p_currency, p_paid_by, p_expense_date, auth.uid(), p_category_id
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

  perform public.enqueue_expense_push(
    'expense_created',
    v_expense.group_id,
    v_expense.id,
    auth.uid()
  );

  return public.expense_to_json(v_expense.id);
end;
$$;

create or replace function public.update_expense(
  p_expense_id uuid,
  p_description text,
  p_amount_minor bigint,
  p_currency text,
  p_paid_by uuid,
  p_expense_date date,
  p_splits jsonb,
  p_category_id uuid
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
  v_note text := coalesce(trim(p_description), '');
  v_category public.expense_categories;
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
  if p_category_id is null then
    raise exception 'category is required';
  end if;
  select * into v_category
  from public.expense_categories
  where id = p_category_id and group_id = v_expense.group_id;
  if v_category.id is null then
    raise exception 'category not found';
  end if;

  update public.expenses
  set description = v_note,
      amount_minor = p_amount_minor,
      currency = p_currency,
      paid_by = p_paid_by,
      expense_date = p_expense_date,
      category_id = p_category_id,
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

  perform public.enqueue_expense_push(
    'expense_updated',
    v_expense.group_id,
    v_expense.id,
    auth.uid()
  );

  return public.expense_to_json(v_expense.id);
end;
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
  p_participant_ids uuid[],
  p_category_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_series_id uuid := gen_random_uuid();
  v_note text := coalesce(trim(p_description), '');
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
  v_category public.expense_categories;
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
  if p_category_id is null then
    raise exception 'category is required';
  end if;
  select * into v_category
  from public.expense_categories
  where id = p_category_id and group_id = p_group_id;
  if v_category.id is null then
    raise exception 'category not found';
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
      installment_count,
      category_id
    ) values (
      p_group_id,
      v_note,
      v_amount,
      p_currency,
      p_paid_by,
      v_date,
      auth.uid(),
      v_series_id,
      v_index,
      v_count,
      p_category_id
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

  if coalesce(array_length(v_ids, 1), 0) > 0 then
    perform public.enqueue_expense_push(
      'expense_created',
      p_group_id,
      v_ids[1],
      auth.uid()
    );
  end if;

  return coalesce(
    (
      select jsonb_agg(public.expense_to_json(eid) order by ordinality)
      from unnest(v_ids) with ordinality as t(eid, ordinality)
    ),
    '[]'::jsonb
  );
end;
$$;
