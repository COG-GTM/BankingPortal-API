# Transaction history: query and index review

Scope: `GET /api/account/transactions` (and `GET /api/account/send-statement`, which reuses the same history query).

## API

`GET /api/account/transactions?page=0&size=20&sort=transactionDate,desc`

- `page` (0-based, default `0`), `size` (default `20`, capped at `100`).
- `sort` may be repeated; allowed properties: `transactionDate`, `amount`, `transactionType`, `id`.
  Unknown properties return `400`. `id desc` is appended as a tie-breaker so pages are stable.
- The body is still a JSON array of `TransactionDTO` (backward compatible shape); paging metadata is returned in
  `X-Total-Count`, `X-Total-Pages`, `X-Page`, `X-Page-Size` headers (exposed via CORS).

## Query counts (per request, measured with Hibernate statistics `getPrepareStatementCount()`)

Measured by `TransactionHistoryQueryCountTests` against MySQL 8.0 (`?size=10`), persistence context cleared before the
request. "Before" is the same test run against `main`, where the endpoint ignores paging params and returns everything.
"Auth" is the 3 statements issued by `JwtAuthenticationFilter`/`TokenService` on every authenticated request
(token lookup, account by id, user by account number) and is unchanged by this work.

| History size (distinct counterparties) | Before | After |
|---|---|---|
| 6 txns (5)   | 9  (3 auth + 1 history + 5 account/user) | 5 (3 auth + 1 id + 1 page; count skipped) |
| 16 txns (15) | 19 (3 auth + 1 history + 15 account/user) | 6 (3 auth + 1 id + 1 page + 1 count) |
| 26 txns (25) | 29 | 6 |
| 31 txns (30) | 34 | 6 |

Before, the count was `4 + D` where `D` = distinct accounts in the history not already in the persistence context:
`Transaction.sourceAccount`/`targetAccount` are `EAGER` `@ManyToOne`, so every distinct `Account` (joined to its `User`)
was fetched with its own `select ... from account join user ... where a.id=?` (N+1), then everything was sorted in memory
and returned in full. After, the count is constant: `select a.id from account where account_number=?`, one page query
using a DTO constructor projection (no entity hydration, no eager associations), and one `count(*)`. Spring Data skips the
count query when the first page is not full, so small histories are 5 statements.

## SQL

Before (derived `findBySourceAccount_AccountNumberOrTargetAccount_AccountNumber`):

```sql
select t.* from transaction t
left join account sa on sa.id = t.source_account_id
left join account ta on ta.id = t.target_account_id
where sa.account_number = ? or ta.account_number = ?          -- no ORDER BY / LIMIT; sorted in Java
-- + one "select account join user where a.id = ?" per distinct account
```

After:

```sql
select a.id from account a where a.account_number = ?         -- const lookup on the unique index

select t.id, t.amount, t.transaction_type, t.transaction_date, sa.account_number, coalesce(ta.account_number, 'N/A')
from transaction t
join account sa on sa.id = t.source_account_id
left join account ta on ta.id = t.target_account_id
where t.source_account_id = ? or t.target_account_id = ?
order by t.transaction_date desc, t.id desc
limit ?, ?

select count(t.id) from transaction t where t.source_account_id = ? or t.target_account_id = ?
```

Filtering on the FK columns (instead of `account_number` through the joins) is what makes the predicate indexable;
an `OR` across two joined tables cannot use any index.

## EXPLAIN ANALYZE (MySQL 8.0, 300k transactions, 2,000 accounts, ~200 txns per account)

| Query | Plan | Rows examined | Time |
|---|---|---|---|
| Before: `OR` on `account_number` via joins | Full table scan of `transaction` + 2 PK lookups per row | 300,000 | ~337 ms |
| Account-number `OR` + `ORDER BY ... LIMIT 20` | Full scan + filesort of 300k rows | 300,000 | ~163 ms |
| After: `OR` on FK columns + `ORDER BY ... LIMIT 20` | `index_merge` union of the two FK indexes, sort 200 rows | 200 | ~0.6 ms |
| After: count | `index_merge` union | 200 | ~0.4 ms |

## Index review

| Table | Index | Status | Used by |
|---|---|---|---|
| `account` | `UNIQUE(account_number)` | existing | account-id lookup (const) |
| `account` | `PRIMARY(id)` | existing | joins to source/target account |
| `transaction` | `FK(source_account_id)` / `FK(target_account_id)` | existing (implicit InnoDB FK indexes) | `index_merge` for filter + count |
| `transaction` | `idx_transaction_source_date (source_account_id, transaction_date)` | **added** | replaces the implicit FK index |
| `transaction` | `idx_transaction_target_date (target_account_id, transaction_date)` | **added** | replaces the implicit FK index |

Notes:

- The existing FK indexes are already sufficient for the new query (see EXPLAIN above); the big win is the query shape.
- The two composite indexes are declared on the `Transaction` entity and are created by `ddl-auto=update`. InnoDB drops
  the implicit FK index once a composite index with the same leading column exists, so there is no net increase in
  index count or write cost. Their value is that each branch can be read in `transaction_date` order: with the current
  `OR` query MySQL still sorts all of an account's rows (cost grows with per-account history), but a
  `UNION ALL` of the two branches, each `ORDER BY transaction_date DESC LIMIT n`, becomes a reverse index range scan
  that reads only `n` rows per branch (measured ~0.15 ms, see "Next steps").
- No index on `transaction_date` alone: every access path filters by account first.
- `transaction_type` and `amount` sorts are allowed but not index-backed; they sort the account's rows, which is bounded
  by the per-account history.

## Next steps

- Keyset (seek) pagination on `(transaction_date, id)` plus a `UNION ALL` query (native or HQL set operation) to keep
  deep pages O(page size) for accounts with very large histories, and to avoid the `count(*)` for infinite-scroll UIs.
- Remove the 3 per-request auth statements (cache the token/user lookup, or put the account id in the JWT so the
  account-number-to-id lookup disappears too).
- Change `Transaction.sourceAccount/targetAccount` to `FetchType.LAZY` so other code paths do not reintroduce N+1.
- Adopt a migration tool (Flyway/Liquibase) instead of relying on `ddl-auto=update` for index DDL.
