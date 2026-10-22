"""Retarget locally extracted Wilds MOT.932 + CLIP.85 events into the addon rig.

No synthetic swing/footwork keyframes: every sample comes from an identified MOT.
Minecraft adaptations: rigid limbs, two-hand reach constraint, back-mounted sheath,
20 Hz timing and collision-respecting horizontal root motion at half source scale.
Raw PAK/USER/MOT files remain in .local and are never copied into the mod.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
from wilds_motion import Rig
from read_wilds_motion_events import extract
from read_wilds_rsz import expand
from retarget_wilds import inverse, normalized, euler
from generate_animations import (ROOT,ASSETS,quat,mul,rotate,add,sub,scale,dot,cross,unit,sample,
                                  matrix_quat,anchor_upper_body,hand_position,vmd_bytes)

RSZ=ROOT/'.local/wilds/rsz'
# PlayerAnimator's body transform runs BEFORE LivingEntityRenderer's (-X,-Y,Z)
# reflection, 0.9375 player scale and -1.501 model translation. Its positions are
# blocks; all limb positions are model pixels. Keep these spaces distinct.
PLAYER_SCALE=.9375
BODY_PIVOT=24.016-.7*16/PLAYER_SCALE
def document(name):
    d=json.loads((RSZ/(name+'.user.3.json')).read_text(encoding='utf-8'))
    return expand(d,d['roots'][0])

def mc(v): return (v[0],-v[1],-v[2])
def mcq(q): return (q[0],-q[1],-q[2],q[3])
def qdirection(v):
    x,y,z=unit(v)
    return (1,0,0,0) if y<-.999999 else normalized((z,0,-x,1+y))

def continuous(q,previous):
    a=euler(q)
    if previous is None:return a
    alternatives=[a,dict(pitch=a['pitch']+180,yaw=180-a['yaw'],roll=a['roll']+180)]
    for v in alternatives:
        for k in v:v[k]=previous[k]+(v[k]-previous[k]+180)%360-180
    return min(alternatives,key=lambda v:sum((v[k]-previous[k])**2 for k in v))

def pose(rig,motion,frame,dual,idle=False):
    positions,rotations=rig.pose(motion,frame)
    def p(n):return positions[rig.index[n]]
    def r(n):return rotations[rig.index[n]]
    body=mcq(r('Hip')) if not idle else (0,0,0,1);inv=inverse(body)
    def local(v):return rotate(inv,mc(v))
    across=unit(sub(p('L_UpperArm'),p('R_UpperArm')))
    up=unit(sub(p('Neck_0'),p('Hip')))
    # Source frame has +Y up, +Z forward. Model frame has +Y down, +Z back.
    forward=unit(cross(across,up));across=unit(cross(up,forward))
    torso=matrix_quat(local(across),local(scale(up,-1)),local(scale(forward,-1)))
    result={'body':euler(body),'torso':euler(torso),'head':euler(mul(inv,mcq(r('Head')))),
            'rightArm':{},'leftArm':{}}
    anchor_upper_body(result)
    shoulders={side:tuple(result[side+'Arm'][k] for k in 'xyz') for side in ['right','left']}
    desired={side:add((0,12,0),scale(local(sub(p(prefix+'_Hand'),p('Hip'))),16))
             for side,prefix in [('right','R'),('left','L')]}
    directions={side:unit(local(rotate(r(prefix+'_Wep'),(0,0,1))))
                for side,prefix in [('right','R'),('left','L')]}
    if not dual:
        # Keep the extracted blade direction, then fit both rigid nine-pixel arms to
        # the same original-model handle. The source elbow/wrist positions set the
        # closest point on the reachable circle, rather than a hand-authored grip.
        d=directions['right'];a=shoulders['right'];b=add(shoulders['left'],scale(d,3))
        axis=unit(sub(b,a));center=scale(add(a,b),.5)
        projected=sub(desired['right'],center);projected=sub(projected,scale(axis,dot(projected,axis)))
        if dot(projected,projected)<1e-10:projected=cross(axis,(0,1,0))
        radius=math.sqrt(max(0,81-dot(sub(b,a),sub(b,a))/4))
        grip=add(center,scale(unit(projected),radius))
        desired={'right':grip,'left':sub(grip,scale(d,3))}
    for side,prefix in [('right','R'),('left','L')]:
        result[side+'Arm'].update(euler(qdirection(sub(desired[side],shoulders[side]))))
        # GS R_Wep: +Z points to the tip and -X is the cutting edge/travel.
        # Our mesh: -X points to the tip, -Y is the edge, +Z is the broad-face
        # normal. Mapping source +X to the face normal made every cut a flat slap.
        # Keep dual blades' independently established attachment basis unchanged.
        d=directions[side];normal=local(rotate(r(prefix+'_Wep'),(1,0,0) if dual else (0,-1,0)))
        normal=unit(sub(normal,scale(d,dot(d,normal))));x=scale(d,-1)
        result['_'+side+'WeaponRotation']=euler(matrix_quat(x,cross(normal,x),normal))
        result[side+'Leg']={**euler(qdirection(local(sub(p(prefix+'_Foot'),p(prefix+'_Thigh'))))),
                            'x':1.9 if side=='left' else -1.9,'y':12,'z':.1}
    # Preserve the source airborne trajectory and grounded support. Knee joints have
    # no Minecraft counterpart; solve the root height for the retargeted rigid soles.
    if not idle:
        pivot=(0,BODY_PIVOT,0)
        def root(v):return add(pivot,rotate(body,sub(v,pivot)))
        soles=[]
        for side in ['right','left']:
            leg=result[side+'Leg'];origin=tuple(leg[k] for k in 'xyz')
            for x in [-2,2]:
                for z in [-2,2]:soles.append(root(add(origin,rotate(quat(leg),(x,12,z))))[1])
        source_floor=min(p('R_Foot')[1],p('L_Foot')[1])-.09
        # Root jumping is visual, while the server owns horizontal collision motion.
        y=24-max(0,source_floor)*16-max(soles)
        sway=mc(sub(p('Hip'),p('root')))
        result['body'].update(x=-sway[0]*PLAYER_SCALE,y=-y/16*PLAYER_SCALE,z=sway[2]*PLAYER_SCALE)
    else:
        for name in ['leftLeg','rightLeg']:result.pop(name)
    result['body'].update(euler((-body[0],-body[1],body[2],body[3])))
    return result

def event_data(clip,dual):
    cancel={};hits=[]
    for track in clip['events']:
        if 'Cancel' in track['name']:
            for p in track['properties']:
                starts=[k['frame'] for k in p.get('keys',[]) if k['value'] is True]
                if starts:cancel[p['name']]=min(starts)
        if 'AttackCollision' not in track['name']:continue
        props={p['name']:p for p in track['properties']}
        request=props.get('_RequestSetID',{}).get('keys',[])
        enabled=False
        on_keys=props.get('_On',{}).get('keys',[])
        for index,k in enumerate(on_keys):
            if k['value'] and not enabled:
                req=next((v['value'] for v in reversed(request) if v['frame']<=k['frame']),None)
                if req is not None and req<200: # auxiliary elemental hitboxes are not new swings
                    end=next((v['frame']-1 for v in on_keys[index+1:] if not v['value']),clip['frames'])
                    hits.append({'frame':k['frame'],'endFrame':min(end,clip['frames']),'request':req,'hand':'SHEATH' if dual and req>=100 else 'BLADE',
                                 'body':'Body' in track['name']})
            enabled=k['value']
    # GS charge-level and powered-TCS alternatives are the SAME physical hit.
    if not dual:
        grouped={}
        for hit in hits:grouped.setdefault(hit['frame'],hit)
        hits=list(grouped.values())
    return cancel,sorted(hits,key=lambda h:(h['frame'],h['hand']))

def source_contact(rig,motion,begin,end,hand):
    side='L' if hand=='SHEATH' else 'R'
    def direction(frame):
        _,q=rig.pose(motion,max(0,min(rig.clips[motion]['frames'],frame)))
        return rotate(q[rig.index[side+'_Wep']],(0,0,1))
    # SlashBlade applies one pulse, unlike Wilds' continuous weapon collider.
    # Prefer active forward travel over the almost motionless end of the follow-through.
    def score(frame):
        velocity=sub(direction(frame+1),direction(frame-1))
        return max(0,direction(frame)[2])*math.sqrt(dot(velocity,velocity))
    return max(range(int(begin),int(end)+1),key=score)

def source_roll(rig,motion,frame,hand,dual):
    side='L' if hand=='SHEATH' else 'R'
    directions=[]
    for t in [max(0,frame-3),frame+3]:
        ps,qs=rig.pose(motion,min(t,rig.clips[motion]['frames']))
        directions.append(mc(rotate(qs[rig.index[side+'_Wep']],(0,0,1))))
    # Tangent in the native slash effect's player XY plane: (-cos roll, sin roll).
    v=sub(directions[1],directions[0])
    return round(math.degrees(math.atan2(v[1],-v[0])),3)

# Names are addon input labels. Source clip IDs and event fields remain inspectable.
# These are selected source actions, not an emulation of Wilds' entire executable.
SPECS={
 'greatsword':('gs',0,None),
 'gs_overhead':('gs',213,'_SIDE_SLASH'),
 'gs_charged_slash':('gs',213,'_STORNG_CHARGE'),
 'gs_sweep':('gs',214,'_CHARGE'), 'gs_rising':('gs',215,'_SIDE_SLASH'),
 'gs_strong_sweep':('gs',216,'_SPIRIT_CHARGE'),
 'gs_leaping_sweep':('gs',218,'_ATTACK_TO_WALK'),
 'gs_strong_slash':('gs',237,'_SPIRIT_CHARGE'),
 'gs_true_slash':('gs',248,'_ATTACK_TO_WALK'),
 'gs_aerial':('gs',352,None), 'gs_landing':('gs',354,'_All'),
 'dualblade':('db',200,None),
 'db_blade_cut':('db',250,'_ATTACK_00_COMBO'),
 'db_sheath_cut':('db',251,'_ATTACK_00_COMBO'),
 'db_cross_cut':('db',252,'_ATTACK_00_COMBO'),
 'db_flurry':('db',270,'_ATTACK_00_COMBO'),
 'db_finisher':('db',278,'_ATTACK_01_COMBO'),
 'db_spin':('db',255,'_ATTACK_01_COMBO'),
 'db_dash':('db',253,'_ATTACK_00_COMBO'),
 'db_retreat':('db',259,'_ATTACK_00_COMBO'),
 'db_aerial':('db',280,None),'db_landing':('db',282,'_ATTACK_00_COMBO'),
 'db_sa_prepare':('db',200,None),
}
for stage,motion in [(1,227),(2,238),(3,249)]:
    SPECS[f'gs_charge_{stage}']=('gs',motion,None)
    SPECS[f'gs_sa_prepare_{stage}']=('gs',motion,None)
    SPECS[f'gs_tackle_{stage}']=('gs',217,'_STORNG_CHARGE' if stage==1 else '_SPIRIT_CHARGE')

def generate(only=None):
    paths={'gs':next((ROOT/'.local/wilds/combat').rglob('wp00_00.motlist.992')),
           'db':next((ROOT/'.local/wilds/dualblade').rglob('wp02_00.motlist.992')),
           'sa':next((ROOT/'.local/wilds/switchaxe').rglob('wp08_00.motlist.992'))}
    from switchaxe_source import SPECS as SA_SPECS, sword_start, manifest as sa_manifest, action_rules
    from weapon_components import tracks
    specs = {**SPECS, **SA_SPECS}
    component_path = next((ROOT/'.local/wilds/switchaxe').rglob('wpg08_00.motlist.992'))
    component_rig = Rig(component_path)
    sa_guide = document('actionguidedata_wp08')
    rigs={k:Rig(v) for k,v in paths.items()}
    events={k:{c['motion_id']:c for c in extract(v)} for k,v in paths.items()}
    global_param=document('wp00globalactionparam')
    connects={w:{v['_MotionID']:v for v in document(wp+'_00_mct')['_DataList']}
              for w,wp in [('gs','wp00'),('db','wp02'),('sa','wp08')]}
    charge=global_param['_ChargeParam']
    manifest={'schema':1,'coordinateSystem':'RE Y-up/Z-forward -> Minecraft Y-down/Z-back',
              'greatswordWeaponBasis':{'sourceTip':[0,0,1],'sourceCuttingEdge':[-1,0,0],
                                      'sourceFaceNormal':[0,-1,0],'modelTip':[-1,0,0],
                                      'modelCuttingEdge':[0,-1,0],'modelFaceNormal':[0,0,1]},
              'rootMotionScale':.5,'chargeTicks':[round(v*20) for v in charge['_Time']],
              'overheatTick':round(charge['_OverheatTime']*20),'overheatLevel':charge['_OverheatLevel'],
              'tackleDamageRate':global_param['_TackleHyperArmorDamageReduceRate'],
              'sources':{},'actions':{}}
    if only:
        manifest=json.loads((ASSETS/'motions/source_actions.json').read_text(encoding='utf-8'))
    manifest['switchaxe']=sa_manifest(document('wp08globalactionparam'))
    manifest['sources']['wpg08']={'path':component_path.as_posix().split('/natives/')[1],
        'sha256':hashlib.sha256(component_path.read_bytes()).hexdigest()}
    for k,p in paths.items():
        manifest['sources'][k]={'path':p.as_posix().split('/natives/')[1],
                                 'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'fps':60,'motVersion':932}
    for name,(weapon,motion,cancel_field) in specs.items():
        if only and weapon != only:continue
        rig=rigs[weapon];dual=weapon=='db';clip=events[weapon][motion]
        cancel,hits=event_data(clip,dual)
        idle=name in ['greatsword','dualblade','switchaxe_axe','switchaxe_sword'];hold='charge_' in name or 'sa_prepare' in name
        duration=60 if hold else math.ceil(clip['frames']/6)*2
        duration=max(2,duration)
        cancel_frame=cancel.get(cancel_field,clip['frames']) if cancel_field else clip['frames']
        metadata={'motionId':motion,'source':weapon,'sourceName':clip['name'],'sourceFrames':clip['frames'],
                  'duration':duration,'cancelTick':math.ceil(cancel_frame/3),'cancelField':cancel_field,
                  'cancelFrames':cancel,'hits':[],'root':[],'samples':[]}
        connect=connects[weapon].get(motion,{})
        defaults=connect.get('_ChangeParamList',[])
        metadata['blendTicks']=math.ceil(defaults[0]['_InterpolationFrame']/3) if defaults else 0
        metadata['sourceConnections']=connect.get('_LinkMotionList',[])
        metadata['hyperArmorEndTick']=0
        for track in clip['events']:
            if track['name']!='app.motion_track.HunterBasic':continue
            for prop in track['properties']:
                if prop['name']!='_HyperArmor':continue
                ks=prop.get('keys',[])
                if ks and ks[0]['frame']==0 and ks[0]['value']:
                    end=next((k['frame'] for k in ks if not k['value']),prop['end'])
                    metadata['hyperArmorEndTick']=math.ceil(end/3)
        if weapon=='sa':
            metadata.update(action_rules(name, sa_guide))
            # Mode changes commit at source WeaponStateMotionID keys. Discharges
            # return to axe at their extracted next-action window.
            metadata['modeChangeTick']=math.ceil(cancel_frame/3) if name in ['sa_discharge','sa_full_release'] else -1
            if name in ['sa_morph_sword','sa_morph_axe']:
                for track in clip['events']:
                    if not track['name'].endswith('.WeaponVisual'):continue
                    for prop in track['properties']:
                        if prop['name']=='_WeaponStateMotionID':
                            keys=prop.get('keys',[])
                            change=next(k for k in keys if k['value'] != keys[0]['value'])
                            metadata['modeChangeTick']=math.ceil(change['frame']/3)
                    break
            metadata['modeAfter']='sword' if name=='sa_morph_sword' else 'axe' if name in ['sa_morph_axe','sa_discharge','sa_full_release'] else None
        if hold or idle:hits=[];metadata['cancelTick']=0
        for h in hits:
            contact=h['frame'] if h['body'] else source_contact(rig,motion,h['frame'],h['endFrame'],h['hand'])
            tick=max(1,min(math.floor(h['endFrame']/3),max(math.ceil(h['frame']/3),round(contact/3))))
            metadata['hits'].append({**h,'contactFrame':contact,'tick':tick,
                                    'pitch':0,
                                    'roll':0 if h['body'] else source_roll(rig,motion,tick*3,h['hand'],dual)})
        metadata['hits'].sort(key=lambda h:(h['tick'],h['hand']))
        keys=[];previous={}
        for tick in range(duration+1):
            actual=motion;frame=min(tick*3,clip['frames'])
            if hold:
                level=sum(tick>=t for t in manifest['chargeTicks'][1:])
                if weapon=='gs':
                    actual=motion+max(0,level-1)
                    frame=(tick*3)%rig.clips[actual]['frames']
                else:frame=(tick*3)%clip['frames']
            p=pose(rig,actual,frame,dual,idle=idle)
            if weapon=='sa':p.update(tracks(component_rig,clip,frame,sword_start(name)))
            for bone,axes in p.items():
                angles=continuous(quat(axes),previous.get(bone));axes.update(angles);previous[bone]=dict(axes)
            keys.append((tick,p))
            pos,_=rig.pose(actual,frame)
            root=pos[rig.index['root']]
            metadata['root'].append([round(root[0]*.5,6),round(root[2]*.5,6)] if not hold and not idle else [0,0])
            metadata['samples'].append([actual,round(frame,4)])
        moves=[{'tick':t,'easing':'linear',**{k:v for k,v in p.items() if not k.startswith('_')},
                'rightWeapon':p['_rightWeaponRotation'],'leftWeapon':p['_leftWeaponRotation']} for t,p in keys]
        for hit in metadata['hits']:
            if hit['body']:continue
            if weapon=='sa':
                # A continuous source collider spans several frames. Select a
                # discrete Minecraft contact with the cutting edge leading, not
                # a wrist turn at the start/end of the same valid source window.
                sword_hit=name.startswith('sa_sword_') or name in ['sa_morph_sword','sa_discharge','sa_full_release']
                def cutting_contact(t):
                    directions=[]
                    for at in [max(0,t-.75),min(duration,t+.75)]:
                        p=sample(keys,at);q=quat(p['body']);q=(-q[0],-q[1],q[2],q[3])
                        directions.append(rotate(mul(q,quat(p['_rightWeaponRotation'])),(-1,0,0)))
                    travel=unit(sub(directions[1],directions[0]));p=sample(keys,t)
                    q=quat(p['body']);q=(-q[0],-q[1],q[2],q[3])
                    q=mul(mul(q,quat(p['_rightWeaponRotation'])),quat(p['swordComponent' if sword_hit else 'axeComponent']))
                    return dot(travel,rotate(q,(0,1 if sword_hit else -1,0)))>.9 and abs(dot(travel,rotate(q,(0,0,1))))<.4
                candidates=[t for t in range(max(1,math.ceil(hit['frame']/3)),min(duration,math.floor(hit['endFrame']/3))+1)
                            if cutting_contact(t)]
                if not candidates:raise ValueError(f'No edge-leading contact inside source window: {name}')
                tick=min(candidates,key=lambda t:abs(t-hit['tick']))
                if tick!=hit['tick']:
                    hit['originalContactFrame']=hit['contactFrame']
                    hit['contactFrame']=tick*3;hit['tick']=tick
                    hit['retargetContactAdjustment']='nearest edge-leading frame within original active collision window'
            directions=[]
            for t in [max(0,hit['tick']-.75),min(duration,hit['tick']+.75)]:
                p=sample(keys,t);root=quat(p['body']);root=(-root[0],-root[1],root[2],root[3])
                weapon_rotation=quat(p['_leftWeaponRotation' if hit['hand']=='SHEATH' else '_rightWeaponRotation'])
                directions.append(rotate(mul(root,weapon_rotation),(-1,0,0)))
            normal=unit(cross(directions[0],directions[1]))
            hit['sourceRoll']=hit['roll']
            hit['roll']=round(math.degrees(math.atan2(normal[0],math.hypot(normal[1],normal[2]))),3)
            hit['pitch']=round(math.degrees(math.atan2(-normal[2],normal[1])),3)
        data={'version':3,'name':name,'author':'NeoMotions',
              'comment':'Retargeted from local Wilds MOT.932; rigid-limb/handle adaptation. See source_actions.json.',
              'neomotions':{'source':weapon,'motionId':motion,'states':[{'tick':0,'state':'source_motion'}]},
              'emote':{'beginTick':0,'endTick':duration,'stopTick':duration+2,'isLoop':idle,
                       'returnTick':0,'degrees':True,'moves':moves}}
        (ASSETS/'player_animation'/f'{name}.json').write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
        # 4x timeline / 4x native playback = effective 120 Hz, same real duration.
        (ASSETS/'motions'/f'{name}.vmd').write_bytes(vmd_bytes(keys,duration,dual,playback_speed=4,component='axeComponent' if weapon=='sa' else None))
        manifest['actions'][name]=metadata
        print(name, motion,'ticks',duration,'hits',[(h['tick'],h['hand']) for h in metadata['hits']])
    # USER provenance includes charge thresholds, action declarations and transition arguments.
    for n in ['wp00globalactionparam','wp00_actionid','wp00_actionparam','wp00_pack','wp00_00_mct',
              'wp02_actionid','wp02_actionparam','wp02_pack','wp02_00_mct',
              'actionguidedata_wp08','actionguidedata_wp08name','wp08globalactionparam','wp08_actionid','wp08_actionparam','wp08_pack','wp08_00_mct','wpg08_00_mct']:
        d=json.loads((RSZ/(n+'.user.3.json')).read_text(encoding='utf-8'))
        manifest['sources'][n]={'path':d['source'].replace('\\','/').split('/natives/')[1],'sha256':d['sha256']}
    (ASSETS/'motions/source_actions.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    print('Wrote',len(specs),'source-derived animation pairs and runtime event manifest.')

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--weapon', choices=['gs','db','sa'])
    generate(parser.parse_args().weapon)
