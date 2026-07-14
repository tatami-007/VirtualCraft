package dev.tatami.virtualcraft.energy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import dev.tatami.virtualcraft.block.EnergyPipeBlock;
import dev.tatami.virtualcraft.blockentity.EnergyPipeBlockEntity;
import dev.tatami.virtualcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Centralized, storage-free FE transport for connected energy pipes. */
public final class EnergyPipeNetwork {
	private static final int MAX_TRANSFER_PER_NETWORK = 262_144;
	private static final int CONNECTION_REFRESH_INTERVAL = 20;
	private static final Direction[] DIRECTIONS = Direction.values();
	private static final EnergyPipeNetwork INSTANCE = new EnergyPipeNetwork();

	private final Map<ServerLevel, Set<BlockPos>> pipesByLevel = new HashMap<>();

	private EnergyPipeNetwork() {
	}

	public static EnergyPipeNetwork getInstance() {
		return INSTANCE;
	}

	public static void registerEvents(IEventBus eventBus) {
		eventBus.addListener(EnergyPipeNetwork::onServerTick);
		eventBus.addListener(EnergyPipeNetwork::onServerStopped);
	}

	private static void onServerTick(ServerTickEvent.Post event) {
		INSTANCE.tick();
	}

	private static void onServerStopped(ServerStoppedEvent event) {
		INSTANCE.pipesByLevel.clear();
	}

	public void register(ServerLevel level, BlockPos pos) {
		pipesByLevel.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(pos.immutable());
	}

	public void unregister(ServerLevel level, BlockPos pos) {
		Set<BlockPos> pipes = pipesByLevel.get(level);
		if (pipes != null) {
			pipes.remove(pos);
			if (pipes.isEmpty()) {
				pipesByLevel.remove(level);
			}
		}
	}

	private void tick() {
		for (Map.Entry<ServerLevel, Set<BlockPos>> entry : new ArrayList<>(pipesByLevel.entrySet())) {
			processLevel(entry.getKey(), entry.getValue());
		}
	}

	private void processLevel(ServerLevel level, Set<BlockPos> registered) {
		registered.removeIf(pos -> !level.isLoaded(pos) || !level.getBlockState(pos).is(ModBlocks.ENERGY_PIPE.get()));
		if (registered.isEmpty()) {
			pipesByLevel.remove(level);
			return;
		}

		Set<BlockPos> unvisited = new HashSet<>(registered);
		while (!unvisited.isEmpty()) {
			BlockPos start = unvisited.iterator().next();
			processNetwork(level, start, registered, unvisited);
		}
	}

	private void processNetwork(ServerLevel level, BlockPos start, Set<BlockPos> registered, Set<BlockPos> unvisited) {
		Queue<BlockPos> queue = new ArrayDeque<>();
		Set<Endpoint> endpointSet = new LinkedHashSet<>();
		List<BlockPos> networkPipes = new ArrayList<>();
		queue.add(start);
		unvisited.remove(start);

		while (!queue.isEmpty()) {
			BlockPos pipePos = queue.remove();
			networkPipes.add(pipePos);
			refreshVisualConnections(level, pipePos);
			for (Direction direction : DIRECTIONS) {
				BlockPos neighborPos = pipePos.relative(direction);
				if (registered.contains(neighborPos)) {
					if (unvisited.remove(neighborPos)) {
						queue.add(neighborPos);
					}
					continue;
				}
				IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, neighborPos, direction.getOpposite());
				if (storage != null) {
					endpointSet.add(new Endpoint(neighborPos.immutable(), direction.getOpposite()));
				}
			}
		}

		boolean transferred = transfer(level, List.copyOf(endpointSet)) > 0;
		for (BlockPos pipePos : networkPipes) {
			boolean active = transferred;
			if (level.getBlockEntity(pipePos) instanceof EnergyPipeBlockEntity pipe) {
				active = pipe.updateActivity(level, transferred);
			}
			EnergyPipeBlock.setActive(level, pipePos, active);
		}
	}

	private static void refreshVisualConnections(ServerLevel level, BlockPos pos) {
		if (level.getGameTime() % CONNECTION_REFRESH_INTERVAL != 0) {
			return;
		}
		var state = level.getBlockState(pos);
		var refreshed = EnergyPipeBlock.refreshConnections(level, pos, state);
		if (refreshed != state) {
			level.setBlock(pos, refreshed, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
		}
	}

	private static int transfer(ServerLevel level, List<Endpoint> endpoints) {
		List<Endpoint> sources = new ArrayList<>();
		List<Endpoint> sinks = new ArrayList<>();
		List<Endpoint> bidirectional = new ArrayList<>();

		for (Endpoint endpoint : endpoints) {
			IEnergyStorage storage = endpoint.resolve(level);
			if (storage == null) {
				continue;
			}
			boolean extracts = storage.canExtract() && storage.extractEnergy(1, true) > 0;
			boolean receives = storage.canReceive() && storage.receiveEnergy(1, true) > 0;
			if (extracts && receives) {
				bidirectional.add(endpoint);
			} else if (extracts) {
				sources.add(endpoint);
			} else if (receives) {
				sinks.add(endpoint);
			}
		}

		int remaining = MAX_TRANSFER_PER_NETWORK;
		rotateForFairness(level, sources);
		rotateForFairness(level, sinks);
		rotateForFairness(level, bidirectional);
		remaining = move(level, sources, sinks, remaining);
		remaining = move(level, sources, bidirectional, remaining);
		remaining = move(level, bidirectional, sinks, remaining);
		return MAX_TRANSFER_PER_NETWORK - remaining;
	}

	private static int move(ServerLevel level, List<Endpoint> sources, List<Endpoint> sinks, int budget) {
		for (Endpoint sourceEndpoint : sources) {
			if (budget <= 0) {
				break;
			}
			IEnergyStorage source = sourceEndpoint.resolve(level);
			if (source == null) {
				continue;
			}
			for (Endpoint sinkEndpoint : sinks) {
				if (budget <= 0) {
					break;
				}
				if (sourceEndpoint.pos().equals(sinkEndpoint.pos())) {
					continue;
				}
				IEnergyStorage sink = sinkEndpoint.resolve(level);
				if (sink == null) {
					continue;
				}
				int available = source.extractEnergy(budget, true);
				if (available <= 0) {
					break;
				}
				int accepted = sink.receiveEnergy(available, true);
				if (accepted <= 0) {
					continue;
				}
				int extracted = source.extractEnergy(accepted, false);
				if (extracted <= 0) {
					break;
				}
				int inserted = sink.receiveEnergy(extracted, false);
				budget -= inserted;
			}
		}
		return budget;
	}

	private static void rotateForFairness(ServerLevel level, List<Endpoint> endpoints) {
		if (endpoints.size() > 1) {
			Collections.rotate(endpoints, (int) (level.getGameTime() % endpoints.size()));
		}
	}

	private record Endpoint(BlockPos pos, Direction side) {
		private IEnergyStorage resolve(ServerLevel level) {
			return level.isLoaded(pos) ? level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side) : null;
		}
	}
}
