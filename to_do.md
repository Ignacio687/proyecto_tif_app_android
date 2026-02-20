# To-Do List

- [x] **Point 0: Make sure the code is respecting the server response schemas defined in http://127.0.0.1:8000/openapi.json**

- [x] **Point 1: The call contact skill should recognize when the contact is a number, so it should call the number instead of the contact name.**
    Call http://127.0.0.1:8000/openapi.json to see the skills response schema.

- [x] **Point 2: Send the current user time zone to the server, so the server can use it to format the time in the response.** look at http://127.0.0.1:8000/openapi.json to see the schema required.

- [ ] **Point 3: Implement the message skill, the user should be able to send a message to a contact. Use contact service to get the contact information.** look at http://127.0.0.1:8000/openapi.json to see the schema required. Check if the schema is correct to be used for sending messages using the android intent like the call contact skill. If not, tell me what to change, so i can modify the server side schema.

- [ ] **Point 4: Implement the Create a reminder skill, at a specific time and date or after a delay. The idea would be to use the android intent to create a reminder on the default calendar app.** look at http://127.0.0.1:8000/openapi.json to see the schema required. Check if the schema is correct to be used for creating reminders using the android intent like the call contact skill. The current schema is very simple, suggest parameters to add based on what android intent allows.

- [ ] **Point 5: The assistant should reproduce a message when wake word is detected, so the senior user can know that the assistant is active and listening, should be predetermined phrases like: si?; te escucho; hola; dime; and so on.** This phrases should be like i said right after wake word is detected, and then sttart lisening, or maybe listen at the same time, to mask that delay before wake word is detected, and the listening starts. I think amybox does have a way to do this documented, fetch https://github.com/just-ai/aimybox-android-sdk/wiki/Core-Android-SDK and https://github.com/just-ai/aimybox-android-sdk/tree/master for context.
