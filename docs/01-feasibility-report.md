# PROJECT STARK — Deliverable 1: Technical Feasibility Report

Status: **Draft, needs owner answers before architecture is final**
Date: 2026-10-11
Target: Samsung Galaxy S23 FE (SM-S711x)

Labels used below:

- **[VERIFIED]**: stated in official Android/AOSP/Samsung documentation, or consistently reported by several reputable sources.
- **[REPORTED]**: reported by reputable press or community testing, but not confirmed by official docs.
- **[UNVERIFIED]**: our assumption. It must be tested on the physical phone.

---

## 0. Environment inspected

| Item | Finding |
|---|---|
| `~/Shaikh OS` | Empty. No existing code. |
| Android SDK | Present at `~/Library/Android/sdk` (platform-tools, build-tools, emulator, system images) |
| `adb` | Installed in the SDK but **not on PATH** |
| Android Studio, Antigravity IDE | Installed |
| Connected device | **None**. The phone isn't connected over USB/ADB yet. |

**The phone's exact firmware is still unknown.** The S23 FE received One UI 8.0 (Android 16) from late September 2025 **[VERIFIED by press]**, and newer releases (One UI 8.5) may have arrived since. The first step of the experiment in §6 reads the actual build from the device.

---

## 1. The single most important finding

> **An ordinary app cannot give you "a second OS". It *can* ask Android to create a separate Android *profile* (a work profile), with you as its owner. That profile has its own encrypted storage, its own app installs and its own lock credential. This is the closest legitimate match to the vision.**

Everything else follows from three platform facts:

1. **Android's real isolation boundary is the user/profile.** Each profile has separate app data directories, separate app instances, separate accounts, and separate credential-encrypted (CE) storage keys. Those keys are protected by the hardware-backed lock-screen key hierarchy **[VERIFIED: AOSP file-based encryption model]**.
2. **A third-party app can create a work profile with the user's consent**, without enterprise enrollment, root or device-owner rights. Android calls this "DPC-first" provisioning (`ACTION_PROVISION_MANAGED_PROFILE`). The app becomes the profile owner **[VERIFIED: developer.android.com "Build a DPC" and source.android.com "Provision for device management"]**. Shelter and Island have shipped this for years **[VERIFIED]**.
3. **A work profile can have its own lock, separate from the phone PIN** ("Use one lock" switched off, the "work challenge"). With a separate challenge, the profile's CE storage is unlocked only by the profile credential **[VERIFIED in principle by AOSP design. UNVERIFIED on Samsung One UI 8: test required]**.

The STARK UI (arc-reactor home, launcher, gallery, files, notes) then runs **inside** that profile, as an app that acts as the profile's own front-end.

---

## 2. What is possible, what is not, what is unverified

### Possible (documented)

| Capability | Mechanism |
|---|---|
| A separate environment with its own app installs and data | Work profile (Android user/profile isolation) |
| Separate WhatsApp/Instagram *instances* | Install them inside the profile from Play Store (the profile gets its own Play Store) |
| Separate Google account | Add an account inside the profile |
| A STARK lock that is **not** the phone PIN | Separate work challenge, enforced by the OS (Gatekeeper/Keystore with hardware throttling), not by our app |
| Private photos, files and notes stored apart from the normal side | App storage inside the profile, plus our own encryption layer on top |
| Hiding profile content from the personal side | Cross-profile data sharing is restricted by default; our profile-owner app can restrict it further (screen capture, cross-profile copy/paste, sharing) |
| "Turn STARK off" | Pausing the profile (quiet mode) stops its apps and silences its notifications |
| A custom STARK look | Our app is the in-profile launcher/shell (Jetpack Compose). The Samsung home screen stays untouched. |
| Recovery if the STARK credential is forgotten | Profile owner's `resetPasswordWithToken` (needs a token you escrow), or delete and restore from your own encrypted backup |

### Not possible (by design, or forbidden by policy)

