- [x] **If the contact skill flow does not find an exact match, it should return only at most (if present) 5 similar contacts, not all the contacts.**

- [x] **Refactor the ContactSkill, should divide into two classes or services, one is the contact skill that handles the call invocation and response, the other one is the contact service that handles the contact retrieval and matching. This will make easier to implement the message skill**

- [ ] **The call contact skill should recognize when the contact is a number, so it should call the number instead of the contact name.** (to address later)