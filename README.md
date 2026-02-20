<div align="center">
  <h1>🦏 RhinoAI: Smart Rhinoplasty Detection App</h1>
  <p><i>Mobil cihazlar üzerinden rinoplasti (burun estetiği) analizi yaparak, yanıltıcı içerikleri tespit eden yapay zeka tabanlı Android uygulaması.</i></p>
</div>

---

## 🎯 Projenin Amacı / Project Purpose
Sosyal medya ve dijital platformlarda paylaşılan "öncesi-sonrası" estetik görsellerinin doğruluğunu cihaz üzerinde (Edge AI) denetleyerek;
* ❌ **Yanıltıcı İçerikleri Tespit Etmek:** Manipüle edilmiş estetik görsellerini yakalamak,
* 🛡️ **Şeffaflık Sağlamak:** Tüketicileri hatalı bilgilendirmeden korumak,
* ⚡ **Mobil Çözüm:** Ağır sunucu süreçlerine gerek kalmadan, yapay zeka analizini doğrudan kullanıcının cebine taşımak.

---

## ## 📸 Ekran Görüntüleri / App Screenshots

<div align="center"> <img src="https://github.com/user-attachments/assets/783b6921-8418-4919-9daf-aceace04e0a1" width="250" /> &nbsp;&nbsp;&nbsp;&nbsp; <img src="https://github.com/user-attachments/assets/d59ed510-cc1e-4b3c-83e7-2bcbead731f3" width="250" /> &nbsp;&nbsp;&nbsp;&nbsp; <img src="https://github.com/user-attachments/assets/6e41a064-d7f7-4a38-bc07-13132b63afe2" width="250" /> </div> <p align="center"> <i>Anasayfa ve analiz sonuç ekranları</i> </p>


*Not: Uygulamanın anasayfasına ve analiz sonuç ekranlarına ait görseller yukarıda yer almaktadır.*


---

## 🛠️ Kullanılan Teknolojiler & Modeller / Tech Stack
Proje, derin öğrenme modellerinin mobil platformlarda en verimli ve hızlı şekilde çalışması için optimize edilmiştir.

**Mimari Yapılar ve Edge AI:**
* 🚀 **Vision Transformer (ViT)** - Ana Model (Mobil donanımlara uyarlanmış `.tflite` formatı)
* 🧠 **Google ML Kit** - Yüz takibi (Face Detection) ve burun bölgesi izolasyonu (Face Mesh)

**Mobil Geliştirme (App):**
* 💻 **Kotlin & Jetpack Compose (Material3)**
* 📸 **CameraX & Coil** - Kamera yönetimi ve görüntü işleme

---

## 📊 Analiz ve Sonuçlar / Analysis & Results
* **👥 Çoklu Fotoğraf Doğrulaması:** Yüz biyometrisi ile seçilen fotoğrafların aynı kişiye ait olup olmadığı kontrol edilir.
* **🎯 Hassas Kırpma (Precision Cropping):** Görüntüdeki sadece cerrahi müdahale yapılan burun bölgesi izole edilir, arka plan gürültüsü modele sokulmaz.
* **🤖 Cihaz Üzerinde Analiz (On-Device Inference):** İnternet bağlantısı gerektirmeden çalışarak gizliliği korur ve %100 offline olarak bir **doğallık/güven skoru (confidence score)** üretir.

---

## 📁 Proje Klasör Yapısı / Folder Structure

```plaintext
RhinoAI/
├── app/src/main/java/.../rhinoplasty/ # 💻 Kotlin kaynak kodları ve Compose UI ekranları
├── app/src/main/assets/               # 🧠 Edge AI için optimize edilmiş TFLite modelleri
├── app/src/main/res/                  # 🎨 Uygulama ikonları, temalar ve statik kaynaklar
├── build.gradle.kts                   # ⚙️ Gradle bağımlılıkları ve modül yapılandırması
└── README.md                          # 📖 Proje dokümantasyonu
```

⚙️ Ön Koşullar / Prerequisites

Projeyi bilgisayarınızda sorunsuz bir şekilde derleyip çalıştırabilmek için aşağıdaki yazılımların kurulu olduğundan emin olun:

🟢 Android Studio: En güncel kararlı sürüm (Latest stable version).

☕ Java Development Kit (JDK): JDK 17 veya üzeri.

📱 Test Cihazı: Fiziksel bir Android cihaz veya Android Studio üzerinden yapılandırılmış bir Sanal Cihaz (Emulator).

🚀 Kurulum ve Kullanım / Installation

Projeyi yerel bilgisayarınızda (Android Studio) çalıştırmak için aşağıdaki adımları izleyebilirsiniz:

1. Repoyu Klonlayın (Clone the repository):

   ``` git clone https://github.com/denizkilinc2/RhinoAI.git ```

 2. Projeyi Açın ve Senkronize Edin:

Android Studio'da File > Open menüsünü kullanarak indirdiğiniz projeyi seçin.

Gerekli kütüphanelerin inmesi için Gradle senkronizasyonunun (Sync) tamamlanmasını bekleyin.

3. Uygulamayı Çalıştırın:

Fiziksel bir Android cihaz bağlayarak veya Emülatör üzerinden yeşil renkli Run (▶️) butonuna basarak uygulamayı derleyin.

🔑 Önemli Notlar / Important Notes

Kamera İzni: Uygulamanın yapay zeka destekli akıllı tarama özelliklerini kullanabilmesi için ilk açılışta talep edilen "Kamera Erişim İzni"ni onaylamanız gerekmektedir.

Gizlilik (Privacy): Uygulama, görüntü analizi yaparken hiçbir veriyi dış sunuculara göndermez; tüm derin öğrenme süreçleri cihaz üzerinde (on-device) gerçekleşir.

👨‍💻 Geliştirici Ekip / Developers

Deniz Kılınç

Elif Onat
