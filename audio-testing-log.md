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

## Current Hypothesis
UMC 1820 WDM driver sample rate is still 48000Hz. Windows Audio Engine must resample 48000→44100 when routing via "Listen to this device" and is doing it badly.

**Next step:** Check UMC 1820 IN 01-02 → Properties → Advanced → sample rate setting
