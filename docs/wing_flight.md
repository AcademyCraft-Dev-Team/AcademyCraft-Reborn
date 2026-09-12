# Wing flight control

Storm Wing, Black Wing, White Wing and Platinum Wing use `WingFlightMotion` for shared movement rules. The local player applies those rules inside `Player.travel`, using vanilla collision movement and position reporting. Steering, release and hover do not wait for a server velocity packet. The server keeps skill activation, CP occupation and input validation authoritative; it consumes the latest held input once per player tick and synchronizes the flight pose.

Input messages contain the full directional bitmask, heading and remaining momentum. Changes send immediately, with a 200 ms heartbeat. The server falls back to hover after 500 ms without fresh input. These intervals use monotonic nanoseconds, independently of world time. Received packet bursts never directly add velocity. Black Wing additionally retains its activation epoch and sequence validation; pausing does not reset that sequence.

Each wing registers **Advanced Skill Settings → Flight control → Momentum after releasing movement**. `remainingMomentum` defaults to `1.0`, including existing configuration files, and is adjustable in 1% increments. On releasing all movement keys, the selected proportion of the velocity is retained once; existing coasting drag then continues. At 0%, all three velocity components are zero during hover. Holding a direction is unaffected by this setting. Changing the setting persists through the existing client configuration system. Client and server must both update because the control packet format changed.

## Validation

Use JetBrains Runtime 25 and the checked-in wrapper:

```powershell
./gradlew.bat test build -DisDev=true
./gradlew.bat build -DisDev=false
./gradlew.bat runGameTestServer -DisDev=true -PacademyGameTests=academy:wing_network_motion
./gradlew.bat runClientDev -DisDev=true --init-script tools/vfxgraph-editor/scripts/wing-flight-client.init.gradle
```

The client smoke runner uses `run/wing-flight-client/saves/black_wing`; prepare an isolated test world at that location before running. It tests all four wings with actual input and travel, pauses the integrated server for 1500 ms, releases boost at 0% momentum, and checks zero velocity, unchanged position, idle pose and subsequent toggle acknowledgement. Screenshots are written to `run/wing-flight-client/screenshots/wing_flight_0.png` through `wing_flight_3.png`.

White Wing and Platinum Wing are currently hidden by the repository's existing unfinished-content policy. The smoke runner exposes them only in its test JVM; production visibility is unchanged.

Unit coverage includes default migration, configuration serialization, 0%/50%/100% momentum, diagonal and opposing inputs, monotonic expiration, packet bursts and codec round trips. The server game test also checks that processing the same player tick twice does not add another impulse.

## Remaining multiplayer limit

The vanilla “moved too quickly” and collision checks remain enabled. Severe dedicated-server stalls can still make queued position reports trigger vanilla correction. The integrated-host smoke verifies local flight responsiveness during a stall; it does not establish that dedicated multiplayer is free of those corrections. Skill shutdown and other authoritative actions also require the server to resume processing.