| Requirement | Why not | Closest legitimate alternative |
|---|---|---|
| A genuinely separate Android OS/VM on this phone | Android Virtualization Framework (pKVM/Microdroid) is privileged, not available to ordinary apps, and is not a phone-OS host anyway | Work profile |
| Custom ROM / GrapheneOS-style rebuild | GrapheneOS supports Pixels only. One UI 8 reportedly **removed bootloader unlocking** even on non-US Samsungs **[REPORTED, Jul 2025]**. Unlocking also permanently trips Knox (Secure Folder, Samsung Wallet/Pass break forever) **[VERIFIED by Samsung]** | Not recommended |
| Using or controlling Samsung Secure Folder from our app | No public API. On One UI 8, Secure Folder moved to Android's **Private profile** and does not support third-party launchers **[REPORTED by SamMobile/Android Authority]** | Use Secure Folder as-is, *or* our own work profile |
| Creating an Android 15+ "Private Space" from our app | Private profile creation is a system feature. On Samsung, that slot is used by Secure Folder **[REPORTED]** | Work profile |
| Moving WhatsApp chat history by copying files | The app's private DB is sandboxed and encrypted. Reading it would need root. | WhatsApp's own transfer/backup or linked-device features |
| "Cloning" Instagram/WhatsApp inside our app (VirtualApp-style engines) | No real isolation, breaks their ToS, flagged by Play Protect | Real install inside the profile |
| Hiding the *existence* of STARK from someone holding the unlocked phone | A work profile shows a "Work" tab/badge and appears in Settings; it can't be hidden like Private Space | Neutral naming; accept that existence is visible |
| Stopping someone with your phone PIN from *deleting* STARK | Anyone with the phone PIN can remove the work profile in Settings. They destroy the data but cannot read it. | Encrypted off-device backup |
| Protection against a rooted/compromised host OS | The OS kernel sees everything once STARK is unlocked | Out of scope (stated in threat model) |

### Unverified: needs the physical phone

| # | Question | Why it matters |
|---|---|---|
| U1 | Exact One UI/Android build and security patch | API behavior, Private profile, Secure Folder layout |
| U2 | Can a third-party DPC create a work profile **while Secure Folder exists** on One UI 8? | The whole primary architecture depends on this |
| U3 | Is "Use one lock" → *off* (separate work challenge) offered on this Samsung build? | It gives an independent STARK credential |
| U4 | When STARK is paused, are the profile's CE keys evicted (`isUserUnlocked(profile) == false`)? | It decides whether "locked STARK" means *encrypted at rest*, or only *not running* |
| U5 | Do Play Store, FCM push, WhatsApp and Instagram install and work inside the profile on this phone? | Third-party app requirement |
| U6 | Does the Keystore report `StrongBox` / `TRUSTED_ENVIRONMENT` for keys created in the profile? | Hardware-backing claims in the UI must be real |
| U7 | Does Samsung's Knox layer restrict a consumer DPC on this build (unknown OEM quirks)? | Shelter warns some vendor implementations are broken |

---

## 3. Architecture comparison (Deliverable 2, preliminary)

Scores are 1–5; higher is better (for effort and maintenance, higher means *less* burden).

| Option | Isolation | Usability | 3rd-party apps | Migration | Dev effort | Maintenance | Verdict |
|---|---|---|---|---|---|---|---|
| **B+D. Our own work profile + STARK shell inside it** | **4** OS profile boundary, own CE keys, own lock | 4 | **5** real installs | 4 | 3 | 3 | **Recommended** |
| C. Samsung Secure Folder (use as-is) | 4 (Knox, Private profile on One UI 8) | 4 | 5 | 3 | **5** (nothing to build) | **5** | **Fallback**. Zero custom UI. |
| A. Vault app only (encrypted app storage) | 2 Same profile; relies on our crypto plus app sandbox | 4 | **1** cannot host other apps | 3 files only | 4 | 4 | Partial: files only |
| E. Custom ROM / separate VM | 5 in theory | 2 | 4 | 3 | 1 | 1 | **Not feasible** on S23 FE / One UI 8 |
| F1. App-virtualization "clone" engines | 1 | 3 | 2 (ToS violations) | 1 | 2 | 1 | **Rejected** |
| F2. Second full Android *user* (multi-user) | 5 | 2 | 5 | 3 | 4 | 4 | Samsung phones generally hide multi-user **[UNVERIFIED on S23 FE]** |

