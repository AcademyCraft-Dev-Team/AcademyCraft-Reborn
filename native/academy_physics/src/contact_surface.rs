//! Contact normals must describe the exposed surface of a voxel union.
//!
//! Rapier handles each cuboid separately. At a shared edge its diagonal normal can
//! include an internal face, making a structure wedge inside its own extraction
//! socket. Cache covered face rectangles when geometry changes, then remove only
//! those normal components. Real exterior wall/floor/ceiling contacts remain.

use rapier3d_f64::parry::bounding_volume::Aabb;
use rapier3d_f64::parry::partitioning::Bvh;
use rapier3d_f64::prelude::*;
use std::collections::{HashMap, HashSet};

const GEOMETRY_EPSILON: f64 = 1.0e-8;
const FACE_INSET: f64 = 1.0e-4;
const FACE_ANCHOR_TOLERANCE: f64 = 1.0e-3;

struct CoveredFace {
    neighbor: ColliderHandle,
    min: Vector,
    max: Vector,
}

struct Surface {
    half: Vector,
    faces: [Vec<CoveredFace>; 6],
    neighbors: HashSet<ColliderHandle>,
}

impl Surface {
    fn new(half: Vector) -> Self {
        Self { half, faces: std::array::from_fn(|_| Vec::new()), neighbors: HashSet::new() }
    }
}

#[derive(Default)]
pub(super) struct ContactSurfaces {
    surfaces: HashMap<ColliderHandle, Surface>,
    fixed_index: Bvh,
    fixed_handles: HashMap<u32, ColliderHandle>,
    dynamic_links: HashMap<RigidBodyHandle, HashSet<RigidBodyHandle>>,
}

impl ContactSurfaces {
    pub(super) fn insert_body(&mut self, body: RigidBodyHandle,
                             bodies: &RigidBodySet, colliders: &ColliderSet) {
        let body = &bodies[body];
        let fixed = body.is_fixed();
        let mut local_index = Bvh::new();
        let mut local_handles = HashMap::new();
        for &handle in body.colliders() {
            let collider = &colliders[handle];
            let Some(cuboid) = collider.shape().as_cuboid() else { continue; };
            self.surfaces.insert(handle, Surface::new(cuboid.half_extents));
            let position = if fixed { *collider.position() }
                           else { *collider.position_wrt_parent().unwrap() };
            let bounds = collider.shape().compute_aabb(&position);
            let expanded = Aabb::new(bounds.mins - Vector::splat(GEOMETRY_EPSILON),
                                     bounds.maxs + Vector::splat(GEOMETRY_EPSILON));
            let (index, handles) = if fixed { (&self.fixed_index, &self.fixed_handles) }
                                   else { (&local_index, &local_handles) };
            let candidates: Vec<_> = index.intersect_aabb(&expanded)
                .filter_map(|id| handles.get(&id).copied()).collect();
            for other_handle in candidates {
                let other = &colliders[other_handle];
                let other_position = if fixed { *other.position() }
                                     else { *other.position_wrt_parent().unwrap() };
                self.cover_faces(handle, other_handle, position.inverse() * other_position);
                self.cover_faces(other_handle, handle, other_position.inverse() * position);
            }
            let id = handle.into_raw_parts().0;
            if fixed {
                self.fixed_handles.insert(id, handle);
                self.fixed_index.insert(bounds, id);
            } else {
                local_handles.insert(id, handle);
                local_index.insert(bounds, id);
            }
        }
    }

