/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cmd;

import javax.swing.DefaultListModel;

@SuppressWarnings({"rawtypes", "unchecked", "serial"})

/**
 *
 * @author dmcd2356
 */
public class ConfigInfo implements Comparable<ConfigInfo> {

    // these are the selections for the configuration String parameters
    // (excludes configName and configVersion, since these are handled seperately)
    public enum StringType {
        usedapis, addentries, projectpath, projectname, averroes,
        exception, infloop, audiofback, pubmeths, cgoptions,
        decompiler, appRegEx, mainClass, startScript,
        debugger
    }

    // these are the selections for the configuration List parameters
    public enum ListType {
        application, libraries,
        applAverroes, libAverroes
    }
    
    // the configuration file information - no widgets for these
    private String configName;           // name of the configuration file
    private String configVersion;        // version of the configuration file

    // this has a widget, but the entry is not saved in the config file
    private String prjpathTextField;     // project path (includes the project name)

    // the following have corresponding widgets in AnalyzerFrame
    private DefaultListModel appList;    // application jar list
    private DefaultListModel libList;    // library jar list
    private DefaultListModel avappList;  // application jar list for Averroes mode
    private DefaultListModel avlibList;  // library jar list for Averroes mode
    private String apiTextField;         // used APIs file
    private String entryTextField;       // additional entry points file
    private String prjnameTextField;     // project name
    private String aveCheckBox;          // Averroes enable (ON, OFF)
    private String exceptCheckBox;       // Exception enable (ON, OFF)
    private String infCheckBox;          // Infinite Loop enable (ON, OFF)
    private String audioFeedbackCheckBox; // Audio feedback when job completes (ON, OFF)
    private String publicMethodsCheckBox; // select public methods or entry function (ON, OFF)
    private String optComboBox;           // option selection (0-1-CFA, O-CFA or RTA)

    // these have a corresponding widget in DecompilerFrame
    private String decompilerComboBox;   // the decompiler selection to use

    // these have a corresponding widget in AverroesFrame
    private String appRegexTextField;    // the regex expression for Averroes
    private String mainClassComboBox;    // the main class selection for Averroes
    private String startScriptTextField; // the start script file selection for Tamiflex

    // these have no corresponding widgets (hardcoded), but are used by the various Frames
    private String debugger;             // 0 to disable debugger, 1 to enable
        
    ConfigInfo ()  {
        this(null, null);
    }
    
    ConfigInfo (String name, String prjpath)  {
        configVersion = "1.5";
        if (name != null)
            configName = name;
        else
            configName = "";

        if (prjpath != null)
            prjpathTextField = prjpath;
        else
            prjpathTextField = "";
            
        appList = new DefaultListModel();
        libList = new DefaultListModel();
        avappList = new DefaultListModel();
        avlibList = new DefaultListModel();

        apiTextField         = "";
        entryTextField       = "";
        prjnameTextField     = "";
        aveCheckBox          = "yes";
        exceptCheckBox       = "no";
        infCheckBox          = "yes";
        audioFeedbackCheckBox = "no";
        publicMethodsCheckBox = "no";
        optComboBox          = "O-1-CFA";

        decompilerComboBox   = "";
        appRegexTextField    = "";
        mainClassComboBox    = "";
        startScriptTextField = "";

        debugger             = "1"; // for now, let's keep debug mode on
    }

    /**
     * sets the name of the ConfigInfo class
     * 
     * @param name - the name to identify with the ConfigInfo class
     */
    public void setName (String name) {
        this.configName = name;
    }
    
    /**
     * gets the name of the ConfigInfo class
     * 
     * @return name of the ConfigInfo class
     */
    public String getName () {
        return this.configName;
    }
    
    /**
     * sets the version of the class
     * 
     * @param version to set the class to (this value is read from the header
     * info from a config file that is read in).
     */
    public void setVersion (String version) {
        this.configVersion = version;
    }
    
    /**
     * get the current version
     * 
     * @return the version of the class
     */
    public String getVersion () {
        return this.configVersion;
    }
    
    /**
     * set the listmodel corresponding to the specified tag.
     * 
     * @param tag   - the tag id of the parameter (must be a List type)
     * @param list  - the listmodel value to set it to
     */    
    public void setList (ListType tag, DefaultListModel list) {
        switch (tag) {
        // can only do List data types here
        case application:     this.appList   = list;  break;
        case libraries:       this.libList   = list;  break;
        case applAverroes:    this.avappList = list;  break;
        case libAverroes:     this.avlibList = list;  break;
        default:
            break;
        }
    }
    
