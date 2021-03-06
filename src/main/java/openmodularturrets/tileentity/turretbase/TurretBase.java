package openmodularturrets.tileentity.turretbase;

import cofh.api.energy.EnergyStorage;
import cofh.api.energy.IEnergyHandler;
import cpw.mods.fml.common.Optional;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.SimpleComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import openmodularturrets.compatability.ModCompatibility;
import openmodularturrets.handler.ConfigHandler;
import openmodularturrets.util.TurretHeadUtil;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.visnet.VisNetHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static openmodularturrets.util.PlayerUtil.*;

@Optional.InterfaceList({
        @Optional.Interface(iface = "dan200.computercraft.api.peripheral.IPeripheral", modid = "ComputerCraft"),
        @Optional.Interface(iface = "li.cil.oc.api.network.SimpleComponent", modid = "OpenComputers"),
        @Optional.Interface(iface = "thaumcraft.api.aspects.IAspectContainer", modid = "Thaumcraft"),
        @Optional.Interface(iface = "thaumcraft.api.aspects.IEssentiaTransport", modid = "Thaumcraft")})

public abstract class TurretBase extends TileEntity implements IEnergyHandler, IInventory, SimpleComponent, ISidedInventory, IEssentiaTransport, IAspectContainer, IPeripheral {
    public int trustedPlayerIndex = 0;
    public ItemStack camoStack;
    //For concealment
    public boolean shouldConcealTurrets;
    //For multiTargeting
    public boolean multiTargeting = false;
    protected EnergyStorage storage;
    protected ItemStack[] inv;
    protected int yAxisDetect;
    protected boolean attacksMobs;
    protected boolean attacksNeutrals;
    protected boolean attacksPlayers;
    protected String owner = "";
    protected String ownerName = "";
    protected List<TrustedPlayer> trustedPlayers;
    protected int ticks;
    protected boolean active;
    protected boolean inverted;
    protected boolean redstone;
    protected boolean checkRedstone = false;
    protected boolean computerAccessible = false;
    protected float amountOfPotentia = 0F;
    protected float maxAmountOfPotentia = ConfigHandler.getPotentiaAddonCapacity();
    protected ArrayList<IComputerAccess> comp;

    public TurretBase(int MaxEnergyStorage, int MaxIO) {
        super();
        this.yAxisDetect = 2;
        this.storage = new EnergyStorage(MaxEnergyStorage, MaxIO);
        this.attacksMobs = true;
        this.attacksNeutrals = true;
        this.attacksPlayers = false;
        this.trustedPlayers = new ArrayList<TrustedPlayer>();
        this.inv = new ItemStack[this.getSizeInventory()];
        this.inverted = true;
        this.active = true;
    }

    public static void updateRedstoneReactor(TurretBase base) {
        if (!TurretHeadUtil.hasRedstoneReactor(base)) {
            return;
        }

        if (ConfigHandler.getRedstoneReactorAddonGen() < (base.getMaxEnergyStored(
                ForgeDirection.UNKNOWN) - base.getEnergyStored(ForgeDirection.UNKNOWN))) {
            //Prioritise redstone blocks
            ItemStack redstoneBlock = TurretHeadUtil.useSpecificItemStackBlockFromBase(base, new ItemStack(
                    Blocks.redstone_block));

            if (redstoneBlock == null) {
                redstoneBlock = TurretHeadUtil.getSpecificItemFromInvExpanders(base.getWorldObj(),
                                                                               new ItemStack(Blocks.redstone_block),
                                                                               base);
            }

            if (redstoneBlock != null && ConfigHandler.getRedstoneReactorAddonGen() * 9 < (base.getMaxEnergyStored(
                    ForgeDirection.UNKNOWN) - base.getEnergyStored(ForgeDirection.UNKNOWN))) {
                base.storage.modifyEnergyStored(ConfigHandler.getRedstoneReactorAddonGen() * 9);
                return;
            }

            ItemStack redstone = TurretHeadUtil.useSpecificItemStackItemFromBase(base, Items.redstone);

            if (redstone == null) {
                redstone = TurretHeadUtil.getSpecificItemFromInvExpanders(base.getWorldObj(),
                                                                          new ItemStack(Items.redstone), base);
            }

            if (redstone != null) {
                base.storage.modifyEnergyStored(ConfigHandler.getRedstoneReactorAddonGen());
            }
        }
    }

