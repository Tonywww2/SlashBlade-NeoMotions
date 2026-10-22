"""Reusable RE gimmick rig -> named OBJ-part transforms on the hand timeline.

Geometry is original. Only bone transforms are sampled from the local weapon rig.
Coordinates are OBJ units, after the source +Z -> model -X attachment basis.
"""
from generate_animations import sub, scale, rotate, mul, matrix_quat
from retarget_wilds import inverse, euler

UNITS = 96
BASIS = matrix_quat((0, 1, 0), (0, 0, -1), (-1, 0, 0))

def model_vector(v): return (-v[2], v[0], -v[1])

def component_pose(rig, motion, frame, bone):
    positions, rotations = rig.pose(motion, min(frame, rig.clips[motion]['frames']))
    origin = rig.index['Grip']; part = rig.index[bone]
    inv = inverse(rotations[origin])
    position = scale(model_vector(rotate(inv, sub(positions[part], positions[origin]))), UNITS)
    rotation = mul(mul(BASIS, mul(inv, rotations[part])), inverse(BASIS))
    return dict(zip('xyz', position), **euler(rotation))

def visual_motion(clip, frame, fallback):
    # First WeaponVisual is the geometry channel; a duplicated channel can add a
    # phial recoil over the same morph. Do not replace the morph by that overlay.
    for track in clip['events']:
        if not track['name'].endswith('.WeaponVisual'): continue
        for prop in track['properties']:
            if prop['name'] != '_WeaponMotionID': continue
            keys = [k for k in prop.get('keys', []) if k['frame'] <= frame]
            if keys:
                key = keys[-1]
                return key['value'], frame - key['frame']
        break
    return fallback, 0

def tracks(rig, clip, frame, sword):
    motion, time = visual_motion(clip, frame, 100 if sword else 1)
    if motion not in rig.clips: raise ValueError(f'Unknown gimmick motion {motion}')
    return {name: component_pose(rig, motion, time, bone)
            for name, bone in [('axeComponent', 'Axe'), ('swordComponent', 'Sword')]}
