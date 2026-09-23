# License + Anti-Tamper

Aplikasi memeriksa `expiry.json` dari GitHub sebelum fitur utama dan service dijalankan.

## Konfigurasi

`expiry.json`:

- `enabled`: false memblokir aplikasi.
- `expires_at`: tanggal/jam UTC kedaluwarsa.
- `package_name`: package ID yang harus cocok.
- `min_version_code`: versi minimum yang diizinkan.
- `signature_sha256`: SHA-256 sertifikat signing APK. Jika diisi, APK yang ditandatangani ulang akan diblokir.

## Mengunci sertifikat produksi

Setelah membuat APK release dengan keystore produksi, ambil SHA-256 sertifikatnya, lalu masukkan ke `signature_sha256`, misalnya:

`AA:BB:CC:...`

Jangan memasukkan private keystore/password ke repository GitHub. Gunakan GitHub Actions Secrets untuk proses signing.

## Catatan keamanan

Ini adalah pemeriksaan anti-tamper ringan. APK yang dimodifikasi secara agresif masih dapat dipatch pada level kode. Untuk distribusi Google Play, pertimbangkan Play Integrity API dan pindahkan keputusan lisensi penting ke backend yang kamu kontrol.
