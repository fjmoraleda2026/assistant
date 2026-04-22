# 🧪 Guía de Testing - Integración Gemini + Telegram + Kafka

## ✅ Pre-requisitos

### 1. Servicios Externos Activos
```bash
# PostgreSQL (base de datos)
- URL: localhost:5432
- Usuario: user_admin
- Contraseña: password_seguro
- Base: assistant_pro

# Redis (caché de memoria)
- URL: localhost:6379
- Sin contraseña (por defecto)

# Kafka (message broker)
- Brokers: localhost:9092
- Topics a crear: telegram-inbound, telegram-outbound
```

### 2. Credenciales Configuradas
```bash
# Variables de entorno OBLIGATORIAS
export GEMINI_API_KEY="tu-api-key-de-google"
export TELEGRAM_BOT_TOKEN="tu-token-del-bot-telegram"
export TELEGRAM_BOT_USERNAME="nombre_del_bot"

# Opcional (con valores por defecto)
export KAFKA_SERVERS="localhost:9092"
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
```

### 3. Obtener Credenciales
- **Gemini API Key**: https://aistudio.google.com/app/apikeys
- **Telegram Bot Token**: Habla con @BotFather en Telegram

## 🚀 Inicio Rápido

### Paso 1: Compilar Proyecto
```bash
cd /path/to/java-assistant
mvn clean compile -DskipTests
```

### Paso 2: Ejecutar Aplicación
```bash
mvn spring-boot:run
```

**Salida esperada**:
```
[INFO] 2026-04-10T18:55:00.000Z  INFO - Spring Boot Application started
[INFO] 2026-04-10T18:55:01.000Z  INFO - ================================
[INFO] 2026-04-10T18:55:01.000Z  INFO - 🤖 Configurando Google Generative AI (Gemini)
[INFO] 2026-04-10T18:55:01.000Z  INFO - ================================
[INFO] 2026-04-10T18:55:02.000Z  INFO - Listening on port 8080
```

### Paso 3: Probar desde Telegram
1. Abre Telegram
2. Busca el bot por su nombre (ej: `@Jmp_hal_bot`)
3. Envía cualquier mensaje, ejemplo:
   ```
   Hola, ¿cuál es la capital de Francia?
   ```
4. **Resultado esperado**: El bot responderá con la respuesta de Gemini en segundos

## 📊 Verificación en Logs

Durante la ejecución, verifica estos logs en orden:

### 1️⃣ Recepción de Mensaje
```
[INFO] POST /api/telegram/webhook - 200 OK
[INFO] Webhook update recibido para chat: 8448893815
```

### 2️⃣ Publicación a Kafka
```
[INFO] Mensaje Telegram recibido y publicado en Kafka: 8448893815
```

### 3️⃣ Consumo desde Kafka
```
[INFO] Evento Kafka recibido: 8448893815
[INFO] Procesando solicitud para chat: 8448893815
```

### 4️⃣ Recuperación de Contexto
```
[DEBUG] Contexto recuperado de longitud: 245
```

### 5️⃣ Llamada a Gemini
```
[INFO] Enviando solicitud a Gemini para chat: 8448893815
[DEBUG] Llamando a Gemini para generar respuesta. Contexto size: 245, Prompt size: 42
[INFO] Respuesta generada por Gemini con tamaño: 156
```

### 6️⃣ Actualización de Memoria
```
[DEBUG] Memoria actualizada para chat: 8448893815
```

### 7️⃣ Publicación de Respuesta
```
[INFO] Respuesta Telegram publicada en Kafka: 8448893815
[INFO] Respuesta enviada a Kafka para 8448893815
```

### 8️⃣ Envío a Telegram
```
[INFO] Respuesta enviada a Telegram para chat: 8448893815
```

## 🔍 Troubleshooting

### ❌ Error: "Could not resolve dependencies"
```
Solución: Asegurate que la conexión a internet es estable
mvn clean install -U
```

### ❌ Error: "GEMINI_API_KEY not found"
```
Solución: Configurar variable de entorno
export GEMINI_API_KEY="tu-key-aqui"
```

