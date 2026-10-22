"""Player pixels: +Y down, forward -Z. OBJ tip: -X. Arms are torso siblings.
Native renderer is T(0,24,0) * 2 * My * VMD * Mx, in player pixels.
Original Monster Hunter inspired poses, not converted game motion data.
"""
import json
import math
from pathlib import Path
import struct
from lower_body import footwork, greatsword_keys

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/slashblade_neomotions'

def rotation(pitch=0, yaw=0, roll=0):
    return dict(pitch=pitch, yaw=yaw, roll=roll)

def quat(euler):
    x,y,z = [math.radians(euler.get(k,0))/2 for k in ('pitch','yaw','roll')]
    sx,cx,sy,cy,sz,cz = math.sin(x),math.cos(x),math.sin(y),math.cos(y),math.sin(z),math.cos(z)
    return (sx*cy*cz-cx*sy*sz,cx*sy*cz+sx*cy*sz,cx*cy*sz-sx*sy*cz,cx*cy*cz+sx*sy*sz)

def mul(a,b):
    ax,ay,az,aw=a;bx,by,bz,bw=b
    return (aw*bx+ax*bw+ay*bz-az*by,aw*by-ax*bz+ay*bw+az*bx,
            aw*bz+ax*by-ay*bx+az*bw,aw*bw-ax*bx-ay*by-az*bz)

def rotate(q,v):
    return mul(mul(q,(*v,0)),(-q[0],-q[1],-q[2],q[3]))[:3]

def add(a,b): return tuple(x+y for x,y in zip(a,b))
def sub(a,b): return tuple(x-y for x,y in zip(a,b))
def scale(a,s): return tuple(x*s for x in a)
def dot(a,b): return sum(x*y for x,y in zip(a,b))
def cross(a,b): return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
def unit(v): return scale(v,1/math.sqrt(dot(v,v)))

def matrix_quat(x,y,z):
    m=[[x[i],y[i],z[i]] for i in range(3)]
    trace=sum(m[i][i] for i in range(3))
    if trace>0:
        s=math.sqrt(trace+1)*2
        return ((m[2][1]-m[1][2])/s,(m[0][2]-m[2][0])/s,(m[1][0]-m[0][1])/s,s/4)
    i=max(range(3),key=lambda j:m[j][j]);j=(i+1)%3;k=(i+2)%3
    s=math.sqrt(max(0,1+m[i][i]-m[j][j]-m[k][k]))*2
    v=[0.,0.,0.];v[i]=s/4;v[j]=(m[i][j]+m[j][i])/s;v[k]=(m[i][k]+m[k][i])/s
    return (*v,(m[k][j]-m[j][k])/s)

def blade_direction(elevation,azimuth):
    e,a=map(math.radians,(elevation,azimuth))
    return (math.cos(e)*math.sin(a),-math.sin(e),-math.cos(e)*math.cos(a))

def weapon_quat(elevation,azimuth,flat=0):
    d=blade_direction(elevation,azimuth);a=math.radians(azimuth)
    normal=add(scale((math.cos(a),0,math.sin(a)),1-flat),scale((0,-1,0),flat))
    z=unit(sub(normal,scale(d,dot(normal,d))));x=scale(d,-1);y=cross(z,x)
    return matrix_quat(x,y,z)

def slash_quat(swing,plane,root_yaw=0,follow=0):
    """Native slash plane, expressed in player-model space; +Y is down.
    Compensate torso/root turning so a diagonal cut stays in its declared plane.
    Horizontal spins deliberately follow the whole-body turn.
    """
    angle,roll=map(math.radians,(swing,plane))
    tangent=(-math.cos(roll),math.sin(roll),0)
    direction=add(scale(tangent,math.sin(angle)),(0,0,-math.cos(angle)))
    normal=(math.sin(roll),math.cos(roll),0)
    x=scale(direction,-1)
    q=matrix_quat(x,cross(normal,x),normal)
    return mul(quat(rotation(0,-root_yaw*(1-follow),0)),q)

