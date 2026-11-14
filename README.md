# 🎬 TubeXXL v2.2

![Android](https://img.shields.io/badge/Android-Media3%20ExoPlayer-00E676?style=for-the-badge&logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin)
![License](https://img.shields.io/badge/License-MIT-blue.style=for-the-badge)

**TubeXXL** je moderní, ultra-kompaktní a výkonná Android aplikace pro streamování a přehrávání videí i hudby z **YouTube**, **přímých URL odkazů (MP4, MP3, HLS)** a **lokálních souborů**.

Aplikace přináší prémiový **Dark Cyber Slate** design, 5-pásmový hardwarový **Ekvalizér**, **Bass Boost**, **Loudness Enhancer** (zesilovač tichých videí), autonomního **Hlasového Agenta v češtině**, **A.I. české titulky** a podporu přehrávání na pozadí.

---

## 🌟 Hlavní Funkce

### 🎛️ 1. Hardwarový Ekvalizér & AudioFX (Variant 2)
- **5-pásmový ekvalizér** (60 Hz, 230 Hz, 910 Hz, 3.6 kHz, 14 kHz) s rozsahem -15 dB až +15 dB.
- **Předvolby zvuku (Presets)**: *Flat, Rock, Pop, Jazz, Klasika, Heavy Metal, Hip Hop, Dance, Zvýraznění basů*.
- **Bass Boost**: Posuvník pro zdůraznění hlubokých basových frekvencí (0–100 %).
- **Loudness Enhancer (Zesilovač tichých videí)**: Zesílení tichých YouTube videí a audio stop až o **+20 dB** bez zkreslení.
- **Plná responzivita v režimu na šířku (Landscape)** s plynulým vertikálním posunem.

### 🎙️ 2. Autonomní Hlasový Agent v češtině
- Podpora českých hlasových příkazů (např. *"Přehrát Santana"*, *"Další"*, *"Předchozí"*, *"Pauza"*, *"Stáhnout"*, *"Kvalita 1080p"*, *"Ukončit"*).
- Agent příkaz vyhodnotí a vykoná akci bez nutnosti manuálního klikání.

### 🤖 3. A.I. České Titulky & WebVTT
- Automatická detekce a načítání oficiálních i automaticky generovaných **českých AI titulků**.
- Vlastní vysoko-kontrastní žlutý **NativeSubtitleOverlay** s elegantním zeleným orámováním.

### 🎨 4. Ultra-Kompaktní Cyber Slate UI
- **Výška zadávacích polí 36.dp** zarovnaná do jedné čitelné línie s tlačítky *Přehrát*, *Otevřít* a *Stáhnout*.
- **Výrazný tučný lemovací rámček (`2.dp GreenPrimary`)** a tmavé terminálové pozadí (`#0F141C`) pro okamžité odlišení vstupních polí.
- **Kompaktní záhlaví pod ovládací lištou**: Název aplikace a odkaz *Podpoř mě* pod ovládací lištou, bez rušivých údajů na obrazovce přehrávače (aktuální verze `v2.2`).

### 📺 5. Přehrávač & Streamovací Engine
- **Media3 ExoPlayer** + **MediaSessionService** (služba na popředí s ovládáním v notifikační liště a přehráváním při zhasnuté obrazovce).
- **Trojúrovňový záložní engine** (`yt-dlp` Android + `NewPipeExtractor` fallback) zaručující 100% stabilitu přehrávání.
- **Celoobrazovkový režim (Fullscreen)** s přepínáním otáčení, poměru stran (Roztáhnout, Oříznout, Přizpůsobit), výběrem kvality (1080p, 720p, 480p, 360p, Auto) a funkci **Odeslat na TV / Chromecast / Smart View**.

---

## 🛠️ Technologie & Architektura

- **Jazyk**: 100% idiomatické Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Media Engine**: AndroidX Media3 ExoPlayer & MediaSession
- **Audio DSP**: `android.media.audiofx.Equalizer`, `BassBoost`, `LoudnessEnhancer`
- **Extrakce médií**: `youtubedl-android` + `NewPipeExtractor` + `FFmpeg`
- **Asynchronní operace**: Kotlin Coroutines & StateFlow

---

## 🚀 Jak Skompilovat a Spustit

### Požadavky:
- Android Studio Jellyfish / Ladybug nebo novější
- JDK 17
- Android SDK 34 (Android 14+)

### Příkazy pro sestavení (Gradle):

```bash
# Skompilovat Debug APK
./gradlew app:assembleDebug

# Skompilovat Release APK
./gradlew app:assembleRelease
```

### Instalace přes ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.multiprehrava/.MainActivity
```

---

## 📧 Autor & Kontakt

- **Vývojář**: Patrik Mohler
- **Verze**: v2.1
- **E-mail**: [patrik.mohler@gmail.com](mailto:patrik.mohler@gmail.com)
- **GitHub Repozitář**: [https://github.com/PatrikKM/Multiprehravac](https://github.com/PatrikKM/Multiprehravac)

---

*Vytvořeno s 💚 a důrazem na špičkový audio-vizuální zážitek.*
