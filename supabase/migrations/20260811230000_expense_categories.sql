-- Per-group expense categories with curated icon keys.

create table if not exists public.expense_categories (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.groups (id) on delete cascade,
  name text not null check (char_length(trim(name)) > 0 and char_length(trim(name)) <= 40),
  icon_key text not null check (icon_key in (
    'bolt', 'water_drop', 'local_gas_station', 'wifi', 'credit_card', 'restaurant',
    'directions_car', 'home', 'shopping_cart', 'medical_services', 'phone', 'school',
    'pets', 'fitness_center', 'movie', 'flight', 'local_cafe', 'local_grocery_store',
    'cleaning_services', 'build', 'child_care', 'attach_money', 'receipt_long', 'category'
  )),
  created_by uuid not null references public.profiles (id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists idx_expense_categories_group_name_lower
  on public.expense_categories (group_id, lower(trim(name)));

create index if not exists idx_expense_categories_group
  on public.expense_categories (group_id);

alter table public.expenses
  add column if not exists category_id uuid null
    references public.expense_categories (id) on delete restrict;

alter table public.expenses drop constraint if exists expenses_description_check;
alter table public.expenses drop constraint if exists expenses_description_or_category_check;

alter table public.expenses
  add constraint expenses_description_or_category_check check (
    char_length(trim(description)) > 0
    or category_id is not null
  );

alter table public.expense_categories enable row level security;

drop policy if exists expense_categories_select on public.expense_categories;
create policy expense_categories_select on public.expense_categories
  for select to authenticated
  using (public.is_group_member(group_id));

drop policy if exists expense_categories_insert on public.expense_categories;
create policy expense_categories_insert on public.expense_categories
  for insert to authenticated
  with check (
    public.is_group_member(group_id)
    and created_by = auth.uid()
  );

drop policy if exists expense_categories_update on public.expense_categories;
create policy expense_categories_update on public.expense_categories
  for update to authenticated
  using (created_by = auth.uid() and public.is_group_member(group_id))
  with check (created_by = auth.uid() and public.is_group_member(group_id));

drop policy if exists expense_categories_delete on public.expense_categories;
create policy expense_categories_delete on public.expense_categories
  for delete to authenticated
  using (created_by = auth.uid() and public.is_group_member(group_id));

grant select, insert, update, delete on public.expense_categories to authenticated;

create or replace function public.expense_category_to_json(p_category_id uuid)
returns jsonb
language sql
stable
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'id', c.id,
    'group_id', c.group_id,
    'name', c.name,
    'icon_key', c.icon_key,
    'created_by', c.created_by,
    'created_at', c.created_at,
    'updated_at', c.updated_at
  )
  from public.expense_categories c
  where c.id = p_category_id;
$$;

create or replace function public.create_expense_category(
  p_group_id uuid,
  p_name text,
  p_icon_key text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_name text := trim(p_name);
  v_icon text := lower(trim(p_icon_key));
  v_id uuid;
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;
  if not public.is_group_member(p_group_id) then
    raise exception 'not a group member';
  end if;
  if v_name is null or char_length(v_name) = 0 or char_length(v_name) > 40 then
    raise exception 'invalid category name';
  end if;
  if v_icon is null or v_icon not in (
    'bolt', 'water_drop', 'local_gas_station', 'wifi', 'credit_card', 'restaurant',
    'directions_car', 'home', 'shopping_cart', 'medical_services', 'phone', 'school',
    'pets', 'fitness_center', 'movie', 'flight', 'local_cafe', 'local_grocery_store',
    'cleaning_services', 'build', 'child_care', 'attach_money', 'receipt_long', 'category'
  ) then
    raise exception 'invalid category icon';
  end if;
  if exists (
    select 1 from public.expense_categories
    where group_id = p_group_id and lower(trim(name)) = lower(v_name)
  ) then
    raise exception 'category name already exists';
  end if;

  insert into public.expense_categories (group_id, name, icon_key, created_by)
  values (p_group_id, v_name, v_icon, auth.uid())
  returning id into v_id;

  return public.expense_category_to_json(v_id);
end;
$$;

create or replace function public.update_expense_category(
  p_category_id uuid,
  p_name text,
  p_icon_key text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_row public.expense_categories;
  v_name text := trim(p_name);
  v_icon text := lower(trim(p_icon_key));
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;

  select * into v_row from public.expense_categories where id = p_category_id;
  if v_row.id is null then
    raise exception 'category not found';
  end if;
  if not public.is_group_member(v_row.group_id) then
    raise exception 'not a group member';
  end if;
  if v_row.created_by <> auth.uid() then
    raise exception 'only creator can edit category';
  end if;
  if v_name is null or char_length(v_name) = 0 or char_length(v_name) > 40 then
    raise exception 'invalid category name';
  end if;
  if v_icon is null or v_icon not in (
    'bolt', 'water_drop', 'local_gas_station', 'wifi', 'credit_card', 'restaurant',
    'directions_car', 'home', 'shopping_cart', 'medical_services', 'phone', 'school',
    'pets', 'fitness_center', 'movie', 'flight', 'local_cafe', 'local_grocery_store',
    'cleaning_services', 'build', 'child_care', 'attach_money', 'receipt_long', 'category'
  ) then
    raise exception 'invalid category icon';
  end if;
  if exists (
    select 1 from public.expense_categories
    where group_id = v_row.group_id
      and lower(trim(name)) = lower(v_name)
      and id <> p_category_id
  ) then
    raise exception 'category name already exists';
  end if;

  update public.expense_categories
  set name = v_name,
      icon_key = v_icon,
      updated_at = now()
  where id = p_category_id;

  return public.expense_category_to_json(p_category_id);
end;
$$;

create or replace function public.delete_expense_category(p_category_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_row public.expense_categories;
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;

  select * into v_row from public.expense_categories where id = p_category_id;
  if v_row.id is null then
    raise exception 'category not found';
  end if;
  if not public.is_group_member(v_row.group_id) then
    raise exception 'not a group member';
  end if;
  if v_row.created_by <> auth.uid() then
    raise exception 'only creator can delete category';
  end if;
  if exists (
    select 1 from public.expenses where category_id = p_category_id
  ) then
    raise exception 'category is in use';
  end if;

  delete from public.expense_categories where id = p_category_id;
end;
$$;

grant execute on function public.expense_category_to_json(uuid) to authenticated;
grant execute on function public.create_expense_category(uuid, text, text) to authenticated;
grant execute on function public.update_expense_category(uuid, text, text) to authenticated;
grant execute on function public.delete_expense_category(uuid) to authenticated;

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
    'category_id', e.category_id,
    'category_name', c.name,
    'category_icon_key', c.icon_key,
    'expense_splits', coalesce(
      (
        select jsonb_agg(jsonb_build_object(
          'id', s.id,
          'expense_id', s.expense_id,
          'user_id', s.user_id,
          'split_type', s.split_type,
          'share_amount_minor', s.share_amount_minor
        ) order by s.user_id::text)
        from public.expense_splits s where s.expense_id = e.id
      ),
      '[]'::jsonb
    )
  )
  from public.expenses e
  left join public.expense_categories c on c.id = e.category_id
  where e.id = p_expense_id;
$$;

drop function if exists public.create_expense(
  uuid, text, bigint, text, uuid, date, jsonb
);

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

  return public.expense_to_json(v_expense.id);
end;
$$;

drop function if exists public.update_expense(
  uuid, text, bigint, text, uuid, date, jsonb
);

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

  return public.expense_to_json(v_expense.id);
end;
$$;

grant execute on function public.create_expense(
  uuid, text, bigint, text, uuid, date, jsonb, uuid
) to authenticated;
grant execute on function public.update_expense(
  uuid, text, bigint, text, uuid, date, jsonb, uuid
) to authenticated;

drop function if exists public.create_installment_expenses(
  uuid, text, bigint, text, uuid, date, int, int, uuid[]
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
  uuid, text, bigint, text, uuid, date, int, int, uuid[], uuid
) to authenticated;
