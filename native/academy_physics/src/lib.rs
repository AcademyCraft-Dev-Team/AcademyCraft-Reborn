//! Bounded standalone Rapier binding. No Minecraft state or foreign-owned objects are retained.

mod contact_surface;

use rapier3d_f64::prelude::*;
use std::collections::HashMap;
use std::mem::{align_of, size_of};
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::{Mutex, OnceLock};

const OK: i32 = 0;
const INVALID_ARGUMENT: i32 = 1;
const INVALID_HANDLE: i32 = 2;
const LIMIT_EXCEEDED: i32 = 3;
const BUFFER_SIZE: i32 = 4;
const WORLD_FAILED: i32 = 5;
const PANIC: i32 = 6;

const MAX_WORLDS: usize = 8;
const MAX_BODIES: usize = 256;
const MAX_BODY_CUBOIDS: usize = 256;
const MAX_WORLD_CUBOIDS: usize = 32_768;
const MAX_JOINTS: usize = 512;
const MAX_COORDINATE: f64 = 1_000_000.0;
const MAX_SPEED: f64 = 10_000.0;
const MAX_IMPULSE: f64 = 1_000_000_000_000.0;
const DESCRIPTION_LEN: usize = 21;
const CUBOID_LEN: usize = 10;
const STATE_LEN: usize = 26;

type Result<T> = std::result::Result<T, i32>;

struct BodyEntry {
    handle: RigidBodyHandle,
    kind: i32,
    cuboids: usize,
}

struct World {
    physics: PhysicsWorld,
    surfaces: contact_surface::ContactSurfaces,
    bodies: HashMap<u64, BodyEntry>,
    cuboids: usize,
    failed: bool,
}

impl World {
    fn new(gravity: Vector) -> Self {
        let mut physics = PhysicsWorld::new();
        physics.gravity = gravity;
        Self { physics, surfaces: contact_surface::ContactSurfaces::default(),
               bodies: HashMap::new(), cuboids: 0, failed: false }
    }

    fn body_handle(&self, body: u64) -> Result<RigidBodyHandle> {
        self.bodies.get(&body).map(|entry| entry.handle).ok_or(INVALID_HANDLE)
    }

    fn dynamic_handle(&self, body: u64) -> Result<RigidBodyHandle> {
        let entry = self.bodies.get(&body).ok_or(INVALID_HANDLE)?;
        if entry.kind != 1 { return Err(INVALID_ARGUMENT); }
        Ok(entry.handle)
    }

    fn snapshot(&self, body: u64) -> Result<[f64; STATE_LEN]> {
        let entry = self.bodies.get(&body).ok_or(INVALID_HANDLE)?;
        let body = self.physics.bodies.get(entry.handle).ok_or(WORLD_FAILED)?;
        let mut output = [0.0; STATE_LEN];
        output[0..3].copy_from_slice(&body.translation().to_array());
        output[3..7].copy_from_slice(&body.rotation().to_array());
        output[7..10].copy_from_slice(&body.linvel().to_array());
        output[10..13].copy_from_slice(&body.angvel().to_array());
        output[14..17].copy_from_slice(&body.local_center_of_mass().to_array());
        output[23] = 1.0;
        if entry.kind == 1 {
            let mass = &body.mass_properties().local_mprops;
            output[13] = body.mass();
            output[17..20].copy_from_slice(&mass.principal_inertia().to_array());
            output[20..24].copy_from_slice(&mass.principal_inertia_local_frame.to_array());
            output[24] = if body.is_sleeping() { 1.0 } else { 0.0 };
            if output[13] <= 0.0 || output[17..20].iter().any(|value| *value <= 0.0) {
                return Err(WORLD_FAILED);
            }
        }
        output[25] = f64::from(entry.kind);
        if !output.iter().all(|value| value.is_finite())
            || output[0..3].iter().any(|value| value.abs() > MAX_COORDINATE)
            || output[7..13].iter().any(|value| value.abs() > MAX_SPEED)
            || !body.is_enabled()
        {
            return Err(WORLD_FAILED);
        }
        Ok(output)
    }

    fn check_output(&self) -> Result<()> {
        if !self.physics.quarantine().is_empty() { return Err(WORLD_FAILED); }
        for body in self.bodies.keys() { self.snapshot(*body)?; }
        Ok(())
    }

    fn step(&mut self, dt: f64, substeps: i32) -> Result<()> {
        if !dt.is_finite() || dt <= 0.0 || dt > 0.1 || !(1..=16).contains(&substeps)
            || dt / f64::from(substeps) < 0.000001
        {
            return Err(INVALID_ARGUMENT);
        }
        self.physics.integration_parameters.dt = dt / f64::from(substeps);
        for _ in 0..substeps {
            self.physics.step_with_events(&self.surfaces, &());
            self.check_output()?;
        }
        Ok(())
    }

    fn remove_body(&mut self, body: u64) -> Result<()> {
        let entry = self.bodies.get(&body).ok_or(INVALID_HANDLE)?;
        let count = entry.cuboids;
        self.surfaces.remove_body(entry.handle, &self.physics.bodies, &self.physics.colliders);
        self.physics.remove_body(entry.handle).ok_or(WORLD_FAILED)?;
        self.bodies.remove(&body);
        self.cuboids -= count;
        Ok(())
    }

    fn create_fixed_joint(&mut self, body1: u64, body2: u64, anchor1: Vector, anchor2: Vector) -> Result<()> {
        if body1 == body2 { return Err(INVALID_ARGUMENT); }
        if self.physics.impulse_joints.len() >= MAX_JOINTS { return Err(LIMIT_EXCEEDED); }
        let handle1 = self.body_handle(body1)?;
        let handle2 = self.body_handle(body2)?;
        let joint = FixedJointBuilder::new()
            .local_anchor1(anchor1)
            .local_anchor2(anchor2)
            .contacts_enabled(false)
            .build();
        self.physics.impulse_joints.insert(handle1, handle2, joint, true);
        self.surfaces.join_bodies(handle1, handle2, &self.physics.bodies, &self.physics.colliders);
        Ok(())
    }

