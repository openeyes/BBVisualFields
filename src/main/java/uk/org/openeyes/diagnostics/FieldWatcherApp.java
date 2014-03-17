/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.org.openeyes.diagnostics;

import java.io.File;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

/**
 *
 * @author rich
 */
public class FieldWatcherApp {

  public static void main(String[] args) {

    if (args.length == 0) {
      System.out.println("Try -h");
      System.exit(1);
    }
    Options options = new Options();
    Option optionFile = new Option("f", "file", true,
            "Specify XML file to send, then quit (mutually exclusive with -i)");
    Option optionHost = new Option("s", "host", true,
            "Specify server to send messages to.");
    Option optionRegex = new Option("r", "regex", true,
            "Regular expression to determine valid patient identifiers.");
    Option optionInterval = new Option("i", "interval", true,
            "Time in seconds to sleep between checking. Must be > 0");
    Option optionPort = new Option("p", "port", true,
            "Port to connect to on server.");
    Option optionInDir = new Option("d", "dir", true,
            "Directory to watch for new files.");
    Option optionDupDir = new Option("u", "duplicates", true,
            "Duplicate files (successfully transferred) are moved to this directory.");
    Option optionErrDir = new Option("e", "errDir", true,
            "Dirctory to move files that that failed validation.");
    Option optionArchiveDir = new Option("a", "archive-dir", true,
            "Directory to move sucessfully transferred files to.");
    Option optionImageOpts = new Option("g", "image-options", true,
            "Specify location and segment of humphrey test to extract, along with"
			+ " scaling parameters. Format: x,y,w,h,x1,y1 where x,y is the"
			+ " the location to cut image with wxh size, scaled to x1,y1."
			+ " Scaling parameters (x1,y1) are optional and can be omitted.");
    Option optionSource = new Option("x", "xml-source", false,
            "Include XML source file information with captured data. Default"
			+ " is false.");
    Option optionHelp = new Option("h", "help", false,
            "Print this help then quit.");
    options.addOption(optionErrDir);
    options.addOption(optionFile);
    options.addOption(optionInDir);
    options.addOption(optionArchiveDir);
    options.addOption(optionHost);
    options.addOption(optionInterval);
    options.addOption(optionImageOpts);
    options.addOption(optionPort);
    options.addOption(optionDupDir);
    options.addOption(optionSource);
    options.addOption(optionHelp);
    options.addOption(optionRegex);
    CommandLineParser parser = new PosixParser();
    FieldWatcher watcher = new FieldWatcher();
    try {
      CommandLine cmd = parser.parse(options, args);
      if (cmd.hasOption("help") || cmd.hasOption('h')) {
        FhirUtils.printHelp(options);
      }
      if (cmd.hasOption("s") || cmd.hasOption("host")) {
        watcher.setHost(cmd.getOptionValue("host"));
      }
      if (cmd.hasOption("d") || cmd.hasOption("dir")) {
        watcher.setDir(new File(cmd.getOptionValue("dir")));
        if (!watcher.getDir().exists()) {
          System.err.println(watcher.getDir().getAbsolutePath()
                  + " does not exist. Specify a valid watch directory.");
          System.exit(1);
        }
      }
      if (cmd.hasOption("a") || cmd.hasOption("archive-dir")) {
        watcher.setArchiveDir(new File(cmd.getOptionValue("archive-dir")));
        if (!watcher.getArchiveDir().exists()) {
          System.err.println(watcher.getArchiveDir().getAbsolutePath()
                  + " does not exist. Specify a valid directory.");
          System.exit(1);
        }
      }
      if (cmd.hasOption("r") || cmd.hasOption("regex")) {
        watcher.setRegex(cmd.getOptionValue("regex"));
      }
//	  TODO - duplicates are an issue that needs to be dealt with
//      if (cmd.hasOption("u") || cmd.hasOption("duplicates")) {
//        watcher.setDuplicateDir(new File(cmd.getOptionValue("duplicates")));
//        if (!watcher.getDuplicateDir().exists()) {
//          System.err.println(watcher.getDuplicateDir() + " does not exist. "
//                  + "Specify a valid directory.");
//          System.exit(1);
//        }
//      }
      if (cmd.hasOption("e") || cmd.hasOption("errDir")) {
        watcher.setErrDir(new File(cmd.getOptionValue("errDir")));
        if (!watcher.getErrDir().exists()) {
          System.err.println(watcher.getErrDir() + " does not exist. "
                  + "Specify a valid directory.");
          System.exit(1);
        }
      }
      if (cmd.hasOption("g") || cmd.hasOption("image-options")) {
        watcher.setImageOptions(cmd.getOptionValue("image-options"));
      }
      if (cmd.hasOption("x") || cmd.hasOption("xml-source")) {
        watcher.setIncludeSource(true);
      }
      if (cmd.hasOption("p") || cmd.hasOption("port")) {
        String port = cmd.getOptionValue("port");
        try {
          watcher.setPort(Integer.parseInt(port));
        } catch (NumberFormatException nfex) {
          System.err.println("Invalid value: " + port);
          System.err.println("Specify port number as a positive integer.");
          System.exit(1);
        }
      }
	  
      if ((cmd.hasOption("i") || cmd.hasOption("interval"))
			  &&  (cmd.hasOption("f") || cmd.hasOption("file"))) {
		System.err.println("Cannot specify interval AND file; specify one or the other.");
		System.exit(1);
	  }
      if (cmd.hasOption("i") || cmd.hasOption("interval")) {
        String interval = cmd.getOptionValue("interval");
        try {
          watcher.setInterval(Integer.parseInt(interval));
        } catch (NumberFormatException nfex) {
          System.err.println("Invalid value: " + interval);
          System.err.println("Specify interval in seconds as a positive integer.");
          System.exit(1);
        }
      }
      if (cmd.hasOption("f") || cmd.hasOption("file")) {
        String file = cmd.getOptionValue("file");
		watcher.processFile(new File(file));
      } else {
	  
		// post checks
		if (null != watcher) {
		  Thread t = new Thread(watcher);
		  t.start();
		}
	  }
    } catch (Exception ex) {
      System.err.println("Error: " + ex.getMessage());
    }
  }
}
