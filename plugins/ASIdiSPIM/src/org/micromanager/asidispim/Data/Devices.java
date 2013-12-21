///////////////////////////////////////////////////////////////////////////////
//FILE:          Devices.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.asidispim.Data;

import java.awt.geom.Point2D;
import org.micromanager.asidispim.Utils.DevicesListenerInterface;
import org.micromanager.asidispim.Utils.Labels;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.asidispim.Utils.DirectionalDevice;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

// TODO implement fast axis checkbox (may need device adapter change and possibly even firmware-level change)

/**
 * Class that holds information about the selected devices
 * This should always be the only source of device information
 * The GUI should update this class and use its information
 * 
 * This could be implemented more elegantly using templates
 * 
 * 
 * @author nico
 * @author Jon
 */
public class Devices {
   // useful static constants 

   public static enum JoystickDevice {
      JOYSTICK, RIGHT_KNOB, LEFT_KNOB
   };
   public static final Map<JoystickDevice, String> JOYSTICKS =
           new EnumMap<JoystickDevice, String>(JoystickDevice.class);
   static {
      JOYSTICKS.put(JoystickDevice.JOYSTICK, "Joystick");
      JOYSTICKS.put(JoystickDevice.RIGHT_KNOB, "Right Wheel");
      JOYSTICKS.put(JoystickDevice.LEFT_KNOB, "Left Wheel");
   }
   public static final Map<String, JoystickDevice> INV_JOYSTICKS =
           new HashMap<String, JoystickDevice>();
   static {
      Iterator<JoystickDevice> it = JOYSTICKS.keySet().iterator();
      while (it.hasNext()) {
         JoystickDevice term = it.next();
         INV_JOYSTICKS.put(JOYSTICKS.get(term), term);
      }
   }
   
   /*
    public static enum AbstractDevices {
    CAMERAA, CAMERAB, DUALCAMERA, PIEZOA, PIEZOB, GALVOA, GALVOB, GALVOC,
    GALVOD, XYSTAGE, LOWERZDRIVE, UPPERZDRIVE };
    public static final Map<AbstractDevices, String> DEVICEMAP = 
    new EnumMap<AbstractDevices, String>(AbstractDevices.class);
    static {
    DEVICEMAP.put(AbstractDevices.CAMERAA,"CameraA");
    }
    * */
   // TODO: change to enums:
   public static final String CAMERAA = "CameraA";
   public static final String CAMERAB = "CameraB";
   public static final String DUALCAMERA = "Dual Camera";
   public static final String PIEZOA = "PiezoA";
   public static final String PIEZOB = "PiezoB";
   public static final String GALVOA = "MicroMirrorA";
   public static final String GALVOB = "MicroMirrorB";
   public static final String GALVOC = "MicroMirrorC";
   public static final String GALVOD = "MicroMirrorD";
   public static final String XYSTAGE = "XY Stage";
   public static final String LOWERZDRIVE = "Lower Z Drive";
   public static final String UPPERZDRIVE = "Upper Z Drive";
   public static final String[] DEVICES = {
      CAMERAA, CAMERAB, DUALCAMERA, PIEZOA, PIEZOB, GALVOA, GALVOB, GALVOC,
      GALVOD, XYSTAGE, LOWERZDRIVE, UPPERZDRIVE};
   public static final String[] TIGERDEVICES = {PIEZOA, PIEZOB, GALVOA,
      GALVOB, GALVOC, GALVOD, XYSTAGE, LOWERZDRIVE, UPPERZDRIVE};
   public static final String[] TWOAXISTIGERDEVICES = {GALVOA, GALVOB, GALVOC,
      GALVOD, XYSTAGE};
   public static final String[] ONEAXISTIGERDEVICES = {PIEZOA, PIEZOB,
      LOWERZDRIVE, UPPERZDRIVE};
   
   public static final String FASTAXISAREV = "FastAxisARev";
   public static final String FASTAXISBREV = "FastAxisBRev";
   public static final String[] FASTAXISREVS = {FASTAXISAREV, FASTAXISBREV};
   public static final String FASTAXISADIR = "FastAxisADir";
   public static final String FASTAXISBDIR = "FastAxisBDir";
   public static final String FASTAXISCDIR = "FastAxisCDir";
   public static final String FASTAXISDDIR = "FastAxisDDir";
   public static final String[] FASTAXISDIRS = {FASTAXISADIR, FASTAXISBDIR,
      FASTAXISCDIR, FASTAXISDDIR};
   public static final String X = "X";
   public static final String Y = "Y";
   public static final String NONE = "None";
   
