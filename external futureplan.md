# External Network Optimization & Ping Stabilization Plan

## Goal Overview
Optimize, reduce, and stabilize Minecraft server connection latency (ping), mitigate packet loss, and eliminate bufferbloat using Smart Queue Management (SQM) and transport-layer tuning. The primary objective is to maintain flat, predictable latency and eliminate micro-freezes, burst queue stalls, and packet drops during intense Hypixel SkyBlock gameplay and macro execution.

---

## 1. Network Queue Management (SQM & Bufferbloat Mitigation)

### The Problem:
Standard home router queues allow packets to pile up in huge unmanaged hardware buffers during sudden network traffic spikes (downloads, streaming, background syncing). This causes bufferbloat, creating sudden latency spikes (ping jumping from 40ms to 350ms+) and delayed Minecraft TCP ACK packets.

### Solution & Action Items:
- **Router-Level SQM Deployment**:
  - Configure **CAKE** (Common Applications Kept Enhanced) or **FQ-CoDel** (Fair Queueing Controlled Delay) on the home gateway / router (via OpenWrt, pfSense, or router QoS settings).
  - Apply active bandwidth rate limiting at 90% to 95% of tested maximum download and upload speeds to force traffic into the managed SQM queues.
  - Prioritize small interactive packets (gaming TCP ACKs, small Netty packets) ahead of high-throughput bulk streams.
- **Queue Burst Stabilization**:
  - Prevent packet drops under burst conditions by smoothing ingress traffic before it hits local network adapters.
  - Eliminate bufferbloat induced packet retransmissions.

---

## 2. Operating System & Transport Layer Tuning (TCP/IP & Windows Netsh)

### Solution & Action Items:
- **TCP_NODELAY & Nagle's Algorithm**:
  - Ensure TCP Nagle's algorithm is disabled for all Minecraft JVM processes to avoid 40ms to 200ms packet aggregation delays (`TCP_NODELAY` flag enabled in Netty connection bootstrap).
  - Verify Windows registry `TcpAckFrequency = 1` and `TCPNoDelay = 1` for the active network adapter interface.
- **Congestion Control & Window Scaling**:
  - Configure TCP congestion provider (e.g. BBR or CUBIC) via `netsh int tcp set global` for optimal packet recovery without aggressive throttling.
  - Tune receive side scaling (RSS) and TCP autotuning to prevent single-core network interrupt bottlenecks.
- **DSCP / QoS Packet Tagging**:
  - Add Windows Group Policy (gpedit) QoS rules to tag Minecraft JVM outgoing packets (`javaw.exe`) with Expedited Forwarding (DSCP 46 / CS5) for prioritized upstream dispatch.

---

## 3. Client-Side Netty & Fabric Pipeline Enhancements

### Solution & Action Items:
- **Netty EventLoop Thread Affinity**:
  - Review Fabric client Netty event loop threads to ensure network decoding doesn't share execution time with heavy client rendering or GC pauses.
  - Profile client tick stalls that could delay reading incoming packets from the TCP socket buffer.
- **Burst Packet Decoupling**:
  - Smooth incoming burst packets across sub-ticks to prevent momentary FPS drops and input desync when receiving large chunk or entity payloads.
- **Connection Health & Ping Telemetry**:
  - Implement a real-time rolling ping tracker with standard deviation (jitter) measurement.
  - Feed real-time jitter data into HypCro watchdog systems: if network jitter spikes beyond threshold, automatically extend watchdog snapback grace periods to prevent false staff-check alerts caused by server lag.

---

## 4. Verification & Testing Standards

- **Waveform Bufferbloat Test**:
  - Run the Waveform Bufferbloat test before and after SQM configuration to verify an **A+** grade (active gaming latency increase < 5ms under full load).
- **MTR / PingPlotter Trace**:
  - Log multi-hop packet loss and route jitter to Hypixel server nodes (`mc.hypixel.net`) to separate ISP backbone routing issues from local hardware queueing.
- **In-Game Benchmark**:
  - Run continuous farming and Dungeon runs while monitoring tablist ping variance and tick latency consistency.
