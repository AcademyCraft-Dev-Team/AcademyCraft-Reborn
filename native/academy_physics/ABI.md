# Academy physics ABI v1

This is a standalone binding foundation, not Minecraft world physics. It owns no
Minecraft objects, block inventories, terrain, players, rendering or persistence.
The implementation is written for this repository against the official
`rapier3d-f64 = 0.36.1` crate. It does not contain Sable code or its native library.

The pinned toolchain is stable Rust 1.99.0. Enabled Rapier features are only
`dim3`, `f64` and `std`, with default features disabled. In particular, this crate
does not opt into parallel stepping, nightly, optional SIMD configurations or
engine serialization. The upstream crate SHA-256 is
`f4588855e9c5451702147b5c6ddff4d93887f1e59c9504b6ac3571818f6ec3d2`.
`Cargo.lock` is generated from the registry by Cargo and must be committed after
its dependency resolution is verified; it must not be synthesized by hand.

## Calling convention and ownership

All exports use the platform C calling convention (`cdecl` on supported x64
targets). Types are exactly `int32_t`, `uint64_t`, and IEEE-754 `double`. There are
no native-owned output pointers, callbacks, strings, Rust objects or C structs in
the ABI. All functions return a status except `academy_physics_abi_version`, which
returns 1, and `academy_physics_engine_version`, which returns 3601 for Rapier
0.36.1. Array lengths count elements, not bytes, and must match exactly.

The Java 25 FFM caller owns aligned native memory, keeps it alive until the call
returns, and ensures each pointer addresses its declared readable/writable
length. Null and misaligned pointers are rejected. As with any C ABI, an arbitrary
non-null dangling pointer cannot be made safe by inspecting its numeric value;
the checked Java wrapper must never construct one. Inputs are copied before any
output write, so aliasing input and output buffers does not create Rust aliases.
Output buffers are unchanged on failure.

Calls are serialized by one internal mutex. A world and all bodies are identified
by process-local, nonzero, monotonically allocated `uint64_t` handles. No handle
is a pointer, and deleted handles are never reused in that library instance.
Exhaustion rejects allocation. Passing a body to another world is invalid.
Closing a world invalidates all its bodies. Repeated remove/close returns
`INVALID_HANDLE`; the Java owning wrapper may make its own close idempotent.

Rust unwinding panics are caught before returning through FFM. A panic involving
a world permanently marks that world failed, including a partially completed
step or mutation. A failed world cannot be inspected, stepped or mutated; it
can still be destroyed. Non-finite or out-of-domain simulation output similarly
fails the world. No rollback or automatic replay is attempted. Process aborts,
allocation aborts and invalid foreign memory access are not recoverable panics.
After any successful close all owned Rapier state is released. Explicit close is
required; this ABI does not promise a Java cleaner or automatic finalization.

## Status codes

| Value | Name | Meaning |
| --- | --- | --- |
| 0 | OK | Complete success; output, if any, is valid. |
| 1 | INVALID_ARGUMENT | Non-finite/out-of-range value, invalid enum/flag, zero quaternion, null or misaligned pointer. |
| 2 | INVALID_HANDLE | Unknown world/body, wrong owner, or already closed/removed. |
| 3 | LIMIT_EXCEEDED | World, body, shape or monotonic-handle capacity exhausted. |
| 4 | BUFFER_SIZE | Negative, incorrect or non-integral array layout length. |
| 5 | WORLD_FAILED | This world was already failed, or invalid solver output failed it now. |
| 6 | PANIC | This call caught a Rust unwind; the addressed world is now failed. |

Ordinary validation errors do not fail the world or change its contents. Validation
precedes insertion/mutation. Once an engine operation begins, a panic may leave
partial internal changes and therefore permanently fails the world.

## Exports

```c
int32_t academy_physics_abi_version(void);
int32_t academy_physics_engine_version(void);
int32_t academy_physics_world_create(const double *gravity, int32_t gravity_len,
                                    uint64_t *out_world, int32_t out_len);
int32_t academy_physics_world_destroy(uint64_t world);
int32_t academy_physics_world_step(uint64_t world, double total_dt, int32_t substeps);
int32_t academy_physics_body_create(uint64_t world, int32_t kind,
                                   const double *description, int32_t description_len,
                                   const double *cuboids, int32_t cuboids_len,
                                   uint64_t *out_body, int32_t out_len);
int32_t academy_physics_body_state(uint64_t world, uint64_t body,
                                  double *out_state, int32_t out_len);
int32_t academy_physics_body_set_pose(uint64_t world, uint64_t body,
                                     const double *pose, int32_t pose_len);
int32_t academy_physics_body_set_velocity(uint64_t world, uint64_t body,
                                         const double *velocity, int32_t velocity_len);
int32_t academy_physics_body_impulse_at_point(uint64_t world, uint64_t body,
                                             const double *impulse_point, int32_t len);
int32_t academy_physics_body_sleep(uint64_t world, uint64_t body);
int32_t academy_physics_body_wake(uint64_t world, uint64_t body);
int32_t academy_physics_body_remove(uint64_t world, uint64_t body);
```

