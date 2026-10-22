"""Original forged-metal prototype meshes for SlashBlade's native OBJ renderer.

OBJ groups are independently renderable: blade, sheath, damaged blade and GUI icons.
Canonical grip is at the origin and the blade points down -X, like native SlashBlade.
No external models, textures, or game-extracted assets are used.
"""
import json
import math
from collections import defaultdict
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT/'src/main/resources/assets/slashblade_neomotions/model'
PALETTE = {
    'steel': (109, 127, 141), 'edge': (207, 219, 225), 'dark': (44, 55, 65),
    'brass': (178, 132, 67), 'grip': (45, 38, 32), 'red': (147, 58, 43),
    'cyan': (108, 183, 192), 'groove': (22, 29, 35), 'mark': (207, 202, 179),
}


class Mesh:
    def __init__(self): self.groups = defaultdict(list)

    def poly(self, group, vertices, material):
        vertices=np.array(vertices,dtype=float)
        # Ear clipping preserves concave broken edges and the sheath's sharpened rail.
        normal=np.zeros(3)
        for a,b in zip(vertices,np.roll(vertices,-1,axis=0)): normal+=np.cross(a,b)
        projected=np.delete(vertices,np.argmax(np.abs(normal)),axis=1)
        cross=lambda a,b: a[0]*b[1]-a[1]*b[0]
        area=sum(cross(a,b) for a,b in zip(projected,np.roll(projected,-1,axis=0)))
        orientation=1 if area>=0 else -1
        remaining=list(range(len(vertices)));triangles=[]
        while len(remaining)>3:
            for slot,b in enumerate(remaining):
                a=remaining[slot-1];c=remaining[(slot+1)%len(remaining)]
                pa,pb,pc=projected[[a,b,c]]
                if cross(pb-pa,pc-pb)*orientation<=1e-8: continue
                def inside(p):
                    return all(cross(y-x,p-x)*orientation>=-1e-8 for x,y in [(pa,pb),(pb,pc),(pc,pa)])
                if any(inside(projected[k]) for k in remaining if k not in (a,b,c)):continue
                triangles.append([a,b,c]);remaining.pop(slot);break
            else: raise ValueError('Non-simple polygon: '+str(vertices))
        triangles.append(remaining)
        for indices in triangles:
            triangle = vertices[indices]
            if np.linalg.norm(np.cross(triangle[1]-triangle[0], triangle[2]-triangle[0])) > 1e-7:
                self.groups[group].append((triangle, material))

    def bevel(self, group, outline, thickness, material='steel', edge='edge', inset=.88, z=0):
        p = np.array(outline, dtype=float)
        area = sum(a[0]*b[1]-a[1]*b[0] for a,b in zip(p,np.roll(p,-1,axis=0)))
        if area < 0: p = p[::-1]
        center = p.mean(axis=0); inner = center+(p-center)*inset
        front = [(x,y,z+thickness/2) for x,y in inner]
        back = [(x,y,z-thickness/2) for x,y in inner]
        rim = [(x,y,z) for x,y in p]
        self.poly(group, front, material); self.poly(group, back[::-1], material)
        for i in range(len(p)):
            j=(i+1)%len(p)
            self.poly(group,[rim[i],rim[j],front[j],front[i]],edge)
            self.poly(group,[rim[j],rim[i],back[i],back[j]],edge)

    def box(self, group, x0,x1,y0,y1,z0,z1, material='steel', edge=None):
        z0,z1=sorted((z0,z1))
        self.bevel(group,[(x0,y0),(x1,y0),(x1,y1),(x0,y1)],z1-z0,material,edge or material,.92,(z0+z1)/2)

    def cylinder(self, group, a, b, radius, material, sides=8, cap='edge'):
        a,b=np.array(a,dtype=float),np.array(b,dtype=float); axis=b-a; axis/=np.linalg.norm(axis)
        u=np.cross(axis,[0,0,1] if abs(axis[2])<.9 else [0,1,0]);u/=np.linalg.norm(u)
        v=np.cross(axis,u)
        rings=[[c+radius*(math.cos(t)*u+math.sin(t)*v) for t in np.arange(sides)*2*math.pi/sides] for c in (a,b)]
        self.poly(group,rings[0][::-1],cap);self.poly(group,rings[1],cap)
        for i in range(sides):
            j=(i+1)%sides
            self.poly(group,[rings[0][i],rings[0][j],rings[1][j],rings[1][i]],material)

    def bolt(self, group, x,y,z, radius=4):
        for side in (-1,1):
            self.cylinder(group,(x,y,side*z),(x,y,side*(z+1)),radius+1.8,'groove',12,'dark')
            self.cylinder(group,(x,y,side*(z+1)),(x,y,side*(z+3)),radius,'brass',6,'edge')
            self.box(group,x-radius*.65,x+radius*.65,y-.7,y+.7,side*(z+3)-.2,side*(z+3)+.2,'groove')

    def ring(self, group, x, radius=11, tube=3):
        # Pommel eye: a real hole, not a painted disk.
        rows=[]
        for i in range(16):
            theta=2*math.pi*i/16
            rows.append([(x+tube*math.sin(phi),(radius+tube*math.cos(phi))*math.cos(theta),
                          (radius+tube*math.cos(phi))*math.sin(theta)) for phi in np.arange(6)*math.pi/3])
        for i in range(16):
            for j in range(6):
                self.poly(group,[rows[i][j],rows[(i+1)%16][j],rows[(i+1)%16][(j+1)%6],rows[i][(j+1)%6]],'dark')

    def grip(self, group, long=False):
        end=42 if long else 30
        self.cylinder(group,(-14,0,0),(end,0,0),8 if long else 6.5,'grip',10,'dark')
        for x in range(-10,end,6):
            self.cylinder(group,(x,0,0),(x+1.8,0,0),8.6 if long else 7.1,'dark',10,'grip')
        for x in (-16,end): self.cylinder(group,(x,0,0),(x+4,0,0),10 if long else 8.5,'brass',8,'steel')
        self.ring(group,end+8,10 if long else 8,2.7)

    def clone(self, source, target, transform=lambda p:p):
        self.groups[target].extend((transform(p.copy()),m) for p,m in list(self.groups[source]))

    def icon(self, target, parts):
        faces=[]
        # Icon geometry is separate from hand-space geometry. Keep it inside a 104-unit square.
        angle=math.radians(-43)
        rotation=np.array([[math.cos(angle),-math.sin(angle),0],[math.sin(angle),math.cos(angle),0],[0,0,1]])
        for group,offset in parts:
            for p,m in self.groups[group]: faces.append(((p+np.array(offset))@rotation.T,m))
        bounds=np.concatenate([p for p,_ in faces]);lo=bounds.min(0);hi=bounds.max(0)
        center=(lo+hi)/2;scale=102/max((hi-lo)[:2])
        self.groups[target]=[((p-center)*scale,m) for p,m in faces]

    def write(self,path):
        lines=['# NeoMotions original metal prototype; regenerate with tools/generate_prototype_models.py']
        index=1
        for group,faces in self.groups.items():
            lines.append('g '+group)
            for triangle,material in faces:
                n=np.cross(triangle[1]-triangle[0],triangle[2]-triangle[0]); n/=np.linalg.norm(n)
                tile=list(PALETTE).index(material); tx=tile%4;ty=tile//4
                # Atlas margins avoid sampling adjacent materials. OBJ parser flips V on load.
                uv=[((tx+.10)/4,1-(ty+.12)/4),((tx+.90)/4,1-(ty+.12)/4),((tx+.50)/4,1-(ty+.88)/4)]
                for point in triangle: lines.append('v '+' '.join(f'{x:.6f}' for x in point))
                for u,v in uv: lines.append(f'vt {u:.6f} {v:.6f}')
                for _ in range(3): lines.append('vn '+' '.join(f'{x:.6f}' for x in n))
                lines.append('f '+' '.join(f'{i}/{i}/{i}' for i in range(index,index+3)))
                index+=3
        path.write_text('\n'.join(lines)+'\n',encoding='utf-8')


