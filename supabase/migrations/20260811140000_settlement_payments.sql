-- Settlement payments (Etapa B)
-- Recorded transfers are separate from expense-derived balances.

create table if not exists public.settlement_payments (
  id uuid primary key default gen_random_uuid(),
  group_id uuid not null references public.groups (id) on delete cascade,
  from_user_id uuid not null references public.profiles (id),
  to_user_id uuid not null references public.profiles (id),
  amount_minor bigint not null check (amount_minor > 0),
  currency text not null check (char_length(currency) = 3),
  period_year int not null,
  period_month int not null check (period_month between 1 and 12),
  created_by uuid not null references public.profiles (id),
  created_at timestamptz not null default now(),
  check (from_user_id <> to_user_id)
);

create index if not exists idx_settlement_payments_group_period
  on public.settlement_payments (group_id, period_year, period_month);

alter table public.settlement_payments enable row level security;

drop policy if exists settlement_payments_select on public.settlement_payments;
create policy settlement_payments_select on public.settlement_payments
  for select to authenticated
  using (public.is_group_member(group_id));

drop policy if exists settlement_payments_insert on public.settlement_payments;
create policy settlement_payments_insert on public.settlement_payments
  for insert to authenticated
  with check (
    public.is_group_member(group_id)
    and created_by = auth.uid()
    and exists (
      select 1 from public.group_members gm
      where gm.group_id = settlement_payments.group_id
        and gm.user_id = from_user_id
    )
    and exists (
      select 1 from public.group_members gm
      where gm.group_id = settlement_payments.group_id
        and gm.user_id = to_user_id
    )
  );

drop policy if exists settlement_payments_delete on public.settlement_payments;
create policy settlement_payments_delete on public.settlement_payments
  for delete to authenticated
  using (public.is_group_member(group_id));

grant select, insert, delete on public.settlement_payments to authenticated;