    /**
     * add entry to the listmodel corresponding to the specified tag.
     * 
     * @param tag   - the tag id of the parameter (must be a List type)
     * @param value - the value to add to the list
     */    
    public void addList (ListType tag, String value) {
        switch (tag) {
        // can only do List data types here
        case application:     this.appList.addElement(value);    break;
        case libraries:       this.libList.addElement(value);    break;
        case applAverroes:    this.avappList.addElement(value);  break;
        case libAverroes:     this.avlibList.addElement(value);  break;
        default:
            break;
        }
    }
    
    /**
     * clear the listmodel corresponding to the specified tag.
     * 
     * @param tag - the tag id of the parameter (must be a List type)
     */    
    public void clearList (ListType tag) {
        switch (tag) {
        // can only do List data types here
        case application:     this.appList.clear();    break;
        case libraries:       this.libList.clear();    break;
        case applAverroes:    this.avappList.clear();  break;
        case libAverroes:     this.avlibList.clear();  break;
        default:
            break;
        }
    }
    
    /**
     * get the listmodel corresponding to the specified tag.
     * 
     * @param tag - the tag id of the parameter (must be a List type)
     * @return the corresponding list from the class (null if invalid selection)
     */    
    public DefaultListModel getList (ListType tag) {
        switch (tag) {
        // can only do List data types here
        case application:     return this.appList;
        case libraries:       return this.libList;
        case applAverroes:    return this.avappList;
        case libAverroes:     return this.avlibList;
        default:
            return null;
        }
    }
    
    /**
     * set the value of the specified tag in the class.
     * If the tag is a List type, the corresponding list will be cleared if the
     * value is empty, and if not the value will be added to the end of the list.
     * 
     * @param tag   - the tag id of the parameter
     * @param value - the value to set the parameter to
     * @return the corresponding value from the class (null if invalid selection)
     */    
    public int setField (ListType tag, String value) {
        switch (tag) {
        // for these List parameters, the setField will clear the list or add an entry
        case application:
        case libraries:
        case applAverroes:
        case libAverroes:
            if (value == null || value.isEmpty())
                this.clearList(tag);
            else
                this.addList(tag, value);
            break;

        default:
            return -1;
        }
        return 0;
    }
    
    /**
     * set the value of the specified tag in the class.
     * If the tag is a List type, the corresponding list will be cleared if the
     * value is empty, and if not the value will be added to the end of the list.
     * 
     * @param tag   - the tag id of the parameter
     * @param value - the value to set the parameter to
     * @return the corresponding value from the class (null if invalid selection)
     */    
    public int setField (StringType tag, String value) {
        if (value == null)
            value = "";
        switch (tag) {
        case usedapis:    this.apiTextField          = value;    break;
        case addentries:  this.entryTextField        = value;    break;
        case projectpath: this.prjpathTextField      = value;    break;
        case projectname: this.prjnameTextField      = value;    break;
        case averroes:    this.aveCheckBox           = value;    break;
        case exception:   this.exceptCheckBox        = value;    break;
        case infloop:     this.infCheckBox           = value;    break;
        case audiofback:  this.audioFeedbackCheckBox = value;    break;
        case pubmeths:    this.publicMethodsCheckBox = value;    break;
        case cgoptions:   this.optComboBox           = value;    break;
        case decompiler:  this.decompilerComboBox    = value;    break;
        case appRegEx:    this.appRegexTextField     = value;    break;
        case mainClass:   this.mainClassComboBox     = value;    break;
        case startScript: this.startScriptTextField  = value;    break;
        case debugger:    this.debugger              = value;    break;
        default:
            return -1;
        }

        return 0;
    }

    /**
     * set the value of the specified tag in the class.
     * This one uses a string for specifying the tag value
     * 
     * @param tagname - the tag id of the parameter
     * @param value - the value to set the parameter to
     * @return the corresponding value from the class (null if invalid selection)
     */    
    public int setFieldValue (String tagname, String value) {
        switch (tagname) {
        case "application":  return setField (ListType.application, value);
        case "libraries":    return setField (ListType.libraries, value);
        case "applAverroes": return setField (ListType.applAverroes, value);
        case "libAverroes":  return setField (ListType.libAverroes, value);

        case "usedapis":       return setField (StringType.usedapis, value);
        case "addentries":     return setField (StringType.addentries, value);
        case "projectpath":    return setField (StringType.projectpath, value);
        case "projectname":    return setField (StringType.projectname, value);
        case "averroes":       return setField (StringType.averroes, value);
        case "exception":      return setField (StringType.exception, value);
        case "infloop":        return setField (StringType.infloop, value);
        case "audiofback":     return setField (StringType.audiofback, value);
        case "pubmeths":       return setField (StringType.pubmeths, value);
        case "cgoptions":      return setField (StringType.cgoptions, value);
        case "decompiler":     return setField (StringType.decompiler, value);
        case "appRegEx":       return setField (StringType.appRegEx, value);
        case "mainClass":      return setField (StringType.mainClass, value);
        case "startScript":    return setField (StringType.startScript, value);
        case "debugger":       return setField (StringType.debugger, value);
        default:
        }

        return -1;
    }