def greatsword_pose(elevation=55,azimuth=0,gx=-.8,gy=8,gz=-6,lean=8,twist=0,flat=0):
    controls=dict(elevation=elevation,azimuth=azimuth,gx=gx,gy=gy,gz=gz,lean=lean,twist=twist,flat=flat)
    d=blade_direction(elevation,azimuth)
    # Intersect two nine-pixel reach spheres, with hands three pixels apart on the handle.
    right=(-5,2,0);left=(5,2,0);separation=3
    second=add(left,scale(d,separation));normal=unit(sub(second,right));center=scale(add(second,right),.5)
    projected=sub((gx,gy,gz),center);projected=sub(projected,scale(normal,dot(projected,normal)))
    radius=math.sqrt(81-dot(sub(second,right),sub(second,right))/4)
    grip=add(center,scale(unit(projected),radius));offhand=sub(grip,scale(d,separation))
    def arm(shoulder,hand):
        v=scale(sub(hand,shoulder),1/9)
        return rotation(-math.degrees(math.acos(max(-1,min(1,v[1])))),math.degrees(math.atan2(-v[0],-v[2])),0)
    return {'body':rotation(0,twist,0),'torso':rotation(lean),'head':rotation(-lean/3,-twist/2),
            'rightArm':arm(right,grip),'leftArm':arm(left,offhand),'_controls':controls}

READY=greatsword_pose(115,65,gx=-2,gy=8,gz=-6,lean=0,twist=-8)
WINDUP=greatsword_pose(145,gy=-4,gz=-3,lean=-10)
HIT=greatsword_pose(-25,gy=7,gz=-6,lean=28)
RECOVER=greatsword_pose(-10,gy=8,gz=-5,lean=18)

