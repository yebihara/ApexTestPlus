Apex Test Plus for Force.com Migration Tool
===========================================
\[[Japanese version](/DeveloperForceJapan/ApexTestPlus/blob/master/README_ja.md)\]

Introduction
------------
Apex Test Plus for Force.com Migration Tool is a custom Ant task to make testing Apex programs easier and faster that works with Force.com Migration Tool.  
This will significantly improve the productivity of Apex development, especially when this is used with Force.com IDE (Eclipse), and help you implement TDD(Test Driven Development).  
With Apex Test Plus, the following features, that are not provided by Force.com Migration Tool nor Force.com IDE, become possible:

* to save test results
* to visualize code coverage
* to view a specific part of debug log that is associated with each test method

Clone or download this repository and open `doc/sample_result/index.html` in a web browser. You can see a sample test result that Apex Test Plus actually generated.  
To know Force.com Migration Tool, see the [Force.com Migration Tool Guide](http://www.salesforce.com/us/developer/docs/daas/index.htm).


Installation
------------

1. Install Force.com Migration Tool (and Ant). Force.com Migration Tool versions that were confirmed to work with Apex Test Plus are 21.0 through 28.0.
1. Download [Apache Commons Lang](http://commons.apache.org/lang/download\_lang.cgi), version 2.6 or newer, and extract `commons-lang-`*version*`.jar` in it. Or you can simply use `lib/commons-lang-2.6.jar` bundled with Apex Test Plus
1. Copy `commons-lang-`*version*`.jar` and `ant-apextestplus.jar` into your Ant installation `lib/` directory.


Creating build.xml
------------------
The easist way to create a proper build.xml would be to start with the sample build.xml and build.properties that come with Force.com Migration Tool.  
At first, open build.xml and add an attribute setting, **xmlns:apextestplus="antlib:com.force.jp.ant.apextestplus"**, to the "project" root element as follows.

    <project name="Sample usage of Salesforce Ant tasks" default="test" basedir="." xmlns:sf="antlib:com.salesforce" xmlns:apextestplus="antlib:com.force.jp.ant.apextestplus">

The name of Ant task to run tests is **runTest**.

Some sample definitions, that will match typical cases, are shown below.

### Retrieving the Latest Source Files from the Server
Using the following task definitions, Apex source code (classes and triggers) are always retrieved from the server just before tests are ran.  
This is the most secure way to correctly generate visual code coverage results because source code may be changed by anyone else on the server.

    <property name="outDir" value="test_result"/>
    <property name="srcDir" value="${outDir}/src"/>

    <target name="runTest" depends="cleanOutDir,bulkRetrieveClasses,bulkRetrieveTriggers">
      <apextestplus:runTest username="${sf.username}" password="${sf.password}" serverurl="${sf.serverurl}" srcDir="${srcDir}" outDir="${outDir}" runAllTests="false" logType="Debugonly" >
        <class>TestClass1</class>
        <class>TestClass2</class>
        <class>TestClass3</class>
      </apextestplus:runTest>
    </target>

    <target name="cleanOutDir">
      <delete dir="${outDir}" />
    </target>

    <target name="bulkRetrieveClasses">
      <mkdir dir="${srcDir}"/>
      <sf:bulkRetrieve username="${sf.username}" password="${sf.password}" serverurl="${sf.serverurl}" metadataType="ApexClass" retrieveTarget="${srcDir}"/>
    </target>

    <target name="bulkRetrieveTriggers">
      <mkdir dir="${srcDir}"/>
      <sf:bulkRetrieve username="${sf.username}" password="${sf.password}" serverurl="${sf.serverurl}" metadataType="ApexTrigger" retrieveTarget="${srcDir}"/>
    </target>

### Using Local Source Files
Retrieving source code from the server is a little bit time-consuming task although it's reliable.  
If you have a dedicated salesforce.com environment (e.g. Developer Edition), it would be safe enough to use the source code that resides on your local PC to generate visual code coverage results.  
Note that this example assumes build.xml is located in the same diretory where your `src/` diretory of Force.com project exists.

    <property name="outDir" value="test_result"/>
    <property name="srcDir" value="src"/>

    <target name="runTestLocal" depends="cleanOutDir">
      <apextestplus:runTest username="${sf.username}" password="${sf.password}" serverurl="${sf.serverurl}" srcDir="${srcDir}" outDir="${outDir}" runAllTests="false" logType="Debugonly" >
        <class>TestClass1</class>
        <class>TestClass2</class>
        <class>TestClass3</class>
      </apextestplus:runTest>
    </target>

    <target name="cleanOutDir">
      <delete dir="${outDir}" />
    </target>


Running Test
------------
Just run the target that contains the runTest test you defined from Ant.  
Once build successfully completes, you will find the test result created in the directory specified by outDir attribute in the runTest task.  
Open index.html in it with web browser to see the test results.


RunTest Task References
-----------------------
The **runTest** task is used to run a set of Apex tests. Test classes and target classes/triggers must be deployed beforehand.

<dl>
<dt>**username**</dt>

<dd>Required attribute. The Salesforce username for login.</dd>

<dt>**password**</dt>

<dd>Required attribute. The Salesforce password for login. If you are using a security token, paste the 25-digit token value to the end of your password.</dd>

<dt>**serverurl**</dt>

<dd>Optional attribute. Defaults to 'https://login.salesforce.com'. This is useful for working against the sandbox instance on test.salesforce.com. </dd>

<dt>**srcDir**</dt>

<dd>Required attribute. The directory that contains `classes/` and `triggers/` diretories where source files of Apex classes and triggers are saved.
Such source files are used as the base of visual code coverage results by colorization.
</dd>

<dt>**outDir**</dt>

<dd>Required attribute. The directory where test result files are saved.</dd>

<dt>**coverageTarget**</dt>

<dd>Optional attribute. Defaults to 75. The percentage number of code coverage target. When the actual coverage doesn't reach the target, the number is red-colored in the test result.</dd>

<dt>**runAllTests**</dt>

<dd>Optional attribute (true/false). Defaults to true. If set to true, the task will run all Apex tests that are deployed.</dd>

<dt>**logType**</dt>
<dd>Optional attribute.  Defaults to 'None'. The debug logging level for tests. Valid options are 'None', 'Debugonly', 'Db', 'Profiling', 'Callout', and 'Detail'.</dd>

<dt>**class**</dt>

<dd>Optional child elements. A list of Apex test classes to be ran. When runAllTests="false", at least one element must be specified. Classes must be deployed beforehand. Each element can have an attribute named "test" (true/false, defaulted to true). Classes with `test="false"` are ignored and not tested.</dd>


Working with Force.com IDE
--------------------------
To make Apex Test Plus work with Force.com IDE (Eclipse), you need to configure Ant-addon of Eclipse first.

1. Open "Preferences" window. (For Windows, [Window] > [Prefernces] / For Mac, [Eclipse] > [Preferences])
1. Open [Ant] > [Runtime] from the left-sided tree and select "Classpath" tab.
1. Add the following three JAR files to Global Entries and click OK.
    * ant-salesforce.jar (Force.com Migration Tool)
    * ant-apextestplus.jar
    * commons-lang-*version*.jar

build.xml and build.properties should be put in the root diretory of the Force.com project you are working on.

Now you can run the test as follows.

1. Right click the build.xml shown in Package Explorer.
1. Choose [Run As] > [2 Ant Build...].
1. Check the target that contains your runTest task.
1. Press "Run" and the test starts.

When you run the same test, you can do it just by clicking "Run External Tool" icon in the tool bar.
