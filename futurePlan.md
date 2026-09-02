# Future Roadmap & Feature Plans

## High Priority & Core Improvements
- [ ] Add humanization for W/S movement. Every 1–2 minutes, delay a reaction or direction switch by up to 1.2 s. Otherwise, choose a random delay from 0 to 800 ms.
- [ ] Add white gift waypoint/esp for specificaly jerry workshop (as QOL)
- [~] Add more ESP
    - ESP player (party player and other player is different) (add Player card, add "ESP party", ESP non-party)
    - crystal hollow chest (make sure only esp chest are not opened, and remember the chest that is opened by player already to not esp that chest anymore, use pos if you able to do that, remember to only do that if the chest is not lockpick chest)
    - crystal hollow lockpick chest helper esp

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

---

> WE SHOULD LEARN FROM Skyhelper and Taunahi.