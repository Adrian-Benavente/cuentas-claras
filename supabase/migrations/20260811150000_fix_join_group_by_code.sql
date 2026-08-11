-- Fix join_group_by_code: parameter name collided with column invite_code.
-- In PL/pgSQL SQL statements, column names take precedence over parameters,
-- so `where ... = upper(trim(invite_code))` compared the column to itself.

drop function if exists public.join_group_by_code(text);

create or replace function public.join_group_by_code(p_invite_code text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_group public.groups;
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;

  if p_invite_code is null or length(trim(p_invite_code)) = 0 then
    raise exception 'invalid invite code';
  end if;

  select * into v_group
  from public.groups g
  where g.invite_code = upper(trim(p_invite_code));

  if not found then
    raise exception 'invalid invite code';
  end if;

  insert into public.group_members (group_id, user_id, role)
  values (v_group.id, auth.uid(), 'MEMBER')
  on conflict do nothing;

  return jsonb_build_object('group_id', v_group.id);
end;
$$;

grant execute on function public.join_group_by_code(text) to authenticated;
