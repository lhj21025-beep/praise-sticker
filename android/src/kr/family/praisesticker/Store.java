package kr.family.praisesticker;

import static kr.family.praisesticker.Cloud.*;

import java.time.*;
import java.time.format.*;
import java.util.*;
import org.json.*;

final class Store {
  final Cloud db;
  JSONObject state, board;
  List<JSONObject> boards = new ArrayList<>(),
      logs = new ArrayList<>(),
      items = new ArrayList<>(),
      wishes = new ArrayList<>(),
      requests = new ArrayList<>(),
      notifications = new ArrayList<>();
  boolean parent;
  int selectedRound = 0, score, streak;
  String best = "아직 없음";
  List<String> lastStamp = new ArrayList<>();

  Store(Cloud db) {
    this.db = db;
  }

  static String now() {
    return Instant.now().toString();
  }

  static String day(String iso) {
    try {
      return Instant.parse(iso).atZone(ZoneId.of("Asia/Seoul")).toLocalDate().toString();
    } catch (Exception e) {
      return iso.length() >= 10 ? iso.substring(0, 10) : iso;
    }
  }

  static String date(String iso) {
    try {
      return Instant.parse(iso)
          .atZone(ZoneId.of("Asia/Seoul"))
          .format(DateTimeFormatter.ofPattern("MM/dd HH:mm"));
    } catch (Exception e) {
      return iso;
    }
  }

  static JSONArray array(JSONObject o, String k) {
    JSONArray a = o == null ? null : o.optJSONArray(k);
    return a == null ? new JSONArray() : a;
  }

  int round() {
    return board.optInt("round");
  }

  String actor() {
    return parent ? "엄빠" : "강천";
  }

  void requireParent() throws Exception {
    if (!parent) throw new Exception("부모 화면에서만 사용할 수 있습니다.");
  }

  void login(boolean isParent, String pw) throws Exception {
    JSONObject fresh = db.get("meta/state", null);
    if (fresh == null) throw new Exception("기존 가족 데이터를 찾지 못했습니다.");
    if (isParent && !fresh.optString("parentPassword", "12345").equals(pw))
      throw new Exception("비밀번호가 틀렸습니다.");
    parent = isParent;
    load();
  }

  static void sortDate(List<JSONObject> a, String field, boolean desc) {
    a.sort((x, y) -> (desc ? -1 : 1) * x.optString(field).compareTo(y.optString(field)));
  }

  void load() throws Exception {
    JSONObject s = db.get("meta/state", null);
    if (s == null) throw new Exception("데이터를 찾지 못했습니다.");
    List<JSONObject> b = db.query("boards", null, null, null);
    b.sort(Comparator.comparingInt(x -> x.optInt("round")));
    JSONObject current = null;
    for (JSONObject x : b) {
      if (!x.has("luckyBoxes"))
        put(
            x,
            "luckyBoxes",
            x.optInt("luckyStep") > 0
                ? arr(obj("step", x.optInt("luckyStep"), "text", x.optString("luckyText")))
                : new JSONArray());
      if (selectedRound > 0 && x.optInt("round") == selectedRound) current = x;
    }
    if (current == null)
      for (JSONObject x : b)
        if (x.optString("status").equals("진행중") || x.optString("status").equals("완료")) {
          current = x;
          break;
        }
    if (current == null && !b.isEmpty()) current = b.get(b.size() - 1);
    if (current == null) throw new Exception("스티커판이 없습니다. 웹에서 첫 회차를 만들어주세요.");
    List<JSONObject> l = db.query("logs", "round", current.optInt("round"), null);
    sortDate(l, "date", true);
    List<JSONObject> i = db.query("items", null, null, null);
    sortDate(i, "obtainedDate", true);
    List<JSONObject> w = db.query("wishes", null, null, null);
    sortDate(w, "date", true);
    List<JSONObject> r = parent ? db.query("requests", "status", "대기 중", null) : new ArrayList<>(),
        n = parent ? db.query("notifications", "status", "대기 중", null) : new ArrayList<>();
    state = s;
    boards = b;
    board = current;
    logs = l;
    items = i;
    wishes = w;
    requests = r;
    notifications = n;
    score = sum(l);
    streak = streak(l);
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (JSONObject x : l)
      if (x.optInt("score") > 0 && !x.optString("executor").equals("시스템"))
        counts.put(x.optString("reason"), counts.getOrDefault(x.optString("reason"), 0) + 1);
    int max = 0;
    best = "아직 없음";
    for (String k : counts.keySet())
      if (counts.get(k) > max) {
        best = k;
        max = counts.get(k);
      }
    if (score >= board.optInt("targetScore", 30) && !board.optString("status").equals("보상 수령 완료")) {
      complete(round());
      board = db.get("boards/" + round(), null);
      items = db.query("items", null, null, null);
      sortDate(items, "obtainedDate", true);
    }
  }

