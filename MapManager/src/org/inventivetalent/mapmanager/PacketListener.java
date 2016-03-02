/*
 * Copyright 2015-2016 inventivetalent. All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without modification, are
 *  permitted provided that the following conditions are met:
 *
 *     1. Redistributions of source code must retain the above copyright notice, this list of
 *        conditions and the following disclaimer.
 *
 *     2. Redistributions in binary form must reproduce the above copyright notice, this list
 *        of conditions and the following disclaimer in the documentation and/or other materials
 *        provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 *  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 *  SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 *  ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 *  ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  The views and conclusions contained in the software and documentation are those of the
 *  authors and contributors and should not be interpreted as representing official policies,
 *  either expressed or implied, of anybody else.
 */

package org.inventivetalent.mapmanager;

import de.inventivegames.packetlistener.handler.PacketHandler;
import de.inventivegames.packetlistener.handler.PacketOptions;
import de.inventivegames.packetlistener.handler.ReceivedPacket;
import de.inventivegames.packetlistener.handler.SentPacket;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.inventivetalent.mapmanager.event.CreativeInventoryMapUpdateEvent;
import org.inventivetalent.mapmanager.event.MapInteractEvent;
import org.inventivetalent.mapmanager.manager.MapManager;
import org.inventivetalent.reflection.resolver.FieldResolver;
import org.inventivetalent.reflection.resolver.MethodResolver;
import org.inventivetalent.reflection.resolver.ResolverQuery;

class PacketListener {

	private final PacketHandler packetHandler;

	private static FieldResolver Vec3DFieldResolver              = new FieldResolver(MapManagerPlugin.nmsClassResolver.resolveSilent("Vec3D"));
	private static FieldResolver PacketUseEntityFieldResolver    = new FieldResolver(MapManagerPlugin.nmsClassResolver.resolveSilent("PacketPlayInUseEntity"));
	private static FieldResolver PacketCreativeSlotFieldResolver = new FieldResolver(MapManagerPlugin.nmsClassResolver.resolveSilent("PacketPlayInSetCreativeSlot"));

	private static MethodResolver CraftItemStackMethodResolver = new MethodResolver(MapManagerPlugin.obcClassResolver.resolveSilent("inventory.CraftItemStack"));

	public PacketListener(final MapManagerPlugin plugin) {
		this.packetHandler = new PacketHandler(plugin) {
			@Override
			@PacketOptions(forcePlayer = true)
			public void onSend(SentPacket sentPacket) {
				if (sentPacket.hasPlayer()) {
					if ("PacketPlayOutMap".equals(sentPacket.getPacketName())) {
						int id = ((Integer) sentPacket.getPacketValue("a")).intValue();

						if (id < 0) {
							//It's one of our maps, invert the id and let it through
							Integer newId = Integer.valueOf(-id);
							sentPacket.setPacketValue("a", newId);
						} else {
							if (!MapManager.Options.ALLOW_VANILLA) {//Vanilla maps not allowed, so we can just cancel all maps
								sentPacket.setCancelled(true);
							} else {
								boolean isPluginMap = !MapManager.Options.ALLOW_VANILLA;
								if (MapManager.Options.ALLOW_VANILLA) {//Less efficient method: check if the ID is used by the player
									isPluginMap = plugin.getMapManager().isIdUsedBy(sentPacket.getPlayer(), (short) id);
								}

								if (isPluginMap) {//It's the ID of one of our maps, so cancel it for this player
									sentPacket.setCancelled(true);
								}
							}
						}
					}
				}
			}

			@Override
			@PacketOptions(forcePlayer = true)
			public void onReceive(ReceivedPacket receivedPacket) {
				if (receivedPacket.hasPlayer()) {
					if ("PacketPlayInUseEntity".equals(receivedPacket.getPacketName())) {
						try {
							int a = (int) receivedPacket.getPacketValue("a");
							Object b = PacketUseEntityFieldResolver.resolveSilent("action", "b").get(receivedPacket.getPacket());
							Object c = receivedPacket.getPacketValue("c");

							MapInteractEvent event = new MapInteractEvent(receivedPacket.getPlayer(), a, ((Enum) b).ordinal(), vec3DtoVector(c));
							if (event.getItemFrame() != null) {
								if (event.getMapWrapper() != null) {
									Bukkit.getPluginManager().callEvent(event);
									if (event.isCancelled()) {
										receivedPacket.setCancelled(true);
									}
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					if ("PacketPlayInSetCreativeSlot".equals(receivedPacket.getPacketName())) {
						try {
							int a = (int) PacketCreativeSlotFieldResolver.resolveSilent("slot", "a").get(receivedPacket.getPacket());
							Object b = receivedPacket.getPacketValue("b");
							ItemStack itemStack = b == null ? null : (ItemStack) CraftItemStackMethodResolver.resolve(new ResolverQuery("asBukkitCopy", MapManagerPlugin.nmsClassResolver.resolve("ItemStack"))).invoke(null, b);

							CreativeInventoryMapUpdateEvent event = new CreativeInventoryMapUpdateEvent(receivedPacket.getPlayer(), a, itemStack);
							if (event.getMapWrapper() != null) {
								Bukkit.getPluginManager().callEvent(event);
								if (event.isCancelled()) {
									receivedPacket.setCancelled(true);
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}
		};
		PacketHandler.addHandler(this.packetHandler);
	}

	protected Vector vec3DtoVector(Object vec3D) {
		if (vec3D == null) { return null; }
		try {
			double a = (double) Vec3DFieldResolver.resolve("x"/*1.9*/, "a").get(vec3D);
			double b = (double) Vec3DFieldResolver.resolve("y"/*1.9*/, "b").get(vec3D);
			double c = (double) Vec3DFieldResolver.resolve("z"/*1.9*/, "c").get(vec3D);
			return new Vector(a, b, c);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new Vector(0, 0, 0);
	}

	protected void disable() {
		PacketHandler.removeHandler(this.packetHandler);
	}

}
