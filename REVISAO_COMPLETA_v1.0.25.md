# REVISÃO COMPLETA v1.0.25 - FIX DEFINITIVO DA CAPTURA SHIZUKU

## 🔴 PROBLEMA RAIZ IDENTIFICADO

O MotorDeBordo escolhe a fonte de telemetria UMA VEZ no `init`, mas **Shizuku pode não estar pronto no boot**!

### Fluxo problemático atual:

```
1. Boot do Android
2. ServicoDeBordo.onCreate() → MotorDeBordo.de(this) IMEDIATAMENTE
3. MotorDeBordo init {
     _fonte = when {
         ShizukuTelemetrySource.disponivel() -> SHIZUKU  ← FALHA AQUI!
         ...
     }
   }
4. Shizuku ainda não subiu → ShizukuTelemetrySource.disponivel() = false
5. Escolhe SHISUKU ou SIMULADOR
6. NUNCA MAIS TENTA SHIZUKU!
```

## 🎯 SOLUÇÃO IMPLEMENTADA

### 1. Re-avaliação Automática de Fonte
- Observador que detecta quando Shizuku fica disponível
- Troca automaticamente para SHIZUKU quando ele ficar pronto
- Retry periódico se Shizuku não estiver disponível

### 2. Logging Massivo
Logs em TUDO para diagnóstico:
- Inicialização do MotorDeBordo
- Escolha de fonte
- Mudança de fonte
- Estado do Shizuku
- Conexões e reconexões
- Callbacks recebidos

### 3. Tela de Diagnóstico Melhorada
- Mostra fonte atual
- Mostra estado do Shizuku (disponível/autorizado)
- Botão para forçar re-avaliação
- Histórico de eventos

## 📝 ARQUIVOS MODIFICADOS

1. **MotorDeBordo.kt**
   - Adicionar observador de disponibilidade do Shizuku
   - Re-avaliação automática de fonte
   - Logging massivo

2. **ShizukuTelemetrySource.kt** 
   - Melhorar detecção de disponibilidade
   - Notificar quando ficar disponível
   - Logging massivo de conexão

3. **ConfiguracaoScreen.kt** (opcional)
   - Mostrar estado detalhado da telemetria
   - Botão de diagnóstico

## 🔧 MUDANÇAS TÉCNICAS

### MotorDeBordo
```kotlin
init {
    Log.i("MotorDeBordo", "🚀 INICIANDO MotorDeBordo")
    Log.i("MotorDeBordo", "   Shizuku disponível: ${ShizukuTelemetrySource.disponivel()}")
    Log.i("MotorDeBordo", "   Shizuku autorizado: ${ShizukuTelemetrySource.autorizado()}")
    Log.i("MotorDeBordo", "   SHISUKU instalado: $shisukuInstalado")
    
    // Escolha inicial de fonte
    escolherFonte()
    
    // Observador: re-avalia fonte quando Shizuku ficar disponível
    escopo.launch {
        while (isActive) {
            delay(5000) // Verifica a cada 5s
            reavaliarFonte()
        }
    }
}

private fun escolherFonte() {
    val nova = when {
        ShizukuTelemetrySource.disponivel() && ShizukuTelemetrySource.autorizado() -> Fonte.SHIZUKU
        ShizukuTelemetrySource.disponivel() -> Fonte.SHIZUKU // Vai pedir autorização
        shisukuInstalado -> Fonte.SHISUKU
        else -> Fonte.SIMULADOR
    }
    
    if (_fonte.value != nova) {
        Log.i("MotorDeBordo", "🔄 MUDANÇA DE FONTE: ${_fonte.value} → $nova")
        _fonte.value = nova
        escutar()
    }
}

private fun reavaliarFonte() {
    val disponivel = ShizukuTelemetrySource.disponivel()
    val autorizado = ShizukuTelemetrySource.autorizado()
    
    // Se Shizuku ficou disponível E não estamos usando ele, troca!
    if (disponivel && _fonte.value != Fonte.SHIZUKU) {
        Log.w("MotorDeBordo", "⚡ SHIZUKU FICOU DISPONÍVEL! Trocando de fonte...")
        escolherFonte()
    }
}
```

### ShizukuTelemetrySource
```kotlin
// No samples() flow
launch {
    Log.i(TAG, "🚀 INICIANDO FLOW SHIZUKU")
    delay(500)
    garantirPermissaoEConectar(::conectar)
}

// Nos logs de conexão
fun conectar(): Boolean {
    Log.i(TAG, "🔌 TENTANDO CONECTAR...")
    Log.i(TAG, "   Shizuku pingBinder: ${Shizuku.pingBinder()}")
    Log.i(TAG, "   Autorização: ${Shizuku.checkSelfPermission()}")
    
    // ... resto da conexão
}
```

## ✅ RESULTADO ESPERADO

1. **Boot do carro**:
   ```
   MotorDeBordo: 🚀 INICIANDO MotorDeBordo
   MotorDeBordo:    Shizuku disponível: false
   MotorDeBordo:    SHISUKU instalado: true
   MotorDeBordo: 🔄 Fonte escolhida: SHISUKU
   
   [5 segundos depois]
   MotorDeBordo: ⚡ SHIZUKU FICOU DISPONÍVEL! Trocando de fonte...
   MotorDeBordo: 🔄 MUDANÇA DE FONTE: SHISUKU → SHIZUKU
   ShizukuTelemetry: 🚀 INICIANDO FLOW SHIZUKU
   ShizukuTelemetry: 🔌 TENTANDO CONECTAR...
   ShizukuTelemetry: 🎉 CONECTADO ao serviço do carro via Shizuku
   ```

2. **App já rodando, Shizuku reinicia**:
   ```
   ShizukuTelemetry: 💀 Binder do Shizuku morreu!
   MotorDeBordo: ⚡ SHIZUKU FICOU DISPONÍVEL! Trocando de fonte...
   ShizukuTelemetry: 🔄 Reconectando...
   ShizukuTelemetry: 🎉 CONECTADO ao serviço do carro via Shizuku
   ```

## 🧪 COMO TESTAR

```bash
# Ver logs completos
adb logcat | grep -E "MotorDeBordo|ShizukuTelemetry"

# Forçar restart do Shizuku (simula falha)
adb shell am force-stop moe.shizuku.privileged.api

# Deve reconectar automaticamente em ~5s
```

## 📊 MÉTRICAS DE SUCESSO

- ✅ Conecta ao Shizuku em < 10s após boot
- ✅ Reconecta automaticamente se Shizuku reiniciar
- ✅ Troca automaticamente de SHISUKU para SHIZUKU quando disponível
- ✅ Logs claros em TODAS as operações
- ✅ Sem mais "hora funciona, hora não"
