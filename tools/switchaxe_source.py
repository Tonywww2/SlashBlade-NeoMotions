"""Selected wp08 actions; input routing and damage are Minecraft adaptations.

Clip identification uses collision requests, WeaponVisual mode transitions and
offline pose inspection. Names come from the extracted Simplified Chinese guide.
"""
SPECS = {
 'switchaxe_axe': ('sa', 0, None), 'switchaxe_sword': ('sa', 20, None),
 'sa_axe_overhead': ('sa', 360, '_AXE_00_COMBO'),
 'sa_axe_side': ('sa', 361, '_AXE_00_COMBO'),
 'sa_axe_rising': ('sa', 362, '_AXE_00_COMBO'),
 'sa_axe_forward': ('sa', 371, '_AXE_00_COMBO'),
 'sa_axe_fade': ('sa', 369, '_AXE_00_COMBO'),
 'sa_axe_wild': ('sa', 365, '_AXE_00_COMBO'),
 'sa_axe_slam': ('sa', 368, '_AXE_00_COMBO'),
 'sa_axe_spiral': ('sa', 372, '_AXE_00_COMBO'),
 'sa_morph_sword': ('sa', 381, '_SWORD_00_COMBO'),
 'sa_morph_axe': ('sa', 540, '_AXE_00_COMBO'),
 'sa_sword_overhead': ('sa', 510, '_SWORD_00_COMBO'),
 'sa_sword_right': ('sa', 525, '_SWORD_00_COMBO'),
 'sa_sword_left': ('sa', 526, '_SWORD_00_COMBO'),
 'sa_sword_double': ('sa', 515, '_SWORD_00_COMBO'),
 'sa_sword_heaven': ('sa', 517, '_SWORD_00_COMBO'),
 'sa_discharge': ('sa', 535, '_AXE_00_COMBO'),
 'sa_full_release': ('sa', 536, '_AXE_00_COMBO'),
}

# IDs are read from wp08_actionparam._ActionGuideID and wp08 name-guide rows.
GUIDES = {
 'sa_axe_overhead': 143967248, 'sa_axe_side': 1338690432, 'sa_axe_rising': -128645088,
 'sa_axe_forward': 1711220352, 'sa_axe_fade': 1185187840, 'sa_axe_wild': -1140363264,
 'sa_axe_slam': -255976208, 'sa_axe_spiral': 372106112, 'sa_morph_sword': -1209716864,
 'sa_morph_axe': -210718064, 'sa_sword_overhead': -1084801536,
 'sa_sword_right': -553980096, 'sa_sword_left': 1855746432, 'sa_sword_double': 150431184,
 'sa_sword_heaven': -1737891328, 'sa_discharge': 1649891712, 'sa_full_release': 26943142,
}

def action_rules(name, guide):
    action_id = GUIDES.get(name)
    rows = [v for v in guide['_Values'] if v['_Action'] == action_id]
    reverse = {v:k for k,v in GUIDES.items()}
    primary = [reverse[v['_TransitionAction']] for v in rows
               if v['_Input'][0]['_Value'] == 15 and v['_Input'][1]['_Value'] == 1629007360
               and v['_TransitionAction'] in reverse]
    return {'guideActionId': action_id,
            'guideTransitions': [{'to':v['_TransitionAction'], 'buttons':[p['_Value'] for p in v['_Input']],
                                  'inputTypes':v['_InputType']} for v in rows],
            'primaryNext': primary[0] if primary else None}

def sword_start(name):
    return name == 'switchaxe_sword' or name.startswith('sa_sword_') or name in (
        'sa_morph_axe', 'sa_discharge', 'sa_full_release')

def manifest(global_param):
    fields = ['_Transform_RequiredGauge', '_Reload_GaugeValue', '_SlashGaugeAutoRecoveryValue',
              '_SlashGaugeAutoRecoveryInterval', '_SwordAwakeGaugeReduceValue',
              '_SwordAwakeGaugeReduceInterval', '_SwordAwakeTime', '_OnSwordAwakenSlashGaugeRecovery',
              '_AxeEnhancedTime', '_AxeEnhancedSlashGaugeGainRate', '_Bin_Power_AttackRate']
    return {'parameters': {k: global_param[k] for k in fields},
            'componentSource': 'wpg08', 'componentUnits': 96,
            'components': [{'group': 'sheath', 'track': 'axeComponent', 'bone': 'Axe'},
                           {'group': 'blade_edge', 'track': 'swordComponent', 'bone': 'Sword'}],
            'adaptation': '100-point gauges; per-action costs/gains and mouse routing are Minecraft balance. '
                          'Axe-mode passive recovery; sword hit confirms build amp once per strike. '
                          'This is a selected action graph, not the complete executable FSM.'}