    /**
     * get value corresponding to the specified tag.
     * 
     * @param tag - the tag id of the parameter
     * @return the corresponding value from the class (null if invalid selection)
     */    
    public String getField (StringType tag) {
        switch (tag) {
        case usedapis:    return this.apiTextField;
        case addentries:  return this.entryTextField;
        case projectpath: return this.prjpathTextField;
        case projectname: return this.prjnameTextField;
        case averroes:    return this.aveCheckBox;
        case exception:   return this.exceptCheckBox;
        case infloop:     return this.infCheckBox;
        case audiofback:  return this.audioFeedbackCheckBox;
        case pubmeths:    return this.publicMethodsCheckBox;
        case cgoptions:   return this.optComboBox;
        case decompiler:  return this.decompilerComboBox;
        case appRegEx:    return this.appRegexTextField;
        case mainClass:   return this.mainClassComboBox;
        case startScript: return this.startScriptTextField;
        case debugger:    return this.debugger;
        default:
            return null;
        }
    }

    /**
     * this returns the status of whether the specified parameter is a field
     * that specifies a path that may be relative.
     * 
     * @param tag - the tag id of the parameter
     * @return true if it is a relative field
     */
    public boolean isParamRelativePath (StringType tag) {
      switch (tag) {
          case usedapis:
          case addentries:
          case startScript:
              return true;
          default:
              break;
      }
      return false;
    }
        
    @Override
    public int compareTo(ConfigInfo data) {
        int rc;
        boolean found;
            
        rc = data.configVersion.compareTo(this.configVersion);
        if (rc != 0) return rc;
        
        if (data.appList.size() < this.appList.size() ||
            data.appList.size() > this.appList.size()   )
            return 1;
        for (int ix = 0; ix < appList.size(); ix++) {
            found = data.appList.contains(this.appList.get(ix));
            return found ? 0 : 1;
        }
            
        if (data.libList.size() < this.libList.size() ||
            data.libList.size() > this.libList.size()   )
            return 1;
        for (int ix = 0; ix < libList.size(); ix++) {
            found = data.libList.contains(this.libList.get(ix));
            return found ? 0 : 1;
        }

        if (data.avappList.size() < this.avappList.size() ||
            data.avappList.size() > this.avappList.size()   )
            return 1;
        for (int ix = 0; ix < avappList.size(); ix++) {
            found = data.avappList.contains(this.avappList.get(ix));
            return found ? 0 : 1;
        }
            
        if (data.avlibList.size() < this.avlibList.size() ||
            data.avlibList.size() > this.avlibList.size()   )
            return 1;
        for (int ix = 0; ix < avlibList.size(); ix++) {
            found = data.avlibList.contains(this.avlibList.get(ix));
            return found ? 0 : 1;
        }

        rc = data.apiTextField.compareTo(this.apiTextField);
        if (rc != 0) return rc;
        rc = data.entryTextField.compareTo(this.entryTextField);
        if (rc != 0) return rc;
        rc = data.prjpathTextField.compareTo(this.prjpathTextField);
        if (rc != 0) return rc;
        rc = data.prjnameTextField.compareTo(this.prjnameTextField);
        if (rc != 0) return rc;
        rc = data.aveCheckBox.compareTo(this.aveCheckBox);
        if (rc != 0) return rc;
        rc = data.exceptCheckBox.compareTo(this.exceptCheckBox);
        if (rc != 0) return rc;
        rc = data.infCheckBox.compareTo(this.infCheckBox);
        if (rc != 0) return rc;
        rc = data.audioFeedbackCheckBox.compareTo(this.audioFeedbackCheckBox);
        if (rc != 0) return rc;
        rc = data.publicMethodsCheckBox.compareTo(this.publicMethodsCheckBox);
        if (rc != 0) return rc;
        rc = data.optComboBox.compareTo(this.optComboBox);
        if (rc != 0) return rc;

        rc = data.decompilerComboBox.compareTo(this.decompilerComboBox);
        if (rc != 0) return rc;
        rc = data.appRegexTextField.compareTo(this.appRegexTextField);
        if (rc != 0) return rc;
        rc = data.mainClassComboBox.compareTo(this.mainClassComboBox);
        if (rc != 0) return rc;
        rc = data.startScriptTextField.compareTo(this.startScriptTextField);
        if (rc != 0) return rc;
            
        return 0;
    }
}
