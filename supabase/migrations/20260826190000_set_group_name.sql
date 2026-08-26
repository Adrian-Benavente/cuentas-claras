-- Group owners may rename the group.

create or replace function public.set_group_name(
  p_group_id uuid,
  p_name text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_name text := trim(p_name);
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;
  if not public.is_group_owner(p_group_id) then
    raise exception 'only owner can set group name';
  end if;
  if v_name is null or char_length(v_name) = 0 then
    raise exception 'invalid name';
  end if;

  update public.groups
  set name = v_name,
      updated_at = now()
  where id = p_group_id;
end;
$$;

grant execute on function public.set_group_name(uuid, text) to authenticated;