    private int getMaxEnergyStorageWithExtenders() {
        int tier = getBaseTier();
        switch (tier) {
            case 1:
                return ConfigHandler.getBaseTierOneMaxCharge() + TurretHeadUtil.getPowerExpanderTotalExtraCapacity(
                        this.worldObj, this.xCoord, this.yCoord, this.zCoord);
            case 2:
                return ConfigHandler.getBaseTierTwoMaxCharge() + TurretHeadUtil.getPowerExpanderTotalExtraCapacity(
                        this.worldObj, this.xCoord, this.yCoord, this.zCoord);
            case 3:
                return ConfigHandler.getBaseTierThreeMaxCharge() + TurretHeadUtil.getPowerExpanderTotalExtraCapacity(
                        this.worldObj, this.xCoord, this.yCoord, this.zCoord);
            case 4:
                return ConfigHandler.getBaseTierFourMaxCharge() + TurretHeadUtil.getPowerExpanderTotalExtraCapacity(
                        this.worldObj, this.xCoord, this.yCoord, this.zCoord);
            case 5:
                return ConfigHandler.getBaseTierFiveMaxCharge() + TurretHeadUtil.getPowerExpanderTotalExtraCapacity(
                        this.worldObj, this.xCoord, this.yCoord, this.zCoord);
        }
        return 0;
    }

    public void addTrustedPlayer(String name) {
        TrustedPlayer trustedPlayer = new TrustedPlayer(name);
        trustedPlayer.uuid = getPlayerUUID(name);
        if (trustedPlayer.uuid != null) {
            for (TrustedPlayer player : trustedPlayers) {
                if (player.getName().toLowerCase().equals(name.toLowerCase()) || trustedPlayer.uuid.toString().equals(
                        owner)) {
                    return;
                }
            }
            trustedPlayers.add(trustedPlayer);
        }
    }

    public void removeTrustedPlayer(String name) {
        for (TrustedPlayer player : trustedPlayers) {
            if (player.getName().equals(name)) {
                trustedPlayers.remove(player);
                return;
            }
        }
    }

    public List<TrustedPlayer> getTrustedPlayers() {
        return trustedPlayers;
    }

    public TrustedPlayer getTrustedPlayer(String name) {
        for (TrustedPlayer trustedPlayer : trustedPlayers) {
            if (trustedPlayer.name.equals(name)) {
                return trustedPlayer;
            }
        }
        return null;
    }

    public TrustedPlayer getTrustedPlayer(UUID uuid) {
        for (TrustedPlayer trustedPlayer : trustedPlayers) {
            if (trustedPlayer.uuid.equals(uuid)) {
                return trustedPlayer;
            }
        }
        return null;
    }

    private NBTTagList getTrustedPlayersAsNBT() {
        NBTTagList nbt = new NBTTagList();
        for (TrustedPlayer trustedPlayer : trustedPlayers) {
            NBTTagCompound nbtPlayer = new NBTTagCompound();
            nbtPlayer.setString("name", trustedPlayer.name);
            nbtPlayer.setBoolean("canOpenGUI", trustedPlayer.canOpenGUI);
            nbtPlayer.setBoolean("canChangeTargeting", trustedPlayer.canChangeTargeting);
            nbtPlayer.setBoolean("admin", trustedPlayer.admin);
            if (trustedPlayer.uuid != null) {
                nbtPlayer.setString("UUID", trustedPlayer.uuid.toString());
            } else if (getPlayerUUID(trustedPlayer.name) != null) {
                nbtPlayer.setString("UUID", getPlayerUUID(trustedPlayer.name).toString());
            }
            nbt.appendTag(nbtPlayer);
        }
        return nbt;
    }

