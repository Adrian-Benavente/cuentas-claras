-- OWNER can remove MEMBER users from a group. Historical expenses/payments are kept.

create or replace function public.remove_group_member(p_group_id uuid, p_user_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_role text;
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;

  if not public.is_group_owner(p_group_id) then
    raise exception 'only owner can remove members';
  end if;

  if p_user_id = auth.uid() then
    raise exception 'cannot remove yourself';
  end if;

  select role into v_role
  from public.group_members
  where group_id = p_group_id and user_id = p_user_id;

  if v_role is null then
    raise exception 'member not found';
  end if;

  if v_role = 'OWNER' then
    raise exception 'cannot remove owner';
  end if;

  delete from public.group_members
  where group_id = p_group_id and user_id = p_user_id;
end;
$$;

grant execute on function public.remove_group_member(uuid, uuid) to authenticated;

-- Allow reading profiles of people who appear in your groups' financial history
-- (needed after a member is removed but expenses/payments still reference them).
drop policy if exists profiles_select on public.profiles;
create policy profiles_select on public.profiles
  for select to authenticated
  using (
    id = auth.uid()
    or exists (
      select 1
      from public.group_members me
      join public.group_members other on me.group_id = other.group_id
      where me.user_id = auth.uid() and other.user_id = profiles.id
    )
    or exists (
      select 1
      from public.group_members me
      join public.expenses e on e.group_id = me.group_id
      where me.user_id = auth.uid()
        and e.paid_by = profiles.id
    )
    or exists (
      select 1
      from public.group_members me
      join public.expenses e on e.group_id = me.group_id
      join public.expense_splits s on s.expense_id = e.id
      where me.user_id = auth.uid()
        and s.user_id = profiles.id
    )
    or exists (
      select 1
      from public.group_members me
      join public.settlement_payments sp on sp.group_id = me.group_id
      where me.user_id = auth.uid()
        and (
          sp.from_user_id = profiles.id
          or sp.to_user_id = profiles.id
          or sp.created_by = profiles.id
        )
    )
  );
