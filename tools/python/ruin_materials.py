"""Authored 16px ruin materials. No resampling, random runtime state or AI atlas slicing.

python tools/python/ruin_materials.py --diff
python tools/python/ruin_materials.py --write
The original asset generator delegates the matching block names here too.
"""
from pathlib import Path
import argparse
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/virtual_craft/textures/block'
PREVIEW = ROOT / 'tools/previews/boss_structures/materials'

STONE = [(58,66,62),(66,75,69),(74,82,76),(83,90,82),(92,98,88)]
IVORY = [(123,112,92),(143,132,109),(161,151,125),(177,168,142),(192,183,157)]
CAP = [(69,39,37),(79,44,40),(89,49,43),(100,56,47),(110,62,51)]

def shifted(palette, amount):
    return [tuple(max(0,min(255,c+amount)) for c in color) for color in palette]

def base(palette, seed=0):
    """Quiet mineral surface, with authored one-pixel grain and short scratches.

    Never subdivide the image into random rectangular color patches. Most of
    the surface stays at one value; sparse marks describe the material instead.
    """
    body=palette[2]
    im=Image.new('RGBA',(16,16),(*body,255))
    light=shifted([body],5)[0]
    dark=shifted([body],-6)[0]
    for x,y in ((1,2),(2,2),(9,1),(12,3),(13,3),(5,5),(6,5),
                (2,9),(10,7),(11,7),(7,12),(14,11),(3,14),(12,15)):
        im.putpixel(((x+seed*3)%16,(y+seed*5)%16),(*light,255))
    for x,y in ((4,1),(7,3),(1,6),(2,6),(12,5),(8,9),(9,9),
                (14,8),(4,11),(5,11),(10,13),(0,14),(15,14)):
        im.putpixel(((x+seed*3)%16,(y+seed*5)%16),(*dark,255))
    return im