`gravity` has 3 doubles. Handle output lengths must be 1. `kind` is 0 for a fixed
body and 1 for a dynamic body. There are no kinematic bodies in ABI v1.

`description` has exactly 21 doubles:

| Offset | Count | Field |
| --- | --- | --- |
| 0 | 3 | Body frame position relative to the Java-owned world origin. |
| 3 | 4 | Body orientation quaternion, x/y/z/w. |
| 7 | 3 | Initial linear velocity at the center of mass, in units/second. |
| 10 | 3 | Initial angular velocity in world axes, radians/second. |
| 13 | 1 | Uniform collider density; positive for dynamic bodies, nonnegative for fixed bodies. |
| 14 | 1 | Friction. |
| 15 | 1 | Restitution. |
| 16 | 1 | Linear damping. |
| 17 | 1 | Angular damping. |
| 18 | 1 | Gravity scale. |
| 19 | 1 | Explicit per-body CCD enabled: exactly 0 or 1; see the solver defaults below. |
| 20 | 1 | Automatic sleeping allowed: exactly 0 or 1. |

`cuboids` contains 1 to 64 consecutive 10-double records: local center x/y/z,
local quaternion x/y/z/w, then positive half-extents x/y/z. The entire shape is
one body with several attached cuboid colliders. Overlapping cuboids contribute
their masses independently; the caller must decompose an intended union without
overlap. Collider orientations do not fill holes between boxes.

`out_state` has exactly 26 doubles:

| Offset | Count | Field |
| --- | --- | --- |
| 0 | 3 | Body frame position (not center of mass), relative to the world origin. |
| 3 | 4 | Body orientation quaternion, x/y/z/w. |
| 7 | 3 | World-axis linear velocity at center of mass. |
| 10 | 3 | World-axis angular velocity. |
| 13 | 1 | Mass. Fixed bodies report 0 as an explicit infinite-mass sentinel. |
| 14 | 3 | Center of mass relative to the body frame. |
| 17 | 3 | Principal moments of inertia about the center of mass. Fixed bodies report zero. |
| 20 | 4 | Principal-inertia frame quaternion in the body frame, x/y/z/w. Fixed bodies report identity. |
| 24 | 1 | Sleeping: exactly 0 or 1. Fixed bodies report 0. |
| 25 | 1 | Body kind: 0 or 1. |

Every successful snapshot is finite. Initial mass properties are available before
the first step. The principal frame can have a different equivalent orientation
for repeated eigenvalues; tests must compare the resulting inertia, not require
a particular eigenvector sign/order.

`pose` has 7 doubles: position followed by x/y/z/w quaternion. Changing a pose is
an explicit teleport and wakes dynamic bodies; it is not a physical controller.
`velocity` has 6 doubles: linear followed by angular velocity, both in world axes;
changing velocity wakes the dynamic body. Fixed bodies reject velocity, impulse,
sleep and wake operations with `INVALID_ARGUMENT`.
`impulse_point` has 6 doubles: world-axis impulse followed by its application point
relative to the same world origin. The engine applies the impulse about its actual
center of mass and wakes the body for a nonzero impulse. An identical pose or
velocity, or a zero impulse, may leave a sleeping body asleep under Rapier's
no-op handling; call `body_wake` for an explicit wake request.
Explicit sleep sets linear/angular velocity to
zero; wake does not restore a previous velocity. Manual sleep is accepted even if
automatic sleeping was disabled.

`total_dt` is the total simulated duration; the engine advances exactly `substeps`
times using `total_dt / substeps`. Wall-clock time and game tick rates are not read.
The caller owns scheduling. Sleeping does not destroy a body or a world.

The binding keeps Rapier 0.36.1's default solver protections. A CCD flag of 0
disables the explicit per-body CCD request; it does not disable the engine's
automatic CCD for fast dynamic bodies against fixed colliders, nor speculative
contacts. Therefore a body with this flag off can still be stopped by a thin
fixed wall. The flag is not a report that all CCD mechanisms are disabled.

The default normalized maximum linear velocity is 400, and `length_unit` remains
1, so the solver can limit linear speed to 400 units/second after a solver
substep. The binding does not override this limit or the default
`max_ccd_substeps = 1`. That internal CCD setting is distinct from the number of
outer substeps supplied to `academy_physics_world_step`. A successful velocity
setter can report its requested value before stepping and a solver-limited value
after stepping. Contacts, gravity and damping also change velocities normally.

