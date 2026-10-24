package com.mki.nightsiege.game;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Everything a kit throws: axes, spears, flash grenades, oil flasks and the lure
 * arrow of the archer's second ultimate. It flies as its item; what happens on
 * impact depends on its kind and lives in {@link GainGear}.
 */
public class KitProjectile extends ThrowableItemProjectile {

    public static EntityType<KitProjectile> TYPE;

    public static final String AXE = "axe";
    public static final String SPEAR = "spear";
    public static final String FLASH = "flash";
    public static final String OIL = "oil";
    public static final String LURE = "lure";
    /** The demolition expert's mortar shell and the alchemist's potion (fired by a tower). */
    public static final String SHELL = "shell";
    public static final String BREW = "brew";

    private String kind = "";
    /** For tower shots: which tower, whose, tier and scales (see TrapService). */
    private String data = "";

    public KitProjectile(EntityType<? extends KitProjectile> type, Level level) {
        super(type, level);
    }

    public KitProjectile(Level level, LivingEntity owner, ItemStack item, String kind) {
        super(TYPE, owner, level, item);
        this.kind = kind;
    }

    /** A shot without a thrower (from a tower). */
    public KitProjectile(Level level, double x, double y, double z, ItemStack item, String kind) {
        super(TYPE, x, y, z, level, item);
        this.kind = kind;
    }

    public String data() {
        return data;
    }

    public void setData(String data) {
        this.data = data == null ? "" : data;
    }

    public static void register() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ModContent.id("kit_projectile"));
        TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
                EntityType.Builder.<KitProjectile>of(KitProjectile::new, MobCategory.MISC)
                        .sized(0.3f, 0.3f).clientTrackingRange(6).updateInterval(5).build(key));
    }

    public String kind() {
        return kind;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SNOWBALL;
    }

    @Override
    protected double getDefaultGravity() {
        return switch (kind) {
            case LURE -> 0.01;
            case AXE, SPEAR -> 0.045;
            default -> 0.03;
        };
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level() instanceof ServerLevel server) {
            GainGear.onProjectileHit(server, this, hit.getEntity(), hit.getLocation(), null);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (level() instanceof ServerLevel server) {
            GainGear.onProjectileHit(server, this, null, hit.getLocation(), hit);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide()) {
            discard();
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putString("ns_kind", kind);
        out.putString("ns_data", data);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        kind = in.getStringOr("ns_kind", "");
        data = in.getStringOr("ns_data", "");
    }
}
