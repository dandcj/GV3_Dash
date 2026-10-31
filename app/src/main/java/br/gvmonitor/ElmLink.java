package br.gvmonitor;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Conversa com o ELM327 por Bluetooth Classic (SPP) e le o cambio pela K-Line,
 * repetindo a sequencia que o SZ Viewer usa (ATTP5, ATSH8119F1, ATST19, ATFI, 21 00).
 * Roda numa thread propria; os callbacks vem dessa thread.
 */
final class ElmLink {
    interface Listener {
        /** bt: 0 desconectado, 1 conectando, 2 conectado. tcm: 0 sem cambio, 1 iniciando, 2 lendo. */
        void onLink(int bt, int tcm, String msg);
        void onData(TcmDecoder.Result r);
    }

    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothAdapter adapter;
    private final String mac;
    private final Listener listener;
    private volatile boolean running;
    private Thread thread;
    private BluetoothSocket sock;
    private InputStream in;
    private OutputStream out;

    ElmLink(BluetoothAdapter adapter, String mac, Listener listener) {
        this.adapter = adapter;
        this.mac = mac;
        this.listener = listener;
    }

    void start() {
        running = true;
        thread = new Thread(this::loop, "elm-link");
        thread.start();
    }

    void stop() {
        running = false;
        closeQuietly();
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        while (running) {
            try {
                listener.onLink(1, 0, "Conectando ao ELM...");
                connect();
                if (!elmInit()) throw new IOException("ELM nao respondeu");
                listener.onLink(2, 0, "ELM conectado");

                boolean kline = false;
                int fails = 0;
                byte[] buf = new byte[128];
                while (running) {
                    if (!kline) {
                        listener.onLink(2, 1, "Ligando no cambio...");
                        kline = klineInit();
                        if (!kline) {
                            listener.onLink(2, 0, "Cambio sem resposta (ligue a ignicao)");
                            Thread.sleep(3000);
                            continue;
                        }
                        listener.onLink(2, 2, "Lendo o cambio");
                        fails = 0;
                    }
                    String r = send("2100 1", 2500);
                    int n = TcmDecoder.hexToBytes(r, buf);
                    TcmDecoder.Result res = n > 0 ? TcmDecoder.decode(buf, n) : null;
                    if (res != null) {
                        fails = 0;
                        listener.onData(res);
                    } else if (++fails >= 3) {
                        kline = false;
                        listener.onLink(2, 0, "Perdeu a sessao do cambio");
                    }
                    Thread.sleep(250);
                }
            } catch (IOException e) {
                listener.onLink(0, 0, "Desconectado: " + e.getMessage());
            } catch (InterruptedException e) {
                // parando
            } finally {
                closeQuietly();
            }
            if (running) {
                try { Thread.sleep(3000); } catch (InterruptedException e) { break; }
            }
        }
        listener.onLink(0, 0, "Parado");
    }

    @SuppressLint("MissingPermission")
    private void connect() throws IOException {
        BluetoothDevice dev = adapter.getRemoteDevice(mac);
        adapter.cancelDiscovery();
        try {
            sock = dev.createRfcommSocketToServiceRecord(SPP);
            sock.connect();
        } catch (IOException first) {
            closeQuietly();
            sock = dev.createInsecureRfcommSocketToServiceRecord(SPP);
            sock.connect();
        }
        in = sock.getInputStream();
        out = sock.getOutputStream();
    }

    private void closeQuietly() {
        try { if (sock != null) sock.close(); } catch (IOException ignored) { }
        sock = null;
    }

    /** Envia um comando e le ate o prompt '>' (ou timeout). */
    private String send(String cmd, int timeoutMs) throws IOException, InterruptedException {
        while (in.available() > 0) in.read();
        out.write((cmd + "\r").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        StringBuilder sb = new StringBuilder();
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (in.available() > 0) {
                int ch = in.read();
                if (ch < 0) throw new IOException("conexao fechada");
                if (ch == '>') return sb.toString().trim();
                if (ch != 0 && ch != '\n') sb.append((char) ch);
            } else {
                Thread.sleep(3);
            }
        }
        return sb.length() > 0 ? sb.toString().trim() : "TIMEOUT";
    }

    /** Mesma configuracao que o SZ Viewer usa antes de falar K-Line. */
    private boolean elmInit() throws IOException, InterruptedException {
        String id = send("ATZ", 3000);
        if (id.equals("TIMEOUT")) return false;
        String[] cmds = {"ATE0", "ATL0", "ATS0", "ATH0", "ATD0", "ATAL",
                "ATIB10", "ATKW0", "ATSW00", "ATAT0"};
        for (String c : cmds) send(c, 3000);
        return true;
    }

    private boolean klineInit() throws IOException, InterruptedException {
        send("ATPC", 3000);
        send("ATTP5", 3000);
        send("ATSH" + Config.TCM_HEADER, 3000);
        send("ATST19", 3000);
        String r = send("ATFI", 6000);
        return r.contains("OK") && !r.contains("ERROR");
    }
}
