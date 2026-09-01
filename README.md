# Haval Trip

Computador de bordo com múltiplas trips para a central multimídia do **Haval
H6** — uma "Viagem atual" que se gerencia sozinha pela ignição, mais Trip A, B,
C, D e quantas mais forem criadas, cada uma contando por conta própria.

Irmão da [Haval APK Store](https://github.com/hugolumazzini/haval-apk-store) e
do Impulse: mesma paleta, mesmas fontes, mesmo tema escuro fixo. Os três
convivem na mesma tela, e um deles com identidade visual própria pareceria app
de outro carro.

## Como está organizado

```
core/    Kotlin/JVM puro — domínio, motor de cálculo, persistência, comparação
app/     Android + Compose — só desenha o que o core publica
```

A separação não é enfeite: o `core` não tem uma linha de Android, então os 61
testes e a simulação de condução rodam no terminal, em segundos, sem emulador.
Quando a leitura real do barramento do H6 existir, ela entra como outra
implementação de `TelemetrySource` e nada acima muda.

### `core`

| Arquivo | O que faz |
| --- | --- |
| `domain/Trip.kt` | `Trip`, `TelemetrySample`, `TripMetrics`, `TripStatus`, `IgnitionState`, `TripRecord`, `VehicleLive` |
| `engine/TripEngine.kt` | as regras de cálculo. Sem estado e sem relógio: recebe e devolve |
| `engine/TripManager.kt` | N contadores simultâneos e a máquina de estados da ignição |
| `storage/TripStorage.kt` | persistência atômica em JSON e a política de quando gravar |
| `services/TripComparison.kt` | comparação analítica entre duas viagens arquivadas |
| `format/TripFormat.kt` | formatação compartilhada entre painel e terminal |
| `demo/Demo.kt` | a simulação de condução |

### `app`

Barra lateral fixa com os contadores à esquerda; à direita, os quatro
quadrantes — **Distância**, **Consumo médio**, **Velocidade média**, **Tempo
total** — em números grandes, com hodômetro, autonomia e consumo instantâneo
numa faixa discreta em cima. Detalhes e histórico ficam a um toque.

## Rodando

Exportar o JDK antes (o do Android Studio serve):

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

```sh
./gradlew :core:test          # 61 testes
./gradlew :core:demo          # simulação de condução no terminal
./gradlew :app:assembleDebug  # APK
```

## Decisões que valem explicação

**Limiar de 1 km/h para "em movimento".** O sensor de roda oscila em torno de
zero com o carro imóvel. Sem o limiar, esse ruído viraria distância fantasma e
"tempo em movimento" no semáforo.

**Teto de 5 s no Δt.** Se o barramento engasga por dois minutos, a amostra
seguinte traria um Δt gigante e o motor integraria a última velocidade lida por
todo o buraco, inventando quilômetros. Melhor descartar o excedente.

**`null` em vez de zero.** Consumo médio sem combustível queimado, velocidade
média sem tempo: a tela mostra `—`. Um `0,0 km/L` no lugar de "ainda não dá para
saber" é informação errada, não informação neutra.

**PAUSED e STANDBY são estados diferentes.** As duas param de contar, mas por
causas diferentes: PAUSED é decisão do motorista e sobrevive a ligar o carro de
novo; STANDBY é consequência da ignição desligada e some sozinho na próxima
partida. Misturar as duas faria a Trip pausada voltar a contar sem ninguém pedir.

**Gravação atômica com `.tmp` + `rename` + `.bak`.** A central perde energia
junto com a ignição, no meio de qualquer coisa. Escrever por cima do arquivo bom
é apostar que a queda não acontece durante a escrita — e ela acontece
justamente aí. O ciclo escreve inteiro num temporário, força ao disco com
`fd.sync()`, promove o arquivo atual a cópia e renomeia por cima.

**Grava a cada 1 km, a cada 5 min ou no corte de ignição.** Gravar a cada
amostra queimaria a memória flash da central sem ganho: um quilômetro de dado
perdido não muda nada para o motorista, um armazenamento queimado muda tudo. O
quilômetro é medido pelo hodômetro do veículo, e não pela soma dos contadores —
somar faria cinco contadores gravarem cinco vezes mais no mesmo trajeto, e
criar um sexto pioraria de novo.

**Todo contador conta sozinho; o motorista só zera.** Nenhum computador de
bordo de fábrica tem "iniciar trip" — tem "zerar trip". E o custo do erro é
assimétrico: contador ligado à toa se resolve com um toque, contador parado
quando não devia perde uma viagem que não volta mais. Só a pausa do motorista
interrompe a contagem, e fechar uma viagem no histórico já recomeça a próxima.

**Cinco minutos de chave fora fecham a "Viagem atual".** O número separa "parei
para abastecer" de "cheguei": um posto, uma padaria, um portão de garagem levam
menos que isso. O carimbo do desligamento vai para o disco justamente porque a
central morre junto com a chave — o que decide não é um cronômetro rodando na
memória, é a diferença entre o carimbo gravado e o relógio da próxima partida.

**A viagem zerada sozinha vai para o histórico antes de sumir.** Perder o
percurso de ontem porque o carro dormiu na garagem seria o pior comportamento
possível para um contador que se apaga sem avisar. Viagem sem distância nenhuma
não é arquivada, para não entupir a lista com "liguei e desliguei".

**No histórico, ler uma viagem é o gesto principal; comparar é o extra.** A
pergunta comum tem uma viagem só — "quanto deu a ida à praia?" —, então um
toque na lista abre os números daquela viagem, e comparar duas vira uma ação a
partir dela. O caminho inverso (escolher duas antes de ver qualquer coisa)
cobrava o gesto mais raro de todo mundo, o tempo todo.

**O nome da viagem arquivada é editável e independente do contador.** "Trip A"
é o contador que mediu; "Ida a praia" é a viagem que ele mediu daquela vez.
Renomear o registro no histórico não mexe no nome que aparece no painel.
Excluir pede confirmação — é a única ação da tela sem volta.

**Litros absolutos vêm acompanhados de km/L na comparação.** Comparar só o gasto
bruto premia a viagem mais curta, e queimar menos porque se andou menos não é
economia.

**A autonomia olha os últimos 25 km, não a média da vida toda.** A pergunta
que ela responde é concreta — "no ritmo dos últimos quilômetros, quanto ainda
dá?" —, então a conta soma quilômetros e litros de verdade dentro de uma janela
e divide um pelo outro. Vinte e cinco quilômetros é longo o bastante para uma
subida de serra não derrubar o número pela metade, e curto o bastante para
sair da cidade e entrar na rodovia aparecer no painel antes de a rodovia
acabar. Os litros queimados parado entram na conta: motor ligado no
engarrafamento gasta sem andar, e ignorar isso deixaria a autonomia otimista
justamente quando ela mais precisa ser honesta. E o número só aparece depois de
500 m rodados — os primeiros metros são sempre manobra de saída, e deixá-los
virar a base faria a autonomia nascer pela metade e só subir, parecendo defeito.

**O hodômetro vitalício é lido, nunca escrito.** Zerar uma Trip não encosta
nele; a Trip guarda apenas em que quilometragem começou e terminou.

**A leitura principal é a linha direta com o serviço da central, pelo Shizuku.**
A central expõe um serviço interno que sabe todos os valores do carro, mas só
atende quem tem privilégio de sistema. O [Shizuku](https://shizuku.rikka.app) é
a ponte para isso: o dono autoriza uma vez e o app passa a falar com o serviço
direto. O que essa escolha compra não é elegância, é controle da lista: **nós**
dizemos quais chaves queremos monitorar. Pela ponte do Shisuku quem decide é
ele, e a lista padrão dele não inclui tanque, autonomia nem consumo médio — que
é metade do que um computador de bordo precisa. O chassi (`car.basic.vin_code`)
fica fora da lista de propósito: é o documento do carro, e o que não entra na
memória não escapa num relatório.

**A ponte pelo HavalShisuku continua, como segunda opção.** O
[HavalShisuku](https://github.com/bobaoapae/haval-app-tool-multimidia) já
conversa com os serviços internos da GWM e reemite cada valor como um broadcast
aberto `android.intent.haval.<chave>`. Escutar isso custa um `BroadcastReceiver`
e nenhuma permissão; falar com o barramento por conta própria exigiria assinatura
de sistema. A dependência é dura e assumida: sem o Shisuku rodando não chega
amostra nenhuma, e a tela diz isso em vez de mostrar zeros parecendo defeito
nosso. O chassi (`car.basic.vin_code`) também é publicado e é a única chave que
o app se recusa a escutar — é o documento do carro, e o que não entra na memória
não escapa num relatório.

**O Shisuku só reemite o que está monitorando, e a lista de fábrica é curta.**
Ele publica as chaves do `DEFAULT_KEYS` dele mais o que o dono marcar à mão em
"Configurar" — e tanque, autonomia, consumo médio e modo de energia **não estão
na lista de fábrica**. Por isso o diagnóstico lista nominalmente o que ainda não
chegou, separando "é padrão dele, então a ponte está fora do ar" de "é chave que
ninguém marcou". A diferença decide se o próximo passo é código aqui ou uma
caixinha marcada lá, e dentro do carro essa é a única pergunta que importa.

**A fonte é escolhível, e nunca troca sozinha.** A central pode ter o Shisuku
instalado sem haver carro dizendo nada — numa bancada, ou com a ignição
desligada. Um fallback automático para o simulador transformaria "o carro está
parado" em números inventados, que é o pior erro possível num aparelho que serve
para medir; então a troca é um botão, e o simulador se anuncia enquanto está no ar.

**O relatório sai por um link curto, e não por uma conta com credencial.** A
tentação era commitar direto num repositório privado, mas isso exige um token —
e a única forma de pôr um token numa central é digitar quase cem caracteres na
tela do carro, o que ninguém faz, ou embutir no APK, onde ele fica extraível. O
link curto de um site de texto (`dpaste.com/XXXXXXXXX`, nove caracteres) resolve
sem nenhuma credencial: dá para ler em voz alta. O custo é ele ser público por
30 dias, e a tela diz isso.

**A tela de Diagnóstico mostra o valor cru, sem conversão.** É ferramenta de
campo, não produto: guarda o último valor de cada chave e uma fita dos últimos
eventos, porque é o movimento do número enquanto o carro anda que revela a
unidade — a foto parada não revela. O relatório é gravado primeiro e só depois
enviado: dentro do carro pode não haver internet, e perder a coleta de uma
viagem inteira porque o Wi-Fi não pegou seria o pior desfecho de um teste que
exige dirigir.

## O que ainda não existe

- **As unidades confirmadas do H6.** Três coisas só o primeiro teste no carro
  responde: se `instant_fuel_consumption` vem por hora ou por distância (daí o
  interruptor no diagnóstico), o que significam os valores de `engine_state` (o
  critério hoje é "diferente de 0 é ligado") e quanto erra converter a
  porcentagem do tanque em litros por uma bóia que não é linear. Toda conversão
  mora em `Unidades`, um objeto só, para a resposta caber numa edição.
- **Chave de assinatura própria.** O build de release sai sem assinatura de
  propósito: reaproveitar a chave da APK Store misturaria as identidades dos
  dois apps.
