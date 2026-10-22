"""Complete skeletal sampling of local MOT.932: position, rotation and scale.
Encoding reference: AzurieWolf/fmt_RE_MESH-Noesis-Plugin-AzurieWolf.
"""
import bisect
import math
from pathlib import Path
import struct
from inspect_motlist import inspect
from retarget_wilds import decode_rotation,normalized,slerp
from generate_animations import mul,rotate,add

def vector(data,offset,code,u):
    def read(fmt):return struct.unpack_from('<'+fmt,data,offset)
    if code==0:return read('3f'),12
    if code in (0x41000,0x42000,0x43000):
        v=list(u[:3]);v[(code>>12)-0x41]=read('f')[0];return v,4
    if code==0x44000:return (read('f')[0],)*3,4
    if code in (0x85000,0x86000,0x87000):
        v=list(u[:3]);axes={0x85000:(0,1),0x86000:(1,2),0x87000:(2,0)}[code]
        for axis,value in zip(axes,read('2f')):v[axis]=value
        return v,8
    if code in (0x21000,0x22000,0x23000,0x24000,0x31000,0x32000,0x33000):
        size=2 if code<0x30000 else 3;v=list(u[1:4]);axis=(code>>12)%16-1
        val=int.from_bytes(data[offset:offset+size],'little' if size==2 else 'big')/((1<<(8*size))-1)*u[0]
        if axis==3:return (val+u[3],)*3,size
        v[axis]+=val;return v,size
    if code&0xffff==0:
        size,bits,endian={0x20000:(2,5,'little'),0x30000:(3,8,'little'),0x40000:(4,10,'little'),
                          0x50000:(5,13,'big'),0x60000:(6,16,'big'),0x70000:(7,18,'big'),0x80000:(8,21,'little')}[code]
        packed=int.from_bytes(data[offset:offset+size],endian);mask=(1<<bits)-1
        return [u[i]*((packed>>(bits*i))&mask)/mask+u[3+i] for i in range(3)],size
    family=code>>16;variant=(code>>12)&15
    if family in (2,3,4,5,6) and variant in (5,6,7):
        size=family;bits=family*4;mask=(1<<bits)-1
        packed=int.from_bytes(data[offset:offset+size],'little' if family in (2,4) else 'big')
        values=[((packed>>(bits*i))&mask)/mask for i in range(2)]
        v=list(u[2:5])
        axes=({5:(1,2),6:(0,2),7:(0,1)} if family==3 else
              {5:(0,1),6:(0,2),7:(1,2)} if family in (2,4) else {5:(0,1),6:(1,2),7:(2,0)})[variant]
        for i,axis in enumerate(axes):v[axis]+=u[i]*values[i]
        return v,size
    raise ValueError(f'Unsupported vector encoding {code:#x}')

def tracks(data,clip):
    base=clip['offset'];header=struct.unpack_from('<Q',data,base+24)[0];result={}
    for i in range(clip['tracks']):
        _,flags,bone_hash,pointer=struct.unpack_from('<HHII',data,base+header+12*i)
        for channel in range(3):
            if not flags&(1<<channel):continue
            tf,count,times,values,unpack=struct.unpack_from('<5I',data,base+pointer);pointer+=20
            fmt='I' if tf>>20==5 else 'B' if tf>>20==2 else 'H'
            ts=list(struct.unpack_from('<'+fmt*count,data,base+times)) if times else [0]*count
            u=struct.unpack_from('<8f',data,base+unpack) if unpack else (0,)*8
            vs=[];cursor=base+values
            for k in range(count):
                v,size=(decode_rotation if channel==1 else vector)(data,cursor,tf&0xff000,u);vs.append(v);cursor+=size
            if count:result[bone_hash,channel]=(ts,vs)
    return result

def sample(track,frame,rotation=False):
    times,values=track;j=bisect.bisect_right(times,frame)
    if j==0:return values[0]
    if j>=len(times):return values[-1]
    f=(frame-times[j-1])/max(1,times[j]-times[j-1])
    return slerp(values[j-1],values[j],f) if rotation else tuple(a*(1-f)+b*f for a,b in zip(values[j-1],values[j]))

class Rig:
    def __init__(self,path):
        self.path=Path(path);self.data=self.path.read_bytes();self.inventory=inspect(self.path)
        self.bones=self.inventory['skeletons'][0]['bones'];self.index={b['name']:i for i,b in enumerate(self.bones)}
        self.clips={c['motion_id']:c for c in self.inventory['clips']};self.cache={}
    def pose(self,motion,frame):
        if motion not in self.cache:self.cache[motion]=tracks(self.data,self.clips[motion])
        return self.forward(self.cache[motion],frame)
    def forward(self,channels,frame):
        positions=[];rotations=[];scales=[]
        for i,b in enumerate(self.bones):
            def get(c,default):return sample(channels[b['hash'],c],frame,c==1) if (b['hash'],c) in channels else default
            p=get(0,b['position'][:3]);r=get(1,normalized(b['rotation_xyzw']));s=get(2,(1,1,1));parent=b['parent']
            if parent>=i:raise ValueError('Skeleton is not topological')
            if parent>=0:
                p=add(positions[parent],rotate(rotations[parent],tuple(a*b for a,b in zip(p,scales[parent]))))
                r=mul(rotations[parent],r);s=tuple(a*b for a,b in zip(s,scales[parent]))
            if not all(math.isfinite(x) for x in (*p,*r,*s)):raise ValueError('Nonfinite pose')
            positions.append(p);rotations.append(r);scales.append(s)
        return positions,rotations
