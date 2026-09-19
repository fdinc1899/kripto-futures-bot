# Kripto Futures Bot — Proje Dokümanı

Android (Kotlin + Jetpack Compose) kaldıraçlı kripto alım-satım botu.
Borsa: Binance USDⓈ-M Futures. Varsayılan mod: **TESTNET (Binance Demo)**.

## 1. Proje yapısı

```
kripto-futures-bot/
├── app/
│   ├── build.gradle.kts          # bağımlılıklar, imza, sürüm
│   ├── debug.keystore            # sabit debug imzası (güncelleme için)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/values/           # strings, colors, themes
│       └── java/com/fatih/futuresbot/
│           ├── FuturesBotApp.kt          # Application
│           ├── MainActivity.kt           # tek Activity + bildirim izni
│           ├── app/AppContainer.kt       # bağımlılık kabı (elle DI)
│           ├── domain/
│           │   ├── model/                # Models, Exchange, Order, Market,
│           │   │                         # Risk, Strategy, Bot, Trade
│           │   └── repository/           # AccountRepository, MarketRepository
│           ├── data/
│           │   ├── binance/              # hesap ve piyasa repository'leri
│           │   ├── settings/             # mod, parite, risk, strateji, bot,
│           │   │                         # bildirim ayarları (SharedPreferences)
│           │   └── history/              # işlem geçmişi (JSON dosyası)
│           ├── network/
│           │   ├── HttpClientFactory.kt
│           │   └── binance/              # REST istemci, imzalama, WebSocket,
│           │                             # hata eşleme, JSON yardımcıları
│           ├── trading/                  # ExchangeClient arayüzü, OrderManager,
│           │                             # RiskEngine, BotEngine, TradingGuard,
│           │                             # HistorySync, TradeMonitor
│           ├── strategy/                 # Indicators, StrategyEngine
│           ├── security/                 # Keystore şifreleme, CredentialStore
│           ├── notifications/            # Notifier
│           └── presentation/             # Compose ekranları ve ViewModel'ler
│               ├── dashboard, markets, chart, trade, positions,
│               ├── orders, bot, history, settings, onboarding
│               ├── navigation/MainShell.kt
│               ├── common/               # ortak bileşenler, biçimlendirme
│               └── theme/
└── .github/workflows/android.yml         # GitHub Actions ile APK derleme
```

Katmanlar ayrıdır: arayüz `presentation`, iş kuralları `trading` + `strategy`,
borsa erişimi `network`. Yeni borsa eklemek için `trading/ExchangeClient`
arayüzünü uygulamak yeterlidir (Bybit, OKX, Bitget).

## 2. Kurulum (telefon, Termux)

```bash
pkg install git
git clone https://github.com/fdinc1899/kripto-futures-bot.git
cd kripto-futures-bot
```

Derleme telefonda yapılmaz; GitHub Actions kullanılır.

## 3. APK oluşturma

- Otomatik: `git push` → **Actions → Android APK** → yeşil ✓ → **Artifacts →
  futures-bot-debug-apk**.
- Bilgisayarda yerel derleme:

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # imzasız release; kendi keystore'unla imzala
```

Debug imzası repodaki `app/debug.keystore` ile sabittir; bu sayede yeni sürüm
uygulama silinmeden kurulur. Bu anahtar yalnızca debug içindir, Play Store
yayını için ayrı bir release keystore üret.

## 4. Binance Demo (testnet) API anahtarı

1. `https://demo.binance.com` adresine Binance hesabınla gir.
2. **Demo işlemlerine başla** ile demo hesabını etkinleştir.
3. **Cüzdan → Vadeli işlemler → Varlığı Sıfırla** ile USDⓈ-M cüzdanına demo
   bakiye yükle.
4. **Profil → Demo İşlem API'si** → **API Oluştur** → **Sistem tarafından
   oluşturulan** → etiket ver.
5. **Kısıtlamaları düzenle** → **Vadeli İşlemleri Etkinleştir** açık,
   **Para Çekme** kapalı → Kaydet.
6. Anahtar ve gizli anahtarı uygulamadaki giriş ekranına yapıştır.

Gizli anahtar yalnızca bir kez gösterilir.

## 5. API anahtarı güvenliği

- Anahtarlar Android Keystore'da üretilen, cihazdan çıkarılamayan AES-256-GCM
  anahtarıyla şifrelenir; diske yalnızca şifreli metin yazılır.
