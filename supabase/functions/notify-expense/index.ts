import { createClient } from "https://esm.sh/@supabase/supabase-js@2.47.10";

const encoder = new TextEncoder();

type PushJob = {
  id: string;
  event: "expense_created" | "expense_updated";
  group_id: string;
  expense_id: string;
  actor_id: string;
};

type ServiceAccount = {
  project_id: string;
  client_email: string;
  private_key: string;
};

Deno.serve(async (request) => {
  if (request.method !== "POST") {
    return json({ error: "method not allowed" }, 405);
  }

  const secret = Deno.env.get("PUSH_WEBHOOK_SECRET") ?? "";
  const authorization = request.headers.get("Authorization") ?? "";
  if (!secret || authorization !== `Bearer ${secret}`) {
    return json({ error: "unauthorized" }, 401);
  }

  let payload: Record<string, unknown>;
  try {
    payload = await request.json();
  } catch {
    return json({ error: "invalid json" }, 400);
  }

  const record = (payload.record as PushJob | undefined) ?? payload as unknown as PushJob;
  const jobId = record?.id ?? (payload.job_id as string | undefined);
  if (!jobId) {
    return json({ error: "missing job" }, 400);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  if (!supabaseUrl || !serviceKey) {
    return json({ error: "missing supabase env" }, 500);
  }

  const supabase = createClient(supabaseUrl, serviceKey);
  const { data: job, error: jobError } = await supabase
    .from("push_jobs")
    .select("id, event, group_id, expense_id, actor_id")
    .eq("id", jobId)
    .maybeSingle<PushJob>();

  if (jobError || job == null) {
    return json({ error: "job not found" }, 404);
  }

  const { data: group } = await supabase
    .from("groups")
    .select("name")
    .eq("id", job.group_id)
    .maybeSingle();
  const { data: actor } = await supabase
    .from("profiles")
    .select("display_name")
    .eq("id", job.actor_id)
    .maybeSingle();
  const { data: expense } = await supabase
    .from("expenses")
    .select("description, installment_index, installment_count, expense_categories(name)")
    .eq("id", job.expense_id)
    .maybeSingle();

  const categoryName = (expense?.expense_categories as { name?: string } | null)?.name ?? null;
  const label = expenseTitle(
    categoryName,
    expense?.description ?? "",
    expense?.installment_index ?? null,
    expense?.installment_count ?? null,
  );
  const actorName = actor?.display_name?.trim() || "Alguien";
  const verb = job.event === "expense_updated" ? "editó un gasto" : "cargó un gasto";
  const body = label ? `${actorName} ${verb}: ${label}` : `${actorName} ${verb}`;
  const title = group?.name?.trim() || "Cuentas Claras";

  const { data: members } = await supabase
    .from("group_members")
    .select("user_id")
    .eq("group_id", job.group_id)
    .neq("user_id", job.actor_id);

  const userIds = (members ?? []).map((row) => row.user_id as string);
  if (userIds.length === 0) {
    return json({ sent: 0 });
  }

  const { data: tokens } = await supabase
    .from("device_push_tokens")
    .select("token")
    .in("user_id", userIds);

  const tokenList = (tokens ?? []).map((row) => row.token as string).filter(Boolean);
  if (tokenList.length === 0) {
    return json({ sent: 0 });
  }

  const serviceAccount = parseServiceAccount();
  const accessToken = await googleAccessToken(serviceAccount);
  let sent = 0;
  const invalid: string[] = [];

  for (const token of tokenList) {
    const result = await sendFcm(serviceAccount.project_id, accessToken, {
      token,
      title,
      body,
      groupId: job.group_id,
      expenseId: job.expense_id,
    });
    if (result === "ok") sent += 1;
    if (result === "unregistered") invalid.push(token);
  }

  if (invalid.length > 0) {
    await supabase.from("device_push_tokens").delete().in("token", invalid);
  }

  return json({ sent, invalid: invalid.length });
});

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export function expenseTitle(
  categoryName: string | null,
  note: string,
  installmentIndex: number | null,
  installmentCount: number | null,
): string {
  const trimmedNote = note.trim();
  const base = !categoryName
    ? trimmedNote
    : trimmedNote.length === 0
    ? categoryName.trim()
    : `${categoryName.trim()} · ${trimmedNote}`;
  if (installmentIndex == null || installmentCount == null) return base;
  const suffix = `(${installmentIndex}/${installmentCount})`;
  if (base.endsWith(suffix) || base.includes(` ${suffix}`)) return base;
  return `${base} ${suffix}`.trim();
}

function parseServiceAccount(): ServiceAccount {
  const raw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT") ?? "";
  if (!raw) throw new Error("missing FIREBASE_SERVICE_ACCOUNT");
  return JSON.parse(raw) as ServiceAccount;
}

async function googleAccessToken(account: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const assertion = await signJwt(
    {
      iss: account.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    },
    account.private_key,
  );
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) {
    throw new Error(`oauth token failed: ${await response.text()}`);
  }
  const jsonBody = await response.json() as { access_token: string };
  return jsonBody.access_token;
}

async function sendFcm(
  projectId: string,
  accessToken: string,
  params: { token: string; title: string; body: string; groupId: string; expenseId: string },
): Promise<"ok" | "unregistered" | "error"> {
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token: params.token,
          notification: {
            title: params.title,
            body: params.body,
          },
          data: {
            groupId: params.groupId,
            expenseId: params.expenseId,
          },
          android: {
            priority: "HIGH",
            notification: {
              channel_id: "expenses",
            },
          },
        },
      }),
    },
  );
  if (response.ok) return "ok";
  const text = await response.text();
  if (text.includes("UNREGISTERED") || text.includes("NOT_FOUND")) return "unregistered";
  console.error("fcm send failed", text);
  return "error";
}

async function signJwt(claims: Record<string, unknown>, pem: string): Promise<string> {
  const header = { alg: "RS256", typ: "JWT" };
  const encodedHeader = base64Url(encoder.encode(JSON.stringify(header)));
  const encodedPayload = base64Url(encoder.encode(JSON.stringify(claims)));
  const unsigned = `${encodedHeader}.${encodedPayload}`;
  const key = await importPrivateKey(pem);
  const signature = await crypto.subtle.sign(
    { name: "RSASSA-PKCS1-v1_5" },
    key,
    encoder.encode(unsigned),
  );
  return `${unsigned}.${base64Url(new Uint8Array(signature))}`;
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const contents = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const binary = Uint8Array.from(atob(contents), (char) => char.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8",
    binary.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}
