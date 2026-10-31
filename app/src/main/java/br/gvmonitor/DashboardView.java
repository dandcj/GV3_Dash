package br.gvmonitor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** Painel desenhado em Canvas: dois medidores (ATF e agua), centro com RPM e linha de cards embaixo. */
final class DashboardView extends View {
    private static final int BG_TOP = Color.rgb(8, 20, 46);
    private static final int BG_BOTTOM = Color.rgb(3, 8, 20);
    private static final int CYAN = Color.rgb(25, 227, 255);
    private static final int AMBER = Color.rgb(255, 176, 32);
    private static final int RED = Color.rgb(255, 59, 59);
    private static final int TRACK = Color.argb(70, 120, 170, 220);
    private static final int TEXT_DIM = Color.rgb(140, 165, 200);

    // Dados (null = sem leitura valida)
    Integer atf, coolant;
    float rpm, batt, tps;
    boolean hasData;
    int btState, tcmState;
    String message = "";
    int alarmLevel;           // 0 ok, 1 atencao, 2 critico
    boolean commLost;
    Bitmap logo;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float dp;

    DashboardView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
    }

    private int levelColor(Integer v, int warn, int crit) {
        if (v == null) return TEXT_DIM;
        if (v >= crit) return RED;
        if (v >= warn) return AMBER;
        return CYAN;
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();

        // Fundo
        p.reset();
        p.setAntiAlias(true);
        p.setShader(new LinearGradient(0, 0, 0, h, BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(null);

        float topH = h * 0.13f;
        float botH = h * 0.24f;
        float margin = 16 * dp;

        drawTop(cv, w, topH);

        float areaH = h - topH - botH;
        float cy = topH + areaH / 2f;
        float r = Math.min(w * 0.235f, areaH * 0.5f) - 8 * dp;

        int atfColor = levelColor(atf, Config.ATF_WARN_C, Config.ATF_CRIT_C);
        drawGauge(cv, w * 0.21f, cy, r, "ATF", "TEMP. CAMBIO",
                atf, Config.ATF_MIN, Config.ATF_MAX, atfColor);
        drawGauge(cv, w * 0.79f, cy, r, "AGUA", "TEMP. MOTOR",
                coolant, Config.ENG_MIN, Config.ENG_MAX, levelColor(coolant, 100, 110));

        drawCenter(cv, w * 0.5f, cy, Math.min(w * 0.13f, areaH * 0.42f));
        drawBottom(cv, w, h, botH, margin);

        if (alarmLevel > 0) {
            // moldura pulsante simples
            boolean on = (System.currentTimeMillis() / 400) % 2 == 0;
            p.reset();
            p.setAntiAlias(true);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6 * dp);
            int col = alarmLevel == 2 ? RED : AMBER;
            p.setColor(on ? col : Color.argb(90, Color.red(col), Color.green(col), Color.blue(col)));
            cv.drawRect(3 * dp, 3 * dp, w - 3 * dp, h - 3 * dp, p);
            postInvalidateDelayed(200);
        }
    }

    private void drawTop(Canvas cv, int w, float topH) {
        float pad = 12 * dp;
        float logoH = topH - pad;
        // Slot do logo (arquivo res/drawable/logo_suzuki.png fornecido por voce)
        if (logo != null) {
            float lw = logoH * logo.getWidth() / (float) logo.getHeight();
            rect.set(pad, pad / 2f, pad + lw, pad / 2f + logoH);
            p.reset();
            p.setAntiAlias(true);
            p.setFilterBitmap(true);
            cv.drawBitmap(logo, null, rect, p);
        }

        p.reset();
        p.setAntiAlias(true);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(topH * 0.38f);
        p.setLetterSpacing(0.25f);
        cv.drawText("GRAND VITARA", w / 2f, topH * 0.62f, p);

        // Status do link (ponto + texto)
        int dot;
        if (btState == 2 && tcmState == 2) dot = Color.rgb(60, 220, 120);
        else if (btState == 0) dot = RED;
        else dot = AMBER;
        p.reset();
        p.setAntiAlias(true);
        p.setColor(dot);
        float dx = w - pad - 6 * dp;
        cv.drawCircle(dx, topH * 0.5f, 5 * dp, p);
        p.setColor(TEXT_DIM);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setTextSize(topH * 0.26f);
        cv.drawText(message, dx - 12 * dp, topH * 0.5f + p.getTextSize() * 0.35f, p);
    }

    private void drawGauge(Canvas cv, float cx, float cy, float r, String title, String sub,
                           Integer value, int min, int max, int color) {
        final float start = 135f, sweep = 270f;
        float stroke = r * 0.13f;
        rect.set(cx - r, cy - r, cx + r, cy + r);

        // Aneis decorativos
        p.reset();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f * dp);
        p.setColor(Color.argb(40, 25, 227, 255));
        cv.drawCircle(cx, cy, r * 1.12f, p);
        cv.drawCircle(cx, cy, r * 0.74f, p);

        // Trilha
        p.setStrokeWidth(stroke);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(TRACK);
        cv.drawArc(rect, start, sweep, false, p);

        // Marcas
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStrokeWidth(2 * dp);
        p.setColor(Color.argb(110, 160, 200, 255));
        for (int i = 0; i <= 18; i++) {
            double a = Math.toRadians(start + sweep * i / 18.0);
            float r1 = r * 0.80f, r2 = r * (i % 3 == 0 ? 0.70f : 0.75f);
            cv.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, p);
        }

        // Valor
        if (value != null) {
            float f = (value - min) / (float) (max - min);
            f = Math.max(0f, Math.min(1f, f));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(stroke * 2.2f);
            p.setColor(Color.argb(45, Color.red(color), Color.green(color), Color.blue(color)));
            cv.drawArc(rect, start, sweep * f, false, p);   // brilho
            p.setStrokeWidth(stroke);
            p.setColor(color);
            cv.drawArc(rect, start, sweep * f, false, p);
        }

        // Numero
        p.reset();
        p.setAntiAlias(true);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setColor(value == null ? TEXT_DIM : Color.WHITE);
        p.setTextSize(r * 0.62f);
        cv.drawText(value == null ? "--" : String.valueOf(value), cx, cy + r * 0.12f, p);

        p.setTypeface(Typeface.DEFAULT);
        p.setColor(color);
        p.setTextSize(r * 0.17f);
        cv.drawText("°C", cx, cy + r * 0.38f, p);

        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(r * 0.20f);
        p.setLetterSpacing(0.15f);
        cv.drawText(title, cx, cy - r * 0.50f, p);

        p.setLetterSpacing(0.08f);
        p.setTypeface(Typeface.DEFAULT);
        p.setColor(TEXT_DIM);
        p.setTextSize(r * 0.12f);
        cv.drawText(sub, cx, cy + r * 0.72f, p);
    }

    private void drawCenter(Canvas cv, float cx, float cy, float r) {
        p.reset();
        p.setAntiAlias(true);
        p.setShader(new RadialGradient(cx, cy, r * 1.3f,
                Color.argb(70, 25, 227, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        cv.drawCircle(cx, cy, r * 1.3f, p);
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2 * dp);
        p.setColor(Color.argb(160, 25, 227, 255));
        cv.drawCircle(cx, cy, r, p);

        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setColor(hasData ? Color.WHITE : TEXT_DIM);
        p.setTextSize(r * 0.62f);
        cv.drawText(hasData ? String.valueOf(Math.round(rpm)) : "--", cx, cy + r * 0.12f, p);
        p.setTypeface(Typeface.DEFAULT);
        p.setColor(CYAN);
        p.setTextSize(r * 0.2f);
        p.setLetterSpacing(0.2f);
        cv.drawText("RPM", cx, cy + r * 0.45f, p);

        // Alerta
        String alert = null;
        int ac = AMBER;
        if (alarmLevel == 2) { alert = "CRITICO - PARE EM N/P"; ac = RED; }
        else if (alarmLevel == 1) { alert = "ATF QUENTE"; }
        else if (commLost) { alert = "SEM LEITURA DO ATF"; }
        if (alert != null) {
            p.setLetterSpacing(0.05f);
            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            p.setColor(ac);
            p.setTextSize(r * 0.20f);
            cv.drawText(alert, cx, cy + r * 1.38f, p);
        }
    }

    private void drawBottom(Canvas cv, int w, int h, float botH, float margin) {
        String[] labels = {"BATERIA", "ACELERADOR", "CAMBIO (TCM)", "BLUETOOTH"};
        String[] values = {
                hasData ? String.format(java.util.Locale.US, "%.1f V", batt) : "--",
                hasData ? String.format(java.util.Locale.US, "%.0f %%", tps) : "--",
                tcmState == 2 ? "LENDO" : tcmState == 1 ? "INICIANDO" : "SEM RESP.",
                btState == 2 ? "OK" : btState == 1 ? "CONECTANDO" : "OFF"
        };
        float gap = 12 * dp;
        float cw = (w - 2 * margin - 3 * gap) / 4f;
        float top = h - botH + 4 * dp;
        float ch = botH - 20 * dp;
        for (int i = 0; i < 4; i++) {
            float x = margin + i * (cw + gap);
            rect.set(x, top, x + cw, top + ch);
            p.reset();
            p.setAntiAlias(true);
            p.setColor(Color.argb(150, 14, 34, 70));
            cv.drawRoundRect(rect, 14 * dp, 14 * dp, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f * dp);
            p.setColor(Color.argb(110, 25, 227, 255));
            cv.drawRoundRect(rect, 14 * dp, 14 * dp, p);

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(TEXT_DIM);
            p.setTextSize(ch * 0.19f);
            p.setLetterSpacing(0.1f);
            cv.drawText(labels[i], x + cw / 2f, top + ch * 0.32f, p);
            p.setLetterSpacing(0f);
            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            p.setColor(Color.WHITE);
            p.setTextSize(ch * 0.34f);
            cv.drawText(values[i], x + cw / 2f, top + ch * 0.74f, p);
        }
    }
}
