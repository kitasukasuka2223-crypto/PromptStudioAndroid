// Prompt Studio — free Wan 2.2 Image-to-Video proxy.
// Uses a public Hugging Face ZeroGPU Space. No fal.ai key or API balance required.
// Shared public GPU capacity means queues, cold starts, and occasional failures are possible.
const HF_SPACE = "https://cbensimon-wan2-2-fp8da-aoti-preview2.hf.space";
const NEGATIVE_PROMPT = "色调艳丽, 过曝, 静态, 细节模糊不清, 字幕, 风格, 作品, 画作, 画面, 静止, 整体发灰, 最差质量, 低质量, JPEG压缩残留, 丑陋的, 残缺的, 多余的手指, 画得不好的手部, 画得不好的脸部, 畸形的, 毁容的, 形态畸形的肢体, 手指融合, 静止不动的画面, 杂乱的背景, 三条腿, 背景人很多, 倒着走";
function cors() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Cache-Control": "no-store"
  };
}
function json(data, status=200) {
  return new Response(JSON.stringify(data), {status, headers:{...cors(),"Content-Type":"application/json"}});
}
export default {
  async fetch(request) {
    if (request.method === "OPTIONS") return new Response(null,{status:204,headers:cors()});
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") return json({ok:true,model:"Wan 2.2 I2V"});
    if (request.method !== "POST" || url.pathname !== "/generate") return json({error:"Rute tidak ditemukan."},404);
    try {
      const form = await request.formData();
      const image = form.get("image");
      const prompt = String(form.get("prompt") || "").trim();
      const duration = Math.max(2.5, Math.min(4.5, Number(form.get("duration") || 3.5)));
      const steps = Math.max(4, Math.min(8, Number(form.get("steps") || 6)));
      if (!(image instanceof File) || !image.type.startsWith("image/")) return json({error:"Pilih gambar PNG, JPG, atau WebP terlebih dahulu."},400);
      if (image.size > 8*1024*1024) return json({error:"Ukuran gambar maksimal 8 MB."},413);
      if (!prompt || prompt.length > 5000) return json({error:"Prompt wajib diisi (maksimal 5.000 karakter)."},400);

      // Upload the original image to the Gradio Space.
      const uploadForm = new FormData();
      uploadForm.append("files",image,image.name || "input.png");
      const uploadResponse = await fetch(HF_SPACE + "/gradio_api/upload",{method:"POST",body:uploadForm});
      if (!uploadResponse.ok) throw new Error("Upload gambar ke server Wan gagal ("+uploadResponse.status+"). Coba lagi beberapa saat.");
      const paths = await uploadResponse.json();
      if (!Array.isArray(paths) || !paths[0]) throw new Error("Server Wan tidak mengembalikan lokasi gambar.");
      const imagePath = paths[0];
      const imageInput = {
        path:imagePath,
        url:HF_SPACE + "/gradio_api/file=" + imagePath,
        orig_name:image.name || "input.png",
        size:image.size,
        mime_type:image.type,
        meta:{_type:"gradio.FileData"}
      };
      const data = [
        imageInput, null, prompt, steps, NEGATIVE_PROMPT, duration,
        1, 1, 42, true, 6, "UniPCMultistep", 3.0, 16, false, true
      ];
      const submit = await fetch(HF_SPACE + "/gradio_api/call/generate_video",{
        method:"POST",
        headers:{"Content-Type":"application/json"},
        body:JSON.stringify({data})
      });
      if (!submit.ok) {
        const detail = await submit.text().catch(()=> "");
        throw new Error("Server Wan menolak permintaan ("+submit.status+"). "+detail.slice(0,300));
      }
      const submitted = await submit.json();
      if (!submitted.event_id) throw new Error("Server Wan tidak memberikan ID proses.");
      const stream = await fetch(HF_SPACE + "/gradio_api/call/generate_video/" + encodeURIComponent(submitted.event_id),{
        method:"GET",
        headers:{"Accept":"text/event-stream"}
      });
      if (!stream.ok || !stream.body) throw new Error("Tidak bisa membuka hasil proses dari server Wan ("+stream.status+").");
      return new Response(stream.body,{
        status:200,
        headers:{...cors(),"Content-Type":"text/event-stream; charset=utf-8","Cache-Control":"no-cache, no-transform","X-Accel-Buffering":"no"}
      });
    } catch (error) {
      return json({error:error && error.message ? error.message : "Terjadi kesalahan saat membuat video."},500);
    }
  }
};
