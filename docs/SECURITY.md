# Authorization rules (MVP)

Authority lives in PostgreSQL RLS and `security definer` RPCs.
The Android app must never be the only access control.

## Membership

A user is a member of a group if a row exists in `group_members` for `(group_id, auth.uid())`.

Helper: `is_group_member(group_id)`, `is_group_owner(group_id)`.

Profiles of current co-members are readable. After someone is removed, their profile remains readable to remaining members if they still appear in that group's expenses, splits, or settlement payments.

## Operations

| Action | Who | Enforcement |
|--------|-----|-------------|
| Read group | Member | RLS `groups_select_member` |
| Read members | Member | RLS `members_select` |
| Read expenses / splits | Member | RLS on `expenses` / `expense_splits` |
| Create group | Authenticated | RPC `create_group` → OWNER membership + invite code |
| Join group | Authenticated | RPC `join_group_by_code(p_invite_code)` |
| Remove member | OWNER | RPC `remove_group_member` (MEMBER only; not self) |
| Rotate invite code | OWNER | RPC `rotate_invite_code` |
| Create expense | Member | RPC `create_expense` (≥2 members; payer must be member; splits must sum; period open) |
| Update expense | Creator or OWNER | RPC `update_expense` (period open for old and new dates) |
| Delete expense | Creator or OWNER | RLS `expenses_delete` + period-open trigger |
| Read settlement payments | Member | RLS `settlement_payments_select` |
| Record settlement payment | Member (`created_by = auth.uid()`) | RLS `settlement_payments_insert` + period-open trigger |
| Delete settlement payment | Member | RLS `settlement_payments_delete` + period-open trigger |
| Close / reopen period | OWNER | RPC `close_group_period` / `reopen_group_period` |
| Read period closures | Member | RLS `group_period_closures_select` |
| Set / clear group avatar | OWNER | Storage `group-avatars` + RPC `set_group_avatar` / `clear_group_avatar` |
| Read group avatar object | Member | Storage policy `group_avatars_select` (bucket also public for CDN URL) |
| Read/update own profile | Self | RLS on `profiles` |
| Read other profiles | Shared group membership | RLS `profiles_select` |

## Closed periods

A period is **OPEN** unless a row exists in `group_period_closures` for `(group_id, year, month)`.

When closed, the server rejects:

- `create_expense` / `update_expense` if the expense date (old or new) falls in that month
- DELETE on `expenses` for expenses in that month (trigger)
- INSERT / DELETE on `settlement_payments` for that month (trigger)

Stable exception message: `period is closed`.

## Invite codes

- 6 characters from a non-ambiguous alphabet
- Unique; generated server-side
- Not derived from group UUID
- Join is atomic via RPC

## Client secrets

Ship only:

- Supabase URL
- Supabase **anon** key
- Google Web Client ID

Never ship the service-role key.
