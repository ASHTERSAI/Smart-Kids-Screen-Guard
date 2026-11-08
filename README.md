# Smart Kids Screen Guard (Android App)

Aplikasi Android cerdas untuk menjaga kebiasaan layar dan keselamatan anak berbasis AI Computer Vision lokal (On-Device).

## Fitur Utama
1. **AI Sleep Detection**: Mendeteksi saat anak tertidur saat menonton HP dan memicu karakter Bubu si Penjaga Tidur.
2. **Eye Distance Guard (30 cm)**: Memperkirakan jarak wajah ke layar menggunakan rumus pinhole camera terkalibrasi dan memicu eskalasi peringatan (Kiko si Penjaga Mata).
3. **Posture Guard**: Mendeteksi kemiringan leher dan posisi membungkuk dengan Lala si Pengingat Duduk.
4. **Eye Rest Reminder (20-20-20)**: Mengingatkan istirahat mata setiap 20 menit selama 20 detik.
5. **Parent Lock & Security**: Terenkripsi dengan Android Keystore & PBKDF2-HMAC-SHA256.
6. **Uninstall Protection**: Dukungan resmi Device Admin & Device Owner (level OS Android).
7. **Privasi 100% On-Device**: Tanpa penyimpanan foto/video dan tanpa koneksi ke cloud server.

## Cara Build & Jalankan di Android Studio
1. Clone repositori ini atau ekstrak file ZIP.
2. Buka folder root di Android Studio (Iguana / Jellyfish / Ladybug atau versi terbaru) dengan JDK 17.
3. Jalankan Gradle Sync.
4. Jalankan Unit Test:
   ```bash
   ./gradlew testDebugUnitTest
   ```
5. Kompilasi APK Debug & Release:
   ```bash
   ./gradlew assembleDebug assembleRelease
   ```
6. File APK yang dihasilkan:
   - `app/build/outputs/apk/debug/app-debug.apk`
   - `app/build/outputs/apk/release/app-release.apk`

## CI/CD Otomatis di GitHub
Project ini sudah dilengkapi GitHub Actions workflow di `.github/workflows/build_apk.yml`. Setiap push ke branch `main`/`master` akan otomatis mengompilasi APK dan menyediakannya sebagai artifact yang siap diunduh!
