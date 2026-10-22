"""Export compact CLIP.85 motion events, without executing the game.

Compact MOT node references are indices (not standalone CLIP file pointers).
Primitive property/key layouts follow REasy file_handlers/clip/parser.py.
"""
import argparse
import json
from pathlib import Path
import struct
from inspect_motlist import inspect

def parse_clip(data,base,offset):
    def rd(fmt,at):return struct.unpack_from('<'+fmt,data,at)
    magic,version,total,nodes,props,keys,bools,actions,plain,_=rd('IIf7I',offset)
    if magic!=0x50494c43 or version!=85:raise ValueError('Expected embedded CLIP.85')
    ptr=[base+x for x in rd('14Q',offset+40)]
    if ptr[1]-ptr[0]!=nodes*40 or ptr[2]-ptr[1]!=props*56:raise ValueError('Invalid compact table strides')
    def string(pool,index,wide=False):
        start=pool+index*(2 if wide else 1);end=start;step=2 if wide else 1
        while data[end:end+step]!=b'\0'*step:end+=step
        return data[start:end].decode('utf-16-le' if wide else 'utf-8')
    def prop(index,ancestors=()):
        if index in ancestors:raise ValueError('Circular property graph')
        at=ptr[1]+index*56
        begin,end=rd('2f',at);name_index,_,member=rd('3Q',at+16)
        count,array,_,kind,flags,aux=rd('Hh4B',at+40)
        result={'name':string(ptr[10],name_index),'type':kind,'begin':begin,'end':end,'array':array}
        if kind in {15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,35,36,37,38,39,40,41,42,43,49,51,52,53,54,55,57}:
            result['children']=[prop(member+i,ancestors+(index,)) for i in range(count)]
        else:
            kk=aux>>4;table={0:2,1:3,2:4,3:5}[kk];stride={0:32,1:8,2:8,3:16}[kk]
            records=[]
            for i in range(count):
                k=ptr[table]+(member+i)*stride;frame=rd('f',k)[0]
                if kk==1:
                    packed=rd('I',k+4)[0];value=bool(packed&1);span=(packed>>10)&65535
                elif kk==2:value=True;span=0
                else:
                    raw_at=k+(16 if kk==0 else 8);raw=rd('Q',raw_at)[0]
                    if kind==10:value=rd('f',raw_at)[0]
                    elif kind==11:value=rd('d',raw_at)[0]
                    elif kind in (6,14):value=rd('i',raw_at)[0]
                    elif kind==12:value=string(ptr[10],raw)
                    elif kind==13:value=string(ptr[11],raw,True)
                    else:value=raw
                    packed=rd('I',k+(8 if kk==0 else 4))[0];span=(packed>>9)&65535
                records.append({'frame':frame,'value':value,'span':span})
            result['keys']=records
        return result
    tracks=[]
    for i in range(nodes):
        at=ptr[0]+i*40;children,count=rd('2H',at);name,child,property_=rd('3Q',at+16)
        tracks.append({'name':string(ptr[11],name,True),'properties':[prop(property_+j) for j in range(count)]})
    return tracks

def extract(path):
    data=path.read_bytes();inventory=inspect(path);result=[]
    for clip in inventory['clips']:
        base=clip['offset'];ptr=struct.unpack_from('<Q',data,base+48)[0]
        count=data[base+116];tracks=[]
        for i in range(count):
            entry=struct.unpack_from('<Q',data,base+ptr+i*8)[0]
            tracks+=parse_clip(data,base,base+entry+64)
        result.append({**clip,'events':[t for t in tracks if t['name']!='Root']})
    return result

if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('input',type=Path);ap.add_argument('--output',type=Path,required=True)
    args=ap.parse_args();result=extract(args.input)
    args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(len(result),'motion event sets exported')
