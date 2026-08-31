# Future Roadmap & Feature Plans

## High Priority & Core Improvements
- [ ] Add humanization for W/S movement. Every 1–2 minutes, delay a reaction or direction switch by up to 1.2 s. Otherwise, choose a random delay from 0 to 800 ms.
- [x] Add a persistent on-screen HUD while the macro is running.
    - Stats to show: Elapsed time (uptime), average crops/second, current crop being farmed, current movement direction (W or S), and failsafe status.
    
- [x] Add watchdog bps check

- [x] Add smart mode and remake the type for Bouncy balls in misc
    - Change to Mode: [Aggresive | Calm | Smart]
    - In smart it will try to go overshoot in the best angle and position 
      to basicaly move the ball back to where the player start, to keep it in the center and bounce much longer
- [x] Revamp Settings area, add more horizontal tabs to clear misunderstanding
    - Movement: Mouse movement, Pathfinding & Flying (with integrated visualizer toggles)
    - Failsafe: WatchDog, Key and Mouse Lock
    - QOL: Free Look
- [x] Add freecam right after freelook
    - In settings control key minecraft (set default U)
    - Speed, and allow to use sprint key to increase speed by 1.2x
- [x] Add auto sprint as QOL (download odin and skyblocker and nofrills to learn, learn odin sprint)
- [ ] Add white gift waypoint/esp for specificaly jerry workshop (as QOL)
- [~] Add more ESP settings tab above Settings tab and in the second group which bound down
    - ESP player (party color), ESP bats and Important Entites in Dungeon (learn dungeon bats glow to add the esp to that bat)
    - Add color selection, typical right next to the On oFF
    - Add the pest back to that esp, and it will sync with the pest esp in pest



- [ ] raycast path? hmm still thinking
---

## GUI & General Config
- [x] Add a pill navigation bar to the top of the GUI menu (for only tab that was align top like currently "farming") to switch to the general config (Macro | General Config).
- [ ] Improve farming logic to fix getting stuck or other issues (Anti-stuck toggle, if toggle on, it will try to switch direction to fix, if toggle off, it will immediately stop and notify you).
    - For example, it currently fails to detect and define which crop to farm if the player is looking at the air, even though the crop is right next to them.
    - When movement stops but the player is not out of the water, it should attempt to switch directions and monitor if the switch behaves normally. If it still isn't in the water as expected, flag it as a potential staff check.

---

## Failsafe & Protection
- [x] Add a better failsafe for when an admin rotates the player back immediately during a macro check rotation, because sometimes some macro mod will react immediately, unlike human which usually not notice that. (print out message "Rotation check WatchDog was
triggered but the admin rotated you back. DO NOT REACT")
    - maybe we have to delay the check a little bit.
- [x] If player is floating while farming repeat sneak, and if can't fix it, we call staff as "Antistuck failed" and stop the macro.
- [x] Fix by adding try catch when got tp out during macro. (not-enough-crash mod save me once, but not many times more, I have to act on my own!)

---

## Advanced Automation & Pathfinding
- [x] Implement realistic human mouse movement simulation (GCD sensitivity math + smooth Bezier curves):
    - Micro-nudge / camera jitter to unstick Squeaky Mousemat interactions when swings fail to register.
    - Natural, curved aiming and entity tracking for pest killing instead of robotic angular snaps.
- [x] Implement 3D flying pathfinding to allow us to kill pests!
    - Need to implement a plot handler to view and teleport between plots correctly!

---

## Completed (Done)
- Improve logging and printing to be more detailed and raise readiness.
    - Add color to text, spacing!
- Fix multiple areas where the dropdown menu does not work correctly in GUI.
- ADD FREE LOOK!!!

---

> WE SHOULD LEARN FROM Skyhelper and Taunahi.