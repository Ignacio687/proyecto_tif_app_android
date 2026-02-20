# To-Do List

- [x] **Point 1: Implement the same patch logic as call contact to send a message skill, so when the contact is not found it should send the server similar contacts (if available), same as the call contact skill.**

- [ ] **Point 2: The assistant should reproduce a message when wake word is detected, so the senior user can know that the assistant is active and listening, should be predetermined phrases like: si?; te escucho; hola; dime; and so on.** This phrases should be like i said right after wake word is detected, and then sttart lisening, or maybe listen at the same time, to mask that delay before wake word is detected, and the listening starts. I think amybox does have a way to do this documented, fetch https://github.com/just-ai/aimybox-android-sdk/wiki/Core-Android-SDK and https://github.com/just-ai/aimybox-android-sdk/tree/master for context.

- [x] **Point 3: Apply same pop up permission request logic when is not granted and requested for calls, like its done with message and reminder skills.**

- [ ] **Point 4: Play YouTube video skill: user asks to play a video, Gemini finds the URL, app opens it**

  **Goal:** The user can ask the assistant to play a YouTube video (e.g. "poné el último video de MrBeast", "quiero ver recetas de milanesas"). The server uses Gemini with Google Search grounding to find the real video URL, returns it as a skill action, and the Android app opens it via an `ACTION_VIEW` intent in the YouTube app.

  **Behavior:**
  1. **Server side (backend):**
     - Add a `PlayYouTubeVideoSkill` to the skill schema (name, action: `play_youtube_video`, params: `video_url`, optionally `video_title`).
     - In the Gemini prompt/instructions, tell the model that when the user asks to play/watch a video, it should use Google Search grounding to find the actual YouTube URL and return it via the skill.
     - The second call (skill extraction) should output the skill with the resolved URL.
  2. **Android side (app):**
     - Implement `PlayYouTubeVideoSkill` that receives `video_url` from the server response.
     - Fire an `ACTION_VIEW` intent with the URL — Android will open it in the YouTube app if installed, or in the browser otherwise.
  3. **Considerations:**
     - Gemini must have **Google Search tool enabled** for this to work reliably (otherwise URLs may be hallucinated).
     - Fallback: if no URL is found, the server should reply saying it couldn't find the video instead of returning a skill with a bad URL.
     - The skill params schema should include `video_url` (required) and `video_title` (optional, for the assistant to say "I'm playing X").
