# Future Roadmap & Feature Plans

## High Priority & Core Improvements
- [ ] Add humanization for W/S movement. Every 1–2 minutes, delay a reaction or direction switch by up to 1.2 s. Otherwise, choose a random delay from 0 to 800 ms.
- [x] Add white gift waypoint/esp for specificaly jerry workshop (as QOL)
- [x] Add more ESP
    - ESP player (party player in green and other players in cyan with Gizmo bounding boxes and nametags)
    - crystal hollow chest (normal chest ESP with opened pos memory blacklist)
    - crystal hollow lockpick chest helper (always visible red sweet-spot aiming cube with full X-ray visibility)

- [x] change bat esp, star mob, miniboss esp mob detection to use nofrills one, instead of current custom

- add scan culling so it only scan esp for the player fov (for example scan star mob in only that specific range, NOT PLAYER)

- [ ] raycast path? hmm still thinking
---

## GUI & General Config
- [ ] Improve farming logic to fix getting stuck or other issues (Anti-stuck toggle, if toggle on, it will try to switch direction to fix, if toggle off, it will immediately stop and notify you).
    - For example, it currently fails to detect and define which crop to farm if the player is looking at the air, even though the crop is right next to them.
    - When movement stops but the player is not out of the water, it should attempt to switch directions and monitor if the switch behaves normally. If it still isn't in the water as expected, flag it as a potential staff check.

---

---

---

## Completed (Done)
- [x] Complete Party API port from SkyHanni with chat pattern matching and live party tracking
- [x] Player ESP (Green for Party members, Cyan for other players)
- [x] Normal Chest ESP with opened position blacklist memory
- [x] Crystal Hollows Lockpick Chest ESP (Magenta) & Red sweet-spot helper cube (always visible, X-ray through blocks)

---

> WE SHOULD LEARN FROM Skyhelper and Taunahi.