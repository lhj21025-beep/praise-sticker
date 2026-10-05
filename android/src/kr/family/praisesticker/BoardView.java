package kr.family.praisesticker;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;
import org.json.*;

final class BoardView extends View {
  final Paint p = new Paint(3);
  final Store store;
  float progress;
  Runnable onLogs;
  java.util.function.IntConsumer onLucky;
  final List<RectF> hit = new ArrayList<>();
  final List<Integer> steps = new ArrayList<>();

  BoardView(Context c, Store s) {
    super(c);
    store = s;
    progress = s.score;
    setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    setContentDescription("스티커판 " + s.score + " / " + s.board.optInt("targetScore"));
  }

  int color(String h) {
    return Color.parseColor(h);
  }

  void fill(Canvas c, int color, float l, float t, float r, float b, float radius) {
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setColor(color);
    c.drawRoundRect(l, t, r, b, radius, radius, p);
  }

  void text(Canvas c, String text, float x, float y, float size, int color) {
    p.setShader(null);
    p.setStyle(Paint.Style.FILL);
    p.setTextAlign(Paint.Align.CENTER);
    p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    p.setTextSize(size);
    p.setColor(color);
    c.drawText(text, x, y, p);
  }

  void line(Canvas c, float x, float y, float xx, float yy, int color, float w) {
    p.setShader(null);
    p.setColor(color);
    p.setStrokeWidth(w);
    c.drawLine(x, y, xx, yy, p);
  }

