/*
 * Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package perffmwk;

import com.gemstone.gemfire.LogWriter;
import com.gemstone.gemfire.SystemFailure;

import hydra.*;

import java.io.*;
import java.text.BreakIterator;
import java.util.*;

import util.TestHelper;

// @todo lises Perhaps go ahead and print all specifications used by the test for easy editing later.
/**
 *  Generates performance reports for specified test directories.  The report
 *  for each test is generated by reading its statistics archives based on
 *  the trim and statistics specifications for the test.
 *  <p>
 *  Test directories are given as command line arguments.  If none are
 *  specified, a report is generated for the current working directory.
 *  <p>
 *  The trim specifications are looked up in "trim.spec" in the test directory.
 *  If not found, all trim specifications used by the test are assumed to
 *  encompass the lifetime of each associated statistic.
 *  To alter the trim used by a test, this file must exist and contain
 *  the desired endpoints.
 *  <p>
 *  The statistics specifications are looked up in "statistics.spec" in the
 *  test directory.  If not found, the default statistics in this package
 *  are used.  To alter the statistics specification used to generate a
 *  performance report, this file can either be edited in place or overridden
 *  via the system property "-DstatSpecFile".  In the latter case, the
 *  statistics specification applies to all tests for which reports are being
 *  generated.
 *  <p>
 *  Any system properties used in the specification files must be specified on
 *  the command line if they do not already exist in a .prop file by the same
 *  name and in the same directory as the test, whether that is relative to the
 *  current directory or to $JTESTS.
 *  <p>
 *  The statistics archives for each test are located by first searching in
 *  system directories by the names {@link GemFirePrms#names} in the test
 *  directory.  If a system directory for a given name is not found, the
 *  reporter looks in the location specified by {@link HostPrms#resourceDirBases}.
 *  <p>
 *  Usage:
 *  <blockquote><pre>
 *    java -classpath $JTESTS:$GEMFIRE/lib/gemfire.jar
 *         -Dgemfire.home=&lt;path_to_gemfire_product_tree&gt;
 *         -DJTESTS=&lt;path_to_test_classes&gt;
 *         [-DstatSpecFile=&lt;stat_spec_filename&gt;]
 *         [-DperfReportFile=&lt;perf_report_filename&gt;]
 *         [-DlogLevel=&lt;perf_reporter_log_level(default:info)&gt;]
 *         [-DBrief=&lt;brief_summar_report(default:false)&gt;]
 *         [-DuseWorkaround=&lt;use_workaround_for_bug_30288(default:true)&gt;]
 *         [user_defined_system_properties]
 *         perffmwk.PerfReporter
 *         [&lt;test_directories&gt;]
 *  </pre></blockquote>
 *  <p>
 *  Example:
 *  <blockquote><pre>
 *     java -classpath $JTESTS:$GEMFIRE/lib/gemfire.jar
 *          -Dgemfire.home=$GEMFIRE
 *          -DJTESTS=$JTESTS
 *          perffmwk.PerfReporter mytest-081303-* yourtest-081303-*
 *  </pre></blockquote>
 *  <p>
 *  Logging by the performance reporter is written to "perfreporter.log".
 *  The verbosity of this log is controlled via the <code>logLevel</code>
 *  system property (see {@link com.gemstone.gemfire.LogWriter}.
 *  <p>
 *  The performance report for each test is written to "perfreport.txt" in
 *  the test directory unless overridden with the system property
 *  "-DperfReportFile".  Any previously generated reports with the same name
 *  are overwritten.  Note that if <code>perfReportFile</code> denotes an
 *  {@linkplain File#isAbsolute absolute} file path, then the report 
 *  will not be placed in the test directory.  Note also that if the
 *  <code>perfReportFile</code> is <code>-</code>, then report will be
 *  printed to standard out.
 * <p>
 * If <code>useWorkaround</code> is true (default), a workaround is used for
 * Bug 30288 to allow active stats to flush to the archive.
 * <P>
 *
 * A "brief" performance report (specified with the "Brief" system
 * property) excludes "test dependent" information such as the trim
 * values and the statistics spec.  Instead, it contains a description
 * of the statistics followed by their measured values.
 */
public class PerfReporter extends Formatter {

  /** The name of the file from which to read the latest properties */
  protected static final String LATEST_PROP_FILE_NAME = "latest.prop";

  /** The name of the file to which to write trim specifications */
  protected static final String TRIM_SPEC_FILE_NAME = "trim.spec";

  /** The name of the file to which to write statistics specifications */
  protected static final String STAT_SPEC_FILE_NAME = "statistics.spec";

