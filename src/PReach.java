import com.ibm.wala.shrikeCT.InvalidClassFileException;
import core.LibrarySummary;
import core.Program;
import cmd.MainLogic;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

public class PReach {

    private static String  myProjPath;
    private static String  jrePathName;

    private ArrayList<String> appPaths;
    private ArrayList<String> libPaths;
    private String apiPath;
    private String entryFilePath;
    private String procSign;
    private ArrayList<String> testInputParams;
    private String branchProbFile;
    private String prismBinary;

    private RunProgramGen programGen;
    private Thread  genThread;          // thread in which generateProgram() runs
    private final Timer timer;        // timer for checking on status of generateProgram()
    private int elapsedSecs;

    static public String classPath = "";

    PReach(ArrayList<String> appPaths, ArrayList<String> libPaths,
           String apiPath, String entryFilePath,
           String procSign, ArrayList<String> testInputParams, String branchProbFile, String prismBinary) {
        timer = new Timer(1000, new TimerListener());
        this.appPaths = appPaths;
        this.libPaths = libPaths;
        this.apiPath = apiPath;
        this.entryFilePath = entryFilePath;
        this.procSign = procSign;
        this.testInputParams = testInputParams;
        this.branchProbFile = branchProbFile;
        this.prismBinary = prismBinary;
    }



    // this keeps track of when the thread finishes so it can make changes to the GUI
    class TimerListener implements ActionListener {
        boolean done = false;

        @Override
        public void actionPerformed(ActionEvent event) {
            // update the elapsed time
            ++elapsedSecs;
            Integer secs = elapsedSecs % 60;
            Integer mins = elapsedSecs / 60;
            String timestamp = ((mins < 10) ? "0" : "") + mins.toString() + ":" +
                    ((secs < 10) ? "0" : "") + secs.toString();

            // check if thread has completed
            if (!done && programGen.exitcode >= 0) {

                // program generation was successful
                if (programGen.exitcode == 0) {
                    try {
                        // stop this timer
                        timer.stop();
                        done = true;
                    } catch (Exception ex) {
                    }
                }
                else {
                    // error occurred. clear the thread so we can try again.
                    genThread = null;
                }
            }
        }
    }

    private int generateProgram () {
        try {
            this.entryFilePath = null; //for public methods as entry points
            Program.makeProgram(this.appPaths, this.libPaths, this.apiPath, this.entryFilePath);
            Program.analyzeProgram();
            Set<String> misses = Program.checkAnalysisScope();
            Set<String> unknowns = LibrarySummary.getUnknownMethodSet();
            doAnalysis(procSign, testInputParams);
        } catch (Exception e) {
            e.printStackTrace();
            return 2;
        }

        return 0;
    }

    // this generates the Program info in a separate thread
    public class RunProgramGen implements Runnable {

        private int exitcode = -1;

        public RunProgramGen() {
        }

        @Override
        public void run() {
            exitcode = generateProgram();
        }

        public int getExitcode () {
            return exitcode;
        }
    }

    private void doAnalysis(String procSign, ArrayList<String> testInputParams) throws InvalidClassFileException {
        MainLogic mainLogic = new MainLogic();
        mainLogic.doDependencyAnalysis(procSign, testInputParams);
        mainLogic.doMarkovChainAnalysis(branchProbFile, prismBinary);
    }

    private void launchProgramGen () {
        // start the program generation in another thread
        if (genThread != null)
            return;

        // start the program generation in a seperate thread
        programGen = new RunProgramGen();
        genThread = new Thread(programGen);
        genThread.start();

        // start the timer for indicating elapsed time
        elapsedSecs = 0;
        timer.setInitialDelay(0);
        timer.start();
    }

    public static void main(String args[]) throws InvalidClassFileException {
        System.out.println("PReach Script Writing Starts...");

        String[] classList = args[0].split(",");
        String[] libList = args[1].split(",");
        String procSign = args[2];
        String[] paramList = args[3].split(",");
        String branchProbFile = args[4];
        String prismBinary = args[5];

        ArrayList<String> testInputParams = new ArrayList(Arrays.asList(paramList));

        PReach preach = new PReach(new ArrayList(Arrays.asList(classList)),
                new ArrayList(Arrays.asList(libList)), "", "",
                procSign, testInputParams, branchProbFile, prismBinary);
        preach.launchProgramGen();

        //preach.doAnalysis(procSign, testInputParams);
    }
}