   public static final HashMap<String, DirectionalDevice> STAGES;
   static {
      STAGES = new HashMap<String, DirectionalDevice>();
      for (String dd : ONEAXISTIGERDEVICES) {
         STAGES.put(dd, new DirectionalDevice(dd, Labels.Directions.X));
      }
      for (String dd : TWOAXISTIGERDEVICES) {
         STAGES.put(dd, new DirectionalDevice(dd, Labels.Directions.X));
         STAGES.put(dd, new DirectionalDevice(dd, Labels.Directions.Y));
      }
   }
   
   public static String posToDisplayString(Double pos) {
      if (pos != null) {
         return NumberUtils.doubleToDisplayString(pos) + "\u00B5"+"m";
//         return NumberUtils.doubleToDisplayString(Math.round(100*pos)/100) + "\u00B5"+"m";  // rounds to two decimal places
      }
      return "";
   }
   
   // Non-static variables
   private HashMap<String, String> deviceInfo_;
   private HashMap<String, Boolean> axisRevs_;
   private HashMap<String, String> axisDirs_;
   private HashMap<String, Double> oneAxisDrivePositions_;
   private HashMap<String, Point2D.Double> twoAxisDrivePositions_;
   private List<DevicesListenerInterface> listeners_;
   private List<String>loadedDevices_;
   private Preferences prefs_;
   private CMMCore core_;

   public Devices() {
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      core_ = MMStudioMainFrame.getInstance().getCore();

      deviceInfo_ = new HashMap<String, String>();
      axisRevs_ = new HashMap<String, Boolean>();
      axisDirs_ = new HashMap<String, String>();
      oneAxisDrivePositions_ = new HashMap<String, Double>();
      twoAxisDrivePositions_ = new HashMap<String, Point2D.Double>();
      listeners_ = new ArrayList<DevicesListenerInterface>();
      loadedDevices_ = new ArrayList<String>();
      
      DetectLoadedDevices();

      for (String device : DEVICES) {
         String mmDevice = prefs_.get(device, "");
         if (!loadedDevices_.contains(mmDevice)) {
            mmDevice = "";
         }
         deviceInfo_.put(device, mmDevice);
      }

      for (String axisRev : FASTAXISREVS) {
         axisRevs_.put(axisRev, prefs_.getBoolean(axisRev, false));
      }

      for (String axisDir : FASTAXISDIRS) {
         axisDirs_.put(axisDir, prefs_.get(axisDir, X));
      }
      
      updateStagePositions();

   }

   /**
    * TODO: throw exception when non-existent key is provided
    *
    * @param key - devicetype
    * @param value - devicename
    */
   public synchronized void putDeviceInfo(String key, String value) {
      if (deviceInfo_.containsKey(key)) {
         deviceInfo_.put(key, value);
         callListeners();
      }
   }

   /**
    * Gets the name under which Micro-Manager knows the device
    *
    * @param abstractName - DeviceType as defined in this class
    * @return Micro-Manager deviceName, or null when not found
    */
   public synchronized String getMMDevice(String abstractName) {
      String mmDevice = deviceInfo_.get(abstractName);
      if (mmDevice == null || mmDevice.equals("")) {
         return null;
      }
      return mmDevice;
   }

   /**
    * Given an input array of abstract device names, returns the corresponding
    * set of names under which Micro-Manager knows these devices
    * Does not include null for devices not known as Micro-Manager devices
    *
    * @param devices array with abstract device names
    * @return array with Micro-Manager device names
    */
   public synchronized String[] getMMDevices(String[] devices) {
      List<String> tigerDevices = new ArrayList<String>();
      for (String device : devices) {
         String d = deviceInfo_.get(device);
         if (d != null && d.length() > 0) {
            tigerDevices.add(d);
         }
      }
      return tigerDevices.toArray(new String[0]);
   }
   
   /**
    * Creates an array with the abstract names of the stages present in the 
    * tiger controller.
    * X and Y of XY drive and MicroMirror devices will be listed separately
    * @return 
    */
   public String[] getTigerStages() {
      List<String> res = new ArrayList<String>();
      res.add(NONE);
      for (String dev : Devices.ONEAXISTIGERDEVICES) {
         if (getMMDevice(dev) != null) {
            res.add(dev);
         }
      }
      for (String dev : Devices.TWOAXISTIGERDEVICES) {
         if (getMMDevice(dev) != null) {
            res.add(new DirectionalDevice(dev, Labels.Directions.X).getFancyName());
            res.add(new DirectionalDevice(dev, Labels.Directions.Y).getFancyName());
         }
      }
      return res.toArray(new String[0]);
   }
   
