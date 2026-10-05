package kr.family.praisesticker;

import static kr.family.praisesticker.Cloud.*;

import java.util.*;
import org.json.*;

public class StoreTest {
  static int checks = 0;

  static void check(boolean ok, String name) {
    if (!ok) throw new AssertionError(name);
    checks++;
  }

  static class Fake extends Cloud {
    Map<String, JSONObject> docs = new LinkedHashMap<>();
    boolean failCommit = false, abortOnce = false;
    Map<String, String> versions = new HashMap<>();
    int revision = 0;

    Fake() {
      super(null);
    }

    void seed(String p, JSONObject o) {
      docs.put(p, o);
      versions.put(p, String.valueOf(++revision));
    }

    JSONObject raw(String path) throws Exception {
      return obj(
          "name",
          ROOT + path,
          "fields",
          encodeFields(docs.get(path)),
          "updateTime",
          versions.get(path));
    }

    @Override
    Object request(String suffix, Object body) throws Exception {
      JSONObject b = (JSONObject) body;
      if (suffix.equals(":beginTransaction")) return obj("transaction", "test");
      if (suffix.equals(":rollback")) return obj();
      if (suffix.equals(":runQuery")) {
        JSONObject q = b.getJSONObject("structuredQuery");
        String collection = q.getJSONArray("from").getJSONObject(0).getString("collectionId");
        JSONObject where = q.optJSONObject("where");
        JSONArray a = new JSONArray();
        for (String path : docs.keySet()) {
          if (!path.startsWith(collection + "/")) continue;
          if (where != null) {
            JSONObject filter = where.getJSONObject("fieldFilter");
            String field = filter.getJSONObject("field").getString("fieldPath");
            Object value = decode(filter.getJSONObject("value"));
            if (!String.valueOf(value).equals(String.valueOf(docs.get(path).opt(field)))) continue;
          }
          a.put(obj("document", raw(path)));
        }
        return a;
      }
      if (suffix.equals(":commit")) {
        if (failCommit) throw new java.io.IOException("network failure");
        if (abortOnce) {
          abortOnce = false;
          throw new ApiError(409, "ABORTED", "conflict");
        }
        Map<String, JSONObject> next = new LinkedHashMap<>();
        for (String k : docs.keySet()) next.put(k, new JSONObject(docs.get(k).toString()));
        JSONArray writes = b.getJSONArray("writes");
        for (int i = 0; i < writes.length(); i++) {
          JSONObject w = writes.getJSONObject(i);
          String path =
              (w.has("update")
                      ? w.getJSONObject("update").getString("name")
                      : w.has("delete") ? w.getString("delete") : w.getString("verify"))
                  .substring(ROOT.length());
          JSONObject cond = w.getJSONObject("currentDocument");
          if (cond.has("exists") && cond.getBoolean("exists") != docs.containsKey(path))
            throw new ApiError(400, "FAILED_PRECONDITION", "existence changed");
          if (cond.has("updateTime") && !cond.getString("updateTime").equals(versions.get(path)))
            throw new ApiError(400, "FAILED_PRECONDITION", "version changed");
        }

        for (int i = 0; i < writes.length(); i++) {
          JSONObject w = writes.getJSONObject(i);
          if (w.has("verify")) continue;
          if (w.has("delete")) {
            next.remove(w.getString("delete").substring(ROOT.length()));
            continue;
          }
          JSONObject up = w.getJSONObject("update");
          String path = up.getString("name").substring(ROOT.length());
          JSONObject data = decodeFields(up.getJSONObject("fields"));
          if (w.has("updateMask")) {
            JSONObject old = next.get(path);
            if (old == null) throw new AssertionError("patch nonexistent " + path);
            for (Iterator<String> it = data.keys(); it.hasNext(); ) {
              String k = it.next();
              old.put(k, data.get(k));
            }
          } else next.put(path, data);
        }
        docs = next;
        for (int i = 0; i < writes.length(); i++) {
          JSONObject w = writes.getJSONObject(i);
          if (w.has("verify")) continue;
          String path =
              (w.has("update")
                      ? w.getJSONObject("update").getString("name")
                      : w.getString("delete"))
                  .substring(ROOT.length());
          if (w.has("delete")) versions.remove(path);
          else versions.put(path, String.valueOf(++revision));
        }
        return obj();
      }
      String path = suffix.split("\\?")[0];
      if (!docs.containsKey(path)) throw new ApiError(404, "NOT_FOUND", "missing");
      return raw(path);
    }
  }

  static Store fixture(Fake db) throws Exception {
    Store s = new Store(db);
    db.seed(
        "meta/state",
        obj(
            "lifetimeScore",
            0,
            "praiseReasons",
            arr(obj("name", "숙제", "count", 3)),
            "penaltyReasons",
            arr("약속"),
            "dailyBuff",
            ""));
    db.seed(
        "boards/25",
        obj(
            "round",
            25,
            "targetScore",
            30,
            "reward",
            "게임 30분",
            "status",
            "진행중",
            "theme",
            "rivals",
            "luckyBoxes",
            arr(obj("step", 5, "text", "[\"룰렛 다시돌리기\",\"게임 10분\"]"))));
    s.load();
    return s;
  }