    fn cover_faces(&mut self, current: ColliderHandle, other: ColliderHandle, relative: Pose) {
        let Some(other_surface) = self.surfaces.get(&other) else { return; };
        // Voxel boxes share axes within a body; also accept equivalent cardinal
        // rotations. Arbitrarily rotated local boxes keep Rapier's normal.
        let axes = [relative.rotation * Vector::X, relative.rotation * Vector::Y,
                    relative.rotation * Vector::Z];
        if axes.iter().any(|axis| axis.abs().max_element() < 1.0 - GEOMETRY_EPSILON) { return; }
        let other_half = axes[0].abs() * other_surface.half.x
                       + axes[1].abs() * other_surface.half.y
                       + axes[2].abs() * other_surface.half.z;
        let other_min = relative.translation - other_half;
        let other_max = relative.translation + other_half;
        let surface = self.surfaces.get_mut(&current).unwrap();
        let mut covered = false;
        for axis in 0..3 {
            for positive in [false, true] {
                let face = surface.half[axis] * if positive { 1.0 } else { -1.0 };
                let sample = face + if positive { GEOMETRY_EPSILON * 2.0 }
                                    else { -GEOMETRY_EPSILON * 2.0 };
                if sample <= other_min[axis] || sample >= other_max[axis] { continue; }
                let min = (-surface.half).max(other_min);
                let max = surface.half.min(other_max);
                // Edge-only or corner-only neighbors do not cover any face.
                if (0..3).any(|tangent| tangent != axis
                    && max[tangent] - min[tangent] <= GEOMETRY_EPSILON) { continue; }
                surface.faces[axis * 2 + usize::from(positive)]
                    .push(CoveredFace { neighbor: other, min, max });
                surface.neighbors.insert(other);
                covered = true;
            }
        }
        if covered {
            // Removal must invalidate both directions, including overlapping
            // boxes where only one of the two has a hidden exterior face.
            self.surfaces.get_mut(&other).unwrap().neighbors.insert(current);
        }
    }

    pub(super) fn remove_body(&mut self, body: RigidBodyHandle,
                             bodies: &RigidBodySet, colliders: &ColliderSet) {
        let mut affected = self.connected_bodies(body);
        affected.remove(&body);
        self.remove_surfaces(body, bodies);
        if let Some(links) = self.dynamic_links.remove(&body) {
            for other in links {
                if let Some(links) = self.dynamic_links.get_mut(&other) {
                    links.remove(&body);
                }
            }
        }
        // Removing a middle body can split a fixed-joint assembly. Rebuild only
        // that old assembly so independent survivors cannot hide each other's faces.
        if !affected.is_empty() {
            for &other in &affected { self.remove_surfaces(other, bodies); }
            for &other in &affected { self.insert_body(other, bodies, colliders); }
            let mut pending = affected;
            while let Some(&start) = pending.iter().next() {
                let connected = self.connected_bodies(start);
                self.cover_group(&connected, bodies, colliders);
                for other in connected { pending.remove(&other); }
            }
        }
    }

    pub(super) fn refresh_body_pose(&mut self, body: RigidBodyHandle,
                                   bodies: &RigidBodySet, colliders: &ColliderSet) {
        let connected = self.connected_bodies(body);
        for &member in &connected { self.remove_surfaces(member, bodies); }
        for &member in &connected { self.insert_body(member, bodies, colliders); }
        if connected.len() > 1 { self.cover_group(&connected, bodies, colliders); }
    }

    fn remove_surfaces(&mut self, body: RigidBodyHandle, bodies: &RigidBodySet) {
        for &handle in bodies[body].colliders() {
            if self.fixed_handles.remove(&handle.into_raw_parts().0).is_some() {
                self.fixed_index.remove(handle.into_raw_parts().0);
            }
            let Some(surface) = self.surfaces.remove(&handle) else { continue; };
            for neighbor in surface.neighbors {
                if let Some(other) = self.surfaces.get_mut(&neighbor) {
                    for face in &mut other.faces {
                        face.retain(|patch| patch.neighbor != handle);
                    }
                    other.neighbors.remove(&handle);
                }
            }
        }
    }

    fn connected_bodies(&self, start: RigidBodyHandle) -> HashSet<RigidBodyHandle> {
        let mut result = HashSet::from([start]);
        let mut pending = vec![start];
        while let Some(body) = pending.pop() {
            if let Some(links) = self.dynamic_links.get(&body) {
                for &other in links {
                    if result.insert(other) { pending.push(other); }
                }
            }
        }
        result
    }

