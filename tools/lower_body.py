"""Phase-based lower-body tracks for the existing combat combo state machine.
Charge -> commit -> recovery -> chain window. A flurry keeps one planted stance.
Hips stay at vanilla pivots; rigid legs require no optional bending dependency.
"""
from dataclasses import dataclass


def stance(right=4, left=-6, yaw=6, roll=1):
    return {'rightLeg': dict(pitch=right,yaw=-yaw,roll=roll),
            'leftLeg': dict(pitch=left,yaw=yaw,roll=-roll)}

GUARD=stance()
BRACE=stance(8,-12)
STRIKE=stance(10,-16)
HEAVY=stance(12,-18)
DUAL_BRACE=stance(6,-10,8,2)

@dataclass
class MotionPlan:
    keys: list
    states: list
    def metadata(self):
        return {'states':[{'tick':t,'state':s} for t,s in self.states],
                'hipAttachment':'vanilla','interpolation':'smoothstep'}


def attack(duration,hit,cancel,initial=GUARD):
    return MotionPlan([(0,initial),(max(2,hit-4),BRACE),(hit,STRIKE),
                       (hit+3,STRIKE),(cancel,GUARD),(duration,GUARD)],
                      [(0,'anticipation'),(hit-2,'commit'),(hit+3,'recovery'),
                       (cancel,'chain_ready'),(duration,'guard')])


def greatsword_keys(name,duration):
    if name=='greatsword': return None
    if name.startswith('gs_charge_'):
        return MotionPlan([(0,GUARD),(6,BRACE),(duration,BRACE)],
                          [(0,'charge_enter'),(6,'charge_hold')])
    if name.startswith('gs_sa_prepare_'):
        return MotionPlan([(0,BRACE),(duration,BRACE)],[(0,'sa_hold')])
    if name.startswith('gs_tackle_'):
        return MotionPlan([(0,BRACE),(4,STRIKE),(8,STRIKE),(10,BRACE),(duration,BRACE)],
                          [(0,'tackle_drive'),(4,'impact'),(8,'recovery'),(10,'chain_ready')])
    if name in ('gs_overhead','gs_charged_slash','gs_strong_slash'):
        hit,cancel={'gs_overhead':(10,14),'gs_charged_slash':(12,18),'gs_strong_slash':(14,22)}[name]
        return attack(duration,hit,cancel,GUARD if name=='gs_overhead' else BRACE)
    if name=='gs_sweep':
        return MotionPlan([(0,GUARD),(6,stance(6,-10,10)),(14,stance(8,-12,12)),
                           (16,GUARD),(duration,GUARD)],
                          [(0,'anticipation'),(8,'pivot_cut'),(14,'recovery'),(16,'chain_ready')])
    if name=='gs_rising': return attack(duration,12,18)
    if name=='gs_true_slash':
        return MotionPlan([(0,BRACE),(10,STRIKE),(14,STRIKE),(20,BRACE),(22,BRACE),
                           (26,HEAVY),(30,HEAVY),(34,GUARD),(duration,GUARD)],
                          [(0,'anticipation'),(8,'first_commit'),(14,'reload'),
                           (24,'second_commit'),(30,'recovery'),(34,'chain_ready')])
    if name=='gs_aerial':
        return MotionPlan([(0,stance(-22,-16)),(6,stance(-38,-24)),(10,stance(-24,-16)),
                           (18,stance(-12,-8)),(duration,stance(-12,-8))],
                          [(0,'air_enter'),(6,'air_cut'),(18,'air_hold')])
    if name=='gs_landing':
        return MotionPlan([(0,stance(-12,-8)),(4,STRIKE),(8,STRIKE),(14,GUARD),(duration,GUARD)],
                          [(0,'landing'),(3,'impact'),(8,'recovery'),(14,'chain_ready')])
    raise ValueError(f'Missing greatsword state track: {name}')


def dualblade_keys(name,duration):
    if name=='dualblade': return None
    timings={'db_blade_cut':(3,3,8),'db_sheath_cut':(3,3,8),'db_cross_cut':(4,7,13),
             'db_flurry':(4,19,23),'db_finisher':(6,9,21)}
    if name in timings:
        first,last,cancel=timings[name];enter=max(2,first-2)
        # One committed state, several arm strikes: do not run in place per hit.
        return MotionPlan([(0,GUARD),(enter,DUAL_BRACE),(last+2,DUAL_BRACE),
                           (cancel,GUARD),(duration,GUARD)],
                          [(0,'anticipation'),(enter,'commit'),(last+2,'recovery'),(cancel,'chain_ready')])
    if name=='db_spin':
        pivot=stance(5,-10,10,1)
        return MotionPlan([(0,GUARD),(3,pivot),(12,pivot),(17,GUARD),(duration,GUARD)],
                          [(0,'pivot_enter'),(3,'pivot_cut'),(12,'recovery'),(17,'chain_ready')])
    if name in ('db_dash','db_retreat'):
        forward=name=='db_dash';step=stance(-16,8) if forward else stance(12,-10)
        recover,cancel=(9,13) if forward else (8,14)
        return MotionPlan([(0,GUARD),(4,step),(recover,step),(cancel,GUARD),(duration,GUARD)],
                          [(0,'step_forward' if forward else 'step_back'),(4,'commit'),
                           (recover,'recovery'),(cancel,'chain_ready')])
    if name=='db_aerial':
        return MotionPlan([(0,stance(-22,-14)),(4,stance(-34,-22)),(10,stance(-34,-22)),
                           (18,stance(-12,-8)),(duration,stance(-12,-8))],
                          [(0,'air_enter'),(4,'air_cut'),(18,'air_hold')])
    if name=='db_landing':
        return MotionPlan([(0,stance(-12,-8)),(4,DUAL_BRACE),(7,DUAL_BRACE),(10,GUARD),(duration,GUARD)],
                          [(0,'landing'),(3,'commit'),(7,'recovery'),(10,'chain_ready')])
    if name=='db_sa_prepare':
        return MotionPlan([(0,GUARD),(6,DUAL_BRACE),(duration,DUAL_BRACE)],[(0,'sa_enter'),(6,'sa_hold')])
    raise ValueError(f'Missing dual-blade state track: {name}')


def footwork(plan,tick,airborne=False):
    if plan is None:return {}
    before,after=plan.keys[0],plan.keys[-1]
    for a,b in zip(plan.keys,plan.keys[1:]):
        if a[0]<=tick<=b[0]:before,after=a,b;break
    f=max(0,min(1,(tick-before[0])/max(1,after[0]-before[0])));f=f*f*(3-2*f)
    return {bone:{axis:before[1][bone][axis]*(1-f)+after[1][bone][axis]*f
                  for axis in before[1][bone]} for bone in before[1]}
