# KastamonuShop

**KastamonuShop**, açık kaynaklı [PhotoCraft](https://github.com/storytold/photocraft) masaüstü görüntü işleme çekirdeğinin (`photocraft-engine` + `photocraft-gpu` + `craft-libs`) **sıfır ödünlü, donanım hızlandırmalı (Vulkan `wgpu` 30)** yerel Android portudur.

## Mimari Özet

| Katman | Teknoloji | Rol |
| :--- | :--- | :--- |
| **Görüntü & Belge Çekirdeği** | `photocraft-engine`, `photocraft-doc`, `photocraft-io`, `photocraft-paint`, `craft-cms` | PSD/PSB, PCRAFT, RAW, TIFF, EXR, HEIF, PNG/JPEG/WebP, katmanlar, fırçalar, ICC renk yönetimi (masaüstü `ui-egui` hariç). |
| **GPU Kompozitör** | `photocraft-gpu` (`wgpu` 30 Vulkan + GLES fallback) | Doğrudan Android `SurfaceView` (`ANativeWindow`) üzerine sıfır kopyalı (zero-copy) donanım hızlandırmalı çizim. |
| **FFI Köprüsü** | `uniffi` 0.29 + `jni` / `ndk` / `raw-window-handle` | Belge/katman/fırça/SAF akışları UniFFI üzerinden; yüksek hızlı Vulkan yüzey yaşam döngüsü doğrudan JNI üzerinden çalışır. |
| **Android Arayüz** | Kotlin + Jetpack Compose + Material 3 (API 29+) | Tam ekran tuval, iki parmak pan/zoom/döndürme, S-Pen/kalem basınç & eğim desteği, SAF dosya açma/dışa aktarma. |

## GitHub'da Kurulum ve Bulutta Otomatik APK Derleme

1. GitHub'da yeni bir depo oluştur (örneğin `KastamonuShop`).
2. Bu paketin içeriğini depoya gönder ve üç kaynak depoyu submodule olarak ekle:
   ```bash
   git init
   git add .
   git submodule add https://github.com/storytold/photocraft.git third_party/photocraft
   git submodule add https://github.com/storytold/craft-libs.git third_party/craft-libs
   git submodule add https://github.com/storytold/craft-i18n.git third_party/craft-i18n
   git commit -m "Initial KastamonuShop Android port (Phase 1)"
   git branch -M main
   git remote add origin https://github.com/<KULLANICI_ADIN>/KastamonuShop.git
   git push -u origin main
   ```
3. GitHub'da **Actions** sekmesine gir: **`Build KastamonuShop APK`** iş akışı otomatik olarak Rust + Android NDK r27 + `cargo-ndk` + Gradle kurup `KastamonuShop-arm64-debug-apk` dosyasını derleyip indirilebilir hale getirir.

*(Alternatif: Submodule yerine elindeki `photocraft-main.zip`, `craft-libs-main.zip` ve `craft-i18n-main.zip` arşivlerini doğrudan `third_party/photocraft`, `third_party/craft-libs` ve `third_party/craft-i18n` klasörlerine açarak da yerel derleme yapabilirsin.)*

## Yerel Bilgisayarda Derleme

Gereksinimler: Rust 1.95+, `rustup target add aarch64-linux-android`, `cargo install cargo-ndk`, Android NDK r27 (`ANDROID_NDK_HOME`), JDK 17, Gradle.

```bash
bash scripts/build-rust-android.sh
cd android && gradle assembleDebug
```

## Lisans ve Atıf

KastamonuShop, üst kaynak projelerle uyumlu olarak **MIT OR Apache-2.0** çift lisansı altındadır. Üst kaynak atıfları için `NOTICE` dosyasına bakınız.
