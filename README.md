# WiFi Quick Connect — Android

প্রিমিয়াম, dark-themed Jetpack Compose অ্যাপ — Settings-এ না গিয়ে সরাসরি নেটওয়ার্ক বেছে পাসওয়ার্ড দিয়ে WiFi-তে কানেক্ট করার শর্টকাট।

## ডিজাইন

- গাঢ় গ্রেডিয়েন্ট ব্যাকগ্রাউন্ড (ইন্ডিগো → নেভি)
- Glassmorphism কার্ড, violet → cyan অ্যাকসেন্ট গ্রেডিয়েন্ট
- সিগন্যাল স্ট্রেংথ অনুযায়ী রঙিন আইকন (সবুজ/হলুদ/লাল)
- অ্যানিমেটেড কানেক্ট বাটন ও স্ট্যাটাস মেসেজ

## GitHub Actions দিয়ে APK বানানো (Android Studio ছাড়াই)

1. এই পুরো ফোল্ডার একটা নতুন GitHub রিপোজিটরিতে push করুন (`main` ব্রাঞ্চে)।
2. GitHub-এ রিপোর **Actions** ট্যাবে যান — push করার সাথে সাথেই "Build APK" workflow শুরু হয়ে যাবে।
3. বিল্ড শেষ হলে (২-৫ মিনিট) সেই রান-এর নিচে **Artifacts** সেকশনে `WiFiQuickConnect-debug-apk` পাবেন — ডাউনলোড করুন।
4. ZIP এক্সট্র্যাক্ট করলে `app-debug.apk` পাবেন — এটা ফোনে পাঠিয়ে ইনস্টল করুন।
   - ইনস্টলের সময় "Install from unknown sources" অনুমতি দিতে হতে পারে।

নিজে থেকে যেকোনো সময় রান করতে চাইলে Actions ট্যাব থেকে **Run workflow** বাটনেও চালানো যাবে।

## Android-এর সীমাবদ্ধতা (জরুরি)

Android 10 (Q) এর পর থেকে কোনো অ্যাপ নিজে থেকে নিরবে WiFi-তে কানেক্ট করতে পারে না — নিরাপত্তার জন্য Android একটা ছোট সিস্টেম কনফার্মেশন শিট দেখায় (এক ট্যাপে কনফার্ম করা যায়)। অ্যাপটি এটা হ্যান্ডেল করে:
- Android 10+: `WifiNetworkSpecifier` API ব্যবহার করে — সিস্টেম ডায়ালগ আসবে।
- Android 9 এবং তার আগে: সরাসরি `WifiConfiguration` API দিয়ে নিরবেই কানেক্ট করে।

ভুল পাসওয়ার্ড দিলে বা কানেকশন ব্যর্থ হলে অ্যাপ স্পষ্ট লাল মেসেজে জানিয়ে দেবে।

## স্থানীয়ভাবে বিল্ড করতে চাইলে (Android Studio দিয়ে)

1. [Android Studio](https://developer.android.com/studio) ইনস্টল করুন।
2. এই ফোল্ডারটি "Open" করুন।
3. Gradle সিঙ্ক শেষ হলে উপরে ▶️ Run চাপুন, অথবা `Build > Build APK(s)`।

## প্রজেক্ট স্ট্রাকচার

```
android-wifi-quick-connect/
├── .github/workflows/build-apk.yml   # অটো APK বিল্ড
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/wifi/quickconnect/
│       │   ├── MainActivity.kt        # প্রিমিয়াম Compose UI
│       │   ├── WifiConnector.kt       # স্ক্যান + কানেক্ট লজিক
│       │   └── ui/theme/              # Color, Type, Theme
│       └── res/                       # strings, icon, theme
├── build.gradle.kts
└── settings.gradle.kts
```

## অনুমতি (Permissions)

- `ACCESS_FINE_LOCATION` / `NEARBY_WIFI_DEVICES` — নেটওয়ার্ক স্ক্যান করার জন্য Android-এর বাধ্যতামূলক শর্ত
- `CHANGE_WIFI_STATE`, `ACCESS_WIFI_STATE` — কানেক্ট করার জন্য

অ্যাপ প্রথমবার চালু হলে এই পারমিশনগুলো চাইবে — অনুমতি দিলেই নেটওয়ার্ক লিস্ট দেখাবে।
