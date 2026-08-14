-- Fallback category "Sin categoría" when deleting a category that is in use.

alter table public.expense_categories
  add column if not exists is_uncategorized boolean not null default false;

create unique index if not exists idx_expense_categories_one_uncategorized
  on public.expense_categories (group_id)
  where is_uncategorized;

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
    'updated_at', c.updated_at,
    'is_uncategorized', c.is_uncategorized
  )
  from public.expense_categories c
  where c.id = p_category_id;
$$;

create or replace function public.ensure_uncategorized_category(p_group_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_id uuid;
begin
  select id into v_id
  from public.expense_categories
  where group_id = p_group_id and is_uncategorized
  limit 1;

  if v_id is not null then
    return v_id;
  end if;

  select id into v_id
  from public.expense_categories
  where group_id = p_group_id
    and lower(trim(name)) = lower('Sin categoría')
  limit 1;

  if v_id is not null then
    update public.expense_categories
    set is_uncategorized = true,
        updated_at = now()
    where id = v_id;
    return v_id;
  end if;

  insert into public.expense_categories (
    group_id, name, icon_key, created_by, is_uncategorized
  )
  values (
    p_group_id, 'Sin categoría', 'category', auth.uid(), true
  )
  returning id into v_id;

  return v_id;
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
  if v_row.is_uncategorized then
    raise exception 'cannot edit default category';
  end if;
  if v_row.created_by <> auth.uid()
     and not public.is_group_owner(v_row.group_id) then
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
  v_uncat uuid;
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
  if v_row.is_uncategorized
     or lower(trim(v_row.name)) = lower('Sin categoría') then
    raise exception 'cannot delete default category';
  end if;
  if v_row.created_by <> auth.uid()
     and not public.is_group_owner(v_row.group_id) then
    raise exception 'only creator can delete category';
  end if;

  if exists (
    select 1 from public.expenses where category_id = p_category_id
  ) then
    v_uncat := public.ensure_uncategorized_category(v_row.group_id);
    update public.expenses
    set category_id = v_uncat,
        updated_at = now()
    where category_id = p_category_id;
  end if;

  delete from public.expense_categories where id = p_category_id;
end;
$$;
