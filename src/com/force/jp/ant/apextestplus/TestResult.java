/**
 * Copyright (c) 2012, salesforce.com, inc. All rights reserved.
 */
package com.force.jp.ant.apextestplus;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sforce.soap.apex.CodeCoverageResult;
import com.sforce.soap.apex.CodeCoverageWarning;
import com.sforce.soap.apex.RunTestFailure;
import com.sforce.soap.apex.RunTestSuccess;
import com.sforce.soap.apex.RunTestsResult;

public class TestResult {
    private final String CSS_FILENAME = "coverage.css";
    
    private Date startTime = null;
    private double totalTime;
    private int numTestsRun;
    private int numTestsSuccess;
    private int numTestsFailures;
    private String coverageWarning;
    private Map<String, TestClassResult> testClassResults = new TreeMap<String, TestClassResult>();
    private Map<String, ClassCoverageResult> classCoverageResults = new TreeMap<String, ClassCoverageResult>();
    private Map<String, ClassCoverageResult> triggerCoverageResults = new TreeMap<String, ClassCoverageResult>();
    private int coverageTarget;

    public TestResult(RunTestsResult result, Date startTime, int coverageTarget) {
        this.totalTime = result.getTotalTime();
        this.numTestsRun = result.getNumTestsRun();
        this.numTestsFailures = result.getNumFailures();
        this.numTestsSuccess = numTestsRun - numTestsFailures;
        this.startTime = startTime;
        this.coverageTarget = coverageTarget;

        for (RunTestSuccess success : result.getSuccesses()) {
            String testClassName = success.getName();
            TestClassResult tcr = testClassResults.get(testClassName);
            if (tcr == null) {
                tcr = new TestClassResult(testClassName);
                testClassResults.put(testClassName, tcr);
            }
            tcr.addSuccess(success);
        }

        for (RunTestFailure failure : result.getFailures()) {
            String testClassName = failure.getName();
            TestClassResult tcr = testClassResults.get(testClassName);
            if (tcr == null) {
                tcr = new TestClassResult(testClassName);
                testClassResults.put(testClassName, tcr);
            }
            tcr.addFailure(failure);
        }
        
        for (CodeCoverageResult coverage : result.getCodeCoverage()) {
            String namespace = coverage.getNamespace();
            String className = namespace == null ? coverage.getName() : namespace + "__" + coverage.getName();
            Map<String, ClassCoverageResult> targetCoverageResults = null;

            // We need to identify the object type for which the CodeCoverageResult object is created
            // since a class and a trigger with the same name can exist.
            // CodeCoverageResult#getType() seems the only way to do that
            // although it's now "obsoleted" according to Metadata API Developer's Guide.
            String classType = coverage.getType();
            if (classType.equals("Class")) {
                targetCoverageResults = classCoverageResults;
            } else if (classType.equals("Trigger")) {
                targetCoverageResults = triggerCoverageResults;
            } else {
                throw new RuntimeException("Type \"" + classType + "\" is not supported.");
            }
            
            ClassCoverageResult ccr = targetCoverageResults.get(className);
            if (ccr == null) {
                ccr = new ClassCoverageResult(className);
                targetCoverageResults.put(className, ccr);
            }
            ccr.addCoverageResult(coverage);
        }
        
        for (CodeCoverageWarning warning : result.getCodeCoverageWarnings()) {
            String namespace = warning.getNamespace();
            String className = namespace == null ? warning.getName() : namespace + "__" + warning.getName();
            
            if (className == null) {
                this.coverageWarning = warning.getMessage();
            } else {
                // When a class and a trigger have the same name,
                // we cannot determine for which one the coverage warning is generated.
                // Since coverage warnings are generated against triggers in most cases,
                // try to find a trigger with that name first, then a normal class.
                // We don't assume CodeCoverageWarning that don't come with CodeCoverageResult
                // with the same class/trigger name for the time being.
                ClassCoverageResult ccr = triggerCoverageResults.get(className);
                if (ccr == null) {
                    ccr = classCoverageResults.get(className);
                    if (ccr == null) {
                        throw new NullPointerException("Trigger nor class not found: " + className);
                    }
                }
                ccr.setCoverageWarning(warning);
            }
        }
    }

