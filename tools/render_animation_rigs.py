"""Offline contact sheets from shipped body JSON, blade VMD, actual OBJ and texture.

Uses Minecraft's rigid model pivots and SlashBlade's native matrix order. Not a game screenshot.
"""
import json
import math
import struct
from functools import lru_cache
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from generate_animations import ASSETS, ROOT, quat, rotate, mul
from render_prototype_models import load


def matrix(q):
    return np.column_stack([rotate(q,v) for v in [(1,0,0),(0,1,0),(0,0,1)]])


@lru_cache(None)
def model(name,group): return load(name,group)


def skin(name,bone,tick):
    raw=(ASSETS/'motions'/f'{name}.vmd').read_bytes();frames=[]
    for i in range(struct.unpack_from('<I',raw,50)[0]):
        offset=54+i*111
        if raw[offset:offset+15].split(b'\0')[0].decode()!=bone:continue
        frame,*values=struct.unpack_from('<I7f',raw,offset+15)
        frames.append((frame,np.array(values[:3]),np.array(values[3:])))
    f=tick*6;a=frames[min(int(f),len(frames)-1)];b=frames[min(math.ceil(f),len(frames)-1)]
    blend=f-int(f);q1=a[2];q2=b[2]
    if np.dot(q1,q2)<0:q2=-q2
    q=q1*(1-blend)+q2*blend;q/=np.linalg.norm(q)
    return np.diag([1,-1,1])@matrix(q)@np.diag([-1,1,1])*.125,np.array([0,24,0])+np.array([2,-2,2])*(a[1]*(1-blend)+b[1]*blend)


def box(lo,hi,color):
    vertices=np.array([[x,y,z] for x in [lo[0],hi[0]] for y in [lo[1],hi[1]] for z in [lo[2],hi[2]]])
    result=[]
    for ids in [(0,1,3,2),(4,6,7,5),(0,4,5,1),(2,3,7,6),(0,2,6,4),(1,5,7,3)]:
        for face in [(ids[0],ids[1],ids[2]),(ids[0],ids[2],ids[3])]:result.append((vertices[list(face)],color))
    return result


def scene(clip,tick,weapon):
    data=json.loads((ASSETS/'player_animation'/f'{clip}.json').read_text())
    keys=data['emote']['moves'];pose=keys[0].copy()
    for key in keys:
        if key['tick']<=tick:pose.update(key)
    body=matrix(quat(pose.get('body',{})));reflection=np.diag([-1,-1,1])
    body=reflection@body@reflection;pivot=np.array([0,24.016-.7*16/.9375,0]);result=[]
    offset=reflection@np.array([pose.get('body',{}).get(k,0) for k in 'xyz'])*16/.9375
    def root(points):return (points-pivot)@body.T+pivot+offset
    for name,lo,hi,default,color in [
        ('head',(-4,-8,-4),(4,0,4),(0,0,0),(187,141,103)),
        ('torso',(-4,0,-2),(4,12,2),(0,0,0),(77,125,144)),
        ('rightArm',(-3,-2,-2),(1,10,2),(-5,2,0),(193,151,111)),
        ('leftArm',(-1,-2,-2),(3,10,2),(5,2,0),(193,151,111)),
        ('rightLeg',(-2,0,-2),(2,12,2),(-1.9,12,.1),(67,74,87)),
        ('leftLeg',(-2,0,-2),(2,12,2),(1.9,12,.1),(67,74,87))]:
        p=pose.get(name,{});q=matrix(quat(p));position=np.array([p.get(k,v) for k,v in zip('xyz',default)])
        result.extend((root(points@q.T+position),color) for points,color in box(lo,hi,color))
    atlas=np.array(Image.open(ASSETS/'model/prototype_metal.png').convert('RGB'))
    component_profile = data.get('neomotions',{}).get('source') == 'sa'
    if component_profile:
        manifest=json.loads((ASSETS/'motions/source_actions.json').read_text(encoding='utf-8'))
        parts=[('blade_core',None)]+[(v['group'],v['track']) for v in manifest['switchaxe']['components']]
    else:parts=[('blade','hardpointA'),('sheath','hardpointB')]
    for group,bone in parts:
        rotation,position=skin(clip,'hardpointA' if component_profile else bone,tick)
        if component_profile and bone:
            part=pose[bone]
            position=position+rotation@np.array([part.get(k,0) for k in 'xyz'])
            rotation=rotation@matrix(quat(part))
        for points,uv in model(weapon,group):
            u,v=uv.mean(axis=0);color=atlas[int(np.clip(1-v,0,1)*255),int(np.clip(u,0,1)*255)]
            result.append((root(points@rotation.T+position),color))
    return result


