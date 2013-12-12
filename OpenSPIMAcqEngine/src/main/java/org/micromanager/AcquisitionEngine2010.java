package org.micromanager;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import org.json.JSONObject;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.api.Autofocus;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.api.ScriptInterface;
import org.micromanager.navigation.PositionList;
import org.micromanager.navigation.StagePosition;

class AcquisitionEngine2010 implements IAcquisitionEngine2010 {
	private CMMCore mmc;

	public AcquisitionEngine2010(ScriptInterface si) {
		this(si.getMMCore());
	}

	public AcquisitionEngine2010(CMMCore mmc) {
		this.mmc = mmc;
	}

	@Override
	public void acquireSingle() {
		// Stub; this method has been removed from the more recent MM API that
		// our code isn't based on yet.
	}

	/* ARBITRARY ACQUISITION TASK MANAGEMENT */

	@Override
	public void attachRunnable(int frame, int position, int channel, int slice, Runnable task) {
	}

	@Override
	public void clearRunnables() {
	}

	/* ACQUISITION CONTROL */

	@Override
	public boolean isFinished() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isPaused() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isRunning() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public long nextWakeTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void pause() {
		// TODO Auto-generated method stub

	}

	@Override
	public void resume() {
		// TODO Auto-generated method stub

	}

	@Override
	public BlockingQueue<TaggedImage> run(SequenceSettings seq) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public BlockingQueue<TaggedImage> run(SequenceSettings seq, boolean cleanup) {
		// TODO Auto-generated method stub
		return null;
	}

	// @Override
	public BlockingQueue<TaggedImage> run(SequenceSettings seq, boolean cleanup, PositionList pos, Autofocus afdev) {
		// TODO

		return null;
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean stopHasBeenRequested() {
		// TODO Auto-generated method stub
		return false;
	}

	/* METADATA */

	@Override
	public JSONObject getSummaryMetadata() {
		// TODO Auto-generated method stub
		return null;
	}

}