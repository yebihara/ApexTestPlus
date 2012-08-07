package com.force.jp.ant.apextestplus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringEscapeUtils;

import com.sforce.soap.apex.CodeCoverageResult;
import com.sforce.soap.apex.CodeCoverageWarning;
import com.sforce.soap.apex.CodeLocation;

public class ClassCoverageResult {
    public enum STATUS {COVERED, NOTCOVERED, IGNORED}
    
    private String className;
    private String coverageWarning;
    private int numLocations;
    private int numLocationsNotCovered;
    private Map<Integer, CodeLocationStatus> lineCoverages = new HashMap<Integer, CodeLocationStatus>();
    
    public ClassCoverageResult(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }
    
    public void setCoverageWarning(CodeCoverageWarning warning) {
        this.coverageWarning = warning.getMessage();
    }
    
    public void addCoverageResult(CodeCoverageResult result) {
        this.numLocations = result.getNumLocations();
        this.numLocationsNotCovered = result.getNumLocationsNotCovered();
        
        for (CodeLocation loc : result.getLocationsNotCovered()) {
            lineCoverages.put(loc.getLine(), new CodeLocationStatus(loc, STATUS.NOTCOVERED));
            
        }
    }
    
    public int getNumLocations() {
        return numLocations;
    }
    
    public int getNumLocationsCovered() {
        return numLocations - numLocationsNotCovered;
    }
    
    public int getNumLocationsNotCovered() {
        return numLocationsNotCovered;
    }

    public BigDecimal getPctCoverage() {
        if (numLocations == 0) {
            return null;
        } else {
            return (new BigDecimal(String.valueOf(((double)numLocations - numLocationsNotCovered) / numLocations * 100))).setScale(2, BigDecimal.ROUND_DOWN);
        }
    }

    public String getCoverageWarning() {
        return coverageWarning;
    }

    public STATUS getCoverageStatus(int line) {
        CodeLocationStatus status = lineCoverages.get(line);
        if (status != null) {
            return status.getStatus();
        } else {
            return STATUS.IGNORED;
        }
    }
    
    public void toHtml(File src, File result) throws IOException {
        BufferedReader in = null;
        PrintStream out = null;

        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(src), "UTF-8"));
            out = new PrintStream(result, "UTF-8");
            
            out.println("<html>");
            out.println("<head>");
            out.println("<title>" + className + "</title>");
            out.println("<link href=\"../coverage.css\" rel=\"stylesheet\" type=\"text/css\" />");
            out.println("</head>");
            out.println("<body>");
            out.println("<h1>" + className + ": " + (getPctCoverage() != null ? getPctCoverage() : "-") + " % (" + getNumLocationsCovered() + "/" + getNumLocations() + ")" + "</h1>");
            out.println("<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" colspan=\"5\">");
            out.println("<tr>");
            out.println("    <td class=\"graphHeader\"><b>&nbsp;line</b></td>");
            out.println("    <td class=\"graphHeader\"><b>&nbsp;source</b></td>");
            out.println("</tr>");

            int lineNo = 0;
            String line = null;

            while ((line = in.readLine()) != null) {
                lineNo ++;
                
                String lineClass = null;
                switch (getCoverageStatus(lineNo)) {
                case COVERED:
                    lineClass = "lineCov";
                    break;
                case NOTCOVERED:
                    lineClass = "lineNoCov";
                    break;
                case IGNORED:
                    lineClass = "srcLine";
                    break;
                }

                out.println("<tr>");
                out.println("    <td align=\"right\" class=\"lineCount\">" + lineNo + "</td>");
                out.println("    <td class=\"srcCell\"><span class=\"" + lineClass + "\">" + StringEscapeUtils.escapeHtml(line) + "</span></td>");
                out.println("</tr>");
            }
            
            out.println("</table>");
            out.println("</body>");
            out.println("</html>");

        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
            
            if (out != null) {
                out.close();
            }
        }
    }
    
    @Override
    public String toString() {
        if (coverageWarning == null) {
            return className + ": " + getPctCoverage() + "% (" + getNumLocationsCovered() + "/" + getNumLocations() + ")";
        } else {
            return className + ": " + getPctCoverage() + "% (" + getNumLocationsCovered() + "/" + getNumLocations() + ") - " + coverageWarning;
        }
    }

    public static class CodeLocationStatus {
        private CodeLocation location;
        private STATUS status;

        public CodeLocationStatus(CodeLocation location, STATUS status) {
            this.location = location;
            setStatus(status);
        }

        public STATUS getStatus() {
            return status;
        }

        public void setStatus(STATUS status) {
            this.status = status;
        }

        public int getColumn() {
            return location.getColumn();
        }

        public int getLine() {
            return location.getLine();
        }

        public int getNumExecutions() {
            return location.getNumExecutions();
        }

        public double getTime() {
            return location.getTime();
        }
    }
}
