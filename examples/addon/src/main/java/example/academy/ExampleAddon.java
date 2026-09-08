package example.academy;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.academy.api.common.ability.*;
import org.academy.api.common.ability.data.SkillStateType;
import org.academy.api.common.ability.program.*;
import org.academy.api.common.damage.*;
import org.academy.api.common.registries.AcademyRegistrations;
import org.academy.api.common.registries.Registries;
import org.academy.api.server.ability.program.ProgramActionContext;
import org.academy.api.server.damage.AbilityDamageService;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mod(ExampleAddon.MOD_ID)
public final class ExampleAddon {
    public static final String MOD_ID = "academy_api_example";
    public static final AcademyRegistrations CONTENT = AcademyRegistrations.create(MOD_ID);
    public static final ResourceKey<AbilityCategory> CATEGORY_KEY =
            ResourceKey.create(Registries.Keys.ABILITY_CATEGORIES, id("cryokinesis"));
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, id("frost"));
    public static final SkillStateType<Integer> CAST_COUNT = SkillStateType.of(id("cast_count"), Codec.INT, () -> 0);
    public static final ResourceKey<DamageType> DIRECT_TYPE = ResourceKey.create(
            net.minecraft.core.registries.Registries.DAMAGE_TYPE, id("direct_frost"));
    public static final ResourceKey<DamageType> TRUE_TYPE = ResourceKey.create(
            net.minecraft.core.registries.Registries.DAMAGE_TYPE, id("true_frost"));
    public static final Identifier ENTRY_ID = id("program/cryokinesis/entry/on_cast");
    public static final Identifier ACTION_ID = id("program/cryokinesis/action/focus");

    public static final net.neoforged.neoforge.registries.DeferredHolder<AbilityDamageProfile, AbilityDamageProfile> FROST =
            CONTENT.damageProfiles().register("frost", () -> AbilityDamageProfile.builder(DAMAGE_TYPE)
                    .settlement(DamageSettlement.STANDARD).build());
    public static final net.neoforged.neoforge.registries.DeferredHolder<AbilityDamageProfile, AbilityDamageProfile> DIRECT =
            CONTENT.damageProfiles().register("direct_frost", () -> AbilityDamageProfile.builder(DIRECT_TYPE)
                    .settlement(DamageSettlement.DIRECT).bypassAbsorption(true).build());
    public static final net.neoforged.neoforge.registries.DeferredHolder<AbilityDamageProfile, AbilityDamageProfile> TRUE =
            CONTENT.damageProfiles().register("true_frost", () -> AbilityDamageProfile.builder(TRUE_TYPE)
                    .settlement(DamageSettlement.TRUE_HEALTH).bypassAbsorption(true).build());
    public static final net.neoforged.neoforge.registries.DeferredHolder<AbilityCategory, AbilityCategory> CATEGORY =
            CONTENT.categories().register("cryokinesis", () -> AbilityCategory.builder()
                    .translationKey("ability.academy_api_example.cryokinesis")
                    .icon(Identifier.withDefaultNamespace("textures/item/snowball.png"))
                    .defaultDamageProfile(FROST.getKey())
                    .program(ProgramProfile.standard(ENTRY_ID)).build());

    public static final net.neoforged.neoforge.registries.DeferredHolder<Skill, ExampleSkill> PRIMARY =
            CONTENT.skills().register("frost_bolt", () -> new ExampleSkill(Skill.Builder.of(CATEGORY_KEY)
                    .level(AbilityLevel.LEVEL1).cpCost(5).proficiencyProfile(SkillProficiencyProfile.NONE)
                    .stateType(CAST_COUNT).icon(Identifier.withDefaultNamespace("textures/item/snowball.png"))));
    // Same Java implementation, registered through the native path, with a deferred dependency.
    public static final DeferredRegister<Skill> NATIVE_SKILLS = DeferredRegister.create(Registries.Keys.SKILLS, MOD_ID);
    public static final net.neoforged.neoforge.registries.DeferredHolder<Skill, ExampleSkill> SECONDARY =
            NATIVE_SKILLS.register("focus", () -> new ExampleSkill(Skill.Builder.of(CATEGORY_KEY)
                    .level(AbilityLevel.LEVEL2).cpCost(10).dependsOn(PRIMARY.getKey())
                    .optionallyDependsOn(ResourceKey.create(Registries.Keys.SKILLS, id("optional_missing")))
                    .damageType(net.minecraft.world.damagesource.DamageTypes.MAGIC)
                    .proficiencyProfile(SkillProficiencyProfile.NONE).stateType(CAST_COUNT)
                    .icon(Identifier.withDefaultNamespace("textures/item/snowball.png"))));

    static {
        CONTENT.programNodes().register("program/cryokinesis/entry/on_cast", () -> ProgramNodes.manualEntry(CATEGORY_KEY));
        CONTENT.programNodes().register("program/cryokinesis/action/focus", FocusNode::new);
    }

    public ExampleAddon(IEventBus modBus) {
        CONTENT.bind(modBus);
        NATIVE_SKILLS.register(modBus);
    }

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    public static final class ExampleSkill extends Skill {
        public ExampleSkill(Builder builder) { super(builder); }
        public boolean hit(ServerPlayer player, LivingEntity target) {
            return executeActive(player, (_, _) ->
                    AbilityDamageService.apply(player, target, this, AbilityDamageService.Request.of(4)));
        }
    }

    public static final class FocusNode implements ProgramNodeExtension<Integer> {
        @Override public Codec<Integer> configurationCodec() { return Codec.intRange(1, 200).fieldOf("ticks").codec(); }
        @Override public int schemaVersion() { return 1; }
        @Override public ProgramNodeRole role() { return ProgramNodeRole.ACTION; }
        @Override public ProgramNodePurity purity() { return ProgramNodePurity.ACTION; }
        @Override public ProgramNodeScope scope() {
            return new ProgramNodeScope(Set.of(CATEGORY_KEY.identifier()), Set.of(SECONDARY.getId()));
        }
        @Override public ProgramNodeSchema schema(Integer configuration) {
            return new ProgramNodeSchema(List.of(ProgramPortDefinition.requiredInput("flow", ProgramValueTypes.FLOW)),
                    List.of(ProgramPortDefinition.output("flow", ProgramValueTypes.FLOW)));
        }
        @Override public ProgramNodeEditorMetadata editorMetadata() {
            var json = new JsonObject();
            json.addProperty("ticks", 20);
            return new ProgramNodeEditorMetadata(json, ProgramNodeEditorMetadata.Group.ACTION,
                    "node.academy_api_example.focus", "screen.academy.program.port.", true, Map.of());
        }
        @Override public ProgramNodeExecution<Integer> execution() {
            return (context, ticks, _) -> {
                var caster = (LivingEntity) context.targetResolver().orElseThrow().caster();
                context.submit(new ProgramAction() {
                    @Override public ResourceKey<Skill> requiredSkill() { return SECONDARY.getKey(); }
                    @Override public float cpCost() { return 10; }
                    @Override public List<LivingEntity> targets() { return List.of(caster); }
                    @Override public ProgramEffect apply(ProgramActionContext action) {
                        var count = action.skill().state(action.caster(), CAST_COUNT).orElseThrow();
                        action.skill().updateState(action.caster(), CAST_COUNT, count + 1);
                        var wasGlowing = caster.isCurrentlyGlowing();
                        caster.setGlowingTag(true);
                        return ProgramEffect.lasting(ticks, () -> caster.setGlowingTag(wasGlowing));
                    }
                });
                return ProgramNodeStep.next("flow");
            };
        }
    }
}
