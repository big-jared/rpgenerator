interface Env {
  BACKEND_URL: string;
  WORKER_SHARED_SECRET: string;
}

interface OAuthProps {
  userId: string;
  email: string;
}

/**
 * MCP handler that proxies authenticated requests to the Cloud Run backend.
 * OAuthProvider validates the bearer token and sets ctx.props before calling this.
 */
export const mcpHandler: ExportedHandler<Env> & {
  fetch: NonNullable<ExportedHandler<Env>["fetch"]>;
} = {
  async fetch(
    request: Request,
    env: Env,
    ctx: ExecutionContext & { props: OAuthProps }
  ): Promise<Response> {
    const { userId, email } = ctx.props;

    // Proxy the MCP request to the Cloud Run backend
    const backendUrl = `${env.BACKEND_URL}/mcp`;

    // Clone headers, add user identity for the backend
    const headers = new Headers(request.headers);
    headers.set("X-User-Id", userId);
    headers.set("X-User-Email", email);
    headers.set("X-Worker-Secret", env.WORKER_SHARED_SECRET);
    // Remove the OAuth bearer token — backend trusts the worker
    headers.delete("Authorization");

    try {
      const backendResponse = await fetch(backendUrl, {
        method: request.method,
        headers,
        body: request.method === "DELETE" ? undefined : request.body,
      });

      // Forward the response back to the client
      const responseHeaders = new Headers(backendResponse.headers);

      return new Response(backendResponse.body, {
        status: backendResponse.status,
        headers: responseHeaders,
      });
    } catch (e) {
      console.error("Backend proxy error:", e);
      return Response.json(
        {
          jsonrpc: "2.0",
          error: { code: -32603, message: "Backend unavailable" },
          id: null,
        },
        { status: 502 }
      );
    }
  },
};
