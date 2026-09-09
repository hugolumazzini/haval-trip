#!/usr/bin/env bash
#
# Põe o app no emulador como ele fica no carro: a central numa tela, o painel
# de instrumentos na outra.
#
# No H6 são dois displays de verdade, e quem coloca a nossa janela no painel é
# o Impulse, com um `am start --display 3 --windowingMode 5` via Shizuku. No
# emulador o papel do painel é feito por um display secundário — em
# Extended controls > Displays, "Add secondary display". Este script acha esse
# display sozinho, porque o id muda a cada vez que ele é recriado.
#
# Uso:  ./simular.sh            compila, instala e abre as duas telas
#       ./simular.sh --foto     idem, e ainda salva uma captura do painel
#
set -euo pipefail
cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
PACOTE=br.com.hugolumazzini.havaltrip

DISPOSITIVO=$(adb devices | awk '/emulator.*device$/ {print $1; exit}')
if [ -z "$DISPOSITIVO" ]; then
  echo "Nenhum emulador rodando. Abra o emulador Haval e rode de novo." >&2
  exit 1
fi
adb() { command adb -s "$DISPOSITIVO" "$@"; }

echo "==> Compilando e instalando em $DISPOSITIVO"
./gradlew :app:assembleDebug -q
adb install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
echo "    instalado"

# O painel do H6 é 1920x720, e a posição do bloco é dada em frações desse
# quadro — num display de outro formato os tamanhos prontos ("Faixa da
# navegação", por exemplo) caem em lugar diferente do que cairiam no carro, e a
# conferência não vale nada. Daí criar o display secundário aqui, no tamanho
# certo, em vez de depender de quem o criou à mão no emulador.
adb shell settings put global overlay_display_devices "1920x720/320" >/dev/null
sleep 2

# O primeiro display que não é o 0. O display 0 é a tela do emulador, que faz
# o papel da central; qualquer outro só existe porque foi criado à mão para
# fazer o papel do painel.
PAINEL=$(adb shell dumpsys display \
  | grep -o 'mDisplayId=[0-9]*' | grep -o '[0-9]*' | sort -un | grep -v '^0$' | head -1 || true)

echo "==> Abrindo a central no display 0"
adb shell am force-stop "$PACOTE"
adb shell am start -n "$PACOTE/.MainActivity" >/dev/null
sleep 3

if [ -z "$PAINEL" ]; then
  echo "!!! Nenhum display secundário encontrado."
  echo "    No emulador: Extended controls (…) > Displays > Add secondary display."
  echo "    O painel do H6 é uma faixa larga; 760x200 a 220 dpi é uma boa medida."
  exit 0
fi

echo "==> Abrindo o painel de instrumentos no display $PAINEL"
adb shell am start --display "$PAINEL" -n "$PACOTE/.ClusterActivity" >/dev/null
sleep 4

if [ "${1:-}" = "--foto" ]; then
  # A captura é o único jeito honesto de conferir o painel: o overlay do
  # emulador desenha fundo preto sobre fundo transparente, e olhar a tela não
  # distingue "não apareceu" de "apareceu escuro".
  FOTO="/tmp/haval-painel-$(date +%H%M%S).png"
  # Pelo cartão e não direto para o stdout: em alguns emuladores o `screencap`
  # redirecionado pelo adb volta vazio, e um arquivo de zero byte parece um app
  # que não desenhou nada.
  adb shell "screencap -p -d $PAINEL > /sdcard/haval-painel.png"
  adb pull /sdcard/haval-painel.png "$FOTO" >/dev/null 2>&1
  echo "==> Captura do painel em $FOTO"
fi

echo "Pronto. Central no display 0, painel no display $PAINEL."
