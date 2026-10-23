# Known Issues and Verification Limits

This file separates verified behavior from deployment risks. Automated tests and screenshot rendering use isolated in-memory H2 databases; they do not connect to the configured user database.

## Verified in the current implementation

- The current full offline Maven run passed 52 tests (0 failures, 0 errors, 0 skipped), including role/service rules, migrations, cash reconciliation, payment/refund linkage, reports, persistence, and UI construction.
- A temporary H2 acceptance run separately verified OWNER, MANAGER, and EMPLOYEE flows, temporary-password enforcement, manager account limits, manager/owner shift review and corrections, employee current-day dashboard access, and employee denial of historical reports and other employees' shifts.
- Closed-shift corrections are append-only, manager/owner-only, idempotent by request key, and audited. They change the adjusted expected amount while preserving the original closeout values.
- The current `mvn -o -s target\maven-settings.xml test` run passed 52 tests (0 failures, 0 errors, 0 skipped). `ScreenshotTool` rendered all 18 screens at 1280×720, 1366×768, 1366×800 and 1920×1080 (72 screenshots) using in-memory H2. The visual pass includes a review of the full 1280×720 screen set and focused POS review at that size. It calls `doClick` for navigation and table selection; it does not submit checkout, cashier, employee, or correction forms. Both headless UI smoke cases passed.
- A closed copy of the default H2 file was migrated through the startup initialization/migration methods. Existing application-table row digests stayed equal; v1/v2 tables, indexes, foreign key, version rows and the singleton drawer row were present. A second startup preserved data without duplicate initialization. The copy and a separate backup remain under `%TEMP%`; the active `data/cafe.mv.db` was never connected to through JDBC or modified.
- The current source packaged successfully with `mvn -o -s target\maven-settings.xml -DskipTests package` after the full test run. Adding `maven-jar-plugin.forceCreation` prevents the already-shaded JAR from being consumed as the next run's input. Shade's remaining `META-INF/MANIFEST.MF` overlap is expected: the configured manifest transformer writes `Main-Class: cafe.App`. The JAR was launched against a new isolated temporary H2 file, initialized the schema, stayed running through an 8-second smoke check, and was then stopped.

## Limitations and unverified behavior

| Description | Impact | Verification / next step |
|---|---|---|
| Real MySQL 8 initialization, migration DDL and concurrency have not been exercised against a MySQL server. | H2 MySQL mode cannot prove MySQL-specific behavior. | Run a disposable-database smoke test before a MySQL deployment. |
| One active cash drawer is modeled per configured database. | Multiple tills with separate physical drawers need a register/drawer identifier before deployment across registers. | Keep one physical drawer for each app database, or extend the schema and ownership rules for multiple registers. |
| Cash corrections adjust reconciliation records, not the physical cash balance or original count. | The original closeout remains historically accurate; corrected expected cash and variance are shown separately. | Use only with a reason and review the audit trail. A cash payout itself must be recorded as a movement while the shift is open. |
| The POS supports full-invoice refunds only and records payment methods locally. | No gateway, split tender or partial refund integration exists. | Add only when the payment provider workflow is defined. |
| UI smoke checks construct screens; screenshot automation clicks module navigation and table selection only, not all operational workflows. Screenshot output uses fixed pixel sizes, not Windows DPI scaling. | Checkout, shift closeout, account management, correction dialogs, and OS scaling can still have issues on a target workstation. | Walk through [`ACCEPTANCE_CHECKLIST.md`](ACCEPTANCE_CHECKLIST.md) on the target workstation and display scaling. |
| Printing uses the operating system's Swing print dialog. | Thermal-printer width and driver behavior are not verified. | Test with the intended printer and receipt width before rollout. |
| Reporting dates use the computer's local timezone. | A store operating across timezone boundaries cannot configure its own business timezone. | Set the workstation timezone consistently; add a store timezone setting if that becomes a requirement. |
| Report grouping materializes paid invoice rows and aggregates in Java. | Large histories may require more memory and query time. | Benchmark with production-sized isolated data before scaling. |
| Login has no rate limit or temporary lockout. | Repeated local login attempts are not throttled. | Introduce a policy if the deployment threat model requires it. |

## Database safety

`Database.initializeIfEmpty` creates schema only for an empty database. Versioned migration steps are additive and designed to be re-runnable after partial DDL completion. Migration v1/v2 were rehearsed on a separate, closed file-level copy of the existing default H2 database; the source file was only copied, and its SHA-256 matched the backup and rehearsal copies before the copies were opened. The active database was not connected to through JDBC or modified. Actual MySQL migration remains unverified. Never use the configured production/user database for integration checks.