    pub(super) fn join_bodies(&mut self, first: RigidBodyHandle, second: RigidBodyHandle,
                             bodies: &RigidBodySet, colliders: &ColliderSet) {
        if !bodies[first].is_dynamic() || !bodies[second].is_dynamic() { return; }
        let connected = self.connected_bodies(first);
        let second_group = self.connected_bodies(second);
        let already_connected = connected.contains(&second);
        self.dynamic_links.entry(first).or_default().insert(second);
        self.dynamic_links.entry(second).or_default().insert(first);
        if already_connected { return; }
        // A joint is installed only when the assembly topology changes; there
        // is no world query or neighbor rebuild during physics stepping.
        self.cover_between_groups(&connected, &second_group, bodies, colliders);
    }

    fn cover_between_groups(&mut self, first: &HashSet<RigidBodyHandle>,
                            second: &HashSet<RigidBodyHandle>,
                            bodies: &RigidBodySet, colliders: &ColliderSet) {
        let mut index = Bvh::new();
        let mut handles: HashMap<u32, ColliderHandle> = HashMap::new();
        for &body in first {
            for &handle in bodies[body].colliders() {
                let id = handle.into_raw_parts().0;
                handles.insert(id, handle);
                index.insert(colliders[handle].compute_aabb(), id);
            }
        }
        for &body in second {
            for &handle in bodies[body].colliders() {
                let collider = &colliders[handle];
                let bounds = collider.compute_aabb();
                let expanded = Aabb::new(bounds.mins - Vector::splat(GEOMETRY_EPSILON),
                                         bounds.maxs + Vector::splat(GEOMETRY_EPSILON));
                for id in index.intersect_aabb(&expanded) {
                    let other_handle = handles[&id];
                    let other = &colliders[other_handle];
                    self.cover_faces(handle, other_handle, collider.position().inverse() * *other.position());
                    self.cover_faces(other_handle, handle, other.position().inverse() * *collider.position());
                }
            }
        }
    }

    fn cover_group(&mut self, group: &HashSet<RigidBodyHandle>,
                   bodies: &RigidBodySet, colliders: &ColliderSet) {
        let mut index = Bvh::new();
        let mut handles: HashMap<u32, ColliderHandle> = HashMap::new();
        for &body in group {
            for &handle in bodies[body].colliders() {
                let collider = &colliders[handle];
                let bounds = collider.compute_aabb();
                let expanded = Aabb::new(bounds.mins - Vector::splat(GEOMETRY_EPSILON),
                                         bounds.maxs + Vector::splat(GEOMETRY_EPSILON));
                let candidates: Vec<_> = index.intersect_aabb(&expanded)
                    .filter_map(|id| handles.get(&id).copied()).collect();
                for other_handle in candidates {
                    let other = &colliders[other_handle];
                    if collider.parent() == other.parent() { continue; }
                    self.cover_faces(handle, other_handle, collider.position().inverse() * *other.position());
                    self.cover_faces(other_handle, handle, other.position().inverse() * *collider.position());
                }
                let id = handle.into_raw_parts().0;
                handles.insert(id, handle);
                index.insert(bounds, id);
            }
        }
    }

    fn exposed_normal(&self, collider: ColliderHandle, colliders: &ColliderSet,
                      point: Vector, normal: Vector) -> Vector {
        let Some(surface) = self.surfaces.get(&collider) else { return normal; };
        if surface.neighbors.is_empty() { return normal; }
        let pose = colliders[collider].position();
        let mut local_normal = pose.rotation.inverse() * normal;
        let local_point = pose.inverse_transform_point(point);
        // Move tangential edge samples slightly inside this face so a box
        // touching only its edge cannot hide an exterior normal component.
        let inset = surface.half.min(Vector::splat(FACE_INSET)) * 0.5;
        let point = local_point.clamp(-surface.half + inset, surface.half - inset);
        for axis in 0..3 {
            if local_normal[axis].abs() < 1.0e-6 { continue; }
            let face_coordinate = surface.half[axis] * local_normal[axis].signum();
            if (local_point[axis] - face_coordinate).abs() > FACE_ANCHOR_TOLERANCE { continue; }
            let face = &surface.faces[axis * 2 + usize::from(local_normal[axis] > 0.0)];
            if face.iter().any(|patch| (0..3).all(|tangent| tangent == axis
                || (point[tangent] >= patch.min[tangent] - GEOMETRY_EPSILON
                    && point[tangent] <= patch.max[tangent] + GEOMETRY_EPSILON))) {
                local_normal[axis] = 0.0;
            }
        }
        pose.rotation * local_normal
    }
}