    fn set_pose(&mut self, body: u64, pose: Pose) -> Result<()> {
        let handle = self.body_handle(body)?;
        self.physics.bodies[handle].set_position(pose, true);
        self.physics.bodies.propagate_modified_body_positions_to_colliders(&mut self.physics.colliders);
        self.surfaces.refresh_body_pose(handle, &self.physics.bodies, &self.physics.colliders);
        self.snapshot(body)?;
        Ok(())
    }
}

struct Registry {
    next_handle: u64,
    worlds: HashMap<u64, World>,
}

impl Registry {
    fn new() -> Self { Self { next_handle: 1, worlds: HashMap::new() } }

    fn allocate_handle(&mut self) -> Result<u64> {
        let current = self.next_handle;
        self.next_handle = current.checked_add(1).ok_or(LIMIT_EXCEEDED)?;
        Ok(current)
    }

    fn create_world(&mut self, gravity: Vector) -> Result<u64> {
        if self.worlds.len() >= MAX_WORLDS { return Err(LIMIT_EXCEEDED); }
        let id = self.allocate_handle()?;
        self.worlds.insert(id, World::new(gravity));
        Ok(id)
    }

    fn world(&mut self, id: u64) -> Result<&mut World> {
        let world = self.worlds.get_mut(&id).ok_or(INVALID_HANDLE)?;
        if world.failed { return Err(WORLD_FAILED); }
        Ok(world)
    }

    fn create_body(&mut self, world: u64, description: Description, cuboids: Vec<Cuboid>) -> Result<u64> {
        let owner = self.world(world)?;
        if owner.bodies.len() >= MAX_BODIES || owner.cuboids + cuboids.len() > MAX_WORLD_CUBOIDS {
            return Err(LIMIT_EXCEEDED);
        }
        let id = self.allocate_handle()?;
        let owner = self.world(world)?;
        let builder = match description.kind {
            0 => RigidBodyBuilder::fixed(),
            1 => RigidBodyBuilder::dynamic(),
            _ => return Err(INVALID_ARGUMENT),
        };
        let body = builder.pose(description.pose)
            .linvel(description.linear_velocity)
            .angvel(description.angular_velocity)
            .linear_damping(description.linear_damping)
            .angular_damping(description.angular_damping)
            .gravity_scale(description.gravity_scale)
            .ccd_enabled(description.ccd)
            .can_sleep(description.can_sleep)
            .build();
        let handle = owner.physics.insert_body(body);
        for cuboid in &cuboids {
            let collider = ColliderBuilder::cuboid(cuboid.half_extents.x, cuboid.half_extents.y, cuboid.half_extents.z)
                .position(cuboid.pose)
                .density(description.density)
                .friction(description.friction)
                .active_hooks(ActiveHooks::MODIFY_SOLVER_CONTACTS)
                .restitution(description.restitution);
            owner.physics.insert_collider(collider, Some(handle));
        }
        // Do not require a simulation step merely to obtain the initial mass/COM/inertia.
        owner.physics.bodies[handle].recompute_mass_properties_from_colliders(&owner.physics.colliders);
        owner.surfaces.insert_body(handle, &owner.physics.bodies, &owner.physics.colliders);
        owner.bodies.insert(id, BodyEntry { handle, kind: description.kind, cuboids: cuboids.len() });
        owner.cuboids += cuboids.len();
        owner.snapshot(id)?;
        Ok(id)
    }
}

struct Description {
    kind: i32,
    pose: Pose,
    linear_velocity: Vector,
    angular_velocity: Vector,
    density: f64,
    friction: f64,
    restitution: f64,
    linear_damping: f64,
    angular_damping: f64,
    gravity_scale: f64,
    ccd: bool,
    can_sleep: bool,
}

struct Cuboid { pose: Pose, half_extents: Vector }

fn bounded(value: f64, min: f64, max: f64) -> Result<f64> {
    if !value.is_finite() || value < min || value > max { Err(INVALID_ARGUMENT) } else { Ok(value) }
}

fn vector(values: &[f64], bound: f64) -> Result<Vector> {
    for value in values { bounded(*value, -bound, bound)?; }
    Ok(Vector::new(values[0], values[1], values[2]))
}

fn quaternion(values: &[f64]) -> Result<Rotation> {
    if values.iter().any(|value| !value.is_finite()) { return Err(INVALID_ARGUMENT); }
    let scale = values.iter().map(|value| value.abs()).fold(0.0, f64::max);
    if scale == 0.0 { return Err(INVALID_ARGUMENT); }
    let scaled = [values[0] / scale, values[1] / scale, values[2] / scale, values[3] / scale];
    let length = scaled.iter().map(|value| value * value).sum::<f64>().sqrt();
    Ok(Rotation::from_xyzw(scaled[0] / length, scaled[1] / length, scaled[2] / length, scaled[3] / length))
}

fn pose(values: &[f64]) -> Result<Pose> {
    Ok(Pose::from_parts(vector(&values[0..3], MAX_COORDINATE)?, quaternion(&values[3..7])?))
}

fn flag(value: f64) -> Result<bool> {
    match value { 0.0 => Ok(false), 1.0 => Ok(true), _ => Err(INVALID_ARGUMENT) }
}

fn description(kind: i32, values: &[f64]) -> Result<Description> {
    if values.len() != DESCRIPTION_LEN { return Err(BUFFER_SIZE); }
    if kind != 0 && kind != 1 { return Err(INVALID_ARGUMENT); }
    let linear_velocity = vector(&values[7..10], MAX_SPEED)?;
    let angular_velocity = vector(&values[10..13], MAX_SPEED)?;
    let density = bounded(values[13], 0.0, 1_000_000_000.0)?;
    if (kind == 1 && density == 0.0)
        || (kind == 0 && (linear_velocity != Vector::ZERO || angular_velocity != Vector::ZERO))
    {
        return Err(INVALID_ARGUMENT);
    }
    Ok(Description {
        kind,
        pose: pose(&values[0..7])?,
        linear_velocity,
        angular_velocity,
        density,
        friction: bounded(values[14], 0.0, 10.0)?,
        restitution: bounded(values[15], 0.0, 1.0)?,
        linear_damping: bounded(values[16], 0.0, 100.0)?,
        angular_damping: bounded(values[17], 0.0, 100.0)?,
        gravity_scale: bounded(values[18], -100.0, 100.0)?,
        ccd: flag(values[19])?,
        can_sleep: flag(values[20])?,
    })
}

