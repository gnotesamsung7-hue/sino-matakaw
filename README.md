# Sino ang Matakaw?

A family social-deduction game for Android phones, played over Wi-Fi (no internet needed).
It's a sleepover at Lola's house. The Pinsan (cousins) clean the house. One or two secret Matakaw
sneak around at night: they put cousins to sleep with an unan, spill juice, and slip through the lagusan.
Hold a Family Meeting and vote, or finish every chore before the rooster crows.

## Layout
- `docs/index.html` — the whole game. Edit this one. (`docs/fonts/` holds the bundled font.)
- `app/` — the Android app: a WebView, a tiny Wi-Fi room server (`RelayServer.java`) and the game finder (`LanBridge.java`).
- `app/sinomatakaw.keystore` — fixed signing key so updates install over the old app. Keep it in the repo.
- `.github/workflows/build-apk.yml` — builds the APK on every push to `main`.

## Get the APK
After each push, the Actions tab builds it (about 4 minutes). Download `sino-ang-matakaw.apk` from
https://github.com/gnotesamsung7-hue/sino-matakaw/releases/tag/apk-latest

## Play
1. All phones on the same Wi-Fi, or connected to the host phone's hotspot.
2. One phone taps **Mag-host sa Wi-Fi**. The others tap **Sumali sa laro** and pick the game
   (or type the address shown on the host's screen).
3. The host adds bots if needed and taps **Simulan!** (4–10 players; 2 Matakaw from 7 players).
