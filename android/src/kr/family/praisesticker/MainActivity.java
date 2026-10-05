package kr.family.praisesticker;

import static kr.family.praisesticker.Cloud.*;

import android.animation.ValueAnimator;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
  static final int INK = 0xff334155,
      MUTED = 0xff64748b,
      PURPLE = 0xff6366f1,
      PINK = 0xffec4899,
      BG = 0xfff8fafc,
      WHITE = 0xffffffff,
      GREEN = 0xff10b981;
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  final Handler main = new Handler(Looper.getMainLooper());
  Store s;
  LinearLayout root, body;
  ScrollView scroll;
  TextView sync;
  boolean busy = false, active = false;
  int dialogs = 0;
  String page = "home", boxType = "보상", itemStatus = "사용 전";
  byte[] exportBytes;
  String exportMime;
  BoardView boardView;
  ProgressBar progress;
  long lastSync = 0;
  boolean logged = false;
  final Runnable poll =
      new Runnable() {
        public void run() {
          if (active && logged && !busy && dialogs == 0) refresh(false);
          main.postDelayed(this, 60000);
        }
      };

  int dp(float n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  interface Job {
    void run() throws Exception;
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    s = new Store(new Cloud(this));
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    loginScreen();
    String role = getPreferences(0).getString("role", "");
    if (role.equals("child")) login(false, "");
    else if (role.equals("parent")) {
      String pw = SecureSession.read(this);
      if (!pw.isEmpty()) login(true, pw);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    active = true;
    main.removeCallbacks(poll);
    main.postDelayed(poll, 60000);
    if (logged && !busy && System.currentTimeMillis() - lastSync > 15000) refresh(false);
  }

  @Override
  protected void onPause() {
    active = false;
    main.removeCallbacks(poll);
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    worker.shutdown();
    main.removeCallbacksAndMessages(null);
    super.onDestroy();
  }

  @Override
  public void onBackPressed() {
    if (!page.equals("home")) {
      page = "home";
      render();
    } else super.onBackPressed();
  }

  void toast(String t) {
    Toast.makeText(this, t, Toast.LENGTH_LONG).show();
  }

  void vibrate() {
    try {
      ((Vibrator) getSystemService(VIBRATOR_SERVICE))
          .vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
    } catch (Exception ignored) {
    }
  }

  void error(Exception e) {
    String m = e.getMessage();
    if (m == null) m = e.toString();
    if (m.contains("PERMISSION") || m.contains("permission"))
      m = "서버 접근 권한을 확인하지 못했습니다. 다시 로그인해주세요.";
    new AlertDialog.Builder(this)
        .setTitle("처리하지 못했어요")
        .setMessage(m + "\n\n연결이 끊겼다면 새로고침하여 반영 여부를 확인해주세요.")
        .setPositiveButton("확인", null)
        .show();
  }

  void run(String label, Job job, Runnable after) {
    if (busy) {
      toast("처리 중입니다. 잠시 기다려주세요.");
      return;
    }
    busy = true;
    if (sync != null) sync.setText(label);
    if (progress != null) progress.setVisibility(View.VISIBLE);
    worker.execute(
        () -> {
          Exception failure = null;
          try {
            job.run();
          } catch (Exception e) {
            failure = e;
          }
          Exception result = failure;
          main.post(
              () -> {
                if (isDestroyed()) return;
                busy = false;
                if (progress != null) progress.setVisibility(View.GONE);
                if (result != null) {
                  if (sync != null) sync.setText("연결 확인 필요 · 새로고침");
                  error(result);
                } else {
                  if (after != null) after.run();
                }
              });
        });
  }

  void change(String label, Job job) {
    run(
        label,
        () -> {
          job.run();
          s.load();
        },
        () -> {
          lastSync = System.currentTimeMillis();
          render();
          toast(label + " 완료");
        });
  }

  void refresh(boolean manual) {
    run(
        "동기화 중…",
        () -> s.load(),
        () -> {
          lastSync = System.currentTimeMillis();
          render();
          if (manual) toast("최신 내용을 불러왔어요.");
        });
  }

  GradientDrawable bg(int color, int stroke) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(18));
    if (stroke != 0) d.setStroke(dp(1), stroke);
    return d;
  }

  LinearLayout col() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(1);
    return l;
  }

  LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(0);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  TextView text(String value, int size, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    if (bold) t.setTypeface(null, 1);
    t.setPadding(dp(2), dp(6), dp(2), dp(6));
    return t;
  }

  void add(LinearLayout l, View v) {
    l.addView(v, new LinearLayout.LayoutParams(-1, -2));
  }

  void gap(LinearLayout l, int n) {
    View v = new View(this);
    l.addView(v, new LinearLayout.LayoutParams(1, dp(n)));
  }

  void center(LinearLayout l, String value, int size, int color) {
    TextView t = text(value, size, color, true);
    t.setGravity(Gravity.CENTER);
    add(l, t);
  }

  Button button(String label, int color, Runnable click) {
    Button b = new Button(this);
    b.setText(label);
    b.setTextSize(13);
    b.setAllCaps(false);
    b.setTypeface(null, 1);
    b.setTextColor(WHITE);
    b.setMinHeight(dp(46));
    b.setMinimumHeight(dp(46));
    b.setPadding(dp(10), dp(8), dp(10), dp(8));
    b.setBackground(bg(color, 0));
    b.setOnClickListener(
        v -> {
          vibrate();
          click.run();
        });
    return b;
  }

  void btn(LinearLayout l, String label, int color, Runnable click) {
    Button b = button(label, color, click);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(8);
    l.addView(b, lp);
  }

  void buttons(LinearLayout l, String[] labels, int[] colors, Runnable[] clicks) {
    LinearLayout r = row();
    for (int i = 0; i < labels.length; i++) {
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
      if (i > 0) lp.leftMargin = dp(6);
      r.addView(button(labels[i], colors[i], clicks[i]), lp);
    }
    gap(l, 8);
    add(l, r);
  }

  LinearLayout card(LinearLayout l) {
    LinearLayout c = col();
    c.setPadding(dp(16), dp(12), dp(16), dp(14));
    c.setBackground(bg(WHITE, 0xffe2e8f0));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(12);
    l.addView(c, lp);
    return c;
  }

  EditText input(LinearLayout l, String hint, String value, boolean number) {
    EditText e = new EditText(this);
    e.setTextSize(16);
    e.setTextColor(INK);
    e.setHint(hint);
    e.setText(value);
    e.setPadding(dp(14), dp(12), dp(14), dp(12));
    e.setBackground(bg(0xfff1f5f9, 0xffe2e8f0));
    if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.topMargin = dp(8);
    l.addView(e, lp);
    return e;
  }

  int num(EditText e) throws Exception {
    try {
      return Integer.parseInt(e.getText().toString().trim());
    } catch (Exception x) {
      throw new Exception("숫자를 입력해주세요.");
    }
  }

  AlertDialog dialog(String title, LinearLayout content) {
    ScrollView sc = new ScrollView(this);
    sc.setFillViewport(true);
    sc.addView(content);
    content.setPadding(dp(20), dp(10), dp(20), dp(20));
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(sc)
            .setNegativeButton("닫기", null)
            .create();
    d.setOnDismissListener(x -> dialogs--);
    dialogs++;
    d.show();
    d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    return d;
  }

  void confirm(String title, Runnable yes) {
    dialogs++;
    new AlertDialog.Builder(this)
        .setMessage(title)
        .setPositiveButton("확인", (d, w) -> yes.run())
        .setNegativeButton("취소", null)
        .setOnDismissListener(d -> dialogs--)
        .show();
  }

  void shell() {
    root = col();
    root.setBackgroundColor(BG);
    root.setOnApplyWindowInsetsListener(
        (v, i) -> {
          v.setPadding(
              i.getSystemWindowInsetLeft(),
              i.getSystemWindowInsetTop(),
              i.getSystemWindowInsetRight(),
              i.getSystemWindowInsetBottom());
          return i;
        });
    setContentView(root);
    root.requestApplyInsets();
    progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progress.setIndeterminate(true);
    progress.setVisibility(View.GONE);
    root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout outer = col();
    outer.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    scroll.addView(outer);
    body = col();
    body.setPadding(dp(18), dp(12), dp(18), dp(24));
    int max = Math.min(getResources().getDisplayMetrics().widthPixels, dp(560));
    outer.addView(body, new LinearLayout.LayoutParams(max, -2));
  }

  void loginScreen() {
    logged = false;
    shell();
    gap(body, 60);
    center(body, "⭐", 68, PURPLE);
    center(body, "칭찬 스티커", 30, INK);
    center(body, "강천이의 작은 도전, 커다란 성장", 15, MUTED);
    gap(body, 25);
    btn(body, "🌱 강천이로 시작하기", PURPLE, () -> login(false, ""));
    btn(
        body,
        "🔐 부모님으로 시작하기",
        INK,
        () -> {
          LinearLayout c = col();
          EditText pw = input(c, "부모 비밀번호", "", false);
          pw.setInputType(129);
          AlertDialog d = dialog("부모님 로그인", c);
          btn(
              c,
              "로그인",
              PURPLE,
              () -> {
                String value = pw.getText().toString();
                d.dismiss();
                login(true, value);
              });
        });
    sync = text("기존 웹앱과 같은 가족 데이터를 사용합니다.", 12, MUTED, false);
    sync.setGravity(Gravity.CENTER);
    add(body, sync);
  }

  void login(boolean parent, String pw) {
    run(
        "로그인 중…",
        () -> s.login(parent, pw),
        () -> {
          logged = true;
          getPreferences(0).edit().putString("role", parent ? "parent" : "child").apply();
          if (parent) SecureSession.save(this, pw);
          lastSync = System.currentTimeMillis();
          page = "home";
          render();
          if (parent && !s.notifications.isEmpty()) showNotifications();
        });
  }

  void render() {
    if (!logged) return;
    int oldY = scroll == null ? 0 : scroll.getScrollY();
    shell();
    LinearLayout header = col();
    header.setPadding(dp(18), dp(13), dp(18), dp(15));
    header.setBackground(
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[] {0xff6366f1, 0xff4338ca}));
    add(body, header);
    add(header, text((s.parent ? "👨‍👩‍👦 엄빠" : "🌱 강천") + "  ·  " + s.title(), 17, WHITE, true));
    int total = s.state.optInt("lifetimeScore");
    ProgressBar lv = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    lv.setMax(50);
    lv.setProgress(total == 0 ? 0 : (total - 1) % 50 + 1);
    add(header, lv);
    add(header, text("누적 " + total + " pts", 12, WHITE, true));
    buttons(
        header,
        new String[] {"🏅 등급", "🎒 보관함", "🙏 소원함"},
        new int[] {0xff4f46e5, PINK, 0xfff59e0b},
        new Runnable[] {
          this::levels,
          () -> {
            page = "items";
            render();
          },
          () -> {
            page = "wishes";
            render();
          }
        });
    sync =
        text(
            "✓ 마지막 동기화 "
                + java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                + "  ·  새로고침 ↻",
            11,
            MUTED,
            false);
    sync.setOnClickListener(v -> refresh(true));
    add(body, sync);
    if (page.equals("items")) items();
    else if (page.equals("wishes")) wishes();
    else home();
    LinearLayout nav = row();
    nav.setPadding(dp(12), dp(8), dp(12), dp(8));
    String[] labels = {"🏠 홈", "📜 기록", "⚙ 더보기"};
    Runnable[] actions = {
      () -> {
        page = "home";
        render();
      },
      this::logs,
      this::more
    };
    for (int i = 0; i < 3; i++) {
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(45), 1);
      if (i > 0) lp.leftMargin = dp(8);
      nav.addView(button(labels[i], i == 0 ? PURPLE : INK, actions[i]), lp);
    }
    add(root, nav);
    scroll.post(() -> scroll.scrollTo(0, oldY));
  }

  void home() {
    LinearLayout c = card(body);
    TextView round = text(s.round() + "회차 스티커판 ▾", 14, PURPLE, true);
    round.setOnClickListener(v -> history());
    add(c, round);
    add(c, text(s.board.optString("reward"), 27, INK, true));
    center(c, s.score + " / " + s.board.optInt("targetScore", 30), 42, PURPLE);
    center(c, "BEST 👍 " + s.best, 12, PURPLE);
    if (s.streak > 0) center(c, "🔥 " + s.streak + " COMBO", 13, 0xffea580c);
    if (!s.state.optString("dailyBuff").isEmpty())
      center(c, "🌟 스페셜 미션 · " + s.state.optString("dailyBuff") + " (+1칸)", 13, 0xffd97706);
    buttons(
        c,
        new String[] {"🎨 테마", "📤 공유"},
        new int[] {0xff64748b, GREEN},
        new Runnable[] {this::themes, this::shareCard});
    boardView = new BoardView(this, s);
    boardView.onLogs = this::logs;
    boardView.onLucky = step -> luckyEditor(step);
    LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, dp(420));
    vp.topMargin = dp(12);
    body.addView(boardView, vp);
    if (s.parent) {
      if (!s.requests.isEmpty())
        btn(body, "🙋 칭찬 요청 " + s.requests.size() + "건", GREEN, this::requests);
      if (!s.notifications.isEmpty())
        btn(body, "🔔 새 알림 " + s.notifications.size() + "건", 0xfff59e0b, this::showNotifications);
      buttons(
          body,
          new String[] {"칭찬 (+)", "차감 (-)", "만회 ↩"},
          new int[] {0xff3b82f6, 0xffef4444, 0xfff59e0b},
          new Runnable[] {
            () -> stampForm(false),
            () -> stampForm(true),
            () -> confirm("마지막 차감을 만회하고 1칸을 추가할까요?", () -> change("만회", () -> s.recover(id())))
          });
      if (!s.lastStamp.isEmpty())
        btn(
            body,
            "↩ 방금 찍은 도장 취소",
            0xff64748b,
            () -> confirm("방금 추가한 도장과 연속 보너스를 취소할까요?", () -> change("도장 취소", s::undo)));
      buttons(
          body,
          new String[] {"🎁 럭키박스", "🌟 미션"},
          new int[] {PINK, 0xfff59e0b},
          new Runnable[] {this::luckyList, this::mission});
      buttons(
          body,
          new String[] {"⚙ 판 관리", "📈 리포트"},
          new int[] {INK, 0xff3b82f6},
          new Runnable[] {this::settings, this::report});
      btn(body, "🎮 미사용시간 이월하기", 0xff0ea5e9, this::rollover);
    } else btn(body, "🙋 칭찬 도장 요청하기", GREEN, this::requestForm);
  }

  List<String> reasonNames(boolean penalty) {
    List<String> names = new ArrayList<>();
    JSONArray list = Store.array(s.state, penalty ? "penaltyReasons" : "praiseReasons");
    for (int i = 0; i < list.length(); i++)
      names.add(penalty ? list.optString(i) : list.optJSONObject(i).optString("name"));
    return names;
  }

  Spinner spinner(LinearLayout l, List<String> choices) {
    Spinner v = new Spinner(this);
    v.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices));
    l.addView(v, new LinearLayout.LayoutParams(-1, dp(52)));
    return v;
  }

  void stampForm(boolean penalty) {
    List<String> names = reasonNames(penalty);
    if (names.isEmpty()) {
      toast("판 관리에서 사유를 먼저 추가해주세요.");
      return;
    }
    LinearLayout c = col();
    Spinner reason = spinner(c, names);
    EditText detail = input(c, "상세 설명 (선택)", "", false);
    EditText count = input(c, "도장 개수", "1", true);
    if (!penalty)
      reason.setOnItemSelectedListener(
          new android.widget.AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(android.widget.AdapterView<?> p) {}

            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
              JSONArray a = Store.array(s.state, "praiseReasons");
              count.setText(String.valueOf(a.optJSONObject(pos).optInt("count", 1)));
            }
          });
    AlertDialog d = dialog(penalty ? "차감하기" : "칭찬하기", c);
    String op = id();
    btn(
        c,
        penalty ? "차감 도장 찍기" : "칭찬 도장 쾅!",
        penalty ? 0xffef4444 : PURPLE,
        () -> {
          try {
            int n = num(count);
            if (n < 1 || n > 100) throw new Exception("1~100개 사이로 입력해주세요.");
            String r = reason.getSelectedItem().toString(), dt = detail.getText().toString();
            d.dismiss();
            change("도장", () -> s.stamp(r, dt, penalty ? -n : n, op));
          } catch (Exception e) {
            error(e);
          }
        });
  }

  void items() {
    center(body, "🎒 아이템 보관함", 23, INK);
    buttons(
        body,
        new String[] {"🎁 보상", "🎰 랜덤박스"},
        new int[] {
          boxType.equals("보상") ? PURPLE : 0xff94a3b8, boxType.equals("랜덤박스") ? PINK : 0xff94a3b8
        },
        new Runnable[] {
          () -> {
            boxType = "보상";
            render();
          },
          () -> {
            boxType = "랜덤박스";
            render();
          }
        });
    int unused = 0, used = 0;
    for (JSONObject i : s.items)
      if (i.optString("type", "랜덤박스").equals(boxType)) {
        if (i.optString("status").equals("사용 전")) unused++;
        else used++;
      }
    buttons(
        body,
        new String[] {"사용 전 (" + unused + ")", "사용 후 (" + used + ")"},
        new int[] {
          itemStatus.equals("사용 전") ? PURPLE : 0xff94a3b8,
          itemStatus.equals("사용 후") ? PURPLE : 0xff94a3b8
        },
        new Runnable[] {
          () -> {
            itemStatus = "사용 전";
            render();
          },
          () -> {
            itemStatus = "사용 후";
            render();
          }
        });
    int shown = 0;
    for (JSONObject i : s.items) {
      if (!i.optString("type", "랜덤박스").equals(boxType) || !i.optString("status").equals(itemStatus))
        continue;
      shown++;
      LinearLayout c = card(body);
      add(
          c,
          text(
              (i.optString("name").equals("룰렛 추첨권") ? "🎟 " : "🎁 ") + i.optString("name"),
              19,
              INK,
              true));
      add(
          c,
          text(
              i.optInt("round") + "회차 · 획득 " + Store.date(i.optString("obtainedDate")),
              12,
              MUTED,
              false));
      if (!i.optString("gameSettlementDate").isEmpty())
        add(c, text("이월 원본: " + i.optString("gameSettlementDate"), 12, 0xff0284c7, false));
      if (itemStatus.equals("사용 후"))
        add(c, text("사용: " + Store.date(i.optString("usedDate")), 12, PINK, false));
      else if (s.parent) add(c, text("자녀 화면에서 사용할 수 있습니다.", 12, MUTED, false));
      else
        btn(c, i.optString("name").equals("룰렛 추첨권") ? "추첨하기 🎯" : "사용하기", PINK, () -> useItem(i));
    }
    if (shown == 0) center(body, "목록이 비어있어요.", 15, MUTED);
  }

  void useItem(JSONObject i) {
    String name = i.optString("name"), itemId = i.optString("__id");
    if (name.equals("룰렛 추첨권")) {
      roulette(i);
      return;
    }
    if (Store.retry(i)) {
      confirm("다시 돌리기를 사용하고 추첨권 1장을 받을까요?", () -> change("추첨권 지급", () -> s.use(itemId, 0, 0)));
      return;
    }
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(name);
    if (m.find()) {
      int max = Integer.parseInt(m.group());
      LinearLayout c = col();
      add(c, text("사용할 양을 입력하세요. 남은 양은 보관함에 유지됩니다.", 14, MUTED, false));
      EditText amount = input(c, "사용량 (1~" + max + ")", String.valueOf(max), true);
      AlertDialog d = dialog(name, c);
      btn(
          c,
          "사용하기",
          PINK,
          () -> {
            try {
              int n = num(amount);
              if (n < 1 || n > max) throw new Exception("1~" + max + " 사이로 입력해주세요.");
              d.dismiss();
              change("보상 사용", () -> s.use(itemId, n, max));
            } catch (Exception e) {
              error(e);
            }
          });
    } else confirm(name + " 보상을 사용할까요?", () -> change("보상 사용", () -> s.use(itemId, 0, 0)));
  }

  void roulette(JSONObject ticket) {
    try {
      String source = ticket.optString("prizesSnapshot");
      if (source.isEmpty()) {
        JSONArray boxes = Store.array(s.board, "luckyBoxes");
        if (boxes.length() > 0) source = boxes.getJSONObject(0).optString("text");
      }
      List<JSONObject> prizes = Store.prizes(source);
      LinearLayout c = col();
      WheelView wheel = new WheelView(this, prizes);
      c.addView(wheel, new LinearLayout.LayoutParams(-1, dp(310)));
      for (JSONObject p : prizes)
        add(
            c,
            text(
                p.optString("name") + " · " + Math.round(p.optDouble("weight") * 100) + "%",
                12,
                MUTED,
                true));
      AlertDialog d = dialog("🎰 행운의 룰렛", c);
      Button spin = button("돌리기 시작!", PINK, () -> {});
      add(c, spin);
      spin.setOnClickListener(
          v -> {
            if (busy) return;
            spin.setEnabled(false);
            d.setCancelable(false);
            d.getButton(-2).setEnabled(false);
            double draw = new java.security.SecureRandom().nextDouble(), acc = 0;
            int pick = prizes.size() - 1;
            for (int n = 0; n < prizes.size(); n++) {
              acc += prizes.get(n).optDouble("weight");
              if (draw < acc) {
                pick = n;
                break;
              }
            }
            double before = 0;
            for (int n = 0; n < pick; n++) before += prizes.get(n).optDouble("weight");
            float angle =
                1800 + (float) (360 - (before + prizes.get(pick).optDouble("weight") / 2) * 360);
            String won = prizes.get(pick).optString("name");
            ValueAnimator animation = ValueAnimator.ofFloat(0, angle);
            animation.setDuration(4000);
            animation.setInterpolator(new android.view.animation.DecelerateInterpolator(3));
            animation.addUpdateListener(
                a -> {
                  wheel.angle = (float) a.getAnimatedValue();
                  wheel.invalidate();
                });
            animation.start();
            main.postDelayed(
                () -> {
                  if (isDestroyed()) return;
                  spin.setText("🎉 " + won + " 당첨!");
                  run(
                      "당첨 저장 중…",
                      () -> {
                        s.win(ticket.optString("__id"), won);
                        s.load();
                      },
                      () -> {
                        d.dismiss();
                        render();
                        toast(won + " · 보관함에 저장했어요!");
                      });
                  d.setCancelable(true);
                  d.getButton(-2).setEnabled(true);
                },
                4100);
          });
    } catch (Exception e) {
      error(e);
    }
  }

  void wishes() {
    center(body, "🙏 소원수리함", 23, INK);
    if (!s.parent)
      btn(
          body,
          "새 소원 적기",
          0xfff59e0b,
          () -> {
            LinearLayout c = col();
            EditText wish = input(c, "어떤 소원을 이루고 싶나요?", "", false);
            AlertDialog d = dialog("소원 보내기", c);
            String op = id();
            btn(
                c,
                "보내기",
                0xfff59e0b,
                () -> {
                  String value = wish.getText().toString().trim();
                  if (value.isEmpty()) {
                    toast("소원을 적어주세요.");
                    return;
                  }
                  d.dismiss();
                  change(
                      "소원 등록",
                      () ->
                          s.add(
                              "wishes",
                              obj(
                                  "round",
                                  s.round(),
                                  "text",
                                  value,
                                  "date",
                                  Store.now(),
                                  "status",
                                  "대기 중",
                                  "comment",
                                  ""),
                              op));
                });
          });
    for (JSONObject w : s.wishes) {
      LinearLayout c = card(body);
      add(c, text(w.optString("text"), 18, INK, true));
      add(
          c,
          text(
              w.optInt("round")
                  + "회차 · "
                  + Store.date(w.optString("date"))
                  + " · "
                  + w.optString("status"),
              12,
              MUTED,
              false));
      if (!w.optString("comment").isEmpty())
        add(c, text("💬 " + w.optString("comment"), 14, PURPLE, false));
      if (s.parent) {
        buttons(
            c,
            new String[] {"이뤄주기", "다음에"},
            new int[] {GREEN, 0xff94a3b8},
            new Runnable[] {() -> wishReply(w, true), () -> wishReply(w, false)});
      } else if (w.optString("status").equals("대기 중"))
        btn(
            c,
            "삭제",
            0xff94a3b8,
            () ->
                confirm(
                    "이 소원을 삭제할까요?",
                    () ->
                        change(
                            "소원 삭제",
                            () ->
                                s.db.transaction(
                                    tx -> {
                                      JSONObject old = tx.get(w.optString("__path"));
                                      if (old != null && old.optString("status").equals("대기 중"))
                                        tx.remove(w.optString("__path"));
                                    }))));
    }
    if (s.wishes.isEmpty()) center(body, "아직 등록한 소원이 없어요.", 15, MUTED);
  }

  void wishReply(JSONObject w, boolean accept) {
    LinearLayout c = col();
    EditText comment = input(c, "강천이에게 남길 말", w.optString("comment"), false);
    AlertDialog d = dialog(accept ? "소원 이뤄주기" : "다음에 이뤄주기", c);
    btn(
        c,
        "저장",
        PURPLE,
        () -> {
          String value = comment.getText().toString();
          d.dismiss();
          change(
              "소원 답장",
              () ->
                  s.patch(
                      w.optString("__path"),
                      obj("status", accept ? "소원 성취! ✨" : "다음에 들어줄게 🙏", "comment", value)));
        });
  }

  void logs() {
    LinearLayout c = col();
    add(c, text("스티커판을 누르면 기록을 볼 수 있어요.", 12, MUTED, false));
    for (JSONObject l : s.logs) {
      LinearLayout cell = card(c);
      int score = l.optInt("score");
      add(
          cell,
          text(
              (score > 0 ? "+" : "") + score + "  " + l.optString("reason"),
              16,
              score < 0 ? 0xffef4444 : PURPLE,
              true));
      if (!l.optString("detail").isEmpty()) add(cell, text(l.optString("detail"), 14, INK, false));
      add(
          cell,
          text(
              Store.date(l.optString("date")) + " · " + l.optString("executor"), 12, MUTED, false));
      if (s.parent) {
        cell.setOnLongClickListener(
            v -> {
              editLog(l);
              return true;
            });
        btn(cell, "수정 / 삭제", 0xff94a3b8, () -> editLog(l));
      }
    }
    if (s.logs.isEmpty()) center(c, "아직 기록이 없어요.", 15, MUTED);
    dialog(s.round() + "회차 기록", c);
  }

  void editLog(JSONObject log) {
    LinearLayout c = col();
    EditText reason = input(c, "사유", log.optString("reason"), false),
        detail = input(c, "상세 설명", log.optString("detail"), false),
        amount = input(c, "개수 (차감은 음수)", String.valueOf(log.optInt("score")), true);
    AlertDialog d = dialog("기록 수정", c);
    btn(
        c,
        "저장",
        PURPLE,
        () -> {
          try {
            int count = num(amount);
            String r = reason.getText().toString().trim(), dt = detail.getText().toString();
            if (r.isEmpty()) throw new Exception("사유를 입력해주세요.");
            d.dismiss();
            change("기록 수정", () -> s.editLog(log.optString("__id"), r, dt, count, false));
          } catch (Exception e) {
            error(e);
          }
        });
    btn(
        c,
        "이 기록 삭제",
        0xffef4444,
        () ->
            confirm(
                "이 기록을 삭제할까요?",
                () -> {
                  d.dismiss();
                  change("기록 삭제", () -> s.editLog(log.optString("__id"), "", "", 0, true));
                }));
  }

  void requestForm() {
    List<String> names = reasonNames(false);
    if (names.isEmpty()) {
      toast("칭찬 사유가 없습니다.");
      return;
    }
    LinearLayout c = col();
    Spinner reason = spinner(c, names);
    EditText detail = input(c, "어떤 일을 했나요?", "", false);
    AlertDialog d = dialog("🙋 칭찬 도장 요청", c);
    String op = id();
    btn(
        c,
        "부모님께 요청하기",
        GREEN,
        () -> {
          String r = reason.getSelectedItem().toString(), dt = detail.getText().toString();
          d.dismiss();
          change(
              "칭찬 요청",
              () ->
                  s.add(
                      "requests",
                      obj(
                          "reason",
                          r,
                          "detail",
                          dt,
                          "childName",
                          "강천",
                          "date",
                          Store.now(),
                          "status",
                          "대기 중"),
                      op));
        });
  }

  void requests() {
    LinearLayout c = col();
    AlertDialog d = dialog("🙋 칭찬 요청", c);
    for (JSONObject r : s.requests) {
      LinearLayout cell = card(c);
      add(cell, text(r.optString("reason"), 17, INK, true));
      add(cell, text(r.optString("detail"), 14, MUTED, false));
      add(
          cell,
          text(
              r.optString("childName") + " · " + Store.date(r.optString("date")),
              12,
              MUTED,
              false));
      buttons(
          cell,
          new String[] {"도장 찍어주기", "확인만"},
          new int[] {GREEN, 0xff94a3b8},
          new Runnable[] {
            () -> {
              int count = 1;
              JSONArray a = Store.array(s.state, "praiseReasons");
              for (int k = 0; k < a.length(); k++)
                if (a.optJSONObject(k).optString("name").equals(r.optString("reason")))
                  count = a.optJSONObject(k).optInt("count", 1);
              final int n = count;
              confirm(
                  "칭찬 도장 " + n + "개를 찍을까요?",
                  () -> {
                    d.dismiss();
                    change(
                        "요청 승인",
                        () -> {
                          s.stamp(
                              r.optString("reason"),
                              r.optString("detail"),
                              n,
                              "request-" + r.optString("__id"));
                          s.patch(r.optString("__path"), obj("status", "확인함"));
                        });
                  });
            },
            () -> {
              d.dismiss();
              change("요청 확인", () -> s.patch(r.optString("__path"), obj("status", "확인함")));
            }
          });
    }
  }

  void showNotifications() {
    LinearLayout c = col();
    for (JSONObject n : s.notifications) {
      LinearLayout cell = card(c);
      add(cell, text(n.optString("text"), 16, INK, true));
      add(
          cell,
          text(
              n.optString("childName") + " · " + Store.date(n.optString("date")),
              12,
              MUTED,
              false));
    }
    AlertDialog d = dialog("🔔 새로운 소식", c);
    btn(
        c,
        "모두 확인했어요",
        0xfff59e0b,
        () -> {
          d.dismiss();
          change("알림 확인", s::dismissNotifications);
        });
  }

  void history() {
    LinearLayout c = col();
    AlertDialog d = dialog("🏆 회차 기록", c);
    List<JSONObject> boards = new ArrayList<>(s.boards);
    Collections.reverse(boards);
    for (JSONObject b : boards) {
      String days = b.has("daysTaken") ? " · " + b.optInt("daysTaken") + "일 만에 완성" : "";
      btn(
          c,
          b.optInt("round") + "회차 · " + b.optString("reward") + "\n" + b.optString("status") + days,
          b.optInt("round") == s.round() ? PURPLE : INK,
          () -> {
            s.selectedRound = b.optInt("round");
            d.dismiss();
            page = "home";
            refresh(false);
          });
    }
  }

  static final String[] THEME_IDS = {
    "rivals",
    "thermometer",
    "flower",
    "rocket",
    "lego",
    "tree",
    "balloon",
    "fishtank",
    "cake",
    "boardgame",
    "constellation",
    "footprint"
  };
  static final String[] THEME_NAMES = {
    "라이벌",
    "온도계",
    "칭찬의 꽃",
    "꿈의 로켓",
    "레고 성",
    "무럭무럭 나무",
    "열기구 여행",
    "우리 집 어항",
    "케이크 조각 모으기",
    "보드게임 말판",
    "별자리 만들기",
    "발자국 산책로"
  };

  void themes() {
    int current = Arrays.asList(THEME_IDS).indexOf(s.board.optString("theme"));
    dialogs++;
    new AlertDialog.Builder(this)
        .setTitle("🎨 스티커판 테마")
        .setSingleChoiceItems(
            THEME_NAMES,
            current,
            (d, n) -> {
              d.dismiss();
              change("테마 변경", () -> s.patch("boards/" + s.round(), obj("theme", THEME_IDS[n])));
            })
        .setNegativeButton("취소", null)
        .setOnDismissListener(d -> dialogs--)
        .show();
  }

  void levels() {
    LinearLayout c = col();
    add(c, text("50점마다 한 단계씩 성장해요.", 14, MUTED, false));
    for (int i = 0; i < Store.TITLES.length; i++)
      add(
          c,
          text(
              (i == 0 ? 0 : i * 50 + 1) + " pts · " + Store.TITLES[i],
              15,
              s.title().equals(Store.TITLES[i]) ? PURPLE : INK,
              s.title().equals(Store.TITLES[i])));
    dialog("🏅 성장 등급", c);
  }

  void mission() {
    List<String> names = reasonNames(false);
    LinearLayout c = col();
    Spinner sp = spinner(c, names);
    add(c, text("선택한 사유로 칭찬하면 기본 개수에 1칸을 더해요.", 14, MUTED, false));
    AlertDialog d = dialog("🌟 오늘의 스페셜 미션", c);
    if (!names.isEmpty())
      btn(
          c,
          "미션 적용",
          0xfff59e0b,
          () -> {
            String value = sp.getSelectedItem().toString();
            d.dismiss();
            change("미션 설정", () -> s.patch("meta/state", obj("dailyBuff", value)));
          });
    btn(
        c,
        "미션 해제",
        0xff94a3b8,
        () -> {
          d.dismiss();
          change("미션 해제", () -> s.patch("meta/state", obj("dailyBuff", "")));
        });
  }

  void rollover() {
    LinearLayout c = col();
    add(c, text("기본은 토·일 60분을 모두 사용한 것으로 처리합니다. 남은 시간만 입력해주세요.", 14, MUTED, false));
    LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul"));
    while (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY)
      date = date.minusDays(1);
    EditText day = input(c, "날짜", date.toString(), false);
    day.setFocusable(false);
    day.setOnClickListener(
        v -> {
          LocalDate current = LocalDate.parse(day.getText());
          DatePickerDialog picker =
              new DatePickerDialog(
                  this,
                  (view, y, m, d) -> day.setText(LocalDate.of(y, m + 1, d).toString()),
                  current.getYear(),
                  current.getMonthValue() - 1,
                  current.getDayOfMonth());
          picker.getDatePicker().setMaxDate(System.currentTimeMillis());
          picker.show();
        });
    EditText mins = input(c, "미사용 시간 (1~60분)", "30", true);
    add(c, text("같은 날짜를 다시 저장하면 해당 날짜의 총 이월시간을 수정합니다. 이미 사용한 시간은 유지합니다.", 12, MUTED, false));
    AlertDialog d = dialog("🎮 미사용시간 이월하기", c);
    btn(
        c,
        "이월 저장",
        0xff0ea5e9,
        () -> {
          try {
            int n = num(mins);
            String value = day.getText().toString();
            d.dismiss();
            change("시간 이월", () -> s.rollover(value, n));
          } catch (Exception e) {
            error(e);
          }
        });
  }

  void settings() {
    LinearLayout c = col();
    EditText target = input(c, "목표 개수", String.valueOf(s.board.optInt("targetScore", 30)), true),
        reward = input(c, "완성 보상", s.board.optString("reward"), false);
    AlertDialog d = dialog("⚙ 판 관리", c);
    btn(
        c,
        "현재 회차 저장",
        PURPLE,
        () -> {
          try {
            int n = num(target);
            String value = reward.getText().toString().trim();
            if (n < 1 || n > 1000 || value.isEmpty())
              throw new Exception("목표(1~1000)와 보상을 입력해주세요.");
            d.dismiss();
            change(
                "스티커판 수정",
                () -> s.patch("boards/" + s.round(), obj("targetScore", n, "reward", value)));
          } catch (Exception e) {
            error(e);
          }
        });
    btn(
        c,
        "새 회차 시작",
        GREEN,
        () -> {
          try {
            int n = num(target);
            String value = reward.getText().toString();
            confirm(
                "현재 회차를 마무리하고 새 회차를 시작할까요?",
                () -> {
                  d.dismiss();
                  change("새 회차", () -> s.newBoard(n, value));
                });
          } catch (Exception e) {
            error(e);
          }
        });
    buttons(
        c,
        new String[] {"칭찬 사유 관리", "차감 사유 관리"},
        new int[] {PURPLE, 0xffef4444},
        new Runnable[] {() -> reasons(false), () -> reasons(true)});
    btn(
        c,
        "놓친 연속 보너스 확인·보정",
        0xff64748b,
        () ->
            confirm(
                "현재 회차에서 빠진 연속 보너스를 보정할까요?",
                () -> {
                  d.dismiss();
                  change("보너스 보정", s::repairBonuses);
                }));
    btn(
        c,
        "과거 회차 완성 기간 채우기",
        0xff64748b,
        () -> {
          d.dismiss();
          change("완성 기간 계산", s::backfill);
        });
  }

  void reasons(boolean penalty) {
    LinearLayout c = col();
    add(
        c,
        text(
            "한 줄에 한 사유를 입력하세요." + (penalty ? "" : " 개수는 이름 뒤에 | 숫자로 적습니다.\n예: 숙제하기 | 2"),
            14,
            MUTED,
            false));
    JSONArray list = Store.array(s.state, penalty ? "penaltyReasons" : "praiseReasons");
    StringBuilder lines = new StringBuilder();
    for (int i = 0; i < list.length(); i++) {
      if (i > 0) lines.append('\n');
      lines.append(
          penalty
              ? list.optString(i)
              : list.optJSONObject(i).optString("name")
                  + " | "
                  + list.optJSONObject(i).optInt("count", 1));
    }
    EditText editor = input(c, "사유 목록", lines.toString(), false);
    editor.setMinLines(8);
    editor.setGravity(Gravity.TOP);
    AlertDialog d = dialog(penalty ? "차감 사유 관리" : "칭찬 사유 관리", c);
    btn(
        c,
        "저장",
        PURPLE,
        () -> {
          try {
            JSONArray updated = new JSONArray();
            for (String line : editor.getText().toString().split("\n")) {
              if (line.trim().isEmpty()) continue;
              if (penalty) updated.put(line.trim());
              else {
                int sep = line.lastIndexOf('|');
                String name = sep < 0 ? line.trim() : line.substring(0, sep).trim();
                int n = sep < 0 ? 1 : Integer.parseInt(line.substring(sep + 1).trim());
                if (name.isEmpty() || n < 1 || n > 100)
                  throw new Exception("사유와 개수(1~100)를 확인해주세요.");
                updated.put(obj("name", name, "count", n));
              }
            }
            d.dismiss();
            change(
                "사유 목록 저장",
                () ->
                    s.patch(
                        "meta/state", obj(penalty ? "penaltyReasons" : "praiseReasons", updated)));
          } catch (Exception e) {
            error(e);
          }
        });
  }

  void luckyList() {
    LinearLayout c = col();
    JSONArray boxes = Store.array(s.board, "luckyBoxes");
    for (int i = 0; i < boxes.length(); i++) {
      JSONObject box = boxes.optJSONObject(i);
      btn(c, "🎁 " + box.optInt("step") + "번째 칸", PINK, () -> luckyEditor(box.optInt("step")));
    }
    btn(c, "+ 럭키박스 추가", PURPLE, () -> luckyEditor(0));
    dialog("🎁 럭키박스", c);
  }

  void luckyEditor(int step) {
    LinearLayout c = col();
    JSONArray boxes = Store.array(s.board, "luckyBoxes");
    String source = "";
    for (int i = 0; i < boxes.length(); i++)
      if (boxes.optJSONObject(i).optInt("step") == step)
        source = boxes.optJSONObject(i).optString("text");
    EditText at = input(c, "몇 번째 칸인가요?", step == 0 ? "5" : String.valueOf(step), true);
    StringBuilder lines = new StringBuilder();
    try {
      if (!source.isEmpty()) {
        for (JSONObject p : Store.prizes(source)) {
          if (lines.length() > 0) lines.append('\n');
          lines.append(p.optString("name"));
          if (!p.isNull("ratio") && p.has("ratio")) lines.append(":").append(p.optDouble("ratio"));
        }
      }
    } catch (Exception ignored) {
    }
    add(c, text("한 줄에 보상 하나. 확률은 ‘이름:숫자’로 입력하세요. 빈 확률은 남은 확률을 나눠 가집니다.", 13, MUTED, false));
    EditText rewards = input(c, "게임 10분:10\n룰렛 다시돌리기", lines.toString(), false);
    rewards.setMinLines(5);
    AlertDialog d = dialog("럭키박스 편집", c);
    btn(
        c,
        "저장",
        PINK,
        () -> {
          try {
            int pos = num(at);
            if (pos < 1 || pos > s.board.optInt("targetScore"))
              throw new Exception("스티커판 범위 안의 칸을 선택해주세요.");
            JSONArray prizes = new JSONArray();
            for (String line : rewards.getText().toString().split("\n")) {
              line = line.trim();
              if (line.isEmpty()) continue;
              int colon = line.lastIndexOf(':');
              String name = colon < 0 ? line : line.substring(0, colon).trim();
              Double ratio = null;
              if (colon >= 0 && !line.substring(colon + 1).trim().isEmpty()) {
                ratio = Double.parseDouble(line.substring(colon + 1).trim());
                if (ratio <= 0) ratio = null;
              }
              if (name.isEmpty()) throw new Exception("보상 이름을 확인해주세요.");
              prizes.put(obj("name", name, "ratio", ratio));
            }
            if (prizes.length() == 0) throw new Exception("보상을 하나 이상 입력해주세요.");
            JSONArray out = new JSONArray();
            for (int i = 0; i < boxes.length(); i++) {
              JSONObject old = boxes.optJSONObject(i);
              if (old.optInt("step") != step && old.optInt("step") != pos) out.put(old);
            }
            out.put(obj("step", pos, "text", prizes.toString()));
            d.dismiss();
            change("럭키박스 저장", () -> s.patch("boards/" + s.round(), obj("luckyBoxes", out)));
          } catch (Exception e) {
            error(e);
          }
        });
    if (step > 0)
      btn(
          c,
          "삭제",
          0xffef4444,
          () ->
              confirm(
                  "이 칸의 럭키박스를 삭제할까요?",
                  () -> {
                    JSONArray out = new JSONArray();
                    for (int i = 0; i < boxes.length(); i++)
                      if (boxes.optJSONObject(i).optInt("step") != step)
                        out.put(boxes.optJSONObject(i));
                    d.dismiss();
                    change("럭키박스 삭제", () -> s.patch("boards/" + s.round(), obj("luckyBoxes", out)));
                  }));
  }

  void report() {
    final List<JSONObject>[] all = new List[] {null};
    run(
        "통계 계산 중…",
        () -> all[0] = s.db.query("logs", null, null, null),
        () -> {
          int praise = 0, penalty = 0;
          int[] days = new int[7];
          Map<String, Integer> counts = new HashMap<>();
          for (JSONObject l : all[0]) {
            int score = l.optInt("score");
            if (score > 0 && !l.optString("executor").equals("시스템")) {
              praise++;
              String reason = l.optString("reason");
              counts.put(reason, counts.getOrDefault(reason, 0) + 1);
              try {
                int weekday =
                    LocalDate.parse(Store.day(l.optString("date"))).getDayOfWeek().getValue() % 7;
                days[weekday]++;
              } catch (Exception ignored) {
              }
            } else if (score < 0) penalty++;
          }
          LinearLayout c = col();
          center(c, "칭찬 " + praise + "회  ·  차감 " + penalty + "회", 22, PURPLE);
          add(c, text("자주 칭찬받은 일 TOP 5", 17, INK, true));
          List<String> keys = new ArrayList<>(counts.keySet());
          keys.sort((a, b) -> counts.get(b) - counts.get(a));
          for (int i = 0; i < Math.min(5, keys.size()); i++)
            add(
                c,
                text(
                    (i + 1) + ". " + keys.get(i) + "  " + counts.get(keys.get(i)) + "회",
                    15,
                    INK,
                    false));
          add(c, text("요일별 칭찬", 17, INK, true));
          int max = 1;
          for (int value : days) max = Math.max(max, value);
          for (int i = 0; i < 7; i++) {
            add(c, text("일월화수목금토".charAt(i) + "요일 · " + days[i] + "회", 13, MUTED, true));
            ProgressBar bar =
                new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(max);
            bar.setProgress(days[i]);
            add(c, bar);
          }
          dialog("📈 성장 리포트", c);
        });
  }

  void more() {
    LinearLayout c = col();
    btn(c, "↻ 지금 동기화", PURPLE, () -> refresh(true));
    btn(c, "🏆 회차 기록", PURPLE, this::history);
    btn(c, "📤 현황 이미지 공유", GREEN, this::shareCard);
    if (s.parent) {
      btn(c, "📥 전체 데이터 CSV 저장", INK, this::export);
      btn(
          c,
          "🔑 부모 비밀번호 변경",
          INK,
          () -> {
            LinearLayout f = col();
            EditText old = input(f, "현재 비밀번호", "", false),
                next = input(f, "새 비밀번호 (4자리 이상)", "", false);
            old.setInputType(129);
            next.setInputType(129);
            AlertDialog d = dialog("비밀번호 변경", f);
            btn(
                f,
                "변경",
                PURPLE,
                () -> {
                  String a = old.getText().toString(), b = next.getText().toString();
                  d.dismiss();
                  change(
                      "비밀번호 변경",
                      () -> {
                        s.password(a, b);
                        SecureSession.save(this, b);
                      });
                });
          });
    }
    add(
        c,
        text(
            "칭찬 스티커 1.0.0\n"
                + "네이티브 Android 앱 · WebView 미사용\n\n"
                + "앱을 열 때, 작업 후, 화면을 보고 있는 동안 60초 간격으로 동기화합니다. 웹에서는 새로고침하면 최신 내용이 표시됩니다.\n\n"
                + "화면과 테마는 네이티브로 다시 그렸으며 저장·공유 창은 Android 기본 화면입니다.",
            12,
            MUTED,
            false));
    btn(
        c,
        "로그아웃",
        0xff64748b,
        () ->
            confirm(
                "로그아웃할까요?",
                () -> {
                  getPreferences(0).edit().remove("role").apply();
                  SecureSession.clear(this);
                  s.selectedRound = 0;
                  logged = false;
                  recreate();
                }));
    dialog("더보기", c);
  }

  static String csv(String v) {
    return "\"" + v.replace("\"", "\"\"") + "\"";
  }

  void export() {
    run(
        "백업 파일 준비 중…",
        () -> {
          StringBuilder out =
              new StringBuilder("\ufeff## Sheet name: Logs\n회차,날짜,사유,상세설명,실행자,점수\n");
          List<JSONObject> all = s.db.query("logs", null, null, null);
          Store.sortDate(all, "date", false);
          for (JSONObject l : all)
            out.append(l.optInt("round"))
                .append(',')
                .append(csv(l.optString("date")))
                .append(',')
                .append(csv(l.optString("reason")))
                .append(',')
                .append(csv(l.optString("detail")))
                .append(',')
                .append(csv(l.optString("executor")))
                .append(',')
                .append(l.optInt("score"))
                .append('\n');
          out.append("\n## Sheet name: Items\n회차,아이템명,획득일,상태,사용일,종류\n");
          for (JSONObject i : s.items)
            out.append(i.optInt("round"))
                .append(',')
                .append(csv(i.optString("name")))
                .append(',')
                .append(csv(i.optString("obtainedDate")))
                .append(',')
                .append(csv(i.optString("status")))
                .append(',')
                .append(csv(i.optString("usedDate")))
                .append(',')
                .append(csv(i.optString("type")))
                .append('\n');
          out.append("\n## Sheet name: Wishes\n회차,소원내용,날짜,상태,부모코멘트\n");
          for (JSONObject w : s.wishes)
            out.append(w.optInt("round"))
                .append(',')
                .append(csv(w.optString("text")))
                .append(',')
                .append(csv(w.optString("date")))
                .append(',')
                .append(csv(w.optString("status")))
                .append(',')
                .append(csv(w.optString("comment")))
                .append('\n');
          out.append("\n## Sheet name: Status\n회차,목표개수,보상,테마,상태,럭키박스\n");
          for (JSONObject b : s.boards)
            out.append(b.optInt("round"))
                .append(',')
                .append(b.optInt("targetScore"))
                .append(',')
                .append(csv(b.optString("reward")))
                .append(',')
                .append(csv(b.optString("theme")))
                .append(',')
                .append(csv(b.optString("status")))
                .append(',')
                .append(csv(Store.array(b, "luckyBoxes").toString()))
                .append('\n');
          out.append("\n## Sheet name: Data\n칭찬사유,기본개수\n");
          JSONArray reasons = Store.array(s.state, "praiseReasons");
          for (int i = 0; i < reasons.length(); i++) {
            JSONObject r = reasons.optJSONObject(i);
            out.append(csv(r.optString("name")))
                .append(',')
                .append(r.optInt("count", 1))
                .append('\n');
          }
          out.append("\n차감사유\n");
          JSONArray penalties = Store.array(s.state, "penaltyReasons");
          for (int i = 0; i < penalties.length(); i++)
            out.append(csv(penalties.optString(i))).append('\n');
          out.append("\n누적점수,").append(s.state.optInt("lifetimeScore")).append('\n');
          exportBytes = out.toString().getBytes("UTF-8");
        },
        () -> {
          Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
          i.addCategory(Intent.CATEGORY_OPENABLE);
          i.setType("text/csv");
          i.putExtra(Intent.EXTRA_TITLE, "칭찬스티커_백업_" + LocalDate.now() + ".csv");
          startActivityForResult(i, 41);
        });
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 41
        && result == RESULT_OK
        && data != null
        && data.getData() != null
        && exportBytes != null) {
      Uri uri = data.getData();
      run(
          "파일 저장 중…",
          () -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
              if (out == null) throw new IOException("파일을 열 수 없습니다.");
              out.write(exportBytes);
            }
          },
          () -> toast("백업 파일을 저장했어요."));
    }
  }

  void shareCard() {
    try {
      LinearLayout c = col();
      c.setPadding(dp(24), dp(24), dp(24), dp(24));
      c.setBackgroundColor(WHITE);
      center(c, "⭐ 강천이의 칭찬 스티커", 25, PURPLE);
      center(c, s.title() + " · " + s.state.optInt("lifetimeScore") + " pts", 14, MUTED);
      center(c, s.round() + "회차 · " + s.board.optString("reward"), 20, INK);
      center(c, s.score + " / " + s.board.optInt("targetScore"), 36, PURPLE);
      BoardView board = new BoardView(this, s);
      c.addView(board, new LinearLayout.LayoutParams(-1, dp(420)));
      center(c, LocalDate.now().toString(), 12, MUTED);
      int width = dp(390);
      c.measure(
          View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
      c.layout(0, 0, width, c.getMeasuredHeight());
      Bitmap image = Bitmap.createBitmap(width, c.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
      c.draw(new Canvas(image));
      File folder = new File(getCacheDir(), "share");
      folder.mkdirs();
      File file = new File(folder, "praise-sticker.png");
      try (OutputStream out = new FileOutputStream(file)) {
        image.compress(Bitmap.CompressFormat.PNG, 100, out);
      }
      image.recycle();
      Uri uri = Uri.parse("content://kr.family.praisesticker.share/praise-sticker.png");
      Intent send = new Intent(Intent.ACTION_SEND);
      send.setType("image/png");
      send.putExtra(Intent.EXTRA_STREAM, uri);
      send.putExtra(Intent.EXTRA_TEXT, "강천이의 칭찬 스티커 현황이에요! 🎉");
      send.setClipData(ClipData.newRawUri("칭찬 스티커", uri));
      send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(Intent.createChooser(send, "현황 이미지 공유"));
    } catch (Exception e) {
      error(e);
    }
  }
}
