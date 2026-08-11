-- Allow creating installment series starting at cuota K of N (remaining only).

drop function if exists public.create_installment_expenses(
  uuid, text, bigint, text, uuid, date, int, uuid[]
);

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

grant execute on function public.create_installment_expenses(
  uuid, text, bigint, text, uuid, date, int, int, uuid[]
) to authenticated;
