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
public class FieldProcessorApp {

  public static void main(String[] args) {

    if (args.length == 0) {
      System.out.println("Try -h");
      System.exit(1);
    }
    Options options = CommonOptions.getCommonOptions();
    Option optionFile = new Option("f", "file", true,
            "Specify XML file to send, then quit (mutually exclusive with -i)");
    Option optionHost = new Option("s", "host", true,
            "Specify server to send messages to.");
    Option optionInterval = new Option("i", "interval", true,
            "Time in seconds to sleep between checking. Must be > 0");
    Option optionPort = new Option("p", "port", true,	
            "Port to connect to on server.");
    Option optionInDir = new Option("d", "dir", true,
            "Directory to watch for new files.");
    Option optionDupDir = new Option("u", "duplicates", true,
            "Duplicate files (successfully transferred) are moved to this directory.");
    Option optionOutgoing = new Option("t", "outgoing", true,
            "Directory to place measurement files that were not successfully sent.");
    options.addOption(optionFile);
    options.addOption(optionInDir);
    options.addOption(optionHost);
    options.addOption(optionInterval);
    options.addOption(optionPort);
    options.addOption(optionDupDir);
    options.addOption(optionOutgoing);
	FieldProcessor watcher = new FieldProcessor();
	CommonOptions.parseCommonOptions(watcher, options, args);
    CommandLineParser parser = new PosixParser();
    try {
      CommandLine cmd = parser.parse(options, args);
      if (cmd.hasOption("help") || cmd.hasOption('h')) {
        FhirUtils.printHelp(options);
      }
      if (cmd.hasOption("s") || cmd.hasOption("host")) {
        watcher.setHost(cmd.getOptionValue("host"));
      }
      if (cmd.hasOption("t") || cmd.hasOption("outgoing")) {
        watcher.setOutgoingDir(cmd.getOptionValue("outgoing"));
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
