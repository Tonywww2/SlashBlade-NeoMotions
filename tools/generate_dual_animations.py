"""Original forehand dual blades, with independent blade and sharpened-sheath wrists.
Root yaw turns the whole character; torso rotation is not inherited by arm pivots.
"""
import copy
import json
from generate_animations import ASSETS, rotation, vmd_bytes, sample, anchor_upper_body
from lower_body import footwork, dualblade_keys


def pose(right=-45,left=-45,twist=0,lean=0,r_yaw=10,l_yaw=-10,r_roll=10,l_roll=-10,
         r_cut=25,l_cut=25,r_plane=120,l_plane=60,follow=0):
    return {'body':rotation(0,twist,0),'torso':rotation(lean),
            'head':rotation(-lean/2,-twist/3),
            'rightArm':rotation(right,r_yaw,r_roll),'leftArm':rotation(left,l_yaw,l_roll),
            '_rightWeapon':dict(swing=r_cut,plane=r_plane,follow=follow),
            '_leftWeapon':dict(swing=l_cut,plane=l_plane,follow=follow)}

READY=pose()
R_WIND=pose(-110,-70,-15,5,-30,15,-25,22,r_cut=-85)
R_HIT=pose(-45,-80,15,16,20,15,10,22)
L_WIND=pose(-70,-110,15,5,-15,30,-22,25,l_cut=-85)
L_HIT=pose(-80,-45,-15,16,-15,-20,-22,-10)
CROSS_WIND=pose(-115,-115,0,-8,-30,30,-25,25,r_cut=-85,l_cut=-85)


def hands(right,left):
    result=pose(lean=14)
    for name in ['rightArm','_rightWeapon']:result[name]=copy.deepcopy(right[name])
    for name in ['leftArm','_leftWeapon']:result[name]=copy.deepcopy(left[name])
    return result


def clips():
    result={'dualblade':(2,[(0,READY),(2,READY)])}
    result['db_blade_cut']=14,[(0,READY),(2,R_WIND),(4,R_HIT),(8,R_HIT),(14,READY)]
    result['db_sheath_cut']=14,[(0,READY),(2,L_WIND),(4,L_HIT),(8,L_HIT),(14,READY)]
    result['db_cross_cut']=20,[(0,READY),(2,CROSS_WIND),(4,hands(R_HIT,L_WIND)),
                             (6,hands(R_HIT,L_WIND)),(8,hands(R_HIT,L_HIT)),(14,hands(R_HIT,L_HIT)),(20,READY)]
    result['db_flurry']=28,[(0,READY),(2,hands(R_WIND,READY)),(4,hands(R_HIT,READY)),
                          (6,hands(READY,L_WIND)),(8,hands(R_WIND,L_HIT)),(10,hands(R_HIT,READY)),
                          (12,hands(READY,L_WIND)),(14,hands(R_WIND,L_HIT)),(16,hands(R_HIT,READY)),
                          (18,hands(READY,L_WIND)),(20,hands(READY,L_HIT)),(24,READY),(28,READY)]
    result['db_finisher']=26,[(0,READY),(4,CROSS_WIND),(6,hands(R_HIT,L_WIND)),
                             (8,hands(R_HIT,L_WIND)),(10,hands(R_HIT,L_HIT)),(16,hands(R_HIT,L_HIT)),(26,READY)]
    result['db_spin']=22,[(0,READY),(2,pose(-80,-80,-50,14,-55,55,r_cut=-90,l_cut=90,r_plane=180,l_plane=180,follow=1)),
                         (6,pose(-70,-70,90,14,-60,60,r_cut=-90,l_cut=90,r_plane=180,l_plane=180,follow=1)),
                         (10,pose(-70,-70,270,14,-60,60,r_cut=-90,l_cut=90,r_plane=180,l_plane=180,follow=1)),
                         (16,pose(twist=360)),(22,pose(twist=360))]
    for _,p in result['db_spin'][1]:p['head']=rotation(-7,0,0)
    result['db_dash']=18,[(0,READY),(2,CROSS_WIND),(4,hands(R_HIT,L_WIND)),
                         (6,hands(R_HIT,L_WIND)),(8,hands(R_HIT,L_HIT)),(12,pose(lean=20)),(18,READY)]
    result['db_retreat']=18,[(0,READY),(2,L_WIND),(4,L_HIT),(8,pose(-95,-95,0,-12)),(18,READY)]
    result['db_aerial']=26,[(0,CROSS_WIND),(2,hands(R_WIND,L_WIND)),(4,hands(R_HIT,L_WIND)),
                           (6,hands(R_HIT,L_WIND)),(8,hands(R_HIT,L_HIT)),(14,pose(-30,-30,0,28)),(26,pose(-30,-30,0,28))]
    result['db_landing']=14,[(0,pose(-30,-90,25,30,l_cut=-55)),(4,L_HIT),(8,pose(lean=28)),(14,READY)]
    result['db_sa_prepare']=60,[(0,READY),(4,pose(-105,-105,0,5,25,-25,20,-20,r_cut=-55,l_cut=-55)),
                               (60,pose(-105,-105,0,5,25,-25,20,-20,r_cut=-55,l_cut=-55))]
    return result


def generate():
    for name,(duration,keys) in clips().items():
        idle=name=='dualblade';moves=[];sampled=[]
        legs=dualblade_keys(name,duration)
        for tick in range(duration+1):
            p=anchor_upper_body(sample(keys,tick))
            p.update(footwork(legs,tick,airborne=name=='db_aerial'))
            sampled.append((tick,p))
            moves.append({'tick':tick,'easing':'linear',**{k:v for k,v in p.items()
                          if not k.startswith('_') and (not idle or k in ('body','torso','rightArm','leftArm'))}})
        animation={'version':3,'name':name,'author':'NeoMotions',
                   'neomotions':legs.metadata() if legs else {'states':[{'tick':0,'state':'locomotion'}]},
                   'comment':'Original dual-blade poses; tools/generate_dual_animations.py',
                   'emote':{'beginTick':0,'endTick':duration,'stopTick':duration+2,'isLoop':idle,
                            'returnTick':0,'degrees':True,'moves':moves}}
        (ASSETS/'player_animation'/f'{name}.json').write_text(json.dumps(animation,indent=2)+'\n',encoding='utf-8')
        (ASSETS/'motions'/f'{name}.vmd').write_bytes(vmd_bytes(sampled,duration,dual=True))
    print(f'Generated {len(clips())} dual-blade poses with independent hand-aligned blade and sheath.')

if __name__=='__main__':
    from retarget_source_actions import generate as generate_source
    generate_source()