---

## 4. Preliminary recommendation (Deliverable 3, pending your answers)

**Primary: "STARK Profile".** We build a Kotlin/Compose app that does four things:

1. **Provisions** a work profile via DPC-first provisioning, with your explicit consent.
2. **Enforces** a separate STARK credential (work challenge). Its policies block screenshots and cross-profile copy/paste/sharing, keep media out of the personal gallery, and redact notifications on the lock screen.
3. **Is the STARK shell** inside the profile: arc-reactor home, launcher for profile apps, secure gallery, files, notes, settings, and a security-state panel that shows only values read from real APIs.
4. **Runs MOVE**: you pick items through the system pickers on the personal side. They are copied into the profile, encrypted with **Google Tink** (streaming AEAD) under a Keystore-wrapped key, then verified by hash and a trial decrypt. Only after your confirmation does STARK ask the personal side to delete the originals.

**Why this wins:** the lock and the at-rest encryption are enforced by **Android itself, with hardware-throttled credential checks**. An app-level PIN can't match that. Brute-forcing a 6-digit app PIN against a file that has been copied off the phone is trivial unless the key also lives in hardware.

**Fallback (if U2 or U3 fails):** Samsung **Secure Folder**, hidden, with its own lock, for third-party apps, plus a **STARK vault app (Option A) inside Secure Folder** for files, notes and the UI experience. You lose the custom launcher for apps, but keep real isolation.

---

## 5. Security boundary at a glance (Deliverable 4 preview)

| Threat | Mitigated? | By what |
|---|---|---|
| 1. Casual user of your unlocked phone | **Yes** | STARK credential, pause and auto-lock, redacted notifications |
| 2. Someone who knows your phone PIN | **Yes for reading. No for deletion.** | Separate challenge → profile CE keys need the STARK credential (U3/U4). They *can* remove the profile. |
| 3. Other ordinary apps | **Yes** | Profile boundary plus the app sandbox |
| 4. Notifications, Recents, screenshots, sharing, media indexing | **Mostly** | `FLAG_SECURE`, profile policies, no MediaStore entries. Third-party apps inside STARK control their own notification content. |
| 5. Locked device lost or stolen | **Yes** | FBE plus hardware-backed keys. STARK keys evicted if U4 holds. |
| 6. Rooted or compromised host OS | **No** | Out of scope. Root sees decrypted data while STARK is unlocked. |
| 7. Forensic/physical access | **Partial** | Before-first-unlock and STARK-locked states resist well. Once unlocked, commercial forensic exploits are out of scope. |

**Recovery truth:** if the STARK credential *and* the reset token/backup key are both lost, the data is unrecoverable. That is the price of real encryption.

---

## 6. Proof of feasibility, the gate before any real code (Deliverable 9)

**Experiment STARK-0** (about 1 day; the throwaway app is roughly 300 lines):

1. Connect the phone (USB debugging) and record the firmware: `getprop ro.build.version.release`, `ro.build.display.id`, the security patch, and `pm list users`.
2. Note whether Secure Folder is set up. Test both states if you're willing.
3. Build a minimal DPC (`DeviceAdminReceiver` + `ACTION_PROVISION_MANAGED_PROFILE`) and provision a profile → **pass U2**.
4. In Settings, switch off "Use one lock" and set a different STARK PIN → **pass U3**.
5. Pause the profile, then check `UserManager.isUserUnlocked(profileHandle)` from the personal side and `dumpsys user` state → **record U4**.
6. In the profile, generate a Keystore key and read `KeyInfo.securityLevel` → **record U6**.
7. Install WhatsApp (as a linked device or with a second number) and Instagram from the in-profile Play Store. Check push delivery while unpaused, and silence while paused → **record U5**.
8. Remove the profile cleanly and confirm the phone is unchanged.

