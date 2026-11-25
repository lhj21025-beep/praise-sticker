# Web / native feature parity checklist

Baseline: web commit d3cf54617442a558bc94944efccdb8cc2683fc76.

| Web function / area | Native implementation | Status |
| --- | --- | --- |
| Parent/child login, password change, remembered role | MainActivity, SecureSession, Store.login/password | Implemented; native session encrypted |
| Score, total points, 37 titles, streak, best praise | Store.load/title, home/levels | Implemented |
| 12 themes + gift thresholds + goal overlay | BoardView, themes/luckyEditor | Implemented as native drawings; visual details differ |
| Praise, penalty, special mission, five-point bonuses | Store.stamp | Implemented with stable operation IDs |
| Recovery, undo, log edit/delete | Store.recover/undo/editLog | Implemented |
| Board target/reward, new round, history | settings/history, Store.newBoard | Implemented |
| Automatic completion and reward deduplication | Store.complete | Implemented; rollover not a completion reward |
| Reason list add/edit/delete/order | reasons multiline editor | Implemented; native editing form differs |
| Lottery thresholds and probability editor | luckyList/luckyEditor, Store.prizes | Implemented |
| Roulette animation, outcome, notification | WheelView, Store.win | Implemented with atomic outcome save |
| Retry reward -> new ticket, original snapshot | Store.use | Implemented with shared retry_<sourceId> identity |
| Reward box / random box, used/unused, partial use | items/useItem, Store.use | Implemented |
| Weekend unused time entry and split usage | rollover, Store.rollover | Implemented; validation before atomic changes |
| Child praise requests, approval/dismissal | requestForm/requests, Store.stamp/patch | Implemented |
| Wish creation, approval, postponement, pending delete | wishes/wishReply | Implemented |
| Random-box sticker reward approval | Store.acknowledgeNotification / web confirmNotification | Parent grants exact count once to active board; shared source marker and log ID |
| Parent game-time redemption + child notice | parentUseGame / parentUseGameForm; item-backed child notices | Implemented; partial/full use, atomic notice, child acknowledgement. Pending release validation/signing. |
| In-app parent notifications | showNotifications | Implemented; not background push |
| Statistics: positive/negative counts, top 5, weekdays | report | Implemented with native bars |
| Completion durations and historical backfill | history, Store.backfill | Implemented |
| Streak bonus repair | Store.repairBonuses | Implemented |
| CSV export and image sharing | export/shareCard, ShareProvider | Implemented with Android storage/share UI |
| Sync | shared Firestore | App foreground refresh; web manual refresh |

Future change policy: check BOTH clients, preserve shared field meanings, add regression coverage for affected behavior, update this table, and build/sign a new APK as well as deploying web changes. Never claim one deployment updates the other client's executable.

Known verification limit: these feature implementations are compiled and key data paths fixture-tested; the complete UI has not been exercised on an Android device in this environment.

Parent game notices live on the used item (`usedByRole`, `parentUseNoticeStatus`, `parentUseNoticeText`, `parentUseNoticeConfirmedAt`) so older parents cannot accidentally dismiss child notices. Both clients use `parent-remainder-{sourceId}` and validate the fresh item's balance. Existing rollover dates and obtained dates are preserved. Child notices appear at login/reentry, remain pending until acknowledged, and do not modify rewards when acknowledged. They are in-app notices, not background push.

1.3.0: Parent redemption supports non-game prizes (whole item) and optional useReason, visible in used history and child notices. Retry rewards exchange tickets; sticker rewards retain parent confirmation before stamps. Roulette tickets retain the child draw flow. Game partial use remains supported.

1.3.1: Both used inventory tabs sort by actual usedDate descending (missing dates last). Inventory title shows combined unused game minutes across both boxes as HH시간MM분. Used items are excluded; partially used remainder items are included.

1.4.0: Effective sticker date defaults to KST today and is independent of actual date and current board; streak bonuses share effectiveDate. Daily all-round history for both roles includes gains, losses, net and system rewards. Legacy logs use KST creation day. Push deployment remains pending Firebase administrative access; this build does not claim background push support.

1.4.1: User-requested native visual refinement: neutral secondary buttons, compact vertical spacing, 48dp touch targets, bordered 52dp dropdown with chevron. Added inline new praise/penalty reason registration with default count, immediate selection and shared Firestore storage, matching the existing web entry point. Web UI remains as requested; this release closes a native parity gap. No device visual verification claimed.

1.5.0: Native manual stamp dialog stays open and keeps selected date until explicitly closed; successful repeated submissions receive new operation IDs. Both clients share six KST daily check slots (07,08,08,20,20,22), persisted opt-in start day and per-date/slot decisions under meta/routine-YYYY-MM-DD-N. Parent approval atomically writes decision, stamp, bonuses, lifetime score and lucky tickets. Skip persists; later stays pending across days. Existing configured reason counts are matched ignoring whitespace (missing reasons default to 1). Backlog begins on first parent launch of this feature. Manual stamps are independent and do not automatically resolve routine checks. Reopen triggers checks; dedicated button allows same-session recheck. No background push claims.

1.5.1: Append stable slot 6 for 수학연산 10문제 맞춤 at 22:00 KST on both clients. Starts 2026-11-24; existing slots and decisions are preserved. Uses configured reason count, default 1, and existing atomic approval path.

1.5.2: Both clients split excess earned points into new boards with the same target/reward, preserving effective dates, bonus records and lifetime points. Already-overfull viewed boards are repaired on parent load. Completion reward IDs remain deterministic. Native and web routine approval immediately displays saving state with retry on failure; removed the web manual-stamp 1-second cooldown. Network completion is not advertised as instantaneous.

1.5.3: Removed web post-stamp UNDO banner and countdown timer, and native last-stamp undo button at user request. Existing history editing/deletion and penalty recovery remain intact.

1.5.4: Routine counts resolve canonical reason names across whitespace/parentheses variants and the explicit 수학연산10문제맞춤 / 수학10문제맞춤 alias on both clients. Math default is 3 when no configured reason exists; configured counts take precedence. Invalid or conflicting counts fail visibly instead of silently granting one. Approval uses the matched reason name for mission bonuses. Historical awards are not modified.
