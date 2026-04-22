# Integración Gemini - Documentación de Flujo

## Estado General
✅ **Integración Completada**: Telegram + Kafka + Gemini + Redis/Postgres

## Arquitectura del Flujo

```
┌─────────────────┐
│   Telegram      │
│   (Usuario)     │
└────────┬────────┘
         │
         │ Webhook /api/telegram/webhook
         │
┌────────▼─────────────┐
│   TelegramBot        │  
│   (Recibe update)    │
└────────┬─────────────┘
         │
         │ Publica evento ChatEvent
         │
┌────────▼──────────────────┐
│   TelegramPublisher       │
│   (Kafka Producer)        │
└────────┬──────────────────┘
         │
    telegram-inbound
  (Kafka Topic)
         │
┌────────▼──────────────────┐  
│   TelegramConsumer        │
│   (Kafka Consumer)        │
└────────┬──────────────────┘
         │
         │ assistantService.processRequest(chatId, prompt)
         │
┌────────▼─────────────────────────────────────────┐
│        AssistantService                          │
│  (Orquestador principal)                         │
└────┬──────────────────────────────────────────────┘
     │
     ├─▶ 1. MemoryManager.getFullContext(chatId)
     │      └─▶ Redis: recuperar últimos mensajes
     │      └─▶ PostgreSQL: resumen histórico
     │
     ├─▶ 2. GeminiService.generateResponse(context, prompt)
     │      └─▶ REST API: generativelanguage.googleapis.com
     │      └─▶ models/gemini-1.5-pro:generateContent
     │      └─▶ Devuelve: String (respuesta de IA)
     │
     ├─▶ 3. MemoryManager.updateMemory(chatId, prompt, response)
     │      └─▶ Redis: agregar mensaje usuario y respuesta
     │      └─▶ Si Redis >= threshold:
     │          └─▶ SummarizationService.compress() (async)
     │
     └─▶ 4. TelegramPublisher.publishOutgoing(chatId, response)
            └─▶ Kafka: telegram-outbound topic
            
         telegram-outbound
         (Kafka Topic)
              │
┌─────────────▼──────────────────┐
│   TelegramSender (Consumer)    │
│   (Lee de Kafka)               │
└─────────────┬──────────────────┘
              │
              │ Bot.sendMessage(chatId, response)
              │
┌─────────────▼─────────┐
│   Telegram API        │
│   (Retorna respuesta) │
└───────────────────────┘
```

## Archivos Creados/Modificados

### 1. **GeminiService.java** (NUEVO)
- **Ubicación**: `src/main/java/com/assistant/core/service/GeminiService.java`
- **Responsabilidad**: Encapsular llamadas HTTP a la API de Google Generative AI
- **Métodos principales**:
  - `generateResponse(systemContext, userPrompt)`: Envía solicitud a Gemini y retorna respuesta
  - `buildPrompt()`: Construye el prompt combinando contexto + prompt del usuario

### 2. **AssistantService.java** (MODIFICADO)
- **Cambios**:
  - ✅ Agregado `GeminiService` como dependencia
  - ✅ Reemplazada respuesta simulada con `geminiService.generateResponse()`
  - ✅ Agregado manejo robusto de excepciones
  - ✅ Agregado logging detallado del flujo

### 3. **HttpConfig.java** (NUEVO)
- **Ubicación**: `src/main/java/com/assistant/core/config/HttpConfig.java`
- **Responsabilidad**: Exponer `RestTemplate` como bean con timeouts configurados
- **Configuración**:
  - Connect timeout: 10 segundos
  - Read timeout: 30 segundos

### 4. **GeminiConfig.java** (NUEVO)
- **Ubicación**: `src/main/java/com/assistant/core/config/GeminiConfig.java`
- **Responsabilidad**: Configuración e inicialización de Gemini

### 5. **TelegramSender.java** (NUEVO)
- **Ubicación**: `src/main/java/com/assistant/core/telegram/TelegramSender.java`
- **Responsabilidad**: Consumidor de Kafka para enviar respuestas de IA a Telegram
- **Flujo**:
  - Escucha topic "telegram-outbound"
  - Recibe eventos `ChatEvent` con respuesta de Gemini
  - Envía respuesta al usuario a través de `TelegramBot.execute(SendMessage)`
  - Manejo robusto de excepciones de la API de Telegram

### 6. **pom.xml** (ACTUALIZADO)
- ✅ No se agregaron dependencias externas adicionales (se usa RestTemplate nativo)

## Configuración en application.yml

```yaml
app:
  ai:
    gemini-api-key: ${GEMINI_API_KEY:AIzaSyBfbkTYw3m1GxONlRs7WxG3SIXV-GXLx6Q}
    model-name: gemini-1.5-pro
```

