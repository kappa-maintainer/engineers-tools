package wile.engineerstools.eapi.baubles;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Optional;


@Optional.Interface(modid="baubles", iface="baubles.api.IBauble", striprefs=true)
public interface IBaubleItem extends baubles.api.IBauble
{
  @Override
  @Optional.Method(modid = "baubles")
  default baubles.api.BaubleType getBaubleType(ItemStack stack)
  { try { return baubles.api.BaubleType.TRINKET; } catch(Exception e) { return baubles.api.BaubleType.RING; } }

  @Override
  @Optional.Method(modid="baubles")
  default void onWornTick(ItemStack stack, EntityLivingBase entity)
  { if((!stack.isEmpty()) && (entity instanceof EntityPlayer)) this.onBaubleTick(stack, entity); }

  /**
   * API tick encapsulation
   */
  default void onBaubleTick(ItemStack stack, EntityLivingBase entity)
  {}

}