  /** The name of the file to which to write performance data */
  public static final String PERFORMANCE_REPORT_FILE_NAME = "perfreport.txt";

  /** Is a "brief" report being generated?
   *
   * author David Whitlock */
  public static boolean brief = false;

  private static boolean useWorkaround = true;

  private static LogWriter log;

  public static void main( String[] args ) {
    // bug 38218: this class loads gemfire.jar due to its use of LogWriter.
    SystemFailure.loadEmergencyClasses();
    try {
      boolean result = runperfreport( args );
      if ( ! result )
        logError( "runperfreport() returned false" );
    } 
    catch (VirtualMachineError e) {
      // Don't try to handle this; let thread group catch it.
      throw e;
    }
    catch( Throwable t ) {
      logError( TestHelper.getStackTrace( t ) );
    }
    System.exit(0);
  }
  public static boolean runperfreport( String[] args ) {

    // get the required settings
    String jtests = System.getProperty( "JTESTS" );
    if ( jtests == null ) {
      usage("Missing JTESTS");
      return false;
    }
    String gemfire = System.getProperty( "gemfire.home" );
    if ( gemfire == null ) {
      usage("Missing gemfire.home");
      return false;
    }
    Vector testDirs = new Vector();
    if ( args.length == 0 ) {
      testDirs.add( System.getProperty( "user.dir" ) );
    } else {
      for ( int i = 0; i < args.length; i++ ) {
        String testDir = args[i];
	if ( ! FileUtil.exists( testDir ) ) {
          usage("Directory not found: " + testDir);
          return false;
	}
	testDirs.add( FileUtil.absoluteFilenameFor( testDir ) );
      }
    }

    // get the optional settings
    String statSpecFile = System.getProperty( "statSpecFile" );
    if ( statSpecFile != null && ! FileUtil.exists( statSpecFile ) ) {
      usage("File not found: " + statSpecFile );
      return false;
    }
    String perfReportFile = System.getProperty( "perfReportFile" );
    String logLevel = System.getProperty( "logLevel", "info" );

    useWorkaround = Boolean.getBoolean("useWorkaround");
    brief = Boolean.getBoolean("Brief");

    // open the reporter log file, append if it already exists
    log = Log.createLogWriter( "perfreporter", "perfreporter", logLevel, true );
    log.info( "PerfReporter PID is " + ProcessMgr.getProcessId() );

    // log the reporter configuration
    log.info( "\nJTESTS = " + jtests +
              "\ngemfire.home = " + gemfire +
	      "\nstatSpecFile = " + statSpecFile +
	      "\nperfReportFile = " + perfReportFile +
	      "\nlogLevel = " + logLevel +
	      "\nBrief = " + brief +
	      "\ntestDirs = " + testDirs );

    // generate a report for each test directory provided
    generatePerformanceReports( testDirs );

    return true;
  }
  private static void generatePerformanceReports( Vector testDirs ) {
    log().info( "Generating performance reports for " + testDirs.size() + " test directories" );
    for ( Iterator i = testDirs.iterator(); i.hasNext(); ) {
      String testDir = (String) i.next();
      log().info( "..." + testDir );
      StatConfig statconfig = getStatConfig( testDir );
      if ( statconfig != null ) {
	SortedMap statvalues = getStatValues( statconfig );
	if ( statvalues != null ) {
	  String statreport = getStatReport( statconfig, statvalues );
	  if ( statreport != null ) {
	    printStatReport( statconfig, statreport );
	  }
	}
      }
    }
  }

  /**
   * Returns the <code>StatConfig</code> for the test run that resides
   * in the given test directory.
   */
  private static StatConfig getStatConfig( String testDir ) {
    String latestPropFile = getLatestPropFileName( testDir );
    String trimFile = getTrimFileName( testDir );
    String statFile = getStatFileName( testDir );
    try {
      return getStatConfig( testDir, latestPropFile, trimFile, statFile );

    } catch( FileNotFoundException e ) {
      log().warning( "Missing file: " + e.getMessage() );
      return null;
    }
  }