  static int sum(List<JSONObject> l) {
    int s = 0;
    for (JSONObject x : l) s += x.optInt("score");
    return s;
  }

  static int streak(List<JSONObject> l) {
    List<JSONObject> a = new ArrayList<>(l);
    sortDate(a, "date", true);
    int n = 0;
    for (JSONObject x : a) {
      if (x.optString("executor").equals("시스템")) continue;
      int s = x.optInt("score");
      if (s < 0) break;
      if (s > 0) n += s;
    }
    return n;
  }

  static boolean completion(JSONObject i) {
    return i.optString("type").equals("보상")
        && !i.optBoolean("gameRollover")
        && i.optString("gameSettlementDate").isEmpty()
        && !i.optString("name").matches(".*게임\\s*\\d+\\s*분.*이월.*");
  }

  static boolean retry(JSONObject i) {
    return i.optString("type", "랜덤박스").equals("랜덤박스")
        && i.optString("name").replaceAll("\\s+", "").matches("(?:룰렛)?다시(?:돌리기|뽑기)(?:권|1회|1회권)?");
  }

  static JSONObject item(int r, String name, String type) {
    return obj(
        "round",
        r,
        "name",
        name,
        "type",
        type,
        "status",
        "사용 전",
        "usedDate",
        "",
        "obtainedDate",
        now());
  }