## Limits and validation

- At most 8 live worlds; 128 bodies and 2048 cuboids per world; 64 cuboids per body.
- `substeps` is 1 through 16; finite `total_dt` is in `(0, 0.1]` seconds, and the
  resulting substep is at least `0.000001` seconds.
- Relative body positions, local shape centers and impulse points: component
  absolute value at most 1,000,000. Half-extents are in `[0.000001, 1024]`.
- Gravity and linear/angular velocity inputs: each component absolute value at
  most 10,000. This is an ABI validation ceiling, not a guarantee that simulation
  will retain that speed; the solver's default linear speed cap is described above.
  Impulse components: absolute value at most 1,000,000,000,000.
- Density is in `[0, 1,000,000,000]`, strictly positive for dynamic bodies.
  Friction is in `[0, 10]`; restitution in `[0, 1]`; damping in `[0, 100]`;
  gravity scale in `[-100, 100]`.
- Fixed bodies require zero initial linear/angular velocities. All scalar values
  must be finite. Quaternions must be nonzero; normalization uses scaling to avoid
  overflow/underflow for finite inputs. Out-of-range inputs are rejected rather
  than silently clamped at the ABI boundary. This does not disable the solver's
  own protections or normal changes caused by simulation.
- Snapshots and every completed substep check all bodies for finite values and
  bounded positions/velocities; invalid output permanently fails the world.

These budgets are prototype work bounds, not advertised gameplay capacity.
Validation of native pointers and bounds does not sandbox arbitrary native callers.

## Platform artifact metadata

The NeoForge mod runs in a named module created after JVM startup. Native access
cannot be granted to that later module using `--enable-native-access=academy`.
The build compiles one fixed FFM bridge separately and packages its class bytes as
`academy/native_physics/FfmPhysicsAccess.bin`. An explicit loader defines only
that embedded class in an unnamed module, using the mod's public internal
`PhysicsNativeAccess` interface. No arbitrary class path or external bridge is
searched. All restricted FFM calls execute inside that bridge.

Opt-in callers must supply `--enable-native-access=ALL-UNNAMED`; the bridge checks
its actual module's native-access permission before creating a linker or loading
the library. This grants native access to unnamed modules in the JVM, not just
this bridge. Ordinary mod use does not open the bridge or load a native library.
The permission check is not replaced by accepting JDK compatibility warnings.
Explicit library close destroys owned worlds, closes the code arena, then removes
the verified private copy. A cleanup failure retains the necessary ownership and
allows close to be retried; it does not re-enable simulation.

The initial targets are `x86_64-pc-windows-msvc` and `x86_64-unknown-linux-gnu`,
reported as `windows-x86_64` and `linux-x86_64` to Java. Libraries are
`academy_physics.dll` and `libacademy_physics.so`. The Java loader verifies its
sidecar before loading the explicit local path:

```properties
format=academy-native-physics-v1
abi=1
engine=rapier3d-f64-0.36.1
platform=windows-x86_64
library=academy_physics.dll
sha256=<actual-library-sha256>
rustVersion=1.99.0
target=x86_64-pc-windows-msvc
sourceSha256=<actual-source-manifest-sha256>
```

`rustVersion`, `target` and `sourceSha256` are provenance fields; Java requires the
format, ABI, engine, platform, library filename and actual artifact hash. The
repository root orchestrates Cargo builds, the generated lockfile, dependency
license notices and JVM integration probes. No library is downloaded at game
runtime and this foundation does not enable Minecraft physics automatically.

## Deployment resolution

Callers do not need JVM properties. The loader resolves the verified artifact in
this order and never probes class paths or downloads:

1. Explicit pair `-Dacademy.nativePhysics.library` and
   `-Dacademy.nativePhysics.manifest` (both required together, absolute paths).
2. Environment pair `ACADEMY_NATIVE_PHYSICS_LIBRARY` and
   `ACADEMY_NATIVE_PHYSICS_MANIFEST`.
3. Home directory from `-Dacademy.nativePhysics.home` or
   `ACADEMY_NATIVE_PHYSICS_HOME`; it must contain the platform library file and
   `academy_physics.properties`.
4. Fixed game-directory layout `academy/native_physics/` beside the installed
   game, holding the same two files with their exact platform names.

A partially configured explicit pair, a broken explicit home, or a mismatched
artifact is an explicit failure; the loader never falls back to a later source
after one was selected. `--enable-native-access=ALL-UNNAMED` remains a launcher
requirement, and the bridge reports it as its own actionable failure when absent.
