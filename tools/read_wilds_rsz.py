"""Read-only USER.3/RSZ export for local Wilds research.

Field layouts come from REasy's Wilds type dump (kept in .local). Unknown layouts,
invalid references and truncated streams fail instead of inventing field values.
Reference: https://github.com/seifhassine/REasy (MIT).
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import uuid

SCHEMA=Path('.local/tools/REasy/resources/data/dumps/rszmhwilds.json')
SCALARS={'Bool':'?','S8':'b','U8':'B','S16':'h','U16':'H','S32':'i','U32':'I',
         'S64':'q','U64':'Q','F32':'f','F64':'d','Object':'I','UserData':'I'}

def parse(path,schema):
    data=path.read_bytes()
    if data[:4]!=b'USR\0':raise ValueError(f'Not USER: {path}')
    base=struct.unpack_from('<Q',data,32)[0]
    magic,version,objects,instances,userdata,_,info,start,external=struct.unpack_from('<6I3Q',data,base)
    if magic!=0x5a5352 or version!=16:raise ValueError('Unsupported RSZ header')
    roots=list(struct.unpack_from('<'+'I'*objects,data,base+48))
    externals={}
    for i in range(userdata):
        index,_,offset=struct.unpack_from('<IIQ',data,base+external+16*i)
        end=base+offset
        while data[end:end+2]!=b'\0\0':end+=2
        externals[index]=data[base+offset:end].decode('utf-16-le')
    position=base+start
    records=[None]
    refs=[]
    def align(n):
        nonlocal position
        position=(position+n-1)//n*n
    def unpack(fmt):
        nonlocal position
        value=struct.unpack_from('<'+fmt,data,position);position+=struct.calcsize('<'+fmt)
        return value[0] if len(value)==1 else list(value)
    def value(field):
        nonlocal position
        align(field['align']);kind=field['type']
        if kind in SCALARS:
            result=unpack(SCALARS[kind])
            if kind in ('Object','UserData'):
                refs.append(result)
                return {'$ref':result}
            return result
        if kind in ('Guid','GameObjectRef'):
            result=str(uuid.UUID(bytes_le=data[position:position+16]));position+=16;return result
        if kind in ('String','Resource','RuntimeType'):
            count=unpack('I');size=count*(1 if kind=='RuntimeType' else 2)
            if size>len(data)-position:raise ValueError(f'Invalid string length {count} at {position}')
            result=data[position:position+size].decode('utf-8' if kind=='RuntimeType' else 'utf-16-le').rstrip('\0')
            position+=size
            return result
        if kind in ('Vec2','Vec3','Vec4','Float2','Float3','Float4','Quaternion','Range','Mat4','Position'):
            return unpack('f'*(field['size']//4))
        if kind in ('Data','KeyFrame'):
            raw=data[position:position+field['size']];position+=field['size'];return {'hex':raw.hex()}
        raise ValueError(f'Unsupported {kind}: {field}')
    for i in range(1,instances):
        type_id,crc=struct.unpack_from('<II',data,base+info+i*8)
        definition=schema[f'{type_id:x}']
        if int(definition['crc'],16)!=crc:raise ValueError(f'Layout CRC mismatch: {definition["name"]}')
        if i in externals:
            records.append({'id':i,'type':definition['name'],'external':externals[i]});continue
        fields={};record_start=position
        try:
            for f in definition['fields']:
                if f['array']:
                    align(4);count=unpack('I')
                    if count>len(data):raise ValueError(f'Invalid array length {count}')
                    fields[f['name']]=[value(f) for _ in range(count)]
                else:fields[f['name']]=value(f)
        except Exception as ex:raise ValueError(f'{path.name}, instance {i} {definition["name"]}, offset {position:#x}: {ex}') from ex
        records.append({'id':i,'type':definition['name'],'offset':record_start,'fields':fields})
    if position!=len(data):raise ValueError(f'{path.name}: unparsed bytes: {len(data)-position}')
    if any(r>=instances for r in refs):raise ValueError('Invalid object reference')
    return {'source':str(path),'sha256':hashlib.sha256(data).hexdigest(),'roots':roots,'instances':records}

def expand(doc,index,seen=()):
    if index==0:return None
    if index in seen:return {'$cycle':index}
    record=doc['instances'][index]
    if 'external' in record:return {'external':record['external']}
    def visit(v):
        if isinstance(v,dict) and '$ref' in v:return expand(doc,v['$ref'],seen+(index,))
        if isinstance(v,list):return [visit(x) for x in v]
        return v
    return {'$type':record['type'],'$id':index,**{k:visit(v) for k,v in record['fields'].items()}}

if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('roots',nargs='+',type=Path)
    ap.add_argument('--output',type=Path,default=Path('.local/wilds/rsz'));args=ap.parse_args()
    schema=json.loads(SCHEMA.read_text(encoding='utf-8'));args.output.mkdir(parents=True,exist_ok=True)
    for root in args.roots:
        paths=[root] if root.is_file() else sorted(root.rglob('*.user.3'))
        for path in paths:
            doc=parse(path,schema)
            target=args.output/(path.name+'.json');target.write_text(json.dumps(doc,ensure_ascii=False,indent=2),encoding='utf-8')
            print(path.name,len(doc['instances'])-1,'instances')
