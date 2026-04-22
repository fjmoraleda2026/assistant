-- Objetivo: Inicialización del esquema de base de datos para el Asistente.
-- Usuario: Administrador de DB / Flyway / Liquibase.
-- Casos de Uso: Creación de tablas de persistencia para resúmenes y logs.

-- Tabla de resúmenes de sesión (Memoria a Largo Plazo)
CREATE TABLE IF NOT EXISTS session_summaries (
    chat_id VARCHAR(50) PRIMARY KEY,
    summary TEXT NOT NULL,
    last_update TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    message_count_processed BIGINT DEFAULT 0,
    metadata JSONB
);

-- Índice para búsquedas rápidas por chat
CREATE INDEX IF NOT EXISTS idx_session_summaries_chat_id ON session_summaries(chat_id);

-- Tabla opcional para auditoría de acciones MCP
CREATE TABLE IF NOT EXISTS mcp_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id VARCHAR(50),
    tool_name VARCHAR(100),
    arguments JSONB,
    result TEXT,
    executed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Proyectos de trabajo por chat/usuario
CREATE TABLE IF NOT EXISTS assistant_projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id VARCHAR(50) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    base_path VARCHAR(512),
    database_name VARCHAR(120),
    database_schema VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_assistant_projects_chat_name UNIQUE (chat_id, name)
);

ALTER TABLE assistant_projects
    ADD COLUMN IF NOT EXISTS base_path VARCHAR(512);

ALTER TABLE assistant_projects
    ADD COLUMN IF NOT EXISTS database_name VARCHAR(120);

ALTER TABLE assistant_projects
    ADD COLUMN IF NOT EXISTS database_schema VARCHAR(120);

CREATE INDEX IF NOT EXISTS idx_assistant_projects_chat
    ON assistant_projects(chat_id, updated_at DESC);

-- Sesiones por proyecto con rol IA
CREATE TABLE IF NOT EXISTS assistant_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    ai_role VARCHAR(40) NOT NULL DEFAULT 'generic',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_assistant_sessions_project
        FOREIGN KEY (project_id) REFERENCES assistant_projects(id) ON DELETE CASCADE,
    CONSTRAINT uk_assistant_sessions_project_name UNIQUE (project_id, name)
);

CREATE INDEX IF NOT EXISTS idx_assistant_sessions_project
    ON assistant_sessions(project_id, updated_at DESC);

-- Hechos confirmados del proyecto para inyectar contexto estable a la IA
CREATE TABLE IF NOT EXISTS assistant_project_facts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    fact TEXT NOT NULL,
    fact_type VARCHAR(30) NOT NULL DEFAULT 'GENERIC',
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    confidence INT,
    source VARCHAR(120),
    source_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assistant_project_facts_project
        FOREIGN KEY (project_id) REFERENCES assistant_projects(id) ON DELETE CASCADE
);

ALTER TABLE assistant_project_facts
    ADD COLUMN IF NOT EXISTS fact_type VARCHAR(30);

ALTER TABLE assistant_project_facts
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

ALTER TABLE assistant_project_facts
    ADD COLUMN IF NOT EXISTS confidence INT;

ALTER TABLE assistant_project_facts
    ADD COLUMN IF NOT EXISTS source_message TEXT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'assistant_project_facts'
          AND column_name = 'confirmed'
    ) THEN
        EXECUTE 'UPDATE assistant_project_facts
                 SET status = CASE WHEN confirmed IS TRUE THEN ''CONFIRMED'' ELSE ''PROPOSED'' END
                 WHERE status IS NULL';
    ELSE
        UPDATE assistant_project_facts
        SET status = 'CONFIRMED'
        WHERE status IS NULL;
    END IF;
END $$;

ALTER TABLE assistant_project_facts
    ALTER COLUMN status SET DEFAULT 'CONFIRMED';

UPDATE assistant_project_facts
SET status = 'CONFIRMED'
WHERE status = '';

ALTER TABLE assistant_project_facts
    ALTER COLUMN status SET NOT NULL;

UPDATE assistant_project_facts
SET fact_type = 'GENERIC'
WHERE fact_type IS NULL OR fact_type = '';

ALTER TABLE assistant_project_facts
    ALTER COLUMN fact_type SET DEFAULT 'GENERIC';

