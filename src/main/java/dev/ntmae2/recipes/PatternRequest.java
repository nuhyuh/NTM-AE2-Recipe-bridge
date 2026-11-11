package dev.ntmae2.recipes;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PatternRequest {

    record FluidAmount(FluidType type, int amount) {}

    final Map<AEItemKey, Long> items = new LinkedHashMap<>();
    final List<FluidAmount> fluids = new ArrayList<>();
    final List<ItemStack> outItems = new ArrayList<>();
    final List<FluidAmount> outFluids = new ArrayList<>();

    private PatternRequest() {}

    
    static PatternRequest of(IPatternDetails pattern, KeyCounter[] inputs) {
        PatternRequest req = new PatternRequest();

        Map<AEFluidKey, Long> rawFluids = new LinkedHashMap<>();
        for (KeyCounter counter : inputs) {
            for (Object2LongMap.Entry<AEKey> entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount <= 0) continue;
                if (key instanceof AEItemKey item) {
                    req.items.merge(item, amount, Long::sum);
                } else if (key instanceof AEFluidKey fluid) {
                    rawFluids.merge(fluid, amount, Long::sum);
                } else {
                    return null;
                }
            }
        }
        for (Map.Entry<AEFluidKey, Long> e : rawFluids.entrySet()) {
            FluidAmount mapped = mapFluid(e.getKey(), e.getValue());
            if (mapped == null) return null;
            req.fluids.add(mapped);
        }
        for (long amount : req.items.values()) if (amount > Integer.MAX_VALUE) return null;

        for (GenericStack out : pattern.getOutputs()) {
            if (out == null) continue;
            AEKey key = out.what();
            if (key instanceof AEItemKey item) {
                if (out.amount() > Integer.MAX_VALUE) return null;
                req.outItems.add(item.toStack((int) out.amount()));
            } else if (key instanceof AEFluidKey fluid) {
                FluidAmount mapped = mapFluid(fluid, out.amount());
                if (mapped == null) return null;
                req.outFluids.add(mapped);
            } else {
                return null;
            }
        }
        return req;
    }

    private static FluidAmount mapFluid(AEFluidKey key, long amount) {
        if (amount <= 0 || amount > Integer.MAX_VALUE) return null;
        FluidType type = Fluids.fromVanilla(key.getFluid());
        if (type == null || type == Fluids.NONE) return null;
        return new FluidAmount(type, (int) amount);
    }
}