   /**
    * Creates an array with the abstract names of the two axis drives 
    * present in the tiger controller
    * @return 
    */
   public String[] getTwoAxisTigerStages() {
      List<String> res = new ArrayList<String>();
      res.add(NONE);
      for (String dev : Devices.TWOAXISTIGERDEVICES) {
         if (getMMDevice(dev) != null) {
            res.add(dev);
         }
      }
      return res.toArray(new String[0]);
   }
   
   /**
    * Return cached position of given abstract stage
    * 
    * @param abstractDeviceName
    * @return stage position
    */
   public Double getStagePosition(String abstractDeviceName) {
      return oneAxisDrivePositions_.get(abstractDeviceName);
   }
   
   /**
    * return cached positon of given abstract XY stage or Micro-Mirror
    * 
    * @param abstractDeviceName
    * @return 
    */
   public Point2D.Double getTwoAxisStagePosition(String abstractDeviceName) {
      return twoAxisDrivePositions_.get(abstractDeviceName);
   }

   /**
    * TODO: throw exception when key is not found
    *
    * @param key - AxisName
    * @param value Axisdirection (true == Reverse)
    */
   public synchronized void putFastAxisRevInfo(String key, boolean value) {
      if (axisRevs_.containsKey(key)) {
         axisRevs_.put(key, value);
         callListeners();
      }
   }

   /**
    * Gets the direction of the specified fast axis
    *
    * TODO: throw exception when axisDir is not found
    *
    * @param axisRev - FASTAXISADIR or FASTAXISBDIR
    * @return axis direction (true == Reverse)
    */
   public synchronized boolean getFastAxisRevInfo(String axisRev) {
      return axisRevs_.get(axisRev);
   }

   /**
    * TODO: throw exception when non-existent key is provided, or value other
    * than X or Y
    *
    * @param key - Axis Type (
    * @param value - X or Y
    */
   public synchronized void putAxisDirInfo(String key, String value) {
      if (value.equals(X) || value.equals(Y)) {
         if (axisDirs_.containsKey(key)) {
            axisDirs_.put(key, value);
            callListeners();
         }
      }
   }

   /**
    * Gets the name under which Micro-Manager knows the device
    *
    * @param axisDir - FASTAXISADIR, FASTAXISBDIR, etc.
    * @return Micro-Manager deviceName, or null when not found
    */
   public synchronized String getAxisDirInfo(String axisDir) {
      return axisDirs_.get(axisDir);
   }

   /**
    * Clears all device settings that associate devices with joystick inputs
    *
    */
   public void clearJoystickBindings() {
      String[] oneAxisTigerDevices = getMMDevices(ONEAXISTIGERDEVICES);
      String propName = "JoystickInput";
      String xyStagePropName = "JoystickEnabled";
      String noInput = "0 - none";
      String no = "No";

      try {
         for (String dev : oneAxisTigerDevices) {
            core_.setProperty(dev, propName, noInput);
         }

         String[] twoAxisTigerDevices = getMMDevices(TWOAXISTIGERDEVICES);
         for (String dev : twoAxisTigerDevices) {
            if (core_.getDeviceType(dev) == mmcorej.DeviceType.XYStageDevice) {
               if (core_.hasProperty(dev, xyStagePropName)) {
               core_.setProperty(dev, xyStagePropName, no);
               }
            } else { // Galvo device
               if (core_.hasProperty(dev, propName + "X")) {
                  core_.setProperty(dev, propName + "X", noInput);
               }
               if (core_.hasProperty(dev, propName + "Y")) {
                  core_.setProperty(dev, propName + "Y", noInput);
               }
            }
         }
      } catch (Exception ex) {
         ReportingUtils.showError("Failed to communicate with Tiger controller");
      }
   }

   /**
    * Associates the given input device (knob or joystick) to the desired device
    * Each device specifies its direction (usually X or Y) as a
    * DirectionalDevice. Two devices can be associated with a Joystick (first one
    * will be linked to the Joystick X, second one to the Joystick Y) A knob can
    * have only one directional device associated.
    * 
    * The stored association for this input device will be removed first
    *
    * @param control Joystick, left or right knob
    * @param devices A combination of a Tiger device and direction
    * @return true on success, false on failure
    */
   public boolean setJoystickOutput(JoystickDevice joystickDevice,
           DirectionalDevice device) {
      String propName = "JoystickInput";
      String xyStagePropName = "JoystickEnabled";
      String yes = "Yes";

      // for joystick device
      String setting = "2 - joystick X";
      String settingY = "3 - joystick Y";
      if (joystickDevice.equals(JoystickDevice.LEFT_KNOB)) {
         setting = "23 - left wheel";
      } else if (joystickDevice.equals(JoystickDevice.RIGHT_KNOB)) {
         setting = "22 - right wheel";
      }

      try {
         String mmDevice = getMMDevice(device.getDeviceName());
         if (device.getNrOfAxis() == DirectionalDevice.NumberOfAxis.ONE) {
            if (core_.hasProperty(mmDevice, propName)) {
               core_.setProperty(mmDevice, propName, setting);
            }
         } else if (device.getNrOfAxis() == DirectionalDevice.NumberOfAxis.TWO) {
            if (core_.hasProperty(mmDevice, xyStagePropName)) {
               core_.setProperty(mmDevice, xyStagePropName, yes);
            } else { // Micro-Mirror Device
               if (core_.hasProperty(mmDevice, propName + "X")) {
                  core_.setProperty(mmDevice, propName + "X", setting);
               }
               if (core_.hasProperty(mmDevice, propName + "Y")) {
                  core_.setProperty(mmDevice, propName + "Y", settingY);
               }
            }
         }
      } catch (Exception ex) {
         ReportingUtils.showError("Failed to communicate with Tiger controller");
         return false;
      }

      return true;
   }
   
