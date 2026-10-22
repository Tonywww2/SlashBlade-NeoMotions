"""Inventory Wilds MOTLIST.992 clips and export embedded rest skeletons.

Format reference: AzurieWolf/fmt_RE_MESH-Noesis-Plugin-AzurieWolf,
motlistFile / motFile.readBoneHeaders. This reader does not decode compressed tracks.
"""
import argparse
import json
from pathlib import Path
import struct


def inspect(path):
    data=path.read_bytes()
    def read(fmt,at): return struct.unpack_from('<'+fmt,data,at)
    def u16text(at):
        end=at
        while end+2<=len(data) and data[end:end+2]!=b'\0\0': end+=2
        return data[at:end].decode('utf-16-le')
    version,=read('I',0)
    if version!=992: raise ValueError(f'Expected MOTLIST.992, found {version}')
    pointers,ids,name=read('3Q',16)
    count,=read('I',48)
    result={'source':str(path),'version':version,'name':u16text(name),'clips':[],'skeletons':[]}
    seen=set()
    for i in range(count):
        base,=read('Q',pointers+i*8)
        if not base or base in seen: continue
        seen.add(base)
        if data[base+4:base+8]!=b'mot ': continue
        mot_version,=read('I',base)
        if mot_version<456: raise ValueError(f'Unsupported MOT version {mot_version}')
        bone_header,track_header=read('2Q',base+16)
        mot_name,=read('Q',base+88)
        frames,=read('f',base+96)
        bones,tracks=read('hh',base+112)
        # MOT.932 moved the sample rate to +120; +118 is NOT the frame rate.
        fps=read('I',base+120)[0] if mot_version==932 else 0
        clip={'index':i,'motion_id':read('H',ids+i*72+8)[0],'name':u16text(base+mot_name),
              'frames':frames,'fps':fps,'bones':bones,'tracks':tracks,'offset':base,'mot_version':mot_version}
        result['clips'].append(clip)
        if bone_header:
            start,n=read('2Q',base+bone_header)
            if start and n==bones:
                skeleton=[]
                for b in range(n):
                    at=base+start+b*80
                    bone_name,parent=read('2Q',at)
                    position=read('4f',at+32)
                    quaternion=read('4f',at+48)
                    index,bone_hash=read('2I',at+64)
                    skeleton.append({'name':u16text(base+bone_name),'parent':int((parent-start)//80) if parent else -1,
                                     'position':position,'rotation_xyzw':quaternion,'index':index,'hash':bone_hash})
                result['skeletons'].append({'clip':clip['name'],'bones':skeleton})
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    result=inspect(args.input)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2,ensure_ascii=False),encoding='utf-8')
    print(f"{len(result['clips'])} clips, {len(result['skeletons'])} embedded skeletons -> {args.output}")

