/*
 * @file ItemTools.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Basic item functionality for mod items.
 */
package wile.engineerstools.items;

import wile.engineerstools.ModEngineersTools;
import wile.engineerstools.detail.ModAuxiliaries;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

public class ItemTools extends Item
{
  ItemTools(String registryName)
  {
    super();
    setRegistryName(ModEngineersTools.MODID, registryName);
    setTranslationKey(ModEngineersTools.MODID + "." + registryName);
    setCreativeTab(ModEngineersTools.CREATIVE_TAB_ENGINEERSTOOLS);
    setMaxStackSize(64);
    setHasSubtypes(false);
  }

  @Override
  @SideOnly(Side.CLIENT)
  public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag)
  { ModAuxiliaries.Tooltip.addInformation(stack, world, tooltip, flag, true); }

}
