/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package MMCustomization;

import com.imaging100x.twophoton.SettingsDialog;
import com.imaging100x.twophoton.TwoPhotonControl;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.AcqControlDlg;
import org.micromanager.AcquisitionEngine2010;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.AcquisitionManager;
import org.micromanager.acquisition.AcquisitionWrapperEngine;
import org.micromanager.acquisition.DefaultTaggedImageSink;
import org.micromanager.acquisition.MMImageCache;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.api.ImageCache;
import org.micromanager.api.SequenceSettings;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 * Adapter class that sends commands in MM from acqWrapperEngine interface to
 * the real acquisition engine created within the plugin
 *
 * @author Henry
 */
public class AcquisitionWrapperEngineAdapter extends AcquisitionWrapperEngine {

    private IAcquisitionEngine2010 realAcqEng_;
    private Preferences prefs_;
    private TwoPhotonControl twoPC_;
    private double initialZPosition_;
    private double focusOffset_ = 0;
    private CMMCore core_ = MMStudioMainFrame.getInstance().getCore();

    public AcquisitionWrapperEngineAdapter(TwoPhotonControl twoP, Preferences prefs) throws NoSuchFieldException {
        super((AcquisitionManager) JavaUtils.getRestrictedFieldValue(
                MMStudioMainFrame.getInstance(), MMStudioMainFrame.class, "acqMgr_"));
        MMStudioMainFrame gui = MMStudioMainFrame.getInstance();
        super.setCore(MMStudioMainFrame.getInstance().getCore(), null);
        twoPC_ = twoP;

        prefs_ = prefs;
        realAcqEng_ = new AcquisitionEngine2010(MMStudioMainFrame.getInstance().getCore());

        try {
            gui.setAcquisitionEngine(this);
            //assorted things copied from MMStudioMainFrame to ensure compatibility
            this.setParentGUI(gui);
            this.setZStageDevice(gui.getCore().getFocusDevice());
            this.setPositionList(gui.getPositionList());
            JavaUtils.setRestrictedFieldValue(gui.getAcqDlg(), AcqControlDlg.class, "acqEng_", this);
        } catch (Exception ex) {
            ReportingUtils.showError("Could't override acquisition engine");
        }
    }

    private void addRunnables(SequenceSettings settings) {
        realAcqEng_.clearRunnables();
        //add runnable to execute at the start of each frame
        //1) for turning off lasers at regular interval
        //2) for setting Z to account for focus drift
        for (int f = 0; f < settings.numFrames; f++) {
            final int frame = f;
            final int skipInterval = SettingsDialog.getEOM1SkipInterval();
            realAcqEng_.attachRunnable(f, 0, 0, 0, new Runnable() {
                @Override
                public void run() {
                    //get Initial z position
                    if (frame == 0) {
                        try {
                            initialZPosition_ = core_.getPosition("Z");
                        } catch (Exception ex) {
                        }
                    } else {
                        try {
                            focusOffset_ = initialZPosition_ - core_.getPosition("Z");
                        } catch (Exception ex) {}
                    }

                    //Set EOM on/off 
                    try {
                        if (frame % skipInterval == 0) {
                            core_.setProperty("EOM1", "Block voltage", "false");
                        } else {
                            core_.setProperty("EOM1", "Block voltage", "true");
                        }
                    } catch (Exception e) {
                        ReportingUtils.showError("Couldn't change EOM voltage block");
                    }

                    //change z position to account for focus drift
                }
            });
        }
        
        
        if (settings.usePositionList) {
            //add position list runnables
            try {
                int numPositions = MMStudioMainFrame.getInstance().getPositionList().getPositions().length;
                for (int p = 0; p < numPositions; p++) {
                    final int posIndex = p;
                    realAcqEng_.attachRunnable(-1, p, -1, -1, new Runnable() {
                        @Override
                        public void run() {                           
                            twoPC_.applyDepthSetting(posIndex, focusOffset_);
                        }
                    });
                }

            } catch (MMScriptException ex) {
                ReportingUtils.showError("Couldn't get position list");
            }
        } else {
            //XY position invariant depth list runnable
            realAcqEng_.attachRunnable(-1, -1, -1, -1, new Runnable() {
                @Override
                public void run() {
                    twoPC_.applyDepthSetting(-1, focusOffset_);
                }
            });
        }
    }

