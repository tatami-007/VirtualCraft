"""Fungal sovereign HUD. Atlas layout mirrored by MushroomBossOverlay."""
from pathlib import Path
import argparse
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[2]
OUTPUT=ROOT/'src/main/resources/assets/virtual_craft/textures/gui/boss/mushroom_king_bar.png'
DARK='#18151b'
ROOT_DARK='#49383c'
BONE='#c1ad89'
SHADOW='#806c55'
LIGHT='#eddbb2'
RED='#793343'
CAP='#a74d59'
AMBER='#dba750'

def shell(state,mirror=False):
    im=Image.new('RGBA',(20,14));d=ImageDraw.Draw(im)
    d.polygon([(0,8),(2,3),(8,0),(17,2),(19,7),(14,12),(5,13)],fill=DARK)
    if state==2:
        d.polygon([(1,8),(4,4),(6,4),(5,10),(10,12),(5,12)],fill=SHADOW)
        d.polygon([(15,3),(18,6),(16,10),(13,11),(15,7)],fill=BONE)
    else:
        d.polygon([(2,7),(4,3),(9,1),(16,3),(17,7),(13,11),(6,11)],fill=SHADOW)
        d.polygon([(3,6),(6,3),(10,2),(15,4),(13,8),(6,10)],fill=BONE)
        d.line((5,5,10,3,14,4),fill=LIGHT)
        for x in (7,10,13): d.line((x,5,x-2,9),fill=SHADOW)
        if state==1:d.line((11,1,9,5,12,7,8,12),fill=DARK,width=2)
    return im.transpose(Image.Transpose.FLIP_LEFT_RIGHT) if mirror else im

def crown(direction):
    im=Image.new('RGBA',(40,18));d=ImageDraw.Draw(im)
    d.polygon([(0,14),(5,9),(7,3),(13,6),(19,0),(26,5),(33,2),(35,10),(39,14),(31,17),(8,17)],fill=DARK)
    d.polygon([(3,12),(8,5),(13,8),(19,3),(26,8),(32,5),(36,12),(29,14),(10,14)],fill=RED)
    d.line((6,10,9,6,14,9,19,4,26,9,31,6,34,10),fill=CAP)
    d.line((5,13,11,14,19,12,28,14,35,13),fill=BONE,width=2)
    for x in range(9,32,4):d.line((x,14,19+(x-19)//2,17),fill=SHADOW)
    if direction==1:d.line((13,8,19,3,25,8),fill=LIGHT,width=2)
    if direction==2:d.line((9,11,30,11),fill=LIGHT,width=2)
    if direction==3:d.line((14,14,19,17,24,14),fill=AMBER,width=2)
    return im

def generate():
    im=Image.new('RGBA',(256,256));d=ImageDraw.Draw(im)
    d.polygon([(3,18),(16,13),(46,17),(101,18),(112,15),(132,15),(145,18),(210,17),(233,13),(241,19),(237,35),(211,39),(34,39),(9,35)],fill=DARK)
    for side in (0,1):
        def pts(p):return [(243-x if side else x,y) for x,y in p]
        for p in (((1,20),(7,14),(18,13),(31,18),(61,19),(100,19)),
                  ((3,22),(1,29),(8,35),(28,33),(50,32),(109,32)),
                  ((7,34),(13,41),(23,36),(37,36)),((10,16),(6,8),(17,12),(28,17))):
            d.line(pts(p),fill=ROOT_DARK,width=4);d.line(pts(p),fill=SHADOW,width=2)
        for x in range(29,101,9):
            d.line(pts(((x,18),(x+4,20),(x+7,19))),fill=BONE)
            d.line(pts(((x,33),(x+3,34),(x+6,32))),fill=SHADOW)
    d.line((26,21,217,21),fill=SHADOW);d.line((26,30,217,30),fill=BONE)
    d.rectangle((26,22,217,29),fill=(0,0,0,0));d.rectangle((35,35,208,38),fill=(0,0,0,0))
    for row,body,hi in ((48,'#291f27','#443039'),(56,'#9c7045','#daba7f'),(64,'#872938','#c86a6c')):
        d.rectangle((0,row,191,row+7),fill=body);d.line((0,row,191,row),fill=hi)
        d.line((0,row+7,191,row+7),fill=RED if row==64 else ROOT_DARK)
    for row,body,hi in ((80,'#30282a','#574939'),(88,'#806c55','#b7a17a'),(96,'#986326',AMBER),(104,'#dba750',LIGHT)):
        d.rectangle((0,row,173,row+3),fill=body);d.line((0,row+1,173,row+1),fill=hi)
        for x in range(4,172,13):d.line((x,row+2,x+2,row+3),fill=hi)
    for state in range(3):
        im.alpha_composite(shell(state),(state*24,120));im.alpha_composite(shell(state,True),(state*24,140))
    for direction in range(4):im.alpha_composite(crown(direction),(direction*48,176))
    return im

def preview(atlas):
    sheet=Image.new('RGBA',(284,260),(24,26,30,255))
    for i,(hp,trail,posture) in enumerate(((1,1,.2),(.65,.84,.6),(.18,.30,.94),(.15,.15,1))):
        x,y=20,10+i*62
        for row,value in ((48,1),(56,trail),(64,hp)):
            sheet.alpha_composite(atlas.crop((0,row,round(192*value),row+8)),(x+26,y+22))
        sheet.alpha_composite(atlas.crop((0,80,174,84)),(x+35,y+35))
        row=104 if i==3 else 96 if posture>=.75 else 88
        sheet.alpha_composite(atlas.crop((0,row,round(174*posture),row+4)),(x+35,y+35))
        sheet.alpha_composite(atlas.crop((0,0,244,42)),(x,y))
        for j,(dx,dy,sy) in enumerate(((3,12,120),(5,26,120),(221,12,140),(219,26,140))):
            state=0 if i==0 else min(2,(i+j)%3)
            sheet.alpha_composite(atlas.crop((state*24,sy,state*24+20,sy+14)),(x+dx,y+dy))
        sheet.alpha_composite(atlas.crop((i*48,176,i*48+40,194)),(x+102,y+2))
    path=ROOT/'tools/previews/mushroom_boss_bar_rework.png';path.parent.mkdir(parents=True,exist_ok=True)
    sheet.resize((852,780),Image.Resampling.NEAREST).save(path)
    sheet.save(path.with_name('mushroom_boss_bar_native.png'))

if __name__=='__main__':
    p=argparse.ArgumentParser(__doc__);p.add_argument('--diff',action='store_true');args=p.parse_args()
    atlas=generate()
    assert atlas.crop((26,22,218,30)).getchannel('A').getextrema()==(0,0)
    assert atlas.crop((35,35,209,39)).getchannel('A').getextrema()==(0,0)
    preview(atlas)
    if args.diff:
        old=Image.open(OUTPUT).convert('RGBA');same=old.size==atlas.size and old.tobytes()==atlas.tobytes()
        print('MATCH' if same else 'DIFF: sovereign frame and continuous posture');raise SystemExit(0 if same else 1)
    atlas.save(OUTPUT,optimize=True)
