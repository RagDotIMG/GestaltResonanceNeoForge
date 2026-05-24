package net.ragdot.gestaltresonance.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.entity.BodyDoubleEntity;
import net.ragdot.gestaltresonance.common.entity.DripDropEntity;
import net.ragdot.gestaltresonance.common.entity.PhaseAfterimageEntity;
import net.ragdot.gestaltresonance.common.entity.PhaseBlossomEntity;
import net.ragdot.gestaltresonance.common.entity.PhaseMineEntity;
import net.ragdot.gestaltresonance.common.entity.PopPodEntity;
import net.ragdot.gestaltresonance.common.entity.PrimedBlockEntity;
import net.ragdot.gestaltresonance.common.entity.SpawnIllusionEntity;

public class GestaltEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, GestaltResonance.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<BodyDoubleEntity>> BODY_DOUBLE =
            ENTITY_TYPES.register("body_double", () ->
                    EntityType.Builder.<BodyDoubleEntity>of(BodyDoubleEntity::new, MobCategory.MISC)
                            .sized(0.6f, 1.8f)
                            .clientTrackingRange(64)
                            .build("body_double"));

    public static final DeferredHolder<EntityType<?>, EntityType<PrimedBlockEntity>> PRIMED_BLOCK =
            ENTITY_TYPES.register("primed_block", () ->
                    EntityType.Builder.<PrimedBlockEntity>of(PrimedBlockEntity::new, MobCategory.MISC)
                            .fireImmune()
                            .sized(0.98F, 0.98F)
                            .clientTrackingRange(10)
                            .updateInterval(10)
                            .build("primed_block"));

    public static final DeferredHolder<EntityType<?>, EntityType<PopPodEntity>> POP_POD =
            ENTITY_TYPES.register("pop_pod", () ->
                    EntityType.Builder.<PopPodEntity>of(PopPodEntity::new, MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(64)
                            .updateInterval(4)
                            .build("pop_pod"));

    public static final DeferredHolder<EntityType<?>, EntityType<DripDropEntity>> DRIP_DROP =
            ENTITY_TYPES.register("drip_drop", () ->
                    EntityType.Builder.<DripDropEntity>of(DripDropEntity::new, MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(32)
                            .updateInterval(4)
                            .build("drip_drop"));

    public static final DeferredHolder<EntityType<?>, EntityType<PhaseAfterimageEntity>> PHASE_AFTERIMAGE =
            ENTITY_TYPES.register("phase_afterimage", () ->
                    EntityType.Builder.<PhaseAfterimageEntity>of(PhaseAfterimageEntity::new, MobCategory.MISC)
                            .sized(0.6f, 1.8f)
                            .clientTrackingRange(64)
                            .build("phase_afterimage"));

    public static final DeferredHolder<EntityType<?>, EntityType<PhaseMineEntity>> PHASE_MINE =
            ENTITY_TYPES.register("phase_mine", () ->
                    EntityType.Builder.<PhaseMineEntity>of(PhaseMineEntity::new, MobCategory.MISC)
                            .sized(0.5f, 0.3f)
                            .clientTrackingRange(64)
                            .updateInterval(20)
                            .build("phase_mine"));

    public static final DeferredHolder<EntityType<?>, EntityType<SpawnIllusionEntity>> SPAWN_ILLUSION =
            ENTITY_TYPES.register("spawn_illusion", () ->
                    EntityType.Builder.<SpawnIllusionEntity>of(SpawnIllusionEntity::new, MobCategory.MISC)
                            .sized(0.6f, 1.8f)
                            .clientTrackingRange(64)
                            .updateInterval(3)
                            .build("spawn_illusion"));

    public static final DeferredHolder<EntityType<?>, EntityType<PhaseBlossomEntity>> PHASE_BLOSSOM =
            ENTITY_TYPES.register("phase_blossom", () ->
                    EntityType.Builder.<PhaseBlossomEntity>of(PhaseBlossomEntity::new, MobCategory.MISC)
                            .sized(0.3f, 0.3f)
                            .clientTrackingRange(64)
                            .updateInterval(20)
                            .build("phase_blossom"));

    public static void onAttributeCreate(EntityAttributeCreationEvent event) {
        event.put(BODY_DOUBLE.get(), BodyDoubleEntity.createAttributes().build());
        event.put(SPAWN_ILLUSION.get(), SpawnIllusionEntity.createAttributes().build());
    }
}