ALTER TABLE assistant_project_facts
    ALTER COLUMN fact_type SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_assistant_project_facts_project_status
    ON assistant_project_facts(project_id, status, updated_at DESC);

-- Catalogo de comandos disponibles en Telegram
CREATE TABLE IF NOT EXISTS command_catalog (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    command_key VARCHAR(120) NOT NULL UNIQUE,
    syntax VARCHAR(240) NOT NULL,
    description TEXT NOT NULL,
    example TEXT,
    category VARCHAR(40) NOT NULL,
    requires_project BOOLEAN NOT NULL DEFAULT FALSE,
    requires_session BOOLEAN NOT NULL DEFAULT FALSE,
    allowed_modes VARCHAR(40) NOT NULL DEFAULT 'BOTH',
    min_role VARCHAR(40),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 100,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_command_catalog_enabled_sort
    ON command_catalog(enabled, sort_order, command_key);

INSERT INTO command_catalog (command_key, syntax, description, example, category, requires_project, requires_session, allowed_modes, enabled, sort_order)
VALUES
    ('help', '/help', 'Muestra la ayuda y el listado de comandos disponibles.', '/help', 'info', FALSE, FALSE, 'BOTH', TRUE, 10),
    ('estado', '/estado', 'Muestra modo actual, proyecto y sesion activos.', '/estado', 'info', FALSE, FALSE, 'BOTH', TRUE, 15),
    ('proyectos', '/proyectos', 'Lista los proyectos actuales del usuario.', '/proyectos', 'project', FALSE, FALSE, 'BOTH', TRUE, 20),
    ('proyecto', '/proyecto crear <nombre>', 'Crea o selecciona proyecto activo.', '/proyecto crear asistente-core', 'project', FALSE, FALSE, 'BOTH', TRUE, 30),
    ('sesiones', '/sesiones', 'Lista sesiones del proyecto activo.', '/sesiones', 'session', TRUE, FALSE, 'SESSION', TRUE, 40),
    ('sesion', '/sesion nueva <nombre> [rol]', 'Crea, selecciona o cierra sesion activa.', '/sesion nueva fase1 architect', 'session', TRUE, FALSE, 'SESSION', TRUE, 50),
    ('rol', '/rol <architect|developer|analyst|personal_assistant|reviewer|devops|generic>', 'Cambia el rol de la sesion activa.', '/rol developer', 'session', TRUE, TRUE, 'SESSION', TRUE, 60),
    ('ai', '/ai <mock|gemini|grok|chatgpt|reset>', 'Alias de /ia para seleccionar proveedor IA o resetear al default.', '/ai gemini', 'ai', FALSE, FALSE, 'BOTH', TRUE, 61),
    ('ia', '/ia <mock|gemini|grok|chatgpt|reset>', 'Selecciona proveedor de IA para este chat o resetea al default.', '/ia gemini', 'ai', FALSE, FALSE, 'BOTH', TRUE, 62),
    ('modelo', '/modelo <nombre-modelo|reset>', 'Selecciona modelo de IA para este chat o resetea al default.', '/modelo gemini-2.0-flash', 'ai', FALSE, FALSE, 'BOTH', TRUE, 63),
    ('mcp', '/mcp <status|enable|disable|tools|logs|reset>', 'Controla herramientas MCP (Model Context Protocol) para este chat.', '/mcp status', 'mcp', FALSE, FALSE, 'BOTH', TRUE, 64),
    ('hechos', '/hechos', 'Lista hechos propuestos y confirmados del proyecto activo.', '/hechos', 'project', TRUE, FALSE, 'BOTH', TRUE, 65),
    ('hecho', '/hecho confirmar <id>', 'Confirma, rechaza o propone un hecho del proyecto activo.', '/hecho confirmar 123e4567-e89b-12d3-a456-426614174000', 'project', TRUE, FALSE, 'BOTH', TRUE, 66),
    ('stateless', '/stateless', 'Activa modo rapido sin memoria conversacional.', '/stateless', 'mode', FALSE, FALSE, 'BOTH', TRUE, 70),
    ('stateful', '/stateful', 'Activa modo con memoria conversacional.', '/stateful', 'mode', FALSE, FALSE, 'BOTH', TRUE, 80)
ON CONFLICT (command_key) DO NOTHING;

-- Asegura que comandos ya existentes queden actualizados tras despliegues incrementales
UPDATE command_catalog SET
    enabled = TRUE,
    syntax = '/proyectos',
    description = 'Lista los proyectos actuales del usuario.'
WHERE command_key = 'proyectos';

UPDATE command_catalog SET
    enabled = TRUE,
    syntax = '/proyecto crear <nombre> | /proyecto info | /proyecto ruta <path> | /proyecto bd <database> [schema]',
    description = 'Crea, selecciona o actualiza metadata estructurada del proyecto activo.',
    example = '/proyecto bd assistant_pro public'
WHERE command_key = 'proyecto';

UPDATE command_catalog SET
    enabled = TRUE,
    syntax = '/sesiones',
    description = 'Lista sesiones del proyecto activo.'
WHERE command_key = 'sesiones';

UPDATE command_catalog SET
    enabled = TRUE,
    syntax = '/sesion nueva <nombre> [rol]',
    description = 'Crea, selecciona o cierra sesion activa.'
WHERE command_key = 'sesion';

UPDATE command_catalog SET
    enabled = TRUE,
    syntax = '/rol <architect|developer|analyst|personal_assistant|reviewer|devops|generic>',
    description = 'Cambia el rol de la sesion activa.'
WHERE command_key = 'rol';

UPDATE command_catalog SET
    enabled = TRUE,
    syntax = '/ai <mock|gemini|grok|chatgpt|reset>',
    description = 'Alias de /ia para seleccionar proveedor IA o resetear al default.',
    example = '/ai gemini',
    category = 'ai',
    requires_project = FALSE,
    requires_session = FALSE,
    allowed_modes = 'BOTH',
    sort_order = 61
WHERE command_key = 'ai';

UPDATE command_catalog SET
    enabled = TRUE,
    syntax = '/ia <mock|gemini|grok|chatgpt|reset>',
    description = 'Selecciona proveedor de IA para este chat o resetea al default.',
    example = '/ia gemini',
    category = 'ai',
    requires_project = FALSE,
    requires_session = FALSE,
    allowed_modes = 'BOTH',
    sort_order = 62
WHERE command_key = 'ia';

UPDATE command_catalog SET
    enabled = TRUE,
    syntax = '/modelo <nombre-modelo|reset>',
    description = 'Selecciona modelo de IA para este chat o resetea al default.',
    example = '/modelo gemini-2.0-flash',
    category = 'ai',
    requires_project = FALSE,
    requires_session = FALSE,
    allowed_modes = 'BOTH',
    sort_order = 63
WHERE command_key = 'modelo';

UPDATE command_catalog SET
    enabled = TRUE,
    syntax = '/hechos [estado] [tipo]',
    description = 'Lista hechos del proyecto activo, con filtros opcionales por estado o tipo.',
    example = '/hechos confirmados database',
    category = 'project',
    requires_project = TRUE,
    requires_session = FALSE,
    allowed_modes = 'BOTH',
    sort_order = 65
WHERE command_key = 'hechos';

UPDATE command_catalog SET
    enabled = TRUE,
    syntax = '/hecho confirmar <id>',
    description = 'Confirma, rechaza o propone un hecho del proyecto activo.',
    example = '/hecho confirmar 123e4567-e89b-12d3-a456-426614174000',
    category = 'project',
    requires_project = TRUE,
    requires_session = FALSE,
    allowed_modes = 'BOTH',
    sort_order = 66
WHERE command_key = 'hecho';

UPDATE command_catalog SET enabled = TRUE WHERE command_key IN ('help', 'estado', 'ai', 'ia', 'modelo', 'mcp', 'stateless', 'stateful', 'hechos', 'hecho');

UPDATE command_catalog SET
    enabled = TRUE,
    syntax = '/mcp <status|enable|disable|tools|logs|reset>',
    description = 'Controla herramientas MCP (Model Context Protocol) para este chat.',
    example = '/mcp status',
    category = 'mcp',
    requires_project = FALSE,
    requires_session = FALSE,
    allowed_modes = 'BOTH',
    sort_order = 64
WHERE command_key = 'mcp';
