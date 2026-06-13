package com.buildingwand;

import com.buildingwand.item.BuildingWandItem;
import com.buildingwand.worksite.WorksiteBlock;
import com.buildingwand.worksite.WorksiteDecorationBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModItems {

    // ── Building Wand ─────────────────────────────────────────────────────────

    private static final ResourceKey<Item> BUILDING_WAND_KEY = ResourceKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(BuildingWandMod.MOD_ID, "building_wand")
    );
    public static final BuildingWandItem BUILDING_WAND = new BuildingWandItem(
            new Item.Properties().stacksTo(1).setId(BUILDING_WAND_KEY)
    );

    // ── Worksite Post ─────────────────────────────────────────────────────────

    private static final ResourceKey<Block> WORKSITE_POST_BLOCK_KEY = ResourceKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(BuildingWandMod.MOD_ID, "worksite_post")
    );
    private static final ResourceKey<Item> WORKSITE_POST_ITEM_KEY = ResourceKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(BuildingWandMod.MOD_ID, "worksite_post")
    );
    private static final ResourceKey<Block> WORKSITE_FRAME_BLOCK_KEY = ResourceKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(BuildingWandMod.MOD_ID, "worksite_frame")
    );
    private static final ResourceKey<Block> WORKSITE_MATERIAL_PILE_BLOCK_KEY = ResourceKey.create(
            Registries.BLOCK,
            Identifier.fromNamespaceAndPath(BuildingWandMod.MOD_ID, "worksite_material_pile")
    );

    public static final WorksiteBlock WORKSITE_POST = new WorksiteBlock(
            BlockBehaviour.Properties.of()
                    .setId(WORKSITE_POST_BLOCK_KEY)
                    .noOcclusion()
                    .strength(0.5f)
    );
    public static final WorksiteDecorationBlock WORKSITE_FRAME = new WorksiteDecorationBlock(
            BlockBehaviour.Properties.of()
                    .setId(WORKSITE_FRAME_BLOCK_KEY)
                    .noOcclusion()
                    .strength(0.1f)
    );
    public static final WorksiteDecorationBlock WORKSITE_MATERIAL_PILE = new WorksiteDecorationBlock(
            BlockBehaviour.Properties.of()
                    .setId(WORKSITE_MATERIAL_PILE_BLOCK_KEY)
                    .noOcclusion()
                    .strength(0.1f)
    );

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, BUILDING_WAND_KEY, BUILDING_WAND);

        Registry.register(BuiltInRegistries.BLOCK, WORKSITE_POST_BLOCK_KEY, WORKSITE_POST);
        Registry.register(BuiltInRegistries.ITEM, WORKSITE_POST_ITEM_KEY,
                new BlockItem(WORKSITE_POST,
                        new Item.Properties().setId(WORKSITE_POST_ITEM_KEY)));
        Registry.register(BuiltInRegistries.BLOCK, WORKSITE_FRAME_BLOCK_KEY, WORKSITE_FRAME);
        Registry.register(BuiltInRegistries.BLOCK, WORKSITE_MATERIAL_PILE_BLOCK_KEY, WORKSITE_MATERIAL_PILE);
    }
}
