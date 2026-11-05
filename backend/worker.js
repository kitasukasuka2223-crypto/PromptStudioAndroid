const MODEL = "kling/v4/image-to-video";
const QUEUE = "https://queue.fal.run/" + MODEL;
function cors() { return { "Access-Control-Allow-Origin":"*", "Access-Control-Allow-Methods":"GET, POST, OPTIONS", "Access-Control-Allow-Headers":"Content-Type, Authorization", "Cache-Control":"no-store" }; }
function json(data, status=200) { return new Response(JSON.stringify(data), {status, headers:{...cors(),"Content-Type":"application/json"}}); }
function authorized(req, env) { return Boolean(env.APP_TOKEN && req.headers.get("Authorization") === "Bearer " + env.APP_TOKEN); }
async function falFetch(url, env, options={}) { return fetch(url, {...options, headers:{"Authorization":"Key "+env.FAL_KEY,...(options.headers||{})}}); }
export default {
 async fetch(request, env) {
  if (request.method === "OPTIONS") return new Response(null,{headers:cors()});
  const url = new URL(request.url);
  if (!authorized(request,env)) return json({error:"Kode akses backend tidak valid."},401);
  if (!env.FAL_KEY) return json({error:"FAL_KEY belum dipasang pada Worker."},500);
  if (request.method === "POST" && url.pathname === "/generate") {
   try {
    const body = await request.json();
    const imageData = body && body.imageData;
    const prompt = body && body.prompt;
    const duration = Number(body && body.duration);
    const resolution = body && body.resolution === "1080p" ? "1080p" : "720p";
    if (typeof imageData !== "string" || !/^data:image\/(png|jpeg|webp);base64,/i.test(imageData)) return json({error:"Gambar harus PNG, JPG, atau WebP."},400);
    if (imageData.length > 11200000) return json({error:"Gambar terlalu besar. Maksimal sekitar 8 MB."},413);
    if (typeof prompt !== "string" || !prompt.trim() || prompt.length > 5000) return json({error:"Prompt wajib diisi, maksimal 5.000 karakter."},400);
    if (![5,10].includes(duration)) return json({error:"Durasi yang didukung saat ini 5 atau 10 detik."},400);
    const response = await falFetch(QUEUE,env,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({prompt:prompt.trim(),start_image_url:imageData,duration,resolution})});
    const data = await response.json();
    if (!response.ok) return json({error:data.detail || data.error || "Fal gagal menerima permintaan."},response.status);
    return json({request_id:data.request_id,status:data.status});
   } catch (e) { return json({error:e.message || "Permintaan tidak dapat diproses."},500); }
  }
  const match = url.pathname.match(/^\/(status|result)$/);
  if (request.method === "GET" && match) {
   const id = url.searchParams.get("requestId") || "";
   if (!/^[0-9a-f-]{20,}$/i.test(id)) return json({error:"ID proses tidak valid."},400);
   const isStatus = match[1] === "status";
   const target = QUEUE + "/requests/" + encodeURIComponent(id) + (isStatus ? "/status" : "");
   const response = await falFetch(target,env);
   const data = await response.json();
   if (!response.ok) return json({error:data.detail || data.error || "Tidak dapat mengambil status atau hasil."},response.status);
   return json(data);
  }
  return json({error:"Rute tidak ditemukan."},404);
 }
}