**Pass criteria:** U2 and U3 pass. U4 is either pass, or documented with the "not running but keys cached" limit accepted. U5 passes for at least one messaging app.

**Note:** removing the profile deletes everything in it. Nothing personal goes into the profile during STARK-0.

---

## 7. Owner decisions (2026-10-11)

| Decision | Choice | Consequence |
|---|---|---|
| Foundation | **Work profile** owned by the STARK app | Primary architecture confirmed, subject to STARK-0 |
| Secure Folder | **In use, with data to keep** | STARK-0 must run *alongside* Secure Folder; it is never deleted or modified. If the work profile can't coexist with it (U2 fails), we stop and re-plan. |
| Recovery | **Offline recovery code** (reset-password token) | The code is shown once and never stored in plain text on the device. Anyone holding it can reset the STARK PIN. That's documented as an accepted residual risk. |
| Lock behaviour | **Keep running** | Notifications keep arriving. Once STARK is unlocked after boot, its CE keys stay in memory until reboot. A STARK lock is therefore OS-enforced *access control*, not *encryption at rest*. Threats 5 and 7 are weaker than with pause-on-lock until the next reboot. STARK's own Tink-encrypted vault (gallery, files, notes) can still drop its key on lock, so vault data **is** re-encrypted at rest. The UI must show this distinction honestly. |

---

## 8. STARK-0 results: Phase A (read-only), 2026-10-11

Collected with `adb` and the probe app (`experiments/stark0-probe`). Nothing on the phone was changed apart from installing the probe app.

| Check | Result | Status |
|---|---|---|
| U1 Firmware | SM-S711B, **One UI 8.5** (`ro.build.version.oneui=80500`), Android 16 / SDK 36, build `S711BXXSIGZH9`, patch **2026-08-05** | **Resolved** |
| Boot integrity | `verifiedbootstate=green`, `flash.locked=1`, `warranty_bit=0` (Knox intact) | Good. Don't change it. |
| Secure Folder | User 150, type `android.os.usertype.profile.PRIVATE`, profile owner `com.samsung.knox.securefolder`, own PASSWORD credential (separate challenge) | Matches the One UI 8 reports |
| Profile slots | `profile.PRIVATE` max 1 per parent (used by Secure Folder). `profile.MANAGED` **enabled, max 1 per parent, unused**. User 0 "Can have profile: true". | Slot available |
| U2 `isProvisioningAllowed(ACTION_PROVISION_MANAGED_PROFILE)` | **true** | **Pre-check passed.** The real provisioning in Phase B is still required. |
| U6 Keystore | AES-256 key → `TRUSTED_ENVIRONMENT`. StrongBox key → **`STRONGBOX`** (`android.hardware.strongbox_keystore` present) | **Resolved (personal side).** Must be repeated inside the profile. |
| Observation | Secure Folder reports `RUNNING_UNLOCKED` with CE keys available (unlocked since boot, about 4.5 days ago) | Shows why "locked" ≠ "encrypted at rest" while a profile keeps running |

Still open: U3 (separate STARK lock), U4 (key eviction when paused), U5 (Play/WhatsApp/Instagram/push), U7 (Knox quirks). They need **Phase B**: actually creating an empty test work profile.

## 9. STARK-0 results: Phase B (empty test profile), 2026-10-11

Evidence comes from `dumpsys user`, `dumpsys lock_settings`, `vold` log lines and probe logs (tag `STARK0`), polled every 2 s by a watcher.

