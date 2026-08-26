-- When an installment date changes, rebase every cuota in the series.

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
  v_shift_series boolean := false;
  v_anchor_index int;
  v_start_day int;
  v_sibling public.expenses;
  v_date_offset int;
  v_period date;
  v_last_day date;
  v_day int;
  v_new_date date;
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

  v_shift_series := v_expense.installment_series_id is not null
    and v_expense.installment_index is not null
    and p_expense_date is distinct from v_expense.expense_date;

  if v_shift_series then
    v_anchor_index := v_expense.installment_index;
    v_start_day := extract(day from p_expense_date)::int;
    for v_sibling in
      select * from public.expenses
      where installment_series_id = v_expense.installment_series_id
        and installment_index is not null
    loop
      v_date_offset := v_sibling.installment_index - v_anchor_index;
      v_period := (date_trunc('month', p_expense_date)
        + make_interval(months => v_date_offset))::date;
      v_last_day := (date_trunc('month', v_period) + interval '1 month - 1 day')::date;
      v_day := least(v_start_day, extract(day from v_last_day)::int);
      v_new_date := make_date(
        extract(year from v_period)::int,
        extract(month from v_period)::int,
        v_day
      );
      if public.is_group_period_closed(
        v_expense.group_id,
        extract(year from v_sibling.expense_date)::int,
        extract(month from v_sibling.expense_date)::int
      ) or public.is_group_period_closed(
        v_expense.group_id,
        extract(year from v_new_date)::int,
        extract(month from v_new_date)::int
      ) then
        raise exception 'period is closed';
      end if;
    end loop;
  elsif public.is_group_period_closed(
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

  if v_shift_series then
    v_anchor_index := v_expense.installment_index;
    v_start_day := extract(day from p_expense_date)::int;
    for v_sibling in
      select * from public.expenses
      where installment_series_id = v_expense.installment_series_id
        and id <> p_expense_id
        and installment_index is not null
    loop
      v_date_offset := v_sibling.installment_index - v_anchor_index;
      v_period := (date_trunc('month', p_expense_date)
        + make_interval(months => v_date_offset))::date;
      v_last_day := (date_trunc('month', v_period) + interval '1 month - 1 day')::date;
      v_day := least(v_start_day, extract(day from v_last_day)::int);
      v_new_date := make_date(
        extract(year from v_period)::int,
        extract(month from v_period)::int,
        v_day
      );
      update public.expenses
      set expense_date = v_new_date,
          updated_at = now()
      where id = v_sibling.id;
    end loop;
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
