package org.openspim.acqeng;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.TaggedImage;

import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.api.Autofocus;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.PositionList;
import org.micromanager.navigation.StagePosition;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.ReportingUtils;

public class MMAcquire implements Runnable {
	private static class FourTuple {
		public int frame, position, channel, slice;

		FourTuple(int f, int p, int c, int s) {
			frame = f;
			position = p;
			channel = c;
			slice = s;
		}

		/**
		 * This is imperfect; technically these numbers can easily exceed 8
		 * bits. However, in most cases the indices will be -1 or 0, so maybe
		 * it'll suffice.
		 */
		@Override
		public int hashCode() {
			return ((frame & 0xFF) << 24) | ((position & 0xFF) << 16) | ((channel & 0xFF) << 8) | (slice & 0xFF);
		}

		@Override
		public boolean equals(Object other) {
			if (!(other instanceof FourTuple)) {
				return super.equals(other);
			} else {
				FourTuple fto = (FourTuple) other;
				return (frame == fto.frame && position == fto.position && channel == fto.channel && slice == fto.slice);
			}
		}
	}

	private static class SeqSpaceLocation {
		public long deltaT; // Time, in nanoseconds, 'at when' this location exists. 
		public MultiStagePosition position; // Stage positions -- root location in physical space.
		public ChannelSpec channel; // Active imaging channel.
		public double Z; // Distance along Z axis from that stage's origin to current slice. Overrides 'position' Z coord?

		public SeqSpaceLocation(long dt, MultiStagePosition msp, ChannelSpec chan, double z) {
			deltaT = dt;
			position = msp;
			channel = chan;
			Z = z;
		}

		public void navigate(CMMCore core) throws Exception {
			// TIME
			long d = deltaT - System.nanoTime();
			if(d > 0)
				Thread.sleep((long)(d / 1e6));
			else
				ReportingUtils.logMessage(String.format("SeqSpaceLocation.navigate: Behind schedule by %.3f s!", d / 1e9));
			// This message will need to be removed before the new acq engine goes live.
			// The method gets called a lot; we'll probably see this all too often.

			// SPACE
			for(int i=0; i < position.size(); ++i) {
				StagePosition sp = position.get(i);
				MMAcquire.runStage(core, sp);
			}

			// CHANNEL
			// ???

			// SLICE
			// ???
		}
	}

	// Settings
	private Map<FourTuple, List<Runnable>> taskMap;

	private CMMCore mmc;
	private SequenceSettings sequence;
	private boolean performCleanup;
	private PositionList posList;
	private Autofocus afDev;
	private Queue<TaggedImage> outputQueue;

	// State
	private FourTuple location;

	public MMAcquire(CMMCore core) {
		if (core == null)
			throw new IllegalArgumentException("Core cannot be null.");

		mmc = core;
		taskMap = new HashMap<FourTuple, List<Runnable>>();
		location = new FourTuple(0, 0, 0, 0);
	}

	public void setSequenceSettings(SequenceSettings seq, boolean replacePosList) {
		sequence = seq;

		if (posList == null || replacePosList)
			setPositionList(seq.positions);
	}

	public void setSequenceSettings(SequenceSettings seq) {
		setSequenceSettings(seq, false);
	}

	public void setDoCleanup(boolean cleanup) {
		performCleanup = cleanup;
	}

	public void setPositionList(PositionList posl) {
		posList = posl;
	}

	public void setPositionList(List<MultiStagePosition> mspl) {
		posList = new PositionList();

		for (MultiStagePosition msp : mspl)
			posList.addPosition(msp);
	}

	public PositionList getPositionList() {
		return posList;
	}

	public void setAutofocus(Autofocus dev) {
		afDev = dev;
	}

	public void setOutputQueue(Queue<TaggedImage> output) {
		outputQueue = output;
	}

	public void addTask(int frame, int position, int channel, int slice, Runnable task) {
		FourTuple addr = new FourTuple(frame, position, channel, slice);
		List<Runnable> others = null;

		if ((others = taskMap.get(addr)) == null)
			taskMap.put(addr, others = new LinkedList<Runnable>());

		if (!others.contains(task))
			others.add(task);
	}

	public void clearTasks() {
		taskMap.clear();
	}

	private static void runStage(CMMCore mmc, StagePosition sp) throws Exception {
		switch (sp.numAxes) {
		case 1:
			if (mmc.getDeviceType(sp.stageName) != DeviceType.StageDevice)
				throw new Error("Single axis given for non-StageDevice.");
			else
				mmc.setPosition(sp.stageName, sp.x);

			break;
		case 2:
			if (mmc.getDeviceType(sp.stageName) != DeviceType.XYStageDevice)
				throw new Error("Two axes given for non-XYStageDevice.");
			else
				mmc.setXYPosition(sp.stageName, sp.x, sp.y);

			break;
		case 3:
			// eh
		default:
			throw new Error("Invalid axis count " + sp.numAxes);
		}
	}

	@Override
	public void run() {
		if(mmc == null)
			throw new Error("Core cannot be null.");

		if(posList == null)
			throw new Error("Position list cannot be null.");

		if(outputQueue == null)
			throw new Error("Output queue cannot be null.");

		if(sequence == null)
			throw new Error("Sequence settings cannot be null.");

		try {
		} catch(Throwable t) {
			throw new Error(t);
		}
	}

}