def clips():
    result={'greatsword':(2,[(0,READY),(2,READY)])}
    for name,duration,hit,charged in [('gs_overhead',24,10,False),('gs_charged_slash',28,12,True),('gs_strong_slash',32,14,True)]:
        result[name]=duration,[(0,WINDUP if charged else READY),(max(3,hit-5),WINDUP),
                              (hit-2,greatsword_pose(75,gy=1,gz=-7)),(hit,HIT),(hit+3,RECOVER),(duration,READY)]
    result['gs_sweep']=26,[(0,READY),(6,greatsword_pose(0,-85,gy=6,flat=1,twist=-15)),
                          (9,greatsword_pose(0,-30,gy=6,flat=1)),(11,greatsword_pose(0,25,gy=6,flat=1,twist=10)),
                          (15,greatsword_pose(0,85,gy=6,flat=1,twist=20)),(26,READY)]
    result['gs_rising']=28,[(0,READY),(6,greatsword_pose(-25,gy=9,lean=25)),
                           (10,greatsword_pose(15,gy=7)),(12,greatsword_pose(85,gy=0,lean=-10)),(18,WINDUP),(28,READY)]
    for stage in (1,2,3):
        result[f'gs_charge_{stage}']=60,[(0,READY),(5,WINDUP),(16,WINDUP),(32,WINDUP),(60,WINDUP)]
        result[f'gs_tackle_{stage}']=12,[(0,WINDUP),(3,greatsword_pose(65,-45,lean=35,twist=-15)),
                                       (5,greatsword_pose(65,-35,lean=40,twist=-10)),(12,WINDUP)]
        result[f'gs_sa_prepare_{stage}']=60,[(0,WINDUP),(60,WINDUP)]
    result['gs_true_slash']=44,[(0,WINDUP),(7,greatsword_pose(65,gy=0)),(10,HIT),
                               (15,greatsword_pose(25,gy=7,lean=15)),(21,WINDUP),
                               (24,greatsword_pose(75,gy=1,gz=-7)),(26,greatsword_pose(-25,gy=7,lean=38)),
                               (31,RECOVER),(44,READY)]
    result['gs_aerial']=36,[(0,WINDUP),(6,greatsword_pose(70,gy=1)),(8,HIT),(36,HIT)]
    result['gs_landing']=18,[(0,HIT),(3,HIT),(9,RECOVER),(18,READY)]
    # Pose direction changes land on common 20/30-Hz samples (two game ticks).
    # A sharp reversal at an odd tick cannot be represented at an integer VMD frame.
    return {name:(duration,[((tick+1)//2*2,p) for tick,p in keys]) for name,(duration,keys) in result.items()}

def sample(keys,tick):
    before,after=keys[0],keys[-1]
    for a,b in zip(keys,keys[1:]):
        if a[0]<=tick<=b[0]: before,after=a,b;break
    f=max(0,min(1,(tick-before[0])/max(1,after[0]-before[0])))
    if '_rightWeaponRotation' in before[1]:
        from retarget_wilds import slerp,euler
        result={}
        for bone,a in before[1].items():
            b=after[1][bone]
            result[bone]={axis:a[axis]*(1-f)+b[axis]*f for axis in a}
            result[bone].update(euler(slerp(quat(a),quat(b),f)))
        return result
    if '_controls' in before[1]:
        controls={k:before[1]['_controls'][k]*(1-f)+after[1]['_controls'][k]*f for k in before[1]['_controls']}
        return greatsword_pose(**controls)
    return {bone:{axis:before[1][bone][axis]*(1-f)+after[1][bone][axis]*f for axis in before[1][bone]} for bone in before[1]}

def native_transform(position,q):
    # Both renderer reflections, including the formerly missing half-turn around Z.
    native_q=mul((-q[0],q[1],-q[2],q[3]),(0,0,1,0))
    return (position[0]/2,12-position[1]/2,position[2]/2),native_q

def anchor_upper_body(p):
    """ModelParts are siblings. Rotate the torso about the hips, then carry its
    neck and shoulder pivots explicitly; neither hip may detach during a lean.
    Arm rotations and weapon swing planes stay in model space.
    """
    torso=quat(p['torso']);hip=(0,12,0)
    origin=sub(hip,rotate(torso,hip))
    p['torso'].update(dict(zip('xyz',origin)))
    p['head'].update(dict(zip('xyz',origin)))
    for left in (False,True):
        shoulder=add(origin,rotate(torso,(5 if left else -5,2,0)))
        p['leftArm' if left else 'rightArm'].update(dict(zip('xyz',shoulder)))
    return p

def hand_position(p,left=False):
    arm=p['leftArm' if left else 'rightArm']
    shoulder=(arm.get('x',5 if left else -5),arm.get('y',2),arm.get('z',0))
    return add(shoulder,rotate(quat(arm),(0,9,0)))

def hand_transform(p,left=False):
    source_rotation=p.get('_leftWeaponRotation' if left else '_rightWeaponRotation')
    if source_rotation is not None:
        return native_transform(hand_position(p,left),quat(source_rotation))
    weapon=p.get('_leftWeapon' if left else '_rightWeapon')
    if weapon is not None:
        q=slash_quat(**weapon,root_yaw=p['body']['yaw']) if 'plane' in weapon else weapon_quat(weapon['elevation'],weapon['azimuth'],weapon['flat'])
    elif ('_controls' in p or '_weapon' in p) and not left:
        c=p.get('_controls',p.get('_weapon'));q=weapon_quat(c['elevation'],c['azimuth'],c['flat'])
    else:
        q=mul(quat(p['leftArm' if left else 'rightArm']),quat(rotation(0,-90,0)))
    return native_transform(hand_position(p,left),q)

def blade_transform(p): return hand_transform(p)

def back_transform(p):
    torso=quat(p['torso'])
    origin=tuple(p['torso'].get(k,0) for k in 'xyz')
    return native_transform(add(origin,rotate(torso,(4,-1,3.5))),mul(torso,quat(rotation(0,0,-56.7))))

def vmd_bytes(keys,duration,dual=False,playback_speed=1,component=None):
    def secondary(p):
        if component is None:return hand_transform(p,True) if dual else back_transform(p)
        part=p[component];q=quat(p['_rightWeaponRotation'])
        position=add(hand_position(p),rotate(q,scale(tuple(part[k] for k in 'xyz'),.125)))
        return native_transform(position,mul(q,quat(part)))
    frames=[]
    for frame in range(duration*3*playback_speed//2+1):
        p=sample(keys,frame*2/(3*playback_speed))
        frames.append(('hardpointA',frame,*hand_transform(p)))
        frames.append(('hardpointB',frame,*secondary(p)))
    # Preserve every 20-Hz pose exactly through the native player's 30-Hz linear
    # position / normalized quaternion interpolation. Odd game ticks lie halfway
    # between two VMD samples; shift that pair equally to reproduce the real pose.
    for tick in (range(1,duration,2) if playback_speed==1 else []):
        p=sample(keys,tick)
        desired=[hand_transform(p),secondary(p)]
        for bone,(target_position,target_q) in enumerate(desired):
            lower=int(tick*1.5)*2+bone;upper=lower+2
            a,b=frames[lower],frames[upper]
            offset=sub(target_position,scale(add(a[2],b[2]),.5))
            q2=scale(b[3],-1) if dot(a[3],b[3])<0 else b[3]
            middle=unit(add(a[3],q2))
            correction=mul(target_q,(-middle[0],-middle[1],-middle[2],middle[3]))
            frames[lower]=(a[0],a[1],add(a[2],offset),mul(correction,a[3]))
            frames[upper]=(b[0],b[1],add(b[2],offset),mul(correction,b[3]))
    out=bytearray(b'Vocaloid Motion Data 0002'.ljust(30,b'\0')+b'NeoMotions'.ljust(20,b'\0'))
    out+=struct.pack('<I',len(frames));interpolation=bytes([20]*8+[107]*8)*4
    for bone,frame,pos,q in frames:
        out+=bone.encode('shift_jis').ljust(15,b'\0')+struct.pack('<I3f4f',frame,*pos,*q)+interpolation
    return out+struct.pack('<5I',0,0,0,0,0)

def generate():
    for name,(duration,keys) in clips().items():
        idle=name=='greatsword';moves=[];sampled=[]
        legs=greatsword_keys(name,duration)
        for tick in range(duration+1):
            p=anchor_upper_body(sample(keys,tick))
            p.update(footwork(legs,tick,airborne=name=='gs_aerial'))
            controls=p.pop('_controls')
            p['_weapon']={key:controls[key] for key in ('elevation','azimuth','flat')}
            sampled.append((tick,p))
            moves.append({'tick':tick,'easing':'linear',**{k:v for k,v in p.items()
                          if not k.startswith('_') and (not idle or k in ('body','torso','rightArm','leftArm'))}})
        animation={'version':3,'name':name,'author':'NeoMotions',
                   'neomotions':legs.metadata() if legs else {'states':[{'tick':0,'state':'locomotion'}]},
                   'comment':'Original two-hand reach-constrained poses; tools/generate_animations.py',
                   'emote':{'beginTick':0,'endTick':duration,'stopTick':duration+2,'isLoop':idle,
                            'returnTick':0,'degrees':True,'moves':moves}}
        (ASSETS/'player_animation'/f'{name}.json').write_text(json.dumps(animation,indent=2)+'\n',encoding='utf-8')
        # Sample the actual 20-Hz arm Euler keys, including their interpolation,
        # rather than a separate higher-rate IK solution the player never renders.
        (ASSETS/'motions'/f'{name}.vmd').write_bytes(vmd_bytes(sampled,duration))
    print(f'Generated {len(clips())} greatsword poses with hand-aligned blade and back-mounted sheath.')

if __name__=='__main__':
    # Production assets must always be regenerated from the locally extracted source.
    from retarget_source_actions import generate as generate_source
    generate_source()
