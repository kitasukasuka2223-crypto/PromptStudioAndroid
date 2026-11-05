# Backend Prompt Studio — Wan 2.2 (mode gratis)

Backend proxy untuk menghubungkan APK Prompt Studio ke model Wan 2.2 Image-to-Video melalui Hugging Face Spaces publik.

## Yang dibutuhkan
- Akun Cloudflare (paket Workers Free cukup untuk proxy sederhana).
- Akun fal.ai dan API key **tidak diperlukan**.
- Tidak perlu menambahkan FAL_KEY atau APP_TOKEN.

## Deploy melalui Cloudflare Dashboard
1. Masuk ke https://dash.cloudflare.com/ lalu buka **Workers & Pages**.
2. Pilih **Create** → **Worker** → **Deploy**.
3. Beri nama, misalnya `prompt-studio-wan`.
4. Pilih **Edit code** / **Quick Edit**.
5. Ganti seluruh kode contoh dengan isi file `backend/worker.js` dari repository PromptStudioAndroid.
6. Tekan **Deploy**.
7. Salin alamat Worker yang berakhiran `.workers.dev`.
8. Buka APK Prompt Studio → bagian **AI Video Generator · Wan 2.2** → tempel alamat Worker.

## Cara menggunakan
1. Pilih gambar di bagian Animasi dari Gambar.
2. Buat atau tempel prompt di Prompt hasil.
3. Tempel URL Worker di AI Video Generator.
4. Pilih durasi 2,5–4,5 detik dan kualitas proses.
5. Tekan **GENERATE VIDEO AI**. Tunggu sampai MP4 muncul, lalu tekan **SIMPAN VIDEO AI KE GALERI**.

## Batasan mode gratis
- Menggunakan GPU publik bersama di Hugging Face ZeroGPU; antrean, cold start, batas penggunaan, atau kegagalan sementara bisa terjadi.
- Durasi dibatasi maksimal sekitar 4,5 detik untuk mengurangi risiko timeout pada GPU gratis.
- Ketersediaan Space publik tidak dijamin. Pemilik Space dapat mengubah atau menonaktifkan layanan sewaktu-waktu.
- Gambar dikirim ke layanan Hugging Face publik untuk diproses. Jangan unggah foto pribadi atau sensitif.

## Endpoint
- `GET /health` — memeriksa Worker.
- `POST /generate` — menerima multipart form berisi `image`, `prompt`, `duration`, dan `steps`; mengalirkan hasil SSE dari Wan 2.2.

Tidak ada API key AI atau token rahasia yang perlu dimasukkan ke APK.
