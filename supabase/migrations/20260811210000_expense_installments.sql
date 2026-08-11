-- Expense installments (cuotas): series of monthly expenses from total + N.

alter table public.expenses
  add column if not exists installment_series_id uuid null,
  add column if not exists installment_index int null,
  add column if not exists installment_count int null;

alter table public.expenses
  drop constraint if exists expenses_installment_fields_check;

alter table public.expenses
  add constraint expenses_installment_fields_check check (
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
  );

create index if not exists idx_expenses_group_installment_series
  on public.expenses (group_id, installment_series_id);

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
  v_base bigint;
  v_remainder bigint;
  v_offset int;
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

  for v_offset in 0 .. (v_count - 1) loop
    v_amount := v_base + case when v_offset < v_remainder then 1 else 0 end;
    v_period := (date_trunc('month', p_start_date) + make_interval(months => v_offset))::date;
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
      v_base_desc || ' (' || (v_offset + 1)::text || '/' || v_count::text || ')',
      v_amount,
      p_currency,
      p_paid_by,
      v_date,
      auth.uid(),
      v_series_id,
      v_offset + 1,
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
  uuid, text, bigint, text, uuid, date, int, uuid[]
) to authenticated;
grant execute on function public.delete_installment_series(uuid) to authenticated;
