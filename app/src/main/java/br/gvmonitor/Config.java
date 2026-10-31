package br.gvmonitor;

/** Ajustes do painel. Mude aqui e gere o APK de novo. */
final class Config {
    /** Cabecalho K-Line do cambio (formato 81, destino 19, origem F1) - igual ao SZ Viewer. */
    static final String TCM_HEADER = "8119F1";

    /** Alarmes do ATF (graus C). */
    static final int ATF_WARN_C = 100;   // atencao: 1 beep a cada 3 s
    static final int ATF_CRIT_C = 120;   // critico: beep continuo - pare em N/P com o motor ligado

    /** Sem leitura valida do ATF por este tempo: duplo beep a cada 4 s (por 60 s). */
    static final long COMM_LOST_MS = 10000;

    /** Escalas dos medidores. */
    static final int ATF_MIN = 0, ATF_MAX = 150;
    static final int ENG_MIN = 40, ENG_MAX = 130;

    private Config() {}
}
