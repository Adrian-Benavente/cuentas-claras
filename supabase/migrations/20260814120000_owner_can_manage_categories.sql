-- Group owners may update or delete any category in the group.
-- Other members remain limited to categories they created.

drop policy if exists expense_categories_update on public.expense_categories;
create policy expense_categories_update on public.expense_categories
  for update to authenticated
  using (
    public.is_group_member(group_id)
    and (created_by = auth.uid() or public.is_group_owner(group_id))
  )
  with check (
    public.is_group_member(group_id)
    and (created_by = auth.uid() or public.is_group_owner(group_id))
  );

drop policy if exists expense_categories_delete on public.expense_categories;
create policy expense_categories_delete on public.expense_categories
  for delete to authenticated
  using (
    public.is_group_member(group_id)
    and (created_by = auth.uid() or public.is_group_owner(group_id))
  );

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
  if v_row.created_by <> auth.uid()
     and not public.is_group_owner(v_row.group_id) then
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