def masonry(palette, floor=False):
    # Two courses at block scale, staggered joints. No texture beneath mortar.
    body=palette[2]
    im=Image.new('RGBA',(16,16),(*body,255))
    for y in range(16):
        row=y//8
        shift=0 if floor else row*4
        for x in range(16):
            u=(x+shift)%8
            v=y%8
            variation=(-2,2,0,1)[(row*2+(x+shift)//8)%4]
            amount=variation
            if v==7 or u==7:
                amount=-19
            elif v==0:
                amount=9
            elif v==6 or u==6:
                amount=-7
            elif u==0:
                amount=4
            im.putpixel((x,y),(*shifted([body],amount)[0],255))
    d=ImageDraw.Draw(im)
    for x,y in ((2,3),(3,3),(11,5),(6,10),(14,12),(1,13)):
        d.point((x,y),fill=(*shifted([body],-5)[0],255))
    for x,y in ((4,2),(12,2),(0,10),(9,13)):
        d.point((x,y),fill=(*shifted([body],5)[0],255))
    # Tiny chipped corners, not a heavy dark outline around every brick.
    for x,y in ((6,0),(3,8)) if not floor else ((6,0),(14,8)):
        d.point((x,y),fill=(*shifted([body],-12)[0],255))
    return im

def cap_skin():
    """Matte continuous cap skin: small pores and shallow directional fibres."""
    body=CAP[2]
    im=Image.new('RGBA',(16,16),(*body,255))
    d=ImageDraw.Draw(im)
    for points in (((1,2),(3,2)),((8,1),(9,1)),((12,5),(14,5)),
                   ((5,6),(6,6)),((2,10),(4,10)),((9,11),(11,11)),
                   ((13,14),(14,14)),((6,14),(7,14))):
        d.line(points,fill=(*shifted([body],4)[0],255))
    for x,y in ((4,3),(11,2),(1,7),(8,8),(14,10),(5,12),(10,15)):
        d.point((x,y),fill=(*shifted([body],-9)[0],255))
        d.point(((x+1)%16,y),fill=(*shifted([body],-3)[0],255))
    return im

def relief(fungal=False):
    p = IVORY if fungal else shifted(STONE,18)
    im = base(p,4)
    d = ImageDraw.Draw(im)
    d.rectangle((1,1,14,14),outline=(*p[0],255))
    d.line((2,13,2,2,13,2),fill=(*p[4],255))
    if fungal:
        points=[(7,12),(7,8),(4,5),(5,3),(10,3),(12,6),(9,8),(7,8)]
    else:
        points=[(5,11),(8,12),(11,10),(10,8),(5,7),(4,5),(6,3),(9,3),(10,4),(8,5)]
    d.line([(x+1,y+1) for x,y in points],fill=(*p[0],255),width=2)
    d.line(points,fill=(*p[4],255),width=1)
    return im

def gills():
    im=Image.new('RGBA',(16,16),(*IVORY[2],255))
    d=ImageDraw.Draw(im)
    # Unequal spacing and one shallow bifurcation. Endpoints join vertically.
    paths=(((1,0),(1,4),(2,7),(1,11),(1,15)),
           ((6,0),(6,6),(5,10),(6,15)),
           ((11,0),(11,3),(12,8),(11,12),(11,15)))
    for points in paths:
        d.line([(x+1,y) for x,y in points],fill=(*IVORY[3],255),width=2)
        d.line(points,fill=(*IVORY[1],255),width=1)
    d.line(((11,4),(9,7),(9,10)),fill=(*IVORY[1],255))
    return im

def lattice():
    im=Image.new('RGBA',(16,16),(32,39,35,255))
    d=ImageDraw.Draw(im)
    for x in range(-16,32,8):
        d.line((x,0,x+16,16),fill=(*STONE[1],255),width=2)
        d.line((x,16,x+16,0),fill=(*STONE[2],255),width=2)
    for x,y in ((0,0),(8,0),(4,4),(12,4),(0,8),(8,8),(4,12),(12,12)):
        d.point((x,y),fill=(*STONE[4],255))
    return im

def lamp(fungal=False):
    im=cap_skin() if fungal else base(STONE,0)
    d=ImageDraw.Draw(im)
    if fungal:
        for cx,cy in ((4,5),(11,4),(8,11)):
            d.ellipse((cx-3,cy-3,cx+3,cy+3),fill=(94,51,26,255))
            d.ellipse((cx-2,cy-2,cx+2,cy+2),fill=(179,106,39,255))
            d.ellipse((cx-1,cy-2,cx+1,cy+1),fill=(239,179,86,255))
            d.point((cx,cy-1),fill=(255,228,158,255))
    else:
        d.rectangle((2,2,13,13),outline=(40,49,43,255))
        d.polygon([(8,3),(12,7),(9,12),(4,10),(3,6)],fill=(62,99,74,255))
        d.polygon([(8,3),(8,8),(4,10),(3,6)],fill=(116,158,114,255))
        d.line((8,4,6,7,5,9),fill=(195,219,158,255))
    return im

def materials():
    return {
        'serpent_stone':base(shifted(STONE,-10)),
        'serpent_stone_bricks':masonry(shifted(STONE,12)),
        'chiseled_serpent_stone_bricks':relief(),
        'serpent_floor_tiles':masonry(shifted(STONE,5),True),
        'serpent_lattice':lattice(),
        'venom_crystal':lamp(),
        'fungal_stone':base(IVORY,2),
        'fungal_stone_bricks':masonry(IVORY),
        'chiseled_fungal_stone_bricks':relief(True),
        'fungal_cap_tiles':cap_skin(),
        'fungal_gill_tiles':gills(),
        'spore_lamp':lamp(True),
    }

def verify(name,im):
    assert im.size == (16,16),name
    assert im.convert('RGBA').getchannel('A').getextrema()==(255,255),name
    assert len(im.getcolors(256))<=24,name

def main():
    p=argparse.ArgumentParser(__doc__)
    p.add_argument('--write',action='store_true')
    p.add_argument('--diff',action='store_true')
    a=p.parse_args()
    PREVIEW.mkdir(parents=True,exist_ok=True)
    sheet=Image.new('RGB',(1056,600),(25,28,29))
    draw=ImageDraw.Draw(sheet)
    differences=0
    for i,(name,im) in enumerate(materials().items()):
        verify(name,im)
        path=OUT/f'{name}.png'
        old=Image.open(path).convert('RGBA')
        changed=sum(old.getpixel((x,y))!=im.getpixel((x,y))
                    for y in range(16) for x in range(16)) if old.size==im.size else -1
        differences+=bool(changed)
        print(f'{name}: {changed} changed pixels; 16x16 opaque')
        if a.write:
            im.save(path)
            verify(name,Image.open(path))
        x,y=(i%6)*176,(i//6)*300
        draw.text((x+4,y+3),name.replace('chiseled_','carved_'),fill='white')
        sheet.paste(im.resize((128,128),Image.Resampling.NEAREST),(x+24,y+24))
        tile=Image.new('RGBA',(48,48))
        for ty in range(3):
            for tx in range(3):
                tile.paste(im,(tx*16,ty*16))
        sheet.paste(tile.resize((132,132),Image.Resampling.NEAREST),(x+22,y+158))
    sheet.save(PREVIEW/'palette_and_tiling.png')
    # Honest native-size, integer nearest-neighbour zoom and tiled comparison.
    focus=Image.new('RGB',(768,464),(25,28,29))
    fd=ImageDraw.Draw(focus)
    for column,name in enumerate(('serpent_stone_bricks','fungal_cap_tiles')):
        im=materials()[name]
        x=column*384
        fd.text((x+16,12),name,fill='white')
        fd.text((x+16,40),'16x16 native',fill='white')
        focus.paste(im,(x+140,40))
        fd.text((x+16,78),'12x nearest-neighbour',fill='white')
        focus.paste(im.resize((192,192),Image.Resampling.NEAREST),(x+16,100))
        fd.text((x+16,306),'4x4 repeat (2x zoom)',fill='white')
        for tx in range(4):
            for ty in range(4):
                focus.paste(im.resize((32,32),Image.Resampling.NEAREST),
                            (x+16+tx*32,328+ty*32))
    focus.save(PREVIEW/'clean_brick_and_cap.png')
    if a.diff and differences:
        raise SystemExit(1)

if __name__=='__main__':
    main()