    public void separateDebugLog(File outDir, String debugLog) throws IOException {
        File debugLogDir = new File(outDir, "debuglogs");
        if (!debugLogDir.exists() && !debugLogDir.mkdirs()) {
            throw new IOException("Failed to create diretory: " + debugLogDir.getAbsolutePath());
        }
        
        // save the whole debug log
        PrintWriter out = null;
        try {
            out = new PrintWriter(new File(debugLogDir, "debug.log"), "UTF-8");
            out.println(debugLog);
        } finally {
            if (out != null) {
                out.close();
            }
        }

        BufferedReader in = null;
        String className = null;
        String methodName = null;
        String prevClassName = null;
        String prevMethodName = null;
        boolean foundNames = false;
        Pattern p = Pattern.compile("\\|CODE_UNIT_STARTED\\|.+\\|(.+)\\.(.+)$");
        StringBuilder sb = null;
        String line = null;
        
        // split debug log by class and method
        in = new BufferedReader(new StringReader(debugLog));

        while ((line = in.readLine()) != null) {
            if (line.endsWith("|EXECUTION_STARTED")) {
                // found the beginning of a test method execution
                sb = new StringBuilder();
            }

            if (sb != null) {
                // try to find 'CODE_UNIT_STARTED' line to get class name and method name
                if (!foundNames) {
                    // this line contains 'CODE_UNIT_STARTED'?
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        foundNames = true;
                        className = m.group(1);
                        methodName = m.group(2);
                    }
                }

                sb.append(line);
                sb.append('\n');
                
                if (line.endsWith("|EXECUTION_FINISHED")) {
                    // found the end of a test method execution
                    TestClassResult classResult = testClassResults.get(className);
                    if (classResult != null) {
                        classResult.attachDebugLog(debugLogDir, methodName, sb.toString());
                        prevClassName = className;
                        prevMethodName = methodName;
                    } else {
                    	// this block of debug logs may be generated by asynchronous calls, such as @future or Batch Apex
                    	// let's append it to that for previous Apex test call
                        classResult = testClassResults.get(prevClassName);
                        if (classResult != null) {
                            classResult.attachDebugLog(debugLogDir, prevMethodName, sb.toString());
                        }
                    }

                    foundNames = false;
                    className = null;
                    methodName = null;
                    sb = null;
                }
            }
        }
    }

    public void visualizeCoverageResults(File outDir, Map<String, File> srcClassMap, Map<String, File> srcTriggerMap) throws IOException {
        deployStyleSheet(outDir);
        genCoverageResultsHtml(new File(outDir, "classes"), classCoverageResults, srcClassMap);
        genCoverageResultsHtml(new File(outDir, "triggers"), triggerCoverageResults, srcTriggerMap);
        
        // create index page
        PrintStream out = null;
        try {
            out = new PrintStream(new File(outDir, "index.html"), "UTF-8");
            out.println(toHtml());
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    private void deployStyleSheet(File outDir) throws IOException {
        BufferedInputStream in = null;
        BufferedOutputStream out = null;

        try {
            URL url = this.getClass().getResource(CSS_FILENAME);
            in = new BufferedInputStream(url.openStream());
            out = new BufferedOutputStream(new FileOutputStream(new File(outDir, CSS_FILENAME)));
            int b;
            while ((b = in.read()) >= 0) {
                out.write(b);
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
            
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                }
            }
        }
    }
    
    private void genCoverageResultsHtml(File outDir, Map<String, ClassCoverageResult> coverageResults, Map<String, File> sourceMap) throws IOException {
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Failed to create diretory: " + outDir.getAbsolutePath());
        }
        
        for (String programName : coverageResults.keySet()) {
            ClassCoverageResult ccr = coverageResults.get(programName);
            File src = sourceMap.get(programName);
            if (!src.exists()) {
                throw new IOException("Source file is not found: " + programName);
            }
            File result = new File(outDir, programName + ".html");
            ccr.toHtml(src, result);
        }
    }
    
    public BigDecimal getTotalPctCoverage() {
        double numLocations = 0;
        double numLocationsCovered = 0;
        
        for (ClassCoverageResult coverage : classCoverageResults.values()) {
            numLocations += coverage.getNumLocations();
            numLocationsCovered += coverage.getNumLocationsCovered(); 
        }
        
        for (ClassCoverageResult coverage : triggerCoverageResults.values()) {
            numLocations += coverage.getNumLocations();
            numLocationsCovered += coverage.getNumLocationsCovered(); 
        }
        
        if (numLocations > 0) {
            return (new BigDecimal(String.valueOf(numLocationsCovered / numLocations * 100))).setScale(2, BigDecimal.ROUND_DOWN);
        } else {
            return BigDecimal.ZERO;
        }
    }

    public String toHtml() {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

        sb.append("<html>\n");
        sb.append("<head>\n");
        sb.append("<title>Test Results</title>\n");
        sb.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">");
        sb.append("<link href=\"coverage.css\" rel=\"stylesheet\" type=\"text/css\" />\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("<h1>Test Results</h1>\n");
        
        sb.append("<h2>Summary</h2>\n");
        sb.append("<table class=\"detailList\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">\n");
        sb.append("<tr>\n");
        sb.append("    <td class=\"labelCol\">Start Time</td>\n");
        sb.append("    <td class=\"data2Col\">" + sdf.format(startTime) + "</td>\n");
        sb.append("</tr>\n");
        sb.append("<tr>\n");
        sb.append("    <td class=\"labelCol\">Total Time</td>\n");
        sb.append("    <td class=\"data2Col\">" + (totalTime / 1000) + " sec</td>\n");
        sb.append("</tr>\n");
        sb.append("<tr>\n");
        sb.append("    <td class=\"labelCol\">Tests Run</td>\n");
        sb.append("    <td class=\"data2Col\">" + numTestsRun + "</td>\n");
        sb.append("</tr>\n");
        sb.append("<tr>\n");
        sb.append("    <td class=\"labelCol\">Tests Failures</td>\n");
        
        if (numTestsFailures == 0) {
            sb.append("    <td class=\"data2Col success\">" + numTestsFailures + "</td>\n");
        } else {
            sb.append("    <td class=\"data2Col failure\">" + numTestsFailures + "</td>\n");
        }
        
        sb.append("</tr>\n");
        
        sb.append("<tr>\n");
        sb.append("    <td class=\"labelCol\">Code Coverage Total</td>\n");
        if (getTotalPctCoverage().doubleValue() >= coverageTarget) {
            sb.append("    <td class=\"data2Col success\">" + getTotalPctCoverage() + "%</td>\n");
        } else {
            sb.append("    <td class=\"data2Col failure\">" + getTotalPctCoverage() + "%</td>\n");
        }
        sb.append("</tr>\n");

        if (coverageWarning != null) {
            sb.append("<tr>\n");
            sb.append("    <td class=\"labelCol\">Test Coverage Warning</td>\n");
            sb.append("    <td class=\"data2Col\">" + coverageWarning + "</td>\n");
            sb.append("</tr>\n");
        }
        
        sb.append("</table>\n");

        sb.append("<h2>Test Results</h2>\n");
        for (TestClassResult tcr : testClassResults.values()) {
            sb.append(tcr.toHtml());
        }

        sb.append("<h2>Code Coverage</h2>\n");
        sb.append("<h3>Apex Classes</h3>\n");
        sb.append("<table class=\"detailList\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">\n");
        sb.append("<colgroup span=\"2\">\n");
        sb.append("    <col width=\"20%\" />\n");
        sb.append("    <col width=\"20%\" />\n");
        sb.append("    <col width=\"60%\" />\n");
        sb.append("</colgroup>\n");
        sb.append("<thead class=\"rich-table-thead\">\n");
        sb.append("    <tr class=\"headerRow\">\n");
        sb.append("        <th class=\"headerRow\" scope=\"col\" colspan=\"1\">Class Name</th>\n");
        sb.append("        <th class=\"headerRow\" scope=\"col\" colspan=\"1\">Coverage</th>\n");
        sb.append("        <th class=\"headerRow\" scope=\"col\" colspan=\"1\">Warning</th>\n");
        sb.append("    </tr>\n");
        sb.append("</thead>\n");
        sb.append("<tbody>\n");

        for (ClassCoverageResult ccr : classCoverageResults.values()) {
            BigDecimal pctCoverage = ccr.getPctCoverage();
            if (pctCoverage != null && pctCoverage.doubleValue() >= coverageTarget) {
                sb.append("    <tr class=\"dataRow success\">\n");
            } else if (ccr.getCoverageWarning() == null) {
                sb.append("    <tr class=\"dataRow\">\n");
            } else {
                sb.append("    <tr class=\"dataRow failure\">\n");
            }
            sb.append("        <td class=\"dataCell\" colspan=\"1\">" + ccr.getClassName() + "</td>\n");
            sb.append("        <td class=\"dataCell\" colspan=\"1\"><a href=\"classes/" + ccr.getClassName() + ".html\">" + (pctCoverage != null ? pctCoverage : " - ") + "% (" + ccr.getNumLocationsCovered() + "/" + ccr.getNumLocations() + ")</a></td>\n");
            sb.append("        <td class=\"dataCell\" colspan=\"1\">" + (ccr.getCoverageWarning() == null ? "" : ccr.getCoverageWarning()) + "</td>\n");
            sb.append("    </tr>\n");
        }

        sb.append("</tbody>\n");
        sb.append("</table>\n");

        sb.append("<h3>Apex Triggers</h3>\n");
        sb.append("<table class=\"detailList\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">\n");
        sb.append("<colgroup span=\"2\">\n");
        sb.append("    <col width=\"20%\" />\n");
        sb.append("    <col width=\"20%\" />\n");
        sb.append("    <col width=\"60%\" />\n");
        sb.append("</colgroup>\n");
        sb.append("<thead class=\"rich-table-thead\">\n");
        sb.append("    <tr class=\"headerRow\">\n");
        sb.append("        <th class=\"headerRow\" scope=\"col\" colspan=\"1\">Trigger Name</th>\n");
        sb.append("        <th class=\"headerRow\" scope=\"col\" colspan=\"1\">Coverage</th>\n");
        sb.append("        <th class=\"headerRow\" scope=\"col\" colspan=\"1\">Warning</th>\n");
        sb.append("    </tr>\n");
        sb.append("</thead>\n");
        sb.append("<tbody>\n");

        for (ClassCoverageResult ccr : triggerCoverageResults.values()) {
            BigDecimal pctCoverage = ccr.getPctCoverage();
            if (pctCoverage != null && pctCoverage.doubleValue() >= coverageTarget) {
                sb.append("    <tr class=\"dataRow success\">\n");
            } else if (ccr.getCoverageWarning() == null) {
                sb.append("    <tr class=\"dataRow\">\n");
            } else {
                sb.append("    <tr class=\"dataRow failure\">\n");
            }
            sb.append("        <td class=\"dataCell\" colspan=\"1\">" + ccr.getClassName() + "</td>\n");
            sb.append("        <td class=\"dataCell\" colspan=\"1\"><a href=\"triggers/" + ccr.getClassName() + ".html\">" + (pctCoverage != null ? pctCoverage : " - ") + "% (" + ccr.getNumLocationsCovered() + "/" + ccr.getNumLocations() + ")</a></td>\n");
            sb.append("        <td class=\"dataCell\" colspan=\"1\">" + (ccr.getCoverageWarning() == null ? "" : ccr.getCoverageWarning()) + "</td>\n");
            sb.append("    </tr>\n");
        }

        sb.append("</tbody>\n");
        sb.append("</table>\n");

        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("------------------------------\n");
        sb.append("Overall Statistics\n");
        sb.append("------------------------------\n");
        sb.append("Total Time: " + (totalTime / 1000) + " sec\n");
        sb.append("Total Number of Tests: " + numTestsRun + "\n");
        sb.append("Number of Successfull Tests: " + numTestsSuccess + "\n");
        sb.append("Number of failure Tests: " + numTestsFailures + "\n");
        
        if (coverageWarning == null) {
            sb.append("Total Code Coverage: " + getTotalPctCoverage() + "%\n");
        } else {
            sb.append("Total Code Coverage: " + getTotalPctCoverage() + "% - " + coverageWarning + "\n");
        }
        
        sb.append("\n------------------------------\n");
        sb.append("Test Results\n");
        sb.append("------------------------------\n");
        for (TestClassResult tcr : testClassResults.values()) {
            sb.append(tcr.toString() + "\n");
        }
        
        sb.append("\n------------------------------\n");
        sb.append("Apex Class Coverage Results\n");
        sb.append("------------------------------\n");
        for (ClassCoverageResult ccr : classCoverageResults.values()) {
            sb.append(ccr.toString() + "\n");
        }
        
        sb.append("\n------------------------------\n");
        sb.append("Trigger Coverage Results\n");
        sb.append("------------------------------\n");
        for (ClassCoverageResult ccr : triggerCoverageResults.values()) {
            sb.append(ccr.toString() + "\n");
        }
        
        return sb.toString();
    }
    
}