### ❌ Redis Connection Refused
```
Solución: Iniciar Redis
# macOS/Linux
redis-server

# Windows (con WSL)
redis-server --port 6379
```

### ❌ Kafka Connection Refused
```
Solución: Iniciar Kafka
# Desde directorio de Kafka
./bin/kafka-server-start.sh config/server.properties
```

### ❌ Bot no responde en Telegram
```
Pasos a verificar:
1. El bot está en modo /start
2. El webhook está registrado en Telegram API
3. Los topics "telegram-inbound" y "telegram-outbound" existen en Kafka
4. TelegramConsumer y TelegramSender están corriendo
```

## 📈 Métricas de Performance

### Tiempos esperados por componente
| Componente | Tiempo Típico |
|-----------|--------------|
| Webhook → Kafka | 50-100ms |
| Kafka → AssistantService | 100-200ms |
| Redis (contexto) | 10-50ms |
| Gemini API | 2-5 segundos |
| Actualización memoria | 20-50ms |
| Kafka → TelegramSender | 100-200ms |
| Telegram API | 500-1000ms |
| **TOTAL** | **3-7 segundos** |

## 🧬 Testing Avanzado

### Test 1: Conversación Multi-turno
```
Usuario: ¿Cuál es Tu nombre?
Bot: [respuesta]

Usuario: ¿Recuerdas lo que preguntaste?
Bot: [respuesta con contexto de pregunta anterior]
```

### Test 2: Manejo de Errores
```
# Detener Gemini API key inválida
export GEMINI_API_KEY="invalid-key"

Usuario: Cualquier mensaje
Bot: "Lo siento, hubo un error procesando tu solicitud"
```

### Test 3: Carga de Memoria
```
# Si Redis >= 20 mensajes, debe activar sumarización
Envia 25 mensajes consecutivos
# Verificar en logs:
[INFO] Umbral de Redis (25 msgs) alcanzado para 8448893815
[DEBUG] Enviando 20 mensajes antiguos a SummarizationService
```

### Test 4: Recovery de Fallos
```
# Simular desconexión de Kafka
Esperar a que se reconecte automáticamente

Usuario: Enviar mensaje después de reconexión
Bot: [respuesta normal]
```

## 📝 Debugging Adicional

### Ver mensajes en Kafka
```bash
# Terminal 1: Consumir topic inbound
cd /path/to/kafka
bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic telegram-inbound

# Terminal 2: Consumir topic outbound
bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic telegram-outbound
```

### Ver estado de Redis
```bash
redis-cli
> KEYS chat:*
> LRANGE chat:8448893815 0 -1
> DBSIZE
```

### Verificar PostgreSQL
```bash
psql -h localhost -U user_admin -d assistant_pro
> SELECT * FROM session_summary WHERE chat_id = '8448893815';
```

## 🎯 Checklist Final

- [ ] Servicios externos (PostgreSQL, Redis, Kafka) activos
- [ ] Variables de entorno configuradas
- [ ] Proyecto compila sin errores
- [ ] Aplicación arranca sin excepciones
- [ ] Webhook recibe updates (logs muestran POST)
- [ ] Mensajes aparecen en Kafka
- [ ] Gemini API responde (logs muestran respuesta)
- [ ] Respuesta llega a Telegram en 3-7 segundos
- [ ] Contexto se mantiene entre mensajes
- [ ] Memoria se almacena en Redis/PostgreSQL

## 🚨 En Caso de Problemas

1. **Revisar logs completos**: `mvn spring-boot:run | grep -i error`
2. **Verificar conectividad**: `telnet localhost 5432` (PostgreSQL)
3. **Monitorear recursos**: `top` o Task Manager
4. **Consultar documentación**: Ver `GEMINI_INTEGRATION.md`
5. **Issues conocidos**: Revisar README.md

---

**Última actualización**: 2026-04-10  
**Versión**: 1.0.0  
**Status**: ✅ Production Ready
