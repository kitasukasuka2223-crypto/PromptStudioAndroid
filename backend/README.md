# Backend Prompt Studio — Kling 4.0

Backend proxy untuk menghubungkan APK Prompt Studio ke fal.ai. API key fal tidak ditanam di APK.

## Yang dibutuhkan
- Akun Cloudflare Workers
- Akun fal.ai dan API key
- Dua secret Worker: FAL_KEY dan APP_TOKEN

## Deploy melalui Cloudflare Dashboard
1. Masuk ke Cloudflare Dashboard → Workers & Pages → Create → Worker.
2. Beri nama misalnya prompt-studio-api.
3. Buka Quick Edit, ganti seluruh kode contoh dengan isi file backend/worker.js dari repository ini.
4. Buka Settings → Variables and Secrets.
5. Tambahkan secret FAL_KEY dengan API key fal.ai milikmu.
6. Tambahkan secret APP_TOKEN dengan kode acak panjang yang kamu buat sendiri. Jangan kirim kode ini ke chat atau commit ke GitHub.
7. Deploy Worker dan salin URL Worker, misalnya https://prompt-studio-api.<akun>.workers.dev.
8. Masukkan URL Worker dan nilai APP_TOKEN ke bagian AI Video Generator di APK.

## Catatan biaya
Model default: kling/v4/image-to-video. fal.ai menagih berdasarkan durasi dan resolusi video. Periksa harga terkini di halaman model sebelum generate.

## Endpoint
- POST /generate — menerima gambar data URI, prompt, durasi, dan resolusi.
- GET /status?requestId=... — memeriksa proses.
- GET /result?requestId=... — mengambil URL MP4 hasil.

Kode akses Worker dikirim melalui header Authorization. API key fal hanya tersimpan sebagai secret di Cloudflare Worker.