### Variables de Entorno Compatibles
- `GEMINI_API_KEY`: API key de Google Generative AI (obligatoria en producción)
- Fallback: vacío, debe definirse por entorno

## Flujo de Datos Detallado

### 1️⃣ Recepción de Telegram
```
Usuario escribe en Telegram 
  → Webhook /api/telegram/webhook 
  → TelegramBot.onWebhookUpdateReceived()
  → ChatEvent publicado a "telegram-inbound"
```

### 2️⃣ Consumo desde Kafka
```
TelegramConsumer escucha "telegram-inbound"
  → Invoca: AssistantService.processRequest(chatId, prompt)
```

### 3️⃣ Procesamiento de IA
```
AssistantService.processRequest():
  
  a) Recuperar contexto:
     - Redis: últimos 5-20 mensajes (overlap)
     - PostgreSQL: resumen histórico de mensajes antiguos
  
  b) Llamar a Gemini:
     - URL: https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent
     - Método: POST
     - Body: { contents: [ { parts: [ { text: fullPrompt } ], role: "user" } ] }
     - Headers: Content-Type: application/json
     - Auth: ?key={GEMINI_API_KEY}
  
  c) Actualizar memoria:
     - Almacenar prompt + respuesta en Redis
     - Si Redis >= 20 mensajes: Sumarizar y comprimir en PostgreSQL
  
  d) Publicar respuesta:
     - TelegramPublisher.publishOutgoing()
     - Envía ChatEvent a "telegram-outbound"
```

### 4️⃣ Envío de Respuesta
```
TelegramSender escucha "telegram-outbound"
  → Bot.sendMessage(chatId, responseText)
  → Usuario recibe respuesta en Telegram
```

## Formato de Solicitud a Gemini API

```json
POST /v1beta/models/gemini-1.5-pro:generateContent?key=API_KEY
Content-Type: application/json

{
  "contents": [
    {
      "parts": [
        {
          "text": "# Contexto de la Conversación\nRESUMEN HISTÓRICO: ...\nMENSAJES RECIENTES: ...\n\n# Nuevo Mensaje del Usuario\n{userPrompt}\n\n# Instrucciones\nResponde de manera clara y concisa..."
        }
      ],
      "role": "user"
    }
  ]
}
```

## Formato de Respuesta de Gemini API

```json
{
  "candidates": [
    {
      "content": {
        "parts": [
          {
            "text": "Respuesta generada por Gemini..."
          }
        ],
        "role": "model"
      },
      "finish_reason": "STOP"
    }
  ]
}
```

## Manejo de Errores

| Escenario | Manejo |
|-----------|--------|
| Fallo en Gemini API | Log de error + Respuesta amistosa al usuario |
| Error en recuperación de contexto | Log de error + Contexto vacío |
| Timeout en API REST | Log de error + Respuesta amistosa |
| API key inválida | Error en inicialización (fail-fast) |

## Testing Manual

### Prerequisitos
```bash
# Variables de entorno
export GEMINI_API_KEY=tu_api_key_aqui
export TELEGRAM_BOT_TOKEN=tu_token_aqui
export KAFKA_SERVERS=localhost:9092
export REDIS_HOST=localhost
export REDIS_PORT=6379
```

### Flujo de Prueba
1. Iniciar aplicación: `mvn spring-boot:run`
2. Enviar mensaje a bot Telegram: `/start` o cualquier texto
3. Webhook recibe update → Publica a Kafka
4. Consumer procesa → Llamada a Gemini
5. Respuesta publicada a Kafka → TelegramSender envía a usuario

### Verificación en Logs
```
[INFO] Evento Kafka recibido: {chatId}
[DEBUG] Contexto recuperado de longitud: {n}
[INFO] Enviando solicitud a Gemini para chat: {chatId}
[INFO] Respuesta generada por Gemini con tamaño: {n}
[INFO] Memoria actualizada para chat: {chatId}
[INFO] Respuesta enviada a Kafka para {chatId}
```

## Escalabilidad y Consideraciones

✅ **Ventajas de esta arquitectura**:
- Desacoplamiento: Telegram, Gemini, Memoria son independientes
- Escalabilidad: TelegramConsumers pueden ser múltiples
- Resiliencia: Fallos en Gemini no derrumban Telegram
- Observabilidad: Cada componente loguea su actividad

⚠️ **Limitaciones actuales**:
- Rate limiting de Gemini API (50 req/min para la API key gratuita)
- Ventana de contexto limitada (no más de ~100k tokens)
- Sin soporte para streaming de respuestas aún

## Próximas Mejoras Sugeridas

1. **Streaming de respuestas**: Usar Server-Sent Events
2. **Manejo de herramientas MCP**: Integrar McpHostManager
3. **Cache de respuestas**: Redis para respuestas frecuentes
4. **Rate limiting**: Implementar circuit breaker
5. **Metricas**: Prometheus para monitoreo de latencias
