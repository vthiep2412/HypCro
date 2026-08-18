# Advanced Staff Check & Failsafe Concepts

This document catalogs advanced staff-check mechanisms used by server administrators and anticheats, along with architectural defense designs for HypCro.

---

## 1. Block Barrier / Obstruction Placement
* **Admin Vector**: Invisible staff member places non-crop blocks (Cobweb, Obsidian, Dirt, Bedrock) directly inside your farming path or crosshair.
* **Failure Mode**: Bot blindly swings and attacks unbreakable blocks or gets trapped inside cobwebs without realizing the lane is obstructed.
* **Defense Strategy**:
  - Raycast targeted block before breaking.
  - If target block is not a valid crop (or is a known obstruction like Cobweb/Bedrock/Obsidian) for more than 3 ticks, immediately halt macro and sound failsafe alarm.

---

## 2. Invisible Entity / Armor Stand Crosshair Bait
* **Admin Vector**: Server/staff spawns an invisible entity or Armor Stand directly in your farming line or orbits it around your player.
* **Failure Mode**: Bot attacks or interacts with the entity, triggering server-side combat logging during farming.
* **Defense Strategy**:
  - Check `client.crosshairPickEntity` / targeted entities.
  - Automatically suppress attack inputs if aiming at an entity.
  - If an invisible or unexpected entity persists in front of the player for > 400ms, flag staff presence.

---

## 3. Vehicle & Mount Trap (Boat / Minecart / Horse)
* **Admin Vector**: Admin places a boat, minecart, or rideable animal directly under the player or in their movement path.
* **Failure Mode**: Player right-clicks or steps into the vehicle and becomes stuck in a seated passenger state.
* **Defense Strategy**:
  - Monitor `player.isPassenger` / `player.vehicle`.
  - The instant a passenger state is entered during macroing, immediately execute dismount packet (Shift/Sneak), abort macro, and ring alarm.

---

## 4. Forced Velocity / Knockback Impulse
* **Admin Vector**: Staff uses a punch, explosion, wind charge, or velocity command to push the player off track without sending a teleport packet.
* **Failure Mode**: Player is knocked off the farm lane and gets stuck against walls or in water.
* **Defense Strategy**:
  - Hook `ClientboundSetEntityMotionPacket` (velocity packet).
  - If the server sends an abnormal knockback vector (`deltaMovement.length() > threshold`), immediately stop macro and alarm.

---

## 5. Direct Admin Chat / Title / Screen Prompt Captcha
* **Admin Vector**: Staff sends a private message (e.g., `[ADMIN] Minikloon: Are you there?`), server alert, or giant red screen title (`"Type 123 in chat"`).
* **Failure Mode**: Normal human reacts; automated macro ignores and continues farming.
* **Defense Strategy**:
  - Intercept `ClientboundSetTitleTextPacket` and chat messages matching keywords: `[ADMIN]`, `[GM]`, `captcha`, `type`, `answer`, `reply`.
  - Pause script immediately and trigger urgent failsafe alarm.

---

## 6. Inventory Item Injection / Siphoning
* **Admin Vector**: Server injects a custom named book/item into the hotbar or opens a custom book GUI.
* **Failure Mode**: Bot interacts with or touches injected admin items.
* **Defense Strategy**:
  - Monitor changes in hotbar item contents/names.
  - If a signed book or foreign item appears in the active slot without player interaction, trigger staff alarm.