fn cuboids(values: &[f64], density: f64, dynamic: bool) -> Result<Vec<Cuboid>> {
    if values.is_empty() || values.len() % CUBOID_LEN != 0 { return Err(BUFFER_SIZE); }
    if values.len() / CUBOID_LEN > MAX_BODY_CUBOIDS { return Err(LIMIT_EXCEEDED); }
    let mut result = Vec::with_capacity(values.len() / CUBOID_LEN);
    let mut total_mass = 0.0;
    for values in values.chunks_exact(CUBOID_LEN) {
        let half_extents = Vector::new(
            bounded(values[7], 0.000001, 1024.0)?,
            bounded(values[8], 0.000001, 1024.0)?,
            bounded(values[9], 0.000001, 1024.0)?,
        );
        total_mass += density * 8.0 * half_extents.x * half_extents.y * half_extents.z;
        result.push(Cuboid { pose: pose(&values[0..7])?, half_extents });
    }
    if !total_mass.is_finite() || (dynamic && total_mass <= 0.0) { return Err(INVALID_ARGUMENT); }
    Ok(result)
}

static REGISTRY: OnceLock<Mutex<Registry>> = OnceLock::new();

/// The registry guard remains outside catch_unwind, so a contained solver panic cannot
/// poison the mutex and thereby prevent unrelated worlds or failed-world destruction.
fn dispatch(world: Option<u64>, allow_failed: bool, action: impl FnOnce(&mut Registry) -> Result<()>) -> i32 {
    let mutex = REGISTRY.get_or_init(|| Mutex::new(Registry::new()));
    let mut registry = mutex.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
    dispatch_on(&mut registry, world, allow_failed, action)
}

fn dispatch_on(registry: &mut Registry, world: Option<u64>, allow_failed: bool,
               action: impl FnOnce(&mut Registry) -> Result<()>) -> i32 {
    if let Some(id) = world {
        let Some(owner) = registry.worlds.get(&id) else { return INVALID_HANDLE; };
        if owner.failed && !allow_failed { return WORLD_FAILED; }
    }
    let status = match catch_unwind(AssertUnwindSafe(|| action(registry))) {
        Ok(Ok(())) => OK,
        Ok(Err(status)) => status,
        Err(_) => PANIC,
    };
    if status == PANIC || status == WORLD_FAILED {
        if let Some(owner) = world.and_then(|id| registry.worlds.get_mut(&id)) { owner.failed = true; }
    }
    status
}

fn check_pointer<T>(pointer: *const T, length: i32, expected: usize) -> Result<()> {
    if length < 0 || length as usize != expected { return Err(BUFFER_SIZE); }
    if pointer.is_null() || pointer.addr() % align_of::<T>() != 0 { return Err(INVALID_ARGUMENT); }
    let bytes = expected.checked_mul(size_of::<T>()).ok_or(BUFFER_SIZE)?;
    if bytes > isize::MAX as usize || pointer.addr().checked_add(bytes).is_none() { return Err(INVALID_ARGUMENT); }
    Ok(())
}

// Inputs are copied, never borrowed alongside caller-owned output memory.
unsafe fn read(pointer: *const f64, length: i32, expected: usize) -> Result<Vec<f64>> {
    check_pointer(pointer, length, expected)?;
    // SAFETY: the ABI caller guarantees valid readable memory for the checked length.
    Ok(unsafe { std::slice::from_raw_parts(pointer, expected) }.to_vec())
}

#[unsafe(no_mangle)]
pub extern "C" fn academy_physics_abi_version() -> i32 { 1 }

#[unsafe(no_mangle)]
pub extern "C" fn academy_physics_engine_version() -> i32 { 3601 }

