#!/usr/bin/env bash
#
# Bancada de testes do Haval Trip: abre e fecha as coisas do carro sem carro.
#
# Fala com o app pelo mesmo canal que a classe BancadaDeTestes escuta — um
# broadcast do Android com a chave da propriedade e o valor cru, exatamente no
# formato que a central publica. O app só aceita esses valores quando está na
# fonte SIMULADOR, então isto não tem como mexer com dado real do carro.
#
# Uso: ./bancada.sh
#
set -u

APP=br.com.hugolumazzini.havaltrip

# O emulador, e não o celular: há dois aparelhos plugados, e sem isto o adb
# reclama de "more than one device" em vez de escolher.
: "${ANDROID_SERIAL:=emulator-5554}"
export ANDROID_SERIAL

# O estado que a bancada acredita estar valendo. Fica aqui, e não no app, porque
# cada propriedade é publicada inteira: para abrir só a porta traseira é preciso
# reenviar as seis posições, com as outras cinco no valor que já tinham.
#
# Portas e cintos descansam em 0; vidro fechado é 1. Não é capricho de quem
# escreveu o script: é o que o H6 publica de verdade.
portas=(0 0 0 0 0 0)
cintos=(0 0 0 0 0)
vidros=(1 1 1 1)
teto=0
pneus="{2.29707,26.0,2.3657,25.0,2.33825,25.0,2.2559,25.0}"

NOMES_PORTA=("Porta do motorista" "Porta do passageiro" "Porta traseira esq." "Porta traseira dir." "Capô" "Porta-malas")
NOMES_CINTO=("Cinto do motorista" "Cinto do passageiro" "Cinto traseiro esq." "Cinto traseiro centro" "Cinto traseiro dir.")
NOMES_VIDRO=("Vidro do motorista" "Vidro do passageiro" "Vidro traseiro esq." "Vidro traseiro dir.")

# As aspas duplas por fora e simples por dentro não são exagero: sem elas o
# shell do Android engole as chaves do `{0,0,1}` e o app recebe texto vazio.
#
# O `< /dev/null` também não é enfeite: o `adb` lê a entrada padrão, que aqui é o
# teclado. Sem isso ele rouba as teclas digitadas enquanto publica, e o menu
# passa a perder comandos — as cinco chamadas de cada atualização comiam as
# cinco teclas seguintes.
enviar() {
    adb shell am broadcast -p "$APP" -a "$APP.BANCADA" \
        -e chave "$1" -e valor "'$2'" </dev/null >/dev/null 2>&1
}

junta() { local IFS=,; echo "{$*}"; }

publicar() {
    enviar car.basic.door_status       "$(junta "${portas[@]}")"
    enviar car.basic.seat_belt_warning "$(junta "${cintos[@]}")"
    enviar car.basic.window_status     "$(junta "${vidros[@]}")"
    enviar car.basic.sunroof_status    "$teto"
    enviar car.basic.tpms_status       "$pneus"
}

# Alterna uma posição entre o valor de repouso e o de acionado.
#
# Uma função por vetor, e não uma só recebendo o nome do vetor, porque o bash
# que vem no macOS é o 3.2 e não tem referência a variável (`local -n`). Repetir
# três funções de duas linhas é mais barato que exigir um bash novo de quem só
# quer abrir uma porta na tela.
alterna_porta() {
    if [[ ${portas[$1]} == 0 ]]; then portas[$1]=1; else portas[$1]=0; fi
}
alterna_vidro() {
    if [[ ${vidros[$1]} == 1 ]]; then vidros[$1]=0; else vidros[$1]=1; fi
}
alterna_cinto() {
    if [[ ${cintos[$1]} == 0 ]]; then cintos[$1]=1; else cintos[$1]=0; fi
}

marca() { [[ $1 != "$2" ]] && echo "●" || echo "○"; }

menu() {
    clear
    echo "  BANCADA — Haval Trip            (● aberto / solto   ○ fechado)"
    echo "  ────────────────────────────────────────────────────────────"
    for i in {0..5}; do
        printf "   %d  %s %s\n" "$((i + 1))" "$(marca "${portas[$i]}" 0)" "${NOMES_PORTA[$i]}"
    done
    echo
    for i in {0..3}; do
        printf "   %s  %s %s\n" "$(echo "qwer" | cut -c$((i + 1)))" "$(marca "${vidros[$i]}" 1)" "${NOMES_VIDRO[$i]}"
    done
    printf "   t  %s Teto solar\n" "$(marca "$teto" 0)"
    echo
    for i in {0..4}; do
        printf "   %s  %s %s\n" "$(echo "asdfg" | cut -c$((i + 1)))" "$(marca "${cintos[$i]}" 0)" "${NOMES_CINTO[$i]}"
    done
    echo
    echo "   p  pneu dianteiro esquerdo vazio        n  pneus normais"
    echo "   z  fechar tudo                          x  sair"
    echo
}

echo "Conferindo o aparelho..."
if ! adb shell pm list packages </dev/null 2>/dev/null | grep -q "$APP"; then
    echo "Não achei o app em $ANDROID_SERIAL."
    echo "Ligue o emulador e instale o app antes, ou rode assim:"
    echo "  ANDROID_SERIAL=outro-aparelho ./bancada.sh"
    exit 1
fi

publicar
while true; do
    menu
    # Sem o `|| break`, a entrada acabar vira laço infinito: o `read` falha,
    # `tecla` fica vazia, o `case` cai no descarte e recomeça. Num terminal a
    # entrada nunca acaba, mas basta canalizar teclas pelo `printf` — que é como
    # se testa este script — para o computador travar girando.
    read -rsn1 -p "  tecla: " tecla || break
    case "$tecla" in
        [1-6]) alterna_porta "$((tecla - 1))" ;;
        q) alterna_vidro 0 ;;
        w) alterna_vidro 1 ;;
        e) alterna_vidro 2 ;;
        r) alterna_vidro 3 ;;
        t) [[ $teto == 0 ]] && teto=1 || teto=0 ;;
        a) alterna_cinto 0 ;;
        s) alterna_cinto 1 ;;
        d) alterna_cinto 2 ;;
        f) alterna_cinto 3 ;;
        g) alterna_cinto 4 ;;
        p) pneus="{1.1,26.0,2.3657,25.0,2.33825,25.0,2.2559,25.0}" ;;
        n) pneus="{2.29707,26.0,2.3657,25.0,2.33825,25.0,2.2559,25.0}" ;;
        z) portas=(0 0 0 0 0 0); cintos=(0 0 0 0 0); vidros=(1 1 1 1); teto=0 ;;
        x)
            # Devolve o controle ao simulador do app: sem isto os valores ficam
            # travados no que a bancada mandou por último.
            adb shell am broadcast -p "$APP" -a "$APP.BANCADA_LIMPAR" </dev/null >/dev/null 2>&1
            clear
            echo "Valores devolvidos ao simulador."
            exit 0
            ;;
        *) continue ;;
    esac
    publicar
done
