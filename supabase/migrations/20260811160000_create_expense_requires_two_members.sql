-- New expenses require at least two group members (shared-expense product rule).
-- update_expense is intentionally unchanged so legacy solo-created expenses can be re-saved.

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

grant execute on function public.create_expense(uuid, text, bigint, text, uuid, date, jsonb) to authenticated;