  void complete(int round) throws Exception {
    db.transaction(
        tx -> {
          JSONObject b = tx.get("boards/" + round);
          List<JSONObject> l = tx.query("logs", "round", round),
              i = tx.query("items", "round", round);
          JSONObject existing = tx.get("items/board-completion-" + round);
          if (b == null
              || b.optString("status").equals("보상 수령 완료")
              || sum(l) < b.optInt("targetScore", 30)) return;
          JSONObject update = obj("status", "보상 수령 완료");
          if (!l.isEmpty()) {
            sortDate(l, "date", false);
            String start = day(l.get(0).optString("date")),
                end = day(l.get(l.size() - 1).optString("date"));
            put(
                update,
                "daysTaken",
                java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDate.parse(start), LocalDate.parse(end))
                    + 1);
            put(update, "firstLogDate", start);
          }
          tx.patch("boards/" + round, update);
          boolean has = existing != null;
          for (JSONObject x : i) has |= completion(x);
          if (!has) {
            JSONObject reward = item(round, b.optString("reward"), "보상");
            put(reward, "rewardSource", "board-completion");
            tx.set("items/board-completion-" + round, reward);
          }
        });
  }

  void stamp(String reason, String detail, int amount, String operationId) throws Exception {
    requireParent();
    int r = round();
    List<String> ids = new ArrayList<>();
    String timestamp = now();
    db.transaction(
        tx -> {
          JSONObject existing = tx.get("logs/" + operationId),
              s = tx.get("meta/state"),
              b = tx.get("boards/" + r);
          List<JSONObject> l = tx.query("logs", "round", r);
          if (existing != null) return;
          if (amount < 0 && sum(l) <= 0) return;
          int score = amount;
          String label = reason;
          if (score > 0 && reason.equals(s.optString("dailyBuff"))) {
            score++;
            label = "🌟 [스페셜 미션 달성!] " + reason;
          }
          int before = streak(l), bonus = amount > 0 ? (before + score) / 5 - before / 5 : 0;
          tx.set(
              "logs/" + operationId,
              obj(
                  "round",
                  r,
                  "date",
                  timestamp,
                  "reason",
                  label,
                  "detail",
                  detail,
                  "executor",
                  actor(),
                  "score",
                  score));
          ids.clear();
          ids.add(operationId);
          for (int j = 0; j < bonus; j++) {
            String id = operationId + "-bonus-" + j;
            tx.set(
                "logs/" + id,
                obj(
                    "round",
                    r,
                    "date",
                    Instant.parse(timestamp).plusMillis(1000L * (j + 1)).toString(),
                    "reason",
                    "🔥 연속 칭찬 " + ((before / 5 + j + 1) * 5) + "회 보너스!",
                    "detail",
                    "대단해요!",
                    "executor",
                    "시스템",
                    "score",
                    1));
            ids.add(id);
          }
          if (score > 0)
            tx.patch("meta/state", obj("lifetimeScore", s.optInt("lifetimeScore") + score));
          JSONArray boxes = array(b, "luckyBoxes");
          if (boxes.length() == 0 && b.optInt("luckyStep") > 0)
            boxes = arr(obj("step", b.optInt("luckyStep"), "text", b.optString("luckyText")));
          for (int j = 0; j < boxes.length(); j++) {
            JSONObject box = boxes.getJSONObject(j);
            int step = box.optInt("step");
            if (step > 0 && sum(l) < step && sum(l) + score + bonus >= step) {
              JSONObject ticket = item(r, "룰렛 추첨권", "랜덤박스");
              put(ticket, "prizesSnapshot", box.optString("text"));
              tx.set("items/" + operationId + "-lucky-" + step, ticket);
            }
          }
        });
    lastStamp = ids;
    complete(r);
  }

  void use(String id, int amount, int max) throws Exception {
    if (parent) throw new Exception("자녀 화면에서 사용할 수 있습니다.");
    String remainder = id + "-remainder", notification = "use-" + id;
    db.transaction(
        tx -> {
          JSONObject it = tx.get("items/" + id), ticket = tx.get("items/retry_" + id);
          if (it == null || !it.optString("status").equals("사용 전")) return;
          String name = it.optString("name");
          JSONObject update = obj("status", "사용 후", "usedDate", now());
          if (retry(it)) {
            if (ticket == null) {
              JSONObject t = item(it.optInt("round"), "룰렛 추첨권", "랜덤박스");
              put(t, "prizesSnapshot", it.optString("prizesSnapshot"));
              put(t, "sourceRetryItemId", id);
              tx.set("items/retry_" + id, t);
            }
            put(update, "replacementTicketId", "retry_" + id);
          } else if (amount > 0 && max > 0) {
            if (amount > max) throw new Exception("사용량이 남은 양보다 많습니다.");
            put(update, "name", name.replaceFirst(String.valueOf(max), String.valueOf(amount)));
            if (it.optBoolean("gameRollover")) put(update, "gameMinutes", amount);
            if (amount < max) {
              JSONObject rem =
                  item(
                      it.optInt("round"),
                      name.replaceFirst(String.valueOf(max), String.valueOf(max - amount)),
                      it.optString("type", "랜덤박스"));
              put(rem, "obtainedDate", it.optString("obtainedDate"));
              if (it.optBoolean("gameRollover") || name.contains("이월")) {
                put(rem, "gameRollover", true);
                put(rem, "gameSettlementDate", it.optString("gameSettlementDate"));
                put(rem, "gameMinutes", max - amount);
              }
              tx.set("items/" + remainder, rem);
            }
          }
          tx.patch("items/" + id, update);
          tx.set(
              "notifications/" + notification,
              obj(
                  "kind",
                  "item_use",
                  "text",
                  "🎒 사용: " + name + (retry(it) ? " → 룰렛 추첨권 1장 지급" : ""),
                  "childName",
                  actor(),
                  "date",
                  now(),
                  "status",
                  "대기 중"));
        });
  }

  void win(String id, String prize) throws Exception {
    if (parent) throw new Exception("자녀 화면에서 추첨해주세요.");
    db.transaction(
        tx -> {
          JSONObject t = tx.get("items/" + id);
          if (t == null || !t.optString("status").equals("사용 전"))
            throw new Exception("이미 사용된 추첨권입니다. 새로고침해주세요.");
          tx.patch("items/" + id, obj("status", "사용 후", "usedDate", now()));
          JSONObject p = item(t.optInt("round"), prize, "랜덤박스");
          put(p, "prizesSnapshot", t.optString("prizesSnapshot"));
          put(p, "sourceTicketId", id);
          tx.set("items/prize-" + id, p);
          tx.set(
              "notifications/win-" + id,
              obj(
                  "kind",
                  "roulette",
                  "text",
                  "🎰 룰렛 당첨: " + prize,
                  "childName",
                  actor(),
                  "date",
                  now(),
                  "status",
                  "대기 중"));
        });
  }

  void patch(String path, JSONObject changes) throws Exception {
    db.transaction(
        tx -> {
          JSONObject old = tx.get(path);
          if (old == null) throw new Exception("항목을 찾지 못했습니다.");
          tx.patch(path, changes);
        });
  }

  void add(String collection, JSONObject data, String id) throws Exception {
    db.transaction(
        tx -> {
          if (tx.get(collection + "/" + id) == null) tx.set(collection + "/" + id, data);
        });
  }

  void editLog(String id, String reason, String detail, int score, boolean remove)
      throws Exception {
    requireParent();
    db.transaction(
        tx -> {
          JSONObject log = tx.get("logs/" + id), s = tx.get("meta/state");
          if (log == null) return;
          int
              before =
                  !log.optString("executor").equals("시스템") ? Math.max(0, log.optInt("score")) : 0,
              after = !log.optString("executor").equals("시스템") && !remove ? Math.max(0, score) : 0;
          if (remove) tx.remove("logs/" + id);
          else tx.patch("logs/" + id, obj("reason", reason, "detail", detail, "score", score));
          if (before != after)
            tx.patch(
                "meta/state", obj("lifetimeScore", s.optInt("lifetimeScore") + after - before));
        });
  }

  void undo() throws Exception {
    requireParent();
    List<String> ids = new ArrayList<>(lastStamp);
    db.transaction(
        tx -> {
          JSONObject s = tx.get("meta/state");
          List<JSONObject> found = new ArrayList<>();
          int delta = 0;
          for (String id : ids) {
            JSONObject l = tx.get("logs/" + id);
            if (l != null) {
              found.add(l);
              if (!l.optString("executor").equals("시스템")) delta += Math.max(0, l.optInt("score"));
            }
          }
          for (JSONObject l : found) tx.remove(l.optString("__path"));
          if (delta != 0)
            tx.patch("meta/state", obj("lifetimeScore", s.optInt("lifetimeScore") - delta));
        });
    lastStamp.clear();
  }

  void recover(String op) throws Exception {
    requireParent();
    int r = round();
    db.transaction(
        tx -> {
          JSONObject prior = tx.get("logs/" + op);
          List<JSONObject> l = tx.query("logs", "round", r);
          if (prior != null) return;
          sortDate(l, "date", true);
          JSONObject target = null;
          for (JSONObject x : l)
            if (x.optInt("score") < 0) {
              target = x;
              break;
            }
          if (target == null) throw new Exception("만회할 차감 내역이 없습니다.");
          tx.patch(
              target.optString("__path"),
              obj("reason", target.optString("reason") + " (만회 완료 ↩️)", "score", 0));
          tx.set(
              "logs/" + op,
              obj(
                  "round",
                  r,
                  "date",
                  now(),
                  "reason",
                  "✨ 만회하기 성공!",
                  "detail",
                  "실수를 멋지게 바로잡았어요!",
                  "executor",
                  "시스템",
                  "score",
                  1));
        });
    complete(r);
  }

  void rollover(String date, int minutes) throws Exception {
    requireParent();
    LocalDate d = LocalDate.parse(date);
    if (d.isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))))
      throw new Exception("미래 날짜는 이월할 수 없습니다.");
    if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY)
      throw new Exception("토요일 또는 일요일만 선택해주세요.");
    if (minutes < 1 || minutes > 60) throw new Exception("1~60분을 입력해주세요.");
    int r = round();
    String newId = id();
    db.transaction(
        tx -> {
          JSONObject old = tx.get("gameSettlements/" + date);
          List<JSONObject> related = tx.query("items", "gameSettlementDate", date);
          if (old != null && !old.optString("itemId").isEmpty() && !old.isNull("itemId")) {
            boolean found = false;
            for (JSONObject i : related)
              found |= i.optString("__id").equals(old.optString("itemId"));
            if (!found) {
              JSONObject x = tx.get("items/" + old.optString("itemId"));
              if (x != null) related.add(x);
            }
          }
          int used = 0;
          for (JSONObject i : related)
            if (i.optString("status").equals("사용 후")) used += gameMinutes(i);
          if (minutes < used) throw new Exception("이미 " + used + "분을 사용했습니다. 그보다 작게 수정할 수 없습니다.");
          for (JSONObject i : related)
            if (i.optString("status").equals("사용 전")) tx.remove(i.optString("__path"));
          int remaining = minutes - used;
          if (remaining > 0) {
            JSONObject i = item(r, "게임 " + remaining + "분 (이월)", "보상");
            put(i, "gameRollover", true);
            put(i, "gameSettlementDate", date);
            put(i, "gameMinutes", remaining);
            tx.set("items/" + newId, i);
          }
          tx.set(
              "gameSettlements/" + date,
              obj(
                  "date",
                  date,
                  "unusedMinutes",
                  minutes,
                  "bankedMinutes",
                  minutes,
                  "usedFromBankedMinutes",
                  used,
                  "remainingBankedMinutes",
                  remaining,
                  "itemId",
                  remaining > 0 ? newId : null,
                  "settledAt",
                  now()));
        });
  }

  static int gameMinutes(JSONObject i) {
    if (i.has("gameMinutes")) return i.optInt("gameMinutes");
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("게임\\s*(\\d+)\\s*분.*이월").matcher(i.optString("name"));
    return m.find() ? Integer.parseInt(m.group(1)) : 0;
  }

  void newBoard(int target, String reward) throws Exception {
    requireParent();
    if (target < 1 || target > 1000 || reward.trim().isEmpty())
      throw new Exception("목표(1~1000)와 보상을 확인해주세요.");
    for (JSONObject b : boards)
      if (b.optString("status").equals("진행중") || b.optString("status").equals("완료"))
        complete(b.optInt("round"));
    final int[] created = {0};
    db.transaction(
        tx -> {
          List<JSONObject> all = tx.query("boards", null, null);
          int max = 0;
          for (JSONObject b : all) max = Math.max(max, b.optInt("round"));
          for (JSONObject b : all)
            if (b.optString("status").equals("진행중") || b.optString("status").equals("완료"))
              tx.patch(b.optString("__path"), obj("status", "보상 수령 완료"));
          created[0] = max + 1;
          tx.set(
              "boards/" + created[0],
              obj(
                  "round",
                  created[0],
                  "targetScore",
                  target,
                  "reward",
                  reward,
                  "theme",
                  "rivals",
                  "status",
                  "진행중",
                  "luckyBoxes",
                  new JSONArray()));
        });
    selectedRound = created[0];
  }

  void dismissNotifications() throws Exception {
    requireParent();
    db.transaction(
        tx -> {
          List<JSONObject> list = tx.query("notifications", "status", "대기 중");
          for (JSONObject n : list) tx.patch(n.optString("__path"), obj("status", "확인함"));
        });
  }

  void password(String old, String value) throws Exception {
    requireParent();
    if (value.length() < 4) throw new Exception("4자리 이상 입력해주세요.");
    db.transaction(
        tx -> {
          JSONObject s = tx.get("meta/state");
          if (!s.optString("parentPassword", "12345").equals(old))
            throw new Exception("현재 비밀번호가 틀렸습니다.");
          tx.patch("meta/state", obj("parentPassword", value));
        });
  }

  void backfill() throws Exception {
    requireParent();
    db.transaction(
        tx -> {
          List<JSONObject> b = tx.query("boards", null, null), logs = tx.query("logs", null, null);
          for (JSONObject x : b) {
            if (x.has("daysTaken") || x.optString("status").equals("진행중")) continue;
            String first = null, last = null;
            for (JSONObject l : logs)
              if (l.optInt("round") == x.optInt("round")) {
                String date = day(l.optString("date"));
                if (first == null || date.compareTo(first) < 0) first = date;
                if (last == null || date.compareTo(last) > 0) last = date;
              }
            if (first != null)
              tx.patch(
                  x.optString("__path"),
                  obj(
                      "daysTaken",
                      java.time.temporal.ChronoUnit.DAYS.between(
                              LocalDate.parse(first), LocalDate.parse(last))
                          + 1,
                      "firstLogDate",
                      first));
          }
        });
  }

  void repairBonuses() throws Exception {
    requireParent();
    int r = round();
    db.transaction(
        tx -> {
          List<JSONObject> logs = tx.query("logs", "round", r);
          JSONObject state = tx.get("meta/state");
          sortDate(logs, "date", false);
          Set<Integer> existing = new HashSet<>();
          java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("연속 칭찬 (\\d+)회 보너스");
          for (JSONObject l : logs) {
            java.util.regex.Matcher m = pattern.matcher(l.optString("reason"));
            if (l.optString("executor").equals("시스템") && m.find())
              existing.add(Integer.parseInt(m.group(1)));
          }
          int streak = 0, added = 0;
          for (JSONObject l : logs) {
            if (l.optString("executor").equals("시스템")) continue;
            int s = l.optInt("score");
            if (s <= 0) {
              streak = 0;
              continue;
            }
            int before = streak;
            streak += s;
            for (int n = (before / 5 + 1) * 5; n <= streak; n += 5)
              if (!existing.contains(n)) {
                existing.add(n);
                added++;
                tx.set(
                    "logs/repair-" + r + "-" + n,
                    obj(
                        "round",
                        r,
                        "date",
                        Instant.parse(l.optString("date")).plusMillis(500).toString(),
                        "reason",
                        "🔥 연속 칭찬 " + n + "회 보너스!",
                        "detail",
                        "대단해요! (보정)",
                        "executor",
                        "시스템",
                        "score",
                        1));
              }
          }
          if (added > 0)
            tx.patch("meta/state", obj("lifetimeScore", state.optInt("lifetimeScore") + added));
        });
    complete(r);
  }

  static final String[] TITLES = {
    "신입 라이벌 🌱",
    "유망주 🌟",
    "베테랑 ⚔️",
    "엘리트 🏅",
    "마스터 👑",
    "전설의 라이벌 🔥",
    "월드 챔피언 🏆",
    "이전 장수 🗡️",
    "왕쌍 장수 🗡️",
    "화웅 장수 🗡️",
    "관평 장수 🗡️",
    "마대 장수 🗡️",
    "조홍 장수 🗡️",
    "조인 장수 🗡️",
    "능통 장수 🗡️",
    "주태 장수 🗡️",
    "우금 장수 🗡️",
    "악진 맹장 🛡️",
    "하후연 맹장 🛡️",
    "위연 맹장 🛡️",
    "손책 맹장 🛡️",
    "하후돈 맹장 🛡️",
    "장합 맹장 🛡️",
    "서황 맹장 🛡️",
    "안량 맹장 🛡️",
    "문추 맹장 🛡️",
    "감녕 맹장 🛡️",
    "태사자 용장 ⚔️",
    "방덕 용장 ⚔️",
    "황충 용장 ⚔️",
    "허저 용장 ⚔️",
    "전위 용장 ⚔️",
    "조운 대장군 🔥⚔️",
    "마초 대장군 🔥⚔️",
    "장비 대장군 🔥⚔️",
    "관우 대장군 🔥⚔️",
    "여포 - 최강 무신 🐉👑"
  };

  String title() {
    return TITLES[
        Math.min(TITLES.length - 1, Math.max(0, (state.optInt("lifetimeScore") - 1) / 50))];
  }

  static List<JSONObject> prizes(String text) throws Exception {
    List<JSONObject> p = new ArrayList<>();
    try {
      JSONArray a = new JSONArray(text);
      for (int i = 0; i < a.length(); i++) {
        Object v = a.get(i);
        p.add(
            v instanceof JSONObject
                ? (JSONObject) v
                : obj("name", String.valueOf(v), "ratio", null));
      }
    } catch (Exception e) {
      if (!text.isEmpty()) p.add(obj("name", text, "ratio", null));
    }
    if (p.isEmpty()) {
      p.add(obj("name", "꽝", "ratio", null));
      p.add(obj("name", "다시 뽑기", "ratio", null));
    }
    double specified = 0;
    int blank = 0;
    for (JSONObject v : p) {
      if (v.isNull("ratio") || !v.has("ratio")) blank++;
      else specified += v.optDouble("ratio", 0);
    }
    double total = 0;
    for (JSONObject v : p) {
      double w =
          specified <= 0
              ? 1.0 / p.size()
              : specified >= 100
                  ? (v.isNull("ratio") ? 0 : v.optDouble("ratio", 0) / specified)
                  : v.isNull("ratio")
                      ? (blank > 0 ? (100 - specified) / 100 / blank : 0)
                      : v.optDouble("ratio", 0) / 100;
      put(v, "weight", w);
      total += w;
    }
    for (JSONObject v : p) put(v, "weight", total > 0 ? v.optDouble("weight") / total : 0);
    return p;
  }
}
