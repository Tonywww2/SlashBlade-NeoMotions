"""Offline provenance and full-body preservation checks against local source binaries."""
import hashlib
import json
import math
from pathlib import Path
from wilds_motion import Rig
from generate_animations import ROOT, ASSETS, quat, mul, rotate, unit, sub, dot
from read_wilds_motion_events import extract

def main():
    manifest=json.loads((ASSETS/'motions/source_actions.json').read_text(encoding='utf-8'))
    paths={'gs':next((ROOT/'.local/wilds/combat').rglob('wp00_00.motlist.992')),
           'db':next((ROOT/'.local/wilds/dualblade').rglob('wp02_00.motlist.992')),
           'sa':next((ROOT/'.local/wilds/switchaxe').rglob('wp08_00.motlist.992'))}
    rigs={w:Rig(p) for w,p in paths.items()}
    component=next((ROOT/'.local/wilds/switchaxe').rglob('wpg08_00.motlist.992'))
    assert hashlib.sha256(component.read_bytes()).hexdigest()==manifest['sources']['wpg08']['sha256']
    count=0
    for w,p in paths.items():assert hashlib.sha256(p.read_bytes()).hexdigest()==manifest['sources'][w]['sha256']
    flip_up=[];flip_arc=0;last_q=None
    for name,entry in manifest['actions'].items():
        data=json.loads((ASSETS/'player_animation'/f'{name}.json').read_text(encoding='utf-8'))
        assert data['neomotions']['source']==entry['source'],(name,'source identifier differs')
        assert data['neomotions']['motionId']==entry['motionId'],(name,'source clip differs')
        rig=rigs[entry['source']]
        assert len(entry['samples'])==data['emote']['endTick']+1
        for key,(clip,frame) in zip(data['emote']['moves'],entry['samples']):
            positions,rotations=rig.pose(clip,frame)
            if name in ['greatsword','dualblade','switchaxe_axe','switchaxe_sword']:continue # walking keeps vanilla legs
            exported=quat(key['body']);body=(-exported[0],-exported[1],exported[2],exported[3])
            original=rotations[rig.index['Hip']];original=(original[0],-original[1],-original[2],original[3])
            assert abs(dot(body,original))>1-1e-8,(name,frame,'root orientation lost')
            for side,prefix in [('left','L'),('right','R')]:
                actual=rotate(mul(body,quat(key[side+'Leg'])),(0,1,0))
                d=unit(sub(positions[rig.index[prefix+'_Foot']],positions[rig.index[prefix+'_Thigh']]))
                wanted=(d[0],-d[1],-d[2])
                assert dot(actual,wanted)>1-1e-8,(name,frame,'leg no longer follows source')
            if name=='gs_true_slash' and 30<=frame<=78:
                flip_up.append(rotate(body,(0,-1,0))[1])
                if last_q is not None:flip_arc+=2*math.acos(min(1,abs(dot(last_q,body))))
                last_q=body
            count+=1
    assert max(flip_up)>.5 and flip_up[-1]<-.9,'Inverted phase or completed flip missing'
    assert flip_arc>math.pi*1.8,('Incomplete full-body turn',flip_arc)
    events={e['motion_id']:e for e in extract(paths['gs'])}
    windows=set()
    for track in events[248]['events']:
        if 'AttackCollision_PlWp'!=track['name'].split('.')[-1]:continue
        for prop in track['properties']:
            if prop['name']=='_IsInRange':windows.add((prop['begin'],prop['end']))
    assert (33,43) in windows and (100,113) in windows
    assert manifest['chargeTicks']==[0,16,31,46] and manifest['overheatTick']==60
    print(f'PASS: {count} source-matched root/leg poses, {len(manifest["actions"])} actions; TCS inverted and recovered. Accumulated 3D orientation path: {math.degrees(flip_arc):.1f} degrees (not a single-axis roll).')

if __name__=='__main__':main()
