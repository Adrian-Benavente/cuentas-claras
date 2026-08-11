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

## Periods

Derived from `expense.date` as `YearMonth`. Not stored on the expense.

## Expenses

- Amount must be `> 0`.
- MVP: all group members participate with `EQUAL`.
- Edit/delete recalculates from source expenses (no cached authoritative balances).
