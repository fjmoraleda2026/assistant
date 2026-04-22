package com.assistant.infrastructure.adapter.out.tools;

import com.assistant.application.port.out.ToolExecutorPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpHostManager implements ToolExecutorPort {

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.mcp.filesystem.base-path:.}")
    private String filesystemBasePath;

    @Value("${app.mcp.postgres.max-rows:50}")
    private int maxRows;

    @Override
    public String executeTool(String toolName, Map<String, Object> arguments) {
        log.info("MCP: Ejecutando herramienta '{}' con argumentos: {}", toolName, arguments);

        String normalized = toolName == null ? "" : toolName.toLowerCase();

        if (normalized.contains("filesystem.write")) {
            return executeFilesystemWrite(arguments);
        }
        if (normalized.contains("filesystem.list")) {
            return executeFilesystemList(arguments);
        }
        if (normalized.contains("filesystem")) {
            return executeFilesystemRead(arguments);
        }
        if (normalized.contains("postgres.describe")) {
            return executePostgresDescribe(arguments);
        }
        if (normalized.contains("postgres")) {
            return executePostgresQuery(arguments);
        }
        return "Error: Herramienta no soportada: " + toolName;
    }

    private String executePostgresQuery(Map<String, Object> arguments) {
        Object sqlArg = arguments.getOrDefault("sql", null);
        if (sqlArg == null) {
            return "Error: parametro 'sql' requerido para postgres.query";
        }

        String sql = sqlArg.toString().trim();

        // Seguridad: solo permitir SELECT
        String sqlUpper = sql.toUpperCase().replaceAll("\\s+", " ").trim();
        if (!sqlUpper.startsWith("SELECT")) {
            log.warn("MCP postgres: sentencia no permitida bloqueada: {}", sql);
            return "Error: solo se permiten sentencias SELECT.";
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            if (rows.isEmpty()) {
                return "Consulta ejecutada. Sin resultados.";
            }

            List<Map<String, Object>> limited = rows.size() > maxRows ? rows.subList(0, maxRows) : rows;

            String result = limited.stream()
                    .map(row -> row.entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining(", ")))
                    .collect(Collectors.joining("\n"));

            if (rows.size() > maxRows) {
                result += "\n[...truncado a " + maxRows + " filas de " + rows.size() + " totales...]";
            }

            log.info("MCP postgres: consulta ejecutada, {} filas devueltas", limited.size());
            return result;

        } catch (Exception e) {
            log.error("MCP postgres: error ejecutando query", e);
            return "Error ejecutando consulta: " + e.getMessage();
        }
    }

    private String executeFilesystemRead(Map<String, Object> arguments) {
        Object pathArg = arguments.getOrDefault("path", arguments.getOrDefault("fileName", null));
        if (pathArg == null) {
            return "Error: parametro 'path' requerido para filesystem.read";
        }
        String relativePath = pathArg.toString().trim();
        if (relativePath.contains("..") || Paths.get(relativePath).isAbsolute()) {
            log.warn("MCP filesystem: path traversal bloqueado: {}", relativePath);
            return "Error: acceso denegado. Solo se permiten rutas relativas.";
        }
        try {
            Path basePath = resolveFilesystemBasePath(arguments);
            Path resolvedPath = basePath.resolve(relativePath).normalize();
            if (!resolvedPath.startsWith(basePath)) {
                return "Error: acceso denegado. Ruta fuera del directorio permitido.";
            }
            if (!Files.exists(resolvedPath)) {
                return "Error: archivo no encontrado: " + relativePath;
            }
            String content = Files.readString(resolvedPath, StandardCharsets.UTF_8);
            if (content.length() > 3000) {
                content = content.substring(0, 3000) + "\n[...truncado a 3000 chars...]";
            }
            log.info("MCP filesystem: leido {} ({} chars)", relativePath, content.length());
            return content;
        } catch (IOException e) {
            log.error("MCP filesystem: error leyendo {}", relativePath, e);
            return "Error leyendo archivo: " + e.getMessage();
        }
    }

    private String executeFilesystemWrite(Map<String, Object> arguments) {
        Object pathArg = arguments.getOrDefault("path", arguments.getOrDefault("fileName", null));
        Object contentArg = arguments.getOrDefault("content", null);

        if (pathArg == null) {
            return "Error: parametro 'path' requerido para filesystem.write";
        }
        if (contentArg == null) {
            return "Error: parametro 'content' requerido para filesystem.write";
        }

        String relativePath = pathArg.toString().trim();
        String content = contentArg.toString();

        if (relativePath.contains("..") || Paths.get(relativePath).isAbsolute()) {
            log.warn("MCP filesystem.write: path traversal bloqueado: {}", relativePath);
            return "Error: acceso denegado. Solo se permiten rutas relativas.";
        }

        try {
            Path basePath = resolveFilesystemBasePath(arguments);
            Path resolvedPath = basePath.resolve(relativePath).normalize();

            if (!resolvedPath.startsWith(basePath)) {
                return "Error: acceso denegado. Ruta fuera del directorio permitido.";
            }

            // Crear directorios padre si no existen
            Path parentDir = resolvedPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            Files.writeString(resolvedPath, content, StandardCharsets.UTF_8);
            log.info("MCP filesystem.write: archivo escrito {} ({} chars)", relativePath, content.length());
            return "Archivo escrito exitosamente en: " + resolvedPath;

        } catch (IOException e) {
            log.error("MCP filesystem.write: error escribiendo {}", relativePath, e);
            return "Error escribiendo archivo: " + e.getMessage();
        }
    }

    private String executeFilesystemList(Map<String, Object> arguments) {
        Object pathArg = arguments.getOrDefault("path", arguments.getOrDefault("directory", "."));
        String relativePath = pathArg == null ? "." : pathArg.toString().trim();

        if (relativePath.contains("..") || Paths.get(relativePath).isAbsolute()) {
            log.warn("MCP filesystem.list: path traversal bloqueado: {}", relativePath);
            return "Error: acceso denegado. Solo se permiten rutas relativas.";
        }

        try {
            Path basePath = resolveFilesystemBasePath(arguments);
            Path resolvedPath = basePath.resolve(relativePath).normalize();

            if (!resolvedPath.startsWith(basePath)) {
                return "Error: acceso denegado. Ruta fuera del directorio permitido.";
            }

            if (!Files.exists(resolvedPath)) {
                return "Error: directorio no encontrado: " + relativePath;
            }

            if (!Files.isDirectory(resolvedPath)) {
                return "Error: la ruta no es un directorio: " + relativePath;
            }

            StringBuilder result = new StringBuilder("Contenido de: " + relativePath + "\n\n");

            // Listar directorios primero
            var dirList = Files.list(resolvedPath)
                    .filter(Files::isDirectory)
                    .sorted()
                    .limit(50)
                    .map(p -> "📁 " + p.getFileName().toString() + "/")
                    .toList();

            // Luego archivos
            var fileList = Files.list(resolvedPath)
                    .filter(Files::isRegularFile)
                    .sorted()
                    .limit(50)
                    .map(p -> {
                        try {
                            long size = Files.size(p);
                            String sizeStr = formatFileSize(size);
                            return "📄 " + p.getFileName().toString() + " (" + sizeStr + ")";
                        } catch (IOException e) {
                            return "📄 " + p.getFileName().toString() + " (?)";
                        }
                    })
                    .toList();

            if (dirList.isEmpty() && fileList.isEmpty()) {
                result.append("(vacío)");
            } else {
                dirList.forEach(d -> result.append(d).append("\n"));
                fileList.forEach(f -> result.append(f).append("\n"));
            }

            if (dirList.size() + fileList.size() >= 50) {
                result.append("\n[...truncado a 50 items...]");
            }

            log.info("MCP filesystem.list: {} directorios, {} archivos", dirList.size(), fileList.size());
            return result.toString();

        } catch (IOException e) {
            log.error("MCP filesystem.list: error listando {}", relativePath, e);
            return "Error listando directorio: " + e.getMessage();
        }
    }

    private String executePostgresDescribe(Map<String, Object> arguments) {
        Object tableArg = arguments.getOrDefault("table", null);
        if (tableArg == null) {
            return "Error: parametro 'table' requerido para postgres.describe";
        }

        String tableName = tableArg.toString().trim();

        // Validar nombre de tabla (solo caracteres alfanuméricos y underscore)
        if (!tableName.matches("^[a-zA-Z0-9_]+$")) {
            return "Error: nombre de tabla inválido";
        }

        try {
            // Obtener info de columnas
            String columnsSql = "SELECT column_name, data_type, is_nullable, column_default " +
                    "FROM information_schema.columns " +
                    "WHERE table_name = ? " +
                    "ORDER BY ordinal_position";

            List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnsSql, tableName);

            if (columns.isEmpty()) {
                return "Error: tabla no encontrada: " + tableName;
            }

            StringBuilder result = new StringBuilder("Estructura de tabla: " + tableName + "\n\n");

            for (Map<String, Object> col : columns) {
                String colName = col.get("column_name") != null ? col.get("column_name").toString() : "?";
                String dataType = col.get("data_type") != null ? col.get("data_type").toString() : "unknown";
                String nullable = "YES".equals(col.get("is_nullable")) ? "nullable" : "NOT NULL";
                String defaultVal = col.get("column_default") != null ? " DEFAULT " + col.get("column_default") : "";

                result.append("  • ").append(colName).append(": ").append(dataType)
                        .append(" [").append(nullable).append(defaultVal).append("]\n");
            }

            // Obtener índices y constraints
            String constraintsSql = "SELECT constraint_name, constraint_type " +
                    "FROM information_schema.table_constraints " +
                    "WHERE table_name = ?";

            List<Map<String, Object>> constraints = jdbcTemplate.queryForList(constraintsSql, tableName);

            if (!constraints.isEmpty()) {
                result.append("\nConstraints:\n");
                for (Map<String, Object> constraint : constraints) {
                    String constraintName = constraint.get("constraint_name") != null ? constraint.get("constraint_name").toString() : "?";
                    String constraintType = constraint.get("constraint_type") != null ? constraint.get("constraint_type").toString() : "?";
                    result.append("  • ").append(constraintName).append(" (").append(constraintType).append(")\n");
                }
            }

            log.info("MCP postgres.describe: tabla {} descrita, {} columnas", tableName, columns.size());
            return result.toString();

        } catch (Exception e) {
            log.error("MCP postgres.describe: error describiendo tabla {}", tableName, e);
            return "Error obteniendo estructura de tabla: " + e.getMessage();
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private Path resolveFilesystemBasePath(Map<String, Object> arguments) {
        Object override = arguments == null ? null : arguments.get("basePath");
        String configuredBasePath = override == null || override.toString().isBlank()
                ? filesystemBasePath
                : override.toString().trim();
        return Paths.get(configuredBasePath).toAbsolutePath().normalize();
    }
}