def greatsword():
    m=Mesh();g='blade'
    profile=[(-34,-27),(-233,-39),(-306,-4),(-271,45),(-67,36),(-34,24)]
    m.bevel(g,profile,15,'steel','edge',.84)
    # Dark reinforced spine, raised center rail, and paired inset vent plates.
    m.bevel(g,[(-48,13),(-250,22),(-275,36),(-65,28),(-48,24)],19,'dark','steel',.91)
    for sign in (-1,1):
        z=sign*8.4
        m.bevel(g,[(-70,-7),(-237,-10),(-255,4),(-70,8)],1.3,'groove','dark',.94,z)
        m.bevel(g,[(-74,-3),(-226,-5),(-242,1),(-74,4)],2,'dark','steel',.94,sign*10)
        for x in (-95,-126,-157,-188): m.box(g,x,x+12,14,21,z,z+sign*2,'brass','dark')
        # Three stamped prototype bars near the root.
        for x in (-61,-57,-53):m.box(g,x,x+1.5,-13,-4,sign*9,sign*9+.25,'mark')
    for x,y in [(-48,17),(-76,23),(-147,27),(-226,29)]:m.bolt(g,x,y,11,3)
    m.bevel(g,[(-34,-43),(-21,-46),(-13,-32),(-13,30),(-24,44),(-36,37)],22,'dark','steel',.77)
    m.bevel(g,[(-31,-28),(-20,-29),(-19,27),(-31,27)],25,'brass','edge',.83)
    m.bolt(g,-29,0,14,7)
    m.grip(g,long=True)
    # A short steel transport collar keeps the large blade visually readable when carried.
    s='sheath'
    m.bevel(s,[(-24,-35),(-104,-39),(-115,-23),(-115,29),(-94,42),(-24,37)],24,'dark','steel',.84)
    for x in (-35,-85):
        m.box(s,x-4,x+4,-36,38,-14,14,'brass','steel')
        m.bolt(s,x,0,15,4)
    m.box(s,-91,-43,-12,13,-14,14,'grip','dark')
    damaged=[(-34,-27),(-118,-31),(-133,-14),(-113,-5),(-137,10),(-109,20),(-124,37),(-67,36),(-34,24)]
    # Build the broken blade as clipped geometry with a jagged, freshly exposed steel face.
    m.bevel('blade_damaged',damaged,15,'steel','edge',.91)
    for p,mat in m.groups[g]:
        if p[:,0].min()>-40: m.groups['blade_damaged'].append((p.copy(),mat))
    m.bevel('blade_fragment',[(-145,-34),(-233,-39),(-306,-4),(-271,45),(-141,38)],15,'steel','edge',.86)
    m.icon('item_blade', [('blade',(0,0,0))])
    m.clone('item_blade','item_bladens')
    m.icon('item_damaged',[('blade_damaged',(0,0,0))])
    return m


