//! Released structures must retain support without relying on a launch or a controller.
use super::*;

fn add(registry: &mut Registry, world: u64, kind: i32, center: Vector, half: Vector,
       tilt: f64) -> u64 {
    let mut values = [0.0; DESCRIPTION_LEN];
    values[..3].copy_from_slice(&center.to_array());
    values[3] = (tilt * 0.5).sin();
    values[6] = (tilt * 0.5).cos();
    values[13] = if kind == 1 { 1.0 } else { 0.0 };
    values[14] = 0.6;
    values[18] = 1.0;
    values[19] = f64::from(kind);
    values[20] = 1.0;
    let description = description(kind, &values).unwrap();
    let boxes = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, half.x, half.y, half.z];
    let boxes = cuboids(&boxes, description.density, kind == 1).unwrap();
    registry.create_body(world, description, boxes).unwrap()
}

#[test]
fn released_blocks_keep_floor_support_in_close_fitting_pits() {
    for half in [0.5, 1.5, 2.5] {
        for tilt in [0.0, 0.001, 0.03] {
            for offset in [0.0, 0.003, 0.03] {
                let mut registry = Registry::new();
                let world = registry.create_world(Vector::new(0.0, -9.81, 0.0)).unwrap();
                add(&mut registry, world, 0, Vector::new(0.0, -0.5, 0.0), Vector::new(8.0, 0.5, 8.0), 0.0);
                for (center, size) in [
                    (Vector::new(-half - 0.5, 0.5, 0.0), Vector::new(0.5, 0.5, 8.0)),
                    (Vector::new(half + 0.5, 0.5, 0.0), Vector::new(0.5, 0.5, 8.0)),
                    (Vector::new(0.0, 0.5, -half - 0.5), Vector::new(half, 0.5, 0.5)),
                    (Vector::new(0.0, 0.5, half + 0.5), Vector::new(half, 0.5, 0.5)),
                ] { add(&mut registry, world, 0, center, size, 0.0); }
                let body = add(&mut registry, world, 1, Vector::new(offset, 3.5, offset),
                               Vector::new(half, 0.5, half), tilt);
                let owner = registry.world(world).unwrap();
                let handle = owner.body_handle(body).unwrap();
                for tick in 0..240 {
                    owner.step(0.05, 2).unwrap();
                    let collider = owner.physics.bodies[handle].colliders()[0];
                    let bottom = owner.physics.colliders[collider].compute_aabb().mins.y;
                    assert!(bottom >= -0.015, "lost floor: half={half} tilt={tilt} offset={offset} tick={tick} bottom={bottom}");
                }
            }
        }
    }
}

#[test]
fn unlaunched_flat_landings_do_not_sink_and_rebound() {
    for half in [0.5, 1.5, 2.5] {
        for height in [3.0, 5.0, 7.0] {
            let mut registry = Registry::new();
            let world = registry.create_world(Vector::new(0.0, -9.81, 0.0)).unwrap();
            add(&mut registry, world, 0, Vector::new(0.0, -0.5, 0.0), Vector::new(8.0, 0.5, 8.0), 0.0);
            let body = add(&mut registry, world, 1, Vector::new(0.0, height + 0.5, 0.0),
                           Vector::new(half, 0.5, half), 0.0);
            let owner = registry.world(world).unwrap();
            for tick in 0..240 {
                owner.step(0.05, 2).unwrap();
                let state = owner.snapshot(body).unwrap();
                assert!(state[1] >= 0.49, "landing penetrated: half={half} height={height} tick={tick} y={}", state[1]);
                assert!(state[8] <= 0.02, "landing rebounded: half={half} height={height} tick={tick} vy={}", state[8]);
            }
            let state = owner.snapshot(body).unwrap();
            assert!((state[1] - 0.5).abs() < 0.01 && state[24] == 1.0,
                    "landing did not naturally settle: {state:?}");
        }
    }
}

#[test]
fn released_plate_lands_across_section_seams_with_an_offset_local_center() {
    let mut registry = Registry::new();
    let world = registry.create_world(Vector::new(0.0, -9.81, 0.0)).unwrap();
    // The real client fixture straddles both X/Z section boundaries at 256.
    for (x, hx) in [(252.5, 3.5), (260.0, 4.0)] {
        for (z, hz) in [(252.5, 3.5), (260.0, 4.0)] {
            add(&mut registry, world, 0, Vector::new(x, 240.5, z), Vector::new(hx, 0.5, hz), 0.0);
        }
    }
    let mut values = [0.0; DESCRIPTION_LEN];
    values[..3].copy_from_slice(&[255.0, 246.0, 255.0]);
    values[6] = 1.0;
    values[13] = 1.0;
    values[14] = 0.6;
    values[18..21].fill(1.0);
    let description = description(1, &values).unwrap();
    let boxes = cuboids(&[1.5, 0.5, 1.5, 0.0, 0.0, 0.0, 1.0, 1.5, 0.5, 1.5], 1.0, true).unwrap();
    let body = registry.create_body(world, description, boxes).unwrap();
    let owner = registry.world(world).unwrap();
    let handle = owner.body_handle(body).unwrap();
    for tick in 0..240 {
        owner.step(0.05, 2).unwrap();
        let collider = owner.physics.bodies[handle].colliders()[0];
        let bottom = owner.physics.colliders[collider].compute_aabb().mins.y;
        let state = owner.snapshot(body).unwrap();
        assert!(bottom >= 240.99, "section seam lost support: tick={tick} bottom={bottom}");
        assert!(state[8] <= 0.02, "section seam rebounded: tick={tick} state={state:?}");
    }
    assert_eq!(owner.snapshot(body).unwrap()[24], 1.0);
}
