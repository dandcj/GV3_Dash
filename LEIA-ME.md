# Painel GV (Android)

Painel em tela cheia (paisagem) para o tablet: ATF, agua, RPM, bateria, acelerador, com alarme sonoro.
Fala com o ELM327 por Bluetooth Classic e le o cambio pela K-Line, igual ao SZ Viewer
(ATTP5 / ATSH8119F1 / ATST19 / ATFI / 21 00).

## Gerar o APK
**Android Studio:** File > Open > pasta `PainelGV`, aguarde o Gradle sincronizar,
Build > Build APK(s). O arquivo sai em `app/build/outputs/apk/debug/app-debug.apk`.

**Sem instalar nada:** suba a pasta para um repositorio no GitHub; o workflow em `.github/workflows`
compila e publica o APK em Actions > Artifacts.

## Antes de usar
1. Pareie o ELM nas configuracoes do Android (PIN 1234). MAC padrao: 01:23:45:67:89:BA.
   Toque longo no painel troca o MAC.
2. Feche o SZ Viewer: o ELM aceita um cliente so.
3. Ignicao ligada para o cambio responder.
4. Logo: coloque `logo_suzuki.png` em `app/src/main/res/drawable-nodpi/`.

## Limites conhecidos
- Nada disso foi compilado nem rodado em um Android real. O decodificador (TcmDecoder) foi testado
  com o quadro real capturado (ATF 69, agua 88, 650 rpm, 14,5 V). O resto (Bluetooth, tela) e codigo novo.
- ATF validado em um unico ponto (69 C). Compare com o SZ Viewer em outra temperatura.
- App em segundo plano: o Android pode matar a conexao. Mantenha o app aberto e desative a
  otimizacao de bateria para ele. Nao ha servico em primeiro plano nesta versao.
- Alarmes: ATF >= 100 C atencao, >= 120 C critico (Config.java).