def render(triangles,width=480,height=460,back=False):
    camera=np.array([.8,-.25,1.3 if back else -1.3]);camera/=np.linalg.norm(camera)
    right=np.cross([0,-1,0],camera);right/=np.linalg.norm(right);up=np.cross(camera,right)
    view=np.vstack([right,up,camera]);image=np.full((height,width,3),(23,30,40),dtype=np.uint8);depth=np.full((height,width),-np.inf)
    cloud=np.concatenate([p for p,_ in triangles])@view.T
    bounds=cloud.max(0)-cloud.min(0);center=(cloud.max(0)+cloud.min(0))/2
    scale=min(8,(width-40)/bounds[0],(height-70)/bounds[1])
    light=np.array([.3,-.6,-1]);light/=np.linalg.norm(light)
    for points,color in triangles:
        normal=np.cross(points[1]-points[0],points[2]-points[0]);normal/=np.linalg.norm(normal)
        p=(points@view.T-center)*scale;xy=np.column_stack([p[:,0]+width/2,height/2-p[:,1]-15])
        low=np.maximum(0,np.floor(xy.min(0)).astype(int));high=np.minimum([width-1,height-1],np.ceil(xy.max(0)).astype(int))
        x0,y0=low;x1,y1=high
        if x0>x1 or y0>y1:continue
        xx,yy=np.meshgrid(np.arange(x0,x1+1)+.5,np.arange(y0,y1+1)+.5);a,b,c=xy
        den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(den)<1e-8:continue
        u=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
        v=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;w=1-u-v
        z=u*p[0,2]+v*p[1,2]+w*p[2,2];region=depth[y0:y1+1,x0:x1+1]
        mask=(u>=0)&(v>=0)&(w>=0)&(z>region);region[mask]=z[mask]
        shaded=np.clip(np.array(color)*(.65+.35*abs(np.dot(normal,light))),0,255)
        image[y0:y1+1,x0:x1+1][mask]=shaded
    return Image.fromarray(image)


def contact_sheet(weapon,cells,output):
    canvas=Image.new('RGB',(1920,1040),(23,30,40));draw=ImageDraw.Draw(canvas)
    font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',20)
    draw.text((30,15),'NEOMOTIONS / 实际动画资源与模型离线预览（非游戏截图）',font=font,fill=(223,229,235))
    for index,(clip,tick,label,back) in enumerate(cells):
        x=index%4*480;y=index//4*490+50
        canvas.paste(render(scene(clip,tick,weapon),back=back),(x,y))
        draw.text((x+20,y+430),f'{label} / tick {tick}',font=font,fill=(211,218,226))
    target=ROOT/'docs/images'/output;canvas.save(target);print(target)


if __name__=='__main__':
    contact_sheet('greatsword_prototype',[
        ('greatsword',0,'源动作待机',False),('gs_charge_1',20,'蓄力姿态',False),
        ('gs_true_slash',14,'真蓄力第一击',False),('gs_true_slash',17,'翻身起势',False),
        ('gs_true_slash',19,'身体倒置',False),('gs_true_slash',25,'翻身回正',False),
        ('gs_true_slash',35,'真蓄力第二击',False),('greatsword',0,'背部刀鞘',True)],'wilds-greatsword-source.png')
    contact_sheet('dualblade_prototype',[
        ('dualblade',0,'源动作待机',False),('db_blade_cut',4,'二连斩右刀',False),
        ('db_blade_cut',9,'二连斩左鞘',False),('db_cross_cut',16,'交叉斩',False),
        ('db_flurry',6,'乱舞起始',False),('db_flurry',15,'乱舞左鞘',False),
        ('db_finisher',26,'六连终结',False),('db_spin',11,'回旋双刃',False)],'wilds-dualblade-source.png')