def dualblade():
    m=Mesh();g='blade'
    outline=[(-20,-15),(-119,-24),(-174,-7),(-151,28),(-83,21),(-26,18)]
    m.bevel(g,outline,9,'steel','edge',.77)
    m.bevel(g,[(-29,10),(-124,17),(-146,26),(-85,20),(-27,17)],13,'dark','steel',.88)
    for sign in (-1,1):
        m.bevel(g,[(-42,-3),(-120,-8),(-142,-2),(-120,4),(-42,6)],1.5,'groove','dark',.88,sign*5.3)
        for x in (-51,-47):m.box(g,x,x+1.8,-9,-2,sign*6,sign*6+.3,'mark')
    for x,y in [(-34,12),(-78,15),(-120,18)]:m.bolt(g,x,y,7,2.3)
    m.bevel(g,[(-26,-26),(-13,-29),(-7,-18),(-7,22),(-20,30),(-29,24)],16,'dark','edge',.78)
    m.bolt(g,-18,0,10,5)
    m.grip(g)
    # The scabbard is a second weapon: armored shell, sharpened lower rail, dedicated grip.
    s='sheath'
    shell=[(-20,-18),(-127,-28),(-182,-5),(-157,33),(-80,27),(-23,23)]
    m.bevel(s,shell,15,'dark','steel',.86)
    m.bevel(s,[(-29,-19),(-127,-30),(-182,-5),(-149,-12),(-121,-20),(-29,-12)],7,'steel','edge',.72)
    # Face armor panels and visible rivets distinguish the sheath from a duplicated blade.
    for sign in (-1,1):
        m.bevel(s,[(-42,-5),(-119,-12),(-149,0),(-128,16),(-43,13)],2,'steel','dark',.86,sign*8.7)
        m.box(s,-108,-62,1,6,sign*10,sign*10+.6,'groove')
        for x in (-55,-51):m.box(s,x,x+1.8,-9,-2,sign*10,sign*10+.5,'brass')
    for x,y in [(-36,15),(-79,19),(-126,22)]:m.bolt(s,x,y,9,2.6)
    m.bevel(s,[(-26,-29),(-11,-31),(-6,-19),(-6,26),(-21,33),(-30,24)],20,'brass','steel',.80)
    m.bolt(s,-18,0,12,5)
    m.grip(s)
    m.bevel('blade_damaged',[(-20,-15),(-73,-20),(-84,-9),(-67,-3),(-81,8),(-69,21),(-26,18)],9,'steel','edge',.86)
    for p,mat in m.groups[g]:
        if p[:,0].min()>-30:m.groups['blade_damaged'].append((p.copy(),mat))
    m.bevel('blade_fragment',[(-88,-22),(-119,-24),(-174,-7),(-151,28),(-85,21)],9,'steel','edge',.82)
    m.icon('item_blade',[('blade',(0,33,0)),('sheath',(8,-33,-6))])
    m.icon('item_bladens',[('blade',(0,0,0))])
    m.icon('item_damaged',[('blade_damaged',(0,33,0)),('sheath',(8,-33,-6))])
    return m