impl PhysicsHooks for ContactSurfaces {
    fn modify_solver_contacts(&self, context: &mut ContactModificationContext) {
        let collider1 = context.collider1;
        let collider2 = context.collider2;
        let colliders = context.colliders;
        let Some(contact) = context.rigid_mut() else { return; };
        let original = *contact.normal;
        let mut corrected = None;
        let mut consistent = true;
        contact.solver_contacts.retain(|point| {
            let one = self.exposed_normal(collider1, colliders, point.anchor1, original);
            let two = -self.exposed_normal(collider2, colliders, point.anchor2, -one);
            if two.length_squared() < 1.0e-8 { return false; }
            let normal = two.normalize();
            if let Some(previous) = corrected {
                consistent &= normal.dot(previous) > 1.0 - 1.0e-6;
            } else {
                corrected = Some(normal);
            }
            true
        });
        // A manifold has one shared normal. Preserve it conservatively when
        // contacts lie on different exposed portions of a partially covered face.
        if consistent && let Some(normal) = corrected {
            *contact.normal = normal;
            for point in contact.solver_contacts.iter_mut() {
                point.dist = (point.anchor2 - point.anchor1).dot(normal);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn fixed_cube(physics: &mut PhysicsWorld, center: Vector, rotation: Rotation) -> RigidBodyHandle {
        let body = physics.insert_body(RigidBodyBuilder::fixed().pose(Pose::from_parts(center, rotation)));
        physics.insert_collider(ColliderBuilder::cuboid(0.5, 0.5, 0.5), Some(body));
        body
    }

    #[test]
    fn cross_body_face_coverage_follows_rotation_and_removal() {
        let rotation = Rotation::from_rotation_z(0.35);
        let mut physics = PhysicsWorld::new();
        let first = fixed_cube(&mut physics, Vector::ZERO, rotation);
        let second = fixed_cube(&mut physics, rotation * Vector::Y, rotation);
        let mut surfaces = ContactSurfaces::default();
        surfaces.insert_body(first, &physics.bodies, &physics.colliders);
        surfaces.insert_body(second, &physics.bodies, &physics.colliders);
        let collider = physics.bodies[first].colliders()[0];
        let point = rotation * Vector::new(0.5, 0.5, 0.0);
        let diagonal = rotation * Vector::new(1.0, 1.0, 0.0).normalize();
        let corrected = surfaces.exposed_normal(collider, &physics.colliders, point, diagonal);
        assert!(corrected.normalize().distance(rotation * Vector::X) < 1.0e-8);
        surfaces.remove_body(second, &physics.bodies, &physics.colliders);
        let corrected = surfaces.exposed_normal(collider, &physics.colliders, point, diagonal);
        assert!(corrected.distance(diagonal) < 1.0e-8);
        assert_eq!(surfaces.fixed_handles.len(), 1);
    }

    #[test]
    fn corner_only_neighbors_do_not_hide_real_faces() {
        let mut physics = PhysicsWorld::new();
        let first = fixed_cube(&mut physics, Vector::ZERO, Rotation::IDENTITY);
        let second = fixed_cube(&mut physics, Vector::new(1.0, 1.0, 0.0), Rotation::IDENTITY);
        let mut surfaces = ContactSurfaces::default();
        surfaces.insert_body(first, &physics.bodies, &physics.colliders);
        surfaces.insert_body(second, &physics.bodies, &physics.colliders);
        let collider = physics.bodies[first].colliders()[0];
        let diagonal = Vector::new(1.0, 1.0, 0.0).normalize();
        assert_eq!(surfaces.exposed_normal(collider, &physics.colliders,
                                          Vector::new(0.5, 0.5, 0.0), diagonal), diagonal);
    }
}
