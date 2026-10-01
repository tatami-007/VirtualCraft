"""Deterministic layered fungal combat Foley; music is deliberately untouched.

Requires numpy + soundfile (libsndfile/Vorbis bundled in the Windows wheel).
--write exports mono 44.1kHz Vorbis and keeps old files in a local backup.
Without --write only exports an audition WAV and reports signal measurements.
"""
import argparse
import hashlib
from pathlib import Path
import shutil
import numpy as np
import soundfile as sf

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'src/main/resources/assets/virtual_craft/sounds/mushroom_king'
PREVIEW=ROOT/'tools/previews/mushroom_audio'
BACKUP=ROOT/'.tmp/mushroom_audio_before_rework_20260920'
RATE=44100
# name -> length, body resonance, attack family; short combo tails prevent masking.
SPECS={
    'root_cadence':(.40,91,'wood'), 'strike':(.36,106,'wood'),
    'cap_clap':(.46,72,'clap'), 'stem_hammer':(.82,49,'heavy'),
    'mycelial_surge':(.68,58,'heavy'), 'root_convergence':(1.10,42,'heavy'),
    'canopy_closure':(.76,61,'clap'), 'spore_needle':(.24,245,'air'),
    'spore_breath':(.42,115,'air'), 'windup':(.32,155,'windup'),
    'plate_break':(.65,132,'crack'), 'shield_break':(.78,84,'crack'),
    'stagger':(.90,67,'voice'), 'roar':(1.60,46,'voice'),
    'summon':(2.40,38,'ritual'), 'death':(2.70,35,'voice'),
}

def synth(name,length,freq,family):
    n=round(length*RATE)
    t=np.arange(n)/RATE
    rng=np.random.default_rng(int.from_bytes(hashlib.sha256(name.encode()).digest()[:4],'little'))
    noise=rng.uniform(-1,1,n)
    woody=np.convolve(noise,np.ones(9)/9,mode='same')
    breath=np.convolve(noise,np.ones(35)/35,mode='same')
    phase=2*np.pi*(freq*t+freq*.12*(1-np.exp(-t*18))/18)
    body=(np.sin(phase)+.33*np.sin(phase*1.51)+.17*np.sin(phase*2.73))
    decay=np.exp(-t/(length*.25))
    if family in ('wood','heavy','clap','crack'):
        signal=.40*body*decay
        signal+=woody*np.exp(-t/ .035)*1.1
        # Asynchronous wood splinters, kept below the initial release transient.
        for at in (.018,.051,.10,.17):
            dt=np.maximum(0,t-at)
            signal+=.20*noise*np.exp(-dt/.018)*(t>=at)
        if family=='heavy':
            signal+=.30*np.sin(2*np.pi*(43*t+1.8*(1-np.exp(-12*t))))*np.exp(-t/.24)
        if family=='clap':
            signal+=.36*breath*np.exp(-t/.10)
        if family=='crack':
            signal+=.26*noise*np.exp(-t/.07)+.12*np.sin(phase*5.17)*np.exp(-t/.14)
    elif family in ('air','windup'):
        env=np.sin(np.pi*np.clip(t/length,0,1))**1.4
        flutter=.72+.28*np.sin(2*np.pi*31*t)
        signal=.9*breath*env*flutter+.13*woody*env
        signal+=.07*np.sin(phase*(1+.7*t/length))*env
    else:
        env=np.sin(np.pi*np.clip(t/length,0,1))**.75
        growl=np.sin(phase+1.5*np.sin(phase*.49))
        signal=.33*growl*env+.35*breath*env
        signal+=.10*np.sin(phase*2.02)*env
        if family=='ritual':
            signal+=.13*np.sin(2*np.pi*190*t)*np.sin(np.pi*t/length)**3
    # Quiet short early reflections, not long overlapping reverb in combos.
    dry=signal.copy()
    for seconds,gain in ((.041,.12),(.083,.055)):
        delay=round(seconds*RATE)
        signal[delay:]+=dry[:-delay]*gain
    signal-=signal.mean()
    edge=min(round(.006*RATE),n//4)
    signal[:edge]*=np.linspace(0,1,edge)
    signal[-edge:]*=np.linspace(1,0,edge)
    peak=np.max(np.abs(signal))
    ceiling=.46 if family=='windup' else .62
    signal=signal/max(peak,1e-8)*ceiling
    assert np.all(np.isfinite(signal))
    return signal.astype('float32')

def main():
    parser=argparse.ArgumentParser(__doc__)
    parser.add_argument('--write',action='store_true')
    args=parser.parse_args()
    PREVIEW.mkdir(parents=True,exist_ok=True)
    montage=[]
    for name,(duration,freq,family) in SPECS.items():
        signal=synth(name,duration,freq,family)
        sf.write(PREVIEW/f'{name}.wav',signal,RATE,subtype='PCM_16')
        if args.write:
            BACKUP.mkdir(parents=True,exist_ok=True)
            dest=OUT/f'{name}.ogg'
            if dest.exists() and not (BACKUP/dest.name).exists():
                shutil.copy2(dest,BACKUP/dest.name)
            sf.write(dest,signal,RATE,format='OGG',subtype='VORBIS')
            decoded,sr=sf.read(dest)
            assert sr==RATE and decoded.ndim==1 and np.isfinite(decoded).all()
            assert len(decoded)==len(signal) and np.max(np.abs(decoded))<.95
        print(f'{name}: {duration:.2f}s, peak {20*np.log10(np.max(np.abs(signal))):.1f} dBFS')
        if name in ('windup','root_cadence','cap_clap','stem_hammer','plate_break'):
            montage.extend([signal,np.zeros(round(.35*RATE))])
    sf.write(PREVIEW/'combat_audition.wav',np.concatenate(montage),RATE,subtype='PCM_16')

if __name__=='__main__':
    main()