  protected void onDraw(Canvas original) {
    super.onDraw(original);
    Canvas c = original;
    c.save();
    c.scale(getWidth() / 360f, getHeight() / 420f);
    int n = Math.max(1, store.board.optInt("targetScore", 30)), active = Math.min(n, store.score);
    String theme = store.board.optString("theme", "rivals");
    Set<Integer> lucky = new HashSet<>();
    JSONArray boxes = Store.array(store.board, "luckyBoxes");
    for (int i = 0; i < boxes.length(); i++) lucky.add(boxes.optJSONObject(i).optInt("step"));
    hit.clear();
    steps.clear();
    fill(c, color("#F8FAFC"), 0, 0, 360, 420, 22);
    if (theme.equals("rivals")
        || theme.equals("lego")
        || theme.equals("boardgame")
        || theme.equals("footprint")) {
      int cols = n > 30 ? 6 : 5, rows = (n + cols - 1) / cols;
      float cell = Math.min(55, 350f / rows), cw = 330f / cols;
      for (int i = 1; i <= n; i++) {
        int rr = (i - 1) / cols, cc = (i - 1) % cols;
        if ((theme.equals("boardgame") || theme.equals("footprint")) && rr % 2 == 1)
          cc = cols - 1 - cc;
        float x = 15 + cc * cw, y = 16 + rr * cell;
        boolean on = i <= active;
        int tone =
            theme.equals("lego")
                ? new int[] {0xfff87171, 0xfffbbf24, 0xff34d399, 0xff60a5fa, 0xffa78bfa}
                    [(i - 1) % 5]
                : 0xff6366f1;
        fill(c, on ? tone : 0xffe2e8f0, x + 3, y + 3, x + cw - 3, y + cell - 3, 8);
        if (theme.equals("lego")) {
          fill(c, on ? tone : 0xffe2e8f0, x + cw * .25f, y - 1, x + cw * .4f, y + 6, 3);
          fill(c, on ? tone : 0xffe2e8f0, x + cw * .6f, y - 1, x + cw * .75f, y + 6, 3);
        }
        if (lucky.contains(i)) {
          p.setStyle(Paint.Style.STROKE);
          p.setColor(0xfffbbf24);
          p.setStrokeWidth(3);
          c.drawRoundRect(x + 3, y + 3, x + cw - 3, y + cell - 3, 8, 8, p);
          p.setStyle(Paint.Style.FILL);
          hit.add(new RectF(x, y, x + cw, y + cell));
          steps.add(i);
        }
        text(
            c,
            lucky.contains(i) && !on
                ? "🎁"
                : theme.equals("footprint") && on ? "🐾" : String.valueOf(i),
            x + cw / 2,
            y + cell * .67f,
            Math.min(20, cell * .4f),
            on ? Color.WHITE : 0xff94a3b8);
      }
    } else if (theme.equals("thermometer")) {
      text(c, "칭찬 온도계", 180, 35, 17, 0xffef4444);
      fill(c, 0xffcbd5e1, 150, 60, 210, 340, 28);
      fill(c, 0xfff1f5f9, 157, 65, 203, 340, 20);
      fill(c, 0xffef4444, 158, 335 - 265f * active / n, 202, 350, 18);
      p.setColor(0xffef4444);
      c.drawCircle(180, 357, 42, p);
      text(c, Math.round(100f * active / n) + "%", 180, 363, 20, Color.WHITE);
      for (int i = 1; i <= n; i++) {
        float y = 335 - 265f * i / n;
        line(c, 144, y, 153, y, 0xff94a3b8, 2);
        if (lucky.contains(i)) {
          text(c, "🎁", 233, y + 7, 21, Color.BLACK);
          hit.add(new RectF(213, y - 15, 260, y + 20));
          steps.add(i);
        }
      }
    } else if (theme.equals("rocket") || theme.equals("balloon") || theme.equals("constellation")) {
      p.setShader(
          new LinearGradient(
              0,
              0,
              0,
              420,
              new int[] {0xff0f172a, 0xff4338ca, 0xff818cf8},
              null,
              Shader.TileMode.CLAMP));
      c.drawRoundRect(0, 0, 360, 420, 22, 22, p);
      p.setShader(null);
      Random rnd = new Random(17);
      for (int i = 0; i < 35; i++)
        text(c, "·", rnd.nextInt(340) + 10, rnd.nextInt(395) + 10, 20, 0xffc7d2fe);
      if (theme.equals("constellation")) {
        float px = 0, py = 0;
        for (int i = 1; i <= n; i++) {
          float x = 35 + ((i - 1) % 5) * 70,
              y = 50 + ((i - 1) / 5) * Math.min(52, 330f / Math.max(1, (n + 4) / 5));
          if (i > 1) line(c, px, py, x, y, i <= active ? 0xfffde68a : 0xff64748b, 2);
          text(c, i <= active ? "★" : "☆", x, y, 25, i <= active ? 0xfffde68a : 0xff94a3b8);
          px = x;
          py = y;
        }
      } else {
        line(c, 180, 370, 180, 55, 0xffa5b4fc, 3);
        text(c, "🏁", 180, 40, 27, Color.WHITE);
        for (int i : lucky) {
          float y = 370 - 300f * i / n;
          text(c, "🎁", 180, y, 24, Color.WHITE);
          hit.add(new RectF(150, y - 25, 210, y + 8));
          steps.add(i);
        }
        text(
            c, theme.equals("rocket") ? "🚀" : "🎈", 180, 385 - 300f * active / n, 55, Color.WHITE);
        text(c, active + " / " + n, 180, 405, 16, Color.WHITE);
      }
    } else if (theme.equals("cake")) {
      text(c, "달콤한 칭찬 케이크", 180, 42, 18, 0xffbe185d);
      RectF circle = new RectF(50, 80, 310, 340);
      for (int i = 0; i < n; i++) {
        p.setColor(i < active ? new int[] {0xfff9a8d4, 0xfffbcfe8, 0xfff472b6}[i % 3] : 0xffe2e8f0);
        c.drawArc(circle, -90 + i * 360f / n, 360f / n - 1, true, p);
      }
      text(c, "🍓", 180, 223, 58, Color.BLACK);
      text(c, active + "조각 모았어요!", 180, 380, 20, 0xffbe185d);
    } else {
      boolean fish = theme.equals("fishtank"), tree = theme.equals("tree");
      fill(c, fish ? 0xffdbeafe : 0xffe0f2fe, 0, 0, 360, 420, 22);
      fill(c, fish ? 0xfffde68a : 0xff86efac, 0, 330, 360, 420, 18);
      text(c, fish ? "🫧" : "☀️", 310, 55, 35, 0xfff59e0b);
      if (tree) {
        fill(c, 0xff92400e, 165, 150, 195, 350, 8);
        p.setColor(0xff4ade80);
        c.drawCircle(180, 175, 110, p);
      }
      int cols = 6;
      for (int i = 1; i <= n; i++) {
        float x = 30 + ((i - 1) % cols) * 60,
            y =
                tree
                    ? 90 + ((i - 1) / cols) * Math.min(42, 230f / Math.max(1, (n + 5) / 6))
                    : 400 - ((i - 1) / cols) * Math.min(42, 280f / Math.max(1, (n + 5) / 6));
        String emoji =
            i <= active ? (fish ? "🐠" : tree ? "🍎" : "🌸") : (fish ? "🫧" : tree ? "🍃" : "🌱");
        text(
            c,
            lucky.contains(i) && i > active ? "🎁" : emoji,
            x,
            y,
            29,
            i <= active ? 0xff16a34a : 0xff94a3b8);
        if (lucky.contains(i)) {
          hit.add(new RectF(x - 22, y - 28, x + 22, y + 10));
          steps.add(i);
        }
      }
    }
    if (store.board.optString("status").equals("보상 수령 완료")) {
      fill(c, 0xeefFFFFF, 22, 155, 338, 254, 20);
      text(c, "GOAL!! 🏆", 180, 195, 27, 0xff6366f1);
      text(c, "보상이 보관함에 담겼어요! 🎁", 180, 231, 15, 0xff64748b);
    }
    c.restore();
  }

  public boolean onTouchEvent(android.view.MotionEvent e) {
    if (e.getAction() == MotionEvent.ACTION_UP) {
      float x = e.getX() * 360 / getWidth(), y = e.getY() * 420 / getHeight();
      for (int i = 0; i < hit.size(); i++)
        if (hit.get(i).contains(x, y) && store.parent && onLucky != null) {
          onLucky.accept(steps.get(i));
          return true;
        }
      if (onLogs != null) onLogs.run();
      performClick();
      return true;
    }
    return true;
  }

  public boolean performClick() {
    super.performClick();
    return true;
  }
}