  /**
   * Returns a <code>StatConfig</code> for a given test run.  It fills
   * in the contents of the <code>StatConfig</code> with information
   * found in the given files.
   *
   * @param testDir
   *        The directory that contains the test output
   * @param latestPropFile
   *        The "latest.prop" for the test run
   * @param trimFile
   *        The file containing the statistics trim intervals for the
   *        test run
   * @param statFile
   *        The statistics spec file that describes the interesting
   *        statistics from the test run
   */
  private static StatConfig getStatConfig( String testDir, String latestPropFile,
                                           String trimFile, String statFile )
  throws FileNotFoundException {
    StatConfig statconfig = new StatConfig( testDir );

    // load required test configuration properties
    Properties latestProps = loadPropFile( latestPropFile );
    statconfig.setLatestProperties( latestProps );

    // load optional test configuration properties
    String testPropFile =
      getTestPropFileName( testDir, statconfig.getTestName() );
    if ( FileUtil.exists( testPropFile ) ) {
      Properties testProps = loadPropFile( testPropFile );
      statconfig.setTestProperties( testProps );
    }
    if (log().finerEnabled()) {
      log().finer(testDir + " set test properties "
                          + statconfig.getTestProperties());
    }

    // load optional trim file
    if ( trimFile != null )
      TrimSpecParser.parseFile( trimFile, statconfig );

    // load optional statistics file
    StatSpecParser.parseFile( statFile, statconfig );

    return statconfig;
  }

  // @todo Couldn't this method call the above four-argument
  //      getStatConfig()? 
  /** 
   * Used by {@link PerfComparer} to create a <code>StatConfig</code>
   * for the test run that resides in the given test output directory.
   */
  protected static StatConfig getStatConfig( String testDir,
                                             String statFile ) {
    try {
      StatConfig statconfig = new StatConfig( testDir );

      // load required test configuration properties
      String latestPropFile = getLatestPropFileName( testDir );
      if ( FileUtil.exists( latestPropFile ) ) {
	Properties p = loadPropFile( latestPropFile );
	statconfig.setLatestProperties( p );
      } else {
	return null;
      }

      // load optional test configuration properties
      String testPropFile = getTestPropFileName( testDir, statconfig.getTestName() );
      if ( FileUtil.exists( testPropFile ) ) {
	Properties p = loadPropFile( testPropFile );
	statconfig.setTestProperties( p );
      }
      if (log().finerEnabled()) {
        log().finer(testDir + " set test properties "
                            + statconfig.getTestProperties());
      }
      // set optional trimFile
      String trimFile = getTrimFileName( testDir );
      if ( FileUtil.exists( trimFile ) ) {
	TrimSpecParser.parseFile( trimFile, statconfig );
      }
      // set optional statfile (typically comes from from base or system property)
      if ( FileUtil.exists( statFile ) ) {
	StatSpecParser.parseFile( statFile, statconfig );
      }
      return statconfig;
    } catch( FileNotFoundException e ) {
      throw new PerfComparisonException( "File not found: " + e.getMessage(), e );
    }
  }

  /**
   * Returns a map that maps the name of statistics spec to a list of
   * {@link PerfStatValue}s for that spec.
   *
   * @see PerfStatReader#processStatConfig
   */
  protected static SortedMap getStatValues( StatConfig statconfig ) {
    // work around Bug 30288, problem reading stats that are being written
    for (int i = 1; i <= PerfStatMgr.RETRY_LIMIT; i++) {
      try {
        return PerfStatReader.processStatConfig( statconfig );
      } catch (ArrayIndexOutOfBoundsException e) {
        if (!useWorkaround) {
          String s = "Make sure that all statistics instances being combined have values for the full duration of the given trim interval.  If so, then the problem could be Bug 30288, and you should try running with -DuseWorkaround=true.";
          throw new HydraRuntimeException(s, e);
        }
        MasterController.sleepForMs(1);
        if (i == PerfStatMgr.RETRY_LIMIT) {
          String s = "Workaround for Bug 30288 failed.  Make sure that all statistics instances being combined have values for the full duration of the given trim interval.";
          throw new HydraRuntimeException(s, e);
        }
      }
    }
    throw new HydraInternalException("Should not happen");
  }

  private static String getStatReport( StatConfig statconfig, SortedMap statvalues ) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter( sw, true );
    printTitleInfo( statconfig, pw );
    pw.println( "\n" + DIVIDER + "\n" );
    printHeaderInfo( statconfig, pw );
    pw.println("");

    if (!brief) {
      pw.println( DIVIDER );
      center( "Trim Values", pw );
      pw.println( DIVIDER );
      printTrimValues( statconfig, pw );
    }