| Check | Evidence | Status |
|---|---|---|
| **U2** Work profile alongside Secure Folder | User 10 `android.os.usertype.profile.MANAGED` created at 16:20. The probe app is profile owner. Secure Folder (user 150) is untouched. | ✅ **PASS** |
| **U3** Independent STARK credential | `ACTION_SET_NEW_PASSWORD` from inside the profile → user 10 `CredentialType: PIN`, `SeparateChallenge: true`; `isUsingUnifiedPassword = false` | ✅ **PASS** |
| U3 lock prompt | After a phone lock and unlock with the phone PIN, opening the profile app required the STARK PIN (`User 10 PIN OK on [com.android.settings]`, 16:29:21) | ✅ PASS |
| **Phone PIN can't decrypt STARK** | After a reboot and phone-PIN unlock, user 10 stayed `RUNNING_LOCKED` (CE storage locked). It only became `RUNNING_UNLOCKED` after the STARK PIN (16:32:44). | ✅ **PASS**, the core security claim |
| **U4** Key eviction | `lockNow(FLAG_EVICT_CREDENTIAL_ENCRYPTION_KEY)` → user 10 `SHUTDOWN` (16:33:41) → `vold lockCeStorage(10)` (16:34:07) → restarted `RUNNING_LOCKED` (16:34:11) → `unlockCeStorage(10)` only after the STARK PIN (16:34:41) | ✅ **PASS**. STARK can truly re-encrypt its storage on demand. |
| U4 timing | About 27 s passed between the lock request and `lockCeStorage`, while the profile's apps were being stopped | ⚠️ Keys stay in memory for ~30 s after "Lockdown" |
| Pause (quiet mode) toggle | Not observed by the watcher (no `QUIET_MODE` flag) | ⏭ Not needed. Eviction via `lockNow` replaces it. |
| **U6** Keystore inside profile | TEE and **StrongBox** keys both generated inside user 10 | ✅ PASS |
| Debug restriction | The profile has `no_debugging_features`, so `adb` can't access user 10 | ℹ️ A good default. Dev builds need a debug-only override. |
| Device-admin policy refresh | Policies declared in `device_admin.xml` are loaded only at provisioning or boot, not on APK update | ℹ️ Must be right before provisioning. Tracked in the build plan. |
| **U5** Play Store / WhatsApp / Instagram / push | The owner signed a Google account into the profile's Play Store. `com.whatsapp` and `com.instagram.android` are installed in user 10. WhatsApp (linked as a companion device) posted conversation notifications as user 10. No Instagram notification has been recorded yet. Push during Lockdown is untested. | ✅ **PASS** for WhatsApp. Instagram push ⏳ |
| Missing system apps | Samsung Camera, Gallery and Messages are **not** installed in the profile by default | ℹ️ The STARK gallery fills the gap. In-app cameras (WhatsApp/Instagram) use their own capture UI. |

### Design consequence: two lock levels

Your decision was "keep running". The test shows STARK can offer both lock levels, using only supported APIs:

- **Lock** (default): the OS STARK PIN is required to open anything. Apps keep running, notifications keep arriving, and CE keys stay cached.
- **Lockdown** (one tap, or automatic, e.g. after N hours or on a schedule): keys are evicted from the kernel and the profile's data is encrypted at rest again. Notifications stop until the next STARK unlock.

---

## Sources

- Android Developers, Build a device policy controller: https://developer.android.com/work/dpc/build-dpc
- AOSP, Provision for device management: https://source.android.com/docs/devices/admin/provision
- Android, Private space: https://www.android.com/articles/how-to-set-up-private-space/
- androidx.security `EncryptedFile` (deprecated): https://developer.android.com/reference/androidx/security/crypto/EncryptedFile
- SamMobile, S23 FE One UI 8: https://www.sammobile.com/news/galaxy-s23-fe-gets-one-ui-8-stable-update/
- SamMobile, One UI 8 Secure Folder on Private profile: https://www.sammobile.com/news/one-ui-8-fixes-samsung-secure-folder-major-security-flaw/
- Android Authority, Secure Folder leak fix: https://www.androidauthority.com/one-ui-8-secure-folder-fix-leak-3554753/
- 9to5Google, One UI 8 bootloader unlock removal: https://9to5google.com/2025/07/26/samsung-galaxy-one-ui-8-bootloader-unlock/
- Shelter (work-profile app, caveats): https://f-droid.org/packages/net.typeblog.shelter/
