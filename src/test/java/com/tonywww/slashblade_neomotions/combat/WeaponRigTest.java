package com.tonywww.slashblade_neomotions.combat;

import dev.kosmx.playerAnim.api.TransformType;
import com.tonywww.slashblade_neomotions.animation.ActionAnimationPlayer;
import com.tonywww.slashblade_neomotions.animation.WeaponPose;
import dev.kosmx.playerAnim.core.impl.AnimationProcessor;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
import dev.kosmx.playerAnim.core.util.Vec3f;
import jp.nyatla.nymmd.MmdMotionPlayer;
import jp.nyatla.nymmd.MmdPmdModel_BasicClass;
import jp.nyatla.nymmd.MmdVmdMotion_BasicClass;
import jp.nyatla.nymmd.types.MmdMatrix;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Real Player Animator sampling versus the real native PMD/VMD skinning player. */
class WeaponRigTest {
    private static final String BASE = "/assets/slashblade_neomotions/";
    private MmdMotionPlayer motion(String name) throws Exception {
        var player = new MmdMotionPlayer() {
            @Override protected void onUpdateSkinningMatrix(MmdMatrix[] matrices) {}
        };
        try (var pmd = getClass().getResourceAsStream("/assets/slashblade/model/bladeholder.pmd");
             var vmd = getClass().getResourceAsStream(BASE + "motions/" + name + ".vmd")) {
            assertNotNull(pmd); assertNotNull(vmd);
            player.setPmd(new MmdPmdModel_BasicClass(pmd, null) {});
            player.setVmd(new MmdVmdMotion_BasicClass(vmd));
        }
        return player;
    }
    private Matrix4f nativeMatrix(MmdMotionPlayer player, String bone, float tick) throws Exception {
        player.updateMotion(tick * 50 * SourceAction.PLAYBACK_SPEED);
        var values = new float[16];
        player._skinning_mat[player.getBoneIndexByName(bone)].getValue(values);
        return new Matrix4f().translate(0, 24, 0).scale(2, -2, 2).mul(new Matrix4f().set(values))
                .scale(-1f / 16, 1f / 16, 1f / 16);
    }
    private Matrix4f part(KeyframeAnimationPlayer animation, String part, Vec3f defaults) {
        var p = animation.get3DTransform(part, TransformType.POSITION, 0, defaults);
        var r = animation.get3DTransform(part, TransformType.ROTATION, 0, Vec3f.ZERO);
        return new Matrix4f().translate(p.getX(), p.getY(), p.getZ()).rotateZYX(r.getZ(), r.getY(), r.getX());
    }
    private KeyframeAnimationPlayer body(String clip, float tick) throws Exception {
        try (var json = getClass().getResourceAsStream(BASE + "player_animation/" + clip + ".json")) {
            var player = new ActionAnimationPlayer(AnimationSerializing.deserializeAnimation(json).get(0), (int) tick, null, false, 0);
            player.setupAnim(tick % 1);
            return player;
        }
    }
    private void assertFirstPersonRig(KeyframeAnimationPlayer playback, float partialTick, String context) {
        var processor = new AnimationProcessor(playback);
        processor.setTickDelta(partialTick);
        int before = playback.getCurrentTick();
        // Independent reference: PlayerRendererMixin body transform, followed by
        // LivingEntityRenderer/PlayerRenderer and the original arm-attached layer.
        var bodyPosition = processor.get3DTransform("body", TransformType.POSITION, Vec3f.ZERO);
        var bodyRotation = processor.get3DTransform("body", TransformType.ROTATION, Vec3f.ZERO);
        var root = new Matrix4f().translate(bodyPosition.getX(), bodyPosition.getY() + .7F, bodyPosition.getZ())
                .rotateZ(bodyRotation.getZ()).rotateY(bodyRotation.getY()).rotateX(bodyRotation.getX())
                .translate(0, -.7F, 0).scale(-1, -1, 1).scale(.9375F).translate(0, -1.501F, 0);
        for (boolean left : new boolean[]{false, true}) {
            String arm = left ? "leftArm" : "rightArm";
            var p = processor.get3DTransform(arm, TransformType.POSITION, new Vec3f(left ? 5 : -5, 2, 0));
            var r = processor.get3DTransform(arm, TransformType.ROTATION, Vec3f.ZERO);
            var w = processor.get3DTransform(left ? "leftWeapon" : "rightWeapon", TransformType.ROTATION, Vec3f.ZERO);
            var third = new Matrix4f(root).scale(1F / 16)
                    .translate(p.getX(), p.getY(), p.getZ())
                    .rotate(new Quaternionf().rotationZYX(r.getZ(), r.getY(), r.getX())).translate(0, 9, 0)
                    .rotate(new Quaternionf().rotationZYX(r.getZ(), r.getY(), r.getX()).conjugate())
                    .rotate(new Quaternionf().rotationZYX(w.getZ(), w.getY(), w.getX())).scale(.125F);
            // Several camera orientations also catch axis reflections, handedness
            // swaps and accidentally rotating the camera with a spinning body.
            for (float[] view : new float[][]{{0, 0}, {.6F, -.8F}, {-.9F, 1.3F}}) {
                var framing = new Matrix4f().translate(0, -.18F, -.5F)
                        .rotateX(view[0]).rotateY(view[1]).translate(0, -1.501F * .9375F, 0);
                var expected = framing.mul(third).scale(.65F);
                var first = WeaponPose.firstPerson(processor, left, view[0], view[1]);
                // Grip, tip, cutting edge and face normal all need the same pose.
                for (var point : new Vector3f[]{new Vector3f(), new Vector3f(-128, 0, 0),
                        new Vector3f(0, -32, 0), new Vector3f(0, 0, 32)}) {
                    float error = expected.transformPosition(new Vector3f(point)).distance(first.transformPosition(new Vector3f(point)));
                    // Float trig/quat roundoff for continuous multi-turn Euler
                    // tracks is below 0.002 model pixels on both JOML versions.
                    assertTrue(error < .0001F, context + (left ? " left" : " right") + " mismatch " + error);
                }
                var normal = WeaponPose.normal(first).transform(new Vector3f(0, 0, 1));
                var expectedNormal = expected.transformDirection(new Vector3f(0, 0, 1)).normalize();
                assertTrue(normal.distance(expectedNormal) < .0001F, context + " lighting normal");
            }
        }
        assertEquals(before, playback.getCurrentTick(), "Rendering must not advance a separate animation clock");
    }
    @Test void firstPersonUsesTheThirdPersonWeaponRigAtEveryQuarterTick() throws Exception {
        var paths = Stream.of(Stream.of(GreatSwordMove.values()).map(m -> m.path),
                Stream.of(DualBladeMove.values()).map(m -> m.path), Stream.of(SwitchAxeMove.values()).map(m -> m.path)).flatMap(s -> s).toList();
        for (String path : paths) {
            var data = body(path, 0).getData();
            for (int sample = 0; sample <= data.endTick * 4; sample++) {
                var playback = new ActionAnimationPlayer(data, sample / 4, null, false, 0);
                assertFirstPersonRig(playback, sample % 4 / 4F, path + " at " + sample / 4F);
            }
        }
    }
    @Test void switchAxeComponentsFollowTheSameHandAndNativeSheathAtEveryFrame() throws Exception {
        for (var move : SwitchAxeMove.values()) {
            var nativePlayer = motion(move.path);
            var data = body(move.path, 0).getData();
            for (int tick = 0; tick < move.duration; tick++) {
                var playback = new ActionAnimationPlayer(data, tick, null, false, 0);
                var animation = new AnimationProcessor(playback); animation.setTickDelta(0);
                var hand = WeaponPose.sampleHand(animation, false);
                var component = WeaponPose.component(animation, "axeComponent");
                var third = new Matrix4f(hand).mul(component);
                var nativeSheath = new Matrix4f().scale(1F / 16).mul(nativeMatrix(nativePlayer, "hardpointB", tick));
                for (var point : new Vector3f[]{new Vector3f(), new Vector3f(-30, -60, 0), new Vector3f(0, 0, 12)}) {
                    float error = third.transformPosition(new Vector3f(point)).distance(nativeSheath.transformPosition(new Vector3f(point)));
                    // Native PMD interpolation uses floats; allow 0.016 of a model pixel.
                    assertTrue(error < .001, move.path + " native component diverges at " + tick + ": " + error);
                }
                for (String track : new String[]{"axeComponent", "swordComponent"}) {
                    var part = WeaponPose.component(animation, track);
                    var reference = new Matrix4f().translate(0, -.18F, -.5F).rotateX(.3F).rotateY(-.4F)
                            .translate(0, -1.501F * .9375F, 0).mul(WeaponPose.playerModel(animation)).mul(hand).scale(.65F).mul(part);
                    var first = WeaponPose.firstPerson(animation, false, .3F, -.4F).mul(part);
                    for (var point : new Vector3f[]{new Vector3f(), new Vector3f(128, 0, 0), new Vector3f(0, 32, 0)})
                        assertTrue(reference.transformPosition(new Vector3f(point)).distance(first.transformPosition(new Vector3f(point))) < .0001);
                }
            }
        }
        var axe = new AnimationProcessor(body("switchaxe_axe", 0)); axe.setTickDelta(0);
        var sword = new AnimationProcessor(body("switchaxe_sword", 0)); sword.setTickDelta(0);
        assertTrue(WeaponPose.component(axe, "axeComponent").getTranslation(new Vector3f())
                .distance(WeaponPose.component(sword, "axeComponent").getTranslation(new Vector3f())) > 100,
                "The sheath must actually slide along the shaft");
        assertTrue(WeaponPose.component(axe, "swordComponent").transformDirection(new Vector3f(1, 0, 0))
                .dot(WeaponPose.component(sword, "swordComponent").transformDirection(new Vector3f(1, 0, 0))) < -.99,
                "The sword edge must unfold through 180 degrees");
    }
    @Test void firstPersonSharesComboBlendingIdleAndHeldPoses() throws Exception {
        String[][] transitions = {{"gs_charged_slash", "gs_charge_2"}, {"gs_charge_3", "gs_true_slash"},
                {"db_blade_cut", "db_sheath_cut"}, {"db_flurry", "db_finisher"},
                {"db_aerial", "db_landing"}, {"db_spin", "dualblade"}, {"gs_true_slash", "greatsword"}};
        for (var pair : transitions) {
            var playback = new ActionAnimationPlayer(body(pair[1], 0).getData(), 0, body(pair[0], 8), false, 7);
            for (int tick = 0; tick < 10; tick++) {
                for (float fraction : new float[]{0, .25F, .5F, .75F}) assertFirstPersonRig(playback, fraction, pair[1]);
                playback.tick();
            }
        }
        for (String path : new String[]{"gs_charge_1", "gs_charge_3", "gs_aerial", "db_aerial", "db_sa_prepare"}) {
            var playback = new ActionAnimationPlayer(body(path, 0).getData(), 100, null, true, 4);
            playback.tick();
            assertFirstPersonRig(playback, .75F, path + " held/late sync");
        }
    }
    @Test void partialTicksPreserveCombinedAxisRotationsOnBothLoaders() throws Exception {
        for (String clip : new String[]{"gs_aerial", "gs_true_slash", "db_spin"}) {
            for (int tick : new int[]{5, 6, 17}) {
                var a = body(clip, tick).get3DTransform("body", TransformType.ROTATION, 0, Vec3f.ZERO);
                var b = body(clip, tick + 1).get3DTransform("body", TransformType.ROTATION, 0, Vec3f.ZERO);
                for (float fraction : new float[]{.25f, .5f, .75f}) {
                    var expected = new Quaternionf().rotationZYX(a.getZ(), a.getY(), a.getX())
                            .slerp(new Quaternionf().rotationZYX(b.getZ(), b.getY(), b.getX()), fraction);
                    var actual = body(clip, tick + fraction).get3DTransform("body", TransformType.ROTATION, 0, Vec3f.ZERO);
                    var orientation = new Quaternionf().rotationZYX(actual.getZ(), actual.getY(), actual.getX());
                    assertTrue(Math.abs(expected.dot(orientation)) > .99999,
                            clip + " combined-axis orientation changed at " + (tick + fraction));
                }
            }
        }
    }
    @Test void retargetedLegsKeepTheirHipPivotsAndFiniteRotations() throws Exception {
        var paths = Stream.of(Stream.of(GreatSwordMove.values()).map(m -> m.path),
                Stream.of(DualBladeMove.values()).map(m -> m.path), Stream.of(SwitchAxeMove.values()).map(m -> m.path)).flatMap(s -> s).toList();
        for (var path : paths) {
            var data = body(path,0).getData();
            for (int tick = 0; tick <= data.endTick; tick++) for(boolean left:new boolean[]{false,true}) {
                var animation=body(path,tick);String name=left?"leftLeg":"rightLeg";
                var defaults=new Vec3f(left?1.9f:-1.9f,12,.1f);
                var position=animation.get3DTransform(name,TransformType.POSITION,0,defaults);
                assertEquals(defaults.getX(),position.getX(),.001,path);
                assertEquals(12,position.getY(),.001,path);assertEquals(.1,position.getZ(),.001,path);
                var rotation=animation.get3DTransform(name,TransformType.ROTATION,0,Vec3f.ZERO);
                assertTrue(Float.isFinite(rotation.getX())&&Float.isFinite(rotation.getY())&&Float.isFinite(rotation.getZ()),path);
            }
        }
    }
    @Test void torsoLeansAroundTheHipAndCarriesTheShoulders() throws Exception {
        var paths=Stream.concat(Stream.of(GreatSwordMove.values()).map(m->m.path),
                Stream.of(DualBladeMove.values()).map(m->m.path)).toList();
        for(var path:paths) {
            var data=body(path,0).getData();
            for(int tick=0;tick<=data.endTick;tick++) {
                var animation=new KeyframeAnimationPlayer(data,tick);
                var torso=part(animation,"torso",Vec3f.ZERO);
                assertTrue(torso.transformPosition(new Vector3f(0,12,0)).distance(new Vector3f(0,12,0))<.001,
                        path+" torso detached from hips at "+tick);
                for(boolean left:new boolean[]{false,true}) {
                    var shoulder=torso.transformPosition(new Vector3f(left?5:-5,2,0));
                    var arm=part(animation,left?"leftArm":"rightArm",new Vec3f(left?5:-5,2,0))
                            .transformPosition(new Vector3f());
                    assertTrue(shoulder.distance(arm)<.001,path+" arm detached from shoulder at "+tick);
                }
            }
        }
    }
    @Test void idleAnimationsLeaveVanillaLegMovementAvailable() throws Exception {
        for (String path : new String[]{"greatsword", "dualblade", "switchaxe_axe", "switchaxe_sword"}) {
            var animation = body(path, 1);
            for (String name : new String[]{"rightLeg", "leftLeg"}) {
                var walkRotation = new Vec3f(.6f, -.1f, .05f);
                var walkPosition = new Vec3f(-1.9f, 12, .1f);
                var rotation = animation.get3DTransform(name, TransformType.ROTATION, 0, walkRotation);
                var position = animation.get3DTransform(name, TransformType.POSITION, 0, walkPosition);
                assertEquals(walkRotation.getX(), rotation.getX(), .00001f, path);
                assertEquals(walkRotation.getY(), rotation.getY(), .00001f, path);
                assertEquals(walkRotation.getZ(), rotation.getZ(), .00001f, path);
                assertEquals(walkPosition.getX(), position.getX(), .00001f, path);
                assertEquals(walkPosition.getY(), position.getY(), .00001f, path);
                assertEquals(walkPosition.getZ(), position.getZ(), .00001f, path);
            }
        }
    }
    @Test void trueChargeKeepsTheSourceFullBodyFlipBetweenItsTwoHits() throws Exception {
        var move=GreatSwordMove.TRUE_SLASH;
        assertEquals(248,move.source.motionId);
        assertEquals(2,move.source.hits.size());
        float minimumUp=1;
        for(int tick=move.source.hits.get(0).tick();tick<=move.source.hits.get(1).tick();tick++) {
            var up=rootRotation(move.path,tick).transformDirection(new Vector3f(0,-1,0));
            minimumUp=Math.min(minimumUp,-up.y);
        }
        assertTrue(minimumUp<-.5,"The inverted phase of the source flip was discarded");
        var upright=rootRotation(move.path,25).transformDirection(new Vector3f(0,-1,0));
        assertTrue(-upright.y>.95,"Body never completes the flip before the second swing");
    }
    @Test void comboTransitionsPreserveLegContinuityWithoutBlendingWeaponHands() throws Exception {
        String[][] transitions={{"gs_charge_1","gs_charged_slash"},{"gs_charged_slash","gs_charge_2"},
                {"gs_tackle_2","gs_charge_3"},{"db_blade_cut","db_sheath_cut"},{"db_flurry","db_finisher"},
                {"gs_true_slash","greatsword"},{"db_aerial","db_landing"}};
        for(var pair:transitions) {
            var previous=body(pair[0],8); var target=body(pair[1],0);
            var playback=new ActionAnimationPlayer(target.getData(),0,previous,false);
            for(String leg:new String[]{"rightLeg","leftLeg"}) {
                var before=part(previous,leg,Vec3f.ZERO).transformDirection(new Vector3f(0,1,0));
                var after=part(playback,leg,Vec3f.ZERO).transformDirection(new Vector3f(0,1,0));
                assertTrue(before.distance(after)<.0001,pair[1]);
            }
            assertEquals(target.get3DTransform("rightArm",TransformType.ROTATION,0,Vec3f.ZERO).getX(),
                    playback.get3DTransform("rightArm",TransformType.ROTATION,0,Vec3f.ZERO).getX(),.0001);
            for(int i=0;i<4;i++)playback.tick();
            target=body(pair[1],4);
            assertEquals(target.get3DTransform("leftLeg",TransformType.ROTATION,0,Vec3f.ZERO).getX(),
                    playback.get3DTransform("leftLeg",TransformType.ROTATION,0,Vec3f.ZERO).getX(),.0001);
        }
    }
    @Test void airHoldWaitsForTheServerLandingStateWithoutSnappingToStanding() throws Exception {
        for(String path:new String[]{"gs_aerial","db_aerial"}) {
            var data=body(path,0).getData();
            var playback=new ActionAnimationPlayer(data,0,null,true);
            for(int i=0;i<100;i++)playback.tick();
            playback.setupAnim(.5f);
            assertTrue(playback.isActive());
            assertEquals(data.endTick,playback.getCurrentTick());
            var end=body(path,data.endTick).get3DTransform("rightLeg",TransformType.ROTATION,0,Vec3f.ZERO);
            assertEquals(end.getX(),playback.get3DTransform("rightLeg",TransformType.ROTATION,0,Vec3f.ZERO).getX(),.0001);
            playback.stop();assertFalse(playback.isActive());
            var delayed=new ActionAnimationPlayer(data,100,null,true);
            assertEquals(data.endTick,delayed.getCurrentTick(),"Late action sync lost its held pose");
        }
    }
    @Test void greatswordGripMatchesBothHandsThroughoutEveryMotion() throws Exception {
        for (var move : GreatSwordMove.values()) {
            var mmd = motion(move.path);
            try (var json = getClass().getResourceAsStream(BASE + "player_animation/" + move.path + ".json")) {
                var data = AnimationSerializing.deserializeAnimation(json).get(0);
                for (int tick = 0; tick <= move.duration; tick++) {
                    var animation = new KeyframeAnimationPlayer(data, tick);
                    var right = part(animation, "rightArm", new Vec3f(-5, 2, 0)).transformPosition(new Vector3f(0, 9, 0));
                    var left = part(animation, "leftArm", new Vec3f(5, 2, 0)).transformPosition(new Vector3f(0, 9, 0));
                    var blade = nativeMatrix(mmd, "hardpointA", tick);
                    float rError = right.distance(blade.transformPosition(new Vector3f()));
                    float lError = left.distance(blade.transformPosition(new Vector3f(24, 0, 0)));
                    assertTrue(rError < .4, move.path + " right hand tick " + tick + " error " + rError);
                    assertTrue(lError < .4, move.path + " offhand tick " + tick + " error " + lError);
                }
            }
        }
    }
    @Test void greatswordSheathStaysAtTheBackInsteadOfFollowingEitherHand() throws Exception {
        for (var move : GreatSwordMove.values()) {
            var mmd = motion(move.path);
            try (var json = getClass().getResourceAsStream(BASE + "player_animation/" + move.path + ".json")) {
                var data = AnimationSerializing.deserializeAnimation(json).get(0);
                for (int tick = 0; tick <= move.duration; tick++) {
                    var animation = new KeyframeAnimationPlayer(data, tick);
                    var back = nativeMatrix(mmd, "hardpointB", tick).transformPosition(new Vector3f());
                    part(animation, "torso", Vec3f.ZERO).invert().transformPosition(back);
                    float error = back.distance(new Vector3f(4, -1, 3.5F));
                    assertTrue(error < .08, move.path + " sheath tick " + tick + " error " + error);
                }
            }
        }
    }
    @Test void dualBladeAndSheathMatchTheirOwnHandsThroughoutEveryMotion() throws Exception {
        for (var move : DualBladeMove.values()) {
            var mmd = motion(move.path);
            try (var json = getClass().getResourceAsStream(BASE + "player_animation/" + move.path + ".json")) {
                var data = AnimationSerializing.deserializeAnimation(json).get(0);
                for (int sample = 0; sample <= move.duration * 4; sample++) {
                    float tick = sample / 4f;
                    var animation = new ActionAnimationPlayer(data, (int) tick, null, false, 0);
                    animation.setupAnim(tick % 1);
                    for (boolean left : new boolean[]{false, true}) {
                        var hand = part(animation, left ? "leftArm" : "rightArm", new Vec3f(left ? 5 : -5, 2, 0))
                                .transformPosition(new Vector3f(0, 9, 0));
                        var grip = nativeMatrix(mmd, left ? "hardpointB" : "hardpointA", tick).transformPosition(new Vector3f());
                        float error = hand.distance(grip);
                        // Fast cuts still stay inside the four-pixel fist at sub-tick
                        // render times despite the native VMD's lower sampling rate.
                        assertTrue(error < 1, move.path + (left ? " sheath" : " blade") + " tick " + tick + " error " + error);
                    }
                }
            }
        }
    }
    private Matrix4f rootRotation(String clip,float tick) throws Exception {
        // PlayerAnimator applies body rotation before LivingEntityRenderer reflects X/Y.
        return new Matrix4f().scale(-1,-1,1).mul(part(body(clip,tick),"body",Vec3f.ZERO)).scale(-1,-1,1);
    }
    private Vector3f fullDirection(String clip,String bone,float tick) throws Exception {
        var direction=nativeMatrix(motion(clip),bone,tick).transformDirection(new Vector3f(-1,0,0)).normalize();
        return rootRotation(clip,tick).transformDirection(direction).normalize();
    }
    private void assertSourceTangent(String clip,String bone,float roll,float pitch,float tick,int duration) throws Exception {
        var a=fullDirection(clip,bone,Math.max(0,tick-.75f));
        var b=fullDirection(clip,bone,Math.min(duration,tick+.75f));
        var normal=new Vector3f(a).cross(b).normalize();
        var renderer=new Matrix4f().scale(1,-1,-1).rotateY(-(float)Math.PI/2)
                .rotateZ((float)Math.toRadians(pitch)).rotateX((float)Math.toRadians(roll));
        var effect=renderer.transformDirection(new Vector3f(0,-1,0)).normalize();
        assertTrue(normal.dot(effect)>.95,clip+" effect plane/direction mismatch at "+tick+": "+normal.dot(effect));
    }
    @Test void everyNativeEffectFollowsTheConvertedSourceWeapon() throws Exception {
        for(var move:GreatSwordMove.values())for(var hit:move.source.hits) {
            if(!hit.body())assertSourceTangent(move.path,"hardpointA",hit.roll(),hit.pitch(),hit.tick(),move.duration);
        }
        for(var move:DualBladeMove.values())for(var hit:move.strikes)
            assertSourceTangent(move.path,hit.hand()==DualBladeMove.Hand.BLADE?"hardpointA":"hardpointB",hit.roll(),hit.pitch(),hit.tick(),move.duration);
    }
    @Test void switchAxeEffectsAndBothCuttingEdgesMatchTheirSourceContacts() throws Exception {
        for (var move : SwitchAxeMove.values()) for (var hit : move.source.hits) {
            assertTrue(hit.tick()*3 >= hit.beginFrame() && hit.tick()*3 <= hit.endFrame());
            assertSourceTangent(move.path, "hardpointA", hit.roll(), hit.pitch(), hit.tick(), move.duration);
            var before = fullDirection(move.path, "hardpointA", Math.max(0, hit.tick() - .75f));
            var after = fullDirection(move.path, "hardpointA", Math.min(move.duration, hit.tick() + .75f));
            var travel = new Vector3f(after).sub(before).normalize();
            boolean sword = move.path.startsWith("sa_sword_") || move == SwitchAxeMove.MORPH_SWORD
                    || move == SwitchAxeMove.DISCHARGE || move == SwitchAxeMove.FULL_RELEASE;
            var animation = new AnimationProcessor(body(move.path, hit.tick())); animation.setTickDelta(0);
            var weapon = rootRotation(move.path, hit.tick()).mul(nativeMatrix(motion(move.path), "hardpointA", hit.tick()))
                    .mul(WeaponPose.component(animation, sword ? "swordComponent" : "axeComponent"));
            var edge = weapon.transformDirection(new Vector3f(0, sword ? 1 : -1, 0)).normalize();
            var face = weapon.transformDirection(new Vector3f(0, 0, 1)).normalize();
            assertTrue(edge.dot(travel) > .85, move.path + " edge trails the stroke");
            assertTrue(Math.abs(face.dot(travel)) < .4, move.path + " broad-face strike");
        }
    }
    @Test void chargedCutsHaveDownwardEffectsForBothTrueChargeHits() {
        for(var move:new GreatSwordMove[]{GreatSwordMove.CHARGED_SLASH,GreatSwordMove.STRONG_SLASH,GreatSwordMove.TRUE_SLASH})
            for(var hit:move.source.hits)assertTrue(Math.sin(Math.toRadians(hit.roll()))>.85,move.path);
    }
    @Test void greatswordMeetsItsSwingWithTheCuttingEdgeInsteadOfTheBroadFace() throws Exception {
        for (var move : GreatSwordMove.values()) for (var hit : move.source.hits) {
            if (hit.body()) continue;
            var before = fullDirection(move.path, "hardpointA", Math.max(0, hit.tick() - .75f));
            var after = fullDirection(move.path, "hardpointA", Math.min(move.duration, hit.tick() + .75f));
            var travel = new Vector3f(after).sub(before).normalize();
            var weapon = rootRotation(move.path, hit.tick())
                    .mul(nativeMatrix(motion(move.path), "hardpointA", hit.tick()));
            // The actual OBJ extends along -X, has its reinforced spine at +Y,
            // cutting edge at -Y, and broad steel faces normal to Z.
            var edge = weapon.transformDirection(new Vector3f(0, -1, 0)).normalize();
            var face = weapon.transformDirection(new Vector3f(0, 0, 1)).normalize();
            assertTrue(edge.dot(travel) > .85, move.path + " edge points away from the cut at " + hit.tick());
            assertTrue(Math.abs(face.dot(travel)) < .4, move.path + " strikes with its broad face at " + hit.tick());
        }
    }
}