- Anahtarlar kodda, repoda veya yedeklerde tutulmaz (`allowBackup=false`).
- İstekler HMAC-SHA256 ile imzalanır, `recvWindow` 5 sn'dir.
- API izinlerinde yalnızca Futures işlem yetkisi verilir; para çekme asla açılmaz.
- Mod değiştirildiğinde (testnet ↔ gerçek) kayıtlı anahtarlar otomatik silinir.
- Settings → **API anahtarlarını sil** hem şifreli kaydı hem Keystore anahtarını siler.

## 6. Güvenlik ve hata yönetimi

- Stop-Loss zorunlu. SL borsaya yerleşmezse pozisyon anında kapatılır.
- Aynı paritede ikinci pozisyon veya bekleyen emir varken yeni emir gönderilmez.
- Sonucu belirsiz emir tekrar gönderilmez; önce `clientOrderId` ile borsadan sorulur.
- Emirden sonra gerçek durum borsadan doğrulanır.
- İşlem başına risk, günlük zarar limiti, maksimum pozisyon, maksimum kaldıraç ve
  minimum risk/ödül kuralları hem önizlemede hem gönderim anında kontrol edilir.
- Günlük zarar limiti dolunca bot kendini kapatır.
- Acil durdurma yeni emirleri kilitler, bekleyen limit girişlerini iptal eder ve
  uygulama yeniden açılsa da aktif kalır.
- Ele alınan hatalar: internet kopması, WebSocket kopması (üstel bekleme ile
  yeniden bağlanma), zaman aşımı, geçersiz API anahtarı, yetersiz marjin,
  minimum emir büyüklüğü, geçersiz miktar/hassasiyet, geçersiz kaldıraç, borsa
  bakımı, tekrarlanan emir, istek limiti ve sunucu hataları.

## 7. Gerçek paraya geçmeden önce test listesi

Hepsi testnet'te, sırayla:

- [ ] Bağlantı testi: altı adım da ✓ (Settings → Bağlantıyı test et)
- [ ] Dashboard'da bakiye, canlı fiyat, mark fiyatı ve funding oranı doğru
- [ ] Markets'tan parite değiştirme, Chart'ta 1m–1d mum yükleme
- [ ] Uçak modu testi: canlı veri kopuyor ve kendiliğinden geri geliyor
- [ ] Market emri: onay ekranındaki değerler doğru, emir doluyor, SL ve TP
      Orders ekranında görünüyor
- [ ] Limit emir: emir defterde bekliyor, dolduğunda SL/TP otomatik ekleniyor
- [ ] Pozisyon kapatma: Positions → Market ile kapat, bekleyen SL/TP iptal oluyor
- [ ] Aynı paritede ikinci emir reddediliyor
- [ ] SL'siz emir reddediliyor
- [ ] Risk limitleri: kaldıraç sınırı, işlem riski sınırı, minimum risk/ödül
      reddi çalışıyor
- [ ] Günlük zarar limiti dolunca yeni emir açılmıyor
- [ ] Acil durdurma: butona basınca emirler kilitleniyor, uygulama yeniden
      açılınca hâlâ kilitli
- [ ] Bot: yalnızca sinyal modunda kayıt düşüyor; otomatik modda emir açılıyor
- [ ] Bot günlük zarar limitinde kendini kapatıyor
- [ ] History: kapanan işlemin çıkışı, PNL'i ve süresi doğru; istatistikler tutarlı
- [ ] Bildirimler: açılış, kapanış, sinyal ve bağlantı uyarısı geliyor
- [ ] En az birkaç gün kesintisiz testnet kullanımı, beklenmeyen davranış yok

## 8. Gerçek moda geçiş

1. Yukarıdaki listenin tamamı işaretli olmalı.
2. Settings → **GERÇEK İŞLEMLERİ ETKİNLEŞTİR** → uyarıyı oku → ikinci ekranda
   **GERÇEK** yaz ve onayla.
3. Kayıtlı demo anahtarı silinir; uygulamayı kapatıp yeniden aç.
4. Gerçek Binance hesabında yalnızca **Futures işlem** izinli yeni bir API
   anahtarı oluştur, para çekme iznini açma.
5. Anahtarı gir, Settings'te sunucunun `fapi.binance.com` olduğunu doğrula.
6. İlk günlerde risk ayarını düşük tut (işlem riski %0,25–0,5, kaldıraç 2–3x,
   günlük zarar limiti %2) ve küçük bakiyeyle başla.

Kaldıraçlı işlem yüksek risklidir; sermayenin tamamı kaybedilebilir. Bu
uygulama finansal tavsiye vermez.
