# Anglická lokalizácia (CZ / EN) a podpora prepínania jazyka

Cieľom tejto implementácie je pridať do aplikácie **Multipřehrávač** plnohodnotnú jazykovú mutáciu (Angličtina), s možnosťou dynamického prepínania jazyka za behu (bez nutnosti reštartovať aplikáciu). Zároveň sa musia adekvátne prispôsobiť funkcie vyhľadávania, Hlasového Agenta a sťahovania titulkov podľa zvoleného jazyka.

## User Review Required

> [!WARNING]
> **Presun textov do strings.xml**: Doteraz boli všetky české texty natvrdo (hardcoded) vpísané v súbore `MainActivity.kt`. V rámci tejto zmeny budú všetky tieto texty nahradené za volania `stringResource(R.string.nieco)`. Tým sa `MainActivity.kt` opticky trochu zmení, avšak toto je jediný správny a oficiálny Android spôsob, ako podporovať viacero jazykov.

> [!IMPORTANT]
> **Vlajka vs. Ikonka jazyka**: V OS Android nie sú natívne "vlajkové" emoji všade rovnako podporované. Ako tlačidlo na prepínanie jazykov v `TopAppBar` použijem univerzálnu Material Ikonu **Zemegule (Language)** (príp. písmená `CZ` / `EN`), aby sme zaistili rovnaký vzhľad na každom telefóne.

## Open Questions

- Je pre Hlasového agenta postačujúca pri angličtine základná sada príkazov: *Play [názov]*, *Next*, *Previous*, *Pause*, *Resume*, *Download*, *Quality [rozlíšenie]*, *Exit / Quit*?
- Angličtina sa stane predvoleným (`default`) jazykom (ak si aplikáciu stiahne používateľ v Nemecku, bude po anglicky). Čeština bude dedikovaná pre kód `cs`. Súhlasíte?

## Proposed Changes

### Komponent: Jazykové zdroje (Resources)

#### [NEW] `app/src/main/res/values/strings.xml` (Predvolená Angličtina)
- Vytvorenie základného súboru s anglickými textami (napr. `app_name`, `search_youtube`, `direct_url`, `buy_me_coffee`, atď.). Všetky kľúče z UI.

#### [NEW] `app/src/main/res/values-cs/strings.xml` (Čeština)
- Vytvorenie dedikovaného súboru pre češtinu so súčasnými prekladmi.

### Komponent: Globálny stav a pamäť

#### [NEW] `app/src/main/java/com/example/multiprehrava/LanguageManager.kt`
- Vytvorenie jednoduchej logiky (opierajúcej sa napr. o `DataStore` alebo `SharedPreferences`), ktorá uloží a načíta zvolený jazyk (`cs` alebo `en`).

### Komponent: Extrakcia Médií & Titulky

#### [MODIFY] [MediaHelper.kt](file:///C:/Users/patrikspravce/AndroidStudioProjects/Multiprehrava/app/src/main/java/com/example/multiprehrava/MediaHelper.kt)
- **Zmeny**: Nahradiť `lang=cs` a `tlang=cs` za dynamický parameter `targetLang` (ktorý bude buď `"cs"` alebo `"en"`). Pre angličtinu sa AI preklad bude generovať do angličtiny, ak originál nie je v EN. Taktiež sa zmení označenie titulkovej stopy (z "Čeština" na "English").

#### [MODIFY] [CzechSubtitleFetcher.kt](file:///C:/Users/patrikspravce/AndroidStudioProjects/Multiprehrava/app/src/main/java/com/example/multiprehrava/CzechSubtitleFetcher.kt)
- **Zmeny**: Premenovanie triedy alebo úprava parametrov tak, aby dokázala sťahovať VTT aj s parametrom `tlang=en`.

### Komponent: Hlasový Agent

#### [MODIFY] [VoiceCommandAgent.kt](file:///C:/Users/patrikspravce/AndroidStudioProjects/Multiprehrava/app/src/main/java/com/example/multiprehrava/VoiceCommandAgent.kt)
- **Zmeny**: Pridanie logiky pre rozpoznávanie anglických povelov, ak je nastavený anglický jazyk. Prispôsobenie konštánt.

### Komponent: Užívateľské Rozhranie (UI)

#### [MODIFY] [MainActivity.kt](file:///C:/Users/patrikspravce/AndroidStudioProjects/Multiprehrava/app/src/main/java/com/example/multiprehrava/MainActivity.kt)
- **Zmeny**:
  - Nahradenie všetkých statických Stringov (cca 150 výskytov) za `stringResource(id = R.string.xxx)`.
  - Do `TopAppBar` (alebo inam) pridáme ikonu Zemegule (Language) alebo prepínač `CZ/EN`.
  - Konfigurácia Locale za behu (pomocou Compose Contextu alebo `AppCompatDelegate`), aby sa UI okamžite prekreslilo pri zmene jazyka bez reštartu.
  - V `launchVoiceRecognizer` sa podstrčí parameter jazyka (buď `cs-CZ` alebo `en-US`) v závislosti od aktuálne zvolenej vlajky.

## Verification Plan

### Manual Verification
1. Skompilovať aplikáciu.
2. V `TopAppBar` kliknúť na ikonu Jazyka (Language / CZ-EN).
3. Celé používateľské rozhranie sa okamžite zmení do Angličtiny. Vstupné polia, dialógy, aj Nápoveda budú v Angličtine.
4. Otestovať stiahnutie videa s titulkami -> pri angličtine by sa mala zvoliť/zobraziť anglická stopa.
5. Otvoriť hlasového agenta (cez mikrofón) a vyskúšať príkaz "Play Queen" -> prehrávač by mal rozpoznať anglický príkaz a prehrať skladbu.
6. Aplikáciu zavrieť a znovu spustiť -> jazyk musí ostať nastavený na angličtinu.
