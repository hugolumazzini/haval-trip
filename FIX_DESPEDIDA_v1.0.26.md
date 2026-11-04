# v1.0.26 - FIX COMPLETO DA DESPEDIDA

## 🔴 PROBLEMA IDENTIFICADO

A despedida aparecia mas sumia imediatamente, como se tivesse sido "eng gatilhada mas removida em seguida".

### Causa Raiz (Dupla):

#### PROBLEMA 1: Race Condition Fatal
```
1. MotorDeBordo detecta ignição OFF
2. Chama ClusterOverlayService.projetarDespedida()
3. Overlay é criado → Despedida APARECE ✅
4. ClusterActivity (que estava rodando) é destruída pelo sistema
5. ClusterActivity.onDestroy() chama ClusterOverlayService.recolher()
6. REMOVE O OVERLAY DA DESPEDIDA! ❌
7. Despedida SOME
```

**Código problemático (ClusterActivity.kt linha 59)**:
```kotlin
override fun onDestroy() {
    if (!espiando) {
        ClusterOverlayService.recolher(this, JanelaDoPainel.NUMEROS)
        // ☝️ Remove overlay SEM VERIFICAR se é despedida!
    }
}
```

#### PROBLEMA 2: Watchdog Desativado na Despedida
```kotlin
// ClusterOverlayService.kt linha 276
if (!despedida) {
    iniciarWatchdog()  // ❌ NÃO roda watchdog na despedida!
}
```

**Resultado**: 
- Despedida SEM watchdog para manter visibilidade
- Outros apps podem cobrir
- Não é reprojetada periodicamente
- Some facilmente

---

## ✅ SOLUÇÃO IMPLEMENTADA

### 1. EstadoDaDespedida - Singleton Global

**Arquivo novo**: `EstadoDaDespedida.kt`

```kotlin
object EstadoDaDespedida {
    private val ativa = AtomicBoolean(false)
    
    fun ativar() { ... }    // Marca despedida como ativa
    fun desativar() { ... } // Desmarca quando carro ligar
    fun estaAtiva(): Boolean { ... }
}
```

**Por quê**: Previne race conditions. Quando despedida está ativa, ClusterActivity NÃO pode remover overlays.

### 2. ClusterActivity - Verificação Antes de Remover

**Antes**:
```kotlin
override fun onDestroy() {
    if (!espiando) {
        ClusterOverlayService.recolher(this, JanelaDoPainel.NUMEROS)
    }
}
```

**Agora**:
```kotlin
override fun onDestroy() {
    if (!espiando && !EstadoDaDespedida.estaAtiva()) {
        // Só remove se despedida NÃO está ativa
        ClusterOverlayService.recolher(this, JanelaDoPainel.NUMEROS)
    } else if (!espiando) {
        Log.w(TAG, "⚠️ NÃO removendo overlay (despedida ATIVA)")
    }
}
```

### 3. ClusterOverlayService - Watchdog SEMPRE Ativo

**Antes**:
```kotlin
if (!despedida) {
    iniciarWatchdog()  // ❌ Não roda na despedida
}
```

**Agora**:
```kotlin
// WATCHDOG SEMPRE ATIVO (inclusive na despedida!)
Log.i(TAG, "🔄 Iniciando watchdog para manter overlay visível")
iniciarWatchdog()
```

**Watchdog melhorado**:
```kotlin
private fun iniciarWatchdog() {
    watchdogJob = escopo.launch {
        var contador = 0
        while (isActive) {
            delay(WATCHDOG_INTERVAL_MS)  // 5s
            contador++
            if (contador % 12 == 0) {  // Log a cada 60s
                Log.d(TAG, "🐕 Watchdog ativo (${contador * 5}s), despedida=$despedidaAtiva")
            }
            garantirVisibilidade()  // Reprojeta para ficar por cima
        }
    }
}
```

### 4. MotorDeBordo - Gerenciamento de Estado

**acionarDespedida()** - Ativa flag ANTES de projetar:
```kotlin
private fun acionarDespedida() {
    escopo.launch {
        delay(500)
        
        if (viagem != null && Despedida.valeMostrar(viagem)) {
            // ATIVA FLAG ANTES de projetar
            EstadoDaDespedida.ativar()
            
            // Projeta despedida
            ClusterOverlayService.projetarDespedida(app, janela, displayId)
        }
    }
}
```

**escutar()** - Desativa flag quando carro ligar:
```kotlin
if (amostra.ignition == IgnitionState.ON) {
    coletaDeEnergia.comecar()
    // DESATIVA despedida quando carro ligar de novo
    EstadoDaDespedida.desativar()
} else {
    coletaDeEnergia.terminar()
    acionarDespedida()
}
```

### 5. Logging Massivo

**EstadoDaDespedida**:
```
👋 DESPEDIDA ATIVADA
   ClusterActivity NÃO deve remover overlays

🔌 DESPEDIDA DESATIVADA
   ClusterActivity pode remover overlays normalmente
```

**MotorDeBordo**:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
👋 ACIONANDO DESPEDIDA
   Janela: NUMEROS
   Display: 3
   Viagem: 5.2km
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ Despedida projetada com sucesso
```

**ClusterActivity.onDestroy()**:
```
🔌 onDestroy: removendo overlay (despedida NÃO ativa)
⚠️ onDestroy: NÃO removendo overlay (despedida ATIVA)
```

**ClusterOverlayService.watchdog**:
```
🐕 Watchdog iniciado (intervalo: 5000ms)
🐕 Watchdog ativo (60s), despedida=true
🐕 Watchdog ativo (120s), despedida=true
```

---

## 🎯 FLUXO CORRETO AGORA

### Ao desligar o carro:

```
1. MotorDeBordo detecta ignição OFF
2. EstadoDaDespedida.ativar() ← FLAG ATIVA
3. ClusterOverlayService.projetarDespedida()
4. Overlay criado → Despedida APARECE
5. Watchdog INICIADO (reprojeta a cada 5s)
6. ClusterActivity destruída
7. onDestroy() verifica: EstadoDaDespedida.estaAtiva() = TRUE
8. NÃO remove overlay! ← PROTEGIDO
9. Despedida FICA VISÍVEL durante a noite
10. Watchdog continua reprojetando (60s, 120s, 180s...)
```

### Ao ligar o carro:

```
1. MotorDeBordo detecta ignição ON
2. EstadoDaDespedida.desativar() ← FLAG DESATIVADA
3. ClusterActivity pode remover overlays normalmente
```

---

## 🧪 DIAGNÓSTICO

Para ver logs completos:
```bash
adb logcat | grep -E "EstadoDaDespedida|MotorDeBordo|ClusterActivity|ClusterOverlayService"
```

---

## ✅ RESULTADO ESPERADO

1. ✅ Despedida APARECE em tela cheia (cluster inteiro)
2. ✅ FICA VISÍVEL durante a noite (watchdog ativo)
3. ✅ NÃO é removida por ClusterActivity.onDestroy()
4. ✅ Reprojetada a cada 5s para ficar por cima
5. ✅ Some automaticamente quando carro ligar

---

## 📊 ARQUIVOS MODIFICADOS

1. **EstadoDaDespedida.kt** (NOVO) - Singleton de estado global
2. **ClusterActivity.kt** - Verifica estado antes de remover
3. **ClusterOverlayService.kt** - Watchdog sempre ativo + logs
4. **MotorDeBordo.kt** - Gerencia estado (ativar/desativar)

---

## 🎉 AGORA A DESPEDIDA FUNCIONA!

**SEM MAIS "apareceu e sumiu"!**
