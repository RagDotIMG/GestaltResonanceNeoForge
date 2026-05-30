// Save this class in your mod and generate all required imports

/**
 * Made with Blockbench 5.0.7
 * Exported for Minecraft version 1.19 or later with Mojang mappings
 * @author Author
 */
public class AmenBreakModelAnimation {
	public static final AnimationDefinition wallslide = AnimationDefinition.Builder.withLength(0.0F)
		.addAnimation("Head", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-19.4451F, 43.3554F, -10.1826F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("LowerArmLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(16.3241F, 18.5336F, 9.8013F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("LowerArmRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(50.0F, 22.5F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("ArmRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-64.6404F, 4.8516F, -37.7172F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("ArmLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-22.0428F, 28.8476F, 11.0943F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("LegLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-13.5308F, -29.0616F, 15.1972F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("LegRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(20.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("AmenBreak", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-2.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("BodyB", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(6.346F, -384.757F, 2.7427F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("LegLeftLow", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-42.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("LegRightLow", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("Torso", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(-12.5F, -40.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.build();
}