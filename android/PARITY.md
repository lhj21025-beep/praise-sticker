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
| In-app parent notifications | showNotifications | Implemented; not background push |
| Statistics: positive/negative counts, top 5, weekdays | report | Implemented with native bars |
| Completion durations and historical backfill | history, Store.backfill | Implemented |
| Streak bonus repair | Store.repairBonuses | Implemented |
| CSV export and image sharing | export/shareCard, ShareProvider | Implemented with Android storage/share UI |
| Sync | shared Firestore | App foreground refresh; web manual refresh |

Future change policy: check BOTH clients, preserve shared field meanings, add regression coverage for affected behavior, update this table, and build/sign a new APK as well as deploying web changes. Never claim one deployment updates the other client's executable.

Known verification limit: these feature implementations are compiled and key data paths fixture-tested; the complete UI has not been exercised on an Android device in this environment.
