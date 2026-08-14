-- Allow the Material Sports Esports (gamepad) icon for categories.

alter table public.expense_categories
  drop constraint if exists expense_categories_icon_key_check;

alter table public.expense_categories
  add constraint expense_categories_icon_key_check check (icon_key in (
    'bolt', 'water_drop', 'local_gas_station', 'wifi', 'credit_card', 'restaurant',
    'directions_car', 'home', 'shopping_cart', 'medical_services', 'phone', 'school',
    'pets', 'fitness_center', 'movie', 'sports_esports', 'flight', 'local_cafe',
    'local_grocery_store', 'cleaning_services', 'build', 'child_care', 'attach_money',
    'receipt_long', 'category'
  ));

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
    'pets', 'fitness_center', 'movie', 'sports_esports', 'flight', 'local_cafe',
    'local_grocery_store', 'cleaning_services', 'build', 'child_care', 'attach_money',
    'receipt_long', 'category'
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
    'pets', 'fitness_center', 'movie', 'sports_esports', 'flight', 'local_cafe',
    'local_grocery_store', 'cleaning_services', 'build', 'child_care', 'attach_money',
    'receipt_long', 'category'
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
