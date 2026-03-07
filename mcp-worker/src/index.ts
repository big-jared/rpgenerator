import OAuthProvider from "@cloudflare/workers-oauth-provider";
import { mcpHandler } from "./mcp-handler.js";
import { authHandler } from "./auth-handler.js";

export default new OAuthProvider({
  apiRoute: "/mcp",
  apiHandler: mcpHandler,
  defaultHandler: authHandler,
  authorizeEndpoint: "/authorize",
  tokenEndpoint: "/token",
  clientRegistrationEndpoint: "/register",
});
