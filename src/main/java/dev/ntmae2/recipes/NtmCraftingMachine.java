package dev.ntmae2.recipes;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import com.hbm.inventory.fluid.tank.FluidTankNTM;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.module.machine.ModuleMachineBase;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NtmCraftingMachine implements ICraftingMachine {

    private final BlockEntity be;

    NtmCraftingMachine(BlockEntity be) {
        this.be = be;
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        Item item = be.getBlockState().getBlock().asItem();
        AEItemKey icon = item == Items.AIR ? null : AEItemKey.of(item);
        return new PatternContainerGroup(icon, be.getBlockState().getBlock().getName(), List.of());
    }

    @Override
    public boolean acceptsPlans() {
        return !be.isRemoved() && !ModuleScanner.modules(be).isEmpty();
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, Direction ejectionDirection) {
        if (be.isRemoved() || be.getLevel() == null || be.getLevel().isClientSide()) return false;

        PatternRequest request = PatternRequest.of(pattern, inputs);
        if (request == null) return false;

        List<ModuleMachineBase> lanes = new ArrayList<>();
        List<GenericRecipe> recipes = new ArrayList<>();
        for (ModuleMachineBase lane : ModuleScanner.modules(be)) {
            GenericRecipe recipe = RecipeMatcher.find(lane, request);
            if (recipe == null) continue;
            lanes.add(lane);
            recipes.add(recipe);
        }

        
        for (int i = 0; i < lanes.size(); i++) {
            ModuleMachineBase lane = lanes.get(i);
            GenericRecipe recipe = recipes.get(i);
            if (recipe.getInternalName().equals(lane.recipe) && tryInsert(lane, recipe, request, false)) return true;
        }

        
        for (int i = 0; i < lanes.size(); i++) {
            ModuleMachineBase lane = lanes.get(i);
            GenericRecipe recipe = recipes.get(i);
            if (recipe.getInternalName().equals(lane.recipe)) continue;
            if (!RecipeMatcher.poolAllows(recipe, lane)) continue;
            if (!isIdle(lane) || !outputsCompatible(lane, recipe)) continue;
            if (tryInsert(lane, recipe, request, true)) {
                RecipeSwitchMod.LOGGER.debug("AE2 pattern switched {} lane {} to recipe {}",
                        be.getBlockState().getBlock(), lane.index, recipe.getInternalName());
                return true;
            }
        }
        return false;
    }

    

    private boolean tryInsert(ModuleMachineBase lane, GenericRecipe recipe, PatternRequest request, boolean switchRecipe) {
        String oldName = lane.recipe;
        boolean oldRestricted = lane.restrictedMode;
        double oldProgress = lane.progress;

        if (switchRecipe) {
            lane.setRecipe(recipe.getInternalName(), false);
            lane.progress = 0D;
        }
        
        lane.setupTanks(recipe);

        boolean ok = false;
        try {
            Map<Integer, ItemStack> plan = planItems(lane, request);
            if (plan != null && fluidsFit(lane, recipe, request)) {
                for (Map.Entry<Integer, ItemStack> e : plan.entrySet()) {
                    lane.inventory.setStackInSlot(e.getKey(), e.getValue());
                }
                if (recipe.inputFluid != null && lane.inputTanks != null) {
                    for (int i = 0; i < recipe.inputFluid.length; i++) {
                        FluidTankNTM tank = lane.inputTanks[i];
                        tank.setFill(tank.getFill() + recipe.inputFluid[i].fill);
                    }
                }
                be.setChanged();
                ok = true;
            }
        } finally {
            if (!ok && switchRecipe) {
                lane.setRecipe(oldName, oldRestricted);
                lane.progress = oldProgress;
                restoreTanks(lane);
            }
        }
        return ok;
    }

    
    private static Map<Integer, ItemStack> planItems(ModuleMachineBase lane, PatternRequest request) {
        Map<Integer, ItemStack> work = new LinkedHashMap<>();
        if (request.items.isEmpty()) return work;
        if (lane.inputSlots == null) return null;
        for (int slot : lane.inputSlots) work.put(slot, lane.inventory.getStackInSlot(slot).copy());

        for (Map.Entry<AEItemKey, Long> entry : request.items.entrySet()) {
            AEItemKey key = entry.getKey();
            ItemStack proto = key.toStack(1);
            long left = entry.getValue();

            for (int pass = 0; pass < 2 && left > 0; pass++) {
                for (int slot : lane.inputSlots) {
                    if (left <= 0) break;
                    ItemStack have = work.get(slot);
                    if (pass == 0 && (have.isEmpty() || !ItemStack.isSameItemSameComponents(have, proto))) continue;
                    if (pass == 1 && !have.isEmpty()) continue;

                    int limit = Math.min(lane.inventory.getSlotLimit(slot), proto.getMaxStackSize());
                    int room = limit - have.getCount();
                    int take = (int) Math.min(room, left);
                    if (take <= 0) continue;

                    if (have.isEmpty()) work.put(slot, key.toStack(take));
                    else have.grow(take);
                    left -= take;
                }
            }
            if (left > 0) return null;
        }
        return work;
    }

    private static boolean fluidsFit(ModuleMachineBase lane, GenericRecipe recipe, PatternRequest request) {
        if (recipe.inputFluid == null || recipe.inputFluid.length == 0) return true;
        if (lane.inputTanks == null || lane.inputTanks.length < recipe.inputFluid.length) return false;
        for (int i = 0; i < recipe.inputFluid.length; i++) {
            FluidTankNTM tank = lane.inputTanks[i];
            if (tank == null || tank.getTankType() != recipe.inputFluid[i].type) return false;
            if (tank.getFill() + recipe.inputFluid[i].fill > tank.getMaxFill()) return false;
        }
        return true;
    }

    private static void restoreTanks(ModuleMachineBase lane) {
        GenericRecipe old = lane.getRecipe();
        if (old != null) {
            lane.setupTanks(old);
            return;
        }
        if (lane.inputTanks != null) for (FluidTankNTM t : lane.inputTanks) if (t != null) t.resetTank();
        if (lane.outputTanks != null) for (FluidTankNTM t : lane.outputTanks) if (t != null && t.getFill() == 0) t.resetTank();
    }

    

    private static boolean isIdle(ModuleMachineBase lane) {
        if (lane.progress > 1e-9) return false;
        if (lane.inputSlots != null) {
            for (int slot : lane.inputSlots) if (!lane.inventory.getStackInSlot(slot).isEmpty()) return false;
        }
        if (lane.inputTanks != null) {
            for (FluidTankNTM tank : lane.inputTanks) if (tank != null && tank.getFill() > 0) return false;
        }
        return true;
    }

    
    private static boolean outputsCompatible(ModuleMachineBase lane, GenericRecipe recipe) {
        if (recipe.outputItem != null && lane.outputSlots != null) {
            for (int i = 0; i < Math.min(recipe.outputItem.length, lane.outputSlots.length); i++) {
                ItemStack in = lane.inventory.getStackInSlot(lane.outputSlots[i]);
                if (in.isEmpty()) continue;
                boolean same = false;
                if (recipe.outputChoices != null && i == 0) {
                    for (GenericRecipe.OutputChoice c : recipe.outputChoices) same |= ItemStack.isSameItemSameComponents(in, c.stack());
                } else {
                    same = ItemStack.isSameItemSameComponents(in, recipe.outputItem[i]);
                }
                if (!same) return false;
            }
        }
        if (recipe.outputFluid != null && lane.outputTanks != null) {
            for (int i = 0; i < Math.min(recipe.outputFluid.length, lane.outputTanks.length); i++) {
                FluidTankNTM tank = lane.outputTanks[i];
                if (tank == null || tank.getFill() <= 0) continue;
                if (tank.getTankType() != recipe.outputFluid[i].type) return false;
            }
        }
        return true;
    }
}