def switchaxe():
    """Original steel prototype; movable meshes are local to source Axe/Sword joints."""
    m=Mesh();g='blade_core'
    m.grip(g,long=True)
    m.bevel(g,[(-18,-8),(-226,-8),(-235,0),(-226,8),(-18,8)],12,'dark','steel',.76)
    for sign in (-1,1):
        m.box(g,-213,-38,-3,3,sign*7,sign*8,'steel','dark')
        m.box(g,-174,-100,-1,1,sign*8.5,sign*9,'brass')
    for x in [-28,-68,-208]:m.bolt(g,x,0,9,4)
    m.bevel(g,[(-25,-16),(-12,-19),(-3,-10),(-3,10),(-12,19),(-25,16)],20,'dark','brass',.75)
    # Reservoir and locking ratchet: compact enough to clear the rear hand.
    m.cylinder(g,(-44,14,0),(-78,14,0),8,'brass',10,'steel')
    m.box(g,-72,-50,20,22,-4,4,'cyan','dark')
    m.clone(g,'blade_core_damaged')
    s='sheath'
    head=[(-34,2),(-42,-32),(-22,-63),(15,-70),(39,-46),(30,-6),(10,12),(-15,12)]
    m.bevel(s,head,14,'steel','edge',.79)
    m.bevel(s,[(-23,6),(-30,-27),(-15,-44),(12,-49),(26,-33),(20,-7),(8,13)],19,'dark','steel',.83)
    for side in [-1,1]:
        m.bevel(s,[(-16,-15),(-13,-32),(11,-35),(18,-21),(11,-11)],2,'groove','brass',.81,side*10.5)
        m.box(s,-10,9,-28,-25,side*12,side*12.6,'cyan')
    for x,y in [(-18,-5),(-10,-39),(18,-41),(17,-9)]:m.bolt(s,x,y,12,3)
    m.ring(s,0,13,3)
    # Folded toward the grip in axe mode; source Sword rotates it 180 degrees
    # into an outward blade. Its cutting edge becomes model -Y in sword mode.
    edge='blade_edge'
    m.bevel(edge,[(-8,-10),(103,-17),(137,0),(109,28),(12,24),(-8,12)],11,'steel','edge',.78)
    m.bevel(edge,[(1,-11),(97,-16),(116,-6),(18,-3),(1,2)],15,'dark','steel',.86)
    for side in [-1,1]:
        m.bevel(edge,[(16,3),(97,1),(112,9),(96,16),(16,13)],1.5,'groove','brass',.9,side*6)
        for x in [18,23,28]:m.box(edge,x,x+2,6,10,side*7,side*7.5,'mark')
    for x,y in [(3,-4),(39,-9),(88,-10)]:m.bolt(edge,x,y,8,2.5)
    m.bevel('blade_edge_damaged',[(-8,-10),(41,-13),(53,-3),(39,4),(57,12),(41,25),(12,24),(-8,12)],11,'steel','edge',.82)
    m.bevel('blade_fragment',[(62,-14),(103,-17),(137,0),(109,28),(64,26)],11,'steel','edge',.8)
    m.bevel('sheath_damaged',[(-25,2),(-29,-24),(-12,-39),(4,-27),(19,-37),(25,-10),(10,12),(-15,12)],14,'dark','edge',.78)
    for name in ['sheath','blade_edge','sheath_damaged','blade_edge_damaged']:
        m.box(name+'_luminous',-5,8,-5,-3,8,9,'cyan')
    # Static carry/stand and inventory meshes are a complete assembled axe.
    for group,target in [('blade_core','blade'),('blade_core_damaged','blade_damaged')]:m.clone(group,target)
    for part in ['sheath','blade_edge']:
        m.clone(part,'blade',lambda p:p+np.array([-223.68,0,0]))
        m.clone(part+'_damaged','blade_damaged',lambda p:p+np.array([-223.68,0,0]))
    m.icon('item_blade',[('blade',(0,0,0))]);m.clone('item_blade','item_bladens')
    m.icon('item_damaged',[('blade_damaged',(0,0,0))])
    return m


def texture():
    rng=np.random.default_rng(20261021)
    pixels=np.zeros((256,256,4),dtype=np.uint8);pixels[:,:,3]=255
    for i,(name,rgb) in enumerate(PALETTE.items()):
        x,y=i%4*64,i//4*64
        grain=rng.normal(0,1.3,(64,64))+rng.normal(0,2.8,(64,1))
        if name=='grip': grain+=np.indices((64,64)).sum(0)%8-4
        for channel,c in enumerate(rgb):pixels[y:y+64,x:x+64,channel]=np.clip(c+grain,0,255)
    Image.fromarray(pixels).save(ASSETS/'prototype_metal.png')


def generate():
    ASSETS.mkdir(parents=True,exist_ok=True);texture()
    summary={}
    for name,build in [('greatsword_prototype',greatsword),('dualblade_prototype',dualblade),('switchaxe_prototype',switchaxe)]:
        m=build();m.write(ASSETS/(name+'.obj'))
        summary[name]={g:len(faces) for g,faces in m.groups.items()}
    (ASSETS/'prototype_models.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(summary,indent=2))


if __name__=='__main__': generate()
