package dev.tatami.virtualcraft.server;

import net.minecraft.util.Tuple;
import net.neoforged.fml.util.thread.SidedThreadGroups;
import org.apache.logging.log4j.Logger;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ServerWorkQueue {
	private static final int MAX_PENDING_WORK = 4096;
	private static final ConcurrentLinkedQueue<Tuple<Runnable, Integer>> WORK_QUEUE = new ConcurrentLinkedQueue<>();

	private ServerWorkQueue() {
	}

	public static void queue(int tick, Runnable action) {
		if (action == null || Thread.currentThread().getThreadGroup() != SidedThreadGroups.SERVER) {
			return;
		}
		if (WORK_QUEUE.size() >= MAX_PENDING_WORK) {
			return;
		}
		WORK_QUEUE.add(new Tuple<>(action, Math.max(1, tick)));
	}

	public static void clear() {
		WORK_QUEUE.clear();
	}

	public static void tick(Logger logger) {
		Iterator<Tuple<Runnable, Integer>> it = WORK_QUEUE.iterator();
		while (it.hasNext()) {
			Tuple<Runnable, Integer> work = it.next();
			int remaining = work.getB() - 1;
			if (remaining <= 0) {
				it.remove();
				runSafely(logger, work.getA());
			} else {
				work.setB(remaining);
			}
		}
	}

	private static void runSafely(Logger logger, Runnable action) {
		try {
			action.run();
		} catch (Throwable error) {
			logger.error("Queued server work threw", error);
		}
	}
}
