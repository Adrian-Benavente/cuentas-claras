# Domain financial rules (contract)

## Money

- Represented as `Money(amountMinor: Long, currency: Currency)`.
- Never use `Float` / `Double` for balances, splits or settlements.
- MVP product currency: ARS (model supports more).

## Equal split

Participants sorted by `userId` ascending:

```text
base = amountMinor / n
rem  = amountMinor % n
share[i] = base + (1 if i < rem else 0)
```

Invariant: `sum(shares) == amountMinor`.

## Balance

```text
balance = amountPaid - amountOwed
```

- `> 0` should receive
- `< 0` should pay
- `= 0` settled

Invariant: `sum(balances) == 0` for a single currency set.

Period summary participants = **current members ∪ anyone in that period's expenses/payments**
(so former members still appear until their historical rows no longer affect the viewed period).

## Settlements (suggested)

Greedy matching of debtors to creditors. Suggested only — not recorded payments.

Applying all suggested transfers must bring balances to zero.

## Settlement payments (recorded)

Recorded transfers (`SettlementPayment`) are separate from expense-derived balances.

- Member rows in the period summary (`Pagó` / `Le corresponde` / `Saldo`) ignore payments.
- Suggested “Para saldar” uses balances after applying payments of that `YearMonth`:
  - payment `from → to` of `X`: `balance(from) += X`, `balance(to) -= X`.
- Marking a suggestion as settled inserts a payment for the full suggested amount in the viewed period.
- Undoing deletes that payment row.

## Periods

Derived from `expense.date` as `YearMonth`. Not stored on the expense.
Settlement payments store `period_year` / `period_month` explicitly.

### Open / closed

- Default status is **OPEN** (no row).
- **CLOSED** only when a row exists in `group_period_closures` for `(group_id, year, month)`.
- Only the group **OWNER** can close or reopen a period.
- Closing is allowed even if suggested settlements remain pending.
- While **CLOSED**, mutations are blocked for that month:
  - create / update / delete expenses whose `expense_date` falls in the period
  - create / delete settlement payments for that period
- Reopening restores those mutations.
- Balances keep being calculated from live expenses; closing freezes mutations, not a balance snapshot.

## Offline (read cache)

- The app may show previously loaded groups, members, expenses, payments and period closures from a local Room cache when there is no network.
- Cache is **read-only**: create/edit/delete, settlements, close/reopen, join and invite actions require connectivity.
- Cached balances are never authoritative; Resumen recomputes from cached expenses via the same domain calculators.
- Logout clears the local cache.

## Expenses

- Amount must be `> 0`.
- **Creating** a new expense requires the group to have **at least 2 members** (UI + `create_expense` RPC).
- MVP: equal split among **members present when the expense is saved**.
- Splits are persisted on `expense_splits` and are **not** auto-updated when someone joins later.
  Legacy expenses created while alone stay 100% on that member until edited and saved again
  (`update_expense` still allows re-splitting among current members).
- Edit/delete recalculates balances from source expenses (no cached authoritative balances).

### Installments (cuotas)

- Create with **total** original amount + **N** cuotas (`2 ≤ N ≤ 48`) and start index **K** (`1 ≤ K ≤ N`) via `create_installment_expenses`.
- Materializes remaining cuotas `K/N` … `N/N` from the chosen date (date of cuota K); does not invent past cuotas `1..K−1`.
- Total is split across the full N plan with the same remainder rule as equal split (`base = total/N`, first `total%N` get +1); only K..N rows are inserted.
- Each cuota is a normal expense in its month (description ends with `(k/N)`), linked by `installment_series_id`.
- Dates keep the start day-of-month, clamped to each month's length.
- If any target month is **CLOSED**, the whole create fails.
- Deleting the series fails if any cuota sits in a closed period; otherwise deletes all cuotas.
- Editing a single cuota is allowed like a normal expense (MVP: no “edit whole series”).

## Membership changes

- OWNER may remove a MEMBER via `remove_group_member` (not self, not OWNER).
- Removing a member does **not** delete or re-split their past expenses.
- After removal they disappear from Miembros but remain in period Resumen if still involved in that period's expenses/payments.
