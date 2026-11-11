package dev.ntmae2.recipes;

import appeng.api.AECapabilities;
import appeng.api.implementations.blockentities.ICraftingMachine;
import com.hbm.tileentity.MachineCore;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod(RecipeSwitchMod.MOD_ID)
public final class RecipeSwitchMod {

    public static final String MOD_ID = "ntm_ae2_recipes";
    public static final String NTM_NAMESPACE = "hbm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RecipeSwitchMod(IEventBus modBus) {
        modBus.addListener(RecipeSwitchMod::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        List<Block> blocks = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (NTM_NAMESPACE.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())) blocks.add(block);
        }
        event.registerBlock(AECapabilities.CRAFTING_MACHINE, RecipeSwitchMod::lookup, blocks.toArray(new Block[0]));
        LOGGER.info("Registered AE2 crafting-machine capability on {} NTM blocks", blocks.size());
    }

    
    private static @Nullable ICraftingMachine lookup(Level level, BlockPos pos, BlockState state,
                                                     @Nullable BlockEntity be, @Nullable Direction side) {
        if (level.isClientSide()) return null;
        try {
            BlockEntity core = be;
            if (core == null || !ModuleScanner.isRecipeMachineClass(core.getClass())) {
                core = MachineCore.at(level, pos, state);
            }
            if (core == null || core.isRemoved() || !ModuleScanner.isRecipeMachineClass(core.getClass())) return null;
            return new NtmCraftingMachine(core);
        } catch (RuntimeException e) {
            LOGGER.debug("Could not resolve NTM machine at {}", pos, e);
            return null;
        }
    }
}