"""Experimental local MOT.932 -> rigid Minecraft humanoid Player Animator JSON.

Decodes rotation tracks, composes the 197-bone rest hierarchy, fits straight Minecraft
limbs to shoulder->hand and thigh->ankle vectors, and samples at 20 Hz. Translation,
root motion, fingers, scale and weapon IK are intentionally not transferred. The
result is a local review asset, not a promise of visually accurate retargeting.
Use --clip <motion_id>; outputs remain in .local unless --output is supplied.
"""
import argparse
import bisect
import json
import math
from pathlib import Path
import struct
from inspect_motlist import inspect
from generate_animations import mul, rotate, vmd_bytes


def normalized(q):
    length=math.sqrt(sum(v*v for v in q))
    if not math.isfinite(length) or length<1e-8: raise ValueError('Invalid quaternion')
    return tuple(v/length for v in q)


def inverse(q): return (-q[0],-q[1],-q[2],q[3])


def slerp(a,b,t):
    dot=sum(x*y for x,y in zip(a,b))
    if dot<0: b=tuple(-x for x in b);dot=-dot
    if dot>.9995: return normalized(tuple(x*(1-t)+y*t for x,y in zip(a,b)))
    angle=math.acos(min(1,dot));s=math.sin(angle)
    return tuple((x*math.sin((1-t)*angle)+y*math.sin(t*angle))/s for x,y in zip(a,b))


def decode_rotation(data,offset,compression,unpack):
    def read(fmt): return struct.unpack_from('<'+fmt,data,offset)
    # Full / three-component / single-axis float encodings.
    if compression==0: return normalized(read('4f')),16
    if compression in (0xb0000,0xc0000): xyz=read('3f');size=12
    elif compression in (0x41000,0x42000,0x43000):
        xyz=[0.,0.,0.];xyz[(compression>>12)-0x41]=read('f')[0];size=4
    elif compression in (0x21000,0x22000,0x23000):
        xyz=[0.,0.,0.];xyz[(compression>>12)-0x21]=unpack[0]*read('H')[0]/65535+unpack[1];size=2
    elif compression in (0x20000,0x30000,0x40000,0x50000,0x60000,0x70000,0x80000):
        size,bits,endian={0x20000:(2,5,'little'),0x30000:(3,8,'little'),0x40000:(4,10,'little'),
                          0x50000:(5,13,'big'),0x60000:(6,16,'big'),0x70000:(7,18,'big'),
                          0x80000:(8,21,'little')}[compression]
        packed=int.from_bytes(data[offset:offset+size],endian);mask=(1<<bits)-1
        xyz=[unpack[i]*((packed>>(bits*i))&mask)/mask+unpack[4+i] for i in range(3)]
    else: raise ValueError(f'Unsupported rotation encoding {compression:#x}')
    return normalized((*xyz,math.sqrt(max(0,1-sum(x*x for x in xyz))))),size


def read_tracks(data,clip):
    base=clip['offset'];header=struct.unpack_from('<Q',data,base+24)[0];tracks={}
    for i in range(clip['tracks']):
        bone,flags,bone_hash,pointer=struct.unpack_from('<HHII',data,base+header+12*i)
        for channel in range(3):
            if not flags&(1<<channel): continue
            track_flags,count,times,values,unpack=struct.unpack_from('<5I',data,base+pointer);pointer+=20
            if channel!=1: continue
            index_format='I' if track_flags>>20==5 else 'B' if track_flags>>20==2 else 'H'
            key_times=list(struct.unpack_from('<'+index_format*count,data,base+times)) if times else [0]*count
            limits=struct.unpack_from('<8f',data,base+unpack) if unpack else (0,)*8
            key_values=[];cursor=base+values
            for k in range(count):
                value,size=decode_rotation(data,cursor,track_flags&0xff000,limits)
                key_values.append(value);cursor+=size
            if count: tracks[bone_hash]=(key_times,key_values)
    return tracks


def at(track,frame):
    times,values=track
    j=bisect.bisect_right(times,frame)
    if j==0:return values[0]
    if j>=len(times):return values[-1]
    return slerp(values[j-1],values[j],(frame-times[j-1])/max(1,times[j]-times[j-1]))


