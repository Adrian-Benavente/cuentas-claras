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

## Expenses

- Amount must be `> 0`.
- **Creating** a new expense requires the group to have **at least 2 members** (UI + `create_expense` RPC).
- MVP: equal split among **members present when the expense is saved**.
- Splits are persisted on `expense_splits` and are **not** auto-updated when someone joins later.
  Legacy expenses created while alone stay 100% on that member until edited and saved again
  (`update_expense` still allows re-splitting among current members).
- Edit/delete recalculates balances from source expenses (no cached authoritative balances).
