//! Explicit offline replay of successful requests recorded at the Java/native boundary.
use super::*;
use std::collections::HashMap;

#[test]
#[ignore = "requires ACADEMY_PHYSICS_TRACE with an isolated client request trace"]
fn replay_client_trace() {
    let path = std::env::var("ACADEMY_PHYSICS_TRACE").expect("trace path required");
    let source = std::fs::read_to_string(path).unwrap();
    let mut registry = Registry::new();
    let mut world = 0;
    let mut handles = HashMap::new();
    let mut steps = 0;
    let mut max_delta: f64 = 0.0;
    let removal = std::env::var("ACADEMY_PHYSICS_TRACE_REMOVE").unwrap_or_default();
    let removals: Vec<u64> = removal.split(',').filter(|v| !v.is_empty()).map(|v| v.parse().unwrap()).collect();
    for (line_index, line) in source.lines().enumerate() {
        let mut fields = line.split_whitespace();
        let operation = fields.next().unwrap();
        let external: u64 = fields.next().unwrap().parse().unwrap();
        let values: Vec<f64> = fields.map(|field| field.parse().unwrap()).collect();
        if operation == "WORLD" {
            world = registry.create_world(vector(&values, MAX_SPEED).unwrap()).unwrap();
            continue;
        }
        if operation == "CREATE" {
            if external == 7 {
                for external in &removals {
                    if let Some(id) = handles.remove(external) { registry.world(world).unwrap().remove_body(id).unwrap(); }
                }
            }
            let description = description(values[0] as i32, &values[1..22]).unwrap();
            let boxes = cuboids(&values[22..], description.density, description.kind == 1).unwrap();
            let id = registry.create_body(world, description, boxes).unwrap();
            handles.insert(external, id);
            eprintln!("REPLAY_CREATE external={external} id={id} kind={} position={:?} boxes={}",
                values[0], &values[1..4], (values.len() - 22) / 10);
            continue;
        }
        let owner = registry.world(world).unwrap();
        let id = handles.get(&external).copied().unwrap_or(0);
        if external != 0 && id == 0 && removals.contains(&external) { continue; }
        match operation {
            "POSE" => owner.set_pose(id, pose(&values).unwrap()).unwrap(),
            "VELOCITY" => {
                let handle = owner.dynamic_handle(id).unwrap();
                owner.physics.bodies[handle].set_linvel(vector(&values[..3], MAX_SPEED).unwrap(), true);
                owner.physics.bodies[handle].set_angvel(vector(&values[3..], MAX_SPEED).unwrap(), true);
            }
            "IMPULSE" => {
                let handle = owner.dynamic_handle(id).unwrap();
                owner.physics.bodies[handle].apply_impulse_at_point(vector(&values[..3], 1e12).unwrap(),
                    vector(&values[3..], MAX_COORDINATE).unwrap(), true);
            }
            "SLEEP" => { let handle = owner.dynamic_handle(id).unwrap(); owner.physics.bodies[handle].sleep(); }
            "WAKE" => { let handle = owner.dynamic_handle(id).unwrap(); owner.physics.wake_up(handle, true); }
            "PAUSE" => owner.set_simulation_paused(id, values[0] == 1.0).unwrap(),
            "REMOVE" => { owner.remove_body(id).unwrap(); handles.remove(&external); }
            "JOINT" => owner.create_fixed_joint(id, handles[&(values[0] as u64)],
                vector(&values[1..4], MAX_COORDINATE).unwrap(), vector(&values[4..], MAX_COORDINATE).unwrap()).unwrap(),
            "STEP" => {
                owner.step(values[0], values[1] as i32).unwrap();
                steps += 1;
                if steps % 40 == 0 {
                    for (id, entry) in &owner.bodies {
                        if entry.kind != 1 { continue; }
                        let state = owner.snapshot(*id).unwrap();
                        eprintln!("REPLAY_STATE step={steps} id={id} position={:?} velocity={:?}", &state[..3], &state[7..13]);
                        for pair in owner.physics.contact_pairs().filter(|_| std::env::var("ACADEMY_PHYSICS_TRACE_CONTACTS").is_ok()) {
                            let first = &owner.physics.colliders[pair.collider1];
                            let second = &owner.physics.colliders[pair.collider2];
                            if first.parent() != Some(entry.handle) && second.parent() != Some(entry.handle) { continue; }
                            for manifold in pair.solver_manifolds() {
                                if manifold.data.solver_contacts.is_empty() { continue; }
                                eprintln!("REPLAY_CONTACT bodies={:?}/{:?} collider_positions={:?}/{:?} normal={:?} contacts={}",
                                    first.parent(), second.parent(), first.position(), second.position(), manifold.data.normal,
                                    manifold.data.solver_contacts.len());
                                for point in &manifold.data.solver_contacts {
                                    eprintln!("  point={:?}", point);
                                }
                            }
                        }
                    }
                }
            }
            "STATE" => {
                let current = owner.snapshot(id).unwrap();
                let delta = values.iter().zip(current).map(|(before, after)| (before - after).abs()).fold(0.0, f64::max);
                max_delta = max_delta.max(delta);
                if removal.is_empty() {
                    assert!(delta < 1e-7, "trace diverged at line {} operation={operation} id={id} delta={delta}", line_index + 1);
                }
            }
            _ => panic!("unknown trace operation {operation}"),
        }
    }
    assert!(steps > 0);
    eprintln!("REPLAY_FINISHED steps={steps} maximum_state_error={max_delta}");
}
