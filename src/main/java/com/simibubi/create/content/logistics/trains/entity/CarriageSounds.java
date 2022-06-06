package com.simibubi.create.content.logistics.trains.entity;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.trains.entity.Carriage.DimensionalCarriageEntity;
import com.simibubi.create.foundation.utility.animation.LerpedFloat;
import com.simibubi.create.foundation.utility.animation.LerpedFloat.Chaser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class CarriageSounds {

	CarriageContraptionEntity entity;

	LerpedFloat distanceFactor;
	LerpedFloat speedFactor;
	LerpedFloat approachFactor;
	LerpedFloat seatCrossfade;

	LoopingSound minecartEsqueSound;
	LoopingSound sharedWheelSound;
	LoopingSound sharedWheelSoundSeated;

	boolean arrived;

	int tick;
	int prevSharedTick;

	public CarriageSounds(CarriageContraptionEntity entity) {
		this.entity = entity;
		distanceFactor = LerpedFloat.linear();
		speedFactor = LerpedFloat.linear();
		approachFactor = LerpedFloat.linear();
		seatCrossfade = LerpedFloat.linear();
		arrived = true;
	}

	public void tick(DimensionalCarriageEntity dce) {
		Minecraft mc = Minecraft.getInstance();
		Entity camEntity = mc.cameraEntity;
		if (camEntity == null)
			return;

		Vec3 leadingAnchor = dce.leadingAnchor();
		Vec3 trailingAnchor = dce.trailingAnchor();
		if (leadingAnchor == null || trailingAnchor == null)
			return;

		tick++;

		Vec3 cam = camEntity.getEyePosition();
		Vec3 contraptionMotion = entity.position()
			.subtract(entity.getPrevPositionVec());
		Vec3 combinedMotion = contraptionMotion.subtract(camEntity.getDeltaMovement());

		if (arrived && contraptionMotion.length() > 0.01f)
			arrived = false;
		arrived |= entity.isStalled();

		Vec3 toBogey1 = leadingAnchor.subtract(cam);
		Vec3 toBogey2 = trailingAnchor.subtract(cam);
		double distance1 = toBogey1.length();
		double distance2 = toBogey2.length();

		Vec3 toCarriage = distance1 > distance2 ? toBogey2 : toBogey1;
		double distance = Math.min(distance1, distance2);
		Vec3 soundLocation = cam.add(toCarriage);

		double dot = toCarriage.normalize()
			.dot(combinedMotion.normalize());

		speedFactor.chase(contraptionMotion.length(), .25f, Chaser.exp(.05f));
		distanceFactor.chase(Mth.clampedLerp(100, 0, (distance - 3) / 64d), .25f, Chaser.exp(50f));
		approachFactor.chase(Mth.clampedLerp(50, 200, .5f * (dot + 1)), .25f, Chaser.exp(10f));
		seatCrossfade.chase(camEntity.getVehicle() instanceof CarriageContraptionEntity ? 1 : 0, .1f, Chaser.EXP);

		speedFactor.tickChaser();
		distanceFactor.tickChaser();
		approachFactor.tickChaser();
		seatCrossfade.tickChaser();

		minecartEsqueSound = playIfMissing(mc, minecartEsqueSound, AllSoundEvents.TRAIN.getMainEvent());
		sharedWheelSound = playIfMissing(mc, sharedWheelSound, AllSoundEvents.TRAIN2.getMainEvent());
		sharedWheelSoundSeated = playIfMissing(mc, sharedWheelSoundSeated, AllSoundEvents.TRAIN3.getMainEvent());

		float volume = Math.min(Math.min(speedFactor.getValue(), distanceFactor.getValue() / 100),
			approachFactor.getValue() / 300 + .0125f);

		if (entity.carriageIndex == 0) {
			float v = volume * (1 - seatCrossfade.getValue() * .35f) * .75f;
			if ((3 + tick) % 4 == 0)
				AllSoundEvents.STEAM.playAt(entity.level, soundLocation, v * ((tick + 7) % 8 == 0 ? 0.75f : .45f),
					1.17f, false);
			if (tick % 16 == 0)
				AllSoundEvents.STEAM.playAt(entity.level, soundLocation, v * 1.5f, .8f, false);
		}

		if (!arrived && speedFactor.getValue() < .002f) {
			arrived = true;
			entity.level.playLocalSound(soundLocation.x, soundLocation.y, soundLocation.z, SoundEvents.LAVA_EXTINGUISH,
				SoundSource.NEUTRAL, .25f, .78f, false);
			entity.level.playLocalSound(soundLocation.x, soundLocation.y, soundLocation.z,
				SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.NEUTRAL, .2f, 1.5f, false);
			AllSoundEvents.STEAM.playAt(entity.level, soundLocation, .75f, .5f, false);
		}

		float pitchModifier = ((entity.getId() * 10) % 13) / 36f;

		volume = Math.min(volume, distanceFactor.getValue() / 800);

		float pitch = Mth.clamp(speedFactor.getValue() * 2 + .25f, .75f, 1.95f) - pitchModifier;
		float pitch2 = Mth.clamp(speedFactor.getValue() * 2, 0.75f, 1.25f) - pitchModifier;

		minecartEsqueSound.setPitch(pitch * 1.5f);

		volume = Math.min(volume, distanceFactor.getValue() / 1000);

		Train train = entity.getCarriage().train;
		for (Carriage carriage : train.carriages) {
			DimensionalCarriageEntity mainDCE = carriage.getDimensionalIfPresent(entity.level.dimension());
			if (mainDCE == null)
				continue;
			CarriageContraptionEntity mainEntity = mainDCE.entity.get();
			if (mainEntity == null)
				continue;
			if (mainEntity.sounds == null)
				mainEntity.sounds = new CarriageSounds(mainEntity);
			mainEntity.sounds.submitSharedSoundVolume(soundLocation, volume);
			if (carriage != entity.getCarriage())
				finalizeSharedVolume(0);
			return;
		}

		finalizeSharedVolume(volume);
		minecartEsqueSound.setLocation(soundLocation);
		sharedWheelSound.setPitch(pitch2);
		sharedWheelSound.setLocation(soundLocation);
		sharedWheelSoundSeated.setPitch(pitch2);
		sharedWheelSoundSeated.setLocation(soundLocation);
	}

	private LoopingSound playIfMissing(Minecraft mc, LoopingSound loopingSound, SoundEvent sound) {
		if (loopingSound == null) {
			loopingSound = new LoopingSound(sound, SoundSource.NEUTRAL);
			mc.getSoundManager()
				.play(loopingSound);
		}
		return loopingSound;
	}

	public void submitSharedSoundVolume(Vec3 location, float volume) {
		Minecraft mc = Minecraft.getInstance();
		minecartEsqueSound = playIfMissing(mc, minecartEsqueSound, AllSoundEvents.TRAIN.getMainEvent());
		sharedWheelSound = playIfMissing(mc, sharedWheelSound, AllSoundEvents.TRAIN2.getMainEvent());
		sharedWheelSoundSeated = playIfMissing(mc, sharedWheelSoundSeated, AllSoundEvents.TRAIN3.getMainEvent());

		boolean approach = true;

		if (tick != prevSharedTick) {
			prevSharedTick = tick;
			approach = false;
		} else if (sharedWheelSound.getVolume() > volume)
			return;

		Vec3 currentLoc = new Vec3(minecartEsqueSound.getX(), minecartEsqueSound.getY(), minecartEsqueSound.getZ());
		Vec3 newLoc = approach ? currentLoc.add(location.subtract(currentLoc)
			.scale(.125f)) : location;

		minecartEsqueSound.setLocation(newLoc);
		sharedWheelSound.setLocation(newLoc);
		sharedWheelSoundSeated.setLocation(newLoc);
		finalizeSharedVolume(volume);
	}

	public void finalizeSharedVolume(float volume) {
		float crossfade = seatCrossfade.getValue();
		minecartEsqueSound.setVolume((1 - crossfade * .65f) * volume / 2);
		volume = Math.min(volume, Math.max((speedFactor.getValue() - .25f) / 4 + 0.01f, 0));
		sharedWheelSoundSeated.setVolume(volume * crossfade);
		sharedWheelSound.setVolume(volume * (1 - crossfade) * 1.5f);
	}

	public void stop() {
		if (minecartEsqueSound != null)
			minecartEsqueSound.stopSound();
		if (sharedWheelSound != null)
			sharedWheelSound.stopSound();
		if (sharedWheelSoundSeated != null)
			sharedWheelSoundSeated.stopSound();
	}

	class LoopingSound extends AbstractTickableSoundInstance {

		protected LoopingSound(SoundEvent p_119606_, SoundSource p_119607_) {
			super(p_119606_, p_119607_);
			attenuation = Attenuation.LINEAR;
			looping = true;
			delay = 0;
			volume = 0.0001f;
		}

		@Override
		public void tick() {}

		public void setVolume(float volume) {
			this.volume = volume;
		}

		public void setPitch(float pitch) {
			this.pitch = pitch;
		}

		public void setLocation(Vec3 location) {
			x = location.x;
			y = location.y;
			z = location.z;
		}

		public void stopSound() {
			Minecraft.getInstance()
				.getSoundManager()
				.stop(this);
		}

	}

}