-- Group avatars (OWNER uploads to Storage; URL stored on groups.avatar_url)

alter table public.groups
  add column if not exists avatar_url text;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'group-avatars',
  'group-avatars',
  true,
  2097152,
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

-- Path: {group_id}/avatar.jpg  → foldername[1] = group_id
drop policy if exists group_avatars_select on storage.objects;
create policy group_avatars_select on storage.objects
  for select to authenticated
  using (
    bucket_id = 'group-avatars'
    and public.is_group_member((storage.foldername(name))[1]::uuid)
  );

drop policy if exists group_avatars_insert on storage.objects;
create policy group_avatars_insert on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'group-avatars'
    and public.is_group_owner((storage.foldername(name))[1]::uuid)
  );

drop policy if exists group_avatars_update on storage.objects;
create policy group_avatars_update on storage.objects
  for update to authenticated
  using (
    bucket_id = 'group-avatars'
    and public.is_group_owner((storage.foldername(name))[1]::uuid)
  )
  with check (
    bucket_id = 'group-avatars'
    and public.is_group_owner((storage.foldername(name))[1]::uuid)
  );

drop policy if exists group_avatars_delete on storage.objects;
create policy group_avatars_delete on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'group-avatars'
    and public.is_group_owner((storage.foldername(name))[1]::uuid)
  );

create or replace function public.set_group_avatar(
  p_group_id uuid,
  p_avatar_url text
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
    raise exception 'only owner can set group avatar';
  end if;
  if p_avatar_url is null or length(trim(p_avatar_url)) = 0 then
    raise exception 'avatar url required';
  end if;

  update public.groups
  set avatar_url = trim(p_avatar_url),
      updated_at = now()
  where id = p_group_id;
end;
$$;

create or replace function public.clear_group_avatar(p_group_id uuid)
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
    raise exception 'only owner can clear group avatar';
  end if;

  update public.groups
  set avatar_url = null,
      updated_at = now()
  where id = p_group_id;
end;
$$;

grant execute on function public.set_group_avatar(uuid, text) to authenticated;
grant execute on function public.clear_group_avatar(uuid) to authenticated;
