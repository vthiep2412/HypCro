# Future Roadmap & Feature Plans

## High Priority & Core Improvements
- [ ] Add humanization for W/S movement. Every 1–2 minutes, delay a reaction or direction switch by up to 1.2 s. Otherwise, choose a random delay from 0 to 800 ms.
- [ ] ADD FREE LOOK!!!
- [ ] Add a persistent on-screen HUD while the macro is running.
    - Stats to show: Elapsed time (uptime), average crops/second, current crop being farmed, current movement direction (W or S), and failsafe status.

---

## GUI & General Config
- [ ] Add a pill navigation bar to the top of the GUI menu to switch to the general config.
    - Improve farming logic to fix getting stuck or other issues (Anti-stuck toggle, if toggle on, it will try to switch direction to fix, if toggle off, it will immediately stop and notify you).
        - For example, it currently fails to detect and define which crop to farm if the player is looking at the air, even though the crop is right next to them.
        - When movement stops but the player is not out of the water, it should attempt to switch directions and monitor if the switch behaves normally. If it still isn't in the water as expected, flag it as a potential staff check.

---

## Failsafe & Protection
- [ ] Add a better failsafe for when an admin rotates the player back immediately after a macro check rotation.
    - maybe we have to delay the check a little bit.
- [ ] If player is floating while farming repeat sneak, and if can't fix it, we call staff as "Antistuck failed" and stop the macro.
- [ ] Fix by adding try catch when got tp out during macro. (not-enough-crash mod save me once, but not many times more, I have to act on my own!)

---

## Advanced Automation & Pathfinding
- [ ] Implement realistic human mouse movement simulation (GCD sensitivity math + smooth Bezier curves):
    - Micro-nudge / camera jitter to unstick Squeaky Mousemat interactions when swings fail to register.
    - Natural, curved aiming and entity tracking for pest killing instead of robotic angular snaps.
- [ ] Implement 3D flying pathfinding to allow us to kill pests!
    - Need to implement a plot handler to view and teleport between plots correctly!

---

## Completed (Done)
- Improve logging and printing to be more detailed and raise readiness.
    - Add color to text, spacing!
- Fix multiple areas where the dropdown menu does not work correctly in GUI.

---

> WE SHOULD LEARN FROM Skyhelper and Taunahi.