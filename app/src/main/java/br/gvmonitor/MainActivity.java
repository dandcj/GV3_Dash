package br.gvmonitor;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;
import android.app.AlertDialog;

public class MainActivity extends Activity implements ElmLink.Listener {
    private static final String DEFAULT_MAC = "01:23:45:67:89:BA";

    private DashboardView view;
    private ElmLink link;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ToneGenerator tone;
    private long lastGoodAtf = 0;
    private long startedAt = 0;
    private long lastBeep = 0;
    private String mac = DEFAULT_MAC;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new DashboardView(this);
        view.message = "Iniciando...";
        setContentView(view);
        hideBars();

        // Logo: coloque res/drawable-nodpi/logo_suzuki.png (voce fornece o arquivo)
        int id = getResources().getIdentifier("logo_suzuki", "drawable", getPackageName());
        if (id != 0) view.logo = BitmapFactory.decodeResource(getResources(), id);

        SharedPreferences sp = getPreferences(MODE_PRIVATE);
        mac = sp.getString("mac", DEFAULT_MAC);

        // Toque longo no painel = trocar o MAC do ELM
        view.setOnLongClickListener(v -> { askMac(); return true; });

        try {
            tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        } catch (RuntimeException ignored) { }

        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
        }

        // Relogio de alarmes (independe de chegar dado novo)
        ui.post(new Runnable() {
            @Override public void run() {
                tickAlarms();
                ui.postDelayed(this, 500);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (isFinishing()) return;
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            startLink();
        } else {
            view.message = "Permita 'Dispositivos proximos' nas configuracoes do app";
            view.invalidate();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            startLink();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (link != null) { link.stop(); link = null; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        if (tone != null) tone.release();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) hideBars();
    }

    private void hideBars() {
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void startLink() {
        if (link != null) return;
        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
        if (ad == null || !ad.isEnabled()) {
            view.message = "Ligue o Bluetooth";
            view.btState = 0;
            view.invalidate();
            Toast.makeText(this, "Ligue o Bluetooth e reabra o app", Toast.LENGTH_LONG).show();
            return;
        }
        startedAt = System.currentTimeMillis();
        link = new ElmLink(ad, mac, this);
        link.start();
    }

    private void askMac() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        et.setText(mac);
        new AlertDialog.Builder(this)
                .setTitle("MAC do ELM327 (pareie antes, PIN 1234)")
                .setView(et)
                .setPositiveButton("Salvar", (d, w) -> {
                    String m = et.getText().toString().trim().toUpperCase();
                    if (BluetoothAdapter.checkBluetoothAddress(m)) {
                        mac = m;
                        getPreferences(MODE_PRIVATE).edit().putString("mac", m).apply();
                        if (link != null) { link.stop(); link = null; }
                        startLink();
                    } else {
                        Toast.makeText(this, "MAC invalido", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---- ElmLink.Listener (thread do link) ----

    @Override
    public void onLink(final int bt, final int tcm, final String msg) {
        ui.post(() -> {
            view.btState = bt;
            view.tcmState = tcm;
            view.message = msg;
            view.invalidate();
        });
    }

    @Override
    public void onData(final TcmDecoder.Result r) {
        ui.post(() -> {
            long now = System.currentTimeMillis();
            view.hasData = true;
            view.rpm = r.rpm;
            view.batt = r.batt;
            view.tps = r.tps;
            view.coolant = r.engOk ? r.coolant : null;
            if (r.atfOk) {
                view.atf = r.atf;
                lastGoodAtf = now;
            } else {
                view.atf = null;
            }
            tickAlarms();
            view.invalidate();
        });
    }

    // ---- Alarmes (thread de UI) ----

    private void tickAlarms() {
        long now = System.currentTimeMillis();
        int level = 0;
        if (view.atf != null && now - lastGoodAtf < Config.COMM_LOST_MS) {
            if (view.atf >= Config.ATF_CRIT_C) level = 2;
            else if (view.atf >= Config.ATF_WARN_C) level = 1;
        }

        // Sem leitura: so avisa depois de ja ter lido algo (ou 15 s de espera) e por no maximo 60 s
        long ref = lastGoodAtf > 0 ? lastGoodAtf : startedAt;
        boolean lost = ref > 0 && now - ref > Config.COMM_LOST_MS;
        if (lost && level == 0) {
            view.atf = null;
        }
        boolean beepLost = lost && lastGoodAtf > 0
                && now - lastGoodAtf < Config.COMM_LOST_MS + 60000;

        boolean changed = level != view.alarmLevel || lost != view.commLost;
        view.alarmLevel = level;
        view.commLost = lost && lastGoodAtf > 0;
        if (changed) view.invalidate();

        if (tone == null) return;
        if (level == 2) {
            if (now - lastBeep > 400) {
                tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200);
                lastBeep = now;
            }
        } else if (level == 1) {
            if (now - lastBeep > 3000) {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 200);
                lastBeep = now;
            }
        } else if (beepLost) {
            if (now - lastBeep > 4000) {
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 300);
                lastBeep = now;
            }
        }
    }
}