def forward(bones,tracks,frame):
    positions=[];rotations=[]
    for i,bone in enumerate(bones):
        local=at(tracks[bone['hash']],frame) if bone['hash'] in tracks else normalized(bone['rotation_xyzw'])
        parent=bone['parent'];position=bone['position'][:3]
        if parent>=i:raise ValueError('Unexpected non-topological skeleton')
        if parent>=0:
            position=tuple(a+b for a,b in zip(positions[parent],rotate(rotations[parent],position)))
            local=mul(rotations[parent],local)
        positions.append(position);rotations.append(local)
    return positions,rotations


def direction_quat(start,end):
    # RE: Y up, Z forward -> Minecraft model: Y down, Z backward.
    d=[end[0]-start[0],start[1]-end[1],start[2]-end[2]]
    length=math.sqrt(sum(x*x for x in d))
    if length<1e-8:return (0,0,0,1)
    x,y,z=[v/length for v in d]
    if y<-.99999:return (1,0,0,0)
    return normalized((z,0,-x,1+y))


def euler(q):
    x,y,z,w=normalized(q)
    return dict(pitch=math.degrees(math.atan2(2*(w*x+y*z),1-2*(x*x+y*y))),
                yaw=math.degrees(math.asin(max(-1,min(1,2*(w*y-z*x))))),
                roll=math.degrees(math.atan2(2*(w*z+x*y),1-2*(y*y+z*z))))


def retarget(path,output,clip_ids):
    inventory=inspect(path);data=path.read_bytes()
    if not inventory['skeletons']:raise ValueError('No embedded rest skeleton')
    bones=inventory['skeletons'][0]['bones'];index={b['name']:i for i,b in enumerate(bones)}
    pairs={'rightArm':('R_UpperArm','R_Hand'),'leftArm':('L_UpperArm','L_Hand'),
           'rightLeg':('R_Thigh','R_Foot'),'leftLeg':('L_Thigh','L_Foot')}
    for start,end in pairs.values():
        if start not in index or end not in index:raise ValueError(f'Missing skeleton landmarks: {start}/{end}')
    report=[]
    for clip in inventory['clips']:
        if clip_ids and clip['motion_id'] not in clip_ids:continue
        if clip['mot_version']!=932 or clip['fps']<=0:raise ValueError('Only MOT.932 with a valid sample rate is supported')
        tracks=read_tracks(data,clip)
        duration=max(2,math.ceil(clip['frames']/clip['fps']*20))
        name=clip['name'].lower();keys=[]
        for tick in range(duration+1):
            positions,rotations=forward(bones,tracks,min(clip['frames'],tick*clip['fps']/20))
            pose={'body':euler((0,0,0,1)),'torso':euler((0,0,0,1)),'head':euler((0,0,0,1))}
            for target,(start,end) in pairs.items():pose[target]=euler(direction_quat(positions[index[start]],positions[index[end]]))
            # Preserve continuous Euler tracks across the -180/180 seam.
            if keys:
                for bone,axes in pose.items():
                    for axis,value in axes.items():
                        previous=keys[-1][1][bone][axis]
                        axes[axis]=previous+(value-previous+180)%360-180
            keys.append((tick,pose))
        result={'version':3,'name':name,'author':'Local Wilds conversion',
                'comment':'Experimental straight-limb retarget; root/torso/IK not validated.',
                'emote':{'beginTick':0,'endTick':duration,'stopTick':duration+2,'degrees':True,
                         'moves':[{'tick':t,'easing':'linear',**p} for t,p in keys]}}
        json_path=output/'player_animation'/f'{name}.json';json_path.parent.mkdir(parents=True,exist_ok=True)
        json_path.write_text(json.dumps(result,indent=2),encoding='utf-8')
        vmd_path=output/'motions'/f'{name}.vmd';vmd_path.parent.mkdir(parents=True,exist_ok=True)
        vmd_path.write_bytes(vmd_bytes(keys,duration))
        report.append({'motion_id':clip['motion_id'],'name':name,'duration_ticks':duration,'rotation_tracks':len(tracks)})
    if not report:raise ValueError('No matching clips')
    (output/'conversion-report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(f'Converted {len(report)} clips into Player Animator JSON + VMD at {output}')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input',type=Path)
    parser.add_argument('--output',type=Path,default=Path('.local/wilds/converted'))
    parser.add_argument('--clip',type=int,action='append',help='Repeat for multiple motion IDs; omit for all clips')
    args=parser.parse_args();retarget(args.input,args.output,args.clip)
