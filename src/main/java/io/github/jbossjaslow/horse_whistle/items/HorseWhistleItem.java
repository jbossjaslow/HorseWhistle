package io.github.jbossjaslow.horse_whistle.items;

import io.github.jbossjaslow.horse_whistle.HorseWhistle;
import io.github.jbossjaslow.horse_whistle.util.HotBarUtil;
import io.github.jbossjaslow.horse_whistle.util.NBTUtil;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HorseWhistleItem extends Item {
    private static final String HORSE_ID_KEY = "horse_id";
    private static final String HORSE_NAME_KEY = "horse_name";

    private static final int ITEM_COOLDOWN = 20; // ticks?

	/*
	##################################################

	PUBLIC METHODS

	##################################################
	 */

    public HorseWhistleItem(FabricItemSettings settings) {
        super(
                settings
                        .maxDamage(HorseWhistle.CONFIG.durability())
                        .rarity(Rarity.RARE)
        );
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        super.use(world, user, hand);

        ItemStack stack = user.getStackInHand(hand);

        // We cannot be on the client
        if (user.getWorld().isClient()) return TypedActionResult.fail(stack);

        if (user.getPose() == EntityPose.CROUCHING) {
            if (NBTUtil.hasNBTFor(stack, HORSE_ID_KEY)) {
                HotBarUtil.displayActionBarText(
                        "Removed attunement from " + NBTUtil.getNBTFrom(stack, HORSE_NAME_KEY),
                        user,
                        Formatting.GREEN);
                NBTUtil.removeNBTFrom(stack, HORSE_ID_KEY);
                NBTUtil.removeNBTFrom(stack, HORSE_NAME_KEY);
                user.getWorld().playSound(
                        null,
                        user.getBlockPos(),
                        SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                        SoundCategory.MASTER,
                        0.5f,
                        0.5f);
                return TypedActionResult.consume(stack);
            } else return TypedActionResult.pass(stack);
        }

        if (NBTUtil.hasNBTFor(stack, HORSE_ID_KEY)) {
            user.getItemCooldownManager().set(this, ITEM_COOLDOWN);
            boolean[] isBroken = { false };
            stack.damage(1, user, (p) -> {
                p.sendToolBreakStatus(hand);
                isBroken[0] = true;
            });
            if (isBroken[0]) {
                // doing it this way because there's no way(?) to
                // return from the overarching function directly from the callback above
                return TypedActionResult.fail(stack);
            }

            String associatedHorseIdString = NBTUtil.getNBTFrom(stack, HORSE_ID_KEY);
            double radius = HorseWhistle.CONFIG.searchRadius();
            double xPos = user.getX();
            double yPos = user.getY();
            double zPos = user.getZ();
            Box searchArea = new Box(
                    xPos - radius,
                    yPos - radius,
                    zPos - radius,
                    xPos + radius,
                    yPos + radius,
                    zPos + radius);
            List<HorseEntity> horses = world.getEntitiesByType(
                    EntityType.HORSE,
                    searchArea,
                    EntityPredicates.VALID_LIVING_ENTITY);
            for (HorseEntity h : horses) {
                if (h.getUuidAsString().equals(associatedHorseIdString)) {
                    teleportHorse(h, user, world);
                    return TypedActionResult.consume(stack);
                }
            }
            HotBarUtil.displayActionBarText(
                    "Could not find " + NBTUtil.getNBTFrom(stack, HORSE_NAME_KEY),
                    user,
                    Formatting.GREEN);
        }

        return TypedActionResult.fail(stack);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        super.useOnEntity(stack, user, entity, hand);
        user.getItemCooldownManager().set(this, ITEM_COOLDOWN);

        // We cannot be on the client to check the UUID of the player
        if (user.getWorld().isClient()) return ActionResult.FAIL;

        if (entity.getType() != EntityType.HORSE || NBTUtil.hasNBTFor(stack, HORSE_ID_KEY))
            return ActionResult.PASS;

        HorseEntity horseEntity = (HorseEntity) entity;

        if (horseEntity.isTame() && horseEntity.getOwnerUuid() == user.getUuid()) {
            user.getWorld().playSound(
                    null,
                    user.getBlockPos(),
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    SoundCategory.MASTER,
                    0.5f,
                    0.5f);

            NBTUtil.writeNBTTo(stack, HORSE_ID_KEY, horseEntity.getUuidAsString());
            if (horseEntity.hasCustomName()) {
                NBTUtil.writeNBTTo(stack, HORSE_NAME_KEY, horseEntity.getCustomName().getString());
            } else {
                NBTUtil.writeNBTTo(stack, HORSE_NAME_KEY, horseEntity.getName().getString());
            }

            HotBarUtil.displayActionBarText(
                    "Attuned whistle to " + NBTUtil.getNBTFrom(stack, HORSE_NAME_KEY),
                    user,
                    Formatting.GREEN);
            return ActionResult.success(false);
        } else {
            return ActionResult.PASS;
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        super.appendTooltip(stack, world, tooltip, context);

        if (NBTUtil.hasNBTFor(stack, HORSE_NAME_KEY)) {
            String tooltipText = "Attuned to " + NBTUtil.getNBTFrom(stack, HORSE_NAME_KEY);
            tooltip.add(Text.translatable(tooltipText).formatted(Formatting.GRAY));
        }
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return super.hasGlint(stack) || NBTUtil.hasNBTFor(stack, HORSE_ID_KEY);
    }

	/*
	##################################################

	PRIVATE METHODS

	##################################################
	 */

    private void teleportHorse(HorseEntity horse, PlayerEntity player, World world) {
        double xPos = player.getX();
        double yPos = player.getY();
        double zPos = player.getZ();
        // TODO: make teleport random, ensure it teleports to a valid block

        int randomX = horse.getRandom().nextInt(10) - 5;
        int randomZ = horse.getRandom().nextInt(10) - 5;

        horse.teleport(xPos + randomX, yPos, zPos + randomZ);
        world.playSound(
                null,
                player.getBlockPos(),
                SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT,
                SoundCategory.MASTER,
                1f,
                1f);
    }
}

