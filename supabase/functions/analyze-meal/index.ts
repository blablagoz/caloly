import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.57.4";

const JSON_HEADERS = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
};

const MODELS = Array.from(new Set([
  Deno.env.get("GEMINI_MODEL") || "gemini-3.6-flash",
  "gemini-3-flash-preview",
  "gemini-2.5-flash",
]));

type RequestBody = {
  mode?: "photo" | "text";
  imageBase64?: string;
  mimeType?: string;
  description?: string;
};

const responseSchema = {
  type: "object",
  additionalProperties: false,
  properties: {
    isFoodPresent: { type: "boolean" },
    confirmationQuestion: {
      type: "string",
      description: "Kullanıcıya Türkçe sorulacak kısa doğrulama sorusu.",
    },
    foods: {
      type: "array",
      minItems: 0,
      maxItems: 10,
      items: {
        type: "object",
        additionalProperties: false,
        properties: {
          name: { type: "string" },
          brand: { type: ["string", "null"] },
          estimatedGrams: { type: "number", minimum: 1, maximum: 3000 },
          gramsMin: { type: "number", minimum: 1, maximum: 3000 },
          gramsMax: { type: "number", minimum: 1, maximum: 3000 },
          calories: { type: "number", minimum: 0, maximum: 5000 },
          caloriesMin: { type: "number", minimum: 0, maximum: 5000 },
          caloriesMax: { type: "number", minimum: 0, maximum: 5000 },
          proteinGrams: { type: "number", minimum: 0, maximum: 500 },
          carbsGrams: { type: "number", minimum: 0, maximum: 1000 },
          fatGrams: { type: "number", minimum: 0, maximum: 500 },
          confidence: { type: "number", minimum: 0, maximum: 1 },
        },
        required: [
          "name", "brand", "estimatedGrams", "gramsMin", "gramsMax",
          "calories", "caloriesMin", "caloriesMax", "proteinGrams",
          "carbsGrams", "fatGrams", "confidence",
        ],
      },
    },
    overallConfidence: { type: "number", minimum: 0, maximum: 1 },
    analysisNotes: { type: ["string", "null"] },
  },
  required: ["isFoodPresent", "confirmationQuestion", "foods", "overallConfidence", "analysisNotes"],
};

const prompt = (mode: "photo" | "text", description?: string) => `
Sen Caloly için Türkçe yanıt veren dikkatli bir yemek analiz asistanısın.
${mode === "photo" ? "Görselde gerçekten görünen" : `Kullanıcının yazdığı "${description}" içindeki`} yiyecek ve içecekleri ayrı kalemler halinde belirle.
Bir tabağın farklı bileşenlerini (pilav, kuru fasulye, yoğurt gibi) ayrı ayrı yaz.
Marka veya restoran ancak açıkça belli ise brand alanına yaz; tahmin ederek marka uydurma.
Her kalem için gerçekçi porsiyon gramı, gram aralığı, toplam kcal ve makroları tahmin et.
Tek fotoğraftan gramajın kesin olmadığını unutma; sahte kesinlik verme ve aralıkları makul tut.
Kalori ve makrolar 100 gram başına değil, görünen/yazılan toplam porsiyon içindir.
confirmationQuestion alanı "Önündeki yemek pilav üstü kuru fasulye ve yanındaki kâsede yoğurt mu?" gibi doğal ve kısa bir Türkçe soru olsun.
${mode === "photo" ? "Görselde yemek yoksa isFoodPresent=false ve foods=[] döndür; nesne uydurma." : "Metin yemek tarif etmiyorsa isFoodPresent=false ve foods=[] döndür."}
`;

function envKey(name: "publishable" | "secret"): string | undefined {
  const modern = Deno.env.get(name === "publishable" ? "SUPABASE_PUBLISHABLE_KEYS" : "SUPABASE_SECRET_KEYS");
  if (modern) {
    try {
      const parsed = JSON.parse(modern);
      if (typeof parsed.default === "string") return parsed.default;
    } catch { /* fall through to legacy key */ }
  }
  return Deno.env.get(name === "publishable" ? "SUPABASE_ANON_KEY" : "SUPABASE_SERVICE_ROLE_KEY") || undefined;
}

