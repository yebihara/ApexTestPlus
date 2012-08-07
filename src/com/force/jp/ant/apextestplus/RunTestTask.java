package com.force.jp.ant.apextestplus;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tools.ant.BuildException;

import com.salesforce.ant.SFDCAntTask;
import com.sforce.soap.apex.DebuggingHeader_element;
import com.sforce.soap.apex.LogType;
import com.sforce.soap.apex.RunTestsRequest;
import com.sforce.soap.apex.RunTestsResult;
import com.sforce.soap.apex.SoapConnection;

public class RunTestTask extends SFDCAntTask {
    private boolean runAllTests = false;
    private List<ClassElement> testClasses = new ArrayList<ClassElement>();
    private String namespace;
    private File srcDir;
    private File outDir;
    private String logType;
    private int coverageTarget = 75;
    
    private Map<String, File> srcClassMap = new HashMap<String, File>();
    private Map<String, File> srcTriggerMap = new HashMap<String, File>();

    @Override
    public void execute() throws BuildException {
        validateAttributes();

        System.out.println("Source Directory: " + getSrcDir());
        System.out.println("Output Directory: " + getOutDir());
        System.out.println("Debug Log Option: " + getLogType());

        if (getRunAllTests()) {
            System.out.println("Classes to be tested: ALL");
        } else {
            System.out.println("Classes to be tested:");
            for (String clazz : getClasses()) {
                System.out.println("  " + clazz);
            }
        }

        RunTestsRequest runTests = new RunTestsRequest();
        runTests.setAllTests(getRunAllTests());
        runTests.setClasses(getClasses());
        
        if (getNamespace() != null) {
            runTests.setNamespace(getNamespace());
        }

        try {
            SoapConnection sc = getApexConnection();

            // set debug log level
            DebuggingHeader_element debugHeader = new DebuggingHeader_element();
            debugHeader.setDebugLevel(readLogType());
            sc.__setDebuggingHeader(debugHeader);

            // run tests
            Calendar startTime = Calendar.getInstance();
            RunTestsResult res = sc.runTests(runTests);
            TestResult tr = new TestResult(res, startTime.getTime(), coverageTarget);

            // save debug log
            if (sc.getDebuggingInfo() != null) {
                tr.separateDebugLog(outDir, sc.getDebuggingInfo().getDebugLog());
            }

            // convert the result to HTML
            tr.visualizeCoverageResults(outDir, srcClassMap, srcTriggerMap);
            
        } catch (BuildException e) {
            e.printStackTrace();
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new BuildException(e);
        }
    }

    public String[] getClasses() {
        List<String> validTestClasses = new ArrayList<String>();
        for (ClassElement clazz : testClasses) {
            if (clazz.getTest()) {
                validTestClasses.add(clazz.text);
            }
        }
        return validTestClasses.toArray(new String[0]);
    }

    public void addClass(ClassElement clazz) {
        this.testClasses.add(clazz);
    }

    public boolean getRunAllTests() {
        return this.runAllTests;
    }

    public void setRunAllTests(boolean allTests) {
        this.runAllTests = allTests;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public File getOutDir() {
        return outDir;
    }

    public void setOutDir(String outDirPath) throws IOException {
        this.outDir = new File(outDirPath);

        if (!this.outDir.exists() && !this.outDir.mkdirs()) {
            throw new IOException("Failed to create diretory: " + this.outDir.getAbsolutePath());
        }
    }

    public File getSrcDir() {
        return srcDir;
    }

    public void setSrcDir(File srcDirPath) {
        this.srcDir = srcDirPath;

        // create a Map of classes - key=class name, value=source file
        File classesDir = new File(srcDirPath + "/classes");
        File[] classes = classesDir.listFiles(
                new FilenameFilter() {
                    public boolean accept(File dir, String name) {
                        return name.endsWith(".cls");
                    }
                });

        for (File srcFile : classes) {
            String programName = srcFile.getName().replaceAll("\\.cls$", "");
            srcClassMap.put(programName, srcFile);
        }
        
        // create a Map of triggers - key=trigger name, value=source file
        File triggersDir = new File(srcDirPath + "/triggers");
        File[] triggers = triggersDir.listFiles(
                new FilenameFilter() {
                    public boolean accept(File dir, String name) {
                        return name.endsWith(".trigger");
                    }
                });

        for (File srcFile : triggers) {
            String programName = srcFile.getName().replaceAll("\\.trigger$", "");
            srcTriggerMap.put(programName, srcFile);
        }
    }

    public int getCoverageTarget() {
        return coverageTarget;
    }

    public void setCoverageTarget(int coverageTarget) {
        this.coverageTarget = coverageTarget;
    }

    public String getLogType() {
        return this.logType;
    }

    public void setLogType(String logType) {
        this.logType = logType;
    }

    private LogType readLogType() {
        return logType == null ? LogType.None : LogType.valueOf(logType);
    }

    public static class ClassElement {
        private String text = "";
        private boolean test = true;
        
        public void addText(String text) {
            this.text += text;
        }

        public String getText() {
            return this.text;
        }
        
        public void setTest(boolean test) {
            this.test = test;
        }
        
        public boolean getTest() {
            return this.test;
        }
    }
}
