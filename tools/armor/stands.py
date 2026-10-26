import re, math, sys
from PIL import Image, ImageDraw
import os
ROOT=os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..') + '/'
TEX=ROOT+'src/main/resources/assets/nightsiege/textures/entity/equipment/'
src=open(ROOT+'src/main/java/com/mki/nightsiege/client/ClassArmorShapes.java').read()
boxes={}
cur=None
for line in src.splitlines():
    m=re.search(r'Map\.entry\("(\w+)"',line)
    if m: cur=m.group(1); boxes[cur]=[]
    m=re.search(r'new Box\("(\w+)", "(\w+)", "(\w+)", (.*?)\)',line)
    if m and cur:
        rest=[x.strip() for x in m.group(4).split(',')]
        f=lambda s: float(s.rstrip('f'))
        vals=dict(slot=m.group(1),part=m.group(2),name=m.group(3),
            x=f(rest[0]),y=f(rest[1]),z=f(rest[2]),w=int(rest[3]),h=int(rest[4]),d=int(rest[5]),grow=f(rest[6]),
            u=int(rest[7]),v=int(rest[8]),mirror=rest[9]=='true',px=f(rest[10]),py=f(rest[11]),pz=f(rest[12]),
            rx=f(rest[13]),ry=f(rest[14]),rz=f(rest[15]))
        boxes[cur].append(vals)
PIV={'head':(0,0,0),'body':(0,0,0),'right_arm':(-5,2,0),'left_arm':(5,2,0),'right_leg':(-1.9,12,0),'left_leg':(1.9,12,0)}
def base(slot_tex):
    out=[]
    O,I=1.0,0.5
    # (slot, layer, part, box, uv, mirror, grow)
    out.append(('head','a','head',(-4,-8,-4,8,8,8),(0,0),False,O))
    out.append(('head','a','head',(-4,-8,-4,8,8,8),(32,0),False,O+0.5))
    out.append(('chest','a','body',(-4,0,-2,8,12,4),(16,16),False,O))
    out.append(('chest','a','right_arm',(-3,-2,-2,4,12,4),(40,16),False,O))
    out.append(('chest','a','left_arm',(-1,-2,-2,4,12,4),(40,16),True,O))
    out.append(('legs','l','body',(-4,0,-2,8,12,4),(16,16),False,I))
    out.append(('legs','l','right_leg',(-2,0,-2,4,12,4),(0,16),False,I))
    out.append(('legs','l','left_leg',(-2,0,-2,4,12,4),(0,16),True,I))
    out.append(('feet','a','right_leg',(-2,0,-2,4,12,4),(0,16),False,O))
    out.append(('feet','a','left_leg',(-2,0,-2,4,12,4),(0,16),True,O))
    return out
def rot(v,rx,ry,rz):
    x,y,z=v
    # Rx
    c,s=math.cos(rx),math.sin(rx); y,z=y*c-z*s, y*s+z*c
    c,s=math.cos(ry),math.sin(ry); x,z=x*c+z*s, -x*s+z*c
    c,s=math.cos(rz),math.sin(rz); x,y=x*c-y*s, x*s+y*c
    return (x,y,z)
def faces(x,y,z,w,h,d,g,u,v,mirror):
    x0,y0,z0=x-g,y-g,z-g; x1,y1,z1=x+w+g,y+h+g,z+d+g
    F=[]
    # each: corners (tl,tr,br,bl) in 3D, uv rect (u0,v0,uw,vh), normal
    F.append(('north',[(x0,y0,z0),(x1,y0,z0),(x1,y1,z0),(x0,y1,z0)],(u+d,v+d,w,h),(0,0,-1)))
    F.append(('south',[(x1,y0,z1),(x0,y0,z1),(x0,y1,z1),(x1,y1,z1)],(u+d+w+d,v+d,w,h),(0,0,1)))
    west=(u,v+d,d,h); east=(u+d+w,v+d,d,h)
    if mirror: west,east=east,west
    F.append(('west',[(x0,y0,z1),(x0,y0,z0),(x0,y1,z0),(x0,y1,z1)],west,(-1,0,0)))
    F.append(('east',[(x1,y0,z0),(x1,y0,z1),(x1,y1,z1),(x1,y1,z0)],east,(1,0,0)))
    F.append(('up',[(x0,y0,z1),(x1,y0,z1),(x1,y0,z0),(x0,y0,z0)],(u+d,v,w,d),(0,-1,0)))
    F.append(('down',[(x0,y1,z0),(x1,y1,z0),(x1,y1,z1),(x0,y1,z1)],(u+d+w,v,w,d),(0,1,0)))
    return F
