package com.simibubi.create.content.trains.schedule.hat;

import com.jozufozu.flywheel.util.transform.TransformStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.foundation.mixin.accessor.AgeableListModelAccessor;
import com.simibubi.create.foundation.mixin.fabric.LivingEntityRendererAccessor;
import com.simibubi.create.foundation.render.CachedBufferer;
import com.simibubi.create.foundation.utility.Couple;

import io.github.fabricators_of_create.porting_lib.mixin.accessors.client.accessor.ModelPartAccessor;
import io.github.fabricators_of_create.porting_lib.mixin.accessors.common.accessor.LivingEntityAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.RegistrationHelper;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelPart.Cube;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class TrainHatArmorLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

	public TrainHatArmorLayer(RenderLayerParent<T, M> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack ms, MultiBufferSource buffer, int light, LivingEntity entity, float limbSwing, float limbSwingAmount,
					   float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
		if (!shouldRenderOn(entity))
			return;

		M entityModel = getParentModel();
		RenderType renderType = Sheets.cutoutBlockSheet();
		ms.pushPose();

		TransformStack msr = TransformStack.cast(ms);
		TrainHatInfo info = TrainHatInfoReloadListener.getHatInfoFor(entity.getType());
		List<ModelPart> partsToHead = new ArrayList<>();

		if (entityModel instanceof AgeableListModel<?> model && entityModel instanceof io.github.fabricators_of_create.porting_lib.mixin.accessors.client.accessor.AgeableListModelAccessor access) {
			if (model.young) {
				if (access.porting_lib$scaleHead()) {
					float f = 1.5F / access.porting_lib$babyHeadScale();
					ms.scale(f, f, f);
				}
				ms.translate(0.0D, access.porting_lib$babyYHeadOffset() / 16.0F, access.porting_lib$babyZHeadOffset() / 16.0F);
			}

			ModelPart head = getHeadPart(model);
			if (head != null) {
				partsToHead.addAll(TrainHatInfo.getAdjustedPart(info, head, ""));
			}
		} else if (entityModel instanceof HierarchicalModel<?> model) {
			partsToHead.addAll(TrainHatInfo.getAdjustedPart(info, model.root(), "head"));
		}

		if (!partsToHead.isEmpty()) {
			partsToHead.forEach(part -> part.translateAndRotate(ms));

			ModelPart lastChild = partsToHead.get(partsToHead.size() - 1);
			if (!lastChild.isEmpty()) {
				Cube cube = ((ModelPartAccessor) lastChild).porting_lib$cubes().get(Mth.clamp(info.cubeIndex(), 0, ((ModelPartAccessor) lastChild).porting_lib$cubes().size() - 1));
				ms.translate(info.offset().x() / 16.0F, (cube.minY - cube.maxY + info.offset().y()) / 16.0F, info.offset().z() / 16.0F);
				float max = Math.max(cube.maxX - cube.minX, cube.maxZ - cube.minZ) / 8.0F * info.scale();
				ms.scale(max, max, max);
			}

			ms.scale(1, -1, -1);
			ms.translate(0, -2.25F / 16.0F, 0);
			msr.rotateX(-8.5F);
			BlockState air = Blocks.AIR.defaultBlockState();
			CachedBufferer.partial(AllPartialModels.TRAIN_HAT, air)
					.forEntityRender()
					.light(light)
					.renderInto(ms, buffer.getBuffer(renderType));
		}

		ms.popPose();
	}

	private boolean shouldRenderOn(LivingEntity entity) {
		if (entity == null)
			return false;
		if (entity.getCustomData()
				.contains("TrainHat"))
			return true;
		if (!entity.isPassenger())
			return false;
		if (entity instanceof Player p) {
			ItemStack headItem = p.getItemBySlot(EquipmentSlot.HEAD);
			if (!headItem.isEmpty())
				return false;
		}
		Entity vehicle = entity.getVehicle();
		if (!(vehicle instanceof CarriageContraptionEntity cce))
			return false;
		if (!cce.hasSchedule() && !(entity instanceof Player))
			return false;
		Contraption contraption = cce.getContraption();
		if (!(contraption instanceof CarriageContraption cc))
			return false;
		BlockPos seatOf = cc.getSeatOf(entity.getUUID());
		if (seatOf == null)
			return false;
		Couple<Boolean> validSides = cc.conductorSeats.get(seatOf);
		return validSides != null;
	}

	public static void registerOn(EntityRenderer<?> entityRenderer, RegistrationHelper helper) {
		if (!(entityRenderer instanceof LivingEntityRenderer<?, ?> livingRenderer))
			return;

		EntityModel<?> model = livingRenderer.getModel();

		if (!(model instanceof HierarchicalModel) && !(model instanceof AgeableListModel))
			return;

		((LivingEntityRendererAccessor) livingRenderer).create$addLayer(new TrainHatArmorLayer<>(livingRenderer));
	}

	private static ModelPart getHeadPart(AgeableListModel<?> model) {
		for (ModelPart part : ((AgeableListModelAccessor) model).create$callHeadParts())
			return part;
		for (ModelPart part : ((AgeableListModelAccessor) model).create$callBodyParts())
			return part;
		return null;
	}

}