   /**
    * Removes the association between an input (joystick) device and a stage
    * both in hardware and in the internal model of this association
    * 
    * @param joystickDevice
    * @param device
    * @return true on success, false on failure
    */
   public boolean unsetJoystickOutput(JoystickDevice joystickDevice,
           DirectionalDevice device) {
      String propName = "JoystickInput";
      String xyStagePropName = "JoystickEnabled";
      String no = "No";

      // for joystick device
      String setting = "0 - none";

      try {
         String mmDevice = getMMDevice(device.getDeviceName());
         if (device.getNrOfAxis() == DirectionalDevice.NumberOfAxis.ONE) {
            if (core_.hasProperty(mmDevice, propName)) {
               core_.setProperty(mmDevice, propName, setting);
            }
         } else if (device.getNrOfAxis() == DirectionalDevice.NumberOfAxis.TWO) {
            if (core_.hasProperty(mmDevice, xyStagePropName)) {
               core_.setProperty(mmDevice, xyStagePropName, no);
            } else {
               for (String prop : new String[]{propName + "X", propName + "Y"}) {
                  if (core_.hasProperty(mmDevice, prop)) {
                     core_.setProperty(mmDevice, prop, setting);
                  }
               }
            }
         }
      } catch (Exception ex) {
         ReportingUtils.showError("Failed to communicate with Tiger controller");
         return false;
      }

      return true;
   }

   
   public final void DetectLoadedDevices() {
      loadedDevices_.clear();
      StrVector strv = core_.getLoadedDevices();
      for (int i =0; i < strv.size(); i++) {
         loadedDevices_.add(strv.get(i));
      }
   }

   private void updateSingleAxisStagePositions() {
      String[] drives = getTigerStages();
      for (String drive : drives) {
         String mmDevice = getMMDevice(drive);
         if (mmDevice != null) {
            try {
               this.oneAxisDrivePositions_.put(drive, core_.getPosition(mmDevice));
            } catch (Exception ex) {
               ReportingUtils.logError("Problem getting position of " + mmDevice);
            }
         }
      }
   }

   private void updateTwoAxisStagePositions() {
      String[] drives = getTwoAxisTigerStages();
      for (String drive : drives) {
         String mmDevice = this.getMMDevice(drive);
         if (mmDevice != null) {
            try {
               twoAxisDrivePositions_.put(drive, core_.getXYStagePosition(mmDevice));
            } catch (Exception ex) {
               ReportingUtils.logError("Problem getting position of " + mmDevice);
            }
         }
      }
   }
   

   public final void updateStagePositions() {
      updateSingleAxisStagePositions();
      updateTwoAxisStagePositions();
   }

   /**
    * Write deviceInfo_ back to Preferences
    */
   public synchronized void saveSettings() {
      for (String device : DEVICES) {
         prefs_.put(device, deviceInfo_.get(device));
      }
      for (String axisRev : FASTAXISREVS) {
         prefs_.putBoolean(axisRev, axisRevs_.get(axisRev));
      }

      for (String axisDir : FASTAXISDIRS) {
         prefs_.put(axisDir, axisDirs_.get(axisDir));
      }
   }

   /**
    * Used to add classes implementing DeviceListenerInterface as listeners
    */
   public void addListener(DevicesListenerInterface listener) {
      listeners_.add(listener);
   }

   /**
    * Remove classes implementing the DeviceListener interface from the listers
    *
    * @param listener
    */
   public void removeListener(DevicesListenerInterface listener) {
      listeners_.remove(listener);
   }

   /**
    * Call each listener in succession to alert them that something changed
    */
   private void callListeners() {
      for (DevicesListenerInterface listener : listeners_) {
         listener.devicesChangedAlert();
      }
   }
}