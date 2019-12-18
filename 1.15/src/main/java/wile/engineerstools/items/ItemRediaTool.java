/*
 * @file ItemRediaTool.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2018 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * REDia multi tool.
 */
package wile.engineerstools.items;

import net.minecraft.entity.monster.ZombiePigmanEntity;
import wile.engineerstools.ModEngineersTools;
import wile.engineerstools.detail.ModAuxiliaries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.math.*;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.fluid.IFluidState;
import net.minecraft.item.*;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.*;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ToolType;
import com.google.common.collect.ImmutableList;
import wile.engineerstools.detail.ModConfig;

import javax.annotation.Nullable;
import java.util.*;

public class ItemRediaTool extends AxeItem
{
  private static int enchantability = ItemTier.DIAMOND.getEnchantability();
  private static int max_item_damage = 3000;
  private static int initial_item_damage = 1500;
  private static boolean with_torch_placing = true;
  private static boolean with_hoeing = true;
  private static boolean with_tree_felling = true;
  private static boolean with_shearing = true;
  private static double efficiency_decay[] = { 0.1, 0.8, 0.9, 1.0, 1.0, 1.3, 1.6, 1.8, 2.0, 2.0 }; // index: 0% .. 100% durability
  private static int fortune_decay[] = { 0, 0, 0, 0, 0, 0, 1, 1, 2, 3 }; // index: 0% .. 100% durability
  private static final int max_attack_cooldown_ms = 2500;
  private static float dirt_digging_speed = 14;
  private static float grass_digging_speed = 15;


  public static void on_config(boolean without_redia_torchplacing, boolean without_redia_hoeing,
                               boolean without_redia_tree_chopping, int durability, String efficiency_curve,
                               String fortune_curve, int redia_tool_initial_durability_percent)
  {
    boolean with_torch_placing = !without_redia_torchplacing;
    with_hoeing = !without_redia_hoeing;
    with_tree_felling = !without_redia_tree_chopping;
    max_item_damage = MathHelper.clamp(durability, 750, 4000);
    initial_item_damage = (max_item_damage * (100-MathHelper.clamp(redia_tool_initial_durability_percent, 1, 100))) / 100;
    ModEngineersTools.LOGGER.info("REDIA tool config: "
            + (with_torch_placing?"":"no-") + "torch-placing, "
            + (with_hoeing?"":"no-") + "hoeing, "
            + (with_tree_felling?"":"no-") + "tree-felling,"
            + (" durability:"+max_item_damage + ", initial-durability:"+redia_tool_initial_durability_percent+"% (dmg="+initial_item_damage+")")
    );
    // Efficiency
    {
      String[] sc = efficiency_curve.replaceAll("^[,0-9]", "").split(",");
      if(sc.length > 0) {
        ArrayList<Double> dc = new ArrayList<Double>();
        boolean parsing_ok = true;
        for(int i=0; (i<sc.length) && (i<efficiency_decay.length); ++i) {
          try {
            dc.add(MathHelper.clamp(Double.parseDouble(sc[i]), 20, 250));
          } catch(Exception ex) {
            ModEngineersTools.logger().error("Parsing efficiency curve failed (number at index "+i+")");
            parsing_ok = false;
            break;
          }
        }
        if(!parsing_ok) {
          ModEngineersTools.logger().warn("Using default efficiency curve due to error.");
        } else {
          for(int i=1; i<dc.size(); ++i) {
            if(dc.get(i) < dc.get(i-1)) dc.set(i, dc.get(i-1));
          }
          while(dc.size() < efficiency_decay.length) dc.add(dc.get(dc.size()-1));
          for(int i=0; i<dc.size(); ++i) efficiency_decay[i] = dc.get(i)/100;
        }
      }
      StringBuilder confout = new StringBuilder();
      confout.append("REDIA tool efficiency curve: [");
      for(int i=0; i<efficiency_decay.length; ++i) confout.append(Math.round(efficiency_decay[i]*100)).append(",");
      confout.deleteCharAt(confout.length()-1).append("]");
      ModEngineersTools.LOGGER.info(confout.toString());
    }
    // Fortune
    {
      String[] sc = efficiency_curve.replaceAll("^[,0-9]", "").split(",");
      if(sc.length > 0) {
        ArrayList<Integer> dc = new ArrayList<Integer>();
        boolean parsing_ok = true;
        for(int i=0; (i<sc.length) && (i<fortune_decay.length); ++i) {
          try {
            dc.add(MathHelper.clamp(Integer.parseInt(sc[i]), 0, 3));
          } catch(Exception ex) {
            ModEngineersTools.logger().error("Parsing fortune curve failed (number at index "+i+")");
            parsing_ok = false;
            break;
          }
        }
        if(!parsing_ok) {
          ModEngineersTools.logger().warn("Using default fortune curve due to error.");
        } else {
          for(int i=1; i<dc.size(); ++i) {
            if(dc.get(i) < dc.get(i-1)) dc.set(i, dc.get(i-1));
          }
          while(dc.size() < fortune_decay.length) dc.add(dc.get(dc.size()-1));
        }
      }
      StringBuilder confout = new StringBuilder();
      confout.append("REDIA tool fortune curve: [");
      for(int i=0; i<fortune_decay.length; ++i) confout.append(Math.round(fortune_decay[i])).append(",");
      confout.deleteCharAt(confout.length()-1).append("]");
      ModEngineersTools.LOGGER.info(confout.toString());
    }
  }

