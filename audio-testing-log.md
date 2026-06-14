# Audio Testing Setup Log
Goal: Route UMC 1820 microphone signal into the Android emulator for testing the Sessions recording feature.

## Chain Overview
UMC 1820 (mic) → VB-Cable → Windows default recording device → QEMU emulator → Android AudioRecord

---

## Step 1 — Install VB-Cable
**Action:** Installed VB-Cable (VB-Audio Virtual Cable)
**Required reboot:** Yes
**Result:** VB-Cable appeared in Windows Sound settings

---

## Step 2 — Set CABLE Output as default recording device
**Action:** Sound settings → Recording tab → set "CABLE Output (VB-Audio Virtual Cable)" as default
**Result:** Done

---

## Step 3 — Route UMC 1820 into VB-Cable via "Listen to this device"
**Action:** Recording tab → UMC 1820 IN 01-02 → Properties → Listen tab → "Listen to this device" → selected "CABLE Input (VB-Audio Virtual Cable)"
**Note:** Multiple UMC options present (IN 01-02, IN 03-04, etc.) — chose IN 01-02 (first stereo pair, physical inputs 1 & 2)
**Note:** Multiple CABLE options present — chose "CABLE Input (VB-Audio Virtual Cable)" not "CABLE In 16ch"
**Result:** Done

---

## Test 1 — No audio input
**Logcat:** `maxAmp=807 PEAK_TARGET=22938 boost=28.4`
**Result:** 807 is near-zero — likely just emulator noise floor, VB-Cable not confirmed working yet

---

## Test 2 — Mouth 1 inch from mic
**Logcat:** `maxAmp=13180 PEAK_TARGET=22938 boost=1.74`
**Result:** Signal confirmed getting through VB-Cable. However recording sounded terrible — choppy and buzzy, no voice character, buzz increases when closer to mic.

---

## Step 4 — Change VB-Cable sample rate to 44100Hz
**Hypothesis:** VB-Cable defaulted to 48000Hz but Android AudioRecord requests 44100Hz — QEMU resampling the gap badly.
**Action:** Changed both CABLE Input and CABLE Output Advanced format from "24 bit, 48000 Hz" to "2 channel, 24 bit, 44100 Hz"
**Warning shown:** An application had the device in use — restarted emulator before testing
**Result:** No improvement — still choppy and buzzy

---

## Step 5 — Check UMC 1820 WDM sample rate
**Finding:** UMC 1820 IN 01-02 Advanced format = "2 channel, 16 bit, 44100 Hz" — already matched, not the cause.

---

## Diagnostic — Bypass VB-Cable entirely
**Action:** Set UMC 1820 IN 01-02 as the Windows default recording device directly (no VB-Cable in chain). Recorded in app.
**Result:** No difference — still buzzy. VB-Cable/"Listen to this device" is NOT the cause.

**Conclusion:** The Android emulator's audio capture (QEMU on Windows) is fundamentally poor. QEMU uses WinMM audio backend by default, which is old and produces bad quality capture.

---

## Step 5 — Try DirectSound emulator backend
**Action:** Launched emulator from command line with `-audio dsound` flag:
`"C:/Users/dalla/AppData/Local/Android/Sdk/emulator/emulator.exe" -avd Pixel_7_API_34 -audio dsound`
**Result:** No improvement — still buzzy.

---

## Final Conclusion — Emulator Audio Not Viable
The Android emulator (QEMU) on Windows has fundamentally broken audio capture regardless of backend (WinMM or DirectSound). This is a known platform limitation. The emulator is fine for all other testing but cannot be used for audio recording validation.

**Decision:** Audio recording must be tested on a real physical Android device.

---

## Next Steps

### Android
- Use **Samsung Galaxy Note 9** (girlfriend's, sitting in a drawer — needs to be located and charged)
- Note 9 runs Android 10 (API 29) — app minSdk is 24, so fully compatible
- VB-Cable setup can still be used to route UMC 1820 into the device if needed, but a real device mic should work fine on its own

### iOS
- Switching to Mac to work on the same features for iPhone
- User owns a new iPhone — physical device available immediately, no emulator issues expected