    pw.println( DIVIDER );
    center( "Statistics Values", pw );
    pw.println( DIVIDER );
    printStatisticsValues( statconfig, statvalues, pw );
    pw.flush();
    return sw.toString();
  }

  protected static void printStatReport( StatConfig statconfig, String report ) {
    String fileName = getPerfReportFileName( statconfig.getTestDir() );
    if (fileName != null) {
      FileUtil.writeToFile( fileName, report );

    } else {
      new PrintStream(new FileOutputStream(FileDescriptor.out)).println(report);
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  ////    FILES                                                             ////
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Returns a <code>Properties</code> object created from the
   * contents of the properties file with the given name.
   */
  private static Properties loadPropFile( String fn )
    throws FileNotFoundException {

    try {
      Properties p = FileUtil.getProperties(fn);
      if (p.size() == 0) {
        throw new FileNotFoundException("File " + fn + " is empty");
      }
      return p;
    } catch( FileNotFoundException e ) {
      throw e;
    } catch( IOException e ) {
      throw new FileNotFoundException( "Unable to load " + fn );
    }
  }

  /**
   * Returns the name of the "latest.prop" file in the given test
   * output directory.
   */
  private static String getLatestPropFileName( String testDir ) {
    return testDir + "/" + LATEST_PROP_FILE_NAME;
  }

  /**
   * Returns the (fully-qualified) name of the ".prop" file found in
   * the given test directory.  The name of the ".prop" is
   * "<code>testDir</code>/<code>testName</code>.prop". 
   *
   * @param testDir
   *        The name of the directory containing the prop file
   * @param testName
   *        The name of the test whose output resides in
   *        <code>testDir</code>
   */
  private static String getTestPropFileName( String testDir, 
                                             String testName ) {
    String fn = testName;
    int slash = fn.lastIndexOf("/");
    if ( slash != -1 ) {
      fn = fn.substring( slash + 1, fn.length() );
    }
    int dot = fn.lastIndexOf( ".conf" );
    if ( dot != -1 ) {
      fn = fn.substring( 0, dot );
    }
    String testpropfn = testDir + "/" + fn + ".prop";
    if (log().finerEnabled()) {
      log().finer(testDir + " using test prop file " + testpropfn);
    }
    return testpropfn;
  }

  /**
   * Returns the name of the statistics trim spec file that resides in
   * the given test output directory.
   */ 
  private static String getTrimFileName( String testDir ) {
    return testDir + "/" + TRIM_SPEC_FILE_NAME;
  }

  /**
   * Returns the name of the statistics spec file that resides in the
   * given test output directory.  If no spec file exists, then the
   * default one from <code>$JTESTS/perffmwk/statistics.spec</code> is
   * used.
   */
  private static String getStatFileName( String testDir ) {
    String fn = System.getProperty( "statSpecFile" );
    if ( fn == null ) {
      fn = testDir + "/" + STAT_SPEC_FILE_NAME;
    }
    if ( ! FileUtil.exists( fn ) ) {
      fn = System.getProperty( "JTESTS" ) + "/perffmwk/statistics.spec";
    }
    return fn;
  }

  /**
   * Returns the name of the performance report file that resides in
   * the given test output directory.
   */
  private static String getPerfReportFileName( String testDir ) {
    // @todo lises Give the new reports different names by default to avoid
    //             ever overwriting.
    String fn = System.getProperty( "perfReportFile" );
    if ( fn == null )
      fn = testDir + "/" + PERFORMANCE_REPORT_FILE_NAME;

    else if (fn.equals("-"))
      fn = null;

    else if (!(new File(fn)).isAbsolute())
      fn = testDir + "/" + fn;

    return fn;
  }

  //////////////////////////////////////////////////////////////////////////////
  ////    PRINTING                                                          ////
  //////////////////////////////////////////////////////////////////////////////

  /**
   * Formats a long string into a 72-column, indented paragraph
   *
   * @param text
   *        The text to be filled
   * @param indent
   *        The number of spaces to indent
   *
   * author David Whitlock
   */
  static String fillParagraph(String text, int indent) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw, true);

    String indentString = "";
    for (int i = 0; i < indent; i++) {
      indentString += " ";
    }
    pw.print(indentString);

    int printed = indentString.length();
    boolean firstWord = true;

    BreakIterator boundary = BreakIterator.getWordInstance();
    boundary.setText(text);
    int start = boundary.first();
    for (int end = boundary.next(); end != BreakIterator.DONE; 
         start = end, end = boundary.next()) {

      String word = text.substring(start, end);

      if (printed + word.length() > 72) {
        pw.println("");
        pw.print(indentString);
        printed = indentString.length();
        firstWord = true;
      }

      if (word.charAt(word.length() - 1) == '\n') {
        pw.write(word, 0, word.length() - 1);

      } else if (firstWord &&
                 Character.isWhitespace(word.charAt(0))) {
        pw.write(word, 1, word.length() - 1);

      } else {
        pw.print(word);
      }
      printed += (end - start);
      firstWord = false;
    }

    return sw.toString();
  }

  /**
   *  Adds title information to the report.
   */
  private static void printTitleInfo( StatConfig statconfig, PrintWriter pw ) {
    Properties p = statconfig.getLatestProperties();
    pw.println("");
    center("Performance Report", pw);
    center(p.getProperty("TestName"), pw);
    center((new Date()).toString(), pw);
  }
  /**
   *  Adds header information to the report.
   */
  private static void printHeaderInfo( StatConfig statconfig, PrintWriter pw ) {
    Properties p = statconfig.getLatestProperties();
    pw.println("TestUser: " + p.getProperty("TestUser"));
    pw.println("TestDirectory: " + p.getProperty("hydra.HostDescription.masterHost.userDir"));

    String description = "No description";
    description = p.getProperty("hydra.Prms-testDescription", description);
    pw.println("TestDescription:");
    pw.println(fillParagraph(description, 2));

    String requirement = "No requirement";
    requirement = p.getProperty("hydra.Prms-testRequirement", requirement);
    pw.println("TestRequirement: " + fillParagraph(requirement, 0));

    pw.println("");
    String hosts = "master-" + p.getProperty("hydra.HostDescription.masterHost.hostName")
                 + "(" + p.getProperty("hydra.HostDescription.masterHost.osType") + ")";
    for ( Enumeration e = p.propertyNames(); e.hasMoreElements(); ) {
      String name = (String) e.nextElement();
      int index = name.indexOf( ".hostName" );
      if ( name.startsWith( "hydra.HostDescription." ) && index != -1 ) {
        if ( ! name.equals( "hydra.HostDescription.masterHost.hostName" ) ) {
          hosts += " " + p.getProperty( name );
	  String os = name.substring( 0, index ) + ".osType";
	  hosts += "(" + p.getProperty( os ) + ")";
	}
      }
    }
    pw.println("TestHosts: " + hosts );
    pw.println("");
    pw.println("Build Version: " + p.getProperty("build.version"));
    pw.println("Source Version: " + p.getProperty("source.repository")
                           + ":"  + p.getProperty("source.revision")
                           + " (" + p.getProperty("source.date") + ")");
    if (statconfig.isNativeClient()) {
      pw.println("Native Client Build Version: "
                  + p.getProperty("nativeClient.version"));
      pw.println("Native Client Source Version: "
                  + p.getProperty("nativeClient.repository") + ":"
                  + p.getProperty("nativeClient.revision"));
    }
  }
  /**
   *  Adds all trim values to the report.
   */
  private static void printTrimValues( StatConfig statconfig, PrintWriter pw ) {
    for ( Iterator i = statconfig.getTrimSpecs().values().iterator(); i.hasNext(); ) {
      TrimSpec trimspec = (TrimSpec) i.next();
      pw.println( trimspec.toReportString() );
    }
  }
  /**
   *  Adds all statistics and their values to the report.
   */
  protected static void printStatisticsValues( StatConfig statconfig, SortedMap statvalues, PrintWriter pw ) {

    for ( Iterator i = statconfig.getStatSpecs().keySet().iterator(); i.hasNext(); ) {

      String statspeckey = (String) i.next();
      StatSpec statspec = statconfig.getStatSpecByKey( statspeckey );
      pw.println( statspec );
      if (!brief) {
        pw.println( SUBDIVIDER );
      }
      //TrimSpec trimspec = statconfig.getTrimSpec( statspec.getTrimSpecName() );
      //pw.println( trimspec );
      //pw.println( SUBDIVIDER );

      List statvaluesforspec = (List) statvalues.get( statspec.getName() );
      if ( statvaluesforspec.size() == 0 ) {
        pw.println( "No matches were found for this specification" );
      } else {
        for ( Iterator j = statvaluesforspec.iterator(); j.hasNext(); ) {
          PerfStatValue statval = (PerfStatValue) j.next();
          pw.println( "==>" + statval.toString() );
        }
      }
      pw.println( DIVIDER );
    }
  }
  private static LogWriter log() {
    return Log.getLogWriter();
  }

  //////////////////////////////////////////////////////////////////////////////
  ////    USAGE                                                             ////
  //////////////////////////////////////////////////////////////////////////////

  private static void usage(String s) {
    System.out.println("\n** " + s + "\n");
    System.out.println( "Usage: java -Dgemfire.home=<path_to_gemfire_product_tree> -DJTESTS=<path_to_test_classes> [-DstatSpecFile=<statistics_specification_filename>] [-DperfReportFile=<perf_report_filename>] [-DlogLevel=<log_level(default:info)>] [-DuseWorkaround=<use_workaround_for_bug_30288(default:true)>] perffmwk.PerfReporter [test_directories]" );
  }
  private static void logError( String msg ) {
    if ( log == null )
      System.err.println( msg );
    else
      log().severe( msg );
  }
}