    private void buildTrustedPlayersFromNBT(NBTTagList nbt) {
        trustedPlayers.clear();
        for (int i = 0; i < nbt.tagCount(); i++) {
            if (!nbt.getCompoundTagAt(i).getString("name").equals("")) {
                NBTTagCompound nbtPlayer = nbt.getCompoundTagAt(i);
                TrustedPlayer trustedPlayer = new TrustedPlayer(nbtPlayer.getString("name"));
                trustedPlayer.canOpenGUI = nbtPlayer.getBoolean("canOpenGUI");
                trustedPlayer.canChangeTargeting = nbtPlayer.getBoolean("canChangeTargeting");
                trustedPlayer.admin = nbtPlayer.getBoolean("admin");
                if (nbtPlayer.hasKey("UUID")) {
                    trustedPlayer.uuid = getPlayerUIDUnstable(nbtPlayer.getString("UUID"));
                } else {
                    trustedPlayer.uuid = getPlayerUUID(trustedPlayer.name);
                }
                if (trustedPlayer.uuid != null) {
                    trustedPlayers.add(trustedPlayer);
                }
            } else if (nbt.getCompoundTagAt(i).getString("name").equals("")) {
                TrustedPlayer trustedPlayer = new TrustedPlayer(nbt.getStringTagAt(i));
                Logger.getGlobal().info("found legacy trusted Player: " + nbt.getStringTagAt(i));
                trustedPlayer.uuid = getPlayerUUID(trustedPlayer.name);
                if (trustedPlayer.uuid != null) {
                    trustedPlayers.add(trustedPlayer);
                }
            }
        }
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound var1 = new NBTTagCompound();
        this.writeToNBT(var1);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 2, var1);
    }

    @Override
    public void writeToNBT(NBTTagCompound par1) {
        super.writeToNBT(par1);

        par1.setInteger("maxStorage", this.storage.getMaxEnergyStored());
        par1.setInteger("energyStored", this.getEnergyStored(ForgeDirection.UNKNOWN));
        par1.setFloat("amountOfPotentia", amountOfPotentia);
        par1.setInteger("maxIO", this.storage.getMaxReceive());
        par1.setInteger("yAxisDetect", this.yAxisDetect);
        par1.setBoolean("attacksMobs", attacksMobs);
        par1.setBoolean("attacksNeutrals", attacksNeutrals);
        par1.setBoolean("attacksPlayers", attacksPlayers);
        par1.setString("owner", owner);
        if (ownerName.isEmpty() && getPlayerNameFromUUID(owner) != null) {
            ownerName = getPlayerNameFromUUID(owner);
        }
        par1.setString("ownerName", ownerName);
        par1.setTag("trustedPlayers", getTrustedPlayersAsNBT());
        par1.setBoolean("active", active);
        par1.setBoolean("inverted", inverted);
        par1.setBoolean("redstone", redstone);
        par1.setBoolean("computerAccessible", computerAccessible);
        par1.setBoolean("shouldConcealTurrets", shouldConcealTurrets);
        par1.setBoolean("multiTargeting", multiTargeting);

        NBTTagList itemList = new NBTTagList();

        for (int i = 0; i < this.inv.length; i++) {
            ItemStack stack = this.getStackInSlot(i);

            if (stack != null) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setByte("Slot", (byte) i);
                stack.writeToNBT(tag);
                itemList.appendTag(tag);
            }
        }
        par1.setTag("Inventory", itemList);

        if (camoStack != null) {
            NBTTagCompound tag2 = new NBTTagCompound();
            camoStack.writeToNBT(tag2);
            par1.setTag("CamoStack", tag2);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound par1) {
        super.readFromNBT(par1);

        this.storage.setCapacity(par1.getInteger("maxStorage"));
        this.storage.setEnergyStored(par1.getInteger("energyStored"));
        this.storage.setMaxReceive(par1.getInteger("maxIO"));
        this.amountOfPotentia = par1.getFloat("amountOfPotentia");
        this.yAxisDetect = par1.getInteger("yAxisDetect");
        this.attacksMobs = par1.getBoolean("attacksMobs");
        this.attacksNeutrals = par1.getBoolean("attacksNeutrals");
        this.attacksPlayers = par1.getBoolean("attacksPlayers");
        this.shouldConcealTurrets = par1.getBoolean("shouldConcealTurrets");
        this.multiTargeting = par1.getBoolean("multiTargeting");
        if (getPlayerUIDUnstable(par1.getString("owner")) != null) {
            this.owner = getPlayerUIDUnstable(par1.getString("owner")).toString();
        } else if (getPlayerUUID(par1.getString("owner")) != null) {
            this.owner = getPlayerUUID(par1.getString("owner")).toString();
        } else {
            Logger.getGlobal().info("Found non existent owner: " + par1.getString(
                    "owner") + "at coordinates: " + this.xCoord + "," + this.yCoord + "," + this.zCoord + ". Dropping Turretbase");
            worldObj.func_147480_a(this.xCoord, this.yCoord, this.zCoord, true);
            return;
        }
        if (par1.hasKey("ownerName")) {
            this.ownerName = par1.getString("ownerName");
        }
        buildTrustedPlayersFromNBT(par1.getTagList("trustedPlayers", 10));
        if (trustedPlayers.size() == 0) {
            buildTrustedPlayersFromNBT(par1.getTagList("trustedPlayers", 8));
        }
        if (par1.hasKey("active")) {
            this.active = par1.getBoolean("active");
        } else {
            active = true;
        }
        if (par1.hasKey("inverted")) {
            this.inverted = par1.getBoolean("inverted");
        } else {
            inverted = true;
        }
        if (par1.hasKey("redstone")) {
            this.redstone = par1.getBoolean("redstone");
        } else {
            checkRedstone = true;
        }
        if (par1.hasKey("computerAccessible")) {
            this.computerAccessible = par1.getBoolean("computerAccessible");
        } else {
            computerAccessible = false;
        }

        NBTTagList tagList = par1.getTagList("Inventory", 10);

        for (int i = 0; i < tagList.tagCount(); i++) {
            NBTTagCompound tag = tagList.getCompoundTagAt(i);
            byte slot = tag.getByte("Slot");
            if (slot >= 0 && slot < inv.length) {
                inv[slot] = ItemStack.loadItemStackFromNBT(tag);
            }
        }

        NBTTagCompound tag2 = par1.getCompoundTag("CamoStack");
        if (tag2 != null) {
            camoStack = ItemStack.loadItemStackFromNBT(tag2);
        }
    }

    @Override
    public ItemStack decrStackSize(int slot, int amt) {
        ItemStack stack = getStackInSlot(slot);

        if (stack != null) {
            if (stack.stackSize <= amt) {
                setInventorySlotContents(slot, null);
            } else {
                stack = stack.splitStack(amt);
                if (stack.stackSize == 0) {
                    setInventorySlotContents(slot, null);
                }
            }
        }
        return stack;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        ItemStack stack = getStackInSlot(slot);
        if (stack != null) {
            setInventorySlotContents(slot, null);
        }
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        inv[slot] = stack;
        if (stack != null && stack.stackSize > getInventoryStackLimit()) {
            stack.stackSize = getInventoryStackLimit();
        }
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return worldObj.getTileEntity(xCoord, yCoord, zCoord) == this && player.getDistanceSq(xCoord + 0.5,
                                                                                              yCoord + 0.5,
                                                                                              zCoord + 0.5) < 64;
    }

    @Optional.Method(modid = "Thaumcraft")
    private IEssentiaTransport getConnectableTileWithoutOrientation() {
        if (worldObj.getTileEntity(this.xCoord + 1, this.yCoord, this.zCoord) instanceof IEssentiaTransport) {
            return (IEssentiaTransport) worldObj.getTileEntity(this.xCoord + 1, this.yCoord, this.zCoord);
        }

        if (worldObj.getTileEntity(this.xCoord - 1, this.yCoord, this.zCoord) instanceof IEssentiaTransport) {
            return (IEssentiaTransport) worldObj.getTileEntity(this.xCoord - 1, this.yCoord, this.zCoord);
        }

        if (worldObj.getTileEntity(this.xCoord, this.yCoord + 1, this.zCoord) instanceof IEssentiaTransport) {
            return (IEssentiaTransport) worldObj.getTileEntity(this.xCoord, this.yCoord + 1, this.zCoord);
        }

        if (worldObj.getTileEntity(this.xCoord, this.yCoord - 1, this.zCoord) instanceof IEssentiaTransport) {
            return (IEssentiaTransport) worldObj.getTileEntity(this.xCoord, this.yCoord - 1, this.zCoord);
        }

        if (worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord + 1) instanceof IEssentiaTransport) {
            return (IEssentiaTransport) worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord + 1);
        }

        if (worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord - 1) instanceof IEssentiaTransport) {
            return (IEssentiaTransport) worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord - 1);
        }
        return null;
    }

    private void updateNeighboursRender() {
        Minecraft.getMinecraft().renderGlobal.markBlockForRenderUpdate(xCoord + 1, yCoord, zCoord);
        Minecraft.getMinecraft().renderGlobal.markBlockForRenderUpdate(xCoord - 1, yCoord, zCoord);
        Minecraft.getMinecraft().renderGlobal.markBlockForRenderUpdate(xCoord, yCoord + 1, zCoord);
        Minecraft.getMinecraft().renderGlobal.markBlockForRenderUpdate(xCoord, yCoord - 1, zCoord);
        Minecraft.getMinecraft().renderGlobal.markBlockForRenderUpdate(xCoord, yCoord, zCoord + 1);
        Minecraft.getMinecraft().renderGlobal.markBlockForRenderUpdate(xCoord, yCoord, zCoord - 1);
    }

    @Optional.Method(modid = "Thaumcraft")
    private int drawEssentia() {
        IEssentiaTransport ic = getConnectableTileWithoutOrientation();
        if (ic != null) {
            if (ic.takeEssentia(Aspect.ENERGY, 1, ForgeDirection.UP) == 1) {
                return 1;
            }
        }
        return 0;
    }

    @Override
    public void updateEntity() {
        if (this.worldObj.isRemote) {
            if (ticks % 10 == 0) {
                updateNeighboursRender();
            }
            ticks = 0;
            return;
        }

        ticks++;

        if (ticks % 5 == 0) {
            //Concealment
            this.shouldConcealTurrets = TurretHeadUtil.hasConcealmentAddon(this);

            //Extenders
            this.storage.setCapacity(getMaxEnergyStorageWithExtenders());

            //Thaumcraft
            if (ModCompatibility.ThaumcraftLoaded && TurretHeadUtil.hasPotentiaUpgradeAddon(this)) {
                if (amountOfPotentia > 0.05F && !(storage.getMaxEnergyStored() - storage.getEnergyStored() == 0)) {
                    if (VisNetHandler.drainVis(worldObj, xCoord, yCoord, zCoord, Aspect.ORDER, 5) == 5) {
                        this.amountOfPotentia = this.amountOfPotentia - 0.05F;
                        this.storage.modifyEnergyStored(Math.round(ConfigHandler.getPotentiaToRFRatio() * 5));
                    } else {
                        this.amountOfPotentia = this.amountOfPotentia - 0.05F;
                        this.storage.modifyEnergyStored(Math.round(ConfigHandler.getPotentiaToRFRatio() / 2));
                    }
                    worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
                }
            }

            if (ticks % 20 == 0) {
                ticks = 0;
                if (ModCompatibility.ThaumcraftLoaded && amountOfPotentia <= maxAmountOfPotentia) {
                    amountOfPotentia = amountOfPotentia + drawEssentia();
                }

                //Computers
                this.computerAccessible = (ModCompatibility.OpenComputersLoaded || ModCompatibility.ComputercraftLoaded) && TurretHeadUtil.hasSerialPortAddon(
                        this);
                updateRedstoneReactor(this);
                worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
            }
        }
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        super.onDataPacket(net, packet);
        readFromNBT(packet.func_148857_g());
    }

    public abstract int getBaseTier();

    public boolean isAttacksMobs() {
        return attacksMobs;
    }

    public void setAttacksMobs(boolean attacksMobs) {
        this.attacksMobs = attacksMobs;
    }

    public boolean isAttacksNeutrals() {
        return attacksNeutrals;
    }

    public void setAttacksNeutrals(boolean attacksNeutrals) {
        this.attacksNeutrals = attacksNeutrals;
    }

    public boolean isAttacksPlayers() {
        return attacksPlayers;
    }

    public void setAttacksPlayers(boolean attacksPlayers) {
        this.attacksPlayers = attacksPlayers;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    @Override
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        return storage.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored(ForgeDirection from) {
        return storage.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored(ForgeDirection from) {
        return storage.getMaxEnergyStored();
    }

    public void setEnergyStored(int energy) {
        storage.setEnergyStored(energy);
    }

    @Override
    public int getSizeInventory() {
        return inv.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inv[slot];
    }

    @Override
    public String getInventoryName() {
        return null;
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void openInventory() {
    }

    @Override
    public void closeInventory() {
    }

    @Override
    public boolean isItemValidForSlot(int i, ItemStack itemstack) {
        return true;
    }

    public int getyAxisDetect() {
        return yAxisDetect;
    }

    public void setyAxisDetect(int yAxisDetect) {
        this.yAxisDetect = yAxisDetect;

        if (this.yAxisDetect > 9) {
            this.yAxisDetect = 9;
        }

        if (this.yAxisDetect < 0) {
            this.yAxisDetect = 0;
        }
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection from) {
        return true;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
    }

    @Override
    public boolean canInsertItem(int slotID, ItemStack itemstack, int side) {
        return isItemValidForSlot(slotID, itemstack);
    }

    @Override
    public boolean canExtractItem(int slotID, ItemStack itemstack, int side) {
        return true;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean getInverted() {
        return this.inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
        this.active = redstone ^ this.inverted;
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public boolean getRedstone() {
        return this.redstone;
    }

    public void setRedstone(boolean redstone) {
        this.redstone = redstone;
        this.active = this.redstone ^ inverted;
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean isConnectable(ForgeDirection face) {
        return TurretHeadUtil.hasPotentiaUpgradeAddon(this);
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean canInputFrom(ForgeDirection face) {
        return TurretHeadUtil.hasPotentiaUpgradeAddon(this);
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean canOutputTo(ForgeDirection face) {
        return false;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public void setSuction(Aspect aspect, int amount) {
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public Aspect getSuctionType(ForgeDirection face) {
        return null;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public int takeEssentia(Aspect aspect, int amount, ForgeDirection face) {
        return 0;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public Aspect getEssentiaType(ForgeDirection face) {
        return null;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public int getEssentiaAmount(ForgeDirection face) {
        return 0;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public int getMinimumSuction() {
        return 0;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean renderExtendedTube() {
        return false;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public AspectList getAspects() {
        if (TurretHeadUtil.hasPotentiaUpgradeAddon(this)) {
            return new AspectList().add(Aspect.ENERGY, (int) Math.floor(amountOfPotentia));
        } else {
            return null;
        }
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public void setAspects(AspectList aspects) {
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean doesContainerAccept(Aspect tag) {
        return tag.equals(Aspect.ENERGY);
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public int addToContainer(Aspect tag, int amount) {
        return 0;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public int getSuctionAmount(ForgeDirection face) {
        return 64;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public int addEssentia(Aspect aspect, int amount, ForgeDirection face) {
        return 0;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean takeFromContainer(Aspect tag, int amount) {
        return false;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean takeFromContainer(AspectList ot) {
        return false;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean doesContainerContainAmount(Aspect tag, int amount) {
        return false;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public boolean doesContainerContain(AspectList ot) {
        return false;
    }

    @Optional.Method(modid = "Thaumcraft")
    @Override
    public int containerContains(Aspect tag) {
        if (tag.equals(Aspect.ENERGY)) {
            return Math.round(amountOfPotentia);
        }
        return 0;
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():string; returns owner of turret base.")
    public Object[] getOwner(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.getOwner()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; returns if the turret is currently set to attack hostile mobs.")
    public Object[] isAttacksMobs(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.isAttacksMobs()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean;  sets to attack hostile mobs or not.")
    public Object[] setAttacksMobs(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        this.setAttacksMobs(args.checkBoolean(0));
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return null;
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; returns if the turret is currently set to attack neutral mobs.")
    public Object[] isAttacksNeutrals(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.isAttacksNeutrals()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; sets to attack neutral mobs or not.")
    public Object[] setAttacksNeutrals(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        this.setAttacksNeutrals(args.checkBoolean(0));
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return null;
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; returns if the turret is currently set to attack players.")
    public Object[] isAttacksPlayers(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.isAttacksPlayers()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; sets to attack players or not.")
    public Object[] setAttacksPlayers(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        this.setAttacksPlayers(args.checkBoolean(0));
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return null;
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():table; returns a table of trusted players on this base.")
    public Object[] getTrustedPlayers(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.getTrustedPlayers()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function(name:String, [canOpenGUI:boolean , canChangeTargeting:boolean , " + "admin:boolean]):string; adds Trusted player to Trustlist.")
    public Object[] addTrustedPlayer(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        this.addTrustedPlayer(args.checkString(0));
        TrustedPlayer trustedPlayer = this.getTrustedPlayer(args.checkString(0));
        trustedPlayer.canOpenGUI = args.optBoolean(1, false);
        trustedPlayer.canChangeTargeting = args.optBoolean(1, false);
        trustedPlayer.admin = args.optBoolean(1, false);
        trustedPlayer.uuid = getPlayerUUID(args.checkString(0));
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return null;
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():string; removes Trusted player from Trustlist.")
    public Object[] removeTrustedPlayer(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        this.removeTrustedPlayer(args.checkString(0));
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return null;
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():int; returns maxiumum energy storage.")
    public Object[] getMaxEnergyStorage(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.storage.getMaxEnergyStored()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():int; returns current energy stored.")
    public Object[] getCurrentEnergyStorage(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.getEnergyStored(ForgeDirection.UNKNOWN)};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; returns if the turret is currently active.")
    public Object[] getActive(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.isActive()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; toggles turret inversion.")
    public Object[] setInverted(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        this.setInverted(args.checkBoolean(0));
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return null;
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; shows redstone invert state.")
    public Object[] getInverted(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.getInverted()};
    }

    @Optional.Method(modid = "OpenComputers")
    @Callback(doc = "function():boolean; shows redstone state.")
    public Object[] getRedstone(Context context, Arguments args) {
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        return new Object[]{this.getRedstone()};
    }

    @Optional.Method(modid = "ComputerCraft")
    @Override
    public String getType() {
        // peripheral.getType returns whaaaaat?
        return "simpleBlock";
    }

    @Optional.Method(modid = "ComputerCraft")
    @Override
    public String[] getMethodNames() {
        // list commands you want..
        return new String[]{commands.getOwner.toString(), commands.attacksPlayers.toString(),
                            commands.setAttacksPlayers.toString(), commands.attacksMobs.toString(),
                            commands.setAttacksMobs.toString(), commands.attacksNeutrals.toString(),
                            commands.setAttacksNeutrals.toString(), commands.getTrustedPlayers.toString(),
                            commands.addTrustedPlayer.toString(), commands.removeTrustedPlayer.toString(),
                            commands.getActive.toString(), commands.getInverted.toString(),
                            commands.getRedstone.toString(), commands.setInverted.toString()};
    }

    @Optional.Method(modid = "ComputerCraft")
    @Override
    public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) throws LuaException, InterruptedException {
        // method is command
        boolean b;
        if (!computerAccessible) {
            return new Object[]{"Computer access deactivated!"};
        }
        switch (commands.values()[method]) {
            case getOwner:
                return new Object[]{this.getOwner()};
            case attacksPlayers:
                return new Object[]{this.attacksPlayers};
            case setAttacksPlayers:
                if (!(arguments[0].toString().equals("true") || arguments[0].toString().equals("false"))) {
                    return new Object[]{"wrong arguments"};
                }
                b = (arguments[0].toString().equals("true"));
                this.attacksPlayers = b;
                return new Object[]{true};
            case attacksMobs:
                return new Object[]{this.attacksMobs};
            case setAttacksMobs:
                if (!(arguments[0].toString().equals("true") || arguments[0].toString().equals("false"))) {
                    return new Object[]{"wrong arguments"};
                }
                b = (arguments[0].toString().equals("true"));
                this.attacksMobs = b;
                return new Object[]{true};
            case attacksNeutrals:
                return new Object[]{this.attacksNeutrals};
            case setAttacksNeutrals:
                if (!(arguments[0].toString().equals("true") || arguments[0].toString().equals("false"))) {
                    return new Object[]{"wrong arguments"};
                }
                b = (arguments[0].toString().equals("true"));
                this.attacksNeutrals = b;
                return new Object[]{true};
            case getTrustedPlayers:
                return new Object[]{this.getTrustedPlayers()};
            case addTrustedPlayer:

                if (!arguments[0].toString().equals("")) {
                    return new Object[]{"wrong arguments"};
                }
                this.addTrustedPlayer(arguments[0].toString());
                if (arguments[1].toString().equals("")) {
                    return new Object[]{"successfully added"};
                }
                for (int i = 1; i <= 4; i++) {
                    if (!(arguments[i].toString().equals("true") || arguments[i].toString().equals("false"))) {
                        return new Object[]{"wrong arguments"};
                    }
                }
                TrustedPlayer trustedPlayer = this.getTrustedPlayer(arguments[0].toString());
                trustedPlayer.canOpenGUI = arguments[1].toString().equals("true");
                trustedPlayer.canChangeTargeting = arguments[2].toString().equals("true");
                trustedPlayer.admin = arguments[3].toString().equals("true");
                trustedPlayer.uuid = getPlayerUUID(arguments[0].toString());
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
                return new Object[]{"succesfully added player to trust list with parameters"};
            case removeTrustedPlayer:
                if (!arguments[0].toString().equals("")) {
                    return new Object[]{"wrong arguments"};
                }
                this.removeTrustedPlayer(arguments[0].toString());
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
                return new Object[]{"removed player from trusted list"};

            case getActive:
                return new Object[]{this.active};
            case getInverted:
                return new Object[]{this.inverted};
            case getRedstone:
                return new Object[]{this.redstone};
            case setInverted:
                if (!(arguments[0].toString().equals("true") || arguments[0].toString().equals("false"))) {
                    return new Object[]{"wrong arguments"};
                }
                b = (arguments[0].toString().equals("true"));
                this.setInverted(b);
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
                return new Object[]{true};
            default:
                break;
        }
        return new Object[]{false};
    }

    @Optional.Method(modid = "ComputerCraft")
    @Override
    public void attach(IComputerAccess computer) {
        if (comp == null) {
            comp = new ArrayList<IComputerAccess>();
        }
        comp.add(computer);
    }

    @Optional.Method(modid = "ComputerCraft")
    @Override
    public void detach(IComputerAccess computer) {
        if (comp == null) {
            comp = new ArrayList<IComputerAccess>();
        }
        comp.remove(computer);
    }

    @Optional.Method(modid = "ComputerCraft")
    @Override
    public boolean equals(IPeripheral other) {
        return other.getType().equals(getType());
    }

    public enum commands {
        getOwner, attacksPlayers, setAttacksPlayers, attacksMobs, setAttacksMobs, attacksNeutrals, setAttacksNeutrals, getTrustedPlayers, addTrustedPlayer, removeTrustedPlayer, getActive, getInverted, getRedstone, setInverted,
    }
}
