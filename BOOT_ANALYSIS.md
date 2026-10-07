# Análise de Estabilidade no Boot - Haval Trip

## ⚠️ PONTOS DE RISCO IDENTIFICADOS

### 1. PRIORIDADE MÁXIMA NO BOOT (CRÍTICO)
**Arquivo:** `AndroidManifest.xml` linha 163
```xml
<intent-filter android:priority="1000">
```
**Risco:** Prioridade 1000 = MÁXIMA. App executa ANTES de serviços críticos do sistema.
**Impacto:** Pode tentar usar recursos que ainda não estão prontos.
**Recomendação:** Reduzir para priority="100" (ainda alta, mas não competindo com sistema)

---

### 2. DIRECT BOOT AWARE (ALTO)
**Arquivo:** `AndroidManifest.xml` linha 150, 162
```xml
android:directBootAware="true"
```
**Risco:** App inicia ANTES do unlock do dispositivo, quando poucos recursos estão disponíveis.
**Impacto:** SharedPreferences, displays secundários, Shizuku podem não estar prontos.
**Recomendação:** Manter, mas adicionar proteções contra recursos indisponíveis.

---

### 3. MÚLTIPLOS BROADCAST RECEIVERS NO BOOT
**Registrados em:**
- `ServicoDeBordo.onCreate()` → 15s depois
  - `PaginaDoCluster.acompanhar()` → bind com ClusterService
  - `TecladoDoVolante.acompanhar()` → BroadcastReceiver
  
- `MotorDeBordo.init()`
  - `HavalTelemetrySource` → BroadcastReceiver (chaves do carro)
  - `BancadaDeTestes` → BroadcastReceiver (modo debug)

**Risco:** 4+ receivers registrados simultaneamente nos primeiros segundos.
**Impacto:** Sobrecarga de memória e processamento.
**Estado atual:** ADIADOS para 15s - BOM!

---

### 4. LOOP DE TELEMETRIA A CADA 1 SEGUNDO
**Arquivo:** `HavalTelemetrySource.kt` linha 58-66
```kotlin
launch {
    while (isActive) {
        delay(intervaloMs)  // 1000ms
        estado.publicarFita()
        if (!estado.mudo()) trySend(estado.montarAmostra())
    }
}
```
**Risco:** Loop infinito rodando a cada 1 segundo desde o boot.
**Impacto:** CPU/memória constante, pode competir com outros apps.
**Recomendação:** OK - necessário para medir viagem.

---

### 5. PROJEÇÃO NO CLUSTER AOS 30 SEGUNDOS
**Arquivo:** `ServicoDeBordo.kt` linha 29 (boot gradual implementado)
```kotlin
android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
    runCatching {
        ProjetorDoPainel.projetarNaPartida(this, Cluster::telasEscolhidas)
    }
}, 30_000)
```
**Risco:** Executa comandos shell (`am start`, `am stack resize`) via Shizuku.
**Estado atual:** ADIADO para 30s - BOM!
**Impacto:** Baixo se Shizuku já estabilizou.

---

### 6. RETRY BACKOFF DO BOOT RECEIVER
**Arquivo:** `HavalTripBootReceiver.kt` linha 22
```kotlin
private val RETRY_BACKOFF_MS = longArrayOf(5000, 15000, 45000)
```
**Estado atual:** 5s / 15s / 45s
**Avaliação:** CONSERVADOR - BOM!

---

## ✅ BOAS PRÁTICAS JÁ IMPLEMENTADAS

1. ✅ Boot gradual em 3 estágios (0s / 15s / 30s)
2. ✅ Retry conservador (5s / 15s / 45s)
3. ✅ `runCatching` em operações críticas
4. ✅ SupervisorJob para não derrubar app inteiro em falha
5. ✅ Projeção adiada para quando Shizuku estabilizar

---

## 🔧 RECOMENDAÇÕES DE CORREÇÃO

### ALTA PRIORIDADE

#### 1. Reduzir prioridade do boot receiver
```xml
<!-- De: priority="1000" -->
<!-- Para: priority="100" -->
<intent-filter android:priority="100">
```
**Razão:** Não competir com serviços críticos do sistema.

#### 2. Adicionar flag de "boot completo"
Evitar operações pesadas se recursos não estão prontos:
```kotlin
private var bootCompleto = false

override fun onReceive(context: Context, intent: Intent) {
    bootCompleto = intent.action == Intent.ACTION_BOOT_COMPLETED
    // Se for LOCKED_BOOT_COMPLETED, espera mais
}
```

### MÉDIA PRIORIDADE

#### 3. Aumentar delay inicial do primeiro retry
```kotlin
// De: 5s / 15s / 45s
// Para: 10s / 30s / 60s
private val RETRY_BACKOFF_MS = longArrayOf(10000, 30000, 60000)
```
**Razão:** Primeiro retry aos 10s ainda é rápido, mas não agressivo.

### BAIXA PRIORIDADE

#### 4. Lazy initialization de recursos pesados
Adiar criação de objetos que não são essenciais nos primeiros 30s.

---

## 📈 CRONOGRAMA ATUAL DO BOOT

```
T+0s    BootReceiver detecta boot
        ↓
T+0s    ServicoDeBordo.onCreate()
        - startForeground()
        - Cluster.iniciar() [lê SharedPreferences]
        - MotorDeBordo.de() [singleton pesado]
          ↓
          - Cria TripManager
          - Registra HavalTelemetrySource (BroadcastReceiver)
          - Inicia loop de telemetria a cada 1s
          - Registra BancadaDeTestes (BroadcastReceiver)
        
T+15s   Receivers do painel
        - PaginaDoCluster.acompanhar() [bind ClusterService]
        - TecladoDoVolante.acompanhar() [BroadcastReceiver]

T+30s   Projeção no cluster
        - ProjetorDoPainel.projetarNaPartida()
          ↓
          - Aguarda Shizuku.OnBinderReceivedListener
          - Executa: am start --display N --windowingMode 5
          - Executa: am stack resize
          - Reforço aos T+60s (insistir = true)
```

---

## 🎯 CONCLUSÃO

**Pontos fortes:**
- Boot gradual bem estruturado
- Retry conservador
- Tratamento de erros adequado

**Pontos de atenção:**
- Priority 1000 pode ser agressivo demais
- Direct boot sem validação de recursos disponíveis

**Mudanças recomendadas:**
1. **CRÍTICA:** Reduzir `priority="1000"` → `priority="100"`
2. **RECOMENDADA:** Aumentar primeiro retry `5s` → `10s`
3. **OPCIONAL:** Adicionar validação de boot completo

---

Gerado em: 2026-10-07
