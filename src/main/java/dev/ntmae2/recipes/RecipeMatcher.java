package dev.ntmae2.recipes;

import com.hbm.inventory.NTMItemHandler;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.fluid.FluidStack;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.items.machine.ItemBlueprints;
import com.hbm.module.machine.ModuleMachineBase;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class RecipeMatcher {

    private RecipeMatcher() {}

    
    static GenericRecipe find(ModuleMachineBase module, PatternRequest req) {
        List<GenericRecipe> all = module.getRecipeSet().recipeOrderedList;
        for (GenericRecipe recipe : all) {
            if (recipe == null) continue;
            if (matches(recipe, req)) return recipe;
        }
        return null;
    }

    
    static boolean poolAllows(GenericRecipe recipe, ModuleMachineBase module) {
        if (!recipe.isPooled()) return true;
        NTMItemHandler inv = module.inventory;
        for (int i = 0; i < inv.getSlots(); i++) {
            String pool = ItemBlueprints.grabPool(inv.getStackInSlot(i));
            if (pool != null && recipe.isPartOfPool(pool)) return true;
        }
        return false;
    }

    static boolean matches(GenericRecipe recipe, PatternRequest req) {
        return inputItemsMatch(recipe, req)
                && inputFluidsMatch(recipe, req)
                && outputItemsMatch(recipe, req)
                && outputFluidsMatch(recipe, req);
    }

    

    private static final class Left {
        final ItemStack one;
        long left;
        Left(ItemStack one, long left) { this.one = one; this.left = left; }
    }

    private static boolean inputItemsMatch(GenericRecipe recipe, PatternRequest req) {
        List<Left> pool = new ArrayList<>();
        req.items.forEach((key, amount) -> pool.add(new Left(key.toStack(1), amount)));

        if (recipe.inputItem != null) {
            for (AStack ingredient : recipe.inputItem) {
                if (ingredient == null) continue;
                Left hit = null;
                for (Left entry : pool) {
                    if (entry.left >= ingredient.stacksize && ingredient.matchesRecipe(entry.one, true)) { hit = entry; break; }
                }
                if (hit == null) return false;
                hit.left -= ingredient.stacksize;
            }
        }
        for (Left entry : pool) if (entry.left != 0) return false;
        return true;
    }

    private static boolean inputFluidsMatch(GenericRecipe recipe, PatternRequest req) {
        List<PatternRequest.FluidAmount> pool = new ArrayList<>(req.fluids);
        if (recipe.inputFluid != null) {
            for (FluidStack fluid : recipe.inputFluid) {
                if (fluid == null) continue;
                if (!removeFluid(pool, fluid)) return false;
            }
        }
        return pool.isEmpty();
    }

    

    private static boolean outputItemsMatch(GenericRecipe recipe, PatternRequest req) {
        List<ItemStack> pool = new ArrayList<>(req.outItems);
        if (recipe.outputItem != null) {
            for (int i = 0; i < recipe.outputItem.length; i++) {
                List<ItemStack> candidates = new ArrayList<>();
                if (recipe.outputChoices != null && i == 0) {
                    for (GenericRecipe.OutputChoice choice : recipe.outputChoices) candidates.add(choice.stack());
                } else {
                    candidates.add(recipe.outputItem[i]);
                }
                ItemStack hit = null;
                for (ItemStack have : pool) {
                    for (ItemStack want : candidates) {
                        if (want != null && !want.isEmpty() && have.getCount() == want.getCount()
                                && ItemStack.isSameItemSameComponents(have, want)) { hit = have; break; }
                    }
                    if (hit != null) break;
                }
                if (hit == null) return false;
                pool.remove(hit);
            }
        }
        return pool.isEmpty();
    }

    private static boolean outputFluidsMatch(GenericRecipe recipe, PatternRequest req) {
        List<PatternRequest.FluidAmount> pool = new ArrayList<>(req.outFluids);
        if (recipe.outputFluid != null) {
            for (FluidStack fluid : recipe.outputFluid) {
                if (fluid == null) continue;
                if (!removeFluid(pool, fluid)) return false;
            }
        }
        return pool.isEmpty();
    }

    private static boolean removeFluid(List<PatternRequest.FluidAmount> pool, FluidStack wanted) {
        for (int i = 0; i < pool.size(); i++) {
            PatternRequest.FluidAmount have = pool.get(i);
            if (have.type() == wanted.type && have.amount() == wanted.fill) {
                pool.remove(i);
                return true;
            }
        }
        return false;
    }
}