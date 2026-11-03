# Custom address and Home portrait

The user requested that the left figure on Home follow the selected form of address, with custom forms of address for both roles.

The existing family vector is retained. FamilyPortrait changes only the left hair styling for Ông/Bà, Chú/Cô, Anh/Chị. Bác, no address, and custom text retain a neutral figure; the app does not infer gender or age from arbitrary text. The illustration is decorative, with one accessible description for the complete image. It stays inside the previous 176dp illustration area and does not change Help or ring geometry.

Vietnamese conversation settings now offer “Tự nhập” for both user and assistant. Each input is single-line, at most 32 characters, 24sp, with a minimum 72dp container. The existing live preview and automatic saving apply. Blank custom values fall back to Bác/Tôi. Text is normalized to NFC and limited to letters, numbers, spaces, hyphens and apostrophes, without compiling user input as a regex.

The preset key and custom label are saved separately, so choosing a preset does not erase the typed label. Existing saved enum keys remain compatible. RespectfulLanguage supplies both displayed guidance and TTS wording through the existing engine. Dictated message content, protected recipient names and target regions are unchanged.

Acceptance checks: choose a preset, return Home, verify the left figure; choose custom text, check preview, leave and reopen Settings; review/Repeat an instruction; verify blank fallbacks and English UI; inspect large-font wrapping. Automated wording tests cover custom values, idempotence and fallback without provider calls. Actual custom-value selection still requires a user-driven phone check.
