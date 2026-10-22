"""Render the actual shipped OBJ and UV atlas for offline visual inspection (no external renderer)."""
from pathlib import Path
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/slashblade_neomotions/model'
OUTPUT=ROOT/'docs/images/prototype-models.png'


def load(name,group):
    vertices=[];uvs=[];triangles=[];current=''
    for line in (ASSETS/(name+'.obj')).read_text().splitlines():
        parts=line.split()
        if not parts:continue
        if parts[0]=='v':vertices.append(list(map(float,parts[1:])))
        elif parts[0]=='vt':uvs.append(list(map(float,parts[1:])))
        elif parts[0]=='g':current=parts[1]
        elif parts[0]=='f' and current==group:
            indices=[list(map(int,p.split('/'))) for p in parts[1:]]
            triangles.append((np.array([vertices[i-1] for i,_,_ in indices]),np.array([uvs[i-1] for _,i,_ in indices])))
    return triangles


def render(name,group,width,height,scale):
    triangles=load(name,group)
    # Upright front three-quarter view, same geometry and atlas used by the game.
    angle=.28;tilt=-.12
    rotZ=np.array([[0,1,0],[-1,0,0],[0,0,1]])
    rotY=np.array([[math.cos(angle),0,math.sin(angle)],[0,1,0],[-math.sin(angle),0,math.cos(angle)]])
    rotX=np.array([[1,0,0],[0,math.cos(tilt),-math.sin(tilt)],[0,math.sin(tilt),math.cos(tilt)]])
    rotation=rotX@rotY@rotZ
    transformed=[(p@rotation.T,uv) for p,uv in triangles]
    cloud=np.concatenate([p for p,_ in transformed]);center=(cloud.min(0)+cloud.max(0))/2
    depth=np.full((height,width),-np.inf);rgba=np.zeros((height,width,4),dtype=np.uint8)
    atlas=np.asarray(Image.open(ASSETS/'prototype_metal.png').convert('RGB'))
    light=np.array([-.4,.65,.7]);light/=np.linalg.norm(light)
    for points,uv in transformed:
        n=np.cross(points[1]-points[0],points[2]-points[0]);n/=np.linalg.norm(n)
        if n[2] <= 0: continue
        view=(points-center)*scale
        xy=np.stack([view[:,0]+width/2,height/2-view[:,1]],axis=1)
        x0,y0=np.maximum(0,np.floor(xy.min(0)).astype(int));x1,y1=np.minimum([width-1,height-1],np.ceil(xy.max(0)).astype(int))
        if x0>x1 or y0>y1:continue
        xx,yy=np.meshgrid(np.arange(x0,x1+1)+.5,np.arange(y0,y1+1)+.5)
        a,b,c=xy;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(den)<1e-9:continue
        u=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
        v=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;w=1-u-v
        z=u*view[0,2]+v*view[1,2]+w*view[2,2]
        region=depth[y0:y1+1,x0:x1+1];mask=(u>=0)&(v>=0)&(w>=0)&(z>region)
        if not mask.any():continue
        region[mask]=z[mask]
        tu=(u*uv[0,0]+v*uv[1,0]+w*uv[2,0])*255
        tv=(1-u*uv[0,1]-v*uv[1,1]-w*uv[2,1])*255
        tex=atlas[np.clip(tv.astype(int),0,255),np.clip(tu.astype(int),0,255)].astype(float)
        diffuse=max(0,np.dot(n,light));half=light+np.array([0,0,1]);half/=np.linalg.norm(half)
        spec=max(0,np.dot(n,half))**28
        color=np.clip(tex*(.48+.65*diffuse)+spec*70,0,255)
        region=rgba[y0:y1+1,x0:x1+1];region[mask,:3]=color[mask];region[mask,3]=255
    return Image.fromarray(rgba)


def generate():
    factor=2;W,H=1600,1080
    canvas=Image.new('RGBA',(W*factor,H*factor),(20,27,34,255));draw=ImageDraw.Draw(canvas)
    font_path='C:/Windows/Fonts/msyh.ttc'
    font=lambda n: ImageFont.truetype(font_path,n*factor)
    def label(x,y,text,size,color):
        cursor=x*factor
        for character in text:
            face=ImageFont.truetype('C:/Windows/Fonts/seguisym.ttf',size*factor) if character in '⌈⌋' else font(size)
            draw.text((cursor,y*factor),character,font=face,fill=color)
            cursor+=draw.textlength(character,font=face)
    def line(points,fill,width=1):draw.line([(x*factor,y*factor) for x,y in points],fill=fill,width=width*factor)
    label(64,35,'NEOMOTIONS  /  FORGED METAL SERIES',16,(155,180,196))
    label(64,75,'大剑 ⌈⌋ 试作型',34,(235,239,240))
    label(850,75,'双刀 ⌈⌋ 试作型',34,(235,239,240))
    label(64,129,'厚钢宽刃 · 加强刀脊 · 铆接护手',17,(155,167,177))
    label(850,129,'短刃刀身 · 刃缘装甲鞘 · 独立握柄',17,(155,167,177))
    line([(64,171),(1536,171)],(59,74,85));line([(790,200),(790,970)],(44,57,66))
    def place(name,group,cx,cy,w,h,scale):
        rendered=render(name,group,w*factor,h*factor,scale*factor)
        mask=rendered.getchannel('A').filter(ImageFilter.GaussianBlur(10*factor))
        shadow=Image.new('RGBA',rendered.size,(0,0,0,0));shadow.putalpha(mask.point(lambda x:int(x*.5)))
        pos=(int((cx-w/2)*factor),int((cy-h/2)*factor))
        canvas.alpha_composite(shadow,(pos[0]+8*factor,pos[1]+14*factor));canvas.alpha_composite(rendered,pos)
    place('greatsword_prototype','blade',332,574,420,790,2.05)
    place('greatsword_prototype','sheath',631,786,220,290,2.0)
    place('dualblade_prototype','blade',1040,585,260,730,2.7)
    place('dualblade_prototype','sheath',1360,585,270,730,2.7)
    label(569,944,'运输护套',17,(155,167,177));label(972,944,'刀身 / 右手',17,(155,167,177));label(1289,944,'刀鞘 / 左手',17,(155,167,177))
    line([(64,993),(1536,993)],(59,74,85))
    label(64,1015,'原创建模  ·  实际 OBJ + 游戏贴图离线渲染',16,(155,167,177))
    label(1120,1015,'显示比例各自调整 / 非游戏截图',14,(120,139,153))
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    canvas.convert('RGB').resize((W,H),Image.Resampling.LANCZOS).save(OUTPUT)
    print(OUTPUT)


if __name__=='__main__':generate()