function json(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return json(405, { message: "Bu işlem desteklenmiyor." });
  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const publishableKey = envKey("publishable");
    const secretKey = envKey("secret");
    const geminiKey = Deno.env.get("GEMINI_API_KEY");
    if (!supabaseUrl || !publishableKey || !secretKey || !geminiKey) {
      return json(503, { message: "Yapay zekâ servisi henüz hazır değil." });
    }

    const authorization = request.headers.get("Authorization");
    if (!authorization?.startsWith("Bearer ")) return json(401, { message: "Yeniden giriş yapmalısın." });
    const userClient = createClient(supabaseUrl, publishableKey, {
      global: { headers: { Authorization: authorization } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: { user }, error: userError } = await userClient.auth.getUser();
    if (userError || !user) return json(401, { message: "Oturumun sona ermiş. Yeniden giriş yap." });

    const body = await request.json() as RequestBody;
    const mode = body.mode;
    if (mode !== "photo" && mode !== "text") return json(400, { message: "Geçersiz analiz isteği." });
    if (mode === "photo" && (!body.imageBase64 || body.imageBase64.length > 8_000_000)) {
      return json(413, { message: "Fotoğraf çok büyük veya okunamadı." });
    }
    if (mode === "text" && (!body.description || body.description.trim().length < 3)) {
      return json(400, { message: "Yemeği biraz daha ayrıntılı yaz." });
    }

    const hasUnlimitedPhotoScans = user.app_metadata?.caloly_unlimited_ai === true;
    let remainingPhotoScans: number | null = null;
    if (mode === "photo" && !hasUnlimitedPhotoScans) {
      const admin = createClient(supabaseUrl, secretKey, { auth: { persistSession: false } });
      const { data: quota, error: quotaError } = await admin.rpc("consume_caloly_photo_quota", {
        p_user_id: user.id,
        p_limit: 3,
      });
      if (quotaError) {
        console.error("quota_error", quotaError.code);
        return json(503, { message: "Fotoğraf kotası şu anda kontrol edilemiyor." });
      }
      if (!quota?.allowed) {
        return json(429, {
          message: "Saatlik 3 fotoğraftan tanıma hakkını kullandın. Daha sonra tekrar dene.",
          resetAt: quota?.resetAt,
        });
      }
      remainingPhotoScans = Number(quota.remaining);
    }

    const inputParts: Record<string, unknown>[] = [{ text: prompt(mode, body.description?.trim()) }];
    if (mode === "photo") {
      inputParts.push({
        inlineData: {
          mimeType: body.mimeType === "image/png" ? "image/png" : "image/jpeg",
          data: body.imageBase64,
        },
      });
    }
    const geminiRequest = {
      method: "POST",
      headers: { "Content-Type": "application/json", "x-goog-api-key": geminiKey },
      body: JSON.stringify({
        contents: [{ role: "user", parts: inputParts }],
        generationConfig: {
          responseFormat: {
            text: { mimeType: "application/json", schema: responseSchema },
          },
        },
      }),
    };
    let geminiResponse: Response | null = null;
    for (const model of MODELS) {
      const response = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`,
        geminiRequest,
      );
      geminiResponse = response;
      if (response.ok || response.status !== 404) break;
    }
    if (!geminiResponse) return json(503, { message: "Yapay zekâ şu anda yanıt veremiyor." });
    if (!geminiResponse.ok) {
      console.error("gemini_error", geminiResponse.status);
      return json(503, { message: "Yapay zekâ şu anda yanıt veremiyor. Biraz sonra tekrar dene." });
    }
    const geminiBody = await geminiResponse.json();
    const text = geminiBody?.candidates?.[0]?.content?.parts
      ?.map((part: { text?: string }) => part.text || "")
      .join("")
      .trim();
    if (!text) return json(503, { message: "Yapay zekâ yanıt vermedi. Tekrar deneyebilirsin." });

    let result: Record<string, unknown>;
    try {
      result = JSON.parse(text.replace(/^```json\s*/i, "").replace(/\s*```$/, ""));
    } catch {
      return json(503, { message: "Yapay zekâ yanıtı okunamadı. Tekrar deneyebilirsin." });
    }
    if (result.isFoodPresent !== true || !Array.isArray(result.foods) || result.foods.length === 0) {
      return json(422, { message: "Yemek bulunamadı" });
    }
    delete result.isFoodPresent;
    return json(200, {
      ...result,
      remainingPhotoScans,
      unlimitedPhotoScans: hasUnlimitedPhotoScans,
    });
  } catch (error) {
    console.error("analyze_meal_unhandled", error instanceof Error ? error.name : "unknown");
    return json(503, { message: "Yemek analizi şu anda tamamlanamadı." });
  }
});
