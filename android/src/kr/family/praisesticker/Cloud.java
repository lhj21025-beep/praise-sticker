package kr.family.praisesticker;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

/** Firestore REST client. Uses the same anonymous authentication and project as the web app. */
class Cloud {
  static final String PROJECT = "praise-sticker-756b4",
      ROOT = "projects/" + PROJECT + "/databases/(default)/documents/",
      BASE = "https://firestore.googleapis.com/v1/" + ROOT;
  static final String KEY = "AIzaSyAW-ItTRaZj4fWs57g_vpF-FGWgsKIEpj8";
  final SharedPreferences prefs;
  String token = "";
  long expiry;

  Cloud(Context c) {
    prefs = c == null ? null : c.getSharedPreferences("cloud", 0);
  }

  static JSONObject obj(Object... kv) {
    JSONObject o = new JSONObject();
    try {
      for (int i = 0; i < kv.length; i += 2)
        o.put((String) kv[i], kv[i + 1] == null ? JSONObject.NULL : kv[i + 1]);
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
    return o;
  }

  static JSONArray arr(Object... values) {
    JSONArray a = new JSONArray();
    for (Object v : values) a.put(v == null ? JSONObject.NULL : v);
    return a;
  }

  static void put(JSONObject o, String k, Object v) {
    try {
      o.put(k, v);
    } catch (JSONException e) {
      throw new IllegalArgumentException(e);
    }
  }

  static String id() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  static final class ApiError extends IOException {
    final int code;
    final String status;

    ApiError(int c, String s, String m) {
      super(m);
      code = c;
      status = s;
    }
  }

  static Object http(String url, Object body, String bearer) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
    c.setConnectTimeout(15000);
    c.setReadTimeout(25000);
    c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
    if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
    if (body != null) {
      c.setRequestMethod("POST");
      c.setDoOutput(true);
      try (OutputStream out = c.getOutputStream()) {
        out.write(body.toString().getBytes("UTF-8"));
      }
    }
    int code = c.getResponseCode();
    InputStream stream = code >= 400 ? c.getErrorStream() : c.getInputStream();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    if (stream != null)
      try (InputStream in = stream) {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) bytes.write(buf, 0, n);
      }
    c.disconnect();
    String text = bytes.toString("UTF-8");
    Object result = text.isEmpty() ? new JSONObject() : new JSONTokener(text).nextValue();
    if (code >= 400) {
      JSONObject error =
          result instanceof JSONObject ? ((JSONObject) result).optJSONObject("error") : null;
      throw new ApiError(
          code,
          error == null ? "" : error.optString("status"),
          error == null ? "연결 오류 " + code : error.optString("message"));
    }
    return result;
  }

  void auth() throws Exception {
    if (!token.isEmpty() && System.currentTimeMillis() < expiry) return;
    String refresh = prefs.getString("refresh", "");
    JSONObject a;
    if (refresh.isEmpty())
      a =
          (JSONObject)
              http(
                  "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + KEY,
                  obj("returnSecureToken", true),
                  null);
    else {
      // securetoken accepts JSON as well as the documented form encoding.
      a =
          (JSONObject)
              http(
                  "https://securetoken.googleapis.com/v1/token?key=" + KEY,
                  obj("grant_type", "refresh_token", "refresh_token", refresh),
                  null);
    }
    token = a.optString("idToken", a.optString("id_token"));
    String next = a.optString("refreshToken", a.optString("refresh_token"));
    if (token.isEmpty()) throw new IOException("로그인 토큰을 받지 못했습니다.");
    prefs.edit().putString("refresh", next).apply();
    expiry = System.currentTimeMillis() + 3300000;
  }

  Object request(String suffix, Object body) throws Exception {
    auth();
    try {
      return http(
          (suffix.startsWith(":") ? BASE.substring(0, BASE.length() - 1) : BASE) + suffix,
          body,
          token);
    } catch (ApiError e) {
      if (e.code != 401) throw e;
      expiry = 0;
      auth();
      return http(
          (suffix.startsWith(":") ? BASE.substring(0, BASE.length() - 1) : BASE) + suffix,
          body,
          token);
    }
  }

  static Object decode(JSONObject v) throws JSONException {
    if (v.has("stringValue")) return v.getString("stringValue");
    if (v.has("integerValue")) return Long.parseLong(v.getString("integerValue"));
    if (v.has("doubleValue")) return v.getDouble("doubleValue");
    if (v.has("booleanValue")) return v.getBoolean("booleanValue");
    if (v.has("timestampValue")) return v.getString("timestampValue");
    if (v.has("arrayValue")) {
      JSONArray a = new JSONArray(), values = v.getJSONObject("arrayValue").optJSONArray("values");
      if (values != null)
        for (int i = 0; i < values.length(); i++) a.put(decode(values.getJSONObject(i)));
      return a;
    }
    if (v.has("mapValue")) return decodeFields(v.getJSONObject("mapValue").optJSONObject("fields"));
    return JSONObject.NULL;
  }

  static JSONObject decodeFields(JSONObject fields) throws JSONException {
    JSONObject o = new JSONObject();
    if (fields != null) {
      Iterator<String> keys = fields.keys();
      while (keys.hasNext()) {
        String k = keys.next();
        o.put(k, decode(fields.getJSONObject(k)));
      }
    }
    return o;
  }

  static JSONObject encode(Object v) throws JSONException {
    if (v == null || v == JSONObject.NULL) return obj("nullValue", JSONObject.NULL);
    if (v instanceof String) return obj("stringValue", v);
    if (v instanceof Boolean) return obj("booleanValue", v);
    if (v instanceof Float || v instanceof Double) return obj("doubleValue", v);
    if (v instanceof Number) return obj("integerValue", v.toString());
    if (v instanceof JSONArray) {
      JSONArray out = new JSONArray(), a = (JSONArray) v;
      for (int i = 0; i < a.length(); i++) out.put(encode(a.get(i)));
      return obj("arrayValue", obj("values", out));
    }
    return obj("mapValue", obj("fields", encodeFields((JSONObject) v)));
  }

  static JSONObject encodeFields(JSONObject o) throws JSONException {
    JSONObject f = new JSONObject();
    Iterator<String> keys = o.keys();
    while (keys.hasNext()) {
      String k = keys.next();
      if (!k.startsWith("__")) f.put(k, encode(o.get(k)));
    }
    return f;
  }

  static JSONObject document(JSONObject raw) throws JSONException {
    JSONObject d = decodeFields(raw.optJSONObject("fields"));
    String name = raw.optString("name");
    d.put("__path", name.startsWith(ROOT) ? name.substring(ROOT.length()) : name);
    d.put("__id", name.substring(name.lastIndexOf('/') + 1));
    d.put("__updateTime", raw.optString("updateTime"));
    return d;
  }

  JSONObject get(String path, String tx) throws Exception {
    try {
      return document(
          (JSONObject)
              request(
                  path + (tx == null ? "" : "?transaction=" + URLEncoder.encode(tx, "UTF-8")),
                  null));
    } catch (ApiError e) {
      if (e.code == 404) return null;
      throw e;
    }
  }

  List<JSONObject> query(String collection, String field, Object value, String tx)
      throws Exception {
    JSONObject q = obj("from", arr(obj("collectionId", collection)));
    if (field != null)
      q.put(
          "where",
          obj(
              "fieldFilter",
              obj("field", obj("fieldPath", field), "op", "EQUAL", "value", encode(value))));
    JSONObject body = obj("structuredQuery", q);
    if (tx != null) body.put("transaction", tx);
    Object raw = request(":runQuery", body);
    JSONArray rows = raw instanceof JSONArray ? (JSONArray) raw : arr(raw);
    List<JSONObject> out = new ArrayList<>();
    for (int i = 0; i < rows.length(); i++) {
      JSONObject row = rows.getJSONObject(i);
      if (row.has("document")) out.add(document(row.getJSONObject("document")));
    }
    return out;
  }

  static JSONObject write(String path, JSONObject data, boolean patch) throws Exception {
    JSONObject w = obj("update", obj("name", ROOT + path, "fields", encodeFields(data)));
    if (patch) {
      JSONArray fields = new JSONArray();
      Iterator<String> keys = data.keys();
      while (keys.hasNext()) {
        String k = keys.next();
        if (!k.startsWith("__")) fields.put(k);
      }
      w.put("updateMask", obj("fieldPaths", fields));
    }
    return w;
  }

  static JSONObject delete(String path) {
    return obj("delete", ROOT + path);
  }

  interface Work {
    void run(Tx tx) throws Exception;
  }

  final class Tx {
    final String id;
    final JSONArray writes = new JSONArray();
    boolean wrote = false;

    Tx(String id) {
      this.id = id;
    }

    JSONObject get(String path) throws Exception {
      if (wrote) throw new IllegalStateException("Read before write required");
      return Cloud.this.get(path, id);
    }

    List<JSONObject> query(String c, String f, Object v) throws Exception {
      if (wrote) throw new IllegalStateException("Read before write required");
      return Cloud.this.query(c, f, v, id);
    }

    void set(String path, JSONObject data) throws Exception {
      wrote = true;
      writes.put(write(path, data, false));
    }

    void patch(String path, JSONObject data) throws Exception {
      wrote = true;
      writes.put(write(path, data, true));
    }

    void remove(String path) {
      wrote = true;
      writes.put(delete(path));
    }
  }

  void transaction(Work work) throws Exception {
    for (int attempt = 0; attempt < 4; attempt++) {
      String id =
          ((JSONObject) request(":beginTransaction", obj("options", obj("readWrite", obj()))))
              .getString("transaction");
      Tx tx = new Tx(id);
      try {
        work.run(tx);
        if (tx.writes.length() > 0) request(":commit", obj("transaction", id, "writes", tx.writes));
        else request(":rollback", obj("transaction", id));
        return;
      } catch (Exception e) {
        try {
          request(":rollback", obj("transaction", id));
        } catch (Exception ignored) {
        }
        if (e instanceof ApiError && ((ApiError) e).status.equals("ABORTED") && attempt < 3)
          continue;
        throw e;
      }
    }
  }
}
