package com.force.jp.ant.apextestplus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sforce.soap.apex.RunTestFailure;
import com.sforce.soap.apex.RunTestSuccess;

public class TestClassResult {
    private String className;
    private List<RunTestSuccess> successes = new ArrayList<RunTestSuccess>();
    private List<RunTestFailure> failures = new ArrayList<RunTestFailure>();
    private Map<String, File> debugLogs = new HashMap<String, File>();
    
    public TestClassResult(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }
    
    public void addSuccess(RunTestSuccess success) {
        successes.add(success);
    }
    
    public void addFailure(RunTestFailure failure) {
        failures.add(failure);
    }

    public File getDebugLog(String methodName) {
        return debugLogs.get(methodName);
    }

    public void attachDebugLog(File outDir, String methodName, String debugLog) throws IOException {
        // prepare output directory
        File classDir = new File(outDir, className);
        if (!classDir.exists() && !classDir.mkdirs()) {
            throw new IOException("Failed to create diretory: " + classDir.getAbsolutePath());
        }

        // write debug log to file
        File logFile = new File(classDir, methodName + ".log");
        PrintWriter out = null;
        try {
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile, true) ,"UTF-8")));
            out.println(debugLog);
        } finally {
            if (out != null) {
                out.close();
            }
        }
        
        debugLogs.put(methodName, logFile);
    }

    public String toHtml() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("<h3>" + className + "</h3>\n");
        sb.append("<table class=\"detailList\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">\n");
        sb.append("<colgroup span=\"5\">\n");
        sb.append("    <col width=\"15%\" />\n");
        sb.append("    <col width=\"5%\" />\n");
        sb.append("    <col width=\"5%\" />\n");
        sb.append("    <col width=\"40%\" />\n");
        sb.append("    <col width=\"30%\" />\n");
        sb.append("    <col width=\"5%\" />\n");
        sb.append("</colgroup>\n");
        sb.append("<thead class=\"rich-table-thead\">\n");
        sb.append("    <tr class=\"headerRow\">\n");
        sb.append("        <th class=\"headerRow\" scope=\"col\" colspan=\"1\">Method Name</th>\n");
        sb.append("        <th class=\"headerRow\" scope=\"col\" colspan=\"1\">Result</th>\n");
        sb.append("        <th class=\"headerRow\" scope=\"col\" colspan=\"1\">Total Time</th>\n");
        sb.append("        <th class=\"headerRow\" scope=\"col\" colspan=\"1\">Message</th>\n");
        sb.append("        <th class=\"headerRow\" scope=\"col\" colspan=\"1\">Stack Trace</th>\n");
        sb.append("        <th class=\"headerRow\" scope=\"col\" colspan=\"1\">Debug Log</th>\n");
        sb.append("    </tr>\n");
        sb.append("</thead>\n");
        sb.append("<tbody>\n");

        for (RunTestSuccess success : successes) {
            sb.append("    <tr class=\"dataRow success\"  onmouseover=\"if (window.hiOn){hiOn(this);} \" onmouseout=\"if (window.hiOff){hiOff(this);} \" onBlur=\"if (window.hiOff){hiOff(this);}\" onFocus=\"if (window.hiOn){hiOn(this);}\">\n");
            sb.append("        <td class=\"dataCell\" colspan=\"1\">" + success.getMethodName() + "</td>\n");
            sb.append("        <td class=\"dataCell\" colspan=\"1\">SUCCESS</td>\n");
            sb.append("        <td class=\"dataCell\" colspan=\"1\">" + (success.getTime() / 1000) + " sec</td>\n");
            sb.append("        <td class=\"dataCell\" colspan=\"1\"></td>\n");
            sb.append("        <td class=\"dataCell\" colspan=\"1\"></td>\n");
            
            if (debugLogs.get(success.getMethodName()) != null) {
                sb.append("        <td class=\"dataCell\" colspan=\"1\"><a href=\"" + debugLogs.get(success.getMethodName()).getAbsolutePath() + "\">View</a></td>\n");
            } else {
                sb.append("        <td class=\"dataCell\" colspan=\"1\"></td>\n");
            }

            sb.append("    </tr>\n");
        }
        
        for (RunTestFailure failure : failures) {
            sb.append("    <tr class=\"dataRow failure\"  onmouseover=\"if (window.hiOn){hiOn(this);} \" onmouseout=\"if (window.hiOff){hiOff(this);} \" onBlur=\"if (window.hiOff){hiOff(this);}\" onFocus=\"if (window.hiOn){hiOn(this);}\">\n");
            sb.append("        <td class=\"dataCell\" colspan=\"1\">" + failure.getMethodName() + "</td>\n");
            sb.append("        <td class=\"dataCell\" colspan=\"1\">FAILURE</td>\n");
            sb.append("        <td class=\"dataCell\" colspan=\"1\">" + (failure.getTime() / 1000) + " sec</td>\n");
            sb.append("        <td class=\"dataCell\" colspan=\"1\">" + failure.getMessage() + "</td>\n");
            sb.append("        <td class=\"dataCell\" colspan=\"1\">" + failure.getStackTrace() + "</td>\n");

            if (debugLogs.get(failure.getMethodName()) != null) {
                sb.append("        <td class=\"dataCell\" colspan=\"1\"><a href=\"" + debugLogs.get(failure.getMethodName()).getAbsolutePath() + "\">View</a></td>\n");
            } else {
                sb.append("        <td class=\"dataCell\" colspan=\"1\"></td>\n");
            }

            sb.append("    </tr>\n");
        }

        sb.append("</tbody>\n");
        sb.append("</table>\n");
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[" + className + "]\n");

        for (RunTestSuccess success : successes) {
            sb.append("SUCCESS: " + success.getMethodName() + " (" + (success.getTime() / 1000) + " sec)\n");
        }
        
        for (RunTestFailure failure : failures) {
            sb.append("FAILURE: " + failure.getMethodName() + " (" + (failure.getTime() / 1000) + " sec) - " + failure.getMessage() + "\n");
        }

        return sb.toString();
    }
}
