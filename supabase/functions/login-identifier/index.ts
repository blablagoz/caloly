import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const headers = { "Content-Type": "application/json" };

const normalizeUsername = (value: string) =>
  value.trim().normalize("NFC").toLocaleLowerCase("tr-TR");

Deno.serve(async (request) => {
  if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
  try {
    const { identifier, password } = await request.json();
    if (typeof identifier !== "string" || typeof password !== "string") {
      return new Response(JSON.stringify({ error: "invalid_credentials" }), { status: 400, headers });
    }

    const url = Deno.env.get("SUPABASE_URL")!;
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const admin = createClient(url, serviceKey, { auth: { persistSession: false } });
    const { data } = await admin.from("login_identifiers").select("login_email").eq("username_key", normalizeUsername(identifier)).maybeSingle();
    if (!data?.login_email) return new Response(JSON.stringify({ error: "invalid_credentials" }), { status: 401, headers });

    // Validate the password with GoTrue before returning the private login address.
    const tokenResponse = await fetch(`${url}/auth/v1/token?grant_type=password`, {
      method: "POST",
      headers: { "Content-Type": "application/json", apikey: anonKey },
      body: JSON.stringify({ email: data.login_email, password }),
    });
    if (!tokenResponse.ok) return new Response(JSON.stringify({ error: "invalid_credentials" }), { status: 401, headers });
    return new Response(JSON.stringify({ email: data.login_email }), { status: 200, headers });
  } catch {
    return new Response(JSON.stringify({ error: "login_unavailable" }), { status: 503, headers });
  }
});