  public static void main(String[] args) throws Exception {
    Fake db = new Fake();
    Store s = fixture(db);
    db.seed("items/retry", Store.item(25, "룰렛 다시돌리기", "랜덤박스"));
    put(db.docs.get("items/retry"), "prizesSnapshot", "[\"A\",\"B\"]");
    db.failCommit = true;
    try {
      s.use("retry", 0, 0);
      throw new AssertionError();
    } catch (java.io.IOException expected) {
    }
    check(
        db.docs.get("items/retry").optString("status").equals("사용 전"),
        "failed exchange keeps source");
    check(!db.docs.containsKey("items/retry_retry"), "failed exchange grants nothing");
    db.failCommit = false;
    db.abortOnce = true;
    s.use("retry", 0, 0);
    check(
        db.docs.get("items/retry_retry").optString("status").equals("사용 전"),
        "retry ticket granted after transaction conflict");
    check(
        db.docs.get("items/retry_retry").optString("prizesSnapshot").equals("[\"A\",\"B\"]"),
        "snapshot preserved");
    int count = db.docs.size();
    s.use("retry", 0, 0);
    check(db.docs.size() == count, "duplicate use is no-op");
    s.win("retry_retry", "룰렛 다시돌리기");
    check(
        db.docs.get("items/prize-retry_retry").optString("sourceTicketId").equals("retry_retry"),
        "roulette linked to source");
    count = db.docs.size();
    try {
      s.win("retry_retry", "different");
      throw new AssertionError();
    } catch (Exception expected) {
    }
    check(db.docs.size() == count, "roulette cannot award twice");
    JSONObject game = Store.item(25, "게임 60분 (이월)", "보상");
    put(game, "gameRollover", true);
    put(game, "gameMinutes", 60);
    put(game, "gameSettlementDate", "2026-10-04");
    db.seed("items/game", game);
    s.use("game", 20, 60);
    check(db.docs.get("items/game").optInt("gameMinutes") == 20, "used portion is 20");
    check(
        db.docs.get("items/game-remainder").optInt("gameMinutes") == 40, "remaining portion is 40");
    s.parent = true;
    int before = db.docs.size();
    try {
      s.rollover("2026-10-04", 10);
      throw new AssertionError();
    } catch (Exception expected) {
    }
    check(
        db.docs.size() == before && db.docs.get("items/game-remainder").optInt("gameMinutes") == 40,
        "invalid rollover cannot delete remaining balance");
    s.rollover("2026-10-04", 50);
    check(
        db.docs.get("gameSettlements/2026-10-04").optInt("remainingBankedMinutes") == 30,
        "rollover subtracts previously used amount");
    db = new Fake();
    s = fixture(db);
    s.parent = true;
    s.stamp("숙제", "", 3, "op1");
    s.stamp("숙제", "", 3, "op2");
    check(db.docs.get("logs/op2-bonus-0").optInt("score") == 1, "5 filled cells award bonus");
    check(
        db.docs.get("meta/state").optInt("lifetimeScore") == 6,
        "lifetime excludes automatic streak bonus");
    check(db.docs.containsKey("items/op2-lucky-5"), "threshold ticket awarded");
    count = db.docs.size();
    s.stamp("숙제", "", 3, "op2");
    check(db.docs.size() == count, "same operation cannot stamp twice");
    s.stamp("숙제", "", 23, "op3");
    check(db.docs.get("boards/25").optString("status").equals("보상 수령 완료"), "completion state set");
    check(db.docs.containsKey("items/board-completion-25"), "completion reward generated");
    count = db.docs.size();
    s.complete(25);
    check(db.docs.size() == count, "completion idempotent");
    List<JSONObject> p =
        Store.prizes("[{\"name\":\"A\",\"ratio\":10},{\"name\":\"B\",\"ratio\":null}]");
    check(
        Math.abs(p.get(0).optDouble("weight") - .1) < 1e-8
            && Math.abs(p.get(1).optDouble("weight") - .9) < 1e-8,
        "weighted probability remainder");
    for (String name : new String[] {"룰렛 다시돌리기", "룰렛 다시 돌리기", "다시 뽑기"})
      check(Store.retry(Store.item(25, name, "랜덤박스")), "retry alias " + name);
    check(!Store.completion(game), "rollover not mistaken for completion reward");
    JSONObject roundtrip =
        obj("number", 4, "boolean", true, "nested", obj("x", arr("y", null, 2.5)));
    check(
        decodeFields(encodeFields(roundtrip)).getJSONObject("nested").getJSONArray("x").length()
            == 3,
        "Firestore codec roundtrip");
    Cloud.Tx guarded = db.new Tx("test");
    guarded.get("meta/state");
    guarded.patch("meta/state", obj("dailyBuff", "new"));
    JSONArray stale = db.guardedWrites(guarded);
    db.seed("meta/state", obj("lifetimeScore", 777, "dailyBuff", "external"));
    try {
      db.request(":commit", obj("writes", stale));
      throw new AssertionError("stale overwrite accepted");
    } catch (Cloud.ApiError expected) {
    }
    check(
        db.docs.get("meta/state").optInt("lifetimeScore") == 777,
        "concurrent external change preserved by precondition");
    System.out.println("PASS: " + checks + " checks; no production writes");
  }
}