def render(look, yaw=-28, pitch=12, scale=6):
    A=Image.open(TEX+'humanoid/class_%s.png'%look).convert('RGBA')
    L=Image.open(TEX+'humanoid_leggings/class_%s.png'%look).convert('RGBA')
    quads=[]
    cy,sy=math.cos(math.radians(yaw)),math.sin(math.radians(yaw))
    cp,sp=math.cos(math.radians(pitch)),math.sin(math.radians(pitch))
    def view(p):
        x,y,z=p
        # model: y down, front -z. camera looks from -z toward +z
        x,z = x*cy - z*sy, x*sy + z*cy
        y,z = y*cp - z*sp, y*sp + z*cp
        return x,y,z
    def add(tex,part,box,uv,mirror,grow,pose=None):
        px,py,pz=PIV[part]
        for name,corn,(u0,v0,uw,vh),n in faces(*box,grow,uv[0],uv[1],mirror):
            if uw<=0 or vh<=0: continue
            def tr(p):
                if pose:
                    bx,by,bz,rx,ry,rz=pose
                    p=rot(p,rx,ry,rz); p=(p[0]+bx,p[1]+by,p[2]+bz)
                return view((p[0]+px,p[1]+py,p[2]+pz))
            nn=n
            if pose: nn=rot(n,pose[3],pose[4],pose[5])
            nv=view(nn)
            if nv[2]>=0: continue   # facing away (camera at -z looking +z)
            shade={'up':1.0,'down':0.5}.get(name, 0.8 if name in('north','south') else 0.62)
            tl,tr_,br,bl=[tr(c) for c in corn]
            for j in range(vh):
                for i in range(uw):
                    uu=u0+(uw-1-i if (mirror and name in('north','south','up','down')) else i); vv=v0+j
                    if uu<0 or vv<0 or uu>=tex.width or vv>=tex.height: continue
                    col=tex.getpixel((uu,vv))
                    if col[3]<20: continue
                    a=i/uw; b=(i+1)/uw; c=j/vh; e=(j+1)/vh
                    def P(s,t):
                        top=[tl[k]+(tr_[k]-tl[k])*s for k in range(3)]
                        bot=[bl[k]+(br[k]-bl[k])*s for k in range(3)]
                        return [top[k]+(bot[k]-top[k])*t for k in range(3)]
                    pts=[P(a,c),P(b,c),P(b,e),P(a,e)]
                    depth=sum(p[2] for p in pts)/4
                    quads.append((depth,[(p[0],p[1]) for p in pts],tuple(int(ch*shade) for ch in col[:3])))
    for slot,layer,part,box,uv,mirror,g in base(None):
        add(A if layer=='a' else L,part,box,uv,mirror,g)
    for b in boxes.get(look,[]):
        tex=L if b['slot']=='legs' else A
        add(tex,b['part'],(b['x'],b['y'],b['z'],b['w'],b['h'],b['d']),(b['u'],b['v']),b['mirror'],b['grow'],
            (b['px'],b['py'],b['pz'],b['rx'],b['ry'],b['rz']))
    # armour stand base plate and stick
    W,H=FRAME_W,FRAME_H
    img=Image.new('RGBA',(W,H),(0,0,0,0)); d=ImageDraw.Draw(img)
    ox,oy=W/2, ORIGIN_Y
    def P2(p): return (ox+p[0]*scale, oy+p[1]*scale)
    # base slab (12x1x12 at y=24..25)
    slab=[]
    for name,corn,_,n in faces(-6,24,-6,12,1,12,0,0,0,False):
        nv=view(n)
        if nv[2]>=0: continue
        pts=[view(c) for c in corn]
        col={'up':(170,170,170),'north':(120,120,120),'south':(120,120,120)}.get(name,(100,100,100))
        slab.append((sum(p[2] for p in pts)/4+50,[(p[0],p[1]) for p in pts],col))
    stick=[]
    for name,corn,_,n in faces(-1,-7,-1,2,31,2,0,0,0,False):
        nv=view(n)
        if nv[2]>=0: continue
        pts=[view(c) for c in corn]
        stick.append((sum(p[2] for p in pts)/4+20,[(p[0],p[1]) for p in pts],(150,108,60) if name=='north' else (120,86,48)))
    allq=slab+stick+quads
    allq.sort(key=lambda q:-q[0])
    for depth,pts,col in allq:
        d.polygon([P2(p) for p in pts],fill=col+(255,))
    return img
FRAME_W, FRAME_H, ORIGIN_Y = 192, 284, 122

if __name__ == '__main__':
    # The armour stands of the class windows: one picture per subclass outfit and a shadow of it
    # for locked classes. Run after tools/armor/build.py changed an outfit.
    out = ROOT + 'src/main/resources/assets/nightsiege/textures/gui/stands/'
    os.makedirs(out, exist_ok=True)
    looks = sys.argv[1:] or sorted(boxes.keys())
    for look in looks:
        img = render(look)
        assert img.getbbox()[1] > 0 and img.getbbox()[3] < FRAME_H, look + ' does not fit the frame'
        img.save(out + look + '.png')
        # A slate silhouette: readable on the windows' near-black panels.
        shadow = Image.new('RGBA', img.size, (66, 60, 88, 0))
        alpha = img.split()[3].point(lambda a: 210 if a > 0 else 0)
        shadow.putalpha(alpha)
        shadow.save(out + look + '_shadow.png')
    print('stands:', len(looks))