    private void runAcquisition(SequenceSettings settings) throws Exception {
        addRunnables(settings);

        // Start up the acquisition engine
        BlockingQueue<TaggedImage> engineOutputQueue = realAcqEng_.run(settings, true, gui_.getPositionList(), null);
        JSONObject summaryMetadata = realAcqEng_.getSummaryMetadata();

        //write pixel overlap into metadata
        summaryMetadata.put("GridPixelOverlapX", SettingsDialog.getXOverlap());
        summaryMetadata.put("GridPixelOverlapY", SettingsDialog.getYOverlap());


        // Set up the DataProcessor<TaggedImage> sequence--no data processors for now
        // BlockingQueue<TaggedImage> procStackOutputQueue = ProcessorStack.run(engineOutputQueue, imageProcessors);

        // create storage
        TaggedImageStorage storage;
        try {
            if (settings.save) {
                //MPTiff storage
                String acqDirectory = createAcqDirectory(summaryMetadata.getString("Directory"), summaryMetadata.getString("Prefix"));
                summaryMetadata.put("Prefix", acqDirectory);
                String acqPath = summaryMetadata.getString("Directory") + File.separator + acqDirectory;
                storage = new DoubleTaggedImageStorage(summaryMetadata, acqPath, prefs_);
            } else {
                //RAM storage
                storage = new DoubleTaggedImageStorage(summaryMetadata, null, prefs_);
            }

//            final int numChannels = MDUtils.getNumChannels(summaryMetadata);
//            final int numPositions = MDUtils.getNumPositions(summaryMetadata);
//            final int numSlices = MDUtils.getNumSlices(summaryMetadata);
            final MMImageCache imageCache = new MMImageCache(storage) {
                @Override
                public JSONObject getLastImageTags() {
                    //So that display doesnt show a position scrollbar when imaging finished
                    JSONObject newTags = null;
                    try {
                        newTags = new JSONObject(super.getLastImageTags().toString());
                        MDUtils.setPositionIndex(newTags, 0);
                    } catch (JSONException ex) {
                        ReportingUtils.showError("Unexpected JSON Error");
                    }
                    return newTags;
                }
//                @Override
//                public void putImage(TaggedImage img) {                   
//                    super.putImage(img);
//
//                    try {
//                        if (MDUtils.getChannelIndex(img.tags) == numChannels -1 &&
//                                MDUtils.getSliceIndex(img.tags) == numSlices - 1
//                                && MDUtils.getPositionIndex(img.tags) == numPositions - 1) {
//                            //last image of this frame
//                            Thread.sleep(2500);
//                            long timeToWake = (realAcqEng_.nextWakeTime() - System.nanoTime() / 1000000) / 1000;
//                            if ( timeToWake > 2) {
//                                //if there is at least two seconds, pause the acq engine and do autofocus
//                                realAcqEng_.pause();
//                                autofocus(imageCache);
//                            }
//                        }
//                    } catch (Exception e) {
//                        ReportingUtils.showError("Couldnt get image tags fpr autofocus");
//                    }   
//                }
            };
            imageCache.setSummaryMetadata(summaryMetadata);


            DisplayPlus stitchedDisplay = new DisplayPlus(imageCache, this, summaryMetadata);

            DefaultTaggedImageSink sink = new DefaultTaggedImageSink(engineOutputQueue, imageCache);
            sink.start();


        } catch (IOException ex) {
            ReportingUtils.showError("Couldn't create image storage or start acquisition");
        }
    }

//    private void autofocus(ImageCache cache) {
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    System.out.println();
//                    //calculate how much to change the relative Z offset by
//                    
//                    //apply new z position
//                    MMStudioMainFrame.getInstance().getCore().setPosition("Z", 100);
//
//                    //update something so that depth list knows about this
//                } catch (Exception e) {
//                }
//                //resume acqEngine
//                realAcqEng_.resume();
//            
//            }
//        }, "Autofocus thread").start();
//    }
    //Copied from MMAcquisition
    private String createAcqDirectory(String root, String prefix) throws Exception {
        File rootDir = JavaUtils.createDirectory(root);
        int curIndex = getCurrentMaxDirIndex(rootDir, prefix + "_");
        return prefix + "_" + (1 + curIndex);
    }

    private int getCurrentMaxDirIndex(File rootDir, String prefix) throws NumberFormatException {
        int maxNumber = 0;
        int number;
        String theName;
        for (File acqDir : rootDir.listFiles()) {
            theName = acqDir.getName();
            if (theName.startsWith(prefix)) {
                try {
                    //e.g.: "blah_32.ome.tiff"
                    Pattern p = Pattern.compile("\\Q" + prefix + "\\E" + "(\\d+).*+");
                    Matcher m = p.matcher(theName);
                    if (m.matches()) {
                        number = Integer.parseInt(m.group(1));
                        if (number >= maxNumber) {
                            maxNumber = number;
                        }
                    }
                } catch (NumberFormatException e) {
                } // Do nothing.
            }
        }
        return maxNumber;
    }

    protected String runAcquisition(SequenceSettings acquisitionSettings, AcquisitionManager acqManager) {
        //refresh GUI so that z returns to original position after acq
        MMStudioMainFrame.getInstance().refreshGUI();
        if (acquisitionSettings.save) {
            File root = new File(acquisitionSettings.root);
            if (!root.canWrite()) {
                int result = JOptionPane.showConfirmDialog(null, "The specified root directory\n" + root.getAbsolutePath() + "\ndoes not exist. Create it?", "Directory not found.", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) {
                    root.mkdirs();
                    if (!root.canWrite()) {
                        ReportingUtils.showError("Unable to save data to selected location: check that location exists.\nAcquisition canceled.");
                        return null;
                    }
                } else {
                    ReportingUtils.showMessage("Acquisition canceled.");
                    return null;
                }
            } else if (!this.enoughDiskSpace()) {
                ReportingUtils.showError("Not enough space on disk to save the requested image set; acquisition canceled.");
                return null;
            }
        }
        try {
            //run acquisition with our acquisition engine
            runAcquisition(acquisitionSettings);
        } catch (Exception ex) {
            ReportingUtils.showError("Probelem running acquisiton: " + ex.getMessage());
        }

        return "";
    }

    @Override
    public void setPause(boolean pause) {
        if (pause) {
            realAcqEng_.pause();
        } else {
            realAcqEng_.resume();
        }
    }

    @Override
    public boolean isPaused() {
        return realAcqEng_.isPaused();
    }

    @Override
    public boolean abortRequest() {
        if (isAcquisitionRunning()) {
            int result = JOptionPane.showConfirmDialog(null,
                    "Abort current acquisition task?",
                    "Micro-Manager", JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                realAcqEng_.stop();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAcquisitionRunning() {
        return realAcqEng_.isRunning();
    }

    @Override
    public long getNextWakeTime() {
        return realAcqEng_.nextWakeTime();
    }

    @Override
    public boolean abortRequested() {
        return realAcqEng_.stopHasBeenRequested();
    }

    @Override
    public boolean isFinished() {
        return realAcqEng_.isFinished();
    }
}
