/**
 * Objetivo: Servidor MCP para interactuar con el sistema de archivos.
 * Usuario: Gemini (via Java McpHost).
 */
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import fs from "fs/promises";
import path from "path";

const server = new Server({
    name: "assistant-filesystem",
    version: "1.0.0",
}, {
    capabilities: {
        tools: {},
    },
});

server.setRequestHandler(ListToolsRequestSchema, async () => ({
    tools: [{
        name: "read_safe_file",
        description: "Lee el contenido de un archivo en el directorio permitido.",
        inputSchema: {
            type: "object",
            properties: {
                path: { type: "string" },
            },
            required: ["path"],
        },
    }],
}));

server.setRequestHandler(CallToolRequestSchema, async (request) => {
    if (request.params.name === "read_safe_file") {
        const filePath = path.join(process.cwd(), request.params.arguments.path);
        const content = await fs.readFile(filePath, "utf-8");
        return { content: [{ type: "text", text: content }] };
    }
    throw new Error("Tool not found");
});

const transport = new StdioServerTransport();
await server.connect(transport);
console.error("MCP Filesystem Server running on stdio");
