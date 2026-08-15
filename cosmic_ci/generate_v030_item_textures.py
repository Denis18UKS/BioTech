#!/usr/bin/env python3
from pathlib import Path
from PIL import Image, ImageDraw
import math, random

ROOT = Path('/tmp/cosmic/CosmicExperiments')
OUT = ROOT / 'src/main/resources/assets/cosmicexperiment/textures/item'
OUT.mkdir(parents=True, exist_ok=True)
SIZE = 32

PALETTES = {
    'sun': ((255,198,54),(255,105,24)),
    'mercury': ((176,167,151),(88,82,76)),
    'venus': ((238,177,73),(126,72,31)),
    'earth': ((51,132,224),(39,119,68)),
    'moon': ((205,205,194),(103,105,105)),
    'mars': ((207,91,45),(112,43,28)),
    'jupiter': ((220,177,133),(145,93,65)),
    'saturn': ((224,197,141),(158,127,84)),
    'uranus': ((122,214,224),(74,151,180)),
    'neptune': ((56,106,228),(25,48,142)),
}


def radial_planet(name, rings=False):
    img = Image.new('RGBA',(SIZE,SIZE),(0,0,0,0))
    d = ImageDraw.Draw(img)
    if rings:
        for off, alpha in [(0,200),(2,110),(-2,110)]:
            d.ellipse((2,13+off,29,20+off), outline=(210,205,176,alpha), width=1)
    c1,c2 = PALETTES[name]
    cx,cy,r=16,16,9
    for y in range(cy-r,cy+r+1):
        for x in range(cx-r,cx+r+1):
            dx=(x-cx)/r; dy=(y-cy)/r
            rr=dx*dx+dy*dy
            if rr>1: continue
            nz=math.sqrt(max(0,1-rr))
            light=max(0.12, 0.22+0.78*max(0,(-0.55*dx-0.25*dy+0.78*nz)))
            t=(dy+1)*0.5
            if name in ('jupiter','saturn'):
                band=0.5+0.5*math.sin((dy*10.0)+(0.9*math.sin(dx*5)))
                t=0.25+0.55*band
            if name=='earth':
                # Ocean base with deliberately non-Minecraft-like tiny continent silhouettes.
                land = (math.sin(dx*8+dy*3)+math.sin(dx*3-dy*9)*0.7+math.sin(dx*13+1.5)*0.35) > 0.72
                base = c2 if land else c1
            else:
                base=tuple(int(c1[i]*(1-t)+c2[i]*t) for i in range(3))
            col=tuple(max(0,min(255,int(v*light+18*(1-light)))) for v in base)
            img.putpixel((x,y),(*col,255))
    # atmosphere highlight
    if name in ('earth','venus','mars','jupiter','saturn','uranus','neptune'):
        atm={'earth':(75,166,255,200),'venus':(255,195,85,150),'mars':(255,117,74,90),
             'jupiter':(244,213,184,70),'saturn':(255,226,170,70),'uranus':(144,232,242,110),'neptune':(71,127,255,130)}[name]
        d.ellipse((6,6,26,26),outline=atm,width=1)
    # small identifying details
    if name=='jupiter': d.ellipse((16,20,21,23),fill=(188,72,38,210))
    if name=='earth':
        d.arc((5,5,27,27),200,330,fill=(215,244,255,180),width=1)
    if name in ('moon','mercury'):
        for px,py,pr in [(12,12,2),(20,17,2),(14,21,1)]: d.ellipse((px-pr,py-pr,px+pr,py+pr),outline=(70,70,70,170))
    return img


def add_nav_mark(img, accent):
    d=ImageDraw.Draw(img)
    # orbital bracket + outward navigation arrow, unique from spawn items
    d.arc((1,1,30,30),205,333,fill=accent,width=2)
    d.polygon([(26,22),(31,23),(28,18)],fill=accent)
    d.ellipse((2,2,5,5),fill=accent)
    return img

for name in PALETTES:
    rings=name in ('saturn','uranus','neptune')
    img=radial_planet(name,rings)
    accent=(255,245,190,255) if name in ('sun','venus','saturn') else (165,235,255,255)
    add_nav_mark(img,accent)
    img.save(OUT/f'navigate_{name}.png')

# Galaxy and experiment icons.
def galaxy(filename, tint, mirrored=False):
    img=Image.new('RGBA',(SIZE,SIZE),(0,0,0,0)); d=ImageDraw.Draw(img)
    random.seed(filename)
    cx=cy=16
    for arm in range(3):
        for i in range(80):
            t=i/79*math.pi*2.6
            ang=(arm*2*math.pi/3 + (-t if mirrored else t))
            rad=1.4+1.45*t
            x=cx+math.cos(ang)*rad+random.uniform(-0.7,0.7)
            y=cy+math.sin(ang)*rad*0.42+random.uniform(-0.5,0.5)
            fade=max(0.15,1-i/100)
            c=tuple(int(v*fade+255*(1-fade)*0.35) for v in tint)
            d.point((round(x),round(y)),fill=(*c,220))
    d.ellipse((13,13,19,19),fill=(255,245,220,245))
    return img

galaxy('spawn_andromeda_shader',(150,175,255),False).save(OUT/'spawn_andromeda_shader.png')
galaxy('spawn_milky_way_shader',(120,205,255),True).save(OUT/'spawn_milky_way_shader.png')

img=Image.new('RGBA',(SIZE,SIZE),(0,0,0,0)); d=ImageDraw.Draw(img)
for r,a in [(13,45),(10,70),(7,120),(4,240)]:
    d.ellipse((16-r,16-r,16+r,16+r),fill=(255,100+min(150,r*8),50,a))
d.ellipse((13,13,19,19),fill=(255,255,235,255))
for ang in range(0,360,30):
    x=16+math.cos(math.radians(ang))*14; y=16+math.sin(math.radians(ang))*14
    d.line((16,16,x,y),fill=(255,180,80,125),width=1)
img.save(OUT/'spawn_supernova_shader.png')

img=Image.new('RGBA',(SIZE,SIZE),(0,0,0,0)); d=ImageDraw.Draw(img)
for off,col in [(0,(255,215,145,220)),(2,(238,130,70,170)),(-2,(255,245,210,180))]:
    d.ellipse((2,12+off,30,20+off),outline=col,width=2)
d.ellipse((9,9,23,23),fill=(0,0,0,255),outline=(255,195,105,210),width=1)
img.save(OUT/'spawn_singularity_shader.png')

print('Generated v0.3 navigator/experiment item textures:', len(list(OUT.glob('navigate_*.png')))+4)
