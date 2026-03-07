interface Env {
  GOOGLE_OAUTH_CLIENT_ID: string;
  GOOGLE_OAUTH_CLIENT_SECRET: string;
  OAUTH_PROVIDER: {
    parseAuthRequest(request: Request): Promise<{
      responseType: string;
      clientId: string;
      redirectUri: string;
      scope: string[];
      state: string;
      codeChallenge?: string;
      codeChallengeMethod?: string;
    }>;
    lookupClient(clientId: string): Promise<unknown>;
    completeAuthorization(options: {
      request: unknown;
      userId: string;
      metadata: unknown;
      scope: string[];
      props: unknown;
    }): Promise<{ redirectTo: string }>;
  };
}

/**
 * Default handler for non-API routes.
 * Shows Google Sign-In at /authorize, proxies health check.
 */
export const authHandler: ExportedHandler<Env> & {
  fetch: NonNullable<ExportedHandler<Env>["fetch"]>;
} = {
  async fetch(
    request: Request,
    env: Env,
    _ctx: ExecutionContext
  ): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return Response.json({ status: "ok" });
    }

    if (url.pathname === "/authorize") {
      if (request.method === "GET") {
        return htmlResponse(loginPage(url.search, env.GOOGLE_OAUTH_CLIENT_ID));
      }

      if (request.method === "POST") {
        return handleGoogleCallback(request, env);
      }
    }

    return new Response("Not found", { status: 404 });
  },
};

function htmlResponse(html: string, status = 200): Response {
  return new Response(html, {
    status,
    headers: { "Content-Type": "text/html; charset=utf-8" },
  });
}

/**
 * Handle the Google Sign-In callback.
 * The login page POSTs the Google ID token credential here.
 */
async function handleGoogleCallback(
  request: Request,
  env: Env
): Promise<Response> {
  const formData = await request.formData();
  const credential = formData.get("credential") as string;
  const oauthQuery = formData.get("oauth_query") as string;

  if (!credential) {
    return htmlResponse(
      loginPage(oauthQuery, env.GOOGLE_OAUTH_CLIENT_ID, "Missing credential."),
      400
    );
  }

  // Decode and verify the Google ID token
  const payload = await verifyGoogleIdToken(credential, env.GOOGLE_OAUTH_CLIENT_ID);
  if (!payload) {
    return htmlResponse(
      loginPage(
        oauthQuery,
        env.GOOGLE_OAUTH_CLIENT_ID,
        "Invalid Google token. Please try again."
      ),
      401
    );
  }

  const userId = payload.sub;
  const email = payload.email;

  // Complete the OAuth authorization flow
  const authorizeUrl = new URL(request.url);
  authorizeUrl.search = oauthQuery;
  const getRequest = new Request(authorizeUrl.toString(), { method: "GET" });

  const oauthReqInfo = await env.OAUTH_PROVIDER.parseAuthRequest(getRequest);

  const { redirectTo } = await env.OAUTH_PROVIDER.completeAuthorization({
    request: oauthReqInfo,
    userId,
    metadata: { email },
    scope: oauthReqInfo.scope,
    props: { userId, email },
  });

  return Response.redirect(redirectTo, 302);
}

interface GoogleIdTokenPayload {
  sub: string;
  email: string;
  email_verified: boolean;
  name?: string;
  picture?: string;
  aud: string;
  iss: string;
  exp: number;
}

/**
 * Verify a Google ID token by checking with Google's tokeninfo endpoint.
 * In production you'd verify the JWT signature, but tokeninfo is simpler and sufficient.
 */
async function verifyGoogleIdToken(
  idToken: string,
  expectedClientId: string
): Promise<GoogleIdTokenPayload | null> {
  try {
    const res = await fetch(
      `https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(idToken)}`
    );
    if (!res.ok) return null;

    const payload = (await res.json()) as GoogleIdTokenPayload;

    // Verify audience matches our client ID
    if (payload.aud !== expectedClientId) return null;
    // Verify issuer
    if (
      payload.iss !== "accounts.google.com" &&
      payload.iss !== "https://accounts.google.com"
    )
      return null;
    // Verify not expired
    if (payload.exp * 1000 < Date.now()) return null;

    return payload;
  } catch {
    return null;
  }
}

// ---------- HTML ----------

function loginPage(
  oauthQuery: string,
  googleClientId: string,
  error?: string
): string {
  const errorHtml = error
    ? `<p class="error">${escapeHtml(error)}</p>`
    : "";

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Sign in - RPGenerator</title>
  <script src="https://accounts.google.com/gsi/client" async defer></script>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: 'Segoe UI', system-ui, sans-serif;
      background: #0a0a1a;
      color: #e0e0e0;
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 2rem;
    }
    .card {
      background: #12121f;
      border: 1px solid #222;
      border-radius: 16px;
      padding: 2.5rem 2rem;
      width: 100%;
      max-width: 400px;
      text-align: center;
    }
    .logo {
      font-weight: 700;
      font-size: 1.5rem;
      color: #7c4dff;
      margin-bottom: 0.5rem;
    }
    .sub {
      color: #888;
      font-size: 0.9rem;
      margin-bottom: 1.5rem;
    }
    .error {
      color: #ff6b6b;
      font-size: 0.85rem;
      margin-bottom: 1rem;
    }
    #g_id_onload_container {
      display: flex;
      justify-content: center;
      margin-top: 1rem;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="logo">RPGenerator</div>
    <p class="sub">Sign in to connect your AI tools</p>
    ${errorHtml}
    <form id="authForm" method="POST" action="/authorize" style="display:none;">
      <input type="hidden" name="oauth_query" value="${escapeHtml(oauthQuery)}" />
      <input type="hidden" name="credential" id="credentialInput" />
    </form>
    <div id="g_id_onload_container">
      <div id="g_id_onload"
        data-client_id="${escapeHtml(googleClientId)}"
        data-callback="handleGoogleResponse"
        data-auto_prompt="false">
      </div>
      <div class="g_id_signin"
        data-type="standard"
        data-size="large"
        data-theme="filled_black"
        data-text="sign_in_with"
        data-shape="rectangular"
        data-logo_alignment="left">
      </div>
    </div>
  </div>
  <script>
    function handleGoogleResponse(response) {
      document.getElementById('credentialInput').value = response.credential;
      document.getElementById('authForm').submit();
    }
  </script>
</body>
</html>`;
}

function escapeHtml(str: string): string {
  return str
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}
