# Frontend boundary tests

These tests run in Vitest's Node environment. Storage is an in-memory standards-shaped fake; Axios adapters simulate server results and errors. No real account, browser session or backend service is modified.

## Coverage

- Session lifetime, token-only persistence, invalid credential rejection, storage-denied fallback, explicit logout and stale-request protection. Expiration during an in-flight request still notifies once.
- Safe internal redirects; external URLs, protocol-relative URLs, backslashes, controls and authentication loops are rejected.
- Axios base URL, Bearer authorization, public login requests and `Result.data` unwrapping.
- Current versus stale HTTP 401, business conflicts, forbidden responses, network failures and silent cancellations.
- Binary image success and JSON error parsing for Blob responses.
- User store role/profile trust, in-flight request deduplication, prevention of stale profile/logout updates, and retry after network failures without discarding an issued token.

Run using the project's test script from `E:/codex/lab-frontend`. These are deterministic automated boundary tests, not evidence of a real browser/backend integration run.
