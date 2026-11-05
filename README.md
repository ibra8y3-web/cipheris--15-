# 🛡️ Cipheris (سيفيرس) - نظام المراسلة اللامركزي والمشفر الفائق
### Next-Generation Sovereign, Encrypted P2P & Mesh Communications Architecture

<div align="center">

![Cipheris Banner](https://img.shields.io/badge/Security-AES--256--GCM%20%7C%20Curve25519-00E676?style=for-the-badge&logo=shield)
![Decentralized](https://img.shields.io/badge/Architecture-P2P%20%7C%20Nostr%20%7C%20Blossom%20%7C%20IPFS-00B0FF?style=for-the-badge&logo=ipfs)
![Platform](https://img.shields.io/badge/Android-Jetpack%20Compose%20%7C%20Kotlin-7C4DFF?style=for-the-badge&logo=android)
![Zero-Knowledge](https://img.shields.io/badge/Privacy-100%25%20Zero--Knowledge%20Local--First-FFAB00?style=for-the-badge)
![Universal Links](https://img.shields.io/badge/Universal%20Links-Render%20Web%20Bridge-10B981?style=for-the-badge)
![Status](https://img.shields.io/badge/Build-Passing-brightgreen?style=for-the-badge)

<br/>

<img src="assets/screenshots/hero_banner.jpg" alt="Cipheris Futuristic Mesh Hero" width="800" style="border-radius: 12px; box-shadow: 0 8px 24px rgba(0,230,118,0.25);" />

<br/>

<p align="center">
  <b>منظومة تواصل سيادية متكاملة ومفتوحة المصدر مصممة للعمل في أصعب الظروف والشبكات المعزولة، مع معمارية لامركزية بالكامل متعددة الطبقات (P2P + Nostr/Blossom + Mesh Gossip Relay) وتشفير عسكري من الطرف إلى الطرف (E2EE)، مدعومة بروابط عالمية ذكية (Universal Web Links) ومحرك راحة بصرية متطور.</b>
</p>

[✨ المميزات الرئيسية](#-المميزات-الرئيسية-core-features) •
[📱 واجهات التطبيق](#-معاينة-واجهات-التطبيق-ui-showcase) •
[🌐 الروابط العالمية والتوجيه](#-الروابط-العالمية-والتوجيه-الذكي-universal-web-routing) •
[👁️ التحكم في السطوع وراحة العين](#️-التحكم-في-السطوع-وراحة-العين-eye-comfort-system) •
[📱 مشاركة الـ QR والباركود](#-مشاركة-الباركود-ورموز-qr-المؤمنة-qr-sharing) •
[🗺️ الخريطة المعمارية](#️-الخريطة-المعمارية-architecture-map) •
[🔐 منظومة التشفير](#-منظومة-التشفير-cryptographic-suite) •
[🚀 البدء والتثبيت](#-دليل-التثبيت-والبدء-quickstart)

---

</div>

---

## 📱 معاينة واجهات التطبيق (UI Showcase)

<div align="center">

| 💬 المحادثات الفردية المشفرة (P2P Chat) | 👥 المجموعات وقنوات البث (Groups & Channels) | 📲 الربط والمسح الذكي (QR Connect) |
| :---: | :---: | :---: |
| <img src="assets/screenshots/chat_screen.jpg" width="280" alt="Encrypted P2P Chat UI" /> | <img src="assets/screenshots/group_channel.jpg" width="280" alt="Decentralized Group and Channel UI" /> | <img src="assets/screenshots/qr_connect.jpg" width="280" alt="QR Barcode Connect UI" /> |

</div>

---

---

## 🌟 نظرة عامة (Overview)

**Cipheris** ليس مجرد تطبيق مراسلة تقليدي، بل هو **بروتوكول اتصالات لامركزي مستقل** صُمم لحماية حرية التواصل وخصوصية المستخدمين المطلقة.
يعمل التطبيق بدون أي خوادم مركزية للتحكم أو تجميع البيانات، معتمداً على تمثيل كل هاتف كـ **عقدة مستقلة ذاتية السيادة (Sovereign Node)** قادرة على بث واستقبال الرسائل، تداول الملفات الضخمة، والتحقق من الهويات الرقمية بطريقة صفرية المعرفة (**Zero-Knowledge**).

---

## 🗺️ الخريطة المعمارية (Architecture Map)

```
                               ┌──────────────────────────────────────────────┐
                               │             📱 Cipheris Client Node          │
                               │  (Jetpack Compose + Clean MVVM + Room DB)    │
                               └──────────────────────┬───────────────────────┘
                                                      │
                         ┌────────────────────────────┼────────────────────────────┐
                         │                            │                            │
                         ▼                            ▼                            ▼
            ┌─────────────────────────┐  ┌─────────────────────────┐  ┌─────────────────────────┐
            │   🔒 Cryptographic Core │  │  📡 Hybrid Transport    │  │  📁 Safe Storage Engine │
            │  • Curve25519 Key Exch. │  │  • Nostr NIP-01 Relay   │  │  • AES-256-GCM at Rest  │
            │  • AES-256-GCM / HMAC   │  │  • Direct P2P Channel   │  │  • In-Memory RAM Player │
            │  • Double-Ratchet Style │  │  • Blossom Blob CDN     │  │  • Zero Cache Leakage   │
            │  • Ed25519 Signatures   │  │  • Gossip Node Mesh     │  │  • Secure Sandbox App   │
            └────────────┬────────────┘  └────────────┬────────────┘  └────────────┬────────────┘
                         │                            │                            │
                         └────────────────────────────┼────────────────────────────┘
                                                      │
                                                      ▼
                       ┌─────────────────────────────────────────────────────────────┐
                       │                   🌐 Decentralized Fabric                    │
                       │   [Nostr Relays] ⟷ [Blossom CDN] ⟷ [P2P Peers] ⟷ [IPFS]    │
                       └─────────────────────────────────────────────────────────────┘
```

---

## ✨ المميزات الرئيسية (Core Features)

### 💬 1. المراسلة الفردية والمجموعات والقنوات (Chats, Groups & Channels)
* 🔒 **محادثات مباشرة (1-on-1 Direct Chats):** اتصال مباشر ومتبادل بين العقد مع مفاتيح مشتقة فريدة لكل محادثة وصندوق تسليم مخصص `cipheris_inbox_<nodeId>`.
* 👥 **مجموعات مشفرة لا مركزية (Encrypted Groups):** تدار المجموعة بدون خادم مركزي، مع تقنية الترحيل المزدوج (**Dual-Path Gossip Relay**) التي تبث الرسائل لموضوع المجموعة وتوزع نسخاً مشفرة في صناديق الأعضاء لضمان وصول الرسالة فوراً.
* 📢 **قنوات البث المفتوحة والمشفرة (Broadcast Channels):** قنوات عامة وخاصة تتيح نشر المحتوى والملفات مع نظام صلاحيات للمالك والمشرفين والمحررين موثق رقمياً.
* ⏳ **مؤقت التدمير الذاتي (Self-Destruct Messages):** حذف فيزيائي للرسائل والوسائط بعد انقضاء الوقت المحدد من جميع الأطراف.

---

### 🌐 2. الروابط العالمية والتوجيه الذكي (Universal Web Routing)
* 🔗 **روابط ويب رسمية مدعومة بالكامل:**
  - **رابط المستخدم:** `https://cipheris-web-1.onrender.com/u/<NodeId>`
  - **رابط المجموعة:** `https://cipheris-web-1.onrender.com/g/<GroupId>?name=...`
  - **رابط القناة:** `https://cipheris-web-1.onrender.com/c/<ChannelId>?name=...&key=...`
* ⚡ **توجيه فوري بدون وسيط (Auto-Disambiguation):** يتعرف التطبيق تلقائياً وبدقة على نوع الرابط ويفتح النافذة المناسبة فوراً (محادثة مباشرة، انضمام لمجموعة، أو اشتراك بقناة).
* 🛡️ **تكامل Digital Asset Links:** تمكين الربط التلقائي عبر ملف `.well-known/assetlinks.json` لفتح الروابط داخل التطبيق مباشرة دون شاشات وسيطة.

---

### 👁️ 3. التحكم في السطوع وراحة العين (Eye Comfort System)
* ☀️ **تخفيف توهج اللون الأبيض (White Tone Intensity):** محرك رياضي لمعايرة حرارة الألوان (`lerpColor`) يقلل إجهاد العين في الوضع الفاتح عن طريق تحويل الأبيض الساطع إلى درجات دافئة ومريحة.
* 🌙 **راحة ليلية فائقة (Night Softness):** خفض التوهج الحاد للنصوص البيضاء في الوضع الليلي للحفاظ على الرؤية الليلية.
* 🎚️ **شريط تمرير دقيق من 0% إلى 100%** مع 4 أوضاع سريعة مسبقة الضبط:
  - **ناصع (0%):** الأبيض القياسي عالي السطوع.
  - **مريح (35%):** إزالة الوهج الحاد مع بقاء الخلفية بيضاء ناصعة.
  - **دافئ (70%):** درجات كريمية دافئة مثالية للقراءة الطويلة.
  - **خافت (100%):** أقصى درجات الهدوء وتخفيف إجهاد العين.

---

### 📱 4. مشاركة الباركود ورموز QR المؤمنة (QR Sharing)
* 🖼️ **تصدير ومشاركة صورة الباركود بنقرة واحدة:** زر مباشر أسفل أي باركود في التطبيق لتوليد ومشاركة صورة بدقة فائقة (`PNG`) عبر WhatsApp، Telegram، Facebook، والبريد الإلكتروني.
* 🛡️ **نقل آمن عبر FileProvider:** حفظ مؤقت داخل مساحات الكاش الخاصة بالتطبيق ومشاركتها بتصاريح قراءة مؤقتة مشفرة (`FLAG_GRANT_READ_URI_PERMISSION`).
* 🏷️ **توليد تلقائي لنصوص الشرح والربط المباشر** مرفقة مع الصورة لتسهيل المسح والانضمام.

---

### 📡 5. المحرك الهجين لنقل الوسائط والملفات (Smart Hybrid Blossom / P2P Transmission)
* 🌸 **دعم مصفوفة خوادم Blossom & Nostr Relays:** رفع وتنزيل الملفات والوسائط ككتل ثنائية مشفرة بالكامل (Blobs) مع التحقق من سلامة البيانات عبر بصمة **SHA-256**.
* ⚡ **نقل مباشر من نظير إلى نظير (Direct P2P Sockets):** تبادل الملفات والبيانات الحساسة مباشرة عبر اتصالات P2P مشفرة دون الحاجة لوسطاء.
* 📦 **تقطيع الكتل والتحميل الموازي (Chunked Parallel Streams):** إرسال واستقبال الملفات الضخمة كحزم مجزأة لضمان استقرار الإرسال حتى في أسوأ حالات الشبكة.

---

### 📂 6. عارض الملفات والمستندات المشفرة المدمج (Secure Sandbox File Viewer)
* 📑 **عارض ملفات PDF مدمج:** تصفح المستندات وتكبيرها والبحث فيها محلياً دون تسريب أي صفحات للقرص العام.
* 💻 **مستعرض الأكواد البرمجية (Syntax Highlighter):** قراءة وتلوين الشيفرات البرمجية للغات متعددة (Kotlin, Java, Python, C++, HTML, JS, JSON).
* 🗜️ **مستكشف الأرشيف (ZIP / RAR Viewer):** استعراض محتويات الملفات المضغوطة وفك محتوياتها مؤقتاً بأمان.
* 🎵 **مشغلات وسائط متقدمة (Native Audio & Video Players):** مشغل صوتي تفاعلي مع موجة ترددية ومشغل فيديو عالي الدقة مع أدوات تحكم سريعة (5 ثوانٍ تقديم/ترجيع).
* 🔍 **محلل الطبقات الثنائية (Hex & Strings Inspector):** فحص البيانات الثنائية واستخراج النصوص الداخلية من أي ملف مشبوه للتحقق الأمني.
* 📲 **تثبيت التطبيقات (Direct APK Installer):** إمكانية مشاركة وتثبيت تطبيقات الأندرويد مباشرة عبر `FileProvider` رسمي وآمن.

---

## 🔐 منظومة التشفير (Cryptographic Suite)

| الطبقة / الغرض | الخوارزمية المستخدمة | الوصف الفني |
| :--- | :--- | :--- |
| **تشفير الرسائل الفورية** | `Curve25519 + AES-256-GCM` | تشفير تام من النهاية إلى النهاية (E2EE) بمفاتيح فريدة لكل طرف |
| **تشفير الملفات والوسائط** | `XOR Dynamic Stream + AES-GCM` | تعمية فورية لتدفق البايتات قبل لمس القرص أو الإرسال للشبكة |
| **التحقق من سلامة البيانات** | `SHA-256 Checksum` | منع التلاعب بالبيانات وضمان مطابقة الملفات 100% |
| **التوقيعات الرقمية** | `Ed25519 Signatures` | إثبات ملكية العقدة والمصادقة على هوية ناشري القنوات والمجموعات |
| **التخزين المشفر في الراحة** | `Android EncryptedSharedPreferences & Room` | حفظ إعدادات المحفظة والهوية محلياً بمفاتيح أجهزة الأمان (Keystore) |

---

## 📁 هيكلية المشروع البرمجية (Project Structure)

```
app/src/main/java/com/example/
├── MainActivity.kt                      # نقطة الدخول الرئيسية وإدارة النوافذ والـ Intent والسمات
├── data/
│   ├── decentralized/
│   │   ├── SmartHybridTransmissionEngine.kt # محرك النقل الهجين بين Blossom و P2P
│   │   ├── NostrBlossomRelayService.kt      # خدمات الترحيل عبر مصفوفة Nostr
│   │   ├── DirectP2PChannelService.kt       # الاتصال المباشر عبر السوكيت بين الأجهزة
│   │   ├── IpfsChunkingService.kt           # تقطيع الكتل الثنائية وإدارتها
│   │   └── DecentralizedModels.kt           # نماذج البيانات والقياسات اللاسلكية
│   ├── local/
│   │   ├── AppDatabase.kt                   # قاعدة بيانات Room المحلية
│   │   ├── ChatDao.kt                       # واجهة الاستعلامات والعمليات المحلية
│   │   └── ChatEntities.kt                  # جداول الرسائل والعقد والمجموعات والقنوات
│   ├── repository/
│   │   └── ChatRepository.kt                # مستودع إدارة البيانات والربط اللامركزي
│   └── util/
│       ├── DeepLinkHelper.kt                # محلل الروابط العالمية وبناء معرّفات الويب والـ QR
│       ├── FileEncryptor.kt                 # محرك التشفير وتدوير المفاتيح
│       └── IdentityCryptoUtil.kt            # توليد الهويات والمفاتيح العامة والخاصة
└── ui/
    ├── screens/
    │   ├── ChatScreens.kt                   # الشاشات التفاعلية للمحادثات والقنوات والمجموعات
    │   ├── DocumentViewer.kt                # عارض المستندات وملفات الأكواد و PDF
    │   ├── BarcodeGenerator.kt              # مولد ومشارك صور الباركود والـ QR
    │   └── BarcodeScanner.kt                # ماسح الرموز الشريطية و QR بالكاميرا
    ├── theme/
    │   ├── Color.kt                         # لوحة الألوان السيادية ومحرك راحة العين
    │   └── Theme.kt                         # سمة Material Design 3 المخصصة
    └── viewmodel/
        └── ChatViewModel.kt                 # إدارة الحالة والمنطق البرمجي (StateFlow)
```

---

## 🚀 دليل التثبيت والبدء (Quickstart)

### المتطلبات الأساسية
* نظام تشغيل **Android 8.0 (API 26)** أو أحدث.
* بيئة تطوير **Android Studio Ladybug (2024.2+)** أو أحدث.
* حزمة تطوير جافا **JDK 17**.

### خطوات البناء والتشغيل
1. **استنساخ المستودع:**
   ```bash
   git clone https://github.com/your-username/Cipheris.git
   cd Cipheris
   ```
2. **مزامنة الحزم والاعتمادات:**
   افتح المشروع في Android Studio، ودع Gradle ينهي عملية المزامنة التلقائية.
3. **توليد حزمة التثبيت (APK):**
   ```bash
   ./gradlew assembleDebug
   ```
   ستجد ملف الـ APK الناتج في المسار:
   `app/build/outputs/apk/debug/app-debug.apk`

---

## 📄 الترخيص (License)

هذا المشروع مرخص تحت رخصة **MIT License** - يمنحك الحرية الكاملة في الاستخدام والتطوير والتوزيع مع الحفاظ على حقوق الملكية المفتوحة.

<div align="center">
  <sub>تم تطوير وصياغة هذا النظام لضمان السيادة الرقمية والحرية المطلقة للتواصل الآمن 🛡️✨</sub>
</div>

