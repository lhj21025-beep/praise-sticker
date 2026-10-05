package kr.family.praisesticker;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;
import org.json.*;

final class WheelView extends View {
  final List<JSONObject> prizes;
  final Paint p = new Paint(3);
  float angle;
  static final int[] COLORS = {
    0xfff87171, 0xfffb923c, 0xfffbbf24, 0xff34d399, 0xff38bdf8, 0xff818cf8, 0xffc084fc, 0xfff472b6
  };

  WheelView(Context c, List<JSONObject> p) {
    super(c);
    prizes = p;
    setContentDescription("보상 룰렛");
  }

  protected void onDraw(Canvas c) {
    float size = Math.min(getWidth(), getHeight()) - 24,
        cx = getWidth() / 2f,
        cy = getHeight() / 2f;
    RectF oval = new RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
    c.save();
    c.rotate(angle, cx, cy);
    float start = -90;
    for (int i = 0; i < prizes.size(); i++) {
      JSONObject v = prizes.get(i);
      float sweep = (float) v.optDouble("weight") * 360;
      p.setColor(COLORS[i % COLORS.length]);
      c.drawArc(oval, start, sweep, true, p);
      c.save();
      c.rotate(start + sweep / 2, cx, cy);
      p.setColor(Color.WHITE);
      p.setTypeface(Typeface.DEFAULT_BOLD);
      p.setTextSize(size / 25);
      p.setTextAlign(Paint.Align.RIGHT);
      String label = v.optString("name");
      if (label.length() > 10) label = label.substring(0, 9) + "…";
      c.drawText(label, cx + size / 2 - 12, cy + 5, p);
      c.restore();
      start += sweep;
    }
    c.restore();
    p.setColor(Color.WHITE);
    c.drawCircle(cx, cy, 22, p);
    p.setColor(0xff334155);
    Path arrow = new Path();
    arrow.moveTo(cx - 12, 4);
    arrow.lineTo(cx + 12, 4);
    arrow.lineTo(cx, 25);
    arrow.close();
    c.drawPath(arrow, p);
  }
}
