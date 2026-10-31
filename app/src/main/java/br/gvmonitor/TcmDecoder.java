package br.gvmonitor;

/**
 * Decodifica a resposta do cambio (TCM) a "21 00" na K-Line.
 * Descoberto no log do SZ Viewer; indice na resposta (0 = 0x61):
 *   [11] acelerador = B*100/255 (%)      [15] bateria = B*20/255 (V)
 *   [16] ATF = B-40 (C)                  [37..38] rotacao = ((B37<<8)|B38)/4 (rpm)
 *   [39] agua = B-40 (C)
 * Byte 0x00 (-40 C) ou 0xFF = leitura invalida.
 */
final class TcmDecoder {
    static final class Result {
        boolean atfOk;
        int atf;
        boolean engOk;
        int coolant;
        float rpm, batt, tps;
    }

    /** Converte hex puro em bytes (ignora CR, LF e espaco). -1 se tiver qualquer outro caractere. */
    static int hexToBytes(String s, byte[] out) {
        int n = 0, half = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\r' || c == '\n' || c == ' ') continue;
            int v = Character.digit(c, 16);
            if (v < 0) return -1;
            if (half < 0) {
                half = v;
            } else {
                if (n >= out.length) return -1;
                out[n++] = (byte) ((half << 4) | v);
                half = -1;
            }
        }
        return half < 0 ? n : -1;
    }

    /** null se nao for uma resposta valida "61 00 ...". */
    static Result decode(byte[] b, int n) {
        if (n < 40 || (b[0] & 0xFF) != 0x61 || b[1] != 0x00) return null;
        Result r = new Result();
        int atf = b[16] & 0xFF;
        int eng = b[39] & 0xFF;
        r.atfOk = atf != 0x00 && atf != 0xFF;
        r.atf = atf - 40;
        r.engOk = eng != 0x00 && eng != 0xFF;
        r.coolant = eng - 40;
        r.rpm = (((b[37] & 0xFF) << 8) | (b[38] & 0xFF)) / 4f;
        r.batt = (b[15] & 0xFF) * 20f / 255f;
        r.tps = (b[11] & 0xFF) * 100f / 255f;
        return r;
    }

    private TcmDecoder() {}
}
