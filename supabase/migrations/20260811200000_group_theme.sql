-- Group color theme (OWNER picks from predefined palettes)

alter table public.groups
  add column if not exists theme_id text not null default 'forest';

alter table public.groups
  drop constraint if exists groups_theme_id_check;

alter table public.groups
  add constraint groups_theme_id_check
  check (theme_id in ('forest', 'ocean', 'sunset', 'slate', 'orchid'));

create or replace function public.set_group_theme(
  p_group_id uuid,
  p_theme_id text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;
  if not public.is_group_owner(p_group_id) then
    raise exception 'only owner can set group theme';
  end if;
  if p_theme_id is null or p_theme_id not in ('forest', 'ocean', 'sunset', 'slate', 'orchid') then
    raise exception 'invalid theme';
  end if;

  update public.groups
  set theme_id = p_theme_id,
      updated_at = now()
  where id = p_group_id;
end;
$$;

grant execute on function public.set_group_theme(uuid, text) to authenticated;