  // -------------------------------------------------------------------------------------------------------------------

  public ItemRediaTool(Item.Properties properties)
  {
    super(ItemTier.DIAMOND, 5, -3, properties
      .addToolType(ToolType.AXE, ItemTier.DIAMOND.getHarvestLevel())
      .addToolType(ToolType.PICKAXE, ItemTier.DIAMOND.getHarvestLevel())
      .addToolType(ToolType.SHOVEL, ItemTier.DIAMOND.getHarvestLevel())
      .maxStackSize(1)
      .rarity(Rarity.UNCOMMON)
      .defaultMaxDamage(max_item_damage)
    );
  }

  @Override
  @OnlyIn(Dist.CLIENT)
  public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag)
  { ModAuxiliaries.Tooltip.addInformation(stack, world, tooltip, flag, true); }

  @Override
  @OnlyIn(Dist.CLIENT)
  public boolean hasEffect(ItemStack stack)
  { return false; } // don't show enchantment glint, looks awful. Also nice to cause some confusion ;-)

  // -------------------------------------------------------------------------------------------------------------------

  @Override
  public int getItemEnchantability()
  { return enchantability; }

  @Override
  public boolean getIsRepairable(ItemStack toRepair, ItemStack repair)
  { return super.getIsRepairable(toRepair, repair); }

  @Override
  public int getHarvestLevel(ItemStack stack, net.minecraftforge.common.ToolType tool, @Nullable PlayerEntity player, @Nullable BlockState blockState)
  { return ItemTier.DIAMOND.getHarvestLevel(); }

  @Override
  public boolean isDamageable()
  { return true; }

  @Override
  public int getMaxDamage(ItemStack stack)
  { return max_item_damage; }

  @Override
  public boolean canHarvestBlock(BlockState block)
  { return true; }

  @Override
  public float getSmeltingExperience(ItemStack item)
  { return 0; }

  @Override
  public boolean canDisableShield(ItemStack stack, ItemStack shield, LivingEntity entity, LivingEntity attacker)
  { return true; }

  @Override
  public boolean isBookEnchantable(ItemStack stack, ItemStack book)
  { return true; }

  @Override
  public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment)
  {
    if(enchantment == Enchantments.FORTUNE) return false;
    if(enchantment == Enchantments.EFFICIENCY) return false;
    if(enchantment == Enchantments.KNOCKBACK) return true;
    if(enchantment == Enchantments.LOOTING) return true;
    if(enchantment == Enchantments.SHARPNESS) return true;
    if(enchantment == Enchantments.FIRE_ASPECT) return true;
    return enchantment.type.canEnchantItem(stack.getItem());
  }

  @Override
  public Collection<ItemGroup> getCreativeTabs()
  { return ModConfig.isOptedOut(this) ? (ModBlockItem.DISABLED_TABS) : (ModBlockItem.ENABLED_TABS); }

  // -------------------------------------------------------------------------------------------------------------------

  private void resetBlockHitCount(ItemStack stack)
  {
    CompoundNBT nbt = stack.getTag();
    if(nbt==null) nbt = new CompoundNBT();
    if(nbt.contains("lhbc")) nbt.putInt("lhbc", 0);
    stack.setTag(nbt);
  }

  public void onCreated(ItemStack stack, World world, PlayerEntity player)
  { stack.setDamage(initial_item_damage); }

  @Override
  public boolean hitEntity(ItemStack stack, LivingEntity target, LivingEntity attacker)
  {
    resetBlockHitCount(stack);
    return super.hitEntity(stack, target, attacker);  // already 2 item dmg, ok
  }

  @Override
  public boolean onLeftClickEntity(ItemStack stack, PlayerEntity player, Entity entity)
  {
    resetBlockHitCount(stack);
    if(entity instanceof VillagerEntity) return true; // Cancel attacks for villagers.
    if((entity instanceof TameableEntity) && (((TameableEntity)entity).isTamed()) && (((TameableEntity)entity).isOwner(player))) return true; // Don't hit own pets
    if(((entity instanceof ZombiePigmanEntity)) && ((ZombiePigmanEntity)entity).getAttackTarget() == null) return true; // noone wants to accidentally step them on the foot.
    if(player.world.isRemote) return false; // only server side evaluation
    if(isAttackingEnabled(stack)) return false;
    if(entity instanceof CreatureEntity) return ((((CreatureEntity)entity).getAttackTarget() == null)); // don't attack bloody pigmen if they are not angry.
    return false; // allow attacking
  }

  @Override
  public ActionResultType onItemUse(ItemUseContext context)
  {
    final Direction facing = context.getFace();
    final Hand hand = context.getHand();
    final PlayerEntity player = context.getPlayer();
    final World world = context.getWorld();
    final BlockPos pos = context.getPos();
    final Vec3d hitvec = context.getHitVec();
    resetBlockHitCount(player.getHeldItem(hand));
    ActionResultType rv;
    if(context.getPlayer().func_225608_bj_() /*isSneaking()*/) {
      rv = tryPlantSnipping(player, world, pos, hand, facing, hitvec);
      if(rv != ActionResultType.PASS) return rv;
      if(facing == Direction.UP) {
        rv = tryDigOver(player, world, pos, hand, facing, hitvec);
        if(rv != ActionResultType.PASS) return rv;
      } else if(facing.getAxis().isHorizontal()) {
        rv = tryTorchPlacing(context);
        if(rv != ActionResultType.PASS) return rv;
      } else {
        rv = super.onItemUse(context); // axe log stripping
      }
    } else {
      rv = tryTorchPlacing(context);
      if(rv != ActionResultType.PASS) return rv;
    }
    return rv;
  }

  @Override
  public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, PlayerEntity player)
  {
    if(!player.world.isRemote) setFortune(stack);
    return false;
  }

  @Override
  public boolean onBlockDestroyed(ItemStack tool, World world, BlockState state, BlockPos pos, LivingEntity player)
  {
    if(!world.isRemote) {
      if(state.getBlockHardness(world, pos) != 0.0f) {
        tool.damageItem(1, player, (p)->{p.sendBreakAnimation(player.getActiveHand());});
      }
      {
        CompoundNBT nbt = tool.getTag();
        if(nbt==null) nbt = new CompoundNBT();
        nbt.putInt("lhbh", state.getBlock().hashCode());
        nbt.putLong("atdisat", System.currentTimeMillis());
        tool.setTag(nbt);
      }
      if(with_tree_felling && (player instanceof PlayerEntity) && (player.func_225608_bj_() /*isSneaking()*/)) {
        if(tryTreeFelling(world, state, pos, player)) return true;
      }
    }
    return true;
  }

  @Override
  public float getDestroySpeed(ItemStack stack, BlockState state)
  {
    // @todo: add "state.isIn(BlockTags.DIRT_LIKE) ||"
    if(state.isIn(BlockTags.SAND)) return dirt_digging_speed;
    if(state.getBlock() == Blocks.GRASS) return grass_digging_speed;
    double scaler = 1.0;
    {
      int hitcount_max = 2;
      CompoundNBT nbt = stack.getTag();
      if(nbt==null) nbt = new CompoundNBT();
      int lasthitblock = nbt.getInt("lhbh");
      if(lasthitblock != 0) { // this also means it's on the server (`onBlockDestroyed()`)
        int hitcount = nbt.getInt("lhbc");
        int hit_id = state.getBlock().hashCode();
        if(lasthitblock==hit_id) {
          hitcount = Math.min(hitcount+1, hitcount_max);
        } else {
          lasthitblock = hit_id;
          hitcount = 0;
        }
        nbt.putInt("lhbh", lasthitblock);
        nbt.putInt("lhbc", hitcount);
        stack.setTag(nbt);
        scaler = (hitcount<hitcount_max) ? 0.5 : 1.0;
      }
    }
    return (float)(
      ((double)efficiency) * scaler *
        efficiency_decay[
          (int)MathHelper.clamp(relativeDurability(stack) * efficiency_decay.length,0,efficiency_decay.length-1)
          ]
    );
  }

  @Override
  @SuppressWarnings("deprecation")
  public boolean itemInteractionForEntity(ItemStack tool, PlayerEntity player, LivingEntity entity, Hand hand)
  {
    if(entity.world.isRemote) return false;
    setFortune(player.getHeldItem(hand));
    return tryEntityShearing(tool, player, entity, hand);
  }

  // -------------------------------------------------------------------------------------------------------------------

  private boolean isAttackingEnabled(ItemStack stack)
  {
    CompoundNBT nbt = stack.getTag();
    if((nbt==null) || (!nbt.contains("atdisat"))) return true;
    if(Math.abs(System.currentTimeMillis()-nbt.getLong("atdisat")) < max_attack_cooldown_ms) return false;
    nbt.remove("atdisat");
    stack.setTag(nbt);
    return true;
  }

  private void setFortune(ItemStack stack)
  {
    int fortune = durabilityDependentFortune(stack);
    Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
    if(fortune > 0) {
      enchantments.put(Enchantments.FORTUNE, fortune);
    } else if(enchantments.containsKey(Enchantments.FORTUNE)) {
      enchantments.remove(Enchantments.FORTUNE);
    }
    EnchantmentHelper.setEnchantments(enchantments, stack);
  }

  private double relativeDurability(ItemStack stack)
  { return MathHelper.clamp(((double)(getMaxDamage(stack)-getDamage(stack)))/((double)getMaxDamage(stack)), 0,1.0); }

  private int durabilityDependentFortune(ItemStack stack)
  { return fortune_decay[MathHelper.clamp((int)(relativeDurability(stack)*fortune_decay.length), 0, fortune_decay.length-1)]; }

  @SuppressWarnings("deprecation")
  private boolean tryEntityShearing(ItemStack tool, PlayerEntity player, LivingEntity entity, Hand hand)
  {
    if((entity.world.isRemote) || (!(entity instanceof net.minecraftforge.common.IShearable))) return false;
    net.minecraftforge.common.IShearable target = (net.minecraftforge.common.IShearable)entity;
    BlockPos pos = new BlockPos(entity.getPosition());
    if (target.isShearable(tool, entity.world, pos)) {
      List<ItemStack> drops = target.onSheared(tool, entity.world, pos, EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, tool));
      Random rand = new java.util.Random();
      drops.forEach(d -> {
        ItemEntity ent = entity.entityDropItem(d, 1.0F);
        ent.setMotion(ent.getMotion().add((double)((rand.nextFloat() - rand.nextFloat()) * 0.1F), (double)(rand.nextFloat() * 0.05F), (double)((rand.nextFloat() - rand.nextFloat()) * 0.1F)));
      });
      tool.damageItem(1, entity, e -> e.sendBreakAnimation(hand));
      player.world.playSound(null, pos, SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.BLOCKS, 0.8f, 1.1f);
    }
    return true;
  }

  @SuppressWarnings("deprecation")
  private ActionResultType tryPlantSnipping(PlayerEntity player, World world, BlockPos pos, Hand hand, Direction facing, Vec3d hitvec)
  {
    if(!with_shearing) return ActionResultType.PASS;
    final ItemStack tool = player.getHeldItem(hand);
    if(tool.getItem()!=this) return ActionResultType.PASS;
    final BlockState state = world.getBlockState(pos);
    final Block block = state.getBlock();
    // replace with tag?
    if((!state.isIn(BlockTags.LEAVES)) && (block != Blocks.COBWEB) && (block != Blocks.GRASS) && (block != Blocks.FERN)
      && (block != Blocks.DEAD_BUSH) && (block != Blocks.VINE) && (block != Blocks.TRIPWIRE) && (!block.isIn(BlockTags.WOOL))
    ) return ActionResultType.PASS;
    ItemEntity ie = new ItemEntity(world, pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, new ItemStack(block.asItem()));
    ie.setDefaultPickupDelay();
    world.addEntity(ie);
    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 1|2|8);
    tool.damageItem(1, player, (p)->{p.sendBreakAnimation(player.getActiveHand());});
    world.playSound(player, pos, SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.BLOCKS, 0.8f, 1.1f);
    return ActionResultType.SUCCESS;
  }

  private ActionResultType tryTorchPlacing(ItemUseContext context)
  {
    PlayerEntity player = context.getPlayer();
    Hand hand = context.getHand();
    if(!with_torch_placing) return ActionResultType.PASS;
    for(int i = 0; i < player.inventory.getSizeInventory(); ++i) {
      ItemStack stack = player.inventory.getStackInSlot(i);
      if((!stack.isEmpty()) && (stack.getItem() == Blocks.TORCH.asItem())) {
        ItemStack tool = player.getHeldItem(hand);
        player.setHeldItem(hand, stack);
        ItemUseContext torch_context = new ItemUseContext(context.getPlayer(), context.getHand(), new BlockRayTraceResult(context.getHitVec(), context.getFace(), context.getPos(), context.func_221533_k()));
        ActionResultType r = stack.getItem().onItemUse(torch_context);
        player.setHeldItem(hand, tool);
        return r;
      }
    }
    return ActionResultType.PASS;
  }

  private ActionResultType tryDigOver(PlayerEntity player, World world, BlockPos pos, Hand hand, Direction facing, Vec3d hitvec)
  {
    if(!with_hoeing) return ActionResultType.PASS;
    if(world.getTileEntity(pos) != null) return ActionResultType.PASS;
    final BlockState state = world.getBlockState(pos);
    BlockState replaced = state;
    final Block block = state.getBlock();
    if((block instanceof GrassBlock) || (block==Blocks.DIRT)) {
      replaced = Blocks.FARMLAND.getDefaultState();
    } else if(block instanceof FarmlandBlock) {
      replaced = Blocks.COARSE_DIRT.getDefaultState();
    } else if(block==Blocks.COARSE_DIRT) {
      replaced = Blocks.GRASS_PATH.getDefaultState();
    } else if(block instanceof GrassPathBlock) {
      replaced = Blocks.DIRT.getDefaultState();
    }
    if(replaced != state) {
      world.playSound(player, pos, SoundEvents.ITEM_HOE_TILL, SoundCategory.BLOCKS, 0.8f, 1.1f);
      if(!world.isRemote)
      {
        world.setBlockState(pos, replaced,1|2);
        ItemStack stack = player.getHeldItem(hand);
        if(stack.getItem() == this) stack.damageItem(1, player, (p)->{p.sendBreakAnimation(player.getActiveHand());});
      }
      return ActionResultType.SUCCESS;
    } else {
      return ActionResultType.PASS;
    }
  }

  // -------------------------------------------------------------------------------------------------------------------
  // Fun algorithm coding ,)
  // -------------------------------------------------------------------------------------------------------------------

  private boolean tryTreeFelling(World world, BlockState state, BlockPos pos, LivingEntity player)
  {
    if((!state.isNormalCube(world, pos)) || (state.getMaterial() != Material.WOOD)) return false;
    if(world.isRemote) return true;
    if(!state.getBlock().getTags().contains(new ResourceLocation("minecraft","logs"))) return false;
    chopTree(world, state, pos, player);
    return true;
  }

  private static final List<Vec3i> hoffsets = ImmutableList.of(
    new Vec3i( 1,0, 0), new Vec3i( 1,0, 1), new Vec3i( 0,0, 1),
    new Vec3i(-1,0, 1), new Vec3i(-1,0, 0), new Vec3i(-1,0,-1),
    new Vec3i( 0,0,-1), new Vec3i( 1,0,-1)
  );

  private List<BlockPos> findBlocksAround(final World world, final BlockPos centerPos, final BlockState leaf_type_state, final Set<BlockPos> checked, int recursion_left)
  {
    ArrayList<BlockPos> to_decay = new ArrayList<BlockPos>();
    for(int y=-1; y<=1; ++y) {
      final BlockPos layer = centerPos.add(0,y,0);
      for(Vec3i v:hoffsets) {
        BlockPos pos = layer.add(v);
        if((!checked.contains(pos)) && (world.getBlockState(pos).getBlock()==leaf_type_state.getBlock())) {
          checked.add(pos);
          to_decay.add(pos);
          if(recursion_left > 0) {
            to_decay.addAll(findBlocksAround(world, pos, leaf_type_state, checked, recursion_left-1));
          }
        }
      }
    }
    return to_decay;
  }

  private static boolean isSameLog(BlockState a, BlockState b)
  { return (a.getBlock()==b.getBlock()); } // "FF, fortunately flattened"

  private static boolean isLeaves(BlockState state)
  {
    if(state.getBlock() instanceof LeavesBlock) return true;
    if(state.getBlock().getTags().contains(new ResourceLocation("minecraft","leaves"))) return true;
    return false;
  }

  private void breakBlock(World world, BlockPos pos, LivingEntity entity)
  {
    BlockState state = world.getBlockState(pos);
    if(entity instanceof PlayerEntity) {
      ((PlayerEntity)entity).addExhaustion(0.005F);
    }
    Block.spawnDrops(state, world, pos, null, entity, new ItemStack(this));
    IFluidState fstate = world.getFluidState(pos);
    if(world.setBlockState(pos, fstate.getBlockState(), 1|2|8)) {
      //state.getBlock().onPlayerDestroy(world, pos, state);
    }
  }

  private void chopTree(World world, BlockState broken_state, BlockPos startPos, LivingEntity player)
  {
    final Block broken_block = broken_state.getBlock();
    if(!(broken_block instanceof LogBlock)) return;
    ItemStack tool = player.getHeldItemMainhand();
    if(tool.getItem() != this) tool = player.getHeldItemOffhand();
    if(tool.getItem() != this) return;
    final int max_broken_blocks = (tool.getMaxDamage()-tool.getDamage()) * 2/3;
    final long ymin = startPos.getY();
    final long max_leaf_distance = 6;
    Set<BlockPos> checked = new HashSet<BlockPos>();
    ArrayList<BlockPos> to_break = new ArrayList<BlockPos>();
    ArrayList<BlockPos> to_decay = new ArrayList<BlockPos>();
    checked.add(startPos);
    // Initial simple layer-up search of same logs. This forms the base corpus, and only leaves and
    // leaf-enclosed logs attached to this corpus may be broken/decayed.
    {
      LinkedList<BlockPos> queue = new LinkedList<BlockPos>();
      LinkedList<BlockPos> upqueue = new LinkedList<BlockPos>();
      queue.add(startPos);
      int cutlevel = 0;
      int steps_left = 64;
      while(!queue.isEmpty() && (--steps_left >= 0)) {
        final BlockPos pos = queue.removeFirst();
        // Vertical search
        final BlockPos uppos = pos.up();
        final BlockState upstate = world.getBlockState(uppos);
        if(!checked.contains(uppos)) {
          checked.add(uppos);
          if(isSameLog(upstate, broken_state)) {
            // Up is log
            upqueue.add(uppos);
            to_break.add(uppos);
            steps_left = 64;
          } else {
            boolean isleaf = isLeaves(upstate);
            if(isleaf || world.isAirBlock(uppos) || (upstate.getBlock() instanceof VineBlock)) {
              if(isleaf) to_decay.add(uppos);
              // Up is air, check adjacent for diagonal up (e.g. Accacia)
              for(Vec3i v:hoffsets) {
                final BlockPos p = uppos.add(v);
                if(checked.contains(p)) continue;
                checked.add(p);
                final BlockState st = world.getBlockState(p);
                final Block bl = st.getBlock();
                if(isSameLog(st, broken_state)) {
                  queue.add(p);
                  to_break.add(p);
                } else if(isLeaves(st)) {
                  to_decay.add(p);
                }
              }
            }
          }
        }
        // Lateral search
        for(Vec3i v:hoffsets) {
          final BlockPos p = pos.add(v);
          if(checked.contains(p)) continue;
          checked.add(p);
          if(p.distanceSq(new BlockPos(startPos.getX(), p.getY(), startPos.getZ())) > (3+cutlevel*cutlevel)) continue;
          final BlockState st = world.getBlockState(p);
          final Block bl = st.getBlock();
          if(isSameLog(st, broken_state)) {
            queue.add(p);
            to_break.add(p);
          } else if(isLeaves(st)) {
            to_decay.add(p);
          }
        }
        if(queue.isEmpty() && (!upqueue.isEmpty())) {
          queue = upqueue;
          upqueue = new LinkedList<BlockPos>();
          ++cutlevel;
        }
      }
    }
    {
      // Determine lose logs between the leafs
      for(BlockPos pos:to_decay) {
        int dist = 1;
        to_break.addAll(findBlocksAround(world, pos, broken_state, checked, dist));
      }
    }
    if(!to_decay.isEmpty()) {
      final BlockState leaf_type_state = world.getBlockState(to_decay.get(0));
      final ArrayList<BlockPos> leafs = to_decay;
      to_decay = new ArrayList<BlockPos>();
      for(BlockPos pos:leafs) {
        int dist = 2;
        to_decay.add(pos);
        to_decay.addAll(findBlocksAround(world, pos, leaf_type_state, checked, dist));
      }
    }
    checked.remove(startPos);
    for(BlockPos pos:to_break) breakBlock(world, pos, player);
    for(BlockPos pos:to_decay) breakBlock(world, pos, player);
    {
      // And now the bill.
      int dmg = (to_break.size()*6/5)+(to_decay.size()/10)-1;
      if(dmg < 1) dmg = 1;
      tool.damageItem(dmg, player, (p)->{p.sendBreakAnimation(player.getActiveHand());});
      if(player instanceof PlayerEntity) {
        float exhaustion = MathHelper.clamp(((float)dmg) / 8, 0.5f, 20f);
        ((PlayerEntity)player).addExhaustion(exhaustion);
      }
    }
  }
}