/// # Safety
/// All buffers must satisfy the lifetime, alignment and validity contract in ABI.md.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn academy_physics_world_create(gravity: *const f64, gravity_len: i32,
                                                       out_world: *mut u64, out_len: i32) -> i32 {
    dispatch(None, false, |registry| {
        check_pointer(out_world, out_len, 1)?;
        let gravity = vector(&unsafe { read(gravity, gravity_len, 3)? }, MAX_SPEED)?;
        let id = registry.create_world(gravity)?;
        // SAFETY: checked writable scalar output supplied by the caller.
        unsafe { out_world.write(id); }
        Ok(())
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn academy_physics_world_destroy(world: u64) -> i32 {
    dispatch(Some(world), true, |registry| {
        registry.worlds.remove(&world).ok_or(INVALID_HANDLE)?;
        Ok(())
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn academy_physics_world_step(world: u64, total_dt: f64, substeps: i32) -> i32 {
    dispatch(Some(world), false, |registry| registry.world(world)?.step(total_dt, substeps))
}

/// # Safety
/// All buffers must satisfy the lifetime, alignment and validity contract in ABI.md.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn academy_physics_body_create(world: u64, kind: i32,
        description_data: *const f64, description_len: i32, cuboid_data: *const f64, cuboid_len: i32,
        out_body: *mut u64, out_len: i32) -> i32 {
    dispatch(Some(world), false, |registry| {
        check_pointer(out_body, out_len, 1)?;
        if cuboid_len <= 0 || cuboid_len as usize % CUBOID_LEN != 0 { return Err(BUFFER_SIZE); }
        if cuboid_len as usize / CUBOID_LEN > MAX_BODY_CUBOIDS { return Err(LIMIT_EXCEEDED); }
        let description = description(kind, &unsafe { read(description_data, description_len, DESCRIPTION_LEN)? })?;
        let cuboids = cuboids(&unsafe { read(cuboid_data, cuboid_len, cuboid_len as usize)? },
                              description.density, kind == 1)?;
        let body = registry.create_body(world, description, cuboids)?;
        unsafe { out_body.write(body); }
        Ok(())
    })
}

/// # Safety
/// The output buffer must satisfy the lifetime, alignment and validity contract in ABI.md.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn academy_physics_body_state(world: u64, body: u64,
                                                     out_state: *mut f64, out_len: i32) -> i32 {
    dispatch(Some(world), false, |registry| {
        check_pointer(out_state, out_len, STATE_LEN)?;
        let state = registry.world(world)?.snapshot(body)?;
        unsafe { std::ptr::copy_nonoverlapping(state.as_ptr(), out_state, STATE_LEN); }
        Ok(())
    })
}

/// # Safety
/// The input buffer must satisfy the lifetime, alignment and validity contract in ABI.md.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn academy_physics_body_set_pose(world: u64, body: u64,
                                                        pose_data: *const f64, pose_len: i32) -> i32 {
    dispatch(Some(world), false, |registry| {
        let pose = pose(&unsafe { read(pose_data, pose_len, 7)? })?;
        let world = registry.world(world)?;
        world.set_pose(body, pose)
    })
}

/// # Safety
/// The input buffer must satisfy the lifetime, alignment and validity contract in ABI.md.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn academy_physics_body_set_velocity(world: u64, body: u64,
        velocity_data: *const f64, velocity_len: i32) -> i32 {
    dispatch(Some(world), false, |registry| {
        let values = unsafe { read(velocity_data, velocity_len, 6)? };
        let linear = vector(&values[0..3], MAX_SPEED)?;
        let angular = vector(&values[3..6], MAX_SPEED)?;
        let world = registry.world(world)?;
        let handle = world.dynamic_handle(body)?;
        let body = &mut world.physics.bodies[handle];
        body.set_linvel(linear, true);
        body.set_angvel(angular, true);
        Ok(())
    })
}

/// # Safety
/// The input buffer must satisfy the lifetime, alignment and validity contract in ABI.md.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn academy_physics_body_impulse_at_point(world: u64, body: u64,
        impulse_point: *const f64, len: i32) -> i32 {
    dispatch(Some(world), false, |registry| {
        let values = unsafe { read(impulse_point, len, 6)? };
        let impulse = vector(&values[0..3], MAX_IMPULSE)?;
        let point = vector(&values[3..6], MAX_COORDINATE)?;
        let world = registry.world(world)?;
        let handle = world.dynamic_handle(body)?;
        world.physics.bodies[handle].apply_impulse_at_point(impulse, point, true);
        world.snapshot(body)?;
        Ok(())
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn academy_physics_body_sleep(world: u64, body: u64) -> i32 {
    dispatch(Some(world), false, |registry| {
        let world = registry.world(world)?;
        let handle = world.dynamic_handle(body)?;
        world.physics.bodies[handle].sleep();
        Ok(())
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn academy_physics_body_wake(world: u64, body: u64) -> i32 {
    dispatch(Some(world), false, |registry| {
        let world = registry.world(world)?;
        let handle = world.dynamic_handle(body)?;
        world.physics.wake_up(handle, true);
        Ok(())
    })
}

#[unsafe(no_mangle)]
pub extern "C" fn academy_physics_body_remove(world: u64, body: u64) -> i32 {
    dispatch(Some(world), false, |registry| registry.world(world)?.remove_body(body))
}

/// # Safety
/// Anchor buffers must satisfy the lifetime, alignment and validity contract in ABI.md.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn academy_physics_fixed_joint_create(world: u64, body1: u64, body2: u64,
        anchor_data: *const f64, anchor_len: i32) -> i32 {
    dispatch(Some(world), false, |registry| {
        let values = unsafe { read(anchor_data, anchor_len, 6)? };
        let anchor1 = vector(&values[0..3], MAX_COORDINATE)?;
        let anchor2 = vector(&values[3..6], MAX_COORDINATE)?;
        registry.world(world)?.create_fixed_joint(body1, body2, anchor1, anchor2)
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn body_description(position: [f64; 3]) -> [f64; DESCRIPTION_LEN] {
        let mut values = [0.0; DESCRIPTION_LEN];
        values[0..3].copy_from_slice(&position);
        values[6] = 1.0;
        values[13] = 1.0;
        values[14] = 0.7;
        values[18] = 1.0;
        values[19] = 1.0;
        values[20] = 1.0;
        values
    }

    fn box_data(center: [f64; 3], half: [f64; 3]) -> [f64; CUBOID_LEN] {
        [center[0], center[1], center[2], 0.0, 0.0, 0.0, 1.0, half[0], half[1], half[2]]
    }

    fn insert(registry: &mut Registry, world: u64, kind: i32, at: [f64; 3], boxes: &[f64]) -> u64 {
        let desc = description(kind, &body_description(at)).unwrap();
        let cuboids = cuboids(boxes, desc.density, kind == 1).unwrap();
        registry.create_body(world, desc, cuboids).unwrap()
    }

    fn near(actual: f64, expected: f64, tolerance: f64) {
        assert!((actual - expected).abs() <= tolerance, "actual={actual}, expected={expected}, tolerance={tolerance}");
    }

    #[test]
    fn single_cuboid_slides_out_of_a_zero_clearance_socket() {
        for (inset, ccd) in [(0.0, true), (0.005, true), (0.0, false)] {
            let mut registry = Registry::new();
            let world = registry.create_world(Vector::new(0.0, -9.81, 0.0)).unwrap();
            insert(&mut registry, world, 0, [0.0, -0.5, 0.0], &box_data([0.0; 3], [3.0, 0.5, 3.0]));
            for (at, half) in [
                ([-1.0, 0.5, 0.0], [0.5, 0.5, 2.0]),
                ([1.0, 0.5, 0.0], [0.5, 0.5, 2.0]),
                ([0.0, 0.5, -1.0], [0.5, 0.5, 0.5]),
                ([0.0, 0.5, 1.0], [0.5, 0.5, 0.5]),
            ] {
                insert(&mut registry, world, 0, at, &box_data([0.0; 3], half));
            }
            let mut desc = body_description([0.0, 0.5, 0.0]);
            desc[19] = if ccd { 1.0 } else { 0.0 };
            let desc = description(1, &desc).unwrap();
            let boxes = cuboids(&box_data([0.0; 3], [0.5 - inset; 3]), desc.density, true).unwrap();
            let body = registry.create_body(world, desc, boxes).unwrap();
            for _ in 0..200 {
                let owner = registry.world(world).unwrap();
                let state = owner.snapshot(body).unwrap();
                let force = ((3.5 + 9.81 / 53.333333 - state[1]) * 53.333333 - state[8] * 16.0)
                    .clamp(-2000.0, 2000.0);
                let handle = owner.body_handle(body).unwrap();
                owner.physics.bodies[handle].apply_impulse(Vector::new(0.0, force * 0.05, 0.0), true);
                owner.step(0.05, 3).unwrap();
            }
            let result = registry.world(world).unwrap().snapshot(body).unwrap();
            near(result[1], 3.5, 0.1);
        }
    }

    #[test]
    fn voxel_union_slides_out_of_its_extraction_socket() {
        let mut selected = Vec::new();
        for x in -5_i32..=5 {
            for y in -2_i32..=0 {
                for z in -5_i32..=5 {
                    if (x * x + z * z) as f64 / 25.0 + (y * y) as f64 / 4.0 <= 1.0 {
                        selected.push((x, y, z));
                    }
                }
            }
        }
        selected.sort_by_key(|&(x, y, z)| (x * x + y * y + z * z, x, y, z));
        selected.truncate(128);
        for (label, merge, split_terrain, split_body) in [("merged", true, false, false), ("unmerged", false, true, false), ("joint", false, true, true)] {
            let mut registry = Registry::new();
            let world = registry.create_world(Vector::new(0.0, -9.81, 0.0)).unwrap();
            let mut moving = Vec::new();
            let mut terrain = Vec::new();
            if merge {
                let mut remaining: std::collections::BTreeSet<_> = selected.iter().copied().collect();
                while let Some(&(x, y, z)) = remaining.first() {
                    let mut max_x = x;
                    while remaining.contains(&(max_x + 1, y, z)) { max_x += 1; }
                    let mut max_z = z;
                    while (x..=max_x).all(|xx| remaining.contains(&(xx, y, max_z + 1))) { max_z += 1; }
                    let mut max_y = y;
                    while (x..=max_x).all(|xx| (z..=max_z).all(|zz| remaining.contains(&(xx, max_y + 1, zz)))) { max_y += 1; }
                    for xx in x..=max_x { for yy in y..=max_y { for zz in z..=max_z { remaining.remove(&(xx, yy, zz)); }}}
                    moving.extend_from_slice(&box_data(
                        [(x + max_x + 1) as f64 / 2.0, (y + max_y + 1) as f64 / 2.0, (z + max_z + 1) as f64 / 2.0],
                        [(max_x - x + 1) as f64 / 2.0, (max_y - y + 1) as f64 / 2.0, (max_z - z + 1) as f64 / 2.0]));
                }
            } else {
                for &(x, y, z) in &selected {
                    moving.extend_from_slice(&box_data([x as f64 + 0.5, y as f64 + 0.5, z as f64 + 0.5], [0.5; 3]));
                }
            }
            for y in -2_i32..=0 {
                for z in -6_i32..=6 {
                    let mut x = -6;
                    while x <= 6 {
                        if selected.contains(&(x, y, z)) { x += 1; continue; }
                        let start = x;
                        while x <= 6 && !selected.contains(&(x, y, z)) { x += 1; }
                        terrain.extend_from_slice(&box_data([(start + x) as f64 / 2.0, y as f64 + 0.5, z as f64 + 0.5],
                                                           [(x - start) as f64 / 2.0, 0.5, 0.5]));
                    }
                }
            }
            insert(&mut registry, world, 0, [0.5, -2.5, 0.5], &box_data([0.0; 3], [8.0, 0.5, 8.0]));
            for boxes in terrain.chunks(if split_terrain { 7 * CUBOID_LEN } else { 256 * CUBOID_LEN }) {
                insert(&mut registry, world, 0, [0.0; 3], boxes);
            }
            let mut moving_bodies = Vec::new();
            for boxes in moving.chunks(if split_body { 64 * CUBOID_LEN } else { 256 * CUBOID_LEN }) {
                let mut description_data = body_description([0.0; 3]);
                description_data[19] = 1.0;
                let description = description(1, &description_data).unwrap();
                let boxes = cuboids(boxes, description.density, true).unwrap();
                moving_bodies.push(registry.create_body(world, description, boxes).unwrap());
            }
            let body = moving_bodies[0];
            for &other in moving_bodies.iter().skip(1) {
                registry.world(world).unwrap().create_fixed_joint(body, other, Vector::ZERO, Vector::ZERO).unwrap();
            }
            for _ in 0..60 {
                let owner = registry.world(world).unwrap();
                for &moving_body in &moving_bodies {
                    let state = owner.snapshot(moving_body).unwrap();
                    let mass = state[13];
                    let max_force = 2000.0 * mass / 128.0;
                    let force = ((3.0 + 9.81 / 53.333333 - state[1]) * 53.333333 * mass - state[8] * 16.0 * mass)
                        .clamp(-max_force, max_force);
                    let handle = owner.body_handle(moving_body).unwrap();
                    owner.physics.bodies[handle].apply_impulse(Vector::new(0.0, force * 0.05, 0.0), true);
                }
                owner.step(0.05, 3).unwrap();
            }
            let result = registry.world(world).unwrap().snapshot(body).unwrap();
            near(result[1], 3.0, 0.12);
            near(result[0], 0.0, 0.02);
            near(result[2], 0.0, 0.02);
            eprintln!("SOCKET_REGRESSION {label} boxes={} y={}", moving.len() / CUBOID_LEN, result[1]);
        }
    }

    #[test]
    fn exposed_wall_and_ceiling_still_stop_fast_rotated_structures() {
        for (rotation, direction) in [(Rotation::IDENTITY, Vector::X),
                (Rotation::from_rotation_z(0.35), Vector::X), (Rotation::IDENTITY, Vector::Y)] {
            let mut registry = Registry::new();
            let world = registry.create_world(Vector::ZERO).unwrap();
            let mut terrain = Vec::new();
            for u in -3..=3 {
                for v in -3..=3 {
                    let center = if direction == Vector::X { [5.0, f64::from(u), f64::from(v)] }
                                 else { [f64::from(u), 5.0, f64::from(v)] };
                    terrain.extend_from_slice(&box_data(center, [0.5; 3]));
                }
            }
            for boxes in terrain.chunks(7 * CUBOID_LEN) {
                let mut data = body_description([0.0; 3]);
                data[3..7].copy_from_slice(&rotation.to_array());
                let desc = description(0, &data).unwrap();
                let boxes = cuboids(boxes, desc.density, false).unwrap();
                registry.create_body(world, desc, boxes).unwrap();
            }
            let mut data = body_description([0.0; 3]);
            data[3..7].copy_from_slice(&rotation.to_array());
            data[7..10].copy_from_slice(&(rotation * direction * 50.0).to_array());
            let desc = description(1, &data).unwrap();
            let boxes = cuboids(&box_data([0.0; 3], [0.5; 3]), desc.density, true).unwrap();
            let moving = registry.create_body(world, desc, boxes).unwrap();
            let owner = registry.world(world).unwrap();
            for _ in 0..20 { owner.step(0.05, 3).unwrap(); }
            let state = owner.snapshot(moving).unwrap();
            let position = Vector::new(state[0], state[1], state[2]);
            let travel = position.dot(rotation * direction);
            assert!(travel > 2.0 && travel < 4.1, "wall/ceiling crossing: travel={travel}");
        }
    }

    #[test]
    fn gravity_contacts_and_sleep_are_real_solver_results() {
        let mut registry = Registry::new();
        let world = registry.create_world(Vector::new(0.0, -9.81, 0.0)).unwrap();
        let ground = insert(&mut registry, world, 0, [0.0, -0.5, 0.0], &box_data([0.0; 3], [5.0, 0.5, 5.0]));
        let falling = insert(&mut registry, world, 1, [0.0, 5.0, 0.0], &box_data([0.0; 3], [0.5; 3]));
        registry.world(world).unwrap().step(0.1, 6).unwrap();
        let falling_state = registry.world(world).unwrap().snapshot(falling).unwrap();
        assert!(falling_state[1] < 5.0 && falling_state[8] < 0.0);
        for _ in 0..100 { registry.world(world).unwrap().step(0.1, 6).unwrap(); }
        let landed = registry.world(world).unwrap().snapshot(falling).unwrap();
        near(landed[1], 0.5, 0.04);
        near(landed[8], 0.0, 0.02);
        assert_eq!(landed[24], 1.0);
        let fixed = registry.world(world).unwrap().snapshot(ground).unwrap();
        assert!(fixed.iter().all(|value| value.is_finite()));
        assert_eq!(fixed[13], 0.0);
        assert_eq!(&fixed[17..20], &[0.0; 3]);
    }

    #[test]
    fn compound_mass_center_and_offset_impulse_use_the_actual_local_frame() {
        let mut registry = Registry::new();
        let world = registry.create_world(Vector::ZERO).unwrap();
        let mut boxes = box_data([-1.0, 0.0, 0.0], [0.5; 3]).to_vec();
        boxes.extend_from_slice(&box_data([2.0, 0.0, 0.0], [1.0, 0.5, 0.5]));
        let id = insert(&mut registry, world, 1, [10.0, 0.0, 0.0], &boxes);
        let owner = registry.world(world).unwrap();
        let before = owner.snapshot(id).unwrap();
        near(before[13], 3.0, 1.0e-12);
        near(before[14], 1.0, 1.0e-12);
        let handle = owner.body_handle(id).unwrap();
        let body = &mut owner.physics.bodies[handle];
        body.apply_impulse_at_point(Vector::new(0.0, 3.0, 0.0), Vector::new(12.0, 0.0, 0.0), true);
        let after = owner.snapshot(id).unwrap();
        near(after[8], 1.0, 1.0e-12);
        assert!(after[12] > 0.0);
        owner.step(0.1, 6).unwrap();
        assert!(owner.snapshot(id).unwrap()[5].abs() > 0.0);
    }

    #[test]
    fn local_cuboid_rotation_changes_mass_inertia_without_rotating_body_origin() {
        let mut registry = Registry::new();
        let world = registry.create_world(Vector::ZERO).unwrap();
        let mut box_values = box_data([2.0, 0.0, 0.0], [2.0, 0.5, 0.5]);
        let half_angle = std::f64::consts::FRAC_PI_4;
        box_values[5] = half_angle.sin();
        box_values[6] = half_angle.cos();
        let id = insert(&mut registry, world, 1, [4.0, 5.0, 6.0], &box_values);
        let state = registry.world(world).unwrap().snapshot(id).unwrap();
        assert_eq!(&state[0..3], &[4.0, 5.0, 6.0]);
        near(state[14], 2.0, 1.0e-12);
        let q = Rotation::from_xyzw(state[20], state[21], state[22], state[23]);
        let principal = Vector::new(state[17], state[18], state[19]);
        // Reconstruct I_xx/I_yy without depending on eigenvector sign or ordering.
        let axis_x = q * Vector::X;
        let axis_y = q * Vector::Y;
        let axis_z = q * Vector::Z;
        let ixx = principal.x * axis_x.x.powi(2) + principal.y * axis_y.x.powi(2) + principal.z * axis_z.x.powi(2);
        let iyy = principal.x * axis_x.y.powi(2) + principal.y * axis_y.y.powi(2) + principal.z * axis_z.y.powi(2);
        assert!(ixx > iyy * 5.0);
    }

    #[test]
    fn wrong_world_deleted_handles_and_close_never_alias_a_new_body() {
        let mut registry = Registry::new();
        let first = registry.create_world(Vector::ZERO).unwrap();
        let other = registry.create_world(Vector::ZERO).unwrap();
        let id = insert(&mut registry, first, 1, [0.0; 3], &box_data([0.0; 3], [0.5; 3]));
        assert_eq!(registry.world(other).unwrap().snapshot(id), Err(INVALID_HANDLE));
        registry.world(first).unwrap().remove_body(id).unwrap();
        assert_eq!(registry.world(first).unwrap().remove_body(id), Err(INVALID_HANDLE));
        let replacement = insert(&mut registry, first, 1, [0.0; 3], &box_data([0.0; 3], [0.5; 3]));
        assert!(replacement > id);
        assert_eq!(registry.world(first).unwrap().snapshot(id), Err(INVALID_HANDLE));
        assert!(registry.worlds.remove(&first).is_some());
        assert_eq!(registry.world(first).err(), Some(INVALID_HANDLE));
        let new_world = registry.create_world(Vector::ZERO).unwrap();
        assert!(new_world > replacement);
    }

    #[test]
    fn world_body_and_shape_budgets_reject_before_insertion() {
        let mut registry = Registry::new();
        let first = registry.create_world(Vector::ZERO).unwrap();
        for _ in 1..MAX_WORLDS { registry.create_world(Vector::ZERO).unwrap(); }
        assert_eq!(registry.create_world(Vector::ZERO), Err(LIMIT_EXCEEDED));
        let boxes = box_data([0.0; 3], [0.5; 3]);
        for i in 0..MAX_BODIES { insert(&mut registry, first, 0, [i as f64 * 2.0, 0.0, 0.0], &boxes); }
        let desc = description(0, &body_description([0.0; 3])).unwrap();
        assert_eq!(registry.create_body(first, desc, cuboids(&boxes, 1.0, false).unwrap()), Err(LIMIT_EXCEEDED));
        assert_eq!(registry.world(first).unwrap().bodies.len(), MAX_BODIES);
        assert!(matches!(cuboids(&boxes.repeat(MAX_BODY_CUBOIDS + 1), 1.0, true), Err(LIMIT_EXCEEDED)));
        let second = *registry.worlds.keys().find(|id| **id != first).unwrap();
        let compound = boxes.repeat(MAX_BODY_CUBOIDS);
        for i in 0..MAX_WORLD_CUBOIDS / MAX_BODY_CUBOIDS {
            insert(&mut registry, second, 0, [i as f64 * 2.0, 0.0, 0.0], &compound);
        }
        let desc = description(0, &body_description([0.0; 3])).unwrap();
        assert_eq!(registry.create_body(second, desc, cuboids(&boxes, 1.0, false).unwrap()), Err(LIMIT_EXCEEDED));
        assert_eq!(registry.world(second).unwrap().cuboids, MAX_WORLD_CUBOIDS);
    }

    #[test]
    fn fixed_joint_keeps_multiple_rigid_bodies_in_one_assembly() {
        let mut registry = Registry::new();
        let world = registry.create_world(Vector::ZERO).unwrap();
        let left = insert(&mut registry, world, 1, [0.0, 0.0, 0.0], &box_data([0.0; 3], [0.5; 3]));
        let right = insert(&mut registry, world, 1, [1.0, 0.0, 0.0], &box_data([0.0; 3], [0.5; 3]));
        registry.world(world).unwrap()
            .create_fixed_joint(left, right, Vector::new(0.5, 0.0, 0.0), Vector::new(-0.5, 0.0, 0.0))
            .unwrap();
        let owner = registry.world(world).unwrap();
        let handle = owner.body_handle(left).unwrap();
        owner.physics.bodies[handle].set_linvel(Vector::new(0.0, 1.0, 0.0), true);
        owner.step(0.1, 6).unwrap();
        let a = owner.snapshot(left).unwrap();
        let b = owner.snapshot(right).unwrap();
        near(b[0] - a[0], 1.0, 1.0e-1);
        near(b[1] - a[1], 0.0, 1.0e-1);
        near(b[2] - a[2], 0.0, 1.0e-1);
        assert!(a[1] > 0.0);
    }

    #[test]
    fn contained_panic_fails_only_its_world_and_still_allows_release() {
        let mut registry = Registry::new();
        let world = registry.create_world(Vector::ZERO).unwrap();
        let healthy = registry.create_world(Vector::ZERO).unwrap();
        assert_eq!(dispatch_on(&mut registry, Some(world), false, |_| panic!("intentional test failure")), PANIC);
        assert_eq!(dispatch_on(&mut registry, Some(world), false, |_| Ok(())), WORLD_FAILED);
        assert_eq!(dispatch_on(&mut registry, Some(healthy), false, |registry| registry.world(healthy)?.step(0.05, 3)), OK);
        assert_eq!(dispatch_on(&mut registry, Some(world), true, |registry| {
            registry.worlds.remove(&world).ok_or(INVALID_HANDLE)?;
            Ok(())
        }), OK);
        assert_eq!(dispatch_on(&mut registry, Some(world), true, |_| Ok(())), INVALID_HANDLE);
    }

    #[test]
    fn invalid_solver_output_permanently_fails_world() {
        let mut registry = Registry::new();
        let world = registry.create_world(Vector::ZERO).unwrap();
        let id = insert(&mut registry, world, 1, [MAX_COORDINATE, 0.0, 0.0], &box_data([0.0; 3], [0.5; 3]));
        let owner = registry.world(world).unwrap();
        let handle = owner.body_handle(id).unwrap();
        owner.physics.bodies[handle].set_linvel(Vector::X, true);
        assert_eq!(dispatch_on(&mut registry, Some(world), false, |registry| registry.world(world)?.step(0.1, 1)), WORLD_FAILED);
        assert_eq!(registry.world(world).err(), Some(WORLD_FAILED));
    }

    #[test]
    fn finite_validation_and_timestep_rejection_do_not_mutate_world() {
        let mut values = body_description([0.0; 3]);
        values[0] = f64::NAN;
        assert!(matches!(description(1, &values), Err(INVALID_ARGUMENT)));
        values = body_description([0.0; 3]);
        values[3..7].fill(0.0);
        assert!(matches!(description(1, &values), Err(INVALID_ARGUMENT)));
        let huge = quaternion(&[f64::MAX, 0.0, 0.0, f64::MAX]).unwrap();
        near(huge.length(), 1.0, 1.0e-15);
        assert!(matches!(cuboids(&box_data([0.0; 3], [-0.5, 0.5, 0.5]), 1.0, true), Err(INVALID_ARGUMENT)));
        let mut registry = Registry::new();
        let world = registry.create_world(Vector::ZERO).unwrap();
        let id = insert(&mut registry, world, 1, [0.0; 3], &box_data([0.0; 3], [0.5; 3]));
        let owner = registry.world(world).unwrap();
        let before = owner.snapshot(id).unwrap();
        for (dt, substeps) in [(f64::NAN, 1), (0.0, 1), (0.11, 1), (0.05, 17), (0.000001, 16)] {
            assert_eq!(owner.step(dt, substeps), Err(INVALID_ARGUMENT));
            assert_eq!(owner.snapshot(id).unwrap(), before);
        }
    }

    #[test]
    fn ffi_buffers_remain_unchanged_on_validation_failure() {
        let gravity = [0.0; 3];
        let mut world = 0;
        assert_eq!(unsafe { academy_physics_world_create(gravity.as_ptr(), 3, &mut world, 1) }, OK);
        let mut output = 123456;
        let desc = body_description([0.0; 3]);
        let boxes = box_data([0.0; 3], [0.5; 3]);
        assert_eq!(unsafe { academy_physics_body_create(world, 1, desc.as_ptr(), 20, boxes.as_ptr(), 10, &mut output, 1) }, BUFFER_SIZE);
        assert_eq!(output, 123456);
        assert_eq!(unsafe { academy_physics_body_create(world, 1, desc.as_ptr(), 21, boxes.as_ptr(), 10, &mut output, 1) }, OK);
        let mut state = [123.0; STATE_LEN];
        assert_eq!(unsafe { academy_physics_body_state(world, output, state.as_mut_ptr(), 25) }, BUFFER_SIZE);
        assert_eq!(state, [123.0; STATE_LEN]);
        assert_eq!(unsafe { academy_physics_body_state(world, output, std::ptr::null_mut(), STATE_LEN as i32) }, INVALID_ARGUMENT);
        // Valid allocation and sufficient capacity, but deliberately wrong alignment.
        let mut unaligned_output = [0xA5A5_A5A5_A5A5_A5A5_u64; STATE_LEN + 1];
        let output_before = unaligned_output;
        let output_pointer = unsafe { unaligned_output.as_mut_ptr().cast::<u8>().add(1).cast::<f64>() };
        assert_eq!(unsafe { academy_physics_body_state(world, output, output_pointer, STATE_LEN as i32) }, INVALID_ARGUMENT);
        assert_eq!(unaligned_output, output_before);
        let unaligned_input = [0_u64; DESCRIPTION_LEN + 1];
        let input_pointer = unsafe { unaligned_input.as_ptr().cast::<u8>().add(1).cast::<f64>() };
        let mut untouched_handle = 654321;
        assert_eq!(unsafe { academy_physics_body_create(world, 1, input_pointer, DESCRIPTION_LEN as i32,
            boxes.as_ptr(), CUBOID_LEN as i32, &mut untouched_handle, 1) }, INVALID_ARGUMENT);
        assert_eq!(untouched_handle, 654321);
        assert_eq!(academy_physics_world_destroy(world), OK);
        assert_eq!(academy_physics_world_destroy(world), INVALID_HANDLE);
    }

    #[test]
    fn ffi_create_copies_inputs_before_writing_an_aliasing_handle_output() {
        let gravity = [0.0; 3];
        let mut world = 0;
        assert_eq!(unsafe { academy_physics_world_create(gravity.as_ptr(), 3, &mut world, 1) }, OK);
        let mut description = body_description([17.0, 23.0, -5.0]);
        let description_before = description;
        let boxes = box_data([2.0, 0.0, 0.0], [0.5; 3]);
        // f64 and u64 have the same size/alignment on the supported targets. The input
        // and scalar output intentionally share the first eight bytes of this allocation.
        let allocation = description.as_mut_ptr();
        let input = allocation.cast_const();
        let output = allocation.cast::<u64>();
        assert_eq!(unsafe { academy_physics_body_create(world, 1, input, DESCRIPTION_LEN as i32,
            boxes.as_ptr(), CUBOID_LEN as i32, output, 1) }, OK);
        let body = unsafe { output.read() };
        let mut state = [0.0; STATE_LEN];
        assert_eq!(unsafe { academy_physics_body_state(world, body, state.as_mut_ptr(), STATE_LEN as i32) }, OK);
        assert_eq!(&state[0..7], &description_before[0..7]);
        assert_eq!(&description[1..], &description_before[1..]);
        near(state[14], 2.0, 1.0e-12);
        assert_eq!(academy_physics_world_destroy(world), OK);
    }

    #[test]
    fn ffi_fixed_body_rejects_all_dynamic_mutators_without_changing_state() {
        let gravity = [0.0, -9.81, 0.0];
        let mut world = 0;
        assert_eq!(unsafe { academy_physics_world_create(gravity.as_ptr(), 3, &mut world, 1) }, OK);
        let description = body_description([4.0, 5.0, 6.0]);
        let boxes = box_data([0.0; 3], [0.5; 3]);
        let mut body = 0;
        assert_eq!(unsafe { academy_physics_body_create(world, 0, description.as_ptr(), DESCRIPTION_LEN as i32,
            boxes.as_ptr(), CUBOID_LEN as i32, &mut body, 1) }, OK);
        let mut before = [0.0; STATE_LEN];
        assert_eq!(unsafe { academy_physics_body_state(world, body, before.as_mut_ptr(), STATE_LEN as i32) }, OK);
        let velocity = [1.0, 2.0, 3.0, 0.0, 0.1, 0.0];
        let impulse = [0.0, 1.0, 0.0, 5.0, 5.0, 6.0];
        assert_eq!(unsafe { academy_physics_body_set_velocity(world, body, velocity.as_ptr(), 6) }, INVALID_ARGUMENT);
        assert_eq!(unsafe { academy_physics_body_impulse_at_point(world, body, impulse.as_ptr(), 6) }, INVALID_ARGUMENT);
        assert_eq!(academy_physics_body_sleep(world, body), INVALID_ARGUMENT);
        assert_eq!(academy_physics_body_wake(world, body), INVALID_ARGUMENT);
        let mut after = [0.0; STATE_LEN];
        assert_eq!(unsafe { academy_physics_body_state(world, body, after.as_mut_ptr(), STATE_LEN as i32) }, OK);
        assert_eq!(before, after);
        assert_eq!(academy_physics_world_step(world, 0.05, 3), OK);
        assert_eq!(unsafe { academy_physics_body_state(world, body, after.as_mut_ptr(), STATE_LEN as i32) }, OK);
        assert_eq!(before, after);
        assert_eq!(academy_physics_world_destroy(world), OK);
    }

    #[test]
    fn exhausted_handle_counter_fails_closed_instead_of_reusing_ids() {
        let mut registry = Registry::new();
        registry.next_handle = u64::MAX;
        assert_eq!(registry.create_world(Vector::ZERO), Err(LIMIT_EXCEEDED));
        assert!(registry.worlds.is_empty());
        assert_eq!(registry.next_handle, u64::MAX);
